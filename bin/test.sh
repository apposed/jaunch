#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[test]\033[0m"

test "$(uname -s)-$(uname -p)" = Linux-x86_64 || {
  echo "[ERROR] Sorry, tests only exist for Linux-x64 right now."
  exit 1
}

if ! command -v cram >/dev/null 2>&1
then
  echo "== Installing cram =="
  pip install --user cram==0.6
fi

cram test
