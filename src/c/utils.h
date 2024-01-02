#ifndef _LOGGING_H
#define _LOGGING_H

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
#define ERROR_REALLOC2 12
#define ERROR_STRDUP 13
#define ERROR_COMMAND_PATH 14
#define ERROR_OUTPUT 15
#define ERROR_JVM_ARGC_TOO_SMALL 16
#define ERROR_JVM_ARGC_TOO_LARGE 17
#define ERROR_MAIN_ARGC_TOO_SMALL 18
#define ERROR_MAIN_ARGC_TOO_LARGE 19
#define ERROR_UNKNOWN_DIRECTIVE 20

// -- Helper functions --

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

#endif
