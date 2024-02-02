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

mkdir -p build

compile() {
  (set -x; gcc \
    -I"$jdkdir/include" \
    -I"$jdkdir/include/linux" \
    -I"$jdkdir/include/darwin" \
    -I"$jdkdir/include/win32" \
    -fPIC -fno-stack-protector \
    src/c/jaunch.c $@)
}

case "$(uname)" in
  Darwin)
    compile -o build/launcher-x86_64 -target x86_64-apple-macos10.12 &&
    compile -o build/launcher-arm64 -target arm64-apple-macos11 &&
    (set -x; lipo -create -output build/launcher build/launcher-x86_64 build/launcher-arm64)
    ;;
  *) compile -o build/launcher ;;
esac
