/*
 * This is the C portion of Jaunch, the configurable native launcher.
 *
 * Its primary function is to launch a non-native runtime plus main program
 * in the same process, by dynamically loading the runtime library.
 *
 * Currently supported runtimes include Python and the Java Virtual Machine.
 *
 * - For Python logic, see python.h.
 * - For JVM logic, see jvm.h.
 *
 * The C portion of Jaunch is empowered by a so-called "configurator" program,
 * which is the more sophisticated portion of Jaunch. The C launcher invokes
 * the configurator program in a separate process, using the function:
 *
 *     int run_command(const char *command,
 *         const char *input[], size_t numInput,
 *         char ***output, size_t *numOutput)
 *
 * The C code waits for the Jaunch configurator process to complete, then
 * passes the outputs given by the configurator to the appropriate `launch`
 * function.
 *
 * In this way, the non-native runtime is launched in the same process by C,
 * but in a way that is fuily customizable from the Jaunch code written in a
 * high-level language.
 *
 * For example, a command line invocation of:
 *
 *   fizzbuzz Hello --verbose=2 --min 100 --max 200
 *
 * might be translated by the configurator into a Python invocation:
 *
 *     python -vv fizzbuzz.py Hello 100..200
 *
 * or a Java invocation:
 *
 *     java -DverboseLevel=2 -Xmx128m com.fizzbuzz.FizzBuzz Hello 100..200
 *
 * depending on the way Jaunch is configured via its jaunch.toml file.
 *
 * See the jaunch.toml file for a walkthrough of how the configurator
 * can be flexibly configured to decide how arguments are transformed.
 */

#include <unistd.h>

#include "common.h"
#include "jvm.h"
#include "python.h"

#ifdef __linux__
#include "linux.h"
#endif

#ifdef __APPLE__
#include "macos.h"
#endif

#ifdef WIN32
#include "win32.h"
#else
#include "posix.h"
#endif

#ifdef __x86_64__
#define OS_ARCH "x64"
#endif

#ifdef __aarch64__
#define OS_ARCH "arm64"
#endif

// List of places to search for the jaunch configurator executable.
//
// NB: This list should align with the configDirs list in Jaunch.kt,
// except for the trailing "Contents/MacOS/" and NULL entries.
//
// The trailing slashes make the math simpler in the path function logic.
const char *JAUNCH_SEARCH_PATHS[] = {
    "jaunch"SLASH,
    ".jaunch"SLASH,
    "config"SLASH"jaunch"SLASH,
    ".config"SLASH"jaunch"SLASH,
    "Contents"SLASH"MacOS"SLASH,
    NULL,
};

/* result=$(dirname "$argv0")/$subdir$command */
char *path(const char *argv0, const char *subdir, const char *command) {
    // Calculate string lengths.
    const char *last_slash = argv0 == NULL ? NULL : strrchr(argv0, SLASH[0]);
    size_t dir_len = (size_t)(last_slash == NULL ? 1 : last_slash - argv0);
    size_t subdir_len = subdir == NULL ? 0 : strlen(subdir);
    size_t command_len = strlen(command);
    size_t result_len = dir_len + 1 + subdir_len + command_len;

    // Allocate the result string.
    char *result = (char *)malloc(result_len + 1);
    if (result == NULL) return NULL;

    // Build the result string.
    if (last_slash == NULL) result[0] = '.';
    else strncpy(result, argv0, dir_len);
    result[dir_len] = SLASH[0];
    result[dir_len + 1] = '\0';
    if (subdir != NULL) strcat(result, subdir); // result += subdir
    strcat(result, command); // result += command

    return result;
}

int main(const int argc, const char *argv[]) {
    // Enable debug mode when --debug is an argument.
    for (size_t i = 0; i < argc; i++)
        if (strcmp(argv[i], "--debug") == 0) debug_mode = 1;

    char *command = NULL;
    size_t search_path_count = sizeof(JAUNCH_SEARCH_PATHS) / sizeof(char *);
    for (size_t i = 0; i < search_path_count; i++) {
        // First, look for jaunch configurator with a `-<os>-<arch>` suffix.
        command = path(
            argc == 0 ? NULL : argv[0],
            JAUNCH_SEARCH_PATHS[i],
            "jaunch-" OS_NAME "-" OS_ARCH EXE_SUFFIX
        );
        if (file_exists(command)) break;
        else debug("[JAUNCH] No configurator at %s", command);

        // If not found, look for plain jaunch configurator with no suffix.
        free(command);
        command = path(
            argc == 0 ? NULL : argv[0],
            JAUNCH_SEARCH_PATHS[i],
            "jaunch" EXE_SUFFIX
        );
        if (file_exists(command)) break;
        else debug("[JAUNCH] No configurator at %s", command);

        // Nothing at this search path; clean up and move on to the next one.
        free(command);
        command = NULL;
    }
    if (command == NULL) {
        error("Failed to locate the jaunch configurator program.");
        return ERROR_COMMAND_PATH;
    }
    debug("[JAUNCH] configurator command = %s", command);

    char **output_argv;
    size_t output_argc;

    // Run external command to process the command line arguments.

    int run_result = run_command((const char *)command, argv, argc, &output_argv, &output_argc);
    free(command);
    if (run_result != SUCCESS) return run_result;

    debug("[JAUNCH] output_argc = %zu", output_argc);
    for (size_t i = 0; i < output_argc; i++) {
        debug("[JAUNCH] output_argv[%zu] = %s", i, output_argv[i]);
    }
    if (output_argc < 1) {
        error("Expected at least 1 line of output but got %d", output_argc);
        return ERROR_OUTPUT;
    }

    // Perform the indicated directive.

    const char *directive = output_argv[0];
    debug("[JAUNCH] directive = %s", directive);

    int launch_result = SUCCESS;

    if (strcmp(directive, "JVM") == 0) {
        launch_result = launch(launch_jvm,
            output_argc, (const char **)output_argv);
    }
    else if (strcmp(directive, "PYTHON") == 0) {
        launch_result = launch(launch_python,
            output_argc, (const char **)output_argv);
    }
    else if (strcmp(directive, "CANCEL") == 0) {
      launch_result = SUCCESS;
    }
    else {
        // If directive is ERROR, show subsequent lines.
        // If directive is anything else, show ALL lines.
        size_t i0 = strcmp(directive, "ERROR") == 0 ? 1 : 0;
        for (size_t i = i0; i < output_argc; i++) {
            error(output_argv[i]);
        }
        // TODO: show_alert(title, message);
    }

    // Clean up.
    for (size_t i = 0; i < output_argc; i++) {
        free(output_argv[i]);
    }
    free(output_argv);

    return launch_result;
}
