#ifndef _UTILS_H
#define _UTILS_H

// -- Return codes --

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
#define ERROR_STRDUP 12
#define ERROR_COMMAND_PATH 13
#define ERROR_OUTPUT 14
#define ERROR_JVM_ARGC_TOO_SMALL 15
#define ERROR_JVM_ARGC_TOO_LARGE 16
#define ERROR_MAIN_ARGC_TOO_SMALL 17
#define ERROR_MAIN_ARGC_TOO_LARGE 18
#define ERROR_UNKNOWN_DIRECTIVE 19

int debug_mode = 0;

// -- Helper functions --

void error(const char *fmt, ...) {
	va_list ap;
	va_start(ap, fmt);
	vfprintf(stderr, fmt, ap);
	va_end(ap);
	fputc('\n', stderr);
}

void debug(const char *fmt, ...) {
	if (!debug_mode) return;
	va_list ap;
	int i;
	va_list nothing;
	va_start(ap, fmt);
	vfprintf(stderr, fmt, ap);
	va_end(ap);
	fputc('\n', stderr);
	fflush(stderr);
}

/* Splits an output buffer into lines. */
int split_lines(char *buffer, char *delim, char ***output, size_t *numOutput) {
    size_t lineCount = 0;
    char *token = strtok(buffer, delim);
    while (token != NULL) {
        *output = realloc(*output, (lineCount + 1) * sizeof(char *));
        if (*output == NULL) { error("Memory reallocation failed"); return ERROR_REALLOC; }
        (*output)[lineCount] = strdup(token);
        if ((*output)[lineCount] == NULL) { error("String duplication failed"); return ERROR_STRDUP; }
        lineCount++;
        token = strtok(NULL, delim);
    }

    *numOutput = lineCount;
}

#endif
