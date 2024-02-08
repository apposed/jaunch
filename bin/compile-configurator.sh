#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[compile-configurator]\033[0m"

# Discern version values.
version=$(grep '^version = ' build.gradle.kts | sed 's/.*"\([^"]*\)".*/\1/')
gitHash=$(git rev-parse --short HEAD)
echo "version -> $version"
echo "gitHash -> $gitHash"

# Replace placeholders with actual version and git hash.
mv src/commonMain/kotlin/version.kt src/commonMain/kotlin/version.kt.original
sed -e "s/{{{VERSION}}}/$version/" -e "s/{{{GIT-HASH}}}/$gitHash/" src/commonMain/kotlin/version.kt.original > src/commonMain/kotlin/version.kt

set +e
./gradlew --no-daemon linkReleaseExecutable
result=$?
# Replace the modified version.kt with its original content.
mv -f src/commonMain/kotlin/version.kt.original src/commonMain/kotlin/version.kt
exit $result
