#include <stdio.h>
#include <dlfcn.h>

int main() {
    const char *libpython = "/home/curtis/miniforge3/lib/libpython3.so";
    void *pythonLib = dlopen(libpython, RTLD_LAZY | RTLD_GLOBAL);

    if (!pythonLib) {
        fprintf(stderr, "Error loading libpython: %s\n", dlerror());
        return 1;
    }

    // Function pointers for Python API functions
    void (*Py_Initialize)(void) = dlsym(pythonLib, "Py_Initialize");
    void (*Py_Finalize)(void) = dlsym(pythonLib, "Py_Finalize");
    int (*PyRun_SimpleString)(const char *) = dlsym(pythonLib, "PyRun_SimpleString");

    if (!Py_Initialize || !Py_Finalize || !PyRun_SimpleString) {
        fprintf(stderr, "Error loading Python symbols: %s\n", dlerror());
        dlclose(pythonLib);
        return 1;
    }

    // Initialize the Python interpreter
    Py_Initialize();

    // Run a Python script
    const char *script = "print('Hello from Python!')";
    PyRun_SimpleString(script);

    // Finalize the Python interpreter
    Py_Finalize();

    // Close the Python library
    dlclose(pythonLib);

    return 0;
}
