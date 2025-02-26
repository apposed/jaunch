## Using Jaunch as your application launcher

1. Download and unpack the
   [latest Jaunch release](https://github.com/apposed/jaunch/releases).
   The rest of this guide will assume you unpacked the Jaunch v1.0.0
   release into your home directory at `~/jaunch-1.0.0`.

2. Alternately, you can [build Jaunch from source](BUILD.md). But if you
   want launchers for all supported platforms (Linux, macOS, and Windows),
   you will need to build Jaunch on all of them and combine the results.
   The release downloads include prebuilt binaries for all platforms.

3. Fire up a POSIX-friendly shell like zsh or bash.
   * On Linux and macOS, the built-in Terminal app will do the trick.
   * On Windows, use [Git Bash](https://gitforwindows.org/), which we
     recommend installing via [Scoop](https://scoop.sh/).

4. Create a directory to serve as your application's base directory.
   For example, if your application is called Fizzbuzz, you might do:
   ```shell
   mkdir Fizzbuzz
   ```
   The rest of this guide will assume your application is named Fizzbuzz;
   replace all such references with your actual application name and title.

5. Create a `fizzbuzz.toml` file matching your app's launch requirements.

   - To gain an understanding of the various configuration options,
     read through `~/jaunch-1.0.0/jaunch/common.toml`, and perhaps
     also `python.toml` and `jvm.toml`.

   - If you are in a hurry, check out some example app configurations
     [here](https://github.com/apposed/jaunch/tree/main/configs).

   - You may also find the [Fiji](https://fiji.sc/) project's
     [configuration](https://github.com/fiji/fiji/blob/-/config/jaunch/fiji.toml)
     illuminating, since it exercises many of Jaunch's capabilities.

6. Optionally, prepare an icon for your application in SVG format.

   - If you don't want to bother with an icon, you can leave off
     the `--app-icon fizzbuzz.svg` arguments in the following step.

   - If you want to use different icons for Linux, macOS, and/or
     Windows, you can use the `--app-icon-linux`, `--app-icon-macos`,
     and `--app-icon-windows` arguments, respectively. The
     `--app-icon-macos` override in particular is useful if you wish
     to abide by macOS's style of icons with rounded rectangle frames.

7. Use Jaunch's app-generation script to copy Jaunch's various bits
   into the correct places within your application base directory:
   ```shell
   ~/jaunch-1.0.0/bin/appify.sh \
     --app-exe fizzbuzz \
     --app-icon fizzbuzz.svg \
     --app-id com.mycompany.fizzbuzz \
     --app-title Fizzbuzz \
     --jaunch-toml fizzbuzz.toml \
     --out-dir Fizzbuzz
   ```
   replacing `com.mycompany` with an appropriate reverse-domain-name
   prefix for your organization.

8. Note any `[WARNING]`s that appear in the appify output.

   - In particular, you may need to install ImageMagick and png2icns
     for your app icon to be successfully converted to the proper
     OS-specific formats (ICNS for macOS; ICO for Windows), and
     Wine to successfully embed the ICO into the Windows EXEs
     if running appify on a non-Windows platform.

   - After installing any needed utilities, repeat the previous step.

9. Copy your application code (Python scripts, Java JAR files, etc.)
   into the `Fizzbuzz` directory structure into locations of your
   choice. For example, Java JAR files might live in `lib` or `jars`.

10. Test your application by executing the appropriate launcher in debug mode:
    - Linux: `Fizzbuzz/fizzbuzz --debug`
    - macOS: `Fizzbuzz/Fizzbuzz.app/Contents/MacOS/fizzbuzz-macos --debug`
    - Windows: `.\Fizzbuzz\fizzbuzz-windows-x64-console.exe --debug`

Congratulations! You have a working Jaunch launcher!

Or maybe you don't, in which case you can ask for help on the
[Jaunch issue tracker](https://github.com/apposed/jaunch/issues).

Next steps:

1. Optionally, compress the launcher executables using
   `~/jaunch-1.0.0/bin/pack.sh`, which uses [UPX](https://upx.github.io/) to
   reduce their file sizes. Be warned that while it is nice to reduce Jaunch
   to the smallest possible size, we have received reports of Windows
   anti-malware tools misidentifying the Jaunch configurator as infected by
   TROJ.Win32.TRX.XXPE50FLM011. So caveat emptor on the binary shrinking!

2. Code-sign your macOS and/or Windows binaries using
   `~/jaunch-1.0.0/bin/sign.sh`. See these guides for detailed instructions:
   - [Code-signing on macOS](MACOS.md#code-signing)
   - [Code-signing on Windows](WINDOWS.md#code-signing)
