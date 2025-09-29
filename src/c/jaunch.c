/*
 * This is the C portion of Jaunch, the configurable native launcher.
 *
 * Its primary function is to launch a non-native runtime plus main program
 * in the same process, by dynamically loading the runtime library.
 *
 * Currently supported runtimes include Python and the Java Virtual Machine.
 *
 * - For Python logic, see python.h.
 * - For JVM logic, see jvm.h.
 *
 * The C portion of Jaunch is empowered by a so-called "configurator" program,
 * which is the more sophisticated portion of Jaunch. The C launcher invokes
 * the configurator program in a separate process, using the function:
 *
 *     int run_command(const char *command,
 *         size_t numInput, const char *input[],
 *         size_t *numOutput, char ***output)
 *
 * The C code waits for the Jaunch configurator process to complete, then
 * passes the outputs given by the configurator to the appropriate `launch`
 * function.
 *
 * In this way, the non-native runtime is launched in the same process by C,
 * but in a way that is fully customizable from the Jaunch code written in a
 * high-level language.
 *
 * For example, a command line invocation of:
 *
 *   fizzbuzz Hello --verbose=2 --min 100 --max 200
 *
 * might be translated by the configurator into a Python invocation:
 *
 *     python -vv fizzbuzz.py Hello 100..200
 *
 * or a Java invocation:
 *
 *     java -DverboseLevel=2 -Xmx128m com.fizzbuzz.FizzBuzz Hello 100..200
 *
 * depending on the way Jaunch is configured via its TOML config files.
 *
 * See the common.toml file for a walkthrough of how the configurator
 * can be flexibly configured to decide how arguments are transformed.
 */

#include <unistd.h>
#include <pthread.h>
#include <errno.h>
#include <time.h>

#include "common.h"

// -- PLATFORMS --
#ifdef __linux__
    #include "linux.h"
#endif
#ifdef __APPLE__
    #include "macos.h"
#endif
#ifdef WIN32
    #include "win32.h"
#else
    #include "posix.h"
#endif
#ifdef __x86_64__
    #define OS_ARCH "x64"
#endif
#ifdef __aarch64__
    #define OS_ARCH "arm64"
#endif

// -- RUNTIMES --
#include "jvm.h"
#include "python.h"

// Thread-local storage for the current thread context.
// This allows platform-specific code to access the context without globals.
__thread ThreadContext *tls_thread_context = NULL;

/*
 * Signal early completion of the current directive to the directive thread.
 * This allows long-running or blocking operations to release the directive thread
 * while continuing to run on the main thread.
 *
 * Must be called from main thread while executing a directive.
 * The main thread must hold the mutex when calling this function.
 */
void ctx_signal_early_completion(ThreadContext *ctx, ThreadState new_state) {
    debug("[JAUNCH] ctx_signal_early_completion called: new state=%d", new_state);

    if (ctx->state != STATE_EXECUTING) {
        error("[JAUNCH] Cannot signal early completion - not in EXECUTING state (current: %d)", ctx->state);
        return;
    }

    debug("[JAUNCH] Transitioning %s directive to early completion with state %s",
          ctx->pending_directive ? ctx->pending_directive : "unknown",
          new_state == STATE_RUNLOOP ? "RUNLOOP" : "WAITING");

    ctx_set_state(ctx, new_state);
    ctx_signal_main(ctx);

    debug("[JAUNCH] Early completion signaled successfully");
}

// List of places to search for the jaunch configurator executable.
//
// NB: This list should align with the configDirs list in Jaunch.kt,
// except for the trailing "Contents/MacOS/" and NULL entries.
//
// The trailing slashes make the math simpler in the path function logic.
const char *JAUNCH_SEARCH_PATHS[] = {
    "jaunch"SLASH,
    ".jaunch"SLASH,
    "config"SLASH"jaunch"SLASH,
    ".config"SLASH"jaunch"SLASH,
    "Contents"SLASH"MacOS"SLASH,
    NULL,
};

/*
 * Execute a single directive and return its error code.
 * This function handles the actual directive execution logic.
 */
int execute_directive(ThreadContext *ctx, const char *directive, size_t dir_argc, const char **dir_argv) {
    if (strcmp(directive, "JVM") == 0) {
        return launch(launch_jvm, dir_argc, dir_argv);
    }
    if (strcmp(directive, "PYTHON") == 0) {
        return launch(launch_python, dir_argc, dir_argv);
    }
    if (strcmp(directive, "SETCWD") == 0) {
        if (dir_argc >= 1) {
            const char *cwd = dir_argv[0];
            debug("[JAUNCH] Changing working directory to: %s", cwd);
            return chdir(cwd);
        }
        error("Ignoring invalid SETCWD directive with no argument.");
        return ERROR_BAD_DIRECTIVE_SYNTAX;
    }
    if (strcmp(directive, "INIT_THREADS") == 0) {
        return init_threads();
    }
    if (strcmp(directive, "RUNLOOP") == 0) {
        const char *mode = dir_argc >= 1 ? dir_argv[0] : ctx->runloop_mode;
        if (mode) {
          debug("[JAUNCH] Invoking runloop with mode %s", mode);
        }
        else {
            error("Ignoring invalid RUNLOOP directive with no mode.");
            return ERROR_BAD_DIRECTIVE_SYNTAX;
        }

        // Note: runloop_run will set STATE_RUNLOOP when appropriate

        runloop_run(ctx, mode);
        return SUCCESS;
    }
    if (strcmp(directive, "ERROR") == 0) {
        // Log all error lines first.
        for (size_t i = 1; i < dir_argc; i++) error(dir_argv[i]);

        // Now join the error lines and display in an alert box.
        char *message = join_strings(dir_argv + 1, dir_argc - 1, "\n");
        if (message != NULL) {
            if (!headless_mode) show_alert("Error", message);
            free(message);
        }
        else {
            error("An unknown error occurred.");
            if (!headless_mode) show_alert("Error", "An unknown error occurred.");
        }

        int error_code = dir_argc >= 1 ? atoi(dir_argv[0]) : 255;
        if (error_code < 20) error_code = 20;
        if (error_code > 255) error_code = 255;
        return error_code;
    }
    error("Unknown directive: %s", directive);
    return ERROR_UNKNOWN_DIRECTIVE;
}

/*
 * Request execution of a directive on the main thread.
 * Blocks until the directive completes (or signals early completion).
 * Returns the error code from the directive execution.
 */
int ctx_request_main_execution(ThreadContext *ctx, const char *directive,
                                 size_t dir_argc, const char **dir_argv) {
    pthread_mutex_lock(&ctx->mutex);

    // Set up the directive for execution
    ctx->pending_directive = directive;
    ctx->pending_argc = dir_argc;
    ctx->pending_argv = dir_argv;
    ctx_set_state(ctx, STATE_EXECUTING);

    // Signal main thread and wait for completion or early completion
    debug("[JAUNCH] Signaling main thread to execute %s directive", directive);
    ctx_signal_main(ctx);

    // Wait for state to change from EXECUTING (either to WAITING, RUNLOOP, or COMPLETE)
    debug("[JAUNCH] Waiting for %s directive to complete...", directive);
    ctx_wait_for_state_change(ctx, STATE_EXECUTING);

    debug("[JAUNCH] %s directive completed with state %d", directive, ctx->state);

    int result = ctx->directive_result;
    pthread_mutex_unlock(&ctx->mutex);
    return result;
}

/*
 * Process all directives in sequence. This function runs on a separate thread and
 * coordinates with the main thread for runloop management and directive execution.
 */
int process_directives(ThreadContext *ctx) {
    int exit_code = SUCCESS;

    size_t index = 0;
    while (index < ctx->out_argc) {
        // Prepare the (argc, argv) for the next directive.
        const char *directive = ctx->out_argv[index];

        // Honor the special ABORT directive immediately (no further parsing).
        if (strcmp(directive, "ABORT") == 0) {
            const size_t extra = ctx->out_argc - index - 1;
            if (extra > 0) error("Ignoring %zu trailing output lines.", extra);
            break;
        }
        if (index == ctx->out_argc - 1) {
            error("Invalid trailing directive: %s", directive);
            break;
        }
        const size_t dir_argc = atoi(ctx->out_argv[index + 1]);
        const char **dir_argv = (const char **)(ctx->out_argv + index + 2);
        CHECK_ARGS("JAUNCH", "dir", dir_argc, 0, ctx->out_argc - index, dir_argv);
        index += 2 + dir_argc; // Advance index past this directive block.

        // If no runloop mode is set, give the platform a chance to set one.
        if (!ctx->runloop_mode) {
            runloop_config(ctx, directive);
            if (ctx->runloop_mode) {
                // The auto-configuration function has chosen a runloop mode.
                // Now we invoke an extra RUNLOOP directive to lock it in.
                int code = ctx_main_thread_available(ctx)
                    ? ctx_request_main_execution(ctx, "RUNLOOP", 0, NULL)
                    : execute_directive(ctx, "RUNLOOP", 0, NULL);

                if (code != SUCCESS) {
                    debug("[JAUNCH] RUNLOOP auto-directive failed with code %d", code);
                    exit_code |= code; // Remember non-zero error code bits.
                }
            }
        }

        // Determine execution context and execute directive
        int error_code;
        if (ctx_main_thread_available(ctx)) {
            // Main thread is available for directive execution.
            debug("[JAUNCH] Executing %s directive on main thread", directive);
            error_code = ctx_request_main_execution(ctx, directive, dir_argc, dir_argv);
        }
        else {
            // Main thread is busy (executing directive or blocked in runloop).
            // Execute the directive on the current (directive processing) thread.
            const char *reason = (ctx->state == STATE_RUNLOOP) ? "runloop is active" : "main thread is busy";
            debug("[JAUNCH] Executing %s directive on directive thread because %s", directive, reason);
            error_code = execute_directive(ctx, directive, dir_argc, dir_argv);
        }

        if (error_code != SUCCESS) {
            debug("[JAUNCH] %s directive failed with code %d, continuing with remaining directives", directive, error_code);
            exit_code |= error_code; // Remember non-zero error code bits.
        }
    }

    // Cleanup all runtime instances after processing all directives.
    debug("[JAUNCH] All directives processed, cleaning up runtimes");
    cleanup_jvm();
    cleanup_python();

    // Stop any active runloop.
    runloop_stop(ctx);

    // Signal completion to main thread.
    pthread_mutex_lock(&ctx->mutex);
    ctx->exit_code = exit_code;
    ctx_set_state(ctx, STATE_COMPLETE);
    ctx_signal_main(ctx);
    pthread_mutex_unlock(&ctx->mutex);

    debug("[JAUNCH] Directive thread returning with exit code %d", exit_code);
    return exit_code;
}

/* result=$(dirname "$argv0")/$subdir$command */
char *path(const char *argv0, const char *subdir, const char *command) {
    // Calculate string lengths.
    const char *last_slash = argv0 == NULL ? NULL : strrchr(argv0, SLASH[0]);
    size_t dir_len = (size_t)(last_slash == NULL ? 1 : last_slash - argv0);
    size_t subdir_len = subdir == NULL ? 0 : strlen(subdir);
    size_t command_len = strlen(command);
    size_t result_len = dir_len + 1 + subdir_len + command_len;

    // Allocate the result string.
    char *result = (char *)malloc(result_len + 1);
    if (result == NULL) return NULL;

    // Build the result string.
    if (last_slash == NULL) result[0] = '.';
    else strncpy(result, argv0, dir_len);
    result[dir_len] = SLASH[0];
    result[dir_len + 1] = '\0';
    if (subdir != NULL) strcat(result, subdir); // result += subdir
    strcat(result, command); // result += command

    return result;
}

int main(const int argc, const char *argv[]) {
    // Enable debug mode when --debug is an argument.
    for (size_t i = 0; i < argc; i++)
        if (strcmp(argv[i], "--debug") == 0) debug_mode = 1;
        else if (strcmp(argv[i], "--headless") == 0) headless_mode = 1;

    // Perform initial platform-specific setup.
    // * On Windows, initialize the console.
    // * On macOS, untranslocate Gatekeeper-mangled apps.
    setup(argc, argv);

    char *command = NULL;
    size_t search_path_count = sizeof(JAUNCH_SEARCH_PATHS) / sizeof(char *);
    for (size_t i = 0; i < search_path_count; i++) {
        // First, look for jaunch configurator with a `-<os>-<arch>` suffix.
        command = path(
            argc == 0 ? NULL : argv[0],
            JAUNCH_SEARCH_PATHS[i],
            "jaunch-" OS_NAME "-" OS_ARCH EXE_SUFFIX
        );
        if (file_exists(command)) break;
        debug("[JAUNCH] No configurator at %s", command);
        free(command);

        // If not found, look for jaunch configurator with fallback suffix.
        if (SUFFIX_FALLBACK[0] != '\0') {
            command = path(
                argc == 0 ? NULL : argv[0],
                JAUNCH_SEARCH_PATHS[i],
                "jaunch-" SUFFIX_FALLBACK EXE_SUFFIX
            );
            if (file_exists(command)) break;
            debug("[JAUNCH] No fallback configurator at %s", command);
            free(command);
        }

        // If not found, look for plain jaunch configurator with no suffix.
        command = path(
            argc == 0 ? NULL : argv[0],
            JAUNCH_SEARCH_PATHS[i],
            "jaunch" EXE_SUFFIX
        );
        if (file_exists(command)) break;
        debug("[JAUNCH] No plain configurator at %s", command);
        free(command);

        // Nothing at this search path; move on to the next one.
        command = NULL;
    }
    if (command == NULL) {
        error("Failed to locate the jaunch configurator program.");
        return ERROR_COMMAND_PATH;
    }
    debug("[JAUNCH] configurator command = %s", command);

    // Prepend original arguments with needed internal arguments.
    // For the moment, the only internal argument passed here is an
    // override of the target architecture, so that macos-arm64 and
    // windows-arm64 can launch in emulated x86-64 mode as appropriate.
    const int internal_argc = 1;
    const int extended_argc = internal_argc + argc;
    const char **extended_argv = malloc(extended_argc * sizeof(char*));
    if (extended_argv == NULL) {
        error("Failed to allocate memory (extended argv)");
        free(command);
        return ERROR_MALLOC;
    }
    extended_argv[0] = argv[0]; // executable path
    extended_argv[1] = "--jaunch-target-arch=" OS_ARCH;
    for (int i = 1; i < argc; i++) {
        extended_argv[internal_argc + i] = argv[i];
    }

    // Run external command to process the command line arguments.
    char **out_argv;
    size_t out_argc;
    int run_result = run_command((const char *)command, extended_argc, extended_argv, &out_argc, &out_argv);
    free(extended_argv);
    free(command);
    if (run_result != SUCCESS) return run_result;

    CHECK_ARGS("JAUNCH", "out", out_argc, 1, 99999, out_argv);
    // Maximum # of lines to treat as valid. ^^^^^
    // We could of course leave this unbounded, but pragmatically, the value
    // will probably never exceed this size -- it is more likely that a
    // programming error in the configurator yields a much-too-large argc
    // value, and it is better to fail fast than to access invalid memory.

    // Initialize thread context for directive processing
    ThreadContext ctx = {
        .mutex = PTHREAD_MUTEX_INITIALIZER,
        .cond = PTHREAD_COND_INITIALIZER,
        .state = STATE_WAITING,
        .out_argc = out_argc,
        .out_argv = out_argv,
        .pending_directive = NULL,
        .pending_argc = 0,
        .pending_argv = NULL,
        .directive_result = SUCCESS,
        .runloop_mode = NULL,
        .exit_code = SUCCESS,
    };

    // Set thread-local context pointer for runloop functions
    tls_thread_context = &ctx;

    debug("[JAUNCH] Starting directive processing on separate thread");
    pthread_t directive_thread;
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setscope(&attr, PTHREAD_SCOPE_SYSTEM);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
    pthread_create(&directive_thread, &attr, (void*(*)(void*))process_directives, &ctx);
    pthread_attr_destroy(&attr);

    // Main thread event loop - handle signals from directive thread.
    while (1) {
        pthread_mutex_lock(&ctx.mutex);

        // Wait for state to change from WAITING
        ctx_wait_for_state_change(&ctx, STATE_WAITING);

        if (ctx.state == STATE_EXECUTING) {
            debug("[JAUNCH-MAIN] Executing directive: %s", ctx.pending_directive);

            // Release mutex while executing the directive (which may call ctx_signal_early_completion)
            pthread_mutex_unlock(&ctx.mutex);

            // Execute the directive.
            int result = execute_directive(
                &ctx,
                ctx.pending_directive,
                ctx.pending_argc,
                ctx.pending_argv
            );

            // Re-acquire mutex to update state
            pthread_mutex_lock(&ctx.mutex);
            ctx.directive_result = result;

            // Signal completion back to directive thread.
            // Note: STATE_RUNLOOP may be set by execute_directive via ctx_signal_early_completion
            if (ctx.state == STATE_EXECUTING) {
                ctx_set_state(&ctx, STATE_WAITING);
            }
            ctx_signal_main(&ctx);
            pthread_mutex_unlock(&ctx.mutex);
        }
        else if (ctx.state == STATE_RUNLOOP) {
            debug("[JAUNCH-MAIN] Main thread in runloop state - continuing to wait");
            pthread_mutex_unlock(&ctx.mutex);
            // Continue waiting - the runloop will eventually exit and change state
        }
        else if (ctx.state == STATE_COMPLETE) {
            pthread_mutex_unlock(&ctx.mutex);
            debug("[JAUNCH-MAIN] Exiting directives loop");
            break;
        }
        else {
            pthread_mutex_unlock(&ctx.mutex);
            error("[JAUNCH-MAIN] Unknown thread state encountered: %d", ctx.state);
            break;
        }
    }

    // Wait for directive processing thread to complete.
    pthread_join(directive_thread, NULL);
    int exit_code = ctx.exit_code;
    debug("[JAUNCH] Directives processing complete");

    // Clean up thread-local context pointer
    tls_thread_context = NULL;

    // Clean up.
    for (size_t i = 0; i < out_argc; i++) {
        free(out_argv[i]);
    }
    free(out_argv);

    // Do any final platform-specific cleanup.
    teardown();

    return exit_code;
}
