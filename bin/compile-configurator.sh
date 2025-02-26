#!/usr/bin/env bash

# Note the use here of `$(dirname "$0")` instead of `${0%/*}`.
# On Windows, when running a shell script via make, the
# `$0` contains backslashes rather than forward slashes.
. "$(dirname "$0")/common.include"

set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[compile-configurator]\033[0m"

# Report version values (discerned in common.include above).
echo "version -> $version"
echo "gitHash -> $gitHash"

# Replace placeholders with actual version and git hash.
mv src/commonMain/kotlin/version.kt src/commonMain/kotlin/version.kt.original
sed -e "s/{{{VERSION}}}/$version/" -e "s/{{{GIT-HASH}}}/$gitHash/" src/commonMain/kotlin/version.kt.original > src/commonMain/kotlin/version.kt

# Call the appropriate Gradle targets for the current platform.
set +e
platform=$(uname -s)
case "$platform" in
  Linux)
    (set -x; ./gradlew --no-daemon linkReleaseExecutableLinuxX64 linkReleaseExecutableLinuxArm64)
    result=$?
    ;;
  Darwin)
    (set -x; ./gradlew --no-daemon linkReleaseExecutableMacosX64 linkReleaseExecutableMacosArm64)
    result=$?
    if [ "$result" -eq 0 ]
    then
      # Merge the macOS binaries into a Universal2 fat binary.
      outDir=build/bin/macosUniversal/releaseExecutable
      mkdir -p "$outDir"
      (set -x; lipo -create -output "$outDir/jaunch.kexe" build/bin/macosArm64/releaseExecutable/jaunch.kexe build/bin/macosX64/releaseExecutable/jaunch.kexe)
      result=$?
    fi
    ;;
  MINGW*|MSYS*)
    (set -x; ./gradlew --no-daemon linkReleaseExecutableWindows)
    result=$?
    ;;
  *)
    echo -e "\033[1;31m[ERROR] Unsupported platform: $platform\033[0m"
    result=1
esac

# Replace the modified version.kt with its original content.
mv -f src/commonMain/kotlin/version.kt.original src/commonMain/kotlin/version.kt
exit $result
