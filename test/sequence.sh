#!/bin/sh

# FIXME hardcoded paths
jvmlib=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home/lib/libjli.dylib
pylib=/opt/homebrew/Caskroom/miniforge/base/lib/libpython3.12.dylib

directives="
JVM|5|$jvmlib|1|-Djava.class.path=.|HelloWorld|1-JVM-main
PYTHON|3|$pylib|hi.py|2-PYTHON-main
JVM|5|$jvmlib|0|HelloWorld|--edt|3-JVM-EDT
PYTHON|3|$pylib|hi.py|4-PYTHON-main
JVM|4|$jvmlib|0|HelloWorld|5-JVM-main
JVM|5|$jvmlib|0|HelloWorld|--edt|6-JVM-EDT
"

echo "$directives" | while read line
do
  test "$line" || continue
  echo "$line" | tr '|' '\n'
done
