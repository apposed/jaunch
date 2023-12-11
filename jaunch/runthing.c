#include <stdio.h>
#include <stdlib.h>

int main() {
    // Command to execute (replace "ls -l" with your desired command)
    const char *command = "ls -l";

    // Open a pipe to the process
    FILE *pipeStream = popen(command, "r");
    if (pipeStream == NULL) {
        perror("popen");
        exit(EXIT_FAILURE);
    }

    // Buffer to store the output
    char buffer[1024];
    size_t bytesRead;

    // Read the output from the process
    while ((bytesRead = fread(buffer, 1, sizeof(buffer), pipeStream)) > 0) {
        fwrite(buffer, 1, bytesRead, stdout);  // Display the output
    }

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

    return 0;
}
