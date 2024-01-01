/*
 * A minimal but general-purpose C program to launch Java in the same process.
 *
 * It has two functions:
 *
 * launch_jvm:
 *   1. path to libjvm
 *   2. argc + argv for the jvm
 *   3. main class to run
 *   4. argc + argv for the main invocation
 * And it loads that libjvm and invokes the main method with those parameters.
 *
 * run_command:
 *   1. path to configurator executable
 *   2. argv list, to be passed to the configurator via stdin, one per line
 * It invokes that configurator executable in its own process, and waits for
 * the process to complete. The configurator produces output suitable for
 * passing to the `launch_jvm` function above. and then calls the low-level
 * function with the outputs.
 */

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>

#ifdef WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#include <sys/wait.h>
#endif

#include "jni.h"

#define SUCCESS 0
#define ERROR_DLOPEN 1
#define ERROR_DLSYM 2
#define ERROR_CREATE_JAVA_VM 3
#define ERROR_FIND_CLASS 4
#define ERROR_GET_STATIC_METHOD_ID 5
#define ERROR_PIPE 6
#define ERROR_FORK 7
#define ERROR_EXECLP 8
#define ERROR_MALLOC 9
#define ERROR_REALLOC 10
#define ERROR_WAITPID 11
#define ERROR_REALLOC2 12
#define ERROR_STRDUP 13
#define ERROR_COMMAND_PATH 14
#define ERROR_OUTPUT 15
#define ERROR_JVM_ARGC_TOO_SMALL 16
#define ERROR_JVM_ARGC_TOO_LARGE 17
#define ERROR_MAIN_ARGC_TOO_SMALL 18
#define ERROR_MAIN_ARGC_TOO_LARGE 19
#define ERROR_UNKNOWN_DIRECTIVE 20

void error(const char *fmt, ...) {
	va_list ap;
	va_start(ap, fmt);
	vfprintf(stderr, fmt, ap);
	va_end(ap);
	fputc('\n', stderr);
}

void debug(const char *fmt, ...) {
	//if (!debug_mode) return;
	va_list ap;
	int i;
	va_list nothing;
	va_start(ap, fmt);
	vfprintf(stderr, fmt, ap);
	va_end(ap);
	fputc('\n', stderr);
	fflush(stderr);
}

/* result=$(dirname "$argv0")/"$command" */
char *path(const char *argv0, const char *command) {
	// Calculate string lengths.
	const char *last_slash = argv0 == NULL ? NULL : strrchr(argv0, '/');
	size_t dir_len = (size_t)(last_slash == NULL ? 1 : last_slash - argv0);
	size_t command_len = strlen(command);
	size_t result_len = dir_len + 1 + command_len;

	// Allocate the result string.
	char *result = (char *)malloc(result_len + 1);
	if (result == NULL) return NULL;

	// Build the result string.
	if (last_slash == NULL) {
		result[0] = '.';
	}
	else {
		strncpy(result, argv0, dir_len);
	}
	result[dir_len] = '/';
	result[dir_len + 1] = '\0';
	strcat(result, command); // result += command

	return result;
}

#ifdef WIN32
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
	if (!CreateProcess(NULL, command, NULL, NULL, TRUE, 0, NULL, NULL, &si, &pi)) {
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
	if (numInputString == NULL) error("malloc");
	snprintf(numInputString, 21, "%zu", numInput);
	writeLine(stdinWrite, numInputString);
	free(numInputString);
	for (size_t i = 0; i < numInput; i++)
		writeLine(stdinWrite, input[i]);

	// Close the stdin write handle to signal end of input
	CloseHandle(stdinWrite);

	// Read from the child process's stdout
	char buffer[1024];
	DWORD bytesRead;
	DWORD totalBytesRead = 0;
	size_t bufferSize = 1024;
	char *outputBuffer = malloc(bufferSize);

	if (outputBuffer == NULL) {
		error("malloc");
		return ERROR_MALLOC;
	}

	while (ReadFile(stdoutRead, buffer, sizeof(buffer), &bytesRead, NULL) && bytesRead > 0) {
		debug("run_command: got %d bytes from jaunch", strlen(buffer));
		if (totalBytesRead + bytesRead > bufferSize) {
			bufferSize *= 2;
			outputBuffer = realloc(outputBuffer, bufferSize);
			if (outputBuffer == NULL) {
				error("realloc");
				return ERROR_REALLOC;
			}
		}
		memcpy(outputBuffer + totalBytesRead, buffer, bytesRead);
		totalBytesRead += bytesRead;
	}

	// Close handles
	CloseHandle(stdoutRead);
	CloseHandle(pi.hProcess);
	CloseHandle(pi.hThread);

	// Return the output buffer and the number of lines
	*output = NULL;
	*numOutput = 0;

	if (totalBytesRead > 0) {
		// Split the output buffer into lines
		size_t lineCount = 0;
		char *token = strtok(outputBuffer, "\n");
		while (token != NULL) {
			*output = realloc(*output, (lineCount + 1) * sizeof(char *));
			if (*output == NULL) {
				error("realloc");
				return ERROR_REALLOC2;
			}
			(*output)[lineCount] = strdup(token);
			if ((*output)[lineCount] == NULL) {
				error("strdup");
				return ERROR_STRDUP;
			}
			lineCount++;
			token = strtok(NULL, "\n");
		}

		*numOutput = lineCount;
		free(outputBuffer); // Free the temporary buffer
	}
}
#else
int run_command(const char *command,
	const char *input[], size_t numInput,
	char ***output, size_t *numOutput)
{
	// Create pipes for stdin and stdout
	int stdinPipe[2];
	int stdoutPipe[2];

	debug("run_command: opening pipes to/from jaunch");
	if (pipe(stdinPipe) == -1 || pipe(stdoutPipe) == -1) {
		error("pipe");
		return ERROR_PIPE;
	}

	// Fork to create a child process
	pid_t pid = fork();

	if (pid == -1) {
		error("fork");
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
		execlp(command, command, (char *)NULL);

		// If execlp fails
		error("execlp");
		return ERROR_EXECLP;
	}
	else { // Parent process
		// Close unused ends of the pipes
		close(stdinPipe[0]);
		close(stdoutPipe[1]);

		// Write to the child process's stdin
		debug("run_command: writing to jaunch stdin");
		// Passing the input line count as the first line tells the child process what
		// to expect, so that it can stop reading from stdin once it has received
		// those lines, even though the pipe is not yet closed. This avoids deadlocks.
		dprintf(stdinPipe[1], "%d\n", numInput);
		fflush(stdinPipe[1]);
		debug("run_command: wrote numInput: %d", numInput);
		for (size_t i = 0; i < numInput; i++) {
			dprintf(stdinPipe[1], "%s\n", input[i]);
			debug("run_command: wrote input #%d: %s", i, input[i]);
		}

		// Close the write end of stdin to signal the end of input
		close(stdinPipe[1]);
		debug("run_command: closed jaunch stdin pipe");

		// Read from the child process's stdout
		char buffer[1024];
		size_t bytesRead;
		size_t totalBytesRead = 0;
		size_t bufferSize = 1024;
		char *outputBuffer = malloc(bufferSize);

		if (outputBuffer == NULL) {
			error("malloc");
			return ERROR_MALLOC;
		}

		while ((bytesRead = read(stdoutPipe[0], buffer, sizeof(buffer))) > 0) {
			debug("run_command: got %d bytes from jaunch", strlen(buffer));
			if (totalBytesRead + bytesRead > bufferSize) {
				bufferSize *= 2;
				outputBuffer = realloc(outputBuffer, bufferSize);
				if (outputBuffer == NULL) {
					error("realloc");
					return ERROR_REALLOC;
				}
			}
			memcpy(outputBuffer + totalBytesRead, buffer, bytesRead);
			totalBytesRead += bytesRead;
		}

		// Close the read end of stdout
		close(stdoutPipe[0]);

		// Wait for the child process to finish
		if (waitpid(pid, NULL, 0) == -1) {
			error("waitpid");
			return ERROR_WAITPID;
		}

		// Return the output buffer and the number of lines
		*output = NULL;
		*numOutput = 0;

		if (totalBytesRead > 0) {
			// Split the output buffer into lines
			size_t lineCount = 0;
			char *token = strtok(outputBuffer, "\n");
			while (token != NULL) {
				*output = realloc(*output, (lineCount + 1) * sizeof(char *));
				if (*output == NULL) {
					error("realloc");
					return ERROR_REALLOC2;
				}
				(*output)[lineCount] = strdup(token);
				if ((*output)[lineCount] == NULL) {
					error("strdup");
					return ERROR_STRDUP;
				}
				lineCount++;
				token = strtok(NULL, "\n");
			}

			*numOutput = lineCount;
			free(outputBuffer); // Free the temporary buffer
		}
	}
	return SUCCESS;
}
#endif

#ifdef WIN32
void dlclose(void* library) { FreeLibrary(library); }
char *dlerror() { return "error" /*GetLastError()*/; }
#endif

int launch_jvm(const char *libjvm_path, const size_t jvm_argc, const char *jvm_argv[],
	const char *main_class_name, const size_t main_argc, const char *main_argv[])
{
	// Load libjvm.
	debug("LOADING LIBJVM");
#ifdef WIN32
	HMODULE jvm_library = LoadLibrary(libjvm_path);
#else
	void *jvm_library = dlopen(libjvm_path, RTLD_NOW | RTLD_GLOBAL);
#endif
	if (!jvm_library) {
		error("Error loading libjvm: %s", dlerror());
		return ERROR_DLOPEN;
	}

	// Load JNI_CreateJavaVM function.
	debug("LOADING JNI_CreateJavaVM");
#ifdef WIN32
	FARPROC JNI_CreateJavaVM = GetProcAddress(jvm_library, "JNI_CreateJavaVM");
#else
	static jint (*JNI_CreateJavaVM)(JavaVM **pvm, void **penv, void *args);
	JNI_CreateJavaVM = dlsym(jvm_library, "JNI_CreateJavaVM");
#endif
	if (!JNI_CreateJavaVM) {
		error("Error finding JNI_CreateJavaVM: %s", dlerror());
		dlclose(jvm_library);
		return ERROR_DLSYM;
	}

	// Populate VM options.
	debug("POPULATING VM OPTIONS");
	JavaVMOption vmOptions[jvm_argc + 1];
	for (size_t i = 0; i < jvm_argc; i++) {
		vmOptions[i].optionString = (char *)jvm_argv[i];
	}
	vmOptions[jvm_argc].optionString = NULL;

	// Populate VM init args.
	debug("POPULATING VM INIT ARGS");
	JavaVMInitArgs vmInitArgs;
	vmInitArgs.version = JNI_VERSION_1_8;
	vmInitArgs.options = vmOptions;
	vmInitArgs.nOptions = jvm_argc;
	vmInitArgs.ignoreUnrecognized = JNI_FALSE;

	// Create the JVM.
	debug("CREATING JVM");
	JavaVM *jvm;
	JNIEnv *env;
	if (JNI_CreateJavaVM(&jvm, (void **)&env, &vmInitArgs) != JNI_OK) {
		error("Error creating Java VM");
		dlclose(jvm_library);
		return ERROR_CREATE_JAVA_VM;
	}

	// Find the main class.
	debug("FINDING MAIN CLASS");
	jclass mainClass = (*env)->FindClass(env, main_class_name);
	if (mainClass == NULL) {
		error("Error finding class %s", main_class_name);
		(*jvm)->DestroyJavaVM(jvm);
		dlclose(jvm_library);
		return ERROR_FIND_CLASS;
	}

	// Find the main method.
	debug("FINDING MAIN METHOD");
	jmethodID mainMethod = (*env)->GetStaticMethodID(env, mainClass, "main", "([Ljava/lang/String;)V");
	if (mainMethod == NULL) {
		error("Error finding main method");
		(*jvm)->DestroyJavaVM(jvm);
		dlclose(jvm_library);
		return ERROR_GET_STATIC_METHOD_ID;
	}

	// Populate main method arguments.
	debug("FINDING MAIN METHOD ARGUMENTS");
	jobjectArray javaArgs = (*env)->NewObjectArray(env, main_argc, (*env)->FindClass(env, "java/lang/String"), NULL);
	for (size_t i = 0; i < main_argc; i++) {
		(*env)->SetObjectArrayElement(env, javaArgs, i, (*env)->NewStringUTF(env, main_argv[i]));
	}

	// Invoke the main method.
	debug("INVOKING MAIN METHOD");
	(*env)->CallStaticVoidMethodA(env, mainClass, mainMethod, (jvalue *)&javaArgs);

	debug("DETACHING CURRENT THREAD");
	if ((*jvm)->DetachCurrentThread(jvm)) {
		error("Could not detach current thread");
	}

	// Clean up.
	debug("DESTROYING JAVA VM");
	(*jvm)->DestroyJavaVM(jvm);
	debug("CLOSING LIBJVM");
	dlclose(jvm_library);
	debug("GOODBYE");

	return SUCCESS;
}

int main(const int argc, const char *argv[]) {
#ifdef WIN32
	const char *jaunch_exe = "jaunch.exe";
#else
	const char *jaunch_exe = "jaunch";
#endif
	const char *command = path(argc == 0 ? NULL : argv[0], jaunch_exe);
	if (command == NULL) {
		error("command path");
		return ERROR_COMMAND_PATH;
	}
	debug("jaunch command = %s", command);

	char **outputLines;
	size_t numOutput;

	// Run external command to process the command line arguments.

	int run_result = run_command(command, argv, argc, &outputLines, &numOutput);
	if (run_result != SUCCESS) return run_result;

	debug("numOutput = %zu", numOutput);
	for (size_t i = 0; i < numOutput; i++) {
		debug("outputLines[%zu] = %s", i, outputLines[i]);
	}
	if (numOutput < 5) {
		error("output");
		return ERROR_OUTPUT;
	}

	// Parse the command's output.

	char **ptr = outputLines;
	const char *directive = *ptr++;
	debug("directive = %s", directive);

	const char *libjvm_path = *ptr++;
	debug("libjvm_path = %s", libjvm_path);

	const int jvm_argc = atoi(*ptr++);
	debug("jvm_argc = %d", jvm_argc);
	if (jvm_argc < 0) {
		error("jvm_argc too small");
		return ERROR_JVM_ARGC_TOO_SMALL;
	}
	if (numOutput < 5 + jvm_argc) {
		error("jvm_argc too large");
		return ERROR_JVM_ARGC_TOO_LARGE;
	}

	const char **jvm_argv = (const char **)ptr;
	ptr += jvm_argc;
	for (size_t i = 0; i < jvm_argc; i++) {
		debug("jvm_argv[%zu] = %s", i, jvm_argv[i]);
	}

	const char *main_class_name = *ptr++;
	debug("main_class_name = %s", main_class_name);

	const int main_argc = atoi(*ptr++);
	debug("main_argc = %d", main_argc);
	if (main_argc < 0) {
		error("main_argc too small");
		return ERROR_MAIN_ARGC_TOO_SMALL;
	}
	if (numOutput < 5 + jvm_argc + main_argc) {
		error("main_argc too large");
		return ERROR_MAIN_ARGC_TOO_LARGE;
	}

	const char **main_argv = (const char **)ptr;
	ptr += main_argc;
	for (size_t i = 0; i < main_argc; i++) {
		debug("main_argv[%zu] = %s", i, main_argv[i]);
	}

	// Perform the indicated directive.

	if (strcmp(directive, "LAUNCH") == 0) {
		// Launch the JVM with the received arguments.
		int launch_result = launch_jvm(
			libjvm_path, jvm_argc, jvm_argv,
			main_class_name, main_argc, main_argv
		);
		// Clean up.
		for (size_t i = 0; i < numOutput; i++) {
			free(outputLines[i]);
		}
		free(outputLines);

		return launch_result;
	}

	if (strcmp(directive, "CANCEL") == 0) return SUCCESS;

	error("Unknown directive: %s", directive);
	return ERROR_UNKNOWN_DIRECTIVE;
}
