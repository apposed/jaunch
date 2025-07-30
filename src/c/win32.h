#include <windows.h>
#include <tlhelp32.h>

#include "common.h"

#define OS_NAME "windows"
#define SLASH "\\"
#define EXE_SUFFIX ".exe"

#ifdef __aarch64__
    // Kotlin Native does not yet support targeting windows-arm64.
    // But windows-arm64 has good support for emulating windows-x64.
    // Therefore, we let windows-arm64 fall back to windows-x64 for
    // the purpose of discovering a suitable configurator to call.
    #define SUFFIX_FALLBACK "windows-x64"
#else
    #define SUFFIX_FALLBACK ""
#endif

// ===========================================================
//                      HELPER FUNCTIONS
// ===========================================================

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

typedef enum {
    PARENT_UNKNOWN,
    PARENT_CMD,
    PARENT_POWERSHELL,
    PARENT_BASH,
    PARENT_EXPLORER,
    PARENT_OTHER
} ParentProcessType;

static ParentProcessType getParentProcessType() {
    DWORD parentPID = 0;
    ParentProcessType result = PARENT_UNKNOWN;

    // Get the parent process ID
    HANDLE snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snapshot == INVALID_HANDLE_VALUE) return result;

    PROCESSENTRY32W entry;
    entry.dwSize = sizeof(entry);
    DWORD currentPID = GetCurrentProcessId();

    if (Process32FirstW(snapshot, &entry)) {
        do {
            if (entry.th32ProcessID == currentPID) {
                parentPID = entry.th32ParentProcessID;
                break;
            }
        }
        while (Process32NextW(snapshot, &entry));
    }

    if (parentPID) {
        // Reset to start of process list
        Process32FirstW(snapshot, &entry);
        do {
            if (entry.th32ProcessID == parentPID) {
                debug("[JAUNCH-WIN32] PARENT PROCESS = %S", entry.szExeFile);
                if (_wcsicmp(entry.szExeFile, L"cmd.exe") == 0) {
                    result = PARENT_CMD;
                }
                else if (_wcsicmp(entry.szExeFile, L"powershell.exe") == 0 ||
                         _wcsicmp(entry.szExeFile, L"pwsh.exe") == 0)
                {
                    result = PARENT_POWERSHELL;
                }
                else if (_wcsicmp(entry.szExeFile, L"bash.exe") == 0) {
                    result = PARENT_BASH;
                }
                else if (_wcsicmp(entry.szExeFile, L"explorer.exe") == 0) {
                    result = PARENT_EXPLORER;
                }
                else {
                    result = PARENT_OTHER;
                }
                break;
            }
        }
        while (Process32NextW(snapshot, &entry));
    }

    CloseHandle(snapshot);
    return result;
}

/** Thread helper function to read from configurator process's stderr. */
DWORD WINAPI ReadStderrThread(LPVOID param) {
    HANDLE stderrRead = (HANDLE)param;
    char buffer[1024];
    DWORD bytesRead;

    while (ReadFile(stderrRead, buffer, sizeof(buffer), &bytesRead, NULL)) {
        if (bytesRead <= 0) continue;

        // Write directly to the main process stderr
        HANDLE parentStderr = GetStdHandle(STD_ERROR_HANDLE);
        WriteFile(parentStderr, buffer, bytesRead, NULL, NULL);
    }
    return 0;
}

// ===========================================================
//              common.h FUNCTION IMPLEMENTATIONS
// ===========================================================

void setup(const int argc, const char *argv[]) {
    // Ahh, the Windows console. Good times!
    // See doc/WINDOWS.md for why this logic is here.

    debug("[JAUNCH-WIN32] CONFIGURING CONSOLE");

    // First, try to attach to an existing console
    if (AttachConsole(ATTACH_PARENT_PROCESS)) {
        debug("[JAUNCH-WIN32] ATTACHED TO PARENT CONSOLE");

        // Glean the parent process type.
        ParentProcessType parentType = getParentProcessType();

        // Reopen stdin/stdout/stderr to connect to the console.
        if (parentType != PARENT_BASH) {
            // Calling freopen when launched from a Git Bash prompt hoses the
            // output -- maybe because it redirects it to a non-bash console?
            // Conversely, if we're running from CMD or PowerShell, and we
            // *don't* freopen the streams, they will not function properly.
            // This, we do this step iff we're *not* running from Git Bash.
            //
            // Unfortunately, this approach is not foolproof: if running bash
            // from inside a Command Prompt or PowerShell, the logic fails to
            // produce any output whatsoever. In that case, we *do* need to
            // freopen the streams to see output from the launcher process...
            // but even if we do that, in that case, we won't see stderr from
            // the configurator subprocess. So I'm throwing up my hands here.

            freopen("CONIN$", "r", stdin);
            freopen("CONOUT$", "w", stdout);
            freopen("CONOUT$", "w", stderr);
            debug("[JAUNCH-WIN32] REOPENED CONSOLE STREAMS");

            // NB: In debug mode, we call getParentProcessType() again so
            // that the name of the parent process gets emitted to stderr,
            // because we probably didn't see it last time due to the console
            // not yet being fully connected.
            if (debug_mode) getParentProcessType();
        }

        // Warn if we're a GUI app running directly from a Windows shell.
        // In theory, this check will always succeed, because in any other
        // scenario the AttachConsole call above would have failed, and this
        // case logic here wouldn't even be triggered. But this console
        // logic has many edge cases, so let's check anyway, just in case.
        DWORD binaryType;
        const char* argv0 = argv[0];
        if (GetBinaryTypeA(argv0, &binaryType) && (binaryType == SCS_32BIT_BINARY || binaryType == SCS_64BIT_BINARY)) {
            switch (parentType) {
                case PARENT_CMD:
                    error("");
                    error("===========================================================");
                    error("WARNING: GUI program launched from Command Prompt.");
                    error("For proper console behavior, make sure to use:");
                    error("    start /wait %s", argv0);
                    error("Or launch from inside a batch script, or from Git Bash.");
                    error("===========================================================");
                    break;
                case PARENT_POWERSHELL:
                    error("");
                    error("=======================================================");
                    error("WARNING: GUI program launched from PowerShell.");
                    error("For proper console behavior, make sure to use:");
                    error("    Start-Process -Wait %s", argv0);
                    error("Or launch from inside a batch script, or from Git Bash.");
                    error("=======================================================");
                    break;
                case PARENT_BASH:
                    debug("[JAUNCH-WIN32] Running from bash; all is well.");
                    break;
                case PARENT_EXPLORER:
                    debug("[JAUNCH-WIN32] Running from Explorer; all is well.");
                    break;
                case PARENT_OTHER:
                    error("");
                    error("==========================================================");
                    error("WARNING: GUI program launched from unknown parent process.");
                    error("Console output may be unreliable.");
                    error("==========================================================");
                    break;
                case PARENT_UNKNOWN:
                    debug("[JAUNCH-WIN32] Failed to detect parent process type.");
                    break;
            }
        }
    }

    // Check whether the console handles are functional.
    HANDLE hStdin = GetStdHandle(STD_INPUT_HANDLE);
    HANDLE hStdout = GetStdHandle(STD_OUTPUT_HANDLE);
    HANDLE hStderr = GetStdHandle(STD_ERROR_HANDLE);
    if (hStdin != NULL && hStdin != INVALID_HANDLE_VALUE) debug("[JAUNCH-WIN32] STDIN IS VALID");
    if (hStdout != NULL && hStdout != INVALID_HANDLE_VALUE) debug("[JAUNCH-WIN32] STDOUT IS VALID");
    if (hStderr != NULL && hStderr != INVALID_HANDLE_VALUE) debug("[JAUNCH-WIN32] STDERR IS VALID");
}

void teardown() {
    // Note: Since we always attach to an existing console, we never own our
    // console, meaning we are not the one responsible for cleaning it up.
    // But if we ever add a case that calls AllocConsole, we will need a
    // corresponding FreeConsole() here to dispose of it.
}

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
    HANDLE stdinRead, stdinWrite, stdoutRead, stdoutWrite, stderrRead, stderrWrite;
    SECURITY_ATTRIBUTES sa = { sizeof(SECURITY_ATTRIBUTES), NULL, TRUE };

    debug("[JAUNCH-WIN32] OPENING STREAMS TO/FROM SUBPROCESS");
    if (!CreatePipe(&stdinRead, &stdinWrite, &sa, 0) ||
        !CreatePipe(&stdoutRead, &stdoutWrite, &sa, 0) ||
        !CreatePipe(&stderrRead, &stderrWrite, &sa, 0))
    {
        handle_error("Error creating pipes");
    }

    // Set the properties of the process to start
    STARTUPINFO si = { sizeof(STARTUPINFO) };
    PROCESS_INFORMATION pi;

    // Specify that the process should inherit the handles
    si.hStdInput = stdinRead;
    si.hStdOutput = stdoutWrite;
    si.hStdError = stderrWrite;
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
    CloseHandle(stderrWrite);

    // Write to the child process's stdin
    debug("[JAUNCH-WIN32] WRITING TO SUBPROCESS STDIN");
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
    debug("[JAUNCH-WIN32] CLOSED SUBPROCESS STDIN STREAM");

    // Read from the child process's stderr in its own thread
    HANDLE hThread = CreateThread(NULL, 0, ReadStderrThread, stderrRead, 0, NULL);

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

    // Wait for stderr thread to terminate
    if (hThread) {
        debug("[JAUNCH-WIN32] WAITING FOR STDERR THREAD");
        WaitForSingleObject(hThread, INFINITE);
        CloseHandle(hThread);
        debug("[JAUNCH-WIN32] STDERR THREAD COMPLETE");
    }

    // Close handles
    debug("[JAUNCH-WIN32] CLOSING OUTPUT STREAM HANDLES");
    CloseHandle(stdoutRead);
    CloseHandle(stderrRead);
    debug("[JAUNCH-WIN32] CLOSING SUBPROCESS HANDLES");
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    debug("[JAUNCH-WIN32] ALL HANDLES CLOSED");

    // Return the output buffer and the number of lines
    *output = NULL;
    *numOutput = 0;
    int split_result = SUCCESS;
    if (totalBytesRead > 0) split_result = split_lines(outputBuffer, "\r\n", output, numOutput);
    free(outputBuffer);
    return split_result;
}

void init_threads() {}

/*
 * The Windows way of displaying a graphical error message.
 *
 * Thank you, Microsoft, for making this task so simple! :-)
 */
void show_alert(const char *title, const char *message) {
    MessageBox(NULL, message, title, MB_ICONERROR);
}

/*
 * The Windows way of launching a runtime.
 *
 * It simply calls the given launch function directly. Easy peasy.
 */
int launch(const LaunchFunc launch_runtime,
    const size_t argc, const char **argv)
{
    return launch_runtime(argc, argv);
}
