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
  if [ "$makeExec" ]; then (set -x; chmod +x "$destDir/$destName"); fi
}

# Clear out previous dist directory.
rm -rf dist

# Copy native launcher executables.
for launcherBinary in build/launcher*
do
  targetFilename="$appName${launcherBinary#build/launcher}"
  binaryType=$(file -b "$launcherBinary" 2>/dev/null)
  case "$binaryType" in
    ELF*) copyBinary "$launcherBinary" dist "$targetFilename" true ;;
    Mach-O*) copyBinary "$launcherBinary" dist/Contents/MacOS "$targetFilename" true ;;
    *) copyBinary "$launcherBinary" dist "$targetFilename" ;;
  esac
done

# Copy jaunch configurator executables.
copyBinary build/bin/linuxArm64/releaseExecutable/jaunch.kexe dist/jaunch jaunch-linux-arm64 true
copyBinary build/bin/linuxX64/releaseExecutable/jaunch.kexe dist/jaunch jaunch-linux-x64 true
copyBinary build/bin/macosArm64/releaseExecutable/jaunch.kexe dist/Contents/MacOS jaunch-macos-arm64 true
copyBinary build/bin/macosX64/releaseExecutable/jaunch.kexe dist/Contents/MacOS jaunch-macos-x64 true
copyBinary build/bin/macosUniversal/releaseExecutable/jaunch.kexe dist/Contents/MacOS jaunch-macos true
copyBinary build/bin/windows/releaseExecutable/jaunch.exe dist/jaunch jaunch-windows-x64.exe

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
