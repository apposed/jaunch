#include <dlfcn.h>    // for dlclose, dlopen, dlsym
#include <limits.h>   // for PATH_MAX
#include <stdio.h>    // for dprintf
#include <stdlib.h>   // for NULL, size_t, free
#include <string.h>   // for strdup
#include <unistd.h>   // for access

#include <sys/wait.h>

#include "logging.h"
#include "common.h"

#define SLASH "/"
#define EXE_SUFFIX ""
#define SUFFIX_FALLBACK ""

// -- Helper functions --

int file_exists(const char *path) {
    return access(path, F_OK) == 0;
}

// ===========================================================
//              common.h FUNCTION IMPLEMENTATIONS
// ===========================================================

void *lib_open(const char *path) {
    // We use here RTLD_NOW | RTLD_GLOBAL because:
    // - We want to fail fast if runtime libraries are broken (RTLD_NOW).
    // - Runtimes need their symbols globally available for plugins (RTLD_GLOBAL).
    return dlopen(path, RTLD_NOW | RTLD_GLOBAL);
}
void *lib_sym(void *library, const char *symbol) { return dlsym(library, symbol); }
void lib_close(void *library) { dlclose(library); }
char *lib_error() { return dlerror(); }

char *canonical_path(const char *path) {
    if (path == NULL) return NULL;

    // Allocate buffer for the resolved path.
    char *resolved = (char *)malloc_or_die(PATH_MAX, "resolved path");

    // Use realpath to resolve symlinks and get canonical path.
    char *result = realpath(path, resolved);
    if (result == NULL) {
        // If realpath fails, fall back to using path as-is.
        free(resolved);
        return strdup(path);
    }

    return resolved;
}

/*
 * POSIX-style function to launch a command in a separate process,
 * and harvest its output from the standard output stream.
 *
 * As opposed to the Windows implementation in win32.h.
 */
void run_command(const char *command,
    size_t numInput, const char *input[],
    size_t *numOutput, char ***output)
{
    // Create pipes for stdin and stdout.
    int stdinPipe[2];
    int stdoutPipe[2];

    LOG_DEBUG("POSIX", "run_command: opening pipes to/from configurator");
    if (pipe(stdinPipe) == -1 || pipe(stdoutPipe) == -1) {
        DIE(ERROR_PIPE, "Failed to open pipes to/from configurator");
    }

    // Fork to create a child process.
    pid_t pid = fork();

    if (pid == -1) DIE(ERROR_FORK, "Failed to fork the process");

    if (pid == 0) { // Child process
        // Close unused ends of the pipes.
        close(stdinPipe[1]);
        close(stdoutPipe[0]);

        // Redirect stdin and stdout.
        dup2(stdinPipe[0], STDIN_FILENO);
        dup2(stdoutPipe[1], STDOUT_FILENO);

        // Close duplicated ends.
        close(stdinPipe[0]);
        close(stdoutPipe[1]);

        // Execute the command.
        // NB: We pass a single "-" argument to indicate to the jaunch
        // configurator that it should harvest the actual input arguments
        // from the stdin stream. We do this to avoid issues with quoting.
        execlp(command, command, "-", (char *)NULL);

        // Note: If we reach this point, execlp has failed.
        DIE(ERROR_EXEC, "Failed to execute the jaunch configurator");
    } else { // Parent process
        // Close unused ends of the pipes.
        close(stdinPipe[0]);
        close(stdoutPipe[1]);

        // Write to the child process's stdin.
        LOG_DEBUG("POSIX", "run_command: writing to jaunch stdin");
        // Passing the input line count as the first line tells the child process what
        // to expect, so that it can stop reading from stdin once it has received
        // those lines, even though the pipe is not yet closed. This avoids deadlocks.
        dprintf(stdinPipe[1], "%zu\n", numInput);
        LOG_DEBUG("POSIX", "run_command: wrote numInput: %zu", numInput);
        for (size_t i = 0; i < numInput; i++) {
            dprintf(stdinPipe[1], "%s\n", input[i]);
            LOG_DEBUG("POSIX", "run_command: wrote input #%zu: %s", i, input[i]);
        }

        // Close the write end of stdin to signal the end of input.
        close(stdinPipe[1]);
        LOG_DEBUG("POSIX", "run_command: closed jaunch stdin pipe");

        // Read from the child process's stdout.
        char buffer[1024];
        size_t bytesRead;
        size_t totalBytesRead = 0;
        size_t bufferSize = 1024;
        char *outputBuffer = malloc_or_die(bufferSize, "initial buffer");

        while ((bytesRead = read(stdoutPipe[0], buffer, sizeof(buffer))) > 0) {
            append_to_buffer(&outputBuffer, &bufferSize, &totalBytesRead, buffer, bytesRead);
        }

        // Close the read end of stdout.
        close(stdoutPipe[0]);
        LOG_DEBUG("POSIX", "run_command: closed jaunch stdout pipe");

        // Wait for the child process to finish.
        if (waitpid(pid, NULL, 0) == -1) {
            DIE(ERROR_WAITPID, "Failed waiting for Jaunch termination");
        }

        // Return the output buffer and the number of lines.
        *output = NULL;
        *numOutput = 0;
        if (totalBytesRead > 0) {
            outputBuffer[totalBytesRead] = '\0'; // Null-terminate before parsing.
            split_lines(outputBuffer, "\n", output, numOutput);
        }
        free(outputBuffer);
    }
}
