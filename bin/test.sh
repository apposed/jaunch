#!/bin/sh
set -e
cd "$(dirname "$0")/.."

if ! command -v cram >/dev/null 2>&1
then
  echo "== Installing cram =="
  pip install --user cram==0.6
fi

cram tests
