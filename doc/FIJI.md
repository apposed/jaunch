This document describes how to *update the Fiji launcher*.

## Prerequisites

* Linux x64 system with ImageMagick, png2icns, and Wine installed
* macOS system [configured for code signing](MACOS.md#code-signing)
* Windows x64 system [configured for code signing](WINDOWS.md#code-signing)

## Steps to release

Begin on the Linux x64 system.

1. Download and unpack the [latest release of Jaunch](https://github.com/apposed/jaunch/releases). The rest of these instructions assume you downloaded Jaunch at release version 1.0.0 and unpacked it into your home directory, creating folder `~/jaunch-1.0.0`.

2. Clone the Fiji repository if you don't already have it:
   ```
   mkdir -p ~/code/fiji
   cd ~/code/fiji
   git clone git@github.com:fiji/fiji
   ```

2. Appify Fiji:
   ```shell
   cd ~/jaunch-1.0.0 &&
   mkdir fiji &&
   bin/appify.sh \
     --out-dir fiji \
     --app-title Fiji \
     --app-exe fiji \
     --app-id sc.fiji.fiji \
     --jaunch-toml ~/code/fiji/fiji/config/jaunch/fiji.toml \
     --app-icon ~/code/fiji/fiji/images/fiji-logo-1.0.svg \
     --app-icon-macos ~/code/fiji/fiji/Contents/Resources/Fiji.icns \
     --info-plist ~/code/fiji/fiji/Contents/Info.plist
   ```

* Transfer `~/jaunch-1.0.0/bin` and `Fiji.app` to the macOS system.

* On the macOS system:
  ```shell
  bin/sign.sh Fiji.app
  ```

* On the Linux system, copy the signed `Fiji.app` back from the Mac.

* Transfer `~/jaunch-1.0.0/bin` and `*.exe` to the Windows system.

* On the Windows system:
  ```shell
  bin/sign.sh *.exe
  ```

* On the Linux system, copy the signed EXEs back from the Windows machine.

* Now integrate that completed `fiji` folder into a Fiji installation.
  I need to write a shell script to do all/most of this...

Could make it simpler by not using `appify.sh` at all for UPDATING,
since Fiji installations already have all the structure in place.
Just need to sign the `Fiji.app`, the EXEs, and update any TOMLs that changed.
Then upload the result to the core Fiji update site.
