#!/bin/sh
set -e
cd "$(dirname "$0")/.."

./gradlew -i build
