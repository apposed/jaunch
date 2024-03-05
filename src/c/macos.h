#include <CoreFoundation/CoreFoundation.h>
//#include <objc/objc.h>
//#include <objc/NSObjCRuntime.h>
#include <pthread.h>

#include "common.h"

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

struct LaunchConfiguration {
    LaunchFunc launch_runtime;
    size_t argc;
    const char **argv;
};

static struct LaunchConfiguration config = {
    .launch_runtime = NULL,
    .argc = 0,
    .argv = NULL
};

static void dummy_call_back(void *info) { }

static void *launch_on_macos(void *dummy) {
    exit(config.launch_runtime(config.argc, config.argv));
}

/*
 * The macOS way of launching a runtime.
 *
 * It starts a new thread using pthread_create, which calls the launch function.
 * Meanwhile, on this thread (main), the CoreFoundation event loop is run.
 * All so that Java's AWT subsystem can work without freezing up the process.
 */
int launch(const LaunchFunc launch_runtime, const size_t argc, const char **argv) {
    // Save arguments into global struct, for later retrieval.
    config.launch_runtime = launch_runtime;
    config.argc = argc;
    config.argv = argv;

    // Call the launch function on a dedicated thread.
    pthread_t thread;
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setscope(&attr, PTHREAD_SCOPE_SYSTEM);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    pthread_create(&thread, &attr, launch_on_macos, NULL);
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
