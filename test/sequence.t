Integration test for several runtime launch directives in sequence.
Pre-requisites: run `make clean demo` in the root directory

Setup:

  $ . "$TESTDIR/common.include"
  $ cd "$TESTDIR/../demo"

Detect current platform:

  $ arch=$(uname -m 2>/dev/null)
  $ case "$arch" in arm64|aarch64) arch=arm64 ;; x86_64|amd64) arch=x64 ;; esac
  $ os=$(uname -s 2>/dev/null)
  $ case "$os" in Linux) platform="linux-$arch" ;; Darwin) platform="macos-$arch" ;; MINGW*|MSYS*) platform="windows-$arch.exe" ;; esac
  $ echo "platform = $platform"
  platform = *-* (glob)

Detect libjvm and libpython locations:

  $ libjvm=$(jaunch/jaunch-"$platform" hi | head -n3 | tail -n1)
  $ libpython=$(jaunch/jaunch-"$platform" hiss | head -n3 | tail -n1)
  $ echo "libjvm = $libjvm"
  libjvm = .*(libjvm|libjli|jvm.dll|JVM.DLL).* (re)
  $ echo "libpython = $libpython"
  libpython = *python* (glob)

Set up custom configurator:

  $ cp -rp jaunch jaunch.original
  $ rm jaunch/jaunch*
  $ sed -e "s:LIBJVM:$libjvm:g" -e "s:LIBPYTHON:$libpython:g" ../test/sequence.sh > jaunch/jaunch
  $ chmod +x jaunch/jaunch

Test complex sequence of hardcoded directives involving Java AWT:

  $ ./hi
  Hello, 1-JVM-main!
  Hello, 2-PYTHON-main!
  Error creating Java Virtual Machine
  [3]

Cleanup:

  $ rm -rf jaunch
  $ mv jaunch.original jaunch
