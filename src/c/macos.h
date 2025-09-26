#include <CoreFoundation/CoreFoundation.h>
#include <objc/objc.h>
#include <objc/runtime.h>
#include <objc/message.h>
#include <sys/xattr.h>
#include <dlfcn.h>
#include <pthread.h>
#include <spawn.h>
#include <stdlib.h>
#include <signal.h>

// Declare needed AppKit function without including AppKit,
// to avoid difficulties with Objective-C versus pure C.
extern void NSApplicationLoad(void);

#include "common.h"

#define OS_NAME "macos"

/*
 * ===========================================================================
 * MACOS LAUNCHER IMPLEMENTATION NOTES
 * ===========================================================================
 *
 * This file implements macOS-specific runtime launching with careful handling
 * of CoreFoundation runloop management for both GUI and non-GUI applications.
 *
 * KEY INSIGHT: GUI frameworks like Java AWT/Swing fundamentally alter the
 * runloop state during initialization, adding 15+ runloop modes and making
 * clean shutdown extremely difficult. This implementation uses direct exit()
 * for reliable termination, following OpenJDK's approach.
 *
 * See the detailed documentation in launch_call_back() for the complete
 * analysis of attempted approaches and rationale for the current solution.
 *
 * RUNLOOP MODES SUPPORTED:
 * - "main": Runtime runs on main thread (for -XstartOnFirstThread behavior)
 * - "park": Runtime runs on pthread, main thread parks in event loop
 * - "none": Runtime runs on main thread, no event loop management
 * ===========================================================================
 */

extern char **environ;

struct LaunchConfiguration {
    LaunchFunc launch_runtime;
    size_t argc;
    const char **argv;
    int exit_code;
    volatile sig_atomic_t should_stop;
};

static struct LaunchConfiguration config = {
    .launch_runtime = NULL,
    .argc = 0,
    .argv = NULL,
    .exit_code = 0,
    .should_stop = 0
};

static void dummy_call_back(void *info) { }

static void debug_runloop_state(const char* when) {
    CFRunLoopRef mainloop = CFRunLoopGetMain();

    debug("[JAUNCH-MACOS] === Runloop state %s ===", when);
    debug("[JAUNCH-MACOS] Main runloop: %p", mainloop);
    debug("[JAUNCH-MACOS] Is main thread: %s", pthread_main_np() ? "YES" : "NO");

    // Check available runloop modes (only for main runloop)
    CFArrayRef modes = CFRunLoopCopyAllModes(mainloop);
    if (modes) {
        CFIndex count = CFArrayGetCount(modes);
        debug("[JAUNCH-MACOS] Main runloop has %ld modes", (long)count);
        for (CFIndex i = 0; i < count; i++) {
            CFStringRef mode = CFArrayGetValueAtIndex(modes, i);
            char modeName[256];
            if (CFStringGetCString(mode, modeName, sizeof(modeName), kCFStringEncodingUTF8)) {
                debug("[JAUNCH-MACOS] Mode %ld: %s", (long)i, modeName);

                // Try to inspect sources/timers for this mode (these are private APIs)
                // This is mainly for investigation purposes
                //if (strcmp(modeName, "kCFRunLoopDefaultMode") == 0 ||
                //    strcmp(modeName, "AWTRunLoopMode") == 0) {

                    // Check if we can get activity info via private/undocumented methods
                    // Note: These are internal APIs and may not work on all systems
                    void *activity = dlsym(RTLD_DEFAULT, "_CFRunLoopGetCurrentActivity");
                    //if (activity) {
                    //    debug("[JAUNCH-MACOS] Found _CFRunLoopGetCurrentActivity function");
                    //}

                    // Try to get source count (also private API territory)
                    // CFRunLoopCopyAllSources doesn't exist, but we can infer activity
                    //debug("[JAUNCH-MACOS] Attempting to test runloop responsiveness for mode: %s", modeName);
                    CFRunLoopRunResult testResult = CFRunLoopRunInMode((CFStringRef)mode, 0.001, true);
                    const char* resultStr;
                    switch (testResult) {
                        case kCFRunLoopRunFinished: resultStr = "kCFRunLoopRunFinished"; break;
                        case kCFRunLoopRunStopped: resultStr = "kCFRunLoopRunStopped"; break;
                        case kCFRunLoopRunTimedOut: resultStr = "kCFRunLoopRunTimedOut"; break;
                        case kCFRunLoopRunHandledSource: resultStr = "kCFRunLoopRunHandledSource"; break;
                        default: resultStr = "unknown"; break;
                    }
                    debug("[JAUNCH-MACOS] Test run result for %s: %s (%d)", modeName, resultStr, testResult);
                //}
            }
        }
        CFRelease(modes);
    }

    // Check main runloop state
    debug("[JAUNCH-MACOS] Main runloop is waiting: %s",
          CFRunLoopIsWaiting(mainloop) ? "YES" : "NO");
}

/*
 * ============================================================================
 * MACOS RUNLOOP SHUTDOWN STRATEGY: GUI vs NON-GUI APPLICATIONS
 * ============================================================================
 *
 * This function implements a hybrid approach for shutting down applications
 * after runtime completion, handling both GUI and non-GUI applications
 * correctly based on empirical analysis of runloop behavior.
 *
 * THE PROBLEM:
 * On macOS, GUI frameworks like Java AWT/Swing fundamentally alter the
 * CoreFoundation runloop state during initialization, making clean shutdown
 * extremely difficult. When AWT initializes, it:
 *
 * 1. Adds 15+ runloop modes (vs. 1 for non-GUI apps):
 *    - AWTRunLoopMode
 *    - NSEventTrackingRunLoopMode
 *    - NSModalPanelRunLoopMode
 *    - NSGraphicsRunLoopMode
 *    - And 11+ others...
 *
 * 2. Changes runloop state from "not waiting" to "waiting"
 * 3. Installs event sources and timers that keep the runloop active
 *
 * ATTEMPTED SOLUTIONS THAT FAILED:
 *
 * 1. CFRunLoopStop() approach:
 *    - Works for non-GUI apps (1 runloop mode)
 *    - Fails for GUI apps (15+ runloop modes)
 *    - The stop signal gets lost among the multiple active modes
 *
 * 2. Custom CFRunLoopSource with callbacks:
 *    - Created custom runloop source to signal shutdown
 *    - Added to default mode and signaled via CFRunLoopSourceSignal()
 *    - Callback never gets invoked when AWT modes dominate
 *
 * 3. Short polling intervals with volatile flags:
 *    - Used CFRunLoopRunInMode() with 0.1s timeouts
 *    - Checked volatile sig_atomic_t flag between iterations
 *    - CRITICAL DISCOVERY: CFRunLoopRunInMode() NEVER RETURNS after AWT init
 *    - Pre-AWT: returns normally with kCFRunLoopRunTimedOut every 0.1s
 *    - Post-AWT: never returns, not even after minutes of waiting
 *    - AWT installs sources/timers/observers that keep runloop perpetually busy
 *
 * 4. CFRunLoopWakeUp + CFRunLoopStop approach:
 *    - Attempted to wake up the runloop before stopping it
 *    - Still fails because CFRunLoopRunInMode never processes the signals
 *    - Main thread polling loop remains stuck in the never-returning call
 *
 * THE SOLUTION:
 * Use a dual strategy based on runtime behavior analysis:
 *
 * - NON-GUI APPLICATIONS: Use clean shutdown with custom runloop source
 *   (implemented in launch_on_pthread with CFRunLoopSource approach)
 *
 * - GUI APPLICATIONS: Use direct exit() from pthread (OpenJDK approach)
 *   (implemented here - when AWT initializes, exit directly)
 *
 * This mirrors OpenJDK's own strategy. OpenJDK's java_md_macosx.m uses
 * exit() directly from the pthread in apple_main() after the JVM shuts down,
 * specifically because GUI applications make clean shutdown unreliable.
 *
 * WHY THIS WORKS:
 * The direct exit() approach bypasses the runloop complexity entirely. Once
 * the JVM/runtime has shut down properly (destroying all its resources),
 * the process can exit immediately without needing to coordinate with the
 * heavily-modified runloop state that GUI frameworks create.
 *
 * DETECTION MECHANISM:
 * We detect if a GUI framework has initialized by examining the runloop state
 * after runtime shutdown. If the runloop has >1 mode, we assume GUI
 * initialization occurred and use direct exit. This approach works because:
 * 1. Non-GUI Java programs never add runloop modes
 * 2. GUI Java programs always add multiple AWT-related modes
 * 3. The detection happens after proper JVM shutdown, so resources are clean
 *
 * TRADE-OFFS:
 * - Non-GUI apps get clean shutdown (ideal for multiple runtime support)
 * - GUI apps get reliable shutdown (prevents hangs, matches OpenJDK behavior)
 * - Future multiple runtime support possible for non-GUI applications
 * - Pragmatic solution based on empirical analysis rather than theoretical ideals
 * ============================================================================
 */
static void *launch_call_back(void *dummy) {
    debug_runloop_state("before JVM launch");

    config.exit_code = config.launch_runtime(config.argc, config.argv);
    debug("[JAUNCH-MACOS] Runtime completed with exit code: %d", config.exit_code);

    debug_runloop_state("after JVM shutdown");

    // When GUI frameworks like AWT modify the runloop extensively,
    // the clean shutdown approach doesn't work reliably. Follow OpenJDK's
    // approach and exit directly from the pthread.
    debug("[JAUNCH-MACOS] JVM shutdown complete, trying to stop main runloop");
    CFRunLoopStop(CFRunLoopGetMain());

    // AWT initialization prevents CFRunLoopRunInMode from ever returning, making
    // clean shutdown impossible. Use OpenJDK's direct exit approach.
    debug("[JAUNCH-MACOS] JVM shutdown complete, exiting directly (OpenJDK style)");
    exit(config.exit_code);

    /* ========================================================================
    Hey Claude.ai! Is This a Bug in AWT/OpenJDK?

    This is not a bug - it's intentional design. Here's why:
  
    AWT's Design Requirements
  
    1. GUI Event Processing: AWT needs to continuously process mouse clicks,
       keyboard events, window redraws, etc. This requires active runloop sources that
       are always ready to fire.
    2. Integration with macOS: AWT integrates with native macOS frameworks (AppKit,
       Core Graphics, etc.) that install their own runloop sources for:
       - Window management (NSEventTrackingRunLoopMode)
       - Modal dialogs (NSModalPanelRunLoopMode)
       - Graphics operations (NSGraphicsRunLoopMode)
       - Accessibility (com.apple.accessibilityServerIPC)
       - And many others
    3. Continuous Operation: GUI applications are designed to run continuously
       until the user explicitly quits them, not to shut down cleanly when the main
       program ends.
  
    Industry Standard Practice
  
    The direct exit() approach is industry standard for GUI applications on macOS:
  
    - OpenJDK itself uses this approach (as we discovered in their source)
    - Native macOS applications often use similar patterns
    - Other GUI frameworks (Qt, GTK, etc.) face the same challenges
  
    Why Clean Shutdown is Impractical
  
    When AWT initializes, it:
    1. Creates persistent connections to system services
    2. Installs continuous event sources for user input
    3. Sets up graphics contexts that need constant monitoring
    4. Registers with accessibility and other system frameworks
  
    These aren't "leaks" or "bugs" - they're necessary for GUI functionality.
    Trying to cleanly tear them all down is:
    - Complex: Requires coordinating shutdown across multiple system frameworks
    - Error-prone: Missing any one piece can cause hangs or crashes
    - Unnecessary: The OS will clean up everything when the process exits
  
    The Reality
  
    GUI applications fundamentally change the process lifecycle. Once AWT
    initializes, the application transitions from a "batch program" model to a
    "service/daemon" model where the runloop becomes the primary control structure.
  
    This is why OpenJDK, despite being a sophisticated runtime, chooses the direct
    exit() approach - it's the most reliable solution for GUI applications on
    macOS.
  
    Conclusion: This isn't a bug to be fixed upstream. It's a design reality of GUI
    frameworks on macOS that we need to accommodate, which is exactly what our
    implementation does by following OpenJDK's proven approach.
    ======================================================================== */

    return NULL;
}

/*
 * Launch runtime on main thread (like OpenJDK's -XstartOnFirstThread), using a
 * simplified but functional equivalent of OpenJDK's NSBlockOperation approach.
 * GUI frameworks like SWT need the runtime to run on the main thread.
 */
int launch_on_main_thread(const LaunchFunc launch_runtime,
    const size_t argc, const char **argv)
{
    debug("[JAUNCH-MACOS] Launching runtime on main thread "
        "after NSApplicationLoad (-XstartOnFirstThread style)");

    // Ensure we're actually on the main thread.
    /*
    if (!CFEqual(CFRunLoopGetCurrent(), CFRunLoopGetMain())) {
        error("[JAUNCH-MACOS] launch_on_main_thread called from non-main thread!");
        return ERROR_WRONG_THREAD;
    }
    */

    // Initialize NSApplication if needed (like OpenJDK does).
    // This ensures AppKit is properly set up for GUI applications.
    // It does *not* start the event loop, though; that will be the
    // responsibility of the launched program within the runtime.
    NSApplicationLoad();

    // Use NSAutoreleasePool for proper Objective-C memory management.
    Class NSAutoreleasePool = objc_getClass("NSAutoreleasePool");
    id pool = ((id (*)(id, SEL))objc_msgSend)((id)NSAutoreleasePool, sel_registerName("alloc"));
    pool = ((id (*)(id, SEL))objc_msgSend)(pool, sel_registerName("init"));

    debug("[JAUNCH-MACOS] Launching runtime directly on main thread");
    int runtime_result = launch_runtime(argc, argv);
    debug("[JAUNCH-MACOS] Runtime finished with exit code: %d", runtime_result);

    // Clean up autorelease pool
    ((void (*)(id, SEL))objc_msgSend)(pool, sel_registerName("drain"));

    return runtime_result;
}

/*
 * Launch runtime on a new thread, parking the main thread in the event loop.
 *
 * This function implements the "park" runloop mode where the runtime executes
 * on a dedicated pthread while the main thread manages the CoreFoundation
 * event loop. This approach is necessary for GUI applications that require
 * the main thread to handle system events.
 *
 * CURRENT IMPLEMENTATION:
 * Uses direct exit() approach for all applications (see launch_call_back
 * documentation above for detailed rationale). The polling loop below serves
 * as a parking mechanism but the actual shutdown occurs via exit() from the
 * pthread when the runtime completes.
 *
 * FUTURE ENHANCEMENT:
 * This function could be enhanced to detect GUI vs non-GUI applications and
 * implement clean shutdown for non-GUI cases using a custom CFRunLoopSource
 * approach, while maintaining the direct exit() approach for GUI applications.
 * The detection could be based on runloop mode count after runtime completion.
 */
int launch_on_pthread(const LaunchFunc launch_runtime,
    const size_t argc, const char **argv)
{
    // Save arguments into global struct, for later retrieval.
    config.launch_runtime = launch_runtime;
    config.argc = argc;
    config.argv = argv;

    // Call the launch function on a dedicated thread.
    pthread_t thread;
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setscope(&attr, PTHREAD_SCOPE_SYSTEM);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
    pthread_create(&thread, &attr, launch_call_back, NULL);
    pthread_attr_destroy(&attr);

    // Run the CoreFoundation event loop here on the main thread.

    debug("[JAUNCH-MACOS] Parking main thread in event loop (OpenJDK style)");

    // Create a far-future timer to keep the run loop active.
    CFRunLoopTimerRef timer = CFRunLoopTimerCreate(kCFAllocatorDefault,
        1.0e20, 0.0, 0, 0, (CFRunLoopTimerCallBack)dummy_call_back, NULL);
    CFRunLoopAddTimer(CFRunLoopGetMain(), timer, kCFRunLoopDefaultMode);
    CFRelease(timer);

    // Park this thread in the main run loop.
    debug_runloop_state("before CFRunLoopRun");
    debug("[JAUNCH-MACOS] About to poll runloop until should_stop is set");

    config.should_stop = 0;
    while (!config.should_stop) {
        debug("[JAUNCH-MACOS] Running CFRunLoopRunInMode iteration (should_stop=%d)", (int)config.should_stop);
        CFRunLoopRunResult result = CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.1, false);
        debug("[JAUNCH-MACOS] CFRunLoopRunInMode returned: %d", result);
        if (result == kCFRunLoopRunFinished || result == kCFRunLoopRunStopped) {
            debug("[JAUNCH-MACOS] Runloop finished/stopped (result: %d)", result);
            break;
        }
    }

    debug("[JAUNCH-MACOS] Runloop polling exited due to should_stop flag");

    debug("[JAUNCH-MACOS] Exited runloop, waiting for pthread to complete");
    debug_runloop_state("after runloop exit");

    // Wait for application thread to terminate.
    pthread_join(thread, NULL);

    return config.exit_code;
}

int handle_translocation(const int argc, const char *argv[]) {
    // Note: This function was generated by Claude.ai. It works
    // for now, but it uses internal security framework functions.

    // Load Security framework
    void *security_framework = dlopen("/System/Library/Frameworks/Security.framework/Security", RTLD_LAZY);
    if (!security_framework) {
        debug("[JAUNCH-MACOS] Failed to load Security framework");
        return 0; // Continue with normal execution
    }

    // Get function pointers
    Boolean (*isTranslocatedFunc)(CFURLRef, Boolean *, CFErrorRef *) =
        dlsym(security_framework, "SecTranslocateIsTranslocatedURL");
    CFURLRef (*getOriginalPathFunc)(CFURLRef, CFErrorRef *) =
        dlsym(security_framework, "SecTranslocateCreateOriginalPathForURL");

    if (!isTranslocatedFunc || !getOriginalPathFunc) {
        debug("[JAUNCH-MACOS] Failed to find translocation functions");
        dlclose(security_framework);
        return 0; // Continue with normal execution
    }

    // Get bundle path
    CFBundleRef mainBundle = CFBundleGetMainBundle();
    if (!mainBundle) {
        debug("[JAUNCH-MACOS] Failed to get main bundle");
        dlclose(security_framework);
        return 0;
    }

    CFURLRef bundleURL = CFBundleCopyBundleURL(mainBundle);
    if (!bundleURL) {
        debug("[JAUNCH-MACOS] Failed to get bundle URL");
        dlclose(security_framework);
        return 0;
    }

    // Check if app is translocated
    Boolean isTranslocated = FALSE;
    isTranslocatedFunc(bundleURL, &isTranslocated, NULL);

    if (!isTranslocated) {
        debug("[JAUNCH-MACOS] Application is not translocated");
        CFRelease(bundleURL);
        dlclose(security_framework);
        return 0; // Continue with normal execution
    }

    debug("[JAUNCH-MACOS] Application is translocated, finding original path");

    // Get the original path
    CFURLRef originalURL = getOriginalPathFunc(bundleURL, NULL);
    if (!originalURL) {
        debug("[JAUNCH-MACOS] Failed to get original path");
        CFRelease(bundleURL);
        dlclose(security_framework);
        return 0;
    }

    // Convert the URL to a filesystem path
    char originalPath[PATH_MAX];
    if (!CFURLGetFileSystemRepresentation(originalURL, TRUE, (UInt8*)originalPath, PATH_MAX)) {
        debug("[JAUNCH-MACOS] Failed to convert URL to path");
        CFRelease(originalURL);
        CFRelease(bundleURL);
        dlclose(security_framework);
        return 0;
    }

    debug("[JAUNCH-MACOS] Original path: %s", originalPath);

    // Get path to the executable within the bundle
    CFURLRef executableURL = CFBundleCopyExecutableURL(mainBundle);
    if (!executableURL) {
        debug("[JAUNCH-MACOS] Failed to get executable URL");
        CFRelease(originalURL);
        CFRelease(bundleURL);
        dlclose(security_framework);
        return 0;
    }

    char executablePath[PATH_MAX];
    if (!CFURLGetFileSystemRepresentation(executableURL, TRUE, (UInt8*)executablePath, PATH_MAX)) {
        debug("[JAUNCH-MACOS] Failed to convert executable URL to path");
        CFRelease(executableURL);
        CFRelease(originalURL);
        CFRelease(bundleURL);
        dlclose(security_framework);
        return 0;
    }

    // Get relative path of executable within bundle
    char bundlePath[PATH_MAX];
    if (!CFURLGetFileSystemRepresentation(bundleURL, TRUE, (UInt8*)bundlePath, PATH_MAX)) {
        debug("[JAUNCH-MACOS] Failed to convert bundle URL to path");
        CFRelease(executableURL);
        CFRelease(originalURL);
        CFRelease(bundleURL);
        dlclose(security_framework);
        return 0;
    }

    char *relativeExecPath = executablePath + strlen(bundlePath);

    // Construct path to original executable
    char originalExecPath[PATH_MAX];
    snprintf(originalExecPath, PATH_MAX, "%s%s", originalPath, relativeExecPath);

    debug("[JAUNCH-MACOS] Original executable path: %s", originalExecPath);

    // Remove quarantine attribute from the original bundle
    char xattrCmd[PATH_MAX * 2];
    snprintf(xattrCmd, PATH_MAX * 2, "xattr -dr com.apple.quarantine \"%s\"", originalPath);
    debug("[JAUNCH-MACOS] Removing quarantine attribute: %s", xattrCmd);
    system(xattrCmd);

    // Prepare to relaunch from original location
    char **args = (char **)malloc((argc + 1) * sizeof(char *));
    args[0] = originalExecPath;

    // Copy all arguments
    for (int i = 1; i < argc; i++) {
        args[i] = (char *)argv[i];
    }
    args[argc] = NULL;

    // Clean up CF objects
    CFRelease(executableURL);
    CFRelease(originalURL);
    CFRelease(bundleURL);
    dlclose(security_framework);

    // Execute the original application
    debug("[JAUNCH-MACOS] Relaunching from original location");
    pid_t pid;
    int status = posix_spawn(&pid, originalExecPath, NULL, NULL, args, environ);

    if (status == 0) {
        debug("[JAUNCH-MACOS] Successfully relaunched, exiting translocated instance");
        free(args);
        exit(0); // Exit this translocated instance
    }
    else {
        debug("[JAUNCH-MACOS] Failed to relaunch: %s", strerror(status));
        free(args);
        return 0; // Continue with normal execution as fallback
    }
}

// ===========================================================
//              common.h FUNCTION IMPLEMENTATIONS
// ===========================================================

void setup(const int argc, const char *argv[]) {
    // Thanks to https://objective-see.org/blog/blog_0x15.html.
    // See doc/MACOS.md for why we have to do this.
  handle_translocation(argc, argv);
}
void teardown() {}

void init_threads() {}

/*
 * The macOS way of displaying a graphical error message.
 *
 * It uses Objective C calls to initialize the application,
 * then create, configure, and display the alert.
 *
 * The reason the code is ugly and hard to read is because we
 * are calling into Objective C from pure C, which goes against
 * the grain of Apple's recommended technology choices.
 * (If you are an expert macOS C developer reading this, please
 * feel warmly invited to file an issue or PR improving this!)
 */
void show_alert(const char *title, const char *message) {
    // Note: This function was generated by Claude.ai. It works, but
    // we make no guarantees about its awesomeness or lack thereof. :-)

    // Get necessary classes
    Class NSAutoreleasePool = objc_getClass("NSAutoreleasePool");
    Class NSApplication = objc_getClass("NSApplication");
    Class NSString = objc_getClass("NSString");
    Class NSAlert = objc_getClass("NSAlert");

    // Create autorelease pool
    id pool = ((id (*)(id, SEL))objc_msgSend)((id)NSAutoreleasePool, sel_registerName("alloc"));
    pool = ((id (*)(id, SEL))objc_msgSend)(pool, sel_registerName("init"));

    // Initialize application
    ((void(*)(void))NSApplicationLoad)();  // Cast the function pointer
    id app = ((id (*)(id, SEL))objc_msgSend)((id)NSApplication, sel_registerName("sharedApplication"));

    // Create strings
    id nsTitle = ((id (*)(id, SEL, const char*))objc_msgSend)((id)NSString,
        sel_registerName("stringWithUTF8String:"), title);
    id nsMessage = ((id (*)(id, SEL, const char*))objc_msgSend)((id)NSString,
        sel_registerName("stringWithUTF8String:"), message);

    // Create and configure alert
    id alert = ((id (*)(id, SEL))objc_msgSend)((id)NSAlert, sel_registerName("alloc"));
    alert = ((id (*)(id, SEL))objc_msgSend)(alert, sel_registerName("init"));
    ((void (*)(id, SEL, id))objc_msgSend)(alert, sel_registerName("setMessageText:"), nsTitle);
    ((void (*)(id, SEL, id))objc_msgSend)(alert, sel_registerName("setInformativeText:"), nsMessage);

    // Show alert
    ((void (*)(id, SEL))objc_msgSend)(alert, sel_registerName("runModal"));

    // Release pool
    ((void (*)(id, SEL))objc_msgSend)(pool, sel_registerName("drain"));
}

/*
 * The macOS way of launching a runtime.
 *
 * The behavior depends on the runloop mode:
 * - "main": Launch on main thread with event loop (like Java's -XstartOnFirstThread flag)
 * - "park": Launch on pthread, park main thread in event loop (like OpenJDK's default behavior)
 * - "none": Launch on main thread, no event loop (e.g. Python Qt apps)
 */
int launch(const LaunchFunc launch_runtime,
    const size_t argc, const char **argv)
{
    const int mode = effective_runloop_mode();

    if (mode == RUNLOOP_MAIN) {
        debug("[JAUNCH-MACOS] Launching on main thread with NSApplicationLoad");
        return launch_on_main_thread(launch_runtime, argc, argv);
    }
    if (mode == RUNLOOP_PARK) {
        debug("[JAUNCH-MACOS] Launching on pthread, parking main thread in event loop");
        return launch_on_pthread(launch_runtime, argc, argv);
    }

    // mode == RUNLOOP_NONE
    debug("[JAUNCH-MACOS] Launching directly on main thread, no event loop");
    return launch_runtime(argc, argv);
}
