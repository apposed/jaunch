#!/bin/sh
set -e
make compile-launcher
cp build/launcher-macos-arm64 demo/hi-macos-arm64
cd demo
./hi-macos-arm64 "$@"
