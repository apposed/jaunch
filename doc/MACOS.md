## macOS notes

Like [Linux](LINUX.md) and [Windows](WINDOWS.md), macOS has some specific concerns and idiosynchrasies that Jaunch must accommodate.

### The main thread's CoreFoundation event loop

Many GUI paradigms have some kind of [event loop](https://en.wikipedia.org/wiki/Event_loop) to organize all of the operations happening in the interface. Java has its AWT Event Dispatch Thread (EDT), Qt has `QEventLoop`, the X Window System has the Xlib event loop (see also the [XInitThreads discussion in LINUX.md](LINUX.md#xinitthreads))... but none of them have challenged this developer nearly so much as macOS's requirement that a [Core Foundation event loop](https://developer.apple.com/library/archive/documentation/Cocoa/Conceptual/Multithreading/RunLoopManagement/RunLoopManagement.html#//apple_ref/doc/uid/10000057i-CH16) be running on the process's main thread. Apple tries to make this requirement as invisible as possible... if you are writing an Objective-C program with XCode. But Jaunch is a cross-platform launcher written in pure C, so it must spin up this event loop itself, then launch the actual main program on a separate pthread.

The main reason for this need is that starting Java directly on the process's main thread results in deadlocks when subsequently initializing Java's graphical AWT subsystem. If your Java program does not do anything with AWT, you might be fine, but if you want to display any GUI elements, your app will freeze.

The same problem occurs when attempting to use Java in-process from Python via a library like [JPype](https://www.jpype.org/): as soon as you invoke any Java AWT code from your Python script, it hangs. To overcome this limitation, JPype provides a `setupGuiEnvironment` function that uses `AppHelper.runConsoleEventLoop()` of `PyObjCTools` to start the Core Foundation event loop on the main thread... but then your Python program is subsequently blocked forever; you must pass the code you want executed as a callback function to the `setupGuiEnvironment` function, for it to be executed while the main thread event loop is running. Not only is PyObjC a hassle to install into a Python environment, this limitation also creates [serious obstacles](https://github.com/imagej/pyimagej/issues?q=label%3Amacos-gui) to unleashing the full power of Python+Java combined, e.g. operating on Java GUI elements interactively in a Python REPL as can be done on Linux or Windows.

Fortunately, because Jaunch on macOS explicitly starts the main thread's event loop, then runs the main program on a separate pthread, Java AWT works, and can be freely used from Python scripts. That said, we are still working out some use cases around combination with other tools, such as combining Java GUI usage with Qt-based projects (e.g. [napari](https://napari.org/)).

### Code signing

TODO: Explain the code signing paradigm on macOS.

- https://developer.apple.com/documentation/xcode/packaging-mac-software-for-distribution
