## Windows notes

Like [Linux](LINUX.md) and [macOS](MACOS.md), Windows has some specific concerns and idiosynchrasies that Jaunch must accommodate.

### Console and GUI executables

On Unix-like systems, all executables attach to the console from which they are launched; if no console is present, no new one is created.

Windows is different: when building your EXE file, you must choose between targeting the *console* subsystem or the *GUI* subsystem. Which one you choose impacts how your program behaves with respect to the console:

* A **console** program will attach to the parent process's console when it launches, as happens in Unix-like environments. This means:
  - The console shell will *block* while the program runs, not offering a new shell prompt until after the program terminates.
  - You will see output from the program's standard output and standard error streams in the console as the program produces it.
  - You can type keystrokes while the console has the focus, and the program will receive them on its standard input stream.

  Unlike Unix-like environments, however, if the executable is launched from a non-shell context, such as double-clicking the EXE file from the File Explorer interface, a *new console* linked to the just-launched program will appear in a new window.

* A **GUI** program normally has no console. As with typical windowing systems of Unix-like environments (e.g. GNOME or KDE), if launched from a non-shell context such as File Explorer, the program's GUI (if any) will appear, but no new console gets created. Even if launched from a shell like PowerShell or Command Prompt, though, the program will start up but not block the shell prompt, returning control immediately to the shell&mdash;and crucially, it does not inherit the shell's console by default, meaning you will not see the program's standard output or error streams within the shell used to launch.

A consequence of this dichotomy is it becomes tricky to create a graphical program that also functions as a command-line tool.

#### Inspecting executables

The `file` command, available as part of MinGW and easily accessible via [Git Bash](https://gitforwindows.org/#bash), is one simple way to inspect an EXE file to determine whether it was built in console mode or GUI mode:

```shell
$ file app/jy-windows-x64.exe
app/jy-windows-x64.exe: PE32+ executable (console) x86-64, for MS Windows, 3 sections
$ file app/hello-windows-x64.exe
app/hello-windows-x64.exe: PE32+ executable (GUI) x86-64, for MS Windows, 3 sections
```

#### Workarounds

What we'd really like is for our Windows EXE to behave the same way as Unix-like systems: inheriting the shell console when launched from a shell, but *not* spawning a new console when launched from outside a shell. Alas, it is not meant to be: Windows simply does not work that way. Fortunately, there are workarounds:

* You can launch a GUI executable from a shell in "wait mode":
  - In Command Prompt, launch the GUI executable with:
    ```cmd
    start /wait myProgram.exe
    ```
  - Or in PowerShell, use:
    ```powershell
    Start-Process -Wait .\myProgram.exe
    ```

* With either shell, you can run a batch file that wraps the GUI executable invocation, effectively converting it into a console program. Jaunch's [demo examples](EXAMPLES.md) offer such a batch file for each example app. (Thanks to [this blog post](https://lastpixel.tv/win32-application-as-both-gui-and-console/) for the tip!)

* You can ship two launchers with your application: one for each mode. This is what Python does with `python.exe` (console) and `pythonw.exe` (GUI), and what OpenJDK does with `java.exe` (console) and `javaw.exe` (GUI). For this reason, the Jaunch native launcher is compiled in both GUI and console modes, and you can choose which of the two modes (or both) you want to ship with your application. (In case you were wondering: the configurator portion of Jaunch is always built in console mode only; when the Jaunch native launcher invokes it as a separate process, it does so with the `CREATE_NO_WINDOW` flag so that the configurator's console does not spawn a visible window.)

#### Attaching to the parent console

Windows programs compiled in GUI mode do not attach to the calling process's console by default *even in wait mode*; it must be done explicitly using the [`AttachConsole`](https://learn.microsoft.com/en-us/windows/console/attachconsole) function. Fortunately, the Jaunch native launcher does this, so that the above tricks (wait mode and batch file wrappers) work as desired.

Unfortunately, even with `AttachConsole`&mdash;and even if we go further and use `freopen` to reopen the stdin/stdout/stderr, which I'm not sure even causes any changes in behavior on current versions of Windows&mdash;the invoked launcher is still in a bad state when run directly from the shell (without wait mode and without wrapping in a batch file). Firstly, on Windows, consoles can have multiple processes attached to them and "there is no guarantee that input is received by the process for which it was intended" ([source](https://learn.microsoft.com/en-us/windows/console/creation-of-a-console)); when control returns immediately to the shell before the launched program terminates, and then keystrokes are typed, each character gets sent to either the launched process or the shell process in a haphazard way, effectively splitting the input between the processes, resulting in a big mess.

#### Recommendations

If you want to provide a hybrid GUI+console application, we recommend using the GUI mode executable to avoid extraneous consoles from appearing, and shipping a batch file wrapper for use from the command line with proper console behavior. Or else ship both the GUI and console mode executables, like Python and Java do.

### Code signing

TODO: Explain the code signing paradigm on Windows.

- GOOD: https://shop.certum.eu/open-source-code-signing-on-simplysign.html
- ????: https://learn.microsoft.com/en-us/azure/trusted-signing/overview
