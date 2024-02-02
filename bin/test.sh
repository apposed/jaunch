#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[test]\033[0m"

if ! command -v cram >/dev/null 2>&1
then
  echo "== Installing cram =="
  pip install --user cram==0.6
fi

cram tests
