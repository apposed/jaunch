#!/bin/sh

libjvm=LIBJVM
libpython=LIBPYTHON

directives="
JVM|5|$libjvm|1|-Djava.class.path=.|HelloWorld|1-JVM-main
PYTHON|3|$libpython|hi.py|2-PYTHON-main
JVM|5|$libjvm|0|HelloWorld|--edt|3-JVM-EDT
PYTHON|3|$libpython|hi.py|4-PYTHON-main
JVM|4|$libjvm|0|HelloWorld|5-JVM-main
JVM|5|$libjvm|0|HelloWorld|--edt|6-JVM-EDT
"

echo "$directives" | while read line
do
  test "$line" || continue
  echo "$line" | tr '|' '\n'
done
