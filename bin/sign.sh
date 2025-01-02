#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[sign]\033[0m"

appDir=app

if [ ! "$THUMBPRINT" ]; then
  echo "[ERROR] THUMBPRINT environment variable unset; cannot sign EXEs."
  exit 1
fi

# Find the correct signtool.exe.
arch=$(uname -m)
case "$arch" in
  x86_64) arch=x64 ;;
esac
signtool=$(
  find '/c/Program Files'*'/Windows Kits' -name signtool.exe |
    grep "/$arch/" | head -n1
)

if [ -f "$signtool" ]
then
  echo "Found signtool.exe at: $signtool"
else
  echo "[ERROR] signtool.exe not found at: $signtool"
  exit 1
fi

if [ ! "$TIMESTAMP_SERVER" ]; then
  TIMESTAMP_SERVER="http://time.certum.pl/"
fi

"$signtool" sign /sha1 "$THUMBPRINT" \
  /tr "$TIMESTAMP_SERVER" \
  /td SHA256 /fd SHA256 /v \
  "$appDir\\"*.exe \
  "$appDir\\jaunch\\jaunch-windows-"*.exe &&

"$signtool" verify /pa /all \
  "$appDir\\"*.exe \
  "$appDir\\jaunch\\jaunch-windows-"*.exe

echo "Signing complete!"
