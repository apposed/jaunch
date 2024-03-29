#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[app]\033[0m"

# Copy Jaunch binaries and configuration from dist folder.
(set -x; cp -rp dist/* app) &&
find app -name 'launcher*' | while read l
do (
  srcDir=${l%/launcher*}
  suffix=${l#*/launcher}
  set -x
  cp "$l" "$srcDir/jy$suffix"
  mv "$l" "$srcDir/parsy$suffix"
) done

# Install needed JAR files.
copyDependency() {
  g=$1
  a=$2
  v=$3
  if [ ! -f app/lib/$a-$v.jar ]
  then
    mkdir -p app/lib
    (set -x; curl -fsL https://search.maven.org/remotecontent\?filepath\=$g/$a/$v/$a-$v.jar > app/lib/$a-$v.jar)
  fi
}
copyDependency org/scijava parsington 3.1.0
copyDependency org/python jython 2.7.3
