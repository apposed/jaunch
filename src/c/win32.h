#include <windows.h>

#include "utils.h"

const char* JAUNCH_EXE = "jaunch.exe -";

void dlclose(void* library) { FreeLibrary(library); }
char* dlerror() { return "error" /*TODO: GetLastError()*/; }

void handleError(const char* errorMessage) {
	fprintf(stderr, "%s (error %lu)\n", errorMessage, GetLastError());
	exit(1);
}

void writeLine(HANDLE stdinWrite, const char *input) {
    DWORD bytesWritten;

    // Calculate the length of the input string
    size_t inputLength = strlen(input);

    // Allocate memory for the string with newline
    char *line = (char *)malloc(inputLength + 2);  // +1 for newline, +1 for null terminator

    // Copy the input string and add a newline
    strcpy(line, input);
    strcat(line, "\n");

    // Write the string with newline to the pipe
    if (!WriteFile(stdinWrite, line, inputLength + 1, &bytesWritten, NULL))
			handleError("Error writing to stdin");

    // Free allocated memory
    free(line);
}

int run_command(const char *command,
	const char *input[], size_t numInput,
	char ***output, size_t *numOutput)
{
	// Create pipes for stdin and stdout
	HANDLE stdinRead, stdinWrite, stdoutRead, stdoutWrite;
	SECURITY_ATTRIBUTES sa = { sizeof(SECURITY_ATTRIBUTES), NULL, TRUE };

	debug("run_command: opening pipes to/from jaunch");
	if (!CreatePipe(&stdinRead, &stdinWrite, &sa, 0) ||
		!CreatePipe(&stdoutRead, &stdoutWrite, &sa, 0))
	{
		handleError("Error creating pipes");
	}

	// Set the properties of the process to start
	STARTUPINFO si = { sizeof(STARTUPINFO) };
	PROCESS_INFORMATION pi;

	// Specify that the process should inherit the handles
	si.hStdInput = stdinRead;
	si.hStdOutput = stdoutWrite;
	si.hStdError = GetStdHandle(STD_ERROR_HANDLE);
	si.dwFlags |= STARTF_USESTDHANDLES;

	// Create the subprocess
	if (!CreateProcess(NULL, (LPSTR)command, NULL, NULL, TRUE, 0, NULL, NULL, &si, &pi)) {
		handleError("Error creating process");
	}

	// Close unnecessary handles
	CloseHandle(stdinRead);
	CloseHandle(stdoutWrite);

	// Write to the child process's stdin
	debug("run_command: writing to jaunch stdin");
	// Passing the input line count as the first line tells the child process what
	// to expect, so that it can stop reading from stdin once it has received
	// those lines, even though the pipe is not yet closed. This avoids deadlocks.
	char *numInputString = (char *)malloc(21);
	if (numInputString == NULL) { error("malloc"); return ERROR_MALLOC; }
	snprintf(numInputString, 21, "%zu", numInput);
	writeLine(stdinWrite, numInputString);
	free(numInputString);
	for (size_t i = 0; i < numInput; i++)
		writeLine(stdinWrite, input[i]);

	// Close the stdin write handle to signal end of input
	CloseHandle(stdinWrite);
	debug("run_command: closed jaunch stdin pipe");

	// Read from the child process's stdout
	char buffer[1024];
	DWORD bytesRead;
	DWORD totalBytesRead = 0;
	size_t bufferSize = 1024;
	char *outputBuffer = malloc(bufferSize);

	if (outputBuffer == NULL) { error("malloc"); return ERROR_MALLOC; }

	while (ReadFile(stdoutRead, buffer, sizeof(buffer), &bytesRead, NULL) && bytesRead > 0) {
		if (totalBytesRead + bytesRead > bufferSize) {
			bufferSize *= 2;
			outputBuffer = realloc(outputBuffer, bufferSize);
			if (outputBuffer == NULL) { error("realloc"); return ERROR_REALLOC; }
		}
		memcpy(outputBuffer + totalBytesRead, buffer, bytesRead);
		totalBytesRead += bytesRead;
	}

	// Close handles
	CloseHandle(stdoutRead);
	debug("run_command: closed jaunch stdout pipe");
	CloseHandle(pi.hProcess);
	CloseHandle(pi.hThread);

	// Return the output buffer and the number of lines
	*output = NULL;
	*numOutput = 0;
	int split_result = SUCCESS;
	if (totalBytesRead > 0) split_result = split_lines(outputBuffer, "\r\n", output, numOutput);
	free(outputBuffer);
	return split_result;
}
