#ifndef _JAUNCH_THREAD_H
#define _JAUNCH_THREAD_H

#include <pthread.h>

#include "common.h"

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

    // Bookmarked thread IDs, for use with thread_name function.
    pthread_t thread_id_main;
    pthread_t thread_id_directives;

    // Exit code to use at process conclusion.
    int exit_code;
} ThreadContext;

// Global thread state instance.
ThreadContext *ctx = NULL;

// =========================
// THREAD INSPECTION METHODS
// =========================

// Thread-aware debug macro: DEBUG(component, format, ...)
// Automatically includes thread name in prefix: [COMPONENT:thread]
#define DEBUG(component, fmt, ...) \
    debug("[%s:%s] " fmt, component, current_thread_name(), ##__VA_ARGS__)

#define CHECK_THREAD_ID(tid1, tid2, name) \
    if (tid1 && tid2 && pthread_equal(tid1, tid2)) return name

const char* thread_name(pthread_t thread_id) {
    CHECK_THREAD_ID(thread_id, ctx->thread_id_main, "main");
    CHECK_THREAD_ID(thread_id, ctx->thread_id_directives, "directives");
    return "unknown";
}

const char* current_thread_name() {
    return thread_name(pthread_self());
}

// ==============================
// THREAD SYNCHRONIZATION HELPERS
// ==============================

/** Initialize thread context for directive processing. */
static inline ThreadContext* ctx_create() {
    ThreadContext *the_ctx = (ThreadContext *)malloc(sizeof(ThreadContext));
    if (the_ctx == NULL) return NULL;

    the_ctx->mutex = (pthread_mutex_t)PTHREAD_MUTEX_INITIALIZER;
    the_ctx->cond = (pthread_cond_t)PTHREAD_COND_INITIALIZER;
    the_ctx->state = STATE_WAITING;
    the_ctx->out_argc = 0;
    the_ctx->out_argv = NULL;
    the_ctx->pending_directive = NULL;
    the_ctx->pending_argc = 0;
    the_ctx->pending_argv = NULL;
    the_ctx->directive_result = 0;
    the_ctx->thread_id_main = pthread_self();
    the_ctx->thread_id_directives = 0;
    the_ctx->runloop_mode = NULL;
    the_ctx->exit_code = 0;

    return the_ctx;
}

/** Clean up thread context resources. */
static inline void ctx_destroy(ThreadContext *context) {
    if (context == NULL) return;
    pthread_mutex_destroy(&context->mutex);
    pthread_cond_destroy(&context->cond);
    free(context);
}

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
 * Check if the current thread is the main thread by examining the context state.
 * Returns 1 if main thread is available for directive execution, 0 otherwise.
 */
static inline int ctx_main_thread_available() {
    return ctx->state == STATE_WAITING;
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
 * Signal the main thread to wake up and check for work.
 */
static inline void ctx_signal_main() {
    pthread_cond_signal(&ctx->cond);
}

/*
 * Signal early completion of the current directive to the directive thread.
 * This allows long-running or blocking operations to release the directive thread
 * while continuing to run on the main thread.
 *
 * Must be called from main thread while executing a directive.
 * The main thread must hold the mutex when calling this function.
 */
void ctx_signal_early_completion(ThreadState new_state) {
    DEBUG("JAUNCH", "Signaling early completion with new state=%d", new_state);

    if (ctx->state != STATE_EXECUTING) {
        error("[JAUNCH] Cannot signal early completion - not in EXECUTING state (current: %d)", ctx->state);
        return;
    }

    DEBUG("JAUNCH", "Transitioning %s directive to early completion with state %s",
          ctx->pending_directive ? ctx->pending_directive : "unknown",
          new_state == STATE_RUNLOOP ? "RUNLOOP" : "WAITING");

    ctx_set_state(new_state);
    ctx_signal_main();

    DEBUG("JAUNCH", "Early completion signaled successfully");
}

/*
 * Request execution of a directive on the main thread.
 * Blocks until the directive completes (or signals early completion).
 * Returns the error code from the directive execution.
 */
int ctx_request_main_execution(const char *directive, size_t dir_argc, const char **dir_argv) {
    ctx_lock();

    // Set up the directive for execution
    ctx->pending_directive = directive;
    ctx->pending_argc = dir_argc;
    ctx->pending_argv = dir_argv;
    ctx_set_state(STATE_EXECUTING);

    // Signal main thread and wait for completion or early completion
    DEBUG("JAUNCH", "Signaling main thread to execute %s directive", directive);
    ctx_signal_main();

    // Wait for state to change from EXECUTING (either to WAITING, RUNLOOP, or COMPLETE)
    DEBUG("JAUNCH", "Waiting for %s directive to complete", directive);
    ctx_wait_for_state_change(STATE_EXECUTING);

    DEBUG("JAUNCH", "%s directive completed with state %d", directive, ctx->state);

    int result = ctx->directive_result;
    ctx_unlock();
    return result;
}

#endif
