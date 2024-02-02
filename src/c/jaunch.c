/*
 * This is the C portion of Jaunch, the configurable Java launcher.
 *
 * Its primary function is:
 *
 *   launch_jvm:
 *     1. path to libjvm
 *     2. argc + argv for the jvm
 *     3. main class to run
 *     4. argc + argv for the main invocation
 *
 * To invoke this function in a configurable way, it uses a secondary function:
 *
 *   run_command:
 *     1. path to configurator executable
 *     2. argv list, to be passed to the configurator via stdin, one per line
 *
 * This function is used to invoke Jaunch, a.k.a. the configurator executable,
 * in its own process. The C code waits for the Jaunch process to complete,
 * then passes the outputs given by Jaunch to the `launch_jvm` function above.
 *
 * In this way, Java is launched in the same process by C, but in a way that
 * is fuily customizable from the Jaunch code written in a high-level language.
 */

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>

#include "jni.h"

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
#define OS_ARCH "aarch64"
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

int launch_jvm(const char *libjvm_path, const size_t jvm_argc, const char *jvm_argv[],
    const char *main_class_name, const size_t main_argc, const char *main_argv[])
{
    // Load libjvm.
    debug("[JAUNCH] LOADING LIBJVM");
#ifdef WIN32
    HMODULE jvm_library = LoadLibrary(libjvm_path);
#else
    void *jvm_library = dlopen(libjvm_path, RTLD_NOW | RTLD_GLOBAL);
#endif
    if (!jvm_library) { error("Error loading libjvm: %s", dlerror()); return ERROR_DLOPEN; }

    // Load JNI_CreateJavaVM function.
    debug("[JAUNCH] LOADING JNI_CreateJavaVM");
#ifdef WIN32
    FARPROC JNI_CreateJavaVM = GetProcAddress(jvm_library, "JNI_CreateJavaVM");
#else
    static jint (*JNI_CreateJavaVM)(JavaVM **pvm, void **penv, void *args);
    JNI_CreateJavaVM = dlsym(jvm_library, "JNI_CreateJavaVM");
#endif
    if (!JNI_CreateJavaVM) {
        error("Error finding JNI_CreateJavaVM: %s", dlerror());
        dlclose(jvm_library);
        return ERROR_DLSYM;
    }

    // Populate VM options.
    debug("[JAUNCH] POPULATING VM OPTIONS");
    JavaVMOption vmOptions[jvm_argc + 1];
    for (size_t i = 0; i < jvm_argc; i++) {
        vmOptions[i].optionString = (char *)jvm_argv[i];
    }
    vmOptions[jvm_argc].optionString = NULL;

    // Populate VM init args.
    debug("[JAUNCH] POPULATING VM INIT ARGS");
    JavaVMInitArgs vmInitArgs;
    vmInitArgs.version = JNI_VERSION_1_8;
    vmInitArgs.options = vmOptions;
    vmInitArgs.nOptions = jvm_argc;
    vmInitArgs.ignoreUnrecognized = JNI_FALSE;

    // Create the JVM.
    debug("[JAUNCH] CREATING JVM");
    JavaVM *jvm;
    JNIEnv *env;
    if (JNI_CreateJavaVM(&jvm, (void **)&env, &vmInitArgs) != JNI_OK) {
        error("Error creating Java Virtual Machine");
        dlclose(jvm_library);
        return ERROR_CREATE_JAVA_VM;
    }

    // Find the main class.
    debug("[JAUNCH] FINDING MAIN CLASS");
    jclass mainClass = (*env)->FindClass(env, main_class_name);
    if (mainClass == NULL) {
        error("Error finding class %s", main_class_name);
        (*jvm)->DestroyJavaVM(jvm);
        dlclose(jvm_library);
        return ERROR_FIND_CLASS;
    }

    // Find the main method.
    debug("[JAUNCH] FINDING MAIN METHOD");
    jmethodID mainMethod = (*env)->GetStaticMethodID(env, mainClass, "main", "([Ljava/lang/String;)V");
    if (mainMethod == NULL) {
        error("Error finding main method of class %s", main_class_name);
        (*jvm)->DestroyJavaVM(jvm);
        dlclose(jvm_library);
        return ERROR_GET_STATIC_METHOD_ID;
    }

    // Populate main method arguments.
    debug("[JAUNCH] FINDING MAIN METHOD ARGUMENTS");
    jobjectArray javaArgs = (*env)->NewObjectArray(env, main_argc, (*env)->FindClass(env, "java/lang/String"), NULL);
    for (size_t i = 0; i < main_argc; i++) {
        (*env)->SetObjectArrayElement(env, javaArgs, i, (*env)->NewStringUTF(env, main_argv[i]));
    }

    // Invoke the main method.
    debug("[JAUNCH] INVOKING MAIN METHOD");
    (*env)->CallStaticVoidMethodA(env, mainClass, mainMethod, (jvalue *)&javaArgs);

    debug("[JAUNCH] DETACHING CURRENT THREAD");
    if ((*jvm)->DetachCurrentThread(jvm)) {
        error("Could not detach current thread from JVM");
    }

    // Clean up.
    debug("[JAUNCH] DESTROYING JAVA VM");
    (*jvm)->DestroyJavaVM(jvm);
    debug("[JAUNCH] CLOSING LIBJVM");
    dlclose(jvm_library);
    debug("[JAUNCH] GOODBYE");

    return SUCCESS;
}

int main(const int argc, const char *argv[]) {
    // Enable debug mode when --debug is an argument.
    for (size_t i = 0; i < argc; i++)
        if (strcmp(argv[i], "--debug") == 0) debug_mode = 1;

    char *command = NULL;
    size_t search_path_count = sizeof(JAUNCH_SEARCH_PATHS) / sizeof(char *);
    for (size_t i = 0; i < search_path_count; i++) {
      // First, look for jaunch configurator with a `-<os>-<arch>` suffix.
      command = path(argc == 0 ? NULL : argv[0], JAUNCH_SEARCH_PATHS[i], "jaunch-" OS_NAME "-" OS_ARCH EXE_SUFFIX);
      if (file_exists(command)) break;
      else debug("[JAUNCH] No configurator at %s", command);

      // If not found, look for plain jaunch configurator with no suffix.
      free(command);
      command = path(argc == 0 ? NULL : argv[0], JAUNCH_SEARCH_PATHS[i], "jaunch" EXE_SUFFIX);
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

    char **outputLines;
    size_t numOutput;

    // Run external command to process the command line arguments.

    int run_result = run_command((const char *)command, argv, argc, &outputLines, &numOutput);
    free(command);
    if (run_result != SUCCESS) return run_result;

    debug("[JAUNCH] numOutput = %zu", numOutput);
    for (size_t i = 0; i < numOutput; i++) {
        debug("[JAUNCH] outputLines[%zu] = %s", i, outputLines[i]);
    }
    if (numOutput < 5) {
        error("Expected at least 5 lines of output but got %d", numOutput);
        return ERROR_OUTPUT;
    }

    // Parse the command's output.

    char **ptr = outputLines;
    const char *directive = *ptr++;
    debug("[JAUNCH] directive = %s", directive);

    const char *libjvm_path = *ptr++;
    debug("[JAUNCH] libjvm_path = %s", libjvm_path);

    const int jvm_argc = atoi(*ptr++);
    debug("[JAUNCH] jvm_argc = %d", jvm_argc);
    if (jvm_argc < 0) {
        error("jvm_argc value is too small: %d", jvm_argc);
        return ERROR_JVM_ARGC_TOO_SMALL;
    }
    if (numOutput < 5 + jvm_argc) {
        error("jvm_argc value is too large: %d", jvm_argc);
        return ERROR_JVM_ARGC_TOO_LARGE;
    }

    const char **jvm_argv = (const char **)ptr;
    ptr += jvm_argc;
    for (size_t i = 0; i < jvm_argc; i++) {
        debug("[JAUNCH] jvm_argv[%zu] = %s", i, jvm_argv[i]);
    }

    const char *main_class_name = *ptr++;
    debug("[JAUNCH] main_class_name = %s", main_class_name);

    const int main_argc = atoi(*ptr++);
    debug("[JAUNCH] main_argc = %d", main_argc);
    if (main_argc < 0) {
        error("main_argc value is too small: %d", main_argc);
        return ERROR_MAIN_ARGC_TOO_SMALL;
    }
    if (numOutput < 5 + jvm_argc + main_argc) {
        error("main_argc value is too large: %d", main_argc);
        return ERROR_MAIN_ARGC_TOO_LARGE;
    }

    const char **main_argv = (const char **)ptr;
    ptr += main_argc;
    for (size_t i = 0; i < main_argc; i++) {
        debug("[JAUNCH] main_argv[%zu] = %s", i, main_argv[i]);
    }

    // Perform the indicated directive.

    if (strcmp(directive, "LAUNCH") == 0) {
        // Launch the JVM with the received arguments.
        int launch_result = launch_jvm(
            libjvm_path, jvm_argc, jvm_argv,
            main_class_name, main_argc, main_argv
        );
        // Clean up.
        for (size_t i = 0; i < numOutput; i++) {
            free(outputLines[i]);
        }
        free(outputLines);

        return launch_result;
    }

    if (strcmp(directive, "CANCEL") == 0) return SUCCESS;

    error("Unknown directive: %s", directive);
    return ERROR_UNKNOWN_DIRECTIVE;
}
