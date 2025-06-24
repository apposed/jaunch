*Jaunch: Launch Programs **Your** Way!™*

[![](https://github.com/scijava/jaunch/actions/workflows/build.yml/badge.svg)](https://github.com/scijava/jaunch/actions/workflows/build.yml)

Jaunch is a native launcher for applications that run inside non-native runtimes,
including the **Java Virtual Machine (JVM)**, the **Python interpreter**, or both.

## Quick start

To use Jaunch for launching for your application, see [SETUP.md](doc/SETUP.md).

To build Jaunch's demo apps from source, see [EXAMPLES.md](doc/EXAMPLES.md).

## Minimum requirements

| Runtime | Minimum |
|---------|---------|
| Java    | 1.8     |
| Python  | 3.8     |

| Operating system | Minimum      |
|------------------|--------------|
| [Linux]          | glibc 2.34   |
| [Ubuntu]         | 22.04 LTS    |
| [macOS]          | 11 "Big Sur" |
| [Windows]        | 10           |

**CPU architecture:** arm64 or x86-64

See [Limitations](doc/LIMITS.md) for more details.

## Documentation

* [Example apps](doc/EXAMPLES.md)
* [Building from source](doc/BUILD.md)
* [How to launch your app with Jaunch](doc/SETUP.md)
* OS-specific concerns: [Linux], [macOS], [Windows]

## License

To maximize the flexibility of downstream projects to use and
adapt this code, Jaunch is [Unlicensed](https://unlicense.org/).
See the [UNLICENSE](UNLICENSE) file for details.

The only exception is the icons in the `icons` folder, which are
from the [OpenMoji] project, licensed under the [CC BY-SA 4.0].
These icons are used by Jaunch's demo apps, and therefore the demo apps
as a whole are licensed under CC BY-SA 4.0 as well. But of course you can
change out the icons, and then the CC BY-SA 4.0 license will no longer apply.
See [icons/NOTICE.txt](icons/NOTICE.txt) for further details.

## Why Jaunch

Do you have a desktop application that runs in the Python interpreter or Java
Virtual Machine? You probably want a friendly native launcher, for example a
.exe file in the case of Windows, that launches your program, right?

There are many existing ways for both Python and the JVM to achieve this goal;
see [Alternatives](#alternatives) below. But none of them do what Jaunch does:

- Discover existing runtime installations, rather than necessarily bundling one.
- Provide supreme flexibility and configurability with program launch parameters.

Jaunch began as a tool to launch [Fiji], a rather complex application for the JVM. But
Fiji also needed the ability to be launched *via Python* (i.e. start Python which then
starts Java using [JPype]), to make its in-process Python integration as convenient as
possible for users to access. So we figured hey, Jaunch knows how to link to libpython
now, so why not support standalone Python apps as well? We do not know of an existing
general tool in the Python ecosystem that fills this niche of launching Python without
necessarily *bundling* Python.

## Design Goals

### Run your program in the same process as the launcher

* Be a good citizen of our native environment.
* Integrate properly with application docks, system trays, and taskbars.

### Discover runtime installations already on the system

* Link to the best native library (libjvm and/or libpython) on demand.
* Search beneath specified runtime directory roots.
* Recognize system-wide installations as well as bundled runtimes.
* Define rules for deciding which installations meet application requirements.
* If no appropriate runtime installation is found, show an informative error message.

### Support runtime customization of runtime launch parameters

* Customize at runtime which arguments are passed to the runtime itself.
* Customize at runtime which arguments are passed to the main class.
* Customize at runtime which program (Java main class or Python script) is run.

### Customize launcher behavior without recompiling native binaries

* Editable TOML files let users customize launcher behavior,
  e.g. adding shortcuts for common command line operations.

### Keep as much code in a high-level language as possible

* Jaunch came into being to replace an aging launcher written as 5000+ lines of C code.
  The design centers around minimizing the size and complexity of needed C code,
  in favor of most code being written in a more maintainable high-level language.
* On the off-chance that the TOML-based configuration is not flexible enough for your
  application's needs, your next layer of customization is the Kotlin codebase, not C.

### Keep the binary size of the native launcher as small as possible

* As few dependencies as possible&mdash;right now Jaunch has none at all
  besides standard platform libraries.
* Where feasible, compress native binaries using [upx].

## Architecture

Jaunch consists of two parts:

1. A native launcher, [written in C](src/c), kept reasonably minimal.

2. A "configurator" executable written in [Kotlin Native], which does the heavy lifting.

The native launcher (1) will be named after your particular application, it is placed in
the base directory of your application. For example, for an application called Fizzbuzz,
the launcher could be named `fizzbuzz.exe`.

The configurator (2) is named `jaunch-<os>-<arch>.exe`, and placed in the `jaunch`
subdirectory of your application. Examples: for ARM64 Linux it would be named
`jaunch/jaunch-linux-arm64`, whereas for x86-64 Windows it would be named
`jaunch/jaunch-windows-x64.exe`. The reason for the `<os>-<arch>` suffix is so that
portable applications can ship with all needed jaunch configurator executables in
the same `jaunch` folder, without any name clashes.

The native launcher invokes the configurator as a subprocess, passing its entire `argv`
list to the appropriate `jaunch` program via a pipe to stdin. The jaunch configurator is
then responsible for outputting the resultant configuration via its stdout.
Each directive block is a sequence of lines, structured as follows:

1. The directive for the native launcher to perform:
   - `JVM` to launch a JVM program using [JNI] functions (e.g. [`JNI_CreateJavaVM`]).
   - `PYTHON` to launch a Python program using Python's [Stable ABI] (e.g. [`Py_BytesMain`]).
   - `ERROR` to display an error message.
   - `ABORT` to immediately terminate without parsing any further directives.

2. The number of subsequent lines for this directive block.

3. The following lines then conform to a structure specific to the directive:

   - For `JVM`:
     1. Path to the jvm native library;
     2. Number of subsequent lines with arguments to the JVM;
     3. Arguments to the JVM, one per line;
     4. The main class to execute;
     5. Arguments to the main program, one per line. The number of main program arguments is calculated from the total number of directive block lines minus the number of already-parsed lines.

   - For `PYTHON`:
     1. Path to the python native library;
     2. Arguments to Python, one per line, including Python runtime arguments if any, main script if any, and main script arguments if any. The number of arguments is calculated from the total number of directive block lines minus the number of already-parsed lines (which is always only 1: the python native library path).

   - For `ERROR`:
     1. The exit code to use when the launcher terminates.
     2. Error message lines. The number of error message lines is calculated from the total number of directive block lines minus the number of already-parsed lines (which is always only 1: the exit code).

   - For `ABORT`: no further lines; parsing stops here.

To deliver this output, the configurator must do the following things:

* Decide which runtime installation to use.
* Decide which main program to run.
* Decide how to transform the user arguments into runtime and/or main arguments.

If your application's needs along these lines are relatively minimal&mdash;e.g. if you
bundle a JDK in a known location, and always pass all user arguments as main
arguments&mdash;you would likely not even need Jaunch's Kotlin/configurator code at all;
you could write your own simple jaunch configurator as a shell script and/or batch file.

However, Jaunch was designed to satisfy the needs of applications with more complex
command line functionality, which is where the Kotlin configurator comes in. It reads
declarative TOML configuration files, which define how Jaunch will make the above
decisions. The TOML configuration is its own layer of the architecture, which is best
learned by reading the [common.toml](configs/common.toml) file directly. With this
design, the behavior of Jaunch can be heavily customized without needing to modify
source code and rebuild. And for applications that need it, there can be multiple
different native launchers in the application base directory that all share the same
jaunch configurator native binaries with different TOML configurations.

## Alternatives

As so often in technology, there are so many. And yet nothing that does what this program does!

### Other Java launching approaches

#### Executable JAR file

* Pros:
  * Simple: double-click the JAR.
* Cons:
  * Needs OpenJDK already installed and registered to handle the .jar extension.
  * Encourages creation of uber-JARs over modular applications.
  * Does not integrate well with native OS application mechanisms.
  * Does not enable transformation of CLI arguments to JVM arguments.

#### jpackage

* Pros:
  * [Official tooling](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html) supported by the OpenJDK project.
* Cons:
  * Imposes its opinions on the application directory structure (e.g. jars must go in `libs` folder).
  * Standalone executable launcher does not support passing JVM arguments (e.g. `-Xmx5g` to override max heap size).
  * Always bundles a Java runtime, rather than discovering existing Java installations.
* Example:
  * The [QuPath] project uses jpackage for its launcher.
    * QuPath provides a configuration dialog to modify the Java maximum heap size, which it handles by editing the jpackage .cfg file on the user's behalf.

You can use [jpackage] to generate one for every platform you want to support: typically
Linux, macOS, and Windows. The jpackage tool is part of the Java Development Kit (JDK),
so you might think there is no need for another Java native launcher.
But jpackage is inflexible:

* The jpackage tool mandates a specific directory structure for your application,
  e.g. putting all JAR libraries in the `lib/app` folder, and the bundled Java
  runtime into `lib/runtime`. As far as I know, you can't use a different structure.

* The native launchers generated by jpackage do not allow users to pass custom arguments
  to the JVM (["users can't provide JVM options to the application"][1]). All arguments
  are handed verbatim to your Java application's main method. So if your users want to
  e.g. increase the maximum heap size by passing -Xmx20g, they must edit the jpackage
  .cfg file located in the application's `/app` directory.

* The jpackage tool also provides no way to support options like `--debugger=8000`
  which are transformed into JVM arguments like
  `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:8000`.
  To simulate that sort of thing with jpackage, you'd need to implement an option in
  your application's interface like "restart application in debug mode" with a port
  and a "suspend" checkbox. And you'd have to decide whether to reset that every time
  your application launches, or else require the user to go back into the options and
  disable the debug mode again for next time when they are finished.

* jpackage apparently no longer supports generating an application without a bundled
  Java. It did once, back when it was the "JavaFX packager", but based on Internet
  searches and the docs, it seems they removed this feature. So you cannot have a
  "portable application" version of your program, runnable from a USB stick across
  multiple platforms, leveraging the current computer's system installation of Java.

* jpackage has a feature to generate multiple launchers, each with its own arguments and
  main class, which is nice. But they end up as separate executables, so you cannot have
  an option like `fizzbuzz --update` that runs a different main class. (It would be
  doable on the Java side by having a main class that always gets run, which then
  delegates to another main class, but it's a hassle.) Whereas the design of Jaunch is
  flexible enough to support a `--main-class` option so that `fizzbuzz --main-class
  org.example.AltClass` will run `AltClass` rather than FizzBuzz's default main class.

* On Windows, a jpackage launcher must be either a console application, or a non-console
  application. It cannot be both, contingent on how the user invokes it. Whereas Jaunch
  connects to the existing console when there is one, but does not create a new one.

* On macOS, native applications must now be code-signed and notarized in order to
  successfully pass [Gatekeeper]&mdash;and also ideally code-signed and
  malware-scanned on Windows to pass [SmartScreen]. Since jpackaged applications
  contain all JAR files inside the target .app bundle, any time you change your
  application's Java codebase, you must rebuild your .app bundle and then re-sign it.
  Whereas with Jaunch, your .app is skinny, with JAR files and TOML configuration
  living outside the .app bundle, meaning you can sign your Jaunch-based launcher once,
  and only need to rebuild and re-sign it when the core Jaunch code changes.
  Depending on your temperament, you may find Jaunch's flexibility in this
  regard to be either a
  [bug](https://en.wikipedia.org/wiki/Gatekeeper_%28macOS%29#Implications) or a
  [feature](https://news.ycombinator.com/item?id=26946297)...

All of that said, jpackage is a very nice tool, and if it works for you, use it! But if you
want more flexibility&mdash;if you want to launch Java ***Your** Way*&mdash;then try Jaunch.

#### Call `java` in its own separate process

E.g., via shell scripts such as `Contents/MacOS/JavaApplicationStub`.

* Pros:
  * Very flexible and easy to code.
* Cons:
  * Needs OpenJDK already installed and available on the system `PATH`, and/or pointed at by `JAVA_HOME`, and/or known to `/usr/libexec/java_home` on macOS, `/usr/bin/update-java-alternatives` on Linux, etc. In a nutshell: you are doing your own discovery of OpenJDK installations.
* Example:
  * The [Icy](https://icy.bioimageanalysis.org/) project uses shell script launchers on macOS and Linux, and a mystery meat (AFAICT) .exe launcher on Windows.
    * For Linux, the `java` on the system path is used.
    * For macOS, the `java` given by `/usr/libexec/java_home -v 1.8` is used.
    * In both cases, no arguments can be passed to the program (neither to the JVM nor to the Icy application).

#### Lean on command-line tools

E.g. [**SDKMAN!**](https://sdkman.io/), [cjdk](https://github.com/cachedjdk/cjdk), [install-jdk](https://github.com/jyksnw/install-jdk), [jgo](https://github.com/scijava/jgo).

* Pros:
  * Leave it to dedicated external code to install and manage your JDKs.
* Cons:
  * Unfriendly to require non-technical users to run terminal commands to launch a GUI-based application.

#### Use a general-purpose Java launcher

[install4j](https://www.ej-technologies.com/products/install4j/overview.html) by ej Technologies.
* You can pass parameters to the JVM at runtime [via the `-J` argument prefix](https://stackoverflow.com/a/63318626/1207769).
* Closed source.

[launch4j](https://launch4j.sourceforge.net/)
* Must choose console or GUI a priori for Windows flavor.
* Still hosted on SourceForge.
* Can customize JVM options at runtime, but only by editing the application's `.l4j.ini` file.

[WinRun4J](https://github.com/poidasmith/winrun4j)
* Windows only.
* [Unmaintained](https://github.com/poidasmith/winrun4j/issues/102) since 2018.

#### Build your own native launcher for Java

[JavaCall.jl](https://github.com/JuliaInterop/JavaCall.jl)
* Written in Julia, permissively licensed.
* Provides a general API for working with the JVM from Julia code.
* Would need to build a native launcher on top of it.
* Does not work on macOS anymore with Julia 1.6.3+.

[hfhbd/jniTest](https://github.com/hfhbd/jniTest)
* Written in Kotlin (Native/KMP application).
* Proof of concept for loading libjvm to launch Java code from a Kotlin program.
* Built binary is 518K.
* Does not use `dlopen`/`dlsym`, but rather links to libjvm. See my [post on Kotlin Discuss](https://discuss.kotlinlang.org/t/27756).
* Also links to libpthread, unlike other native launchers on this list.

#### Example Java launchers

[ImageJ Launcher](https://github.com/imagej/imagej-launcher)
* Written in C.
* Built binary is 91K.
* More lines of C code than Jaunch's C and Kotlin pieces combined.
* Supports custom runtime arguments to both ImageJ/Fiji and the JVM.
* Sometimes needs to re-exec with `execvp`, either itself with changes to environment variables, or the system Java.

[ijp-imagej-launcher](https://github.com/ij-plugins/ijp-imagej-launcher)
* Written in Scala.
* Built binary is 7.8M.
* Links to libstdc++, unlike other native launchers on this list.
* Calls `java` in a separate process.

[Why](https://github.com/AstroImageJ/Why) (AstroImageJ)
* Written in Rust.
* Uses [jni-rs](https://github.com/jni-rs/jni-rs) crate, which leans on the intelligent Java discovery of [java-locator](https://crates.io/crates/java-locator).
* Built binary is 51M.
* Minimal dynamic library dependencies.
* Project clearly targets Windows only; I had to modify the `Cargo.toml` and `file_handler.rs` in order to compile it for Linux.
* No stated license.

### Other Python launching approaches

#### PyInstaller

From [the PyInstaller website](https://pyinstaller.org/):
> PyInstaller bundles a Python application and all its dependencies into a single package. The user can run the packaged app without installing a Python interpreter or any modules.

#### Briefcase

From [the Briefcase website](https://beeware.org/project/projects/tools/briefcase/):
> Briefcase is a tool for converting a Python project into a standalone native application."

#### constructor

From [the constructor website](https://conda.github.io/constructor/):
> `constructor` is a tool which allows constructing an installer for a collection of conda packages.

------------------------------------------------------------------------------

[1]: https://docs.oracle.com/en/java/javase/21/jpackage/support-application-features.html#GUID-34C3BE72-CDE5-469B-BC4D-3D9A6DD2AEEA
[CC BY-SA 4.0]: https://creativecommons.org/licenses/by-sa/4.0/
[Fiji]: https://fiji.sc/
[Gatekeeper]: https://en.wikipedia.org/wiki/Gatekeeper_(macOS)
[JNI]: https://en.wikipedia.org/wiki/Java_Native_Interface
[JPype]: https://jpype.readthedocs.io/
[Kotlin Native]: https://kotlinlang.org/docs/native-overview.html
[Linux]: doc/LINUX.md
[OpenMoji]: https://openmoji.org/
[QuPath]: https://qupath.readthedocs.io/
[SmartScreen]: https://en.wikipedia.org/wiki/Microsoft_SmartScreen#Windows_malware_protection
[Stable ABI]: https://docs.python.org/3/c-api/stable.html#stable-abi
[Ubuntu]: doc/LINUX.md
[Windows]: doc/WINDOWS.md
[`JNI_CreateJavaVM`]: https://docs.oracle.com/en/java/javase/21/docs/specs/jni/invocation.html#creating-the-vm
[`Py_BytesMain`]: https://docs.python.org/3/c-api/veryhigh.html#c.Py_BytesMain
[jpackage]: https://docs.oracle.com/en/java/javase/21/docs/specs/jni/invocation.html#creating-the-vm
[macOS]: doc/MACOS.md
[upx]: https://upx.github.io/
