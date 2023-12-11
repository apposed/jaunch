#!/bin/sh
jdkdir=$JAVA_HOME
test -d "$jdkdir" || jdkdir=$(update-java-alternatives -l | head -n1 | sed 's/.* //')
gcc -shared -Wl,--no-as-needed -I"$jdkdir/include" -I"$jdkdir"/include/linux -fPIC -fno-stack-protector jaunch/jaunch.c -o libjaunch.so -ldl
gcc -I./jaunch -L. -fPIC -fno-stack-protector jaunch/fiji.c -o launch-fiji -ljaunch
