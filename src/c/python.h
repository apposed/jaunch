#ifndef _JAUNCH_PYTHON_H
#define _JAUNCH_PYTHON_H

#include <stddef.h>   // for size_t

#include "logging.h"
#include "common.h"

/*
 * This is the logic implementing Jaunch's PYTHON directive.
 * 
 * It dynamically loads libpython and calls Py_BytesMain with the given args.
 */
static int launch_python(const size_t argc, const char **argv) {
    // =======================================================================
    // Parse the arguments, which must conform to the following structure:
    //
    // 1. Path to the runtime native library (libpython).
    // 2. Path to the runtime executable launcher (python).
    // 3. List of arguments to the Python runtime, one per line.
    // =======================================================================

    if (argc < 1) {
      FAIL(ERROR_ARGC_OUT_OF_BOUNDS, "Too few PYTHON directive arguments: %d", argc);
    }

    char **ptr = (char **)argv;

    const char *libpython_path = *ptr++;
    LOG_INFO("PYTHON", "libpython_path = %s", libpython_path);

    const int python_argc = argc - 1;
    const char **python_argv = (const char **)ptr;

    const char *python_exe_path = *ptr++;
    LOG_INFO("PYTHON", "python_exe_path = %s", python_exe_path);

    // =======================================================================
    // Load the Python runtime.
    // =======================================================================

    // Load libpython.
    LOG_DEBUG("PYTHON", "Loading libpython");
    void *python_library = lib_open(libpython_path);
    if (python_library == NULL) {
        FAIL(ERROR_DLOPEN, "Failed to load libpython: %s", lib_error());
    }

    // Load Py_BytesMain function.
    LOG_DEBUG("PYTHON", "Loading Py_BytesMain");
    static int (*Py_BytesMain)(int, char **);
    Py_BytesMain = lib_sym(python_library, "Py_BytesMain");
    if (Py_BytesMain == NULL) {
        LOG_ERROR("Failed to locate Py_BytesMain function: %s", lib_error());
        lib_close(python_library);
        return ERROR_DLSYM;
    }

    // Invoke Python main routine with the specified arguments.
    int result = Py_BytesMain(python_argc, (char **)python_argv);

    if (result != 0) {
      LOG_ERROR("Failed to run Python script: %d", result);
      lib_close(python_library);
      return result;
    }

    // =======================================================================
    // Clean up.
    // =======================================================================

    LOG_DEBUG("PYTHON", "Closing libpython");
    lib_close(python_library);
    LOG_INFO("PYTHON", "Python cleanup complete");

    return SUCCESS;
}

static void cleanup_python() {}

#endif
