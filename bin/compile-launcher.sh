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
  echo "[ERROR] The jni.h header is needed to compile Jaunch."
  echo "[ERROR] Please set JAVA_HOME to point to an OpenJDK installation."
  exit 1
}

mkdir -p build

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

case "$(uname)" in
  Linux)
    compile gcc -o build/launcher-linux-x64 &&
    compile aarch64-linux-gnu-gcc -o build/launcher-linux-arm64
    ;;
  Darwin)
    compile gcc -o build/launcher-macos-arm64 -framework CoreFoundation -target arm64-apple-macos11 &&
    compile gcc -o build/launcher-macos-x64 -framework CoreFoundation -target x86_64-apple-macos10.12 &&
    (set -x; lipo -create -output build/launcher build/launcher-macos-x64 build/launcher-macos-arm64)
    ;;
  MINGW*|MSYS*) compile gcc -o build/launcher-windows-x64.exe ;;
  *) compile gcc -o build/launcher ;;
esac
