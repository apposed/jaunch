## Building Jaunch from source

* **Linux:**
  ```shell
  make dist
  ```

* **macOS:**
  [Install XCode](https://kotlinlang.org/docs/native-overview.html#target-platforms)
  first. Then in a Terminal:
  ```shell
  make dist
  ```

* **Windows:** Install [Scoop](https://scoop.sh/) first. Then in a PowerShell:
  ```powershell
  scoop install mingw
  make dist
  ```
  And if that fails, try it from the bash shell&mdash;e.g.:
  ```powershell
  sh -c "make dist"
  ```
  Which way works might depend on whether you installed Git using Scoop.

The build process will:

1. Build the native C code, generating a binary named `build/launcher`
   (`build\launcher.exe` on Windows).

2. Build the Kotlin Native code, generating a binary named `jaunch`
   (`jaunch.exe` on Windows).

3. Copy the needed files to the `dist` directory, including:
   * The two native binaries (1) and (2);
   * The default TOML configuration files.
   * The `Props.class` helper program.

Then run the `launcher` binary in the `dist` folder and watch the fireworks.
If it doesn't work, try appending the `--debug` flag, which will show what's
happening under the hood.
