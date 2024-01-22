#include <dlfcn.h>
#include <sys/wait.h>

#include "common.h"

#define SLASH '/'

int run_command(const char *command,
    const char *input[], size_t numInput,
    char ***output, size_t *numOutput)
{
    // Create pipes for stdin and stdout
    int stdinPipe[2];
    int stdoutPipe[2];

    debug("[JAUNCH] run_command: opening pipes to/from configurator");
    if (pipe(stdinPipe) == -1 || pipe(stdoutPipe) == -1) {
      error("Failed to open pipes to/from configurator");
      return ERROR_PIPE;
    }

    // Fork to create a child process
    pid_t pid = fork();

    if (pid == -1) {
      error("Failed to fork the process");
      return ERROR_FORK;
    }

    if (pid == 0) { // Child process
        // Close unused ends of the pipes
        close(stdinPipe[1]);
        close(stdoutPipe[0]);

        // Redirect stdin and stdout
        dup2(stdinPipe[0], STDIN_FILENO);
        dup2(stdoutPipe[1], STDOUT_FILENO);

        // Close duplicated ends
        close(stdinPipe[0]);
        close(stdoutPipe[1]);

        // Execute the command
        execlp(command, command, "-", (char *)NULL);

        // If execlp fails
        error("Failed to execute the jaunch configurator");
        return ERROR_EXECLP;
    }
    else { // Parent process
        // Close unused ends of the pipes
        close(stdinPipe[0]);
        close(stdoutPipe[1]);

        // Write to the child process's stdin
        debug("[JAUNCH] run_command: writing to jaunch stdin");
        // Passing the input line count as the first line tells the child process what
        // to expect, so that it can stop reading from stdin once it has received
        // those lines, even though the pipe is not yet closed. This avoids deadlocks.
        dprintf(stdinPipe[1], "%zu\n", numInput);
        debug("[JAUNCH] run_command: wrote numInput: %d", numInput);
        for (size_t i = 0; i < numInput; i++) {
            dprintf(stdinPipe[1], "%s\n", input[i]);
            debug("[JAUNCH] run_command: wrote input #%d: %s", i, input[i]);
        }

        // Close the write end of stdin to signal the end of input
        close(stdinPipe[1]);
        debug("[JAUNCH] run_command: closed jaunch stdin pipe");

        // Read from the child process's stdout
        char buffer[1024];
        size_t bytesRead;
        size_t totalBytesRead = 0;
        size_t bufferSize = 1024;
        char *outputBuffer = malloc(bufferSize);

        if (outputBuffer == NULL) {
          error("Memory allocation failed (initial buffer)");
          return ERROR_MALLOC;
        }

        while ((bytesRead = read(stdoutPipe[0], buffer, sizeof(buffer))) > 0) {
            if (totalBytesRead + bytesRead > bufferSize) {
                bufferSize *= 2;
                outputBuffer = realloc(outputBuffer, bufferSize);
                if (outputBuffer == NULL) {
                  error("Memory reallocation failed (run_command)");
                  return ERROR_REALLOC;
                }
            }
            memcpy(outputBuffer + totalBytesRead, buffer, bytesRead);
            totalBytesRead += bytesRead;
        }

        // Close the read end of stdout
        close(stdoutPipe[0]);
        debug("[JAUNCH] run_command: closed jaunch stdout pipe");

        // Wait for the child process to finish
        if (waitpid(pid, NULL, 0) == -1) {
          error("Failed waiting for Jaunch termination");
          return ERROR_WAITPID;
        }

        // Return the output buffer and the number of lines
        *output = NULL;
        *numOutput = 0;
        int split_result = SUCCESS;
        if (totalBytesRead > 0) split_result = split_lines(outputBuffer, "\n", output, numOutput);
        free(outputBuffer);
        if (split_result != SUCCESS) return split_result;
    }
    return SUCCESS;
}
