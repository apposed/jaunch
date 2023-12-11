#!/bin/sh

jdkdir=$JAVA_HOME
test -d "$jdkdir" || jdkdir=$(update-java-alternatives -l | head -n1 | sed 's/.* //')
gcc -shared -Wl,--no-as-needed -I"$jdkdir/include" -I"$jdkdir"/include/linux -fPIC -fno-stack-protector jaunch/jaunch.c -o libjaunch.so -ldl

pythondir=$PYTHON_HOME
test -d "$pythondir" || pythondir=$(cd "$(dirname "$(which python)")/.." && pwd)
python_include_dir=$(find "$pythondir/include" -name 'python*' -type d | head -n1)
gcc -o gopy gopy.c -I "$python_include_dir"
