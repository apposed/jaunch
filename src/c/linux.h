#include <string.h>

#include "common.h"

#define OS_NAME "linux"

int is_command_available(const char *command) {
    return access(command, X_OK) == 0;
}

void show_alert(const char *title, const char *message) {
    if (is_command_available("zenity")) {
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
    else if (is_command_available("kdialog")) {
        char *titleArg = malloc(strlen(title) + 9);  // --title={message}
        strcpy("--title=", titleArg);
        strcat((char *)title, titleArg);
        execlp("kdialog", "--sorry", titleArg, message, (char *)NULL);
        free(titleArg);
    }
    else if (is_command_available("notify-send")) {
        execlp("notify-send", "-a", title, "-c", "im.error",
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
