Jaunch: launch Java the right way!

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

## Building

```shell
./compile.sh
./jaunch
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
