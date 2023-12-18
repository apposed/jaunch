#!/bin/sh

jdkdir=$JAVA_HOME
test -d "$jdkdir" || jdkdir=$(update-java-alternatives -l | head -n1 | sed 's/.* //')
test -d "$jdkdir" || {
  >&2 echo "No Java installation found."
  exit 1
}

libjvmPath=$(find -L "$jdkdir" -name libjvm.so | head -n1)

fijidir=$FIJI_HOME
test -d "$fijidir" || fijidir=$HOME/Applications/Fiji.app
test -d "$fijidir" || {
  >&2 echo "No Fiji installation found."
  exit 2
}

classpath=$(find "$fijidir/jars" "$fijidir/plugins" -name "*.jar" | tr '\n' ':')

kbMemAvailable=$(cat /proc/meminfo | grep 'MemAvailable' | head -n1 | sed 's/[^0-9]//g')
mbToUse=$(echo "3 * $kbMemAvailable / 4 / 1024" | bc)

echo "$libjvmPath"
echo 5
echo "-Xmx${mbToUse}m"
echo "--add-opens=java.base/java.lang=ALL-UNNAMED"
echo "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"
echo "-Djava.class.path=$classpath"
echo "-DtestFooJVM=testBarJVM"
echo "sc/fiji/Main"
echo "1"
echo "-DtestFooMain=testBarMain"
