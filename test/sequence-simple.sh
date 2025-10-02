#!/bin/sh

libjvm=LIBJVM
libpython=LIBPYTHON

directives="
JVM|5|$libjvm|1|-Djava.class.path=.|HelloWorld|1-JVM-main
PYTHON|3|$libpython|hi.py|2-PYTHON-main
PYTHON|3|$libpython|hi.py|3-PYTHON-main
JVM|4|$libjvm|0|HelloWorld|4-JVM-main
"

echo "$directives" | while read line
do
  test "$line" || continue
  echo "$line" | tr '|' '\n'
done
