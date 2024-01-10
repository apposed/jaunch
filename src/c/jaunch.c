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

#ifdef APPLE
#include "macos.h"
#endif

#ifdef WIN32
#include "win32.h"
#else
#include "posix.h"
#endif

/* result=$(dirname "$argv0")/"$command" */
char *path(const char *argv0, const char *command) {
    // Calculate string lengths.
    const char *last_slash = argv0 == NULL ? NULL : strrchr(argv0, '/');
    size_t dir_len = (size_t)(last_slash == NULL ? 1 : last_slash - argv0);
    size_t command_len = strlen(command);
    size_t result_len = dir_len + 1 + command_len;

    // Allocate the result string.
    char *result = (char *)malloc(result_len + 1);
    if (result == NULL) return NULL;

    // Build the result string.
    if (last_slash == NULL) {
        result[0] = '.';
    }
    else {
        strncpy(result, argv0, dir_len);
    }
    result[dir_len] = '/';
    result[dir_len + 1] = '\0';
    strcat(result, command); // result += command

    return result;
}

int launch_jvm(const char *libjvm_path, const size_t jvm_argc, const char *jvm_argv[],
    const char *main_class_name, const size_t main_argc, const char *main_argv[])
{
    // Load libjvm.
    debug("LOADING LIBJVM");
#ifdef WIN32
    HMODULE jvm_library = LoadLibrary(libjvm_path);
#else
    void *jvm_library = dlopen(libjvm_path, RTLD_NOW | RTLD_GLOBAL);
#endif
    if (!jvm_library) { error("Error loading libjvm: %s", dlerror()); return ERROR_DLOPEN; }

    // Load JNI_CreateJavaVM function.
    debug("LOADING JNI_CreateJavaVM");
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
    debug("POPULATING VM OPTIONS");
    JavaVMOption vmOptions[jvm_argc + 1];
    for (size_t i = 0; i < jvm_argc; i++) {
        vmOptions[i].optionString = (char *)jvm_argv[i];
    }
    vmOptions[jvm_argc].optionString = NULL;

    // Populate VM init args.
    debug("POPULATING VM INIT ARGS");
    JavaVMInitArgs vmInitArgs;
    vmInitArgs.version = JNI_VERSION_1_8;
    vmInitArgs.options = vmOptions;
    vmInitArgs.nOptions = jvm_argc;
    vmInitArgs.ignoreUnrecognized = JNI_FALSE;

    // Create the JVM.
    debug("CREATING JVM");
    JavaVM *jvm;
    JNIEnv *env;
    if (JNI_CreateJavaVM(&jvm, (void **)&env, &vmInitArgs) != JNI_OK) {
        error("Error creating Java VM");
        dlclose(jvm_library);
        return ERROR_CREATE_JAVA_VM;
    }

    // Find the main class.
    debug("FINDING MAIN CLASS");
    jclass mainClass = (*env)->FindClass(env, main_class_name);
    if (mainClass == NULL) {
        error("Error finding class %s", main_class_name);
        (*jvm)->DestroyJavaVM(jvm);
        dlclose(jvm_library);
        return ERROR_FIND_CLASS;
    }

    // Find the main method.
    debug("FINDING MAIN METHOD");
    jmethodID mainMethod = (*env)->GetStaticMethodID(env, mainClass, "main", "([Ljava/lang/String;)V");
    if (mainMethod == NULL) {
        error("Error finding main method");
        (*jvm)->DestroyJavaVM(jvm);
        dlclose(jvm_library);
        return ERROR_GET_STATIC_METHOD_ID;
    }

    // Populate main method arguments.
    debug("FINDING MAIN METHOD ARGUMENTS");
    jobjectArray javaArgs = (*env)->NewObjectArray(env, main_argc, (*env)->FindClass(env, "java/lang/String"), NULL);
    for (size_t i = 0; i < main_argc; i++) {
        (*env)->SetObjectArrayElement(env, javaArgs, i, (*env)->NewStringUTF(env, main_argv[i]));
    }

    // Invoke the main method.
    debug("INVOKING MAIN METHOD");
    (*env)->CallStaticVoidMethodA(env, mainClass, mainMethod, (jvalue *)&javaArgs);

    debug("DETACHING CURRENT THREAD");
    if ((*jvm)->DetachCurrentThread(jvm)) {
        error("Could not detach current thread");
    }

    // Clean up.
    debug("DESTROYING JAVA VM");
    (*jvm)->DestroyJavaVM(jvm);
    debug("CLOSING LIBJVM");
    dlclose(jvm_library);
    debug("GOODBYE");

    return SUCCESS;
}

int main(const int argc, const char *argv[]) {
    // Enable debug mode when --debug is an argument.
    for (size_t i = 0; i < argc; i++)
        if (strcmp(argv[i], "--debug") == 0) debug_mode = 1;

    const char *command = path(argc == 0 ? NULL : argv[0], JAUNCH_EXE);
    if (command == NULL) {
        error("command path");
        return ERROR_COMMAND_PATH;
    }
    debug("jaunch command = %s", command);

    char **outputLines;
    size_t numOutput;

    // Run external command to process the command line arguments.

    int run_result = run_command(command, argv, argc, &outputLines, &numOutput);
    if (run_result != SUCCESS) return run_result;

    debug("numOutput = %zu", numOutput);
    for (size_t i = 0; i < numOutput; i++) {
        debug("outputLines[%zu] = %s", i, outputLines[i]);
    }
    if (numOutput < 5) {
        error("output");
        return ERROR_OUTPUT;
    }

    // Parse the command's output.

    char **ptr = outputLines;
    const char *directive = *ptr++;
    debug("directive = %s", directive);

    const char *libjvm_path = *ptr++;
    debug("libjvm_path = %s", libjvm_path);

    const int jvm_argc = atoi(*ptr++);
    debug("jvm_argc = %d", jvm_argc);
    if (jvm_argc < 0) {
        error("jvm_argc too small");
        return ERROR_JVM_ARGC_TOO_SMALL;
    }
    if (numOutput < 5 + jvm_argc) {
        error("jvm_argc too large");
        return ERROR_JVM_ARGC_TOO_LARGE;
    }

    const char **jvm_argv = (const char **)ptr;
    ptr += jvm_argc;
    for (size_t i = 0; i < jvm_argc; i++) {
        debug("jvm_argv[%zu] = %s", i, jvm_argv[i]);
    }

    const char *main_class_name = *ptr++;
    debug("main_class_name = %s", main_class_name);

    const int main_argc = atoi(*ptr++);
    debug("main_argc = %d", main_argc);
    if (main_argc < 0) {
        error("main_argc too small");
        return ERROR_MAIN_ARGC_TOO_SMALL;
    }
    if (numOutput < 5 + jvm_argc + main_argc) {
        error("main_argc too large");
        return ERROR_MAIN_ARGC_TOO_LARGE;
    }

    const char **main_argv = (const char **)ptr;
    ptr += main_argc;
    for (size_t i = 0; i < main_argc; i++) {
        debug("main_argv[%zu] = %s", i, main_argv[i]);
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
