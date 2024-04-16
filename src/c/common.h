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
#define ERROR_ARGC_OUT_OF_BOUNDS 15
#define ERROR_UNKNOWN_DIRECTIVE 16

// ===========================================================
//           PLATFORM-SPECIFIC FUNCTION DECLARATIONS
// For implementations, see linux.h, macos.h, posix.h, win32.h
// ===========================================================

// Implementations in posix.h, win32.h
void *lib_open(const char *path);
void *lib_sym(void *library, const char *symbol);
void lib_close(void *library);
char *lib_error();
int run_command(const char *command,
    size_t numInput, const char *input[],
    size_t *numOutput, char ***output);

// implementations in linux.h, win32.h, macos.h
void init_threads();
void show_alert(const char *title, const char *message);
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

#define CHECK_ARGS(prefix, name, argc, min, max, argv) \
    do { \
        debug("[%s] %s_argc = %zu", (prefix), (name), (argc)); \
        if ((argc) < (min) || (argc) > (max)) { \
            error("Error: %s_argc value %d is out of bounds [%d, %d]\n", \
                name, (argc), (min), (max)); \
            return ERROR_ARGC_OUT_OF_BOUNDS; \
        } \
        for (size_t a = 0; a < (argc); a++) { \
            debug("[%s] %s_argv[%zu] = %s", (prefix), (name), a, argv[a]); \
        } \
    } while(0)

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
