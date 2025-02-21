## Linux notes

### XInitThreads

On systems running the [X Window System](https://www.x.org/), it might be necessary to [invoke the `XInitThreads()` function](https://tronche.com/gui/x/xlib/display/threads.html) before interacting with the display across multiple threads, for example when using OpenGL or Xlib for window management.

To accommodate this (admittedly niche) use case, Jaunch provides an `INIT_THREADS` directive, which calls Jaunch's `init_threads()` function, which on Linux invokes `XInitThreads()`. For other operating systems, `init_threads()` is currently a no-op.

The `INIT_THREADS` directive was originally included in Jaunch for one specific reason: to support the [sciview](https://imagej.net/plugins/sciview) plugin in [Fiji](https://fiji.sc/). The sciview plugin is built on the [scenery](https://imagej.net/libs/scenery) rendering library, which previously had an OpenGL backend, thus requiring an `XInitThreads()` call for multithreaded visualization to work. More recently, scenery dropped its OpenGL backend in favor of a Vulkan-based one, so the `XInitThreads()` call might not be necessary anymore for sciview and scenery. But Fiji has [other](https://imagej.net/plugins/3d-viewer) [plugins](https://imagej.net/plugins/volume-calculator) that still utilize OpenGL, and other applications might potentially need it as well, so Jaunch retains the `INIT_THREADS` feature.
