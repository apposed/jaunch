#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[dist]\033[0m"

appName=$1
test "$appName" || appName=launcher

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

# Clear out previous dist directory.
rm -rf dist

os=$(uname -s)
case "$os" in
  Linux) os=linux ;;
  Darwin) os=macos ;;
  MINGW*) os=windows ;;
esac
arch=$(uname -m)
case "$arch" in
  x86_64|amd64) arch=x64 ;;
  arm64) arch=aarch64 ;;
esac
suffix="$os-$arch"

# Copy native launcher executable.
posixLauncherBinaryPath=build/launcher
posixLauncherBinaryType=$(file -b "$posixLauncherBinaryPath" 2>/dev/null)
case "$posixLauncherBinaryType" in
  ELF*) copyBinary "$posixLauncherBinaryPath" dist "$appName-$suffix" true ;;
  Mach-O*) copyBinary "$posixLauncherBinaryPath" dist/Contents/MacOS "$appName-$suffix" true ;;
esac
copyBinary build/launcher.exe dist "$appName-$suffix".exe

# Copy jaunch configurator executables.
posixJaunchBinaryPath=build/bin/posix/releaseExecutable/jaunch.kexe
posixJaunchBinaryType=$(file -b "$posixJaunchBinaryPath" 2>/dev/null)
case "$posixJaunchBinaryType" in
  ELF*) copyBinary "$posixJaunchBinaryPath" dist/jaunch "jaunch-$suffix" ;;
  Mach-O*) copyBinary "$posixJaunchBinaryPath" dist/Contents/MacOS "jaunch-$suffix" ;;
esac
copyBinary build/bin/windows/releaseExecutable/jaunch.exe dist/jaunch "jaunch-$suffix.exe"

# Copy TOML configuration files.
if [ ! -f dist/jaunch/jaunch.toml ]
then
  mkdir -p dist/jaunch
  (set -x; cp jaunch.toml dist/jaunch/)
fi

# Copy Props.class helper program.
if [ ! -f dist/jaunch/Props.class ]
then
  (set -x; cp Props.class dist/jaunch/)
fi

# Wrap it up into a tarball.
tar czf jaunch.tar.gz dist
