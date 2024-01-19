#!/bin/bash
set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[compile-config]\033[0m"

./gradlew linkReleaseExecutable
