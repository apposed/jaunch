#ifndef _JAUNCH_COMMON_H
#define _JAUNCH_COMMON_H

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define SUCCESS 0
#define ERROR_DLOPEN 1
#define ERROR_DLSYM 2
#define ERROR_CREATE_JAVA_VM 3
#define ERROR_FIND_CLASS 4
#define ERROR_GET_STATIC_METHOD_ID 5
#define ERROR_PIPE 6
#define ERROR_FORK 7
#define ERROR_EXECLP 8
#define ERROR_MALLOC 9
#define ERROR_REALLOC 10
#define ERROR_WAITPID 11
#define ERROR_STRDUP 12
#define ERROR_COMMAND_PATH 13
#define ERROR_OUTPUT 14
#define ERROR_ARG_COUNT_TOO_SMALL 15
#define ERROR_ARG_COUNT_TOO_LARGE 16

// ===========================================================
//           PLATFORM-SPECIFIC FUNCTION DECLARATIONS
// For implementations, see linux.h, macos.h, posix.h, win32.h
// ===========================================================

// run_command implementations in posix.h, win32.h
int run_command(const char *command,
    const char *input[], size_t numInput,
    char ***output, size_t *numOutput);

// show_alert implementations in linux.h, macos.h, win32.h
void show_alert(const char *title, const char *message);

// launch implementations in linux.h, macos.h, win32.h
typedef int (*LaunchFunc)(const size_t, const char **);
int launch(const LaunchFunc launch_func,
    const size_t argc, const char **argv);

// =================
// UTILITY FUNCTIONS
// =================

int debug_mode = 0;

void error(const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    vfprintf(stderr, fmt, ap);
    va_end(ap);
    fputc('\n', stderr);
    fflush(stderr);
}

void debug(const char *fmt, ...) {
    if (!debug_mode) return;
    va_list ap;
    int i;
    va_list nothing;
    va_start(ap, fmt);
    vfprintf(stderr, fmt, ap);
    va_end(ap);
    fputc('\n', stderr);
    fflush(stderr);
}

/* Splits an output buffer into lines. */
int split_lines(char *buffer, char *delim, char ***output, size_t *numOutput) {
    size_t lineCount = 0;
    char *token = strtok(buffer, delim);
    while (token != NULL) {
        *output = realloc(*output, (lineCount + 1) * sizeof(char *));
        if (*output == NULL) {
          error("Memory reallocation failed");
          return ERROR_REALLOC;
        }
        (*output)[lineCount] = strdup(token);
        if ((*output)[lineCount] == NULL) {
          error("String duplication failed");
          return ERROR_STRDUP;
        }
        lineCount++;
        token = strtok(NULL, delim);
    }
    *numOutput = lineCount;
    return SUCCESS;
}

#endif
