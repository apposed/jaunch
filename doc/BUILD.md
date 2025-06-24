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

<details><summary>Which cross-compilations are performed?</summary>

The Jaunch build system makes a best effort to build for all OS+arch targets,
but only certain cross-compilations are possible:

<table>
<thead>
<tr>
<th rowspan=2>Target</th>
<th colspan=6>Host platform</th>
</tr>
<th>Linux arm64</th>
<th>Linux x64</th>
<th>macOS</th>
<th>Windows arm64</th>
<th>Windows x64</th>
</thead>
<tbody>
<tr>
<td>launcher-linux-arm64</td>
<td><center>❔<sup>1</sup></center></td> <!-- Linux arm64 host -->
<td><center>✅<sup>2</sup></center></td> <!-- Linux x64 host -->
<td><center>➖</center></td> <!-- macOS host -->
<td><center>➖</center></td> <!-- Windows arm64 host -->
<td><center>➖</center></td> <!-- Windows x64 host -->
</tr>
<tr>
<td>launcher-linux-x64</td>
<td><center>❔<sup>1</sup></center></td> <!-- Linux arm64 host -->
<td><center>✅</center></td> <!-- Linux x64 host -->
<td><center>➖</center></td> <!-- macOS host -->
<td><center>➖</center></td> <!-- Windows arm64 host -->
<td><center>➖</center></td> <!-- Windows x64 host -->
</td>
</tr>
<tr>
<td>launcher-macos-arm64</td>
<td rowspan=2><center>➖<sup>3</sup></center></td> <!-- Linux arm64 host -->
<td rowspan=2><center>➖<sup>3</sup></center></td> <!-- Linux x64 host -->
<td rowspan=2><center>✅</center></td> <!-- macOS host -->
<td rowspan=2><center>➖<sup>3</sup></center></td> <!-- Windows arm64 host -->
<td rowspan=2><center>➖<sup>3</sup></center></td> <!-- Windows x64 host -->
</tr>
<tr>
<td>launcher-macos-x64</td>
</tr>
<tr>
<td>launcher-windows-arm64</td>
<td><center>❔<sup>1</sup></center></td> <!-- Linux arm64 host -->
<td><center>✅<sup>4</sup></center></td> <!-- Linux x64 host -->
<td><center>➖</center></td> <!-- macOS host -->
<td><center>❔<sup>1</sup></center></td> <!-- Windows arm64 host -->
<td><center>➖</center></td> <!-- Windows x64 host -->
</tr>
<tr>
<td>launcher-windows-x64</td>
<td><center>❔<sup>1</sup></center></td> <!-- Linux arm64 host -->
<td><center>✅<sup>4</sup></center></td> <!-- Linux x64 host -->
<td><center>➖</center></td> <!-- macOS host -->
<td><center>➖</center></td> <!-- Windows arm64 host -->
<td><center>✅</center></td> <!-- Windows x64 host -->
</tr>
<tr>
<td>jaunch-linux-arm64</td>
<td><center>➖<sup>5</sup></center></td> <!-- Linux arm64 host -->
<td><center>✅</center></td> <!-- Linux x64 host -->
<td><center>➖</center></td> <!-- macOS host -->
<td><center>➖<sup>6</sup></center></td> <!-- Windows arm64 host -->
<td><center>➖</center></td> <!-- Windows x64 host -->
</td>
</tr>
<tr>
<td>jaunch-linux-x64</td>
<td><center>➖<sup>5</sup></center></td> <!-- Linux arm64 host -->
<td><center>✅</center></td> <!-- Linux x64 host -->
<td><center>➖</center></td> <!-- macOS host -->
<td><center>➖<sup>6</sup></center></td> <!-- Windows arm64 host -->
<td><center>➖</center></td> <!-- Windows x64 host -->
</td>
</tr>
<tr>
<td>jaunch-macos-arm64</td>
<td rowspan=2><center>➖<sup>3,5</sup></center></td> <!-- Linux arm64 host -->
<td rowspan=2><center>➖<sup>3</sup></center></td> <!-- Linux x64 host -->
<td rowspan=2><center>✅</center></td> <!-- macOS host -->
<td rowspan=2><center>➖<sup>3,6</sup></center></td> <!-- Windows arm64 host -->
<td rowspan=2><center>➖<sup>3</sup></center></td> <!-- Windows x64 host -->
</tr>
<tr>
<td>jaunch-macos-x64</td>
</tr>
<tr>
<td>jaunch-windows-arm64</td>
<td><center>➖<sup>5,7</sup></center></td> <!-- Linux arm64 host -->
<td><center>➖<sup>7</sup></center></td> <!-- Linux x64 host -->
<td><center>➖<sup>7</sup></center></td> <!-- macOS host -->
<td><center>➖<sup>6,7</sup></center></td> <!-- Windows arm64 host -->
<td><center>➖<sup>7</sup></center></td> <!-- Windows x64 host -->
</tr>
<tr>
<td>jaunch-windows-x64</td>
<td><center>➖<sup>5</sup></center></td> <!-- Linux arm64 host -->
<td><center>➖</center></td> <!-- Linux x64 host -->
<td><center>➖</center></td> <!-- macOS host -->
<td><center>➖<sup>6</sup></center></td> <!-- Windows arm64 host -->
<td><center>✅</center></td> <!-- Windows x64 host -->
</tr>
</tbody>
</table>

<br><sup>1</sup> Untested; build system almost certainly needs adjustment.
<br><sup>2</sup> Requires <a href="https://packages.debian.org/sid/gcc-aarch64-linux-gnu">aarch64-linux-gnu-gcc</a>.
<br><sup>3</sup> Only macOS tooling can target macOS.
<br><sup>4</sup> Using <a href="https://github.com/mstorsjo/llvm-mingw">llvm-mingw</a>.
<br><sup>5</sup> No Kotlin Native support for linux-arm64 host (<a href="https://youtrack.jetbrains.com/issue/KT-36871">KT-36871</a>).
<br><sup>6</sup> No Kotlin Native support for windows-arm64 host (<a href="https://youtrack.jetbrains.com/issue/KT-48420">KT-48420</a>).
<br><sup>7</sup> No Kotlin Native support for windows-arm64 target (<a href="https://youtrack.jetbrains.com/issue/KT-68504">KT-68504</a>).

To cover all platforms, the [Jaunch CI](https://github.com/apposed/jaunch/actions) runs `make dist` on linux-x64, macos-x64, and windows-x64 host nodes, then aggregates all results into one unified `dist` folder. This covers all targets except `jaunch-windows-arm64`, which is not currently possible to build due to [lack of support in Kotlin Native](https://youtrack.jetbrains.com/issue/KT-68504").

</details>

### Testing the result

You can verify that the build is functional by running the
appropriate `launcher` binary or script in the `dist` folder.
The default `launcher.toml` configuration is a simple REPL
entry point, which can launch either Python or JShell.

If it doesn't work, run again with the `--debug` flag,
which will show what's happening under the hood.

### Next steps

* To play with Jaunch's demo applications, see [EXAMPLES.md](EXAMPLES.md).

* To use Jaunch as a launcher for *your* application, see [SETUP.md](SETUP.md).

* To learn about the various operating-system-specific considerations, see
  [LINUX.md](LINUX.md), [MACOS.md](MACOS.md), and [WINDOWS.md](WINDOWS.md).
