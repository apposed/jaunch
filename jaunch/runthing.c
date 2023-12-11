#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Function to run a command and capture its stdout lines
char **runCommand(const char *command, size_t *numLines) {
    // Open a pipe to the process
    FILE *pipeStream = popen(command, "r");
    if (pipeStream == NULL) {
        perror("popen");
        exit(EXIT_FAILURE);
    }

    // Buffer to store the output
    char buffer[1024];
    size_t totalSize = 0;

    // Read the entire output into a single string
    char *outputString = NULL;
    size_t bytesRead;
    while ((bytesRead = fread(buffer, 1, sizeof(buffer), pipeStream)) > 0) {
        outputString = realloc(outputString, totalSize + bytesRead + 1);
        if (outputString == NULL) {
            perror("realloc");
            exit(EXIT_FAILURE);
        }
        memcpy(outputString + totalSize, buffer, bytesRead);
        totalSize += bytesRead;
    }

    // Null-terminate the output string
    outputString[totalSize] = '\0';

    // Close the pipe
    int status = pclose(pipeStream);
    if (status == -1) {
        perror("pclose");
        exit(EXIT_FAILURE);
    } else {
        // Check if the child process terminated normally
        if (WIFEXITED(status)) {
            printf("Child process exited with status %d\n", WEXITSTATUS(status));
        } else {
            printf("Child process did not exit normally\n");
        }
    }

    // Count the number of lines in the output
    size_t lineCount = 0;
    for (size_t i = 0; i < totalSize; i++) {
        if (outputString[i] == '\n') {
            lineCount++;
        }
    }

    // Allocate an array of strings to store the lines
    char **lines = (char **)malloc(lineCount * sizeof(char *));
    if (lines == NULL) {
        perror("malloc");
        exit(EXIT_FAILURE);
    }

    // Split the output string into lines
    size_t lineIndex = 0;
    char *token = strtok(outputString, "\n");
    while (token != NULL) {
        lines[lineIndex] = strdup(token);
        if (lines[lineIndex] == NULL) {
            perror("strdup");
            exit(EXIT_FAILURE);
        }
        lineIndex++;
        token = strtok(NULL, "\n");
    }

    *numLines = lineCount;
    free(outputString); // Free the temporary string
    return lines;
}

int main() {
    const char *command = "ls -l";

    size_t numLines;
    char **outputLines = runCommand(command, &numLines);

    // Display the output lines
    for (size_t i = 0; i < numLines; i++) {
        printf("Line %zu: %s\n", i + 1, outputLines[i]);
        free(outputLines[i]); // Free each string
    }

    // Free the array of lines
    free(outputLines);

    return 0;
}
