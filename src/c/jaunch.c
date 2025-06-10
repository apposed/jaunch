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
 *         size_t numInput, const char *input[],
 *         size_t *numOutput, char ***output)
 *
 * The C code waits for the Jaunch configurator process to complete, then
 * passes the outputs given by the configurator to the appropriate `launch`
 * function.
 *
 * In this way, the non-native runtime is launched in the same process by C,
 * but in a way that is fully customizable from the Jaunch code written in a
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
 * depending on the way Jaunch is configured via its TOML config files.
 *
 * See the common.toml file for a walkthrough of how the configurator
 * can be flexibly configured to decide how arguments are transformed.
 */

#include <unistd.h>

#include "common.h"

// -- PLATFORMS --
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

// -- RUNTIMES --
#include "jvm.h"
#include "python.h"

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
        else if (strcmp(argv[i], "--headless") == 0) headless_mode = 1;

    // Perform initial platform-specific setup.
    // * On Windows, initialize the console.
    // * On macOS, untranslocate Gatekeeper-mangled apps.
    setup(argc, argv);

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

    // Run external command to process the command line arguments.
    char **out_argv;
    size_t out_argc;
    int run_result = run_command((const char *)command, argc, argv, &out_argc, &out_argv);
    free(command);
    if (run_result != SUCCESS) return run_result;

    CHECK_ARGS("JAUNCH", "out", out_argc, 1, 99999, out_argv);
    // Maximum # of lines to treat as valid. ^^^^^
    // We could of course leave this unbounded, but pragmatically, the value
    // will probably never exceed this size -- it is more likely that a
    // programming error in the configurator yields a much-too-large argc
    // value, and it is better to fail fast than to access invalid memory.

    // Perform the indicated directive(s).

    int exit_code = SUCCESS;
    size_t index = 0;
    while (index < out_argc) {
        // Prepare the (argc, argv) for the next directive.
        const char *directive = (const char *)(out_argv[index]);

        // Honor the special ABORT directive immediately (no further parsing).
        if (strcmp(directive, "ABORT") == 0) {
            const size_t extra = out_argc - index - 1;
            if (extra > 0) error("Ignoring %zu trailing output lines.", extra);
            break;
        }
        if (index == out_argc - 1) {
            error("Invalid trailing directive: %s", directive);
            break;
        }
        const size_t dir_argc = atoi(out_argv[index + 1]);
        const char **dir_argv = (const char **)(out_argv + index + 2);
        CHECK_ARGS("JAUNCH", "dir", dir_argc, 0, out_argc - index, dir_argv);
        index += 2 + dir_argc; // Advance index past this directive block.

        // Call the directive's associated function.
        if (strcmp(directive, "JVM") == 0) {
            exit_code = launch(launch_jvm, dir_argc, dir_argv);
            if (exit_code != SUCCESS) break;
        }
        else if (strcmp(directive, "PYTHON") == 0) {
            exit_code = launch(launch_python, dir_argc, dir_argv);
            if (exit_code != SUCCESS) break;
        }
        else if (strcmp(directive, "INIT_THREADS") == 0) {
            init_threads();
        }
        else if (strcmp(directive, "ERROR") == 0) {
            // =======================================================================
            // Parse the arguments, which must conform to the following structure:
            //
            // 1. Exit code to use after issuing the error message.
            // 2. The error message, which may span multiple lines.
            // =======================================================================
            exit_code = atoi(dir_argv[0]);
            if (exit_code < 20) exit_code = 20;
            if (exit_code > 255) exit_code = 255;

            // Log all error lines first.
            for (size_t i = 1; i < dir_argc; i++) error(dir_argv[i]);

            // Now join the error lines and display in an alert box.
            char *message = join_strings(dir_argv + 1, dir_argc - 1, "\n");
            if (message != NULL) {
                if (!headless_mode) show_alert("Error", message);
                free(message);
            }
            else {
                error("An unknown error occurred.");
                if (!headless_mode) show_alert("Error", "An unknown error occurred.");
            }
        }
        else {
            // Mysterious directive! Fail fast.
            error("Unknown directive: %s", directive);
            exit_code = ERROR_UNKNOWN_DIRECTIVE;
            break;
        }
    }

    // Clean up.
    for (size_t i = 0; i < out_argc; i++) {
        free(out_argv[i]);
    }
    free(out_argv);

    // Do any final platform-specific cleanup.
    teardown();

    return exit_code;
}
