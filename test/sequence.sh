#!/bin/sh

directives="
JVM|5|LIBJVM|1|-Djava.class.path=.|HelloWorld|1-JVM-main
PYTHON|3|LIBPYTHON|hi.py|2-PYTHON-main
JVM|5|LIBJVM|0|HelloWorld|--edt|3-JVM-EDT
PYTHON|3|LIBPYTHON|hi.py|4-PYTHON-main
JVM|4|LIBJVM|0|HelloWorld|5-JVM-main
JVM|5|LIBJVM|0|HelloWorld|--edt|6-JVM-EDT
"

echo "$directives" | while read line
do
  test "$line" || continue
  echo "$line" | tr '|' '\n'
done
