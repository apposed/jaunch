#include <string.h>
#include <dlfcn.h>
#include <limits.h>

#include "common.h"

#define OS_NAME "linux"

void (*xinit_threads_reference)();

int is_command_available(const char *command) {
    return access(command, X_OK) == 0;
}

int find_executable(const char *name, char *path_buf, size_t buf_size) {
    char *path_env = getenv("PATH");
    if (!path_env) return 0;

    char *path_copy = strdup(path_env);
    if (!path_copy) return 0;

    char *dir = strtok(path_copy, ":");
    while (dir) {
        snprintf(path_buf, buf_size, "%s/%s", dir, name);
        if (access(path_buf, X_OK) == 0) {
            free(path_copy);
            return 1;
        }
        dir = strtok(NULL, ":");
    }

    free(path_copy);
    return 0;
}

// ===========================================================
//              common.h FUNCTION IMPLEMENTATIONS
// ===========================================================

void setup(const int argc, const char *argv[]) {}
void teardown() {}

void runloop_config(ThreadContext *ctx, const char *directive) {}
void runloop_run(ThreadContext *ctx, const char *mode) {}
void runloop_stop(ThreadContext *ctx) {}

int init_threads() {
    void *libX11Handle = dlopen("libX11.so", RTLD_LAZY);
    if (libX11Handle != NULL) {
        debug("[JAUNCH-LINUX] Running XInitThreads");
        xinit_threads_reference = dlsym(libX11Handle, "XInitThreads");

        if (xinit_threads_reference != NULL) {
            xinit_threads_reference();
            return SUCCESS;
        }
        error("Could not find XInitThreads in X11 library: %s", dlerror());
    }
    else {
        error("Could not find X11 library, not running XInitThreads.");
    }
    return ERROR_MISSING_FUNCTION;
}

/*
 * The Linux way of displaying a graphical error message.
 *
 * It looks for a utility program in decreasing order of idealness:
 * zenity, then kdialog, then xmessage, and finally notify-send.
 * If it finds one, it invokes it via execlp to display the message;
 * if not, it falls back to plain old printf.
 */
void show_alert(const char *title, const char *message) {
    char exe[PATH_MAX];

    if (find_executable("zenity", exe, sizeof(exe))) {
        char *titleArg = malloc(strlen(title) + 9);  // --title={message}
        strcpy(titleArg, "--title=");
        strcat(titleArg, title);
        char *textArg = malloc(strlen(message) + 8);  // --text={message}
        strcpy(textArg, "--text=");
        strcat(textArg, message);
        debug("[JAUNCH-LINUX] '%s' '%s' '%s' '%s'", exe, "--error", titleArg, textArg);
        execlp(exe, "zenity", "--error", titleArg, textArg, (char *)NULL);
        // Note: execlp replaces the process, so the free calls are orphaned.
        free(titleArg);
        free(textArg);
    }
    else if (find_executable("kdialog", exe, sizeof(exe))) {
        char *titleArg = malloc(strlen(title) + 9);  // --title={message}
        strcpy("--title=", titleArg);
        strcat((char *)title, titleArg);
        debug("[JAUNCH-LINUX] '%s' '%s' '%s' '%s'", exe, "--sorry", titleArg, message);
        execlp(exe, "kdialog", "--sorry", titleArg, message, (char *)NULL);
        // Note: execlp replaces the process, so the free calls are orphaned.
        free(titleArg);
    }
    else if (find_executable("xmessage", exe, sizeof(exe))) {
      debug("[JAUNCH-LINUX] '%s' '%s' '%s' '%s' '%s'", exe,
          "-buttons", "OK:0", "-nearmouse", message);
      execlp(exe, "xmessage",
          "-buttons", "OK:0", "-nearmouse", message, (char *)NULL);
    }
    else if (find_executable("notify-send", exe, sizeof(exe))) {
        debug("[JAUNCH-LINUX] '%s' '%s' '%s' '%s' '%s' '%s'", exe,
            "-a", title, "-c", "im.error", message);
        execlp(exe, "notify-send",
            "-a", title, "-c", "im.error", message, (char *)NULL);
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
