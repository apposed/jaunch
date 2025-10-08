#ifndef _JAUNCH_LOGGING_H
#define _JAUNCH_LOGGING_H

#include <stdarg.h>  // for va_end, va_list, va_start
#include <stdio.h>   // for size_t, stderr, fflush, fputc, vfprintf
#include <string.h>  // strcmp
#include <unistd.h>  // for exit

// =========================
// GLOBAL STATE DECLARATIONS
// =========================

extern int log_level;
extern int headless_mode;

// =================
// LOGGING FUNCTIONS
// =================

void log_at_level(int verbosity, const char *fmt, ...) {
    if (log_level < verbosity) return;
    va_list ap;
    va_start(ap, fmt);
    vfprintf(stderr, fmt, ap);
    va_end(ap);
    fputc('\n', stderr);
    fflush(stderr);
}

// Forward declaration for thread-aware log messages - implementation in thread.h.
const char *current_thread_name();

// ==============
// LOGGING MACROS
// ==============

#define LOG_SET_LEVEL(argc, argv) do { \
    for (size_t i = 0; i < argc; i++) \
        if (strcmp(argv[i], "--debug") == 0) log_level++; \
        else if (strcmp(argv[i], "--headless") == 0) headless_mode++; \
} while (0)

#define LOG_DEBUG(component, fmt, ...) \
    log_at_level(2, "[%s:%s] " fmt, component, current_thread_name(), ##__VA_ARGS__)

#define LOG_INFO(component, fmt, ...) \
    log_at_level(1, "[%s:%s] " fmt, component, current_thread_name(), ##__VA_ARGS__)

#define LOG_WARN(fmt, ...) \
    log_at_level(0, "[WARNING] " fmt, ##__VA_ARGS__)

#define LOG_ERROR(fmt, ...) \
    log_at_level(0, "[ERROR] " fmt, ##__VA_ARGS__)

#define LOG_BLANK(fmt, ...) \
    log_at_level(0, fmt, ##__VA_ARGS__)

#define FAIL(code, fmt, ...) do { \
    LOG_ERROR(fmt, ##__VA_ARGS__); \
    return code; \
} while (0)

#define DIE(code, fmt, ...) do { \
    log_at_level(0, "[FATAL] " fmt, ##__VA_ARGS__); \
    exit(code); \
} while (0)

#endif
