#ifndef _JAUNCH_JVM_H
#define _JAUNCH_JVM_H

#include "jni.h"

#include "common.h"

/*
 * This is the logic implementing Jaunch's JVM directive.
 * 
 * It dynamically loads libjvm, calls JNI_CreateJavaVM with the given JVM args,
 * discovers the requested main class using FindClass, and invokes it with the
 * given main args using CallStaticVoidMethodA.
 */
static int launch_jvm(const size_t argc, const char **argv) {
    // =======================================================================
    // Parse the arguments, which must conform to the following structure:
    //
    // 1. Directive, which must be `JVM`.
    // 2. Path to the runtime native library (libjvm).
    // 3. Number of arguments to the JVM.
    // 4. List of arguments to the JVM, one per line.
    // 5. Fully qualified main class name in slash-separated (not dot-separated) format.
    // 6. Number of arguments to the main program.
    // 7. List of main arguments, one per line.
    // =======================================================================

    char **ptr = (char **)argv;
    const char *directive = *ptr++;
    debug("[JAUNCH-JVM] directive = %s", directive);
 
    const char *libjvm_path = *ptr++;
    debug("[JAUNCH-JVM] libjvm_path = %s", libjvm_path);

    const int jvm_argc = atoi(*ptr++);
    debug("[JAUNCH-JVM] jvm_argc = %d", jvm_argc);
    if (jvm_argc < 0) {
        error("jvm_argc value is too small: %d", jvm_argc);
        return ERROR_ARG_COUNT_TOO_SMALL;
    }
    if (argc < 5 + jvm_argc) {
        error("jvm_argc value is too large: %d", jvm_argc);
        return ERROR_ARG_COUNT_TOO_LARGE;
    }

    const char **jvm_argv = (const char **)ptr;
    ptr += jvm_argc;
    for (size_t i = 0; i < jvm_argc; i++) {
        debug("[JAUNCH-JVM] jvm_argv[%zu] = %s", i, jvm_argv[i]);
    }

    const char *main_class_name = *ptr++;
    debug("[JAUNCH-JVM] main_class_name = %s", main_class_name);

    const int main_argc = atoi(*ptr++);
    debug("[JAUNCH-JVM] main_argc = %d", main_argc);
    if (main_argc < 0) {
        error("main_argc value is too small: %d", main_argc);
        return ERROR_ARG_COUNT_TOO_SMALL;
    }
    if (argc < 5 + jvm_argc + main_argc) {
        error("main_argc value is too large: %d", main_argc);
        return ERROR_ARG_COUNT_TOO_LARGE;
    }

    const char **main_argv = (const char **)ptr;
    ptr += main_argc;
    for (size_t i = 0; i < main_argc; i++) {
        debug("[JAUNCH-JVM] main_argv[%zu] = %s", i, main_argv[i]);
    }

    // =======================================================================
    // Load the JVM.
    // =======================================================================

    // Load libjvm.
    debug("[JAUNCH-JVM] LOADING LIBJVM");
    void *jvm_library = dlopen(libjvm_path);
    if (!jvm_library) { error("Error loading libjvm: %s", dlerror()); return ERROR_DLOPEN; }

    // Load JNI_CreateJavaVM function.
    debug("[JAUNCH-JVM] LOADING JNI_CreateJavaVM");
    static jint (*JNI_CreateJavaVM)(JavaVM **pvm, void **penv, void *args);
    JNI_CreateJavaVM = dlsym(jvm_library, "JNI_CreateJavaVM");
    if (!JNI_CreateJavaVM) {
        error("Error finding JNI_CreateJavaVM: %s", dlerror());
        dlclose(jvm_library);
        return ERROR_DLSYM;
    }

    // Populate VM options.
    debug("[JAUNCH-JVM] POPULATING VM OPTIONS");
    JavaVMOption vmOptions[jvm_argc + 1];
    for (size_t i = 0; i < jvm_argc; i++) {
        vmOptions[i].optionString = (char *)jvm_argv[i];
    }
    vmOptions[jvm_argc].optionString = NULL;

    // Populate VM init args.
    debug("[JAUNCH-JVM] POPULATING VM INIT ARGS");
    JavaVMInitArgs vmInitArgs;
    vmInitArgs.version = JNI_VERSION_1_8;
    vmInitArgs.options = vmOptions;
    vmInitArgs.nOptions = jvm_argc;
    vmInitArgs.ignoreUnrecognized = JNI_FALSE;

    // Create the JVM.
    debug("[JAUNCH-JVM] CREATING JVM");
    JavaVM *jvm;
    JNIEnv *env;
    if (JNI_CreateJavaVM(&jvm, (void **)&env, &vmInitArgs) != JNI_OK) {
        error("Error creating Java Virtual Machine");
        dlclose(jvm_library);
        return ERROR_CREATE_JAVA_VM;
    }

    // Find the main class.
    debug("[JAUNCH-JVM] FINDING MAIN CLASS");
    jclass mainClass = (*env)->FindClass(env, main_class_name);
    if (mainClass == NULL) {
        error("Error finding class %s", main_class_name);
        (*jvm)->DestroyJavaVM(jvm);
        dlclose(jvm_library);
        return ERROR_FIND_CLASS;
    }

    // Find the main method.
    debug("[JAUNCH-JVM] FINDING MAIN METHOD");
    jmethodID mainMethod = (*env)->GetStaticMethodID(env, mainClass, "main", "([Ljava/lang/String;)V");
    if (mainMethod == NULL) {
        error("Error finding main method of class %s", main_class_name);
        (*jvm)->DestroyJavaVM(jvm);
        dlclose(jvm_library);
        return ERROR_GET_STATIC_METHOD_ID;
    }

    // Populate main method arguments.
    debug("[JAUNCH-JVM] FINDING MAIN METHOD ARGUMENTS");
    jobjectArray javaArgs = (*env)->NewObjectArray(env, main_argc, (*env)->FindClass(env, "java/lang/String"), NULL);
    for (size_t i = 0; i < main_argc; i++) {
        (*env)->SetObjectArrayElement(env, javaArgs, i, (*env)->NewStringUTF(env, main_argv[i]));
    }

    // Invoke the main method.
    debug("[JAUNCH-JVM] INVOKING MAIN METHOD");
    (*env)->CallStaticVoidMethodA(env, mainClass, mainMethod, (jvalue *)&javaArgs);

    debug("[JAUNCH-JVM] DETACHING CURRENT THREAD");
    if ((*jvm)->DetachCurrentThread(jvm)) {
        error("Could not detach current thread from JVM");
    }

    // =======================================================================
    // Clean up.
    // =======================================================================

    debug("[JAUNCH-JVM] DESTROYING JAVA VM");
    (*jvm)->DestroyJavaVM(jvm);
    debug("[JAUNCH-JVM] CLOSING LIBJVM");
    dlclose(jvm_library);
    debug("[JAUNCH-JVM] GOODBYE");

    return SUCCESS;
}

#endif
