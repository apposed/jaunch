#!/usr/bin/env bash

STEP_PREFIX='[RELEASE] '
. "${0%/*}/common.include"

cd "$(dirname "$0")/.."

version=$(grep '^version = ' build.gradle.kts |
  sed 's/version = "\(.*\)-SNAPSHOT"/\1/')

echo "This script will release Jaunch at version $version."
read -p 'Are you sure? Press ENTER to continue, or ^C to cancel.'

make clean &&
git diff-index --quiet HEAD -- ||
  die 'Dirty working copy'

# Set version to a release value.
step 'Incrementing version' &&
sed -i -e 's/-SNAPSHOT//' build.gradle.kts &&
version=$(grep '^version = ' build.gradle.kts | sed 's/version = "\(.*\)"/\1/') ||
  die 'Version bump failed'

# Commit, tag, and push.
step 'Performing git operations' &&
git commit -m "Release version $version" build.gradle &&
git push &&
git tag "$version" &&
git push tag ||
  die 'Git operations failed.'

# Wait for the CI build to start.
step 'Waiting for the CI build to start' &&
echo 'Please visit https://github.com/apposed/jaunch/actions' &&
read -p 'After the release build starts, press ENTER to continue.' ||
  die 'Went awry while waiting.'

# Bump to next development version.
step 'Bumping to the next development cycle' &&
vprefix=${version%.*} &&
vsuffix=${version##*.} &&
nversion="$vprefix.$((vsuffix+1))-SNAPSHOT" &&
sed -i -e 's/version = "[^"]*"/version = "'"$nversion"'"/' build.gradle.kts &&
git commit -m "Bump to next development cycle" build.gradle &&
git push ||
  die 'Failed to bump version and commit.'

# Wait for the CI build to finish.
step 'Waiting for the CI build to finish' &&
echo 'Please check https://github.com/apposed/jaunch/actions again.' &&
echo 'When the release build completes successfully, click into the' &&
echo 'build and download the "jaunch" artifact to jaunch working copy.' &&
read -p 'Once jaunch.zip exists in the right place, press ENTER to continue.' ||
  die 'Went awry while waiting.'

# Unpack the jaunch artifact.
step 'Unpacking the Jaunch build artifact' &&
unzip jaunch.zip &&
tar xvf jaunch.tar.gz &&
test -d dist ||
  die 'Failed to unpack Jaunch artifact.'

# Construct the release distribution archive.
step 'Constructing Jaunch distribution archive' &&
cp -rpv README.md UNLICENSE bin doc dist/
mv dist "jaunch-$nversion"
archive="jaunch-$nversion.zip"
zip -r9 "$archive" "jaunch-$nversion"

# START HERE
#bin/appify.sh --app-exe launcher --app-id org.apposed.jaunch --app-title Jaunch --jaunch-toml dist/jaunch/launcher.toml --out-dir dist

step 'Complete!'
ls -la "$archive"
unzip -l "$archive"
