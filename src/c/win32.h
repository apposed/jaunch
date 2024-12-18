#include <windows.h>

#include "common.h"

#define OS_NAME "windows"
#define SLASH "\\"
#define EXE_SUFFIX ".exe"

static BOOL hasConsole = FALSE;
static BOOL isConsoleOwner = FALSE;

static void setupConsole() {
    // First, try to attach to an existing console
    if (AttachConsole(ATTACH_PARENT_PROCESS)) {
        hasConsole = TRUE;

        // Reopen stdin/stdout/stderr to connect to the console
        freopen("CONIN$", "r", stdin);
        freopen("CONOUT$", "w", stdout);
        freopen("CONOUT$", "w", stderr);

        // Get and set proper console mode for input
        HANDLE hStdin = GetStdHandle(STD_INPUT_HANDLE);
        if (hStdin != INVALID_HANDLE_VALUE) {
            DWORD mode;
            if (GetConsoleMode(hStdin, &mode)) {
                // Enable standard input processing
                mode |= ENABLE_PROCESSED_INPUT | ENABLE_LINE_INPUT | ENABLE_ECHO_INPUT;
                SetConsoleMode(hStdin, mode);
            }
        }

        return;
    }

    // If that failed, see if we already have a console
    HANDLE console = GetStdHandle(STD_OUTPUT_HANDLE);
    if (console != INVALID_HANDLE_VALUE && console != NULL) {
        hasConsole = TRUE;
        return;
    }

    // No console needed/available
    // We could create a new console here with AllocConsole() if needed
    // But for now, we'll just run without one
    hasConsole = FALSE;
}

// Helper function to cleanup console if we created one
static void cleanupConsole() {
    if (isConsoleOwner) {
        FreeConsole();
    }
}

void handle_error(const char* errorMessage) {
    fprintf(stderr, "%s (error %lu)\n", errorMessage, GetLastError());
    exit(1);
}

void write_line(HANDLE stdinWrite, const char *input) {
    // Copy the input string and add a newline
    size_t inputLength = strlen(input);
    char *line = (char *)malloc(inputLength + 2);  // +1 for newline, +1 for null terminator
    strcpy(line, input);
    strcat(line, "\n");

    // Write the string with newline to the pipe
    DWORD bytesWritten;
    if (!WriteFile(stdinWrite, line, inputLength + 1, &bytesWritten, NULL))
        handle_error("Error writing to stdin");

    // Free allocated memory
    free(line);
}

int file_exists(const char *path) {
    return GetFileAttributesA(path) != INVALID_FILE_ATTRIBUTES;
}

// ===========================================================
//              common.h FUNCTION IMPLEMENTATIONS
// ===========================================================

void *lib_open(const char *path) { return LoadLibrary(path); }
void *lib_sym(void *library, const char *symbol) { return GetProcAddress(library, symbol); }
void lib_close(void *library) { FreeLibrary(library); }
char *lib_error() {
    DWORD errorMessageID = GetLastError();
    if (errorMessageID == 0) return NULL; // No error
    LPSTR message = NULL;
    FormatMessageA(
        FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
        NULL,
        errorMessageID,
        MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
        (LPSTR)&message,
        0,
        NULL
    );
    return message;
}

/*
 * Windows-style function to launch a command in a separate process,
 * and harvest its output from the standard output stream.
 *
 * As opposed to the POSIX (Linux and macOS) implementation in posix.h.
 */
int run_command(const char *command,
    size_t numInput, const char *input[],
    size_t *numOutput, char ***output)
{
    // Create pipes for stdin and stdout
    HANDLE stdinRead, stdinWrite, stdoutRead, stdoutWrite;
    SECURITY_ATTRIBUTES sa = { sizeof(SECURITY_ATTRIBUTES), NULL, TRUE };

    debug("run_command: opening pipes to/from jaunch");
    if (!CreatePipe(&stdinRead, &stdinWrite, &sa, 0) ||
        !CreatePipe(&stdoutRead, &stdoutWrite, &sa, 0))
    {
        handle_error("Error creating pipes");
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

    // Add CREATE_NO_WINDOW flag to prevent console window from appearing
    DWORD createFlags = CREATE_NO_WINDOW;
    char *commandPlusDash = malloc(strlen(command) + 3);
    if (commandPlusDash == NULL) {
        error("Failed to allocate memory (command plus dash)");
        return ERROR_MALLOC;
    }
    strcpy(commandPlusDash, command);
    // NB: We pass a single "-" argument to indicate to the jaunch
    // configurator that it should harvest the actual input arguments
    // from the stdin stream. We do this to avoid issues with quoting.
    strcat(commandPlusDash, " -");
    if (!CreateProcess(NULL, (LPSTR)commandPlusDash, NULL, NULL, TRUE,
        createFlags, NULL, NULL, &si, &pi))
    {
        free(commandPlusDash);
        handle_error("Error creating process");
    }
    free(commandPlusDash);

    // Close unnecessary handles
    CloseHandle(stdinRead);
    CloseHandle(stdoutWrite);

    // Write to the child process's stdin
    debug("run_command: writing to jaunch stdin");
    // Passing the input line count as the first line tells the child process what
    // to expect, so that it can stop reading from stdin once it has received
    // those lines, even though the pipe is not yet closed. This avoids deadlocks.
    char *numInputString = (char *)malloc(21);
    if (numInputString == NULL) {
        error("Failed to allocate memory (input line count)");
        return ERROR_MALLOC;
    }
    snprintf(numInputString, 21, "%zu", numInput);
    write_line(stdinWrite, numInputString);
    free(numInputString);
    for (size_t i = 0; i < numInput; i++)
        write_line(stdinWrite, input[i]);

    // Close the stdin write handle to signal end of input
    CloseHandle(stdinWrite);
    debug("run_command: closed jaunch stdin pipe");

    // Read from the child process's stdout
    char buffer[1024];
    DWORD bytesRead;
    DWORD totalBytesRead = 0;
    size_t bufferSize = 1024;
    char *outputBuffer = malloc(bufferSize);

    if (outputBuffer == NULL) {
        error("Failed to allocate memory (output buffer)");
        return ERROR_MALLOC;
    }

    while (ReadFile(stdoutRead, buffer, sizeof(buffer), &bytesRead, NULL) && bytesRead > 0) {
        if (totalBytesRead + bytesRead > bufferSize) {
            bufferSize *= 2;
            outputBuffer = realloc(outputBuffer, bufferSize);
            if (outputBuffer == NULL) {
                error("Failed to reallocate memory (output buffer)");
                return ERROR_REALLOC;
            }
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

void init_threads() {}

void show_alert(const char *title, const char *message) {
    MessageBox(NULL, message, title, MB_ICONERROR);
}

/*
 * The Windows way of launching a runtime.
 *
 * It simply calls the given launch function directly. Easy peasy.
 * Except... we need to wrangle Windows consoles. Good times!
 */
int launch(const LaunchFunc launch_runtime,
    const size_t argc, const char **argv)
{
    setupConsole();
    int result = launch_runtime(argc, argv);
    cleanupConsole();
    return result;
}
