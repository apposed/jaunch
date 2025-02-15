#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[demo]\033[0m"

test -d dist || { echo '[ERROR] No dist folder; please `make dist` first.' 1>&2; exit 1; }

copyDependency() {
  g=$1
  a=$2
  v=$3
  if [ ! -f .cache/lib/$a-$v.jar ]
  then
    mkdir -p .cache/lib
    echo "Downloading $a..."
    (set -x; curl -fsL https://search.maven.org/remotecontent\?filepath\=$g/$a/$v/$a-$v.jar > .cache/lib/$a-$v.jar)
  fi
  if [ ! -f "$demoDir/lib/$a-$v.jar" ]
  then
    mkdir -p "$demoDir/lib"
    echo "Copying $a..."
    cp -v .cache/lib/$a-$v.jar "$demoDir/lib/"
  fi
}

selectWindowsEXE() {
  exe=$1
  kind=$2
  for f in "$demoDir/$exe-windows-"*; do
    case "$f" in
      *-$kind.exe) mv -v "$f" "${f%-$kind.exe}.exe" ;;
      *) rm -v "$f" ;;
    esac
  done
}

demoDir=demo
mkdir -p "$demoDir"

# Hi
bin/appify.sh --out-dir "$demoDir" --app-title Hi --app-exe hi \
  --app-id org.apposed.jaunch.hi --jaunch-toml configs/hi.toml \
  --app-icon icons/1F44B-waving-hand.svg
cp -v configs/HelloWorld.* "$demoDir"
selectWindowsEXE hi console

# Hello
bin/appify.sh --out-dir "$demoDir" --app-title Hello --app-exe hello \
  --app-id org.apposed.jaunch.hello --jaunch-toml configs/hello.toml \
  --app-icon icons/E1C0-wireframes.svg
cp -v configs/HelloSwing.* "$demoDir"
selectWindowsEXE hello gui

# Jy
bin/appify.sh --out-dir "$demoDir" --app-title Jy --app-exe jy \
  --app-id org.apposed.jaunch.jy --jaunch-toml configs/jy.toml \
  --app-icon icons/2615-hot-beverage.svg
copyDependency org/python jython 2.7.4
selectWindowsEXE jy console

# Parsy
bin/appify.sh --out-dir "$demoDir" --app-title Parsy --app-exe parsy \
  --app-id org.apposed.jaunch.parsy --jaunch-toml configs/parsy.toml \
  --app-icon icons/1F523-input-symbols.svg
copyDependency org/scijava parsington 3.1.0
selectWindowsEXE parsy console

# Paunch
bin/appify.sh --out-dir "$demoDir" --app-title Paunch --app-exe paunch \
  --app-id org.apposed.jaunch.paunch --jaunch-toml configs/paunch.toml \
  --app-icon icons/1F40D-snake.svg
# Note: We leave both EXE files (console and gui) for Paunch.

# REPL
bin/appify.sh --out-dir "$demoDir" --app-title REPL --app-exe repl \
  --app-id org.apposed.jaunch.repl --jaunch-toml configs/repl.toml \
  --app-icon icons/E1C1-code-editor.svg
selectWindowsEXE repl console

# Remove .sh extension from shell scripts.
# Just so it's a little bit easier to launch!
for f in "$demoDir"/*.sh; do
  mv "$f" "${f%.sh}"
done
