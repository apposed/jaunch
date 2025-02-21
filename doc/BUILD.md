## Building Jaunch from source

### Prerequisites

* **Linux:**
  - Ensure you have `gcc`, `make`, and `git`. E.g. for Debian/Ubuntu:
    ```shell
    sudo apt install build-essential git
    ```

* **macOS:**
  - [Install XCode](https://kotlinlang.org/docs/native-overview.html#target-platforms).

* **Windows:**
  - Install [Scoop](https://scoop.sh/).
  - Then in a PowerShell, install Git and MinGW:
    ```powershell
    scoop install git mingw
    ```

### Building

In a terminal window (on Windows, use PowerShell or Git Bash, not Command Prompt),
navigate to the Jaunch codebase, downloading it from the remote repository if needed:
```
git clone https://github.com/apposed/jaunch
cd jaunch
```

Then build it:
```
make dist
```

The build process will:

1. Build the native C code, generating a binary named `build/launcher`
   (`build\launcher.exe` on Windows).

2. Build the Kotlin Native code, generating a binary named `jaunch`
   (`jaunch.exe` on Windows).

3. Copy the needed files to the `dist` directory, including:
   * The two native binaries (1) and (2);
   * The default TOML configuration files.
   * The `Props.class` helper program.

### Running

Run the appropriate `launcher` binary or script in the `dist` folder.

If it doesn't work, run again with the `--debug` flag,
which will show what's happening under the hood.

### Next steps

* To play with Jaunch's demo applications, see [EXAMPLES.md](EXAMPLES.md).

* To use Jaunch as a launcher for *your* application, see [SETUP.md](SETUP.md).

* To learn about the various operating-system-specific considerations, see
  [LINUX.md](LINUX.md), [MACOS.md](MACOS.md), and [WINDOWS.md](WINDOWS.md).
