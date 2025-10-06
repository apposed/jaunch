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
  $ case "$os" in Linux) jaunch="jaunch/jaunch" ;; Darwin) jaunch="Hi.app/Contents/MacOS/jaunch" ;; MINGW*|MSYS*) jaunch="jaunch/jaunch" ;; esac
  $ echo "platform = $platform"
  platform = *-* (glob)
  $ echo "jaunch = $jaunch"
  jaunch = */jaunch (glob)

Detect libjvm, libpython, and binpython locations:

  $ libjvm=$(jaunch/jaunch-"$platform" hi | head -n3 | tail -n1)
  $ pythoninfo=$(jaunch/jaunch-"$platform" hiss | head -n4 | tail -n2)
  $ libpython=$(echo "$pythoninfo" | head -n1)
  $ binpython=$(echo "$pythoninfo" | tail -n1)
  $ echo "libjvm = $libjvm"
  libjvm = .*(libjvm|libjli|jvm.dll|JVM.DLL).* (re)
  $ echo "libpython = $libpython"
  libpython = *ython* (glob)
  $ echo "binpython = $binpython"
  binpython = *python* (glob)

Prepare for custom configurator:

  $ cp -rp jaunch jaunch.original
  $ rm jaunch/jaunch*
  $ cp -rp Hi.app Hi.app.original
  $ test ! -e Hi.app/Contents/MacOS/jaunch-macos || rm Hi.app/Contents/MacOS/jaunch*

Test simple sequence of directives (no Java AWT):

  $ sed -e "s:LIBJVM:$libjvm:g" -e "s:LIBPYTHON:$libpython:g" -e "s:BINPYTHON:$binpython:g" ../test/sequence-simple.sh > "$jaunch"
  $ chmod +x "$jaunch"
  $ ./hi
  Hello, 1-JVM-main!
  Hello, 2-PYTHON-main!
  Hello, 3-PYTHON-main!
  Hello, 4-JVM-main!

Test complex sequence of directives involving Java AWT:

  $ sed -e "s:LIBJVM:$libjvm:g" -e "s:LIBPYTHON:$libpython:g" -e "s:BINPYTHON:$binpython:g" ../test/sequence-awt.sh > "$jaunch"
  $ chmod +x "$jaunch"
  $ ./hi
  Hello, 1-JVM-main!
  Hello, 2-PYTHON-main!
  Hello, 3-JVM-EDT!
  Hello, 4-PYTHON-main!
  Hello, 5-JVM-main!
  Hello, 6-JVM-EDT!

Cleanup:

  $ rm -rf jaunch Hi.app
  $ mv jaunch.original jaunch
  $ mv Hi.app.original Hi.app
