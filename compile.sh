#!/bin/sh

set -e

cd "$(dirname "$0")"

echo
echo "== Building C code =="
jdkdir=$JAVA_HOME
test -d "$jdkdir" || jdkdir=$(update-java-alternatives -l | head -n1 | sed 's/.* //')
gcc -I"$jdkdir/include" -I"$jdkdir/include/linux" -fPIC -fno-stack-protector src/c/jaunch.c -o runme

echo
echo "== Building Kotlin code =="
./gradlew build
ln -sf build/bin/posix/debugExecutable/jaunch.kexe jaunch

echo
echo "== Compilation complete =="
echo "Now try running:"
echo "./runme"
