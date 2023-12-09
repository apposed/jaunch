#!/bin/sh
gcc -shared -Wl,--no-as-needed -I/usr/lib/jvm/default-java/include -I/usr/lib/jvm/default-java/include/linux -fPIC -fno-stack-protector jaunch/jaunch.c -o libjaunch.so
gcc -I./jaunch -L. -ljaunch -fPIC -fno-stack-protector jaunch/fiji.c -o launch-fiji
