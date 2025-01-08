#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[sign]\033[0m"

sign_linux() {
  echo '[INFO] Signing complete! Nothing was signed, because Linux binaries just work,'
  echo '[INFO] without invasively asking for permission from corporate overlords.'
}

sign_macos() {
  if [ ! "$DEV_ID" ]
  then
    echo '[ERROR] DEV_ID environment variable unset; cannot sign executables.'
    exit 1
  fi
  for exe in 'dist/Contents/MacOS/'*-macos-*
  do
    codesign --force --options runtime \
      --entitlements sign/entitlements.plist \
      --sign "$DEV_ID" "$exe"
    codesign -vv "$exe"
  done

  echo '[INFO] Signing complete!'
}

sign_windows() {
  if [ ! "$THUMBPRINT" ]; then
    echo '[ERROR] THUMBPRINT environment variable unset; cannot sign EXEs.'
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
    TIMESTAMP_SERVER='http://time.certum.pl/'
  fi

  "$signtool" sign /sha1 "$THUMBPRINT" \
    /tr "$TIMESTAMP_SERVER" \
    /td SHA256 /fd SHA256 /v \
    'dist\'*.exe \
    'dist\jaunch\jaunch-windows-'*.exe &&

  "$signtool" verify /pa /all \
    'dist\'*.exe \
    'dist\jaunch\jaunch-windows-'*.exe

  echo '[INFO] Signing complete!'
}

case "$(uname -s)" in
  Linux) sign_linux ;;
  Darwin) sign_macos ;;
  MINGW*|MSYS*) sign_windows ;;
  *)
    echo "[WARNING] Don't know how to sign binaries for platform: $(uname -s)"
  ;;
esac
