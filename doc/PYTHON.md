This document collects important notes about the Python runtime.

## Discovering the Python launcher and shared library

For Jaunch to launch a Python program, it needs the path to both the Python
launcher (e.g. `python.exe` on Windows) and the Python shared library (e.g.
`libpython3.13.so` on Linux).

Unfortunately, where these two files are located within each particular Python
environment varies substantially by which platform (Linux, macOS, Windows) and
environment management tools (pip/virtualenv, conda/mamba, pixi, uv) are used.

Fortunately, if we locate the Python launcher, we can use it to run a Python
program to report the location of the associated Python shared library.
Unfortunately, the logic for doing so is platform- and environment-specific;
see `configs/props.py` for the various cases and heuristics used.

### macOS case-sensitivity gotcha

Homebrew Python installations have a directory structure like:

```
<root>/
├── bin/
│   ├── python3 -> python3.13
│   └── python3.13
├── lib/
│   └── libpython3.13.dylib -> ../Python
└── Python  # <-- The actual dylib
```

On case-insensitive macOS filesystems (the default), checking for
`${root}/python` as an executable candidate will incorrectly match the
`Python` dylib file. Attempting to execute it produces:

```
sh: .../Python.framework/Versions/3.13/python: cannot execute binary file
```

**Jaunch's solution:** The `python.exe-suffixes` in Jaunch's default
`configs/python.toml` use platform hints to target `bin/python` on macOS (and
Linux) systems, and `python.exe` (not nested beneath a `bin` directory) on
Windows systems. This targeted approach avoids the above pitfall, but if you
customize your `python.exe-suffixes`, be aware of this issue when doing so.

## Managing the Python runtime

### Py_BytesMain: The Stable ABI approach

Jaunch uses Python's [Stable ABI](https://docs.python.org/3/c-api/stable.html)
via the `Py_BytesMain` function rather than the traditional
`Py_Initialize`/`Py_Finalize` sequence. This provides better compatibility
across Python versions at the cost of some constraints:

- **Minimum version:** Python 3.8+ (when `Py_BytesMain` was introduced)
- **argv requirements:** The first argument must be the Python executable path
  (see platform differences below).
- **No interpreter state reuse:** Unlike the JVM, Python is loaded fresh each
  time and unloaded after completion (see `launch_python` in `python.h`).

Because Jaunch needs to load the libpython library to invoke the `Py_BytesMain`
function, and that function expects the path to the Python executable binary
as its first argument (`argv[0]`), Jaunch must locate both as described above.

**Current implementation:** Jaunch's PYTHON directive passes both the
libpython path (first argument) and python executable path (second argument)
to the C layer, which uses them appropriately for `Py_BytesMain`
(see `launch_python` in `python.h`).

### No instance caching

Unlike the JVM (which can only have one instance per process and is therefore
cached), Python's libpython is loaded dynamically, used via `Py_BytesMain`,
and then unloaded (see `launch_python` in `python.h`). There is no reuse
mechanism across multiple PYTHON directives.

This is simpler than the JVM approach but means Python startup overhead occurs
for each directive.
