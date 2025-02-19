#include <string.h>
#include <dlfcn.h>

#include "common.h"

#define OS_NAME "linux"

void (*xinit_threads_reference)();

int is_command_available(const char *command) {
    return access(command, X_OK) == 0;
}

// ===========================================================
//              common.h FUNCTION IMPLEMENTATIONS
// ===========================================================

void init_threads() {
    void *libX11Handle = dlopen("libX11.so", RTLD_LAZY);
    if (libX11Handle != NULL) {
        debug("[JAUNCH-LINUX] Running XInitThreads");
        xinit_threads_reference = dlsym(libX11Handle, "XInitThreads");

        if (xinit_threads_reference != NULL) {
            xinit_threads_reference();
        }
        else {
            error("Could not find XInitThreads in X11 library: %s\n", dlerror());
        }
    }
    else {
        error("Could not find X11 library, not running XInitThreads.\n");
    }
}

void show_alert(const char *title, const char *message) {
    if (is_command_available("/usr/bin/zenity")) {
        char *titleArg = malloc(strlen(title) + 9);  // --title={message}
        strcpy(titleArg, "--title=");
        strcat(titleArg, title);
        char *textArg = malloc(strlen(message) + 8);  // --text={message}
        strcpy(textArg, "--text=");
        strcat(textArg, message);
        error("/usr/bin/zenity '%s' '%s' '%s'\n", "--error", titleArg, textArg);
        execlp("/usr/bin/zenity", "zenity",
            "--error", titleArg, textArg, (char *)NULL);
        // Note: execlp replaces the process, so the free calls are orphaned.
        free(titleArg);
        free(textArg);
    }
    else if (is_command_available("/usr/bin/kdialog")) {
        char *titleArg = malloc(strlen(title) + 9);  // --title={message}
        strcpy("--title=", titleArg);
        strcat((char *)title, titleArg);
        execlp("/usr/bin/kdialog", "kdialog",
            "--sorry", titleArg, message, (char *)NULL);
        // Note: execlp replaces the process, so the free calls are orphaned.
        free(titleArg);
    }
    else if (is_command_available("/usr/bin/notify-send")) {
        execlp("/usr/bin/notify-send", "notify-send",
            "-a", title, "-c", "im.error",
            /*"-i", iconPath,*/ message, (char *)NULL);
    }
    else {
        printf("%s\n", message);
    }
}

/*
 * The Linux way of launching a runtime.
 *
 * It calls the given launch function directly. The epitome of elegance. ^_^
 */
int launch(const LaunchFunc launch_runtime,
    const size_t argc, const char **argv)
{
    return launch_runtime(argc, argv);
}
