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

// Implementations in linux.h, win32.h, macos.h
void setup(const int argc, const char *argv[]);
void teardown();
void init_threads();                                     // INIT_THREADS
void show_alert(const char *title, const char *message); // ERROR
typedef int (*LaunchFunc)(const size_t, const char **);
int launch(const LaunchFunc launch_func,                 // JVM, PYTHON
    const size_t argc, const char **argv);

// ============
// GLOBAL STATE
// ============
int debug_mode = 0;
int headless_mode = 0;
char *runloop_mode = "auto";

// =================
// UTILITY FUNCTIONS
// =================

void print_at_level(int verbosity, const char *fmt, ...) {
    if (debug_mode < verbosity) return;
    va_list ap;
    va_start(ap, fmt);
    vfprintf(stderr, fmt, ap);
    va_end(ap);
    fputc('\n', stderr);
    fflush(stderr);
}

#define error(fmt, ...) print_at_level(0, fmt, ##__VA_ARGS__)
#define debug(fmt, ...) print_at_level(1, fmt, ##__VA_ARGS__)
#define debug_verbose(fmt, ...) print_at_level(2, fmt, ##__VA_ARGS__)

#define CHECK_ARGS(prefix, name, argc, min, max, argv) \
    do { \
        debug_verbose("[%s] %s_argc = %zu", (prefix), (name), (argc)); \
        if ((argc) < (min) || (argc) > (max)) { \
            error("Error: %s_argc value %d is out of bounds [%d, %d]\n", \
                name, (argc), (min), (max)); \
            return ERROR_ARGC_OUT_OF_BOUNDS; \
        } \
        for (size_t a = 0; a < (argc); a++) { \
            debug_verbose("[%s] %s_argv[%zu] = %s", (prefix), (name), a, argv[a]); \
        } \
    } \
    while(0)

/* Splits an output buffer into lines. */
int split_lines(char *buffer, char *delim, char ***output, size_t *numOutput) {
    size_t lineCount = 0;
    char *token = strtok(buffer, delim);
    while (token != NULL) {
        *output = realloc(*output, (lineCount + 1) * sizeof(char *));
        if (*output == NULL) {
          error("Failed to reallocate memory (split lines)");
          return ERROR_REALLOC;
        }
        (*output)[lineCount] = strdup(token);
        if ((*output)[lineCount] == NULL) {
          error("Failed to duplicate string");
          return ERROR_STRDUP;
        }
        lineCount++;
        token = strtok(NULL, delim);
    }
    *numOutput = lineCount;
    return SUCCESS;
}

/* Joins strings with the given delimiter. Returns newly allocated string. */
char *join_strings(const char **strings, size_t count, const char *delim) {
    if (count == 0) return NULL;

    // Calculate total length needed.
    size_t total_len = 0;
    size_t delim_len = strlen(delim);
    for (size_t i = 0; i < count; i++) {
        total_len += strlen(strings[i]);
        if (i < count - 1) total_len += delim_len;
    }

    // Allocate and build the joined string.
    char *result = (char *)malloc(total_len + 1); // +1 for null terminator
    if (result == NULL) return NULL;

    result[0] = '\0'; // Start with empty string.
    for (size_t i = 0; i < count; i++) {
        strcat(result, strings[i]);
        if (i < count - 1) strcat(result, delim);
    }

    return result;
}

#endif
