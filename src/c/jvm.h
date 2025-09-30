#ifndef _JAUNCH_JVM_H
#define _JAUNCH_JVM_H

#include <stdlib.h>   // for NULL, size_t, atoi

#include "jni.h"      // for JavaVM, JNIEnv, JNI_CreateJavaVM, JNI_* constants

#include "logging.h"
#include "common.h"

// Global JVM state for reuse across multiple directives.
static JavaVM *cached_jvm = NULL;
static void *cached_jvm_library = NULL;

/*
 * This is the logic implementing Jaunch's JVM directive.
 *
 * It dynamically loads libjvm, calls JNI_CreateJavaVM with the given JVM args,
 * discovers the requested main class using FindClass, and invokes it with the
 * given main args using CallStaticVoidMethodA.
 *
 * For multiple JVM directives, the JVM instance is cached and reused.
 * The JVM is only destroyed when cleanup_jvm() is called at the end of all directives.
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
    LOG_INFO("JVM", "libjvm_path = %s", libjvm_path);

    const int jvm_argc = atoi(*ptr++);
    const char **jvm_argv = (const char **)ptr;
    CHECK_ARGS("JVM", "jvm", jvm_argc, 0, argc - 3, jvm_argv);
    ptr += jvm_argc;

    const char *main_class_name = *ptr++;
    LOG_INFO("JVM", "main_class_name = %s", main_class_name);

    const int main_argc = argc - 3 - jvm_argc;
    const char **main_argv = (const char **)ptr;
    CHECK_ARGS("JVM", "main", main_argc, 0, main_argc, main_argv);

    // =======================================================================
    // Load the JVM or reuse cached instance.
    // =======================================================================

    JavaVM *jvm;
    JNIEnv *env;
    void *jvm_library;

    if (cached_jvm == NULL) {
        // First JVM directive - create new JVM instance
        LOG_INFO("JVM", "Loading libjvm (first time)");
        jvm_library = lib_open(libjvm_path);
        if (!jvm_library) { LOG_ERROR("Failed to load libjvm: %s", lib_error()); return ERROR_DLOPEN; }

        // Load JNI_CreateJavaVM function.
        LOG_DEBUG("JVM", "Loading JNI_CreateJavaVM");
        static jint (*JNI_CreateJavaVM)(JavaVM **pvm, void **penv, void *args);
        JNI_CreateJavaVM = lib_sym(jvm_library, "JNI_CreateJavaVM");
        if (!JNI_CreateJavaVM) {
            LOG_ERROR("Failed to locate JNI_CreateJavaVM function: %s", lib_error());
            lib_close(jvm_library);
            return ERROR_DLSYM;
        }

        // Populate VM options.
        LOG_DEBUG("JVM", "Populating VM options");
        JavaVMOption vmOptions[jvm_argc + 1];
        for (size_t i = 0; i < jvm_argc; i++) {
            vmOptions[i].optionString = (char *)jvm_argv[i];
        }
        vmOptions[jvm_argc].optionString = NULL;

        // Populate VM init args.
        LOG_DEBUG("JVM", "Populating VM init args");
        JavaVMInitArgs vmInitArgs;
        vmInitArgs.version = JNI_VERSION_1_8;
        vmInitArgs.options = vmOptions;
        vmInitArgs.nOptions = jvm_argc;
        vmInitArgs.ignoreUnrecognized = JNI_FALSE;

        // Create the JVM.
        LOG_DEBUG("JVM", "Creating JVM");
        if (JNI_CreateJavaVM(&jvm, (void **)&env, &vmInitArgs) != JNI_OK) {
            LOG_ERROR("Failed to create the Java Virtual Machine");
            lib_close(jvm_library);
            return ERROR_CREATE_JAVA_VM;
        }

        // Cache the JVM instance for reuse
        cached_jvm = jvm;
        cached_jvm_library = jvm_library;
        LOG_INFO("JVM", "JVM created and cached for reuse");
    } else {
        // Subsequent JVM directive - reuse cached instance
        LOG_INFO("JVM", "Reusing cached JVM");
        jvm = cached_jvm;
        jvm_library = cached_jvm_library;

        // Attach current thread to existing JVM
        if ((*jvm)->AttachCurrentThread(jvm, (void **)&env, NULL) != JNI_OK) {
            LOG_ERROR("Failed to attach thread to cached JVM");
            return ERROR_CREATE_JAVA_VM;
        }

        // Note: JVM options from subsequent directives are ignored when reusing JVM
        if (jvm_argc > 0) {
            LOG_WARN("JVM", "JVM options ignored when reusing cached JVM instance");
        }
    }

    // Find the main class.
    LOG_DEBUG("JVM", "Finding main class");
    jclass mainClass = (*env)->FindClass(env, main_class_name);
    if (mainClass == NULL) {
        LOG_ERROR("Failed to locate class %s", main_class_name);
        (*jvm)->DestroyJavaVM(jvm);
        lib_close(jvm_library);
        return ERROR_FIND_CLASS;
    }

    // Find the main method.
    LOG_DEBUG("JVM", "Finding main method");
    jmethodID mainMethod = (*env)->GetStaticMethodID(env, mainClass, "main", "([Ljava/lang/String;)V");
    if (mainMethod == NULL) {
        LOG_ERROR("Failed to find main method of class %s", main_class_name);
        (*jvm)->DestroyJavaVM(jvm);
        lib_close(jvm_library);
        return ERROR_GET_STATIC_METHOD_ID;
    }

    // Populate main method arguments.
    LOG_DEBUG("JVM", "Populating main method arguments");
    jobjectArray javaArgs = (*env)->NewObjectArray(env, main_argc, (*env)->FindClass(env, "java/lang/String"), NULL);
    for (size_t i = 0; i < main_argc; i++) {
        (*env)->SetObjectArrayElement(env, javaArgs, i, (*env)->NewStringUTF(env, main_argv[i]));
    }

    // Invoke the main method.
    LOG_DEBUG("JVM", "Invoking main method");
    (*env)->CallStaticVoidMethodA(env, mainClass, mainMethod, (jvalue *)&javaArgs);

    LOG_DEBUG("JVM", "Detaching current thread");
    if ((*jvm)->DetachCurrentThread(jvm)) {
        LOG_ERROR("Could not detach current thread from JVM");
    }

    // =======================================================================
    // Clean up - but keep JVM alive for potential reuse
    // =======================================================================

    LOG_INFO("JVM", "JVM directive completed - keeping JVM alive for potential reuse");
    // JVM will be destroyed later in cleanup_jvm() when all directives are done

    return SUCCESS;
}

/*
 * Cleanup function to destroy the cached JVM instance when all directives are complete.
 * This should be called at the end of the directive processing loop.
 */
static void cleanup_jvm() {
    if (cached_jvm != NULL) {
        LOG_DEBUG("JVM", "Destroying cached JVM");
        (*cached_jvm)->DestroyJavaVM(cached_jvm);
        LOG_DEBUG("JVM", "Closing libjvm");
        lib_close(cached_jvm_library);
        cached_jvm = NULL;
        cached_jvm_library = NULL;
        LOG_INFO("JVM", "JVM cleanup complete");
    }
}

#endif
