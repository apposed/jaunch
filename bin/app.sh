#!/bin/bash
set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[app]\033[0m"

copyBinary() {
  srcPath=$1
  destDir=$2
  destName=$3
  makeExec=$4
  if [ ! -f "$srcPath" ]; then return; fi
  mkdir -p "$destDir"
  (set -x; cp "$srcPath" "$destDir/$destName")
  if [ "$makeExec" ]; then chmod +x "$destDir/$destName"; fi
}

# Copy native launcher executable.
posixLauncherBinaryPath=build/launcher
posixLauncherBinaryType=$(file -b "$posixLauncherBinaryPath" 2>/dev/null)
case "$posixLauncherBinaryType" in
  ELF*) copyBinary "$posixLauncherBinaryPath" app jy true ;;
  Mach-O*) copyBinary "$posixLauncherBinaryPath" app/Contents/MacOS jy true ;;
esac
copyBinary build/launcher.exe app jy.exe

# Copy jaunch configurator executables.
posixJaunchBinaryPath=build/bin/posix/releaseExecutable/jaunch.kexe
posixJaunchBinaryType=$(file -b "$posixJaunchBinaryPath" 2>/dev/null)
case "$posixJaunchBinaryType" in
  ELF*) copyBinary "$posixJaunchBinaryPath" app/jaunch jaunch ;;
  Mach-O*) copyBinary "$posixJaunchBinaryPath" app/Contents/MacOS jaunch ;;
esac
copyBinary build/bin/windows/releaseExecutable/jaunch.exe app/jaunch jaunch.exe

# Copy TOML configuration files.
if [ ! -f app/jaunch/jaunch.toml ]
then
  mkdir -p app/jaunch
  (set -x; cp jaunch.toml app/jaunch/)
fi

# Copy Props.class helper program.
if [ ! -f app/jaunch/Props.class ]
then
  (set -x; cp Props.class app/jaunch/)
fi

# Install needed JAR files.
if [ ! -f app/lib/jython-2.7.3.jar ]
then
  mkdir -p app/lib
  (set -x; curl -fsL https://search.maven.org/remotecontent\?filepath\=org/python/jython/2.7.3/jython-2.7.3.jar > app/lib/jython-2.7.3.jar)
fi
