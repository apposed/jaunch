## Example apps

The Jaunch repository includes several demo applications powered by Jaunch:

* Parsy -
  A simple console for evaluating expressions, backed by the
  [Parsington](https://github.com/scijava/parsington) library.

* Jy -
  An entry point into the [Jython](https://jython.org/) console.

* REPL -
  A dual-platform app capable of launching both the Python REPL
  and Java's JShell, depending on which flag you pass at launch.

* Paunch -
  An entry point into the Python REPL, which on macOS launches Python in a
  separate thread while running the CoreFoundation event loop on the main
  thread of the process. One advantage of this approach is that foreign
  graphical subsystems such as Java's Abstract Windowing Toolkit (AWT) can be
  used (via libraries like [JPype](https://jpype.readthedocs.io/) in the same
  process as Python without blocking the REPL.

* Hello -
  A simple GUI app displaying a "Hello World" dialog box using Java Swing.

* Hi -
  An even simpler Java app, which simply prints "Hello world" to the console.

* Hiss -
  A simple Python app, which prints a custom greeting based on its arguments.

To try them out, [build from source](doc/BUILD.md), then run the
`make demo` target, which assembles a `demo` folder with everything needed.
