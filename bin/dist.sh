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

# Copy native launcher executable.
posixLauncherBinaryPath=build/launcher
posixLauncherBinaryType=$(file -b "$posixLauncherBinaryPath" 2>/dev/null)
case "$posixLauncherBinaryType" in
  ELF*) copyBinary "$posixLauncherBinaryPath" dist "$appName" true ;;
  Mach-O*) copyBinary "$posixLauncherBinaryPath" dist/Contents/MacOS "$appName" true ;;
esac
copyBinary build/launcher.exe dist "$appName".exe

# Copy jaunch configurator executables.
posixJaunchBinaryPath=build/bin/posix/releaseExecutable/jaunch.kexe
posixJaunchBinaryType=$(file -b "$posixJaunchBinaryPath" 2>/dev/null)
case "$posixJaunchBinaryType" in
  ELF*) copyBinary "$posixJaunchBinaryPath" dist/jaunch jaunch ;;
  Mach-O*) copyBinary "$posixJaunchBinaryPath" dist/Contents/MacOS jaunch ;;
esac
copyBinary build/bin/windows/releaseExecutable/jaunch.exe dist/jaunch jaunch.exe

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

# Download upx as needed, if available for the current platform.
if [ ! -d .upx ]
then
  v=4.2.2
  case "$(uname -s)-$(uname -m)" in
    Linux-x86_64)
      mkdir .upx &&
      cd .upx &&
      curl -fsLO https://github.com/upx/upx/releases/download/v$v/upx-$v-amd64_linux.tar.xz &&
      tar xf upx-$v-amd64_linux.tar.xz
      ;;
    MINGW*-x86_64)
      mkdir .upx &&
      cd .upx &&
      curl -fsLO https://github.com/upx/upx/releases/download/v$v/upx-$v-win64.zip &&
      unzip upx-$v-win64.zip
      ;;
  esac
fi

# If possible, run upx to compress the available binaries.
if [ -x .upx/*/upx ]
then
  (
    set -x;
    .upx/*/upx \
      "dist/$appName" \
      "dist/Contents/MacOS/$appName" \
      "dist/$appName.exe" \
      dist/jaunch/jaunch \
      dist/jaunch/Contents/MacOS/jaunch \
      dist/jaunch/jaunch.exe 2>&1
  ) | grep -v FileNotFoundException
fi

# Display the result.
echo ================================================================
ls -Rl dist
