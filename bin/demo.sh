#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[demo]\033[0m"

test -d dist || { echo '[ERROR] No dist folder; please `make dist` first.' 1>&2; exit 1; }

section() { echo -e "\033[0;32m--== $* ==--\033[0m"; }

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
  find "$demoDir" -maxdepth 1 -name "$exe"-windows-* |
  while read exe; do
    case "$exe" in
      *-$kind.exe) mv -v "$exe" "${exe%-$kind.exe}.exe" ;;
      *) rm -v "$exe" ;;
    esac
  done
}

demoDir=demo
mkdir -p "$demoDir"

# Hi
section 'Hi'
bin/appify.sh --out-dir "$demoDir" --app-title Hi --app-exe hi \
  --app-id org.apposed.jaunch.hi --jaunch-toml configs/hi.toml \
  --app-icon icons/1F44B-waving-hand.svg
cp -v configs/HelloWorld.* "$demoDir"
selectWindowsEXE hi console

# Hiss
echo
section 'Hiss'
bin/appify.sh --out-dir "$demoDir" --app-title Hiss --app-exe hiss \
  --app-id org.apposed.jaunch.hiss --jaunch-toml configs/hiss.toml \
  --app-icon icons/1F602-tears-of-joy.svg
cp -v configs/hi.py "$demoDir"
selectWindowsEXE hiss console

# Hello
echo
section 'Hello'
bin/appify.sh --out-dir "$demoDir" --app-title Hello --app-exe hello \
  --app-id org.apposed.jaunch.hello --jaunch-toml configs/hello.toml \
  --app-icon icons/1F604-smiling-eyes.svg
cp -v configs/HelloSwing.* "$demoDir"
selectWindowsEXE hello gui

# Jy
echo
section 'Jy'
bin/appify.sh --out-dir "$demoDir" --app-title Jy --app-exe jy \
  --app-id org.apposed.jaunch.jy --jaunch-toml configs/jy.toml \
  --app-icon icons/2615-hot-beverage.svg
copyDependency org/python jython 2.7.4
selectWindowsEXE jy console

# Parsy
echo
section 'Parsy'
bin/appify.sh --out-dir "$demoDir" --app-title Parsy --app-exe parsy \
  --app-id org.apposed.jaunch.parsy --jaunch-toml configs/parsy.toml \
  --app-icon icons/1F523-input-symbols.svg
copyDependency org/scijava parsington 3.1.0
selectWindowsEXE parsy console

# Paunch
echo
section 'Paunch'
bin/appify.sh --out-dir "$demoDir" --app-title Paunch --app-exe paunch \
  --app-id org.apposed.jaunch.paunch --jaunch-toml configs/paunch.toml \
  --app-icon icons/1F40D-snake.svg
# Note: We leave both EXE files (console and gui) for Paunch.

# REPL
echo
section 'REPL'
bin/appify.sh --out-dir "$demoDir" --app-title REPL --app-exe repl \
  --app-id org.apposed.jaunch.repl --jaunch-toml configs/repl.toml \
  --app-icon icons/E1C1-code-editor.svg
selectWindowsEXE repl console

# Remove .sh extension from shell scripts.
# Just so it's a little bit easier to launch!
echo
for f in "$demoDir"/*.sh; do
  mv -v "$f" "${f%.sh}"
done

# Remove any extraneous jaunch/jaunch-macos* binaries.
# Find them in the Contents/MacOS folder of each .app.
find "$demoDir/jaunch" -name 'jaunch-macos*' | while read f; do (
  set -x; rm "$f"
) done
