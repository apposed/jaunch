## Building Jaunch from source

* **Linux:**
  ```shell
  make app
  ```

* **macOS:**
  [Install XCode](https://kotlinlang.org/docs/native-overview.html#target-platforms)
  first. Then in a Terminal:
  ```shell
  make app
  ```

* **Windows:** Install [Scoop](https://scoop.sh/) first. Then in a PowerShell:
  ```powershell
  scoop install mingw
  make app
  ```
  And if that fails, try it from the bash shell&mdash;e.g.:
  ```powershell
  sh -c "make app"
  ```
  Which way works might depend on whether you installed Git using Scoop.

The build process will:

1. Build the native C code, generating a binary named `build/launcher`
   (`build\launcher.exe` on Windows).

2. Build the Kotlin Native code, generating a binary named `jaunch`
   (`jaunch.exe` on Windows).

3. Copy the needed files to the `app` directory, including:
   * The two native binaries (1) and (2);
   * Three TOML configuration files, `jaunch.toml`, `jy.toml`, and `parsy.toml`;
   * The `Props.class` helper program.

Then run the `jy` or `parsy` binary in the `app` folder and watch the fireworks.
If it doesn't work, try appending the `--debug` flag, which will show what's
happening under the hood.

Note that these `jy` and `parsy` launchers are binary identical&mdash;each is
merely an illustration of how your native launcher could be named and work.
They launch different programs due to their respective `.toml` configurations.
Typical applications need only one native launcher binary&mdash;the sample `app`
distribution is just showcasing how Jaunch supports multiple launchers as needed.
