*Jaunch: Launch Java **Your** Way!™*

* Native launcher that integrates well into its natural environment.
  * Windows EXE
  * macOS universal2 binary usable inside a .app bundle
  * Linux ELF binary

* Configurable:
  * Customize how Java is discovered:
    * Recognize system-wide installations.
    * Search beneath specified directory roots.
    * Define rules for deciding which installation is most preferred.
  * Customize at runtime which Java main class is run.
  * Customize at runtime which arguments are passed to the JVM.
  * Customize at runtime which arguments are passed to the main class.

* Run Java in the same process as the launcher.
  * Be a good citizen of our native environment.
  * Integrate properly with application docks, system trays, taskbars, and other icon-oriented thingamajigs.

## Design Goals

Support for launching *your* JVM-based application.
- jaunch.exe is the Kotlin program. It does not need to be modified.
- The native launcher (built from jaunch.c) should be named whatever you want. E.g. fiji.exe.
- fiji.toml is the configuration that jaunch.exe uses to decide how to behave.
  - When fiji.exe invokes jaunch.exe, it passes `fiji` (can I do this from cross-platform C?) to jaunch.exe.
- In this way, there can be multiple different launchers in the same directory that all lean on the same jaunch.exe.

Discover available Javas from:
- Subfolders of the application (i.e. bundled Java).
- Known OS-specific system installation locations.
  - /usr/libexec/java_home (macOS)
  - /usr/lib/update-java-alternatives (Linux)
  - Windows registry?
- Known tool-specific installation locations.
  - sdkman
  - install-jdk
  - cjdk
  - conda (base only?)
  - brew
  - scoop
  This can be done in general by having a hardcoded list of directories in the default CFG content, which can be extended by specific applications as desired.

.. more to come ...

## Building

```shell
./compile.sh
./runme
```

Linux only for the moment, but targeting Windows and macOS is also a near-term goal.

## Alternatives

As so often in technology, there are so many. And yet nothing that does what this program does!

### Executable JAR file

* Pros:
  * Simple: double-click the JAR.
* Cons:
  * Needs OpenJDK already installed and registered to handle the .jar extension.
  * Encourages creation of uber-JARs over modular applications.
  * Does not integrate well with native OS application mechanisms.

### jpackage

* Pros:
  * [Official tooling](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html)
  * Official tooling supported by the OpenJDK project.
* Cons:
  * Imposes its opinions on the application directory structure (e.g. jars must go in `libs` folder).
  * Standalone executable launcher does not support passing JVM arguments (e.g. `-Xmx5g` to override max heap size).

### Call `java` in its own separate process

E.g., via shell scripts such as `Contents/MacOS/JavaApplicationStub`

* Pros:
  * Very flexible and easy to code.
* Cons:
  * Needs OpenJDK already installed and available on the system `PATH`, and/or pointed at by `JAVA_HOME`, and/or known to `/usr/libexec/java_home` on macOS, `/usr/bin/update-java-alternatives` on Linux, etc. In a nutshell: you are doing your own discovery of OpenJDK installations.

### Lean on command-line tools

E.g. [**SDKMAN!**](https://sdkman.io/), [cjdk](https://github.com/cachedjdk/cjdk), [install-jdk](https://github.com/jyksnw/install-jdk), [jgo](https://github.com/scijava/jgo).

* Pros:
  * Leave it to dedicated external code to install and manage your JDKs.
* Cons:
  * Unfriendly to require non-technical users to run terminal commands to launch a GUI-based application.
