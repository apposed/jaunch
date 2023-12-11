/*
 * A minimal but general-purpose C program to launch Java in the same process.
 *
 * It has two functions:
 *
 * launch_jvm:
 *   1. path to libjvm
 *   2. argc + argv for the jvm
 *   3. main class to run
 *   4. argc + argv for the main invocation
 * And it actually loads that libjvm and invokes the main function with those parameters.
 *
 * config_and_launch:
 *	 1. path to configurator executable
 *	 2. argc + argv to pass to the configurator
 * And it invokes that configurator executable with those arguments in its own process,
 * and waits for the process to complete. The configurator produces output suitable for
 * passing to the `launch_jvm` function above. and then calls the low-level function with the
 * outputs.
 */

#include <stdio.h>
#include <dlfcn.h>

#include "jni.h"

#define ERROR_DLOPEN 1
#define ERROR_DLSYM 2
#define ERROR_CREATE_JAVA_VM 3
#define ERROR_FIND_CLASS 4
#define ERROR_GET_STATIC_METHOD_ID 5

int launch_jvm(const char *libjvm_path, const int jvm_argc, const char *jvm_argv[],
	const char *main_class_name, const int main_argc, const char *main_argv[])
{
	int i;

	// Load libjvm.
	printf("LOADING LIBJVM\n");
	void *jvm_library = dlopen(libjvm_path, RTLD_NOW | RTLD_GLOBAL);
	if (!jvm_library) {
		fprintf(stderr, "Error loading libjvm: %s\n", dlerror());
		return ERROR_DLOPEN;
	}

	// Load JNI_CreateJavaVM function.
	printf("LOADING JNI_CreateJavaVM\n");
	static jint (*JNI_CreateJavaVM)(JavaVM **pvm, void **penv, void *args);
	JNI_CreateJavaVM = dlsym(jvm_library, "JNI_CreateJavaVM");
	if (!JNI_CreateJavaVM) {
		fprintf(stderr, "Error finding JNI_CreateJavaVM: %s\n", dlerror());
		dlclose(jvm_library);
		return ERROR_DLSYM;
	}

	// Populate VM options.
	printf("POPULATING VM OPTIONS\n");
	JavaVMOption vmOptions[jvm_argc + 1];
	for (i = 0; i < jvm_argc; i++) {
		vmOptions[i].optionString = (char *)jvm_argv[i];
	}
	vmOptions[jvm_argc].optionString = NULL;

	// Populate VM init args.
	printf("POPULATING VM INIT ARGS\n");
	JavaVMInitArgs vmInitArgs;
	vmInitArgs.version = JNI_VERSION_1_8;
	vmInitArgs.options = vmOptions;
	vmInitArgs.nOptions = jvm_argc;
	vmInitArgs.ignoreUnrecognized = JNI_FALSE;

	// Create the JVM.
	printf("CREATING JVM\n");
	JavaVM *jvm;
	JNIEnv *env;
	if (JNI_CreateJavaVM(&jvm, (void **)&env, &vmInitArgs) != JNI_OK) {
		fprintf(stderr, "Error creating Java VM\n");
		dlclose(jvm_library);
		return ERROR_CREATE_JAVA_VM;
	}

	// Find the main class.
	printf("FINDING MAIN CLASS\n");
	jclass mainClass = (*env)->FindClass(env, main_class_name);
	if (mainClass == NULL) {
		fprintf(stderr, "Error finding class %s\n", main_class_name);
		(*jvm)->DestroyJavaVM(jvm);
		dlclose(jvm_library);
		return ERROR_FIND_CLASS;
	}

	// Find the main method.
	printf("FINDING MAIN METHOD\n");
	jmethodID mainMethod = (*env)->GetStaticMethodID(env, mainClass, "main", "([Ljava/lang/String;)V");
	if (mainMethod == NULL) {
		fprintf(stderr, "Error finding main method\n");
		(*jvm)->DestroyJavaVM(jvm);
		dlclose(jvm_library);
		return ERROR_GET_STATIC_METHOD_ID;
	}

	// Populate main method arguments.
	printf("FINDING MAIN METHOD ARGUMENTS\n");
	jobjectArray javaArgs = (*env)->NewObjectArray(env, main_argc, (*env)->FindClass(env, "java/lang/String"), NULL);
	for (i = 0; i < main_argc; i++) {
		(*env)->SetObjectArrayElement(env, javaArgs, i, (*env)->NewStringUTF(env, main_argv[i]));
	}

	// Invoke the main method.
	printf("INVOKING MAIN METHOD\n");
	(*env)->CallStaticVoidMethodA(env, mainClass, mainMethod, (jvalue *)&javaArgs);

	printf("DETACHING CURRENT THREAD\n");
	if ((*jvm)->DetachCurrentThread(jvm)) {
		fprintf(stderr, "Could not detach current thread");
	}

	// Clean up.
	printf("DESTROYING JAVA VM\n");
	(*jvm)->DestroyJavaVM(jvm);
	printf("CLOSING LIBJVM\n");
	dlclose(jvm_library);
	printf("GOODBYE\n");
}

int main(const int argc, const char *argv[]) {
	return 0;
}
