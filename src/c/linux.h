#include <string.h>

#include "common.h"

#define OS_NAME "linux"

int startup_jvm(
    const char *libjvm_path, const size_t jvm_argc, const char *jvm_argv[],
    const char *main_class_name, const size_t main_argc, const char *main_argv[])
{
    return launch_jvm(
        libjvm_path, jvm_argc, jvm_argv,
        main_class_name, main_argc, main_argv);
}

int isCommandAvailable(const char *command) {
    return access(command, X_OK) == 0;
}

void show_alert(const char *title, const char *message) {
    if (isCommandAvailable("zenity")) {
        char *titleArg = malloc(strlen(title) + 9);  // --title={message}
        strcpy("--title=", titleArg);
        strcat((char *)title, titleArg);
        char *textArg = malloc(strlen(message) + 8);  // --text={message}
        strcpy("--text=", textArg);
        strcat((char *)message, textArg);
        execlp("zenity", "--error", titleArg, textArg, (char *)NULL);
        free(titleArg);
        free(textArg);
    }
    else if (isCommandAvailable("kdialog")) {
        char *titleArg = malloc(strlen(title) + 9);  // --title={message}
        strcpy("--title=", titleArg);
        strcat((char *)title, titleArg);
        execlp("kdialog", "--sorry", titleArg, message, (char *)NULL);
        free(titleArg);
    }
    else if (isCommandAvailable("notify-send")) {
        execlp("notify-send", "-a", title, "-c", "im.error",
            /*"-i", iconPath,*/ message, (char *)NULL);
    }
    else {
        printf("%s\n", message);
    }
}
