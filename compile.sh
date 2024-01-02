#!/bin/sh

set -e

cd "$(dirname "$0")"

echo
echo "== Building C code =="
jdkdir=$JAVA_HOME
test -d "$jdkdir" || jdkdir=$(test ! -x update-java-alternatives || update-java-alternatives -l | head -n1 | sed 's/.* //')
test -d "$jdkdir" || jdkdir=$(test ! -x /usr/libexec/java_home || /usr/libexec/java_home)
gcc -I"$jdkdir/include" -I"$jdkdir/include/linux" -I"$jdkdir/include/darwin" -I"$jdkdir/include/win32" -fPIC -fno-stack-protector src/c/jaunch.c -o fiji

echo
echo "== Building Kotlin code =="
./gradlew build
test ! -f build/bin/posix/debugExecutable/jaunch.kexe || ln -f build/bin/posix/debugExecutable/jaunch.kexe jaunch
test ! -f build/bin/windows/debugExecutable/jaunch.exe || cp build/bin/windows/debugExecutable/jaunch.exe jaunch.exe

echo
echo "== Compilation complete =="
echo "Now try running:"
echo "./fiji"
