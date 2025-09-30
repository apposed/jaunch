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
    // 2. List of arguments to the Python runtime, one per line.
    // =======================================================================

    const char *libpython_path = argv[0];
    LOG_INFO("PYTHON", "libpython_path = %s", libpython_path);

    // =======================================================================
    // Load the Python runtime.
    // =======================================================================

    // Load libpython.
    LOG_DEBUG("PYTHON", "Loading libpython");
    void *python_library = lib_open(libpython_path);
    if (!python_library) { LOG_ERROR("Error loading libpython: %s", lib_error()); return ERROR_DLOPEN; }

    // Load Py_BytesMain function.
    LOG_DEBUG("PYTHON", "Loading Py_BytesMain");
    static int (*Py_BytesMain)(int, char **);
    Py_BytesMain = lib_sym(python_library, "Py_BytesMain");
    if (!Py_BytesMain) {
        LOG_ERROR("Error finding Py_BytesMain function: %s", lib_error());
        lib_close(python_library);
        return 1;
    }

    // Invoke Python main routine with the specified arguments.
    int result = Py_BytesMain(argc, (char **)argv);

    if (result != 0) {
      LOG_ERROR("Error running Python script: %d", result);
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
