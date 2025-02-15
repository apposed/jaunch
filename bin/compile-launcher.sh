#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[compile-launcher]\033[0m"

# Locate Java installation. We need this for the jni.h include,
# even though Java will be loaded via dlopen at runtime.
jdkdir=$JAVA_HOME
test -d "$jdkdir" || jdkdir=$(test ! -x update-java-alternatives || update-java-alternatives -l | head -n1 | sed 's/.* //')
test -d "$jdkdir" || jdkdir=$(test ! -x /usr/libexec/java_home || /usr/libexec/java_home)
test -d "$jdkdir" || {
  echo '[ERROR] The jni.h header is needed to compile Jaunch.'
  echo '[ERROR] Please set JAVA_HOME to point to an OpenJDK installation.'
  exit 1
}

mkdir -p build

find-llvm-mingw() {
  find .cache/llvm-mingw -maxdepth 1 -type d 2>/dev/null | sort -r | head -n1
}

configure-llvm-mingw() {
  cdir=$(find-llvm-mingw)
  if [ "$cdir" ]
  then
    echo "$cdir"
  else
    v=20241217
    n=llvm-mingw-$v-msvcrt-ubuntu-20.04-x86_64
    mkdir -p .cache/llvm-mingw &&
    (
      cd .cache/llvm-mingw &&
      set -x &&
      curl -fLO https://github.com/mstorsjo/llvm-mingw/releases/download/$v/$n.tar.xz &&
      tar xf $n.tar.xz
    ) &&
    echo "$(find-llvm-mingw)"
  fi
}

compile() {
  compiler=$1
  command -v "$compiler" >/dev/null 2>&1 || {
    echo "[WARNING] No compiler installed: $compiler"
    echo "[WARNING] Skipping invocation: $@"
    return
  }
  shift
  (set -x; "$compiler" \
    -I"$jdkdir/include" \
    -I"$jdkdir/include/linux" \
    -I"$jdkdir/include/darwin" \
    -I"$jdkdir/include/win32" \
    -fPIC -fno-stack-protector \
    src/c/jaunch.c $@)
}

case "$(uname -s)" in
  Linux)
    # Compile Linux targets.
    compile aarch64-linux-gnu-gcc -o build/launcher-linux-arm64 &&
    compile gcc -o build/launcher-linux-x64 &&
    # Cross-compile Windows targets (thanks, llvm-mingw!).
    cdir=$(configure-llvm-mingw)
    if [ -d "$cdir" ]
    then
      # windows-arm64 (console and GUI builds)
      compile "$cdir/bin/aarch64-w64-mingw32-clang" \
        -o build/launcher-windows-arm64-console.exe -mconsole &&
      compile "$cdir/bin/aarch64-w64-mingw32-clang" \
        -o build/launcher-windows-arm64-gui.exe -mwindows &&
      # windows-x64 (console and GUI builds)
      compile "$cdir/bin/x86_64-w64-mingw32-clang" \
        -o build/launcher-windows-x64-console.exe -mconsole &&
      compile "$cdir/bin/x86_64-w64-mingw32-clang" \
        -o build/launcher-windows-x64-gui.exe -mwindows
    else
      echo '[WARNING] Failed to set up llvm-mingw; skipping Windows cross-compilation'
    fi
    ;;
  Darwin)
    # Compile macOS targets.
    compile gcc -o build/launcher-macos-arm64 -framework CoreFoundation -target arm64-apple-macos11 &&
    compile gcc -o build/launcher-macos-x64 -framework CoreFoundation -target x86_64-apple-macos10.12 &&
    (set -x; lipo -create -output build/launcher-macos build/launcher-macos-x64 build/launcher-macos-arm64)
    ;;
  MINGW*|MSYS*)
    # Compile Windows targets (current arch only).
    arch=$(uname -m)
    if [ "$arch" = "x86_64" ]; then arch=x64; fi
    compile gcc -o "build/launcher-windows-$arch-console.exe" -mconsole
    compile gcc -o "build/launcher-windows-$arch-gui.exe" -mwindows
    ;;
  *)
    # Unknown architecture; just try it.
    compile gcc -o build/launcher
    ;;
esac
