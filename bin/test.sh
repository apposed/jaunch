#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[test]\033[0m"

test -d demo || { echo '[ERROR] No demo folder; please `make demo` first.' 1>&2; exit 1; }

if ! command -v prysk >/dev/null 2>&1
then
  echo '[ERROR] Please install prysk. One easy way is via uv:'
  echo
  echo '    uv tool install prysk'
  exit 2
fi

prysk -v test
