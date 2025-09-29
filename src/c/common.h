#ifndef _JAUNCH_COMMON_H
#define _JAUNCH_COMMON_H

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>

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
#define ERROR_ARGC_OUT_OF_BOUNDS 15
#define ERROR_UNKNOWN_DIRECTIVE 16
#define ERROR_BAD_DIRECTIVE_SYNTAX 17
#define ERROR_MISSING_FUNCTION 18

#define RUNLOOP_NONE 0
#define RUNLOOP_MAIN 1
#define RUNLOOP_PARK 2

// ===============
// DATA STRUCTURES
// ===============

// Thread communication states.
typedef enum {
    STATE_WAITING,     // Main thread is available for directive execution
    STATE_EXECUTING,   // Main thread is executing a directive
    STATE_RUNLOOP,     // Main thread is blocked in platform runloop
    STATE_COMPLETE     // All directive processing is complete
} ThreadState;

// Structure for thread communication and directive processing.
typedef struct {
    pthread_mutex_t mutex;
    pthread_cond_t cond;
    ThreadState state;

    // Original directive data.
    size_t out_argc;
    char **out_argv;

    // Directive to execute on main thread.
    const char *pending_directive;
    size_t pending_argc;
    const char **pending_argv;
    int directive_result;

    // Runloop configuration.
    char *runloop_mode;

    // Exit code to use at process conclusion.
    int exit_code;
} ThreadContext;

// ============
// GLOBAL STATE
// ============
int debug_mode = 0;
int headless_mode = 0;
ThreadContext *ctx = NULL;

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

// =============================
// THREAD SYNCHRONIZATION HELPERS
// =============================

static inline void ctx_lock() {
    pthread_mutex_lock(&ctx->mutex);
}

static inline void ctx_unlock() {
    pthread_mutex_unlock(&ctx->mutex);
}

/*
 * Transition the thread context to a new state while holding the mutex.
 * This ensures atomic state transitions with proper synchronization.
 */
static inline void ctx_set_state(ThreadState new_state) {
    ctx->state = new_state;
}

/*
 * Signal the main thread to wake up and check for work.
 */
static inline void ctx_signal_main() {
    pthread_cond_signal(&ctx->cond);
}

/*
 * Wait for the state to change from the current state.
 * Must be called with mutex locked; returns with mutex still locked.
 */
static inline void ctx_wait_for_state_change(ThreadState expected_state) {
    while (ctx->state == expected_state) {
        pthread_cond_wait(&ctx->cond, &ctx->mutex);
    }
}

/*
 * Request that the main thread execute a directive.
 * Blocks until the directive completes (or signals early completion).
 * Returns the directive result code.
 */
int ctx_request_main_execution(const char *directive, size_t dir_argc, const char **dir_argv);

/*
 * Signal that the current directive has completed early and will continue
 * running asynchronously (e.g., blocking in a runloop).
 * This releases the directive thread to continue processing.
 * Must be called from main thread while executing a directive.
 */
void ctx_signal_early_completion(ThreadState new_state);

/*
 * Check if the current thread is the main thread by examining the context state.
 * Returns 1 if main thread is available for directive execution, 0 otherwise.
 */
static inline int ctx_main_thread_available() {
    return ctx->state == STATE_WAITING;
}

#endif
