#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[dist]\033[0m"

test -d build || { echo '[ERROR] No build folder; please `make compile-all` first.' 1>&2; exit 1; }

appName=$1
test "$appName" || appName=launcher

copyFile() {
  srcPath=$1
  destDir=$2
  destName=$3
  makeExec=$4
  if [ ! -f "$srcPath" ]; then return; fi
  test "$destName" || destName="${srcPath##*/}"
  mkdir -pv "$destDir"
  cp -v "$srcPath" "$destDir/$destName"
  if [ "$makeExec" ]; then chmod -v +x "$destDir/$destName"; fi
}

# Clear out previous dist directory.
rm -rfv dist

# Copy native launcher executables.
for launcherBinary in build/launcher*
do
  targetFilename="$appName${launcherBinary#build/launcher}"
  binaryType=$(file -b "$launcherBinary" 2>/dev/null)
  case "$binaryType" in
    ELF*|Mach-O*) copyFile "$launcherBinary" dist "$targetFilename" true ;;
    *) copyFile "$launcherBinary" dist "$targetFilename" ;;
  esac
done

# Copy jaunch configurator executables.
copyFile build/bin/linuxArm64/releaseExecutable/jaunch.kexe dist/jaunch jaunch-linux-arm64 true
copyFile build/bin/linuxX64/releaseExecutable/jaunch.kexe dist/jaunch jaunch-linux-x64 true
copyFile build/bin/macosArm64/releaseExecutable/jaunch.kexe dist/jaunch jaunch-macos-arm64 true
copyFile build/bin/macosX64/releaseExecutable/jaunch.kexe dist/jaunch jaunch-macos-x64 true
copyFile build/bin/macosUniversal/releaseExecutable/jaunch.kexe dist/jaunch jaunch-macos true
copyFile build/bin/windowsArm64/releaseExecutable/jaunch.exe dist/jaunch jaunch-windows-arm64.exe
copyFile build/bin/windowsX64/releaseExecutable/jaunch.exe dist/jaunch jaunch-windows-x64.exe

# Copy property extractor helper programs and TOML configuration files.
copyFile configs/Props.class dist/jaunch
copyFile configs/props.py dist/jaunch
copyFile configs/common.toml dist/jaunch
copyFile configs/jvm.toml dist/jaunch
copyFile configs/python.toml dist/jaunch
copyFile configs/repl.toml dist/jaunch "$appName.toml"

# Copy platform-agnostic launch scripts.
copyFile configs/launcher.sh dist "$appName.sh" true
copyFile configs/launcher.bat dist "$appName.bat"

# Wrap it up into a tarball.
tar czf jaunch.tar.gz dist
