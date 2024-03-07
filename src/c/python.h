#ifndef _JAUNCH_PYTHON_H
#define _JAUNCH_PYTHON_H

/*
 * This is the logic implementing Jaunc's PYTHON directive.
 * 
 * It dynamically loads libpython and calls Py_BytesMain with the given args.
 */
static int launch_python(const size_t argc, const char **argv) {
    // =======================================================================
    // Parse the arguments, which must conform to the following structure:
    //
    // 1. Directive, which must be `PYTHON`.
    // 2. Path to the runtime native library (libpython).
    // 3. Number of arguments to the Python runtime.
    // 4. List of arguments to the Python runtime, one per line,
    //    including an initial argv[0] "executable" on the first line.
    // =======================================================================

    char **ptr = (char **)argv;
    const char *directive = *ptr++;
    debug("[JAUNCH-PYTHON] directive = %s", directive);
 
    const char *libpython_path = *ptr++;
    debug("[JAUNCH-PYTHON] libpython_path = %s", libpython_path);

    const int python_argc = atoi(*ptr++);
    debug("[JAUNCH-PYTHON] python_argc = %d", python_argc);
    if (python_argc < 0) {
        error("python_argc value is too small: %d", python_argc);
        return ERROR_ARG_COUNT_TOO_SMALL;
    }
    if (argc < 3 + python_argc) {
        error("python_argc value is too large: %d", python_argc);
        return ERROR_ARG_COUNT_TOO_LARGE;
    }

    const char **python_argv = (const char **)ptr;
    ptr += python_argc;
    for (size_t i = 0; i < python_argc; i++) {
        debug("[JAUNCH-PYTHON] python_argv[%zu] = %s", i, python_argv[i]);
    }

    // =======================================================================
    // Load the Python runtime.
    // =======================================================================

    // Load libpython.
    debug("[JAUNCH-PYTHON] LOADING LIBPYTHON");
    void *python_library = loadlib(libpython_path);
    if (!python_library) { error("Error loading libpython: %s", dlerror()); return ERROR_DLOPEN; }

    // Load Py_BytesMain function.
    debug("[JAUNCH-PYTHON] LOADING Py_BytesMain");
    static int (*Py_BytesMain)(int, char **);
    Py_BytesMain = dlsym(python_library, "Py_BytesMain");
    if (!Py_BytesMain) {
        error("Error finding Py_BytesMain function: %s", dlerror());
        dlclose(python_library);
        return 1;
    }

    // Invoke Python main routine with the specified arguments.
    int result = Py_BytesMain(python_argc, (char **)python_argv);

    if (result != 0) {
      error("Error running Python script: %d", result);
      dlclose(python_library);
      return result;
    }

    // =======================================================================
    // Clean up.
    // =======================================================================

    debug("[JAUNCH-PYTHON] CLOSING LIBPYTHON");
    dlclose(python_library);
    debug("[JAUNCH-PYTHON] GOODBYE");

    return SUCCESS;
}

#endif
