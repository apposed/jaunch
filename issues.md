### All OSes

**Friendly error dialog when Java not found:** The ImageJ Launcher has logic
to display error dialog boxes on Windows with `FormatMessage` and `MessageBox`,
including handling of `GetLastError()` codes. Jaunch has the beginnings of such
a feature for all supported OSes, via the
`void show_alert(const char *title, const char *message)` function.

**Pending updates:** The ImageJ Updater relies on the launcher to move files
from `update/<X>` to `<X>` in the application directory, before Java starts up.
Jaunch needs to have an analogous (but configurable) feature. (And how does the
ImageJ Launcher mark obsolete files for deletion?)

**Cache last-used JVM:** Right now, Jaunch performs the `jvm-root-directory`
search every time.
Better would be to cache the last successful matching Java, and try using
that again preferentially before continuing on to any others. That way,
the user has a stable application setup until/unless the last-used Java 
actually stops meeting Jaunch's constraints (e.g. if the developer bumps
up the jvm-version-min). (Contrast with the ImageJ Launcher, which always
chooses the Java with the newest timestamp... pretty limited...)

**java.library.path:** The ImageJ Launcher builds up a `java_library_path` with
(on Linux) `LD_LIBRARY_PATH` or (on macOS) `DYLD_LIBRARY_PATH`, and then
`lib/<platform>`.
* And on all platforms, it recursively adds all dirs under `${app-dir}/lib`
  that contain at least one native library for that platform. (It even checks
  the contents of every candidate to make sure it really *is* a native library
  structurally...)
* And on Windows, it appends the `java_library_path` to `PATH`.
* On macOS and Linux, it uses `execvp` to re-execute the native launcher when
  the original `LD_LIBRARY_PATH`/`DYLD_LIBRARY_PATH` does not match the
  built-up one.
* And of course it passes -Djava.library.path=${java-library-path} to the JVM.

Should Jaunch do any/all of this? It hinges on whether the following comment
is still true: "Unfortunately, `ld.so` only looks at `LD_LIBRARY_PATH` at
startup, so we have to reexec after setting that variable."

From the [ImageJ Launcher source code](https://github.com/openjdk/jdk/blob/jdk-23%2B4/src/java.base/unix/native/libjli/java_md.c#L67-L85):
```c
 * Previously the launcher modified the LD_LIBRARY_PATH appropriately for the
 * desired data model path, regardless if data models matched or not. The
 * launcher subsequently exec'ed the desired executable, in order to make the
 * LD_LIBRARY_PATH path available, for the runtime linker.
 *
 * Now, in most cases,the launcher will dlopen the target libjvm.so. All
 * required libraries are loaded by the runtime linker, using the
 * $RPATH/$ORIGIN baked into the shared libraries at compile time. Therefore,
 * in most cases, the launcher will only exec, if the data models are
 * mismatched, and will not set any environment variables, regardless of the
 * data models.
 *
 * However, if the environment contains a LD_LIBRARY_PATH, this will cause the
 * launcher to inspect the LD_LIBRARY_PATH. The launcher will check
 *  a. if the LD_LIBRARY_PATH's first component is the path to the desired
 *     libjvm.so
 *  b. if any other libjvm.so is found in any of the paths.
 * If case b is true, then the launcher will set the LD_LIBRARY_PATH to the
 * desired JRE and reexec, in order to propagate the environment.
```

**Launch Java via Python:** It would be very useful for Fiji if Jaunch supported
discovery of `libpython` also, and launching Java from inside a Python interpreter
via the [scyjava](https://github.com/scijava/scyjava) project, which uses JPype.
Then Fiji's CPython script language could be used more easily.

**Squash multiple memory options:** The ImageJ Launcher keeps only the last
memory option for the `-Xm*` flags.
```c
 * If passing -Xmx=99999999g -Xmx=37m to Java, the former still triggers an
 * error. So let's keep only the last, so that the command line can override
 * invalid settings in jvm.cfg.
```
Should Jaunch do this?

**debug.exe:** The ImageJ Launcher enables debug mode if the launcher was
renamed to `debug`/`debug.exe`. This is a cool trick, but incompatible with
Jaunch's expectation that the executable name will match the TOML name. Should
we bother? It is especially convenient on Windows for GUI users, where the name
`debug.exe` also triggers a `new_win_console()`.

**Java discovery from system path:** The ImageJ Launcher searches the system
path (`$PATH`) for java executables, as a way to discover Java installations.
Should Jaunch support doing this?

**Launch java in a subprocess:** The ImageJ Launcher has logic to "fall back to
system Java": i.e. launch `bin/java` in a subprocess, should the `dlopen` of
libjvm fail. Jaunch does not have this logic, and in practice it seems
unnecessary. What do we lose by cutting this?

**Repeat options:** Jaunch's design treats options as a set, not a list.
Therefore, order does not matter, nor does Jaunch support repeat options.
E.g., when passing an option twice with different values such as
`--add a.jar --add b.jar`, the latter value overwrites the former.
A workaround is to use a style like `--add a.jar,b.jar`.

### POSIX (Linux & macOS)

**Follow symlinks:** The ImageJ Launcher follows symlinks when searching
directory structures. (It uses `readlink`.) Should Jaunch also follow symlinks
when doing glob matching?
TEST THIS&mdash;DOES IT ALREADY WORK ON LINUX AND MACOS?

**Linux aarch64:** What about Raspberry Pi? Does KMP/Native support it?
If so: how should the naming of executables be done? Shall we keep doing
what Fiji/ImageJ2 has done until now, which is to append an os/arch suffix?
jaunch-linux-x64, jaunch-linux-aarch64, jaunch-macos, jaunch-windows-x64.exe?
And then for portable applications, symlink or copy to the current os/arch?
There's no reason to do that for the configurator though, only for the
native launcher executable. The native launcher should always reach out to
a jaunch executable with matching os/arch.

**Auto-enable headless mode.** The ImageJ Launcher tries to detect when
headless mode will be required, using the following code:
```c
if (!headless &&
#ifdef __APPLE__
	!CGSessionCopyCurrentDictionary()
#elif defined(__linux__)
	!getenv("DISPLAY")
#else
	0
#endif
) {
error("No GUI detected.  Falling back to headless mode.");
headless = 1;
}
```
Should Jaunch also do this? It was only a "best effort" feature...

### Linux

**XInitThreads:** The ImageJ Launcher calls XInitThreads on Linux when not in
headless mode, so that 3D graphics libraries like Vulkan work as needed:
```c
#ifdef __linux__
  void (*xinit_threads_reference)();
  ...
  // This call is neccessary on Linux to avoid X11 errors when using
  // various 3D graphics APIs like Vulkan or OpenGL.
  if (!headless) {
    void *libX11Handle = dlopen("libX11.so", RTLD_LAZY);
    if(libX11Handle != NULL) {
      debug("Running XInitThreads\n");
      xinit_threads_reference = dlsym(libX11Handle, "XInitThreads");

      if(xinit_threads_reference != NULL) {
        xinit_threads_reference();
      } else {
        error("Could not find XInitThreads in X11 library: %s\n", dlerror());
      }
    } else {
      error("Could not find X11 library, not running XInitThreads.\n");
    }
  }
#endif
```
Should Jaunch also do this? Maybe an optional directive? It would be a
directive that does not switch the result from `LAUNCH` to `CANCEL`.

**.desktop file:** The ImageJ Launcher writes a `.desktop` file for the
application, into both the application directory and
`~/.local/share/applications`. Should Jaunch have such a feature?

**IPv6 network workaround:** The ImageJ Launcher tries to detect if the IPv6
network stack is broken, which it is/was known to be in SUSE Linux at some
point, and if so, it adds `-Djava.net.preferIPv4Stack=true`. I favor dropping
this from Jaunch.

### macOS

**Symlink from Contents/MacOS:** The jaunch configurator lives in
`Contents/MacOS/jaunch` on macOS, and is smart enough to look in `../../jaunch`
for the rest of its bits. But it can be nice to have a symlink to `myapp` in
the application base directory from `Contents/MacOS/myapp`, to make the command
line program more visible and simpler to invoke. But the application won't
start from such a symlinked executable, because the native code does not follow
the symlink to the original binary, nor allow for the possibility that the
executable itself might dwell outside of `Contents/MacOS`. The native launcher
should be improved to support both locations, as well as symlinks to anywhere.

**Universal2 binary:** The jaunch configurator binary is built only for the
current architecture. It needs to be built as a Universal2 binary, so that it
can also run on aarch64 (M1/M2/etc.) systems without the need for Rosetta.

**CWD:** On OS X 10.9 Mavericks (and maybe later versions of macOS?), the PWD
variable might not be set, in which case we need to chdir ourselves to the
application directory, or else the cwd will be / when double-clicked from
Finder. TEST THIS.
Relatedly, on pre-Mavericks, the added `-psn_` arg messes things up.
But we probably don't need to worry about this in 2024.

**APP_NAME/APP_ICON:** The ImageJ Launcher sets the `APP_NAME` and `APP_ICON`
at launch. Should Jaunch really be in the business of doing that? Or should it
always be baked into the macOS binary at build time?

**Launch on separate thread:** The ImageJ Launcher uses `pthread_create` and
`CFRunLoopSourceCreate` to start the JVM on a dedicated thread:
```c
/* MacOSX needs to run Java in a new thread, AppKit in the main thread. */

static void dummy_call_back(void *info) { }
...
pthread_t thread;
pthread_attr_t attr;
pthread_attr_init(&attr);
pthread_attr_setscope(&attr, PTHREAD_SCOPE_SYSTEM);
pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);

/* Start the thread that we will start the JVM on. */
pthread_create(&thread, &attr, start_ij_aux, NULL);
pthread_attr_destroy(&attr);

CFRunLoopSourceContext context;
memset(&context, 0, sizeof(context));
context.perform = &dummy_call_back;

CFRunLoopSourceRef ref = CFRunLoopSourceCreate(NULL, 0, &context);
CFRunLoopAddSource (CFRunLoopGetCurrent(), ref, kCFRunLoopCommonModes);
CFRunLoopRun();
```
Should Jaunch also do this?

**Parse Info.plist:** The ImageJ Launcher reads command line options from the
`Info.plist` file, from the `<dict>` of `<key>`s. Do we actually need this?
If not: how can Fiji persist user-specific edits to the max heap? Edit TOML?
The fields that the ImageJ Launcher reads include:
- `heap`, `mem`, `memory`
- `system` (enabling "use system JVM")
- `allowMultiple` (for `allow_multiple` flag)
- `JVMOptions` (for `jvm_options` list)
- `DefaultArguments` (for `default_arguments` list)

### Windows

**Console popup:** The native launcher currently pops up a Command Prompt
console. The Jaunch Kotlin code should probably use `javaw.exe` (not
`java.exe`) when querying the Java installation's system properties.
Needs further testing.

**Short/DOS paths:** Logic to infer the application directory might be less
than ideal on Windows; the ImageJ Launcher has support for using DOS file paths
via `GetShortPathName` when `WIN32` is set. But is this ever necessary in the
era of modern 64-bit Windows?

**Java bin on PATH:** The ImageJ Launcher on Windows appends the `bin`
directory of the selected JVM directory to the `PATH`, then uses
`setenv_or_exit` to update it. Does it matter? Does the `jvm-dir` need to be on
the `PATH` for any purpose at runtime?

**MinGW64 breakage:** The ImageJ Launcher has the following logic:
```c
#if defined(WIN64)
/* work around MinGW64 breakage */
argc = __argc;
argv = __argv;
argv[0] = _pgmptr;
#endif
```
Is this still relevant? What does it do?

**Java discovery from Windows registry:** The ImageJ Launcher checks the
Windows registry for Java installations.
Migrating this code to Jaunch Kotlin would be feasible. Should we do it?
Slick would be to support reading from the registry via the same variable
syntax. But to do the needed level of indirection might require adding a
new TOML section&mdash;maybe:
```toml
vars = [
	"winregCurrentVersion|HKEY_LOCAL_MACHINE\SOFTWARE\JavaSoft\Java Development Kit\CurrentVersion",
	"winregJavaHome|HKEY_LOCAL_MACHINE\SOFTWARE\JavaSoft\Java Development Kit\${winregCurrentVersion}\JavaHome",
]
```
Kinda hacky for this one feature...

**Best physical memory API:** Jaunch Kotlin uses `ullTotalPhys` and
`ullAvailPhys`, but the ImageJ Launcher uses `dwTotalPhys` and `dwAvailPhys`.
What's the difference? Which one is better?

**Console attachment logic:** The ImageJ Launcher has logic to attach to a
console using `freopen` with channels `CONIN$` (stdin) and `CONOUT$`
(stdout and stderr), as well as create a new console using `FreeConsole()` +
`AllocConsole()`. And it has a hidden option `--console`/`--attach-console`
which calls `attach_win_console()` to do this.
It also has `--new-console` to create a new console.
Is this ever actually necessary?

**Setting EXE icon.** The ImageJ Launcher has `--set-icon` hidden argument to
call `UpdateResource` to set the native launcher EXE's icon.
```c
int set_exe_icon(const char *exe_path, const char *ico_path)
{
	int id = 1, i;
	struct icon icon;
	HANDLE handle;

	if (suffixcmp(exe_path, -1, ".exe")) {
		error("Not an .exe file: '%s'", exe_path);
		return 1;
	}
	if (!file_exists(exe_path)) {
		error("File not found: '%s'", exe_path);
		return 1;
	}
	if (suffixcmp(ico_path, -1, ".ico")) {
		error("Not an .ico file: '%s'", ico_path);
		return 1;
	}
	if (!file_exists(ico_path)) {
		error("File not found: '%s'", ico_path);
		return 1;
	}

	if (parse_ico_file(ico_path, &icon))
		return 1;

	handle = BeginUpdateResource(exe_path, FALSE);
	if (!handle) {
		error("Could not update resources of '%s'", exe_path);
		return 1;
	}
	UpdateResource(handle, RT_GROUP_ICON,
			"MAINICON", MAKELANGID(LANG_ENGLISH, SUBLANG_ENGLISH_US),
			icon.header, sizeof(struct header) + icon.count * sizeof(struct resource_directory));
	for (i = 0; i < icon.count; i++) {
		UpdateResource(handle, RT_ICON,
				MAKEINTRESOURCE(id++), MAKELANGID(LANG_ENGLISH, SUBLANG_ENGLISH_US),
				icon.images[i], icon.items[i].bytes_in_resource);
	}
	return !EndUpdateResource(handle, FALSE);
}
```
At first I thought this would be better served by an external tool. But it
would actually be nice for the Jaunch configurator to have a set-icon directive
that does this, making it as easy as possible for people to build out their own
native launchers without needing to install another tool.

**`LoadLibraryA` vs `LoadLibrary`** The ImageJ Launcher's Windows
implementation of `dlopen` uses `LoadLibraryA` `#if WIN32`, and `LoadLibrary`
`#if defined(WIN32)`.
Firstly: what's the difference in these cases?
Secondly, should Jaunch pattern its `dlopen`/`dlsym`/`dlerror` functions
more closely after the ImageJ Launcher?

**PowerShell quoting:** The `--dry-run` output on Windows works in Command
Prompt, but not PowerShell, because PowerShell gets weird about unquoted
arguments with the dot (.) symbol. Should the Windows `--dry-run` put double
quotes around each argument?

### Fiji-specific

**Original ImageJ launcher:** The ImageJ Launcher purported to be able to
launch the original ImageJ even from a plain ImageJ installation. Let's make a
separate TOML for this.

**JDB support:** The ImageJ Launcher supports a secret option `--jdb` for
launching inside jdb. To make it work, the ImageJ Launcher adds `lib/tools.jar`
to the classpath, and adds the `-jdb` flag to the launcher options... which is
handled on the Java side by the `ClassLauncher`. Should we implement this also
in `fiji.toml`?

**tools.jar:** The ImageJ Launcher also adds `tools.jar` when `--tools-jar`
or `--only-tools-jar` are passed (two more secret options). Furthermore,
`--only-tools-jar` adds `-freeze-classloader` to the launcher options.
Is this useful for Jaunch?

**ImageJ.cfg:** The ImageJ Launcher writes the legacy `ImageJ.cfg` file into
the application directory. Does Jaunch need to keep doing this?
