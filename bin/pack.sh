#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[pack]\033[0m"

# Download upx as needed, if available for the current platform.
if [ ! -d .upx ]
then
  v=4.2.2
  case "$(uname -s)-$(uname -m)" in
    Linux-x86_64)
      mkdir .upx &&
      cd .upx &&
      curl -fsLO https://github.com/upx/upx/releases/download/v$v/upx-$v-amd64_linux.tar.xz &&
      tar xf upx-$v-amd64_linux.tar.xz
      ;;
    MINGW*-x86_64)
      mkdir .upx &&
      cd .upx &&
      curl -fsLO https://github.com/upx/upx/releases/download/v$v/upx-$v-win64.zip &&
      unzip upx-$v-win64.zip
      ;;
  esac
fi

# If possible, run upx to compress the available binaries.
if [ -x .upx/*/upx ]
then
  (
    set -x;
    .upx/*/upx $(find dist -perm /+x -type f; find dist -iname '*.exe')
  )
fi

# Display the result.
echo ================================================================
ls -Rl dist
