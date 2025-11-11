## Using Jaunch as your application launcher

1. OPTION 1: Download and unpack the
   [latest Jaunch release](https://github.com/apposed/jaunch/releases).
   The rest of this guide will assume you unpacked the Jaunch v2.0.3
   release into your home directory at `~/jaunch-2.0.3`.

2. OPTION 2: You can [build Jaunch from source](BUILD.md). But if you
   want launchers for all supported platforms (Linux, macOS, and Windows),
   you will need to build Jaunch on all of them and combine the results.
   The release downloads include prebuilt binaries for all platforms.

3. Fire up a POSIX-friendly shell like zsh or bash.
   * On Linux and macOS, the built-in Terminal app will do the trick.
   * On Windows, use [Git Bash](https://gitforwindows.org/), which we
     recommend installing via [Scoop](https://scoop.sh/) (`scoop install git`).

4. Create a directory to serve as your application's base directory.
   For example, if your application is called Fizzbuzz, you might do:
   ```shell
   mkdir Fizzbuzz
   ```
   The rest of this guide will assume your application is named Fizzbuzz;
   replace all such references with your actual application name and title.

5. Create a `fizzbuzz.toml` file matching your app's launch requirements.

   - To gain an understanding of the various configuration options,
     read through
     [`common.toml`](https://github.com/apposed/jaunch/tree/main/configs/common.toml),
     and perhaps also
     [`python.toml`](https://github.com/apposed/jaunch/tree/main/configs/python.toml)
     and
     [`jvm.toml`](https://github.com/apposed/jaunch/tree/main/configs/jvm.toml).

   - If you are in a hurry, check out some example app configurations
     [here](https://github.com/apposed/jaunch/tree/main/configs).

   - You may also find the [Fiji](https://fiji.sc/) project's
     [configuration](https://github.com/fiji/fiji/blob/-/config/jaunch/fiji.toml)
     illuminating, since it exercises many of Jaunch's capabilities.

6. Prepare an icon for your application in SVG, ICNS, and ICO formats.

   - On Linux, the png2icns utility from the icnstools package
     is helpful for creating ICNS files.

   - If you don't want to bother with icons, you can leave off
     the `--app-icon-*` arguments in the following step.

7. Use Jaunch's app-generation script to copy Jaunch's various bits
   into the correct places within your application base directory:
   ```shell
   ~/jaunch-2.0.3/bin/appify.sh \
     --app-exe fizzbuzz \
     --app-icon-linux fizzbuzz.svg \
     --app-icon-macos fizzbuzz.icns \
     --app-icon-windows fizzbuzz.ico \
     --app-id com.mycompany.fizzbuzz \
     --app-title Fizzbuzz \
     --jaunch-toml fizzbuzz.toml \
     --out-dir Fizzbuzz
   ```
   replacing `com.mycompany` with an appropriate reverse-domain-name
   prefix for your organization.

   Notice any `[WARNING]`s that appear in the appify output.

   *Note: Regardless of whether you built Jaunch from source, you will find
   the app-generation script in the `bin` folder. All `bin` scripts should
   work correctly from either a source working copy or a downloaded release.*

8. Copy your application code (Python scripts, Java JAR files, etc.)
   into the `Fizzbuzz` directory structure into locations of your
   choice. For example, Java JAR files might live in `lib` or `jars`,
   depending how you configure the `jvm.classpath` in your `fizzbuzz.toml`.

9. Test your application by executing the appropriate launcher in debug mode:
   - Linux: `Fizzbuzz/fizzbuzz --debug`
   - macOS: `Fizzbuzz/Fizzbuzz.app/Contents/MacOS/fizzbuzz-macos --debug`
   - Windows: `.\Fizzbuzz\fizzbuzz-windows-x64-console.exe --debug`

Congratulations! You have a working Jaunch launcher!

Or maybe you don't, in which case you can ask for help on the
[Jaunch issue tracker](https://github.com/apposed/jaunch/issues).

Next steps:

1. Optionally, compress the launcher executables using
   `~/jaunch-2.0.3/bin/pack.sh`, which uses [UPX](https://upx.github.io/) to
   reduce their file sizes. Be warned that while it is nice to reduce Jaunch
   to the smallest possible size, we have received reports of Windows
   anti-malware tools misidentifying Jaunch binaries as infected by
   [various malware](https://github.com/apposed/jaunch/commit/3ecb2a215f6601cd09ef8985597bb1e85ed5e240).
   So caveat emptor on the binary shrinking!

2. Code-sign your macOS and/or Windows binaries using
   `~/jaunch-2.0.3/bin/sign.sh`. See these guides for detailed instructions:
   - [Code-signing on macOS](MACOS.md#code-signing)
   - [Code-signing on Windows](WINDOWS.md#code-signing)
