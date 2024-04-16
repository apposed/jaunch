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
    // 1. Path to the runtime native library (libjvm).
    // 2. Number of arguments to the JVM.
    // 3. List of arguments to the JVM, one per line.
    // 4. Fully qualified main class name in slash-separated (not dot-separated) format.
    // 5. List of main arguments, one per line.
    //
    // Note that an explicit count of main arguments is not needed because
    // it can be computed from argc
    // =======================================================================

    char **ptr = (char **)argv;
 
    const char *libjvm_path = *ptr++;
    debug("[JAUNCH-JVM] libjvm_path = %s", libjvm_path);

    const int jvm_argc = atoi(*ptr++);
    const char **jvm_argv = (const char **)ptr;
    CHECK_ARGS("JAUNCH-JVM", "jvm", jvm_argc, 0, argc - 3, jvm_argv);
    ptr += jvm_argc;

    const char *main_class_name = *ptr++;
    debug("[JAUNCH-JVM] main_class_name = %s", main_class_name);

    const int main_argc = argc - 3 - jvm_argc;
    const char **main_argv = (const char **)ptr;
    CHECK_ARGS("JAUNCH-JVM", "main", main_argc, 0, main_argc, main_argv);

    // =======================================================================
    // Load the JVM.
    // =======================================================================

    // Load libjvm.
    debug("[JAUNCH-JVM] LOADING LIBJVM");
    void *jvm_library = lib_open(libjvm_path);
    if (!jvm_library) { error("Error loading libjvm: %s", lib_error()); return ERROR_DLOPEN; }

    // Load JNI_CreateJavaVM function.
    debug("[JAUNCH-JVM] LOADING JNI_CreateJavaVM");
    static jint (*JNI_CreateJavaVM)(JavaVM **pvm, void **penv, void *args);
    JNI_CreateJavaVM = lib_sym(jvm_library, "JNI_CreateJavaVM");
    if (!JNI_CreateJavaVM) {
        error("Error finding JNI_CreateJavaVM: %s", lib_error());
        lib_close(jvm_library);
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
        lib_close(jvm_library);
        return ERROR_CREATE_JAVA_VM;
    }

    // Find the main class.
    debug("[JAUNCH-JVM] FINDING MAIN CLASS");
    jclass mainClass = (*env)->FindClass(env, main_class_name);
    if (mainClass == NULL) {
        error("Error finding class %s", main_class_name);
        (*jvm)->DestroyJavaVM(jvm);
        lib_close(jvm_library);
        return ERROR_FIND_CLASS;
    }

    // Find the main method.
    debug("[JAUNCH-JVM] FINDING MAIN METHOD");
    jmethodID mainMethod = (*env)->GetStaticMethodID(env, mainClass, "main", "([Ljava/lang/String;)V");
    if (mainMethod == NULL) {
        error("Error finding main method of class %s", main_class_name);
        (*jvm)->DestroyJavaVM(jvm);
        lib_close(jvm_library);
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
    lib_close(jvm_library);
    debug("[JAUNCH-JVM] GOODBYE");

    return SUCCESS;
}

#endif
