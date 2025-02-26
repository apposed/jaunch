#!/usr/bin/env bash

. "${0%/*}/common.include"

# Check arguments.
test $# -ge 1 || {
  echo 'Usage: pack.sh executable-file [executable-file ...]'
  echo 'Compress executables using upx to reduce binary sizes.'
  exit 1
}

# Download upx as needed, if available for the current platform.
upxdir="$basedir/.cache/upx"
if [ ! -d "$upxdir" ]
then
  v=4.2.4
  case "$(uname -s)-$(uname -m)" in
    Linux-x86_64) (
      mkdir -p "$upxdir" && cd "$upxdir" &&
      curl -fsLO "https://github.com/upx/upx/releases/download/v$v/upx-$v-amd64_linux.tar.xz" &&
      tar xf upx-$v-amd64_linux.tar.xz
    ) ;;
    MINGW*-x86_64|MSYS*-x86_64) (
      mkdir -p "$upxdir" && cd "$upxdir" &&
      curl -fsLO "https://github.com/upx/upx/releases/download/v$v/upx-$v-win64.zip" &&
      unzip upx-$v-win64.zip
    ) ;;
  esac
fi

# If possible, run upx to compress the available binaries.
test -x "$upxdir"/*/upx || die 'No upx for this platform; cannot pack executables.'

before=$(du -bc "$@" | tail -n1 | cut -f1)
(set -x; "$upxdir"/*/upx --force --best "$@")
after=$(du -bc "$@" | tail -n1 | cut -f1)
percent=$(echo "100 * ($before - $after) / $before" | bc)
step "Result: $before -> $after; $percent% smaller"
