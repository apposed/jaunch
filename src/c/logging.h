#ifndef _JAUNCH_LOGGING_H
#define _JAUNCH_LOGGING_H

#include <stdarg.h>
#include <stdio.h>

// ============
// GLOBAL STATE
// ============
int debug_mode;
int headless_mode;

// =================
// LOGGING FUNCTIONS
// =================

void log_at_level(int verbosity, const char *fmt, ...) {
    if (debug_mode < verbosity) return;
    va_list ap;
    va_start(ap, fmt);
    vfprintf(stderr, fmt, ap);
    va_end(ap);
    fputc('\n', stderr);
    fflush(stderr);
}

// Forward declaration for thread-aware log messages - implementation in thread.h.
const char* current_thread_name();

// ==============
// LOGGING MACROS
// ==============

#define LOG_SET_LEVEL(argc, argv) \
    for (size_t i = 0; i < argc; i++) \
        if (strcmp(argv[i], "--debug") == 0) debug_mode++; \
        else if (strcmp(argv[i], "--headless") == 0) headless_mode++;

#define LOG_BLANK(fmt, ...) \
    log_at_level(0, fmt, ##__VA_ARGS__)
#define LOG_WARN(fmt, ...) \
    log_at_level(0, "[WARNING] " fmt, ##__VA_ARGS__)
#define LOG_ERROR(fmt, ...) \
    log_at_level(0, "[ERROR] " fmt, ##__VA_ARGS__)
#define LOG_INFO(component, fmt, ...) \
    log_at_level(1, "[%s:%s] " fmt, component, current_thread_name(), ##__VA_ARGS__)
#define LOG_DEBUG(component, fmt, ...) \
    log_at_level(2, "[%s:%s] " fmt, component, current_thread_name(), ##__VA_ARGS__)

#endif
