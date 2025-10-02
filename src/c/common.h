#ifndef _JAUNCH_COMMON_H
#define _JAUNCH_COMMON_H

#include <stdlib.h>
#include <string.h>   // for strcat, strdup, strlen, strtok

#include "logging.h"

#define SUCCESS 0
#define ERROR_DLOPEN 1
#define ERROR_DLSYM 2
#define ERROR_CREATE_JAVA_VM 3
#define ERROR_FIND_CLASS 4
#define ERROR_GET_STATIC_METHOD_ID 5
#define ERROR_PIPE 6
#define ERROR_FORK 7
#define ERROR_EXEC 8
#define ERROR_MALLOC 9
#define ERROR_REALLOC 10
#define ERROR_WAITPID 11
#define ERROR_STRDUP 12
#define ERROR_COMMAND_PATH 13
#define ERROR_ARGC_OUT_OF_BOUNDS 15
#define ERROR_UNKNOWN_DIRECTIVE 16
#define ERROR_BAD_DIRECTIVE_SYNTAX 17
#define ERROR_MISSING_FUNCTION 18
#define ERROR_BAD_LOCKING 19

// ===========================================================
//           PLATFORM-SPECIFIC FUNCTION DECLARATIONS
// For implementations, see linux.h, macos.h, posix.h, win32.h
// ===========================================================

// Implementations in posix.h, win32.h
void *lib_open(const char *path);
void *lib_sym(void *library, const char *symbol);
void lib_close(void *library);
char *lib_error();
void run_command(const char *command,
    size_t numInput, const char *input[],
    size_t *numOutput, char ***output);

// Implementations in linux.h, macos.h, win32.h
void setup(const int argc, const char *argv[]);
void teardown();
void runloop_config(const char *directive);
void runloop_run(const char *mode);
void runloop_stop();
int init_threads();                                      // INIT_THREADS
void show_alert(const char *title, const char *message); // ERROR
typedef int (*LaunchFunc)(const size_t, const char **);
int launch(const LaunchFunc launch_func,                 // JVM, PYTHON
    const size_t argc, const char **argv);

// =================
// UTILITY FUNCTIONS
// =================

#define CHECK_ARGS(component, name, argc, min, max, argv) do { \
    LOG_DEBUG(component, "%s_argc = %zu", (name), (argc)); \
    if ((argc) < (min) || (argc) > (max)) \
        DIE(ERROR_ARGC_OUT_OF_BOUNDS, \
            "Error: %s_argc value %d is out of bounds [%d, %d]", \
            (name), (argc), (min), (max)); \
    for (size_t a = 0; a < (argc); a++) \
        LOG_DEBUG(component, "%s_argv[%zu] = %s", (name), a, (argv)[a]); \
} while (0)

/* Splits an output buffer into lines. */
void split_lines(char *buffer, char *delim, char ***output, size_t *numOutput) {
    size_t lineCount = 0;
    char *token = strtok(buffer, delim);
    while (token != NULL) {
        *output = realloc(*output, (lineCount + 1) * sizeof(char *));
        if (*output == NULL) {
            DIE(ERROR_REALLOC, "Failed to reallocate memory (split lines)");
        }
        (*output)[lineCount] = strdup(token);
        if ((*output)[lineCount] == NULL) {
            DIE(ERROR_STRDUP, "Failed to duplicate string");
        }
        lineCount++;
        token = strtok(NULL, delim);
    }
    *numOutput = lineCount;
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
