#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[app]\033[0m"

appDir=app

targetDir() {
  if [ "$1" ]; then result="$appDir/$1"; else result="$appDir"; fi
  if [ ! -d "$result" ]; then (set -x; mkdir -p "$result"); fi
  echo "$result"
}

if [ ! -d "$appDir/jaunch" ]; then (set -x; mkdir -p "$appDir"/jaunch); fi

# Copy launcher binaries within the dist folder, duplicating to all demo apps.
#
# We use the dist folder rather than build because those binaries may have
# been packed by upx, whereas those in build are the raw compiled outputs.
#
# And we use this awkward find construction to capture launcher binaries in
# both the base dist folder as well as nested under dist/Contents/MacOS.
echo "Copying launcher binaries..."
find dist -name 'launcher-*' | while read f
do
  srcDir=${f%/launcher*}
  targetDir=$(targetDir "${srcDir#dist}")
  suffix=${f#*/launcher}

  # Strip Windows-specific -console and -gui suffixes.
  case "$suffix" in
    *-console.exe) suffix="${suffix%-console.exe}.exe" ;;
    *-gui.exe) suffix="${suffix%-gui.exe}.exe" ;;
  esac

  # Populate console apps.
  test "$f" != "${f%-gui.exe}" && winGUI=true || winGUI=false
  if [ "$winGUI" = false ]
  then
    for app in jy parsy paunch repl
    do
      (set -x; cp "$f" "$targetDir/$app$suffix")
    done
  fi

  # Populate GUI apps.
  test "$f" != "${f%-console.exe}" && winConsole=true || winConsole=false
  if [ "$winConsole" = false ]
  then
    for app in hello
    do
      (set -x; cp "$f" "$targetDir/$app$suffix")
    done
  fi
done

# Copy wrapper launch scripts.
for app in jy parsy paunch repl hello
do
  case "$(uname -s)" in
    MINGW*|MSYS*) (
      set -x
      cp configs/launcher.bat "$appDir/$app.bat"
    );;
    *) (
      set -x
      cp configs/launcher.sh "$appDir/$app"
      chmod +x "$appDir/$app"
    );;
  esac
done

# Copy hello app Java program.
(set -x; cp configs/HelloSwing.class "$appDir/")

# Copy jaunch configurator binaries.
echo "Copying jaunch configurator binaries..."
find dist -name 'jaunch-*' | while read f
do
  srcDir=${f%/jaunch-*}
  targetDir=$(targetDir "${srcDir#dist/}")
  (set -x; cp "$f" "$targetDir/")
done

# Copy Props.class helper program and TOML configuration files.
echo "Copying configuration files and helpers..."
for f in \
  Props.class \
  common.toml \
  hello.toml \
  jvm.toml \
  jy.toml \
  parsy.toml \
  paunch.toml \
  python.toml \
  repl.toml
do
  if [ ! -f "app/jaunch/$f" ]
  then
    (set -x; cp "configs/$f" app/jaunch/)
  fi
done

# Install needed JAR files.
copyDependency() {
  g=$1
  a=$2
  v=$3
  if [ ! -f .lib/$a-$v.jar ]
  then
    mkdir -p .lib
    echo "Downloading $a..."
    (set -x; curl -fsL https://search.maven.org/remotecontent\?filepath\=$g/$a/$v/$a-$v.jar > .lib/$a-$v.jar)
  fi
  if [ ! -f app/lib/$a-$v.jar ]
  then
    mkdir -p app/lib
    echo "Copying $a..."
    (set -x; cp .lib/$a-$v.jar app/lib/)
  fi
}
copyDependency org/scijava parsington 3.1.0 # dependency of parsy
copyDependency org/python jython 2.7.3      # dependency of jy
