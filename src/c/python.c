#include <stdio.h>
#include <dlfcn.h>

#include <CoreFoundation/CoreFoundation.h>
#include <pthread.h>

static void dummy_call_back(void *info) { }

static int start_python() {
    // Load libpython3.10 dynamically
    const char *libpython_path =
        "/usr/local/Caskroom/mambaforge/base/envs/pyimagej-dev/lib/libpython3.10.dylib";
    void *libpython = dlopen(libpython_path, RTLD_LAZY);
    if (!libpython) {
        fprintf(stderr, "Error loading libpython3.10: %s\n", dlerror());
        return 1;
    }

    typedef void (*Py_InitializeFunc)();
    Py_InitializeFunc Py_Initialize = (Py_InitializeFunc)dlsym(libpython, "Py_Initialize");
    if (!Py_Initialize) {
        fprintf(stderr, "Error finding Py_Initialize function: %s\n", dlerror());
        dlclose(libpython);
        return 1;
    }
    typedef int (*PyRun_SimpleStringFunc)(const char *);
    PyRun_SimpleStringFunc PyRun_SimpleString = (PyRun_SimpleStringFunc)dlsym(libpython, "PyRun_SimpleString");
    if (!PyRun_SimpleString) {
        fprintf(stderr, "Error finding PyRun_SimpleString function: %s\n", dlerror());
        dlclose(libpython);
        return 1;
    }

    // Initialize Python interpreter
    Py_Initialize();

    // Do something with Python...
    const char *script =
      "print('Hello, Python from C!')\n"
      "import imagej\n"
      "ij = imagej.init(mode='interactive')\n"
      "print(ij.getVersion())\n"
      "ij.ui().showUI()\n"
      "print('We did it! \\^_^/')\n"
      "";
    int result = PyRun_SimpleString(script);
    if (result != 0) {
      fprintf(stderr, "Error running Python script: %d\n", result);
      dlclose(libpython);
      return 1;
    }

    return 0;
}

static void *run_python(void *dummy) {
    exit(start_python());
}

int main() {
    // Start Python on a dedicated thread.
    pthread_t thread;
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setscope(&attr, PTHREAD_SCOPE_SYSTEM);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    pthread_create(&thread, &attr, run_python, NULL);
    pthread_attr_destroy(&attr);

    // Run the AppKit event loop here on the main thread.
    CFRunLoopSourceContext context;
    memset(&context, 0, sizeof(context));
    context.perform = &dummy_call_back;

    CFRunLoopSourceRef ref = CFRunLoopSourceCreate(NULL, 0, &context);
    CFRunLoopAddSource (CFRunLoopGetCurrent(), ref, kCFRunLoopCommonModes);
    CFRunLoopRun();

    return 0;
}
