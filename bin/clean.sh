#!/bin/sh
set -e
cd "$(dirname "$0")/.."

rm -rfv build app/jy app/jaunch/jaunch* app/jaunch/*.class
