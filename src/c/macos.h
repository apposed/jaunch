#include <CoreFoundation/CoreFoundation.h>
//#include <objc/objc.h>
//#include <objc/NSObjCRuntime.h>
#include <pthread.h>

#define OS_NAME "macos"

void show_alert(const char *title, const char *message) {
	/* TODO: Get this objc code working.
	// Create an NSString from the C string
	id nsMessage = objc_msgSend((id)objc_getClass("NSString"), sel_registerName("stringWithUTF8String:"), message);

	// Create an NSAlert
	id alert = objc_msgSend((id)objc_getClass("NSAlert"), sel_registerName("alloc"));
	objc_msgSend(alert, sel_registerName("init"));
	objc_msgSend(alert, sel_registerName("setMessageText:"), nsMessage);

	// Run the alert modal
	objc_msgSend(alert, sel_registerName("runModal"));
	*/
}

struct JVMConfiguration {
	const char *libjvm_path;
	size_t jvm_argc;
	const char **jvm_argv;
	const char *main_class_name;
	size_t main_argc;
	const char **main_argv;
};

static struct JVMConfiguration config = {
	.libjvm_path = NULL,
	.jvm_argc = 0,
	.jvm_argv = NULL,
	.main_class_name = NULL,
	.main_argc = 0,
	.main_argv = NULL
};

static void dummy_call_back(void *info) { }

static void *startup_jvm_macos(void *dummy) {
	exit(launch_jvm(
		config.libjvm_path, config.jvm_argc, config.jvm_argv,
		config.main_class_name, config.main_argc, config.main_argv
	));
}

int startup_jvm(
	const char *libjvm_path, const size_t jvm_argc, const char *jvm_argv[],
	const char *main_class_name, const size_t main_argc, const char *main_argv[])
{
	// Save arguments into global struct, for later retrieval.
	config.libjvm_path = libjvm_path;
	config.jvm_argc = jvm_argc;
	config.jvm_argv = jvm_argv;
	config.main_class_name = main_class_name;
	config.main_argc = main_argc;
	config.main_argv = main_argv;

	// Start the JVM on a dedicated thread.
	pthread_t thread;
	pthread_attr_t attr;
	pthread_attr_init(&attr);
	pthread_attr_setscope(&attr, PTHREAD_SCOPE_SYSTEM);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
	pthread_create(&thread, &attr, startup_jvm_macos, NULL);
	pthread_attr_destroy(&attr);

	// Run the AppKit event loop here on the main thread.
	CFRunLoopSourceContext context;
	memset(&context, 0, sizeof(context));
	context.perform = &dummy_call_back;

	CFRunLoopSourceRef ref = CFRunLoopSourceCreate(NULL, 0, &context);
	CFRunLoopAddSource (CFRunLoopGetCurrent(), ref, kCFRunLoopCommonModes);
	CFRunLoopRun();

	return 0;
}
