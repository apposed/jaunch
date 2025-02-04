#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[pack]\033[0m"

test -d dist || { echo '[ERROR] No dist folder; please `make dist` first.' 1>&2; exit 1; }

# Download upx as needed, if available for the current platform.
if [ ! -d .cache/upx ]
then
  v=4.2.4
  case "$(uname -s)-$(uname -m)" in
    Linux-x86_64) (
      mkdir -p .cache/upx &&
      cd .cache/upx &&
      curl -fsLO https://github.com/upx/upx/releases/download/v$v/upx-$v-amd64_linux.tar.xz &&
      tar xf upx-$v-amd64_linux.tar.xz
    ) ;;
    MINGW*-x86_64|MSYS*-x86_64) (
      mkdir -p .cache/upx &&
      cd .cache/upx &&
      curl -fsLO https://github.com/upx/upx/releases/download/v$v/upx-$v-win64.zip &&
      unzip upx-$v-win64.zip
    ) ;;
  esac
fi

# If possible, run upx to compress the available binaries.
if [ -x .cache/upx/*/upx ]
then
  # NB: The `-maxdepth 2` intentionally excludes Contents/MacOS/* binaries,
  # because upx complains that it does not support packing macOS executables.
  # There is a flag `--force-macos`, but when I tested it, it mangled the
  # Universal2 fat binary. Maybe we could pack each architecture, and only
  # afterward combine them with lipo? But doing that would be more involved.
  (set -x; .cache/upx/*/upx --best $(find dist -maxdepth 2 -perm /+x -type f)) || true
else
  echo '[WARNING] No upx for this platform; skipping executable packing.' 1>&2;
fi

# Display the result.
echo ================================================================
ls -l $(find dist -type f)
