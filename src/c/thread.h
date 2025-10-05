#ifndef _JAUNCH_THREAD_H
#define _JAUNCH_THREAD_H

#include <stdlib.h>   // for exit, NULL, size_t, free
#include <pthread.h>  // for pthread_mutex, pthread_cond, etc.
#include <errno.h>    // for EDEADLK, EPERM

#include "logging.h"
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

static inline char *thread_state(ThreadState state) {
  if (state == STATE_WAITING) return "WAITING";
  if (state == STATE_EXECUTING) return "EXECUTING";
  if (state == STATE_RUNLOOP) return "RUNLOOP";
  if (state == STATE_COMPLETE) return "COMPLETE";
  return "UNKNOWN";
}

/*
 * Structure for thread communication and directive processing.
 *
 * MUTEX LOCKING POLICY:
 * ---------------------
 * All accesses to ThreadContext fields MUST be protected by holding the mutex,
 * with the following exceptions:
 *
 * 1. ctx_create() and ctx_destroy() - These are called in single-threaded
 *    contexts (before thread creation and after thread join, respectively).
 *
 * 2. After pthread_join() returns - Once the directive thread has been joined,
 *    the main thread has exclusive access and the mutex is no longer needed.
 *
 * 3. Functions documented as "Caller must hold mutex" - These functions assume
 *    the caller has already acquired the lock and will release it appropriately.
 *    Examples: ctx_set_state(), ctx_wait_for_state_change(), ctx_signal_main()
 *
 * 4. Functions documented as "Thread-safe operation that handles locking" -
 *    These functions assume the caller does *not* have the lock, and perform
 *    their operations bracketed with ctx_lock() and ctx_unlock() internally.
 *
 * 5. Thread ID fields (thread_id_main, thread_id_directives) - These are set
 *    once during initialization and are effectively read-only thereafter. The
 *    thread_name() function reads these without locking to avoid deadlock in
 *    logging paths.
 *
 * For all other accesses, use ctx_lock() and ctx_unlock() to protect field reads
 * and writes. This ensures thread safety and prevents data races.
 */
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
    const char *runloop_mode;

    // Bookmarked thread IDs, for use with thread_name function.
    pthread_t thread_id_main;
    pthread_t thread_id_directives;

    // Exit code to use at process conclusion.
    int exit_code;
} ThreadContext;

// =========================
// GLOBAL STATE DECLARATIONS
// =========================

// Global thread state instance.
extern ThreadContext *context;

// This wrapper around the global context is not really necessary,
// since it is always initialized straight away in jaunch.c's main,
// but it's here anyway, "just in case", to avoid SIGSEGVs.
static inline ThreadContext *ctx() {
  if (context == NULL) DIE(255, "Internal error: context not initialized");
  return context;
}

// =========================
// THREAD INSPECTION METHODS
// =========================

#define CHECK_THREAD_ID(tid1, tid2, name) \
    if (tid1 && tid2 && pthread_equal(tid1, tid2)) return name

const char *thread_name(pthread_t thread_id) {
    CHECK_THREAD_ID(thread_id, ctx()->thread_id_main, "main");
    CHECK_THREAD_ID(thread_id, ctx()->thread_id_directives, "directives");
    return "unknown";
}

const char *current_thread_name() {
    return thread_name(pthread_self());
}

// ==============================
// THREAD SYNCHRONIZATION HELPERS
// ==============================

/** Initialize thread context for directive processing. */
static inline void ctx_create() {
    ThreadContext *ctx = (ThreadContext *)malloc_or_die(sizeof(ThreadContext), "thread context");

    // Initialize mutex with error checking
    // to catch double-locks and invalid unlocks.
    pthread_mutexattr_t mutex_attr;
    pthread_mutexattr_init(&mutex_attr);
    pthread_mutexattr_settype(&mutex_attr, PTHREAD_MUTEX_ERRORCHECK);
    pthread_mutex_init(&ctx->mutex, &mutex_attr);
    pthread_mutexattr_destroy(&mutex_attr);

    pthread_cond_init(&ctx->cond, NULL);
    ctx->state = STATE_WAITING;
    ctx->out_argc = 0;
    ctx->out_argv = NULL;
    ctx->pending_directive = NULL;
    ctx->pending_argc = 0;
    ctx->pending_argv = NULL;
    ctx->directive_result = 0;
    ctx->thread_id_main = pthread_self();
    ctx->thread_id_directives = 0;
    ctx->runloop_mode = NULL;
    ctx->exit_code = 0;

    context = ctx;
}

/** Clean up thread context resources. */
static inline void ctx_destroy() {
    if (context == NULL) return;
    pthread_mutex_destroy(&context->mutex);
    pthread_cond_destroy(&context->cond);
    free(context);
    context = NULL;
}

static inline void ctx_lock() {
    int result = pthread_mutex_lock(&ctx()->mutex);
    if (result == EDEADLK) {
        DIE(ERROR_BAD_LOCKING,
            "Deadlock detected - attempted to lock already-held mutex");
    } else if (result != 0) {
        DIE(ERROR_BAD_LOCKING,
            "pthread_mutex_lock failed with error %d", result);
    }
}

static inline void ctx_unlock() {
    int result = pthread_mutex_unlock(&ctx()->mutex);
    if (result == EPERM) {
        DIE(ERROR_BAD_LOCKING,
            "Attempted to unlock mutex not owned by this thread");
    } else if (result != 0) {
        DIE(ERROR_BAD_LOCKING,
            "pthread_mutex_unlock failed with error %d", result);
    }
}

/*
 * Transition the thread context to a new state while holding the mutex.
 * This ensures atomic state transitions with proper synchronization.
 * Caller must hold mutex; returns with mutex still locked.
 */
static inline void ctx_set_state(ThreadState new_state) {
    ctx()->state = new_state;
}

/*
 * Check if the current thread is the main thread by examining the context state.
 * Returns 1 if main thread is available for directive execution, 0 otherwise.
 * Thread-safe operation that handles locking internally.
 */
static inline int ctx_main_thread_available() {
    ctx_lock();
    int available = ctx()->state == STATE_WAITING;
    ctx_unlock();
    return available;
}

/*
 * Get the current runloop mode.
 * Thread-safe operation that handles locking internally.
 */
static inline const char *ctx_get_runloop_mode() {
    ctx_lock();
    const char *mode = ctx()->runloop_mode;
    ctx_unlock();
    return mode;
}

/*
 * Set the runloop mode.
 * Thread-safe operation that handles locking internally.
 */
static inline void ctx_set_runloop_mode(const char *mode) {
    ctx_lock();
    ctx()->runloop_mode = mode;
    ctx_unlock();
}

/*
 * Wait for the state to change from the current state.
 * Caller must hold mutex; returns with mutex still locked.
 */
static inline void ctx_wait_for_state_change(ThreadState expected_state) {
    while (ctx()->state == expected_state) {
        pthread_cond_wait(&ctx()->cond, &ctx()->mutex);
    }
}

/*
 * Signal the main thread to wake up and check for work.
 * Caller must hold mutex; returns with mutex still locked.
 */
static inline void ctx_signal_main() {
    pthread_cond_signal(&ctx()->cond);
}

/*
 * Signal early completion of the current directive to the directive thread.
 * This allows long-running or blocking operations to release the directive thread
 * while continuing to run on the main thread.
 *
 * Must be called from main thread while executing a directive.
 * Caller must hold mutex; returns with mutex still locked.
 */
void ctx_signal_early_completion(ThreadState new_state) {
    LOG_DEBUG("JAUNCH", "Signaling early completion with new state=%s", thread_state(new_state));

    if (ctx()->state != STATE_EXECUTING) {
        LOG_ERROR("Cannot signal early completion - not in EXECUTING state (current: %s)", thread_state(ctx()->state));
        return;
    }

    LOG_DEBUG("JAUNCH", "Transitioning %s directive to early completion with state %s",
          ctx()->pending_directive ? ctx()->pending_directive : "unknown",
          new_state == STATE_RUNLOOP ? "RUNLOOP" : "WAITING");

    ctx_set_state(new_state);
    ctx_signal_main();

    LOG_DEBUG("JAUNCH", "Early completion signaled successfully");
}

/*
 * Request execution of a directive on the main thread.
 * Blocks until the directive completes (or signals early completion).
 * Returns the error code from the directive execution.
 * Thread-safe operation that handles locking internally.
 */
int ctx_request_main_execution(const char *directive, size_t dir_argc, const char **dir_argv) {
    ctx_lock();

    // Set up the directive for execution.
    ctx()->pending_directive = directive;
    ctx()->pending_argc = dir_argc;
    ctx()->pending_argv = dir_argv;
    ctx_set_state(STATE_EXECUTING);

    // Signal main thread and wait for completion or early completion.
    LOG_DEBUG("JAUNCH", "Signaling main thread to execute %s directive", directive);
    ctx_signal_main();

    // Wait for state to change from EXECUTING (either to WAITING, RUNLOOP, or COMPLETE).
    LOG_DEBUG("JAUNCH", "Waiting for %s directive to complete", directive);
    ctx_wait_for_state_change(STATE_EXECUTING);

    LOG_DEBUG("JAUNCH", "%s directive completed with state %s", directive, thread_state(ctx()->state));

    int result = ctx()->directive_result;
    ctx_unlock();
    return result;
}

#endif
