#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[clean]\033[0m"

rm -rfv \
  build \
  dist \
  app/Contents \
  app/jaunch \
  app/jy* \
  app/parsy* \
  app/paunch* \
  app/repl* \
  jaunch.tar.gz
