#!/bin/sh

jdkdir=$JAVA_HOME
test -d "$jdkdir" || jdkdir=$(update-java-alternatives -l | head -n1 | sed 's/.* //')
gcc -I"$jdkdir/include" -I"$jdkdir/include/linux" -fPIC -fno-stack-protector src/c/jaunch.c -o jaunch
