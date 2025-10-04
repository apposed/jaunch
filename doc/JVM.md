This document collects important notes about the JVM runtime.

## Java discovery heuristics

Finding a suitable Java installation is complex. Jaunch uses three techniques
in order of increasing cost:

1. **Directory name parsing** - Fast but fragile; extracts version/distro
   from paths like `/usr/lib/jvm/java-17-openjdk` (see `extractJavaVersion`
   in `jvm.kt`).

2. **`release` file inspection** - Reads metadata from `$JAVA_HOME/release`
   if present, though some distros (Corretto 8, JBRSDK 8) lack this file or
   have incomplete entries (see `readReleaseInfo` in `jvm.kt`).

3. **Launching `java Props`** - Most reliable but slowest; executes the Java
   binary to extract `System.getProperties()` (see `askJavaForSystemProperties`
   in `jvm.kt`)

These techniques are tried lazily and cached, so expensive operations only
run when necessary. See `jvm.allow-weird-runtimes` in `configs/jvm.toml` for
how Jaunch handles installations with incomplete metadata.

## JVM argument handling

### Percentage-based heap sizes

Jaunch supports a `%` suffix for memory arguments, allowing heap sizes to be
specified as a percentage of total system RAM (e.g., `-Xmx50%`). This is
handled in the configurator (see `calculateMemory` in `jvm.kt`), which queries
system memory and converts percentages to appropriate `k`, `m`, or `g` suffixes.

**Example:** `--heap=75%` on a system with 16 GB RAM becomes `-Xmx12g`.

### Duplicate argument squashing

Because the JVM only respects the **last** occurrence of memory-related flags
like `-Xms` and `-Xmx`, Jaunch actively removes duplicates, keeping only the
final value (see `tweakArgs` in `jvm.kt`). This prevents confusion when
arguments come from multiple sources (config files, environment, CLI flags).

### Classpath construction

Jaunch uses `-Djava.class.path=` rather than `-cp` for setting the classpath,
because the latter fails when passed into the `JNI_CreateJavaVM` function.
When multiple classpath sources exist, they are **merged** (concatenated with
the platform-specific separator) rather than replaced (see `tweakArgs` in
`jvm.kt`).

**Important:** This means classpaths are additive across configuration sources.

### -XstartOnFirstThread

This macOS-specific argument (required by some OpenGL/SWT applications)
triggers a `RUNLOOP:main` directive in Jaunch (see `launch` in `jvm.kt`).
However, if `--jaunch-runloop` is also specified, the `-XstartOnFirstThread`
flag is ignored with a warning since the explicit option takes precedence.
For more about the macOS runloop, see "The RUNLOOP directive" in
[MACOS.md](MACOS.md).

## Managing the JVM lifecycle

The JVM has unique lifecycle constraints that Jaunch's C layer must handle:

### JVM instance caching and reuse

**Only one JVM per process:** The JNI specification prohibits creating multiple
JVM instances in a single process. Once `JNI_CreateJavaVM` succeeds, attempting
to create another JVM in the same process will fail. To support multiple JVM
directives, Jaunch caches the first JVM instance (`cached_jvm` in `jvm.h`) and
reuses it for subsequent directives.

**Reuse workflow:**
1. First JVM directive: Load `libjvm`, call `JNI_CreateJavaVM`, cache the
   `JavaVM*` and library handle.
2. Subsequent directives: Reuse cached JVM, attach current thread with
   `AttachCurrentThread`.
3. After each main method invocation: Call `DetachCurrentThread` but keep JVM
   alive.
4. Final cleanup: Destroy JVM with `DestroyJavaVM` and close library in
   the `cleanup_jvm()` function of `src/c/jvm.h`.

**Important implications:**
- JVM options (e.g., `-Xmx`, classpath) can only be set during the first JVM
  creation.
- Subsequent directives that specify JVM options will have those options
  ignored with a warning (see "Subsequent JVM directive" section of `jvm.h`).
- The cached JVM persists across multiple Java main class invocations within a
  single launcher execution.

### Platform-specific issues

**macOS AWT and CFRunLoopStop:** After AWT (Abstract Window Toolkit)
initializes on macOS, the `CFRunLoopStop` function stops working correctly.
This is a known issue with how AWT integrates with macOS's Core Foundation run
loop. Jaunch must account for this when managing event loops or attempting to
stop the launcher process cleanly after GUI initialization. For more about the
macOS runloop, see "The RUNLOOP directive" in [MACOS.md](MACOS.md).
