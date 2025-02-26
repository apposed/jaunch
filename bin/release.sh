#!/usr/bin/sh

STEP_PREFIX='[RELEASE] '
. "${0%/*}/common.include"

# For reasons of laziness, we restrict ourselves to Linux.
test "$(uname)" = Linux || die 'Not a Linux system.'

cd "$(dirname "$0")/.."

release=$(grep '^version = ' build.gradle.kts |
  sed 's/version = "\([^"]*\)-SNAPSHOT".*/\1/')

echo "This script will release Jaunch at version $release."
echo 'Are you sure? Press ENTER to continue, or ^C to cancel.'
read answer

make clean &&
git diff-index --quiet HEAD -- ||
  die 'Dirty working copy.'

# Set version to a release value.
step 'Incrementing version' &&
sed -i -e 's/-SNAPSHOT//' build.gradle.kts &&
version=$(grep '^version = ' build.gradle.kts |
  sed 's/version = "\([^"]*\)".*/\1/') ||
  die 'Version bump failed.'

# Commit and push.
step 'Performing git operations' &&
git commit -m "Release version $release" build.gradle.kts &&
git push ||
  die 'Git operations failed.'

# Wait for the CI build.
step 'Waiting for the CI build to finish' &&
echo 'Please visit https://github.com/apposed/jaunch/actions' &&
echo 'When the release build completes successfully, click into the' &&
echo 'build and download the "jaunch" artifact to this working copy.' &&
echo 'Once jaunch.zip exists in the right place, press ENTER to continue.' &&
read answer ||
  die 'Went awry while waiting for CI completion.'

# Unpack the jaunch artifact.
step 'Unpacking the Jaunch build artifact' &&
unzip jaunch.zip &&
tar xvf jaunch.tar.gz &&
test -d "$distdir" ||
  die 'Failed to unpack Jaunch artifact.'

# Construct Jaunch.app (macOS) and jaunch.desktop (Linux).
step 'Constructing default Jaunch applications' &&
"$script_dir/appify.sh" \
  --app-exe launcher \
  --app-id org.apposed.jaunch \
  --app-title Jaunch \
  --jaunch-toml "$distdir"/jaunch/launcher.toml \
  --out-dir "$distdir" &&
mv "$distdir"/launcher.desktop "$distdir"/jaunch.desktop ||
  die 'Failed to construct Jaunch applications.'

# Copy documentation and shell scripts into the distribution.
step 'Copying resources into the Jaunch distribution' &&
cp -rpv README.md UNLICENSE bin doc "$distdir/" &&
# Remove the build-system-specific scripts, leaving only the utility scripts.
rm -v "$distdir/bin/release.sh" \
  $(grep -o 'bin/[^ ]*.sh' Makefile | sed "s;^;$distdir/;" ) ||
  die 'Failed to copy resources into the distribution.'

# Generate RELEASE file.
step 'Generating RELEASE file' &&
cat >"$distdir/RELEASE" <<EOL
version = $release
datestamp = $(date)
EOL
test $? -eq 0 ||
  die 'Failed to write RELEASE file.'

# Slim down the packable executables.
step 'Compressing executables' &&
bin/pack.sh $(find "$distdir" -name '*-linux-*') \
  $(find "$distdir" -name '*-windows-x64*.exe') ||
  die 'Failed to pack the executables.'

# Bundle up files needing macOS code signing.
step 'Zipping up Jaunch.app for macOS code signing'
rm -rfv jaunch-bin-macos &&
mkdir -pv jaunch-bin-macos/bin &&
cp -v "$distdir/RELEASE" jaunch-bin-macos/ &&
cp -v bin/common.include bin/sign.sh jaunch-bin-macos/bin/ &&
mkdir -pv jaunch-bin-macos/configs &&
cp -v configs/entitlements.plist jaunch-bin-macos/configs/ &&
cp -rpv "$distdir/Jaunch.app" jaunch-bin-macos/ &&
zip -r9 jaunch-bin-macos.zip jaunch-bin-macos ||
  die 'Failed to zip up code-signable macOS files.'

# Bundle up files needing Windows code signing.
step 'Zipping up EXEs for Windows code signing'
rm -rfv jaunch-bin-windows &&
mkdir -pv jaunch-bin-windows/bin &&
cp -v "$distdir/RELEASE" jaunch-bin-windows/ &&
cp -v bin/common.include bin/sign.sh jaunch-bin-windows/bin/ &&
cp -pv $(find "$distdir" -name '*.exe') jaunch-bin-windows/ &&
zip -r9 jaunch-bin-windows.zip jaunch-bin-windows ||
  die 'Failed to zip up code-signable Windows files.'

# Wait for macOS and Windows code signing.
step 'Waiting for code signing' &&
echo 'Sign the .app inside jaunch-bin-macos.zip on a macOS machine.' &&
echo 'Sign the EXEs inside jaunch-bin-windows.zip on a Windows machine.' &&
echo 'Then transfer the files back to their original spots within' &&
echo "     $distdir" &&
echo 'For instructions, see doc/MACOS.md and doc/WINDOWS.md.' &&
echo 'Once you have done so, press ENTER to continue.' &&
read answer ||
  die 'Went awry while waiting for code-signing to happen.'

# Zip up the result.
step 'Constructing Jaunch distribution archive' &&
mv "$distdir" "jaunch-$release" &&
archive="jaunch-$release.zip" &&
zip -r9 "$archive" "jaunch-$release" ||
  die 'Failed to ZIP up the distribution folder.'

# Tag the successful release.
git tag "$release" &&
git push tag ||
  die 'Failed to tag release.'

# Bump to next development version.
step 'Bumping to the next development cycle' &&
vprefix=${release%.*} &&
vsuffix=${release##*.} &&
nversion="$vprefix.$((vsuffix+1))-SNAPSHOT" &&
sed -i -e 's/version = "[^"]*"/version = "'"$nversion"'"/' build.gradle.kts &&
git commit -m "Bump to next development cycle" build.gradle.kts &&
git push ||
  die 'Failed to bump version and commit.'

step 'Complete! :-)'
ls -la "$archive"
unzip -l "$archive"
