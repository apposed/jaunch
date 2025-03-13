#!/usr/bin/env bash

STEP_PREFIX='[WINDOWS] '
. "${0%/*}/common.include"

# Parse arguments.
test $# -eq 2 || {
  echo 'Usage: inject-icon.sh app-icon app-exe'
  echo 'Injects the icon from the given ICO file into the specified EXE file.'
  exit 1
}

icon="$1"
exe="$2"

# Validate arguments.
test -f "$icon" || die "Not a file: $icon"
test -f "$exe" || die "Not a file: $exe"

# Check whether the executable is code-signed.
# Although signtool.exe is capable of re-signing previously signed
# executables, it will fail when attempting to re-sign an EXE that
# had an icon injected after the first code signing operation.
# The workaround is to first un-sign the EXE, then inject the icon.
step 'Checking for existing code signature'
isSigned=
if strings "$exe" |
  grep -iq "certificat\|authenticode\|digisign"
then
  # Executable is already signed! The signature
  # needs to be removed before injecting the icon.
  isSigned=1
fi

# Check whether the operating system is Windows.
isWindows=
case "$(uname)" in
  MINGW*|MSYS*) isWindows=1 ;;
esac

# Remove any existing code signature before proceeding.
if [ "$isSigned" ]
then
  if [ -z "$isWindows" ]
  then
    die "Executable '$exe' is signed. Injecting an icon\n" \
      "will invalidate the signature and prevent re-signing."
  fi

  # Find the correct signtool.exe.
  step 'Locating signtool.exe'
  arch=$(uname -m)
  case "$arch" in
    x86_64) arch=x64 ;;
  esac
  signtool=$(
    find '/c/Program Files'*'/Windows Kits' -name signtool.exe |
      grep "/$arch/" | head -n1
  )
  test "$signtool" || die "signtool.exe not found"
  test -f "$signtool" || die "signtool.exe is not a file: $signtool"
  echo "$signtool"

  step 'Removing existing code signature'

  # Note: The MSYS2_ARG_CONV_EXCL variable setting tells Git Bash not
  # to attempt any conversion on arguments containing the / character,
  # so that the signtool parameters are preserved correctly.

  MSYS2_ARG_CONV_EXCL=/ "$signtool" remove /s "$exe"
fi

# Download rcedit.exe as needed.
cachedir="$basedir/.cache"
rcedit_release=https://github.com/electron/rcedit/releases/download/v2.0.0
rcedit_filename=rcedit-x64.exe
rcedit="$cachedir/$rcedit_filename"
if [ ! -f "$rcedit" ]; then
  step 'Downloading rcedit.exe'
  mkdir -pv "$cachedir"
  cd "$cachedir"
  curl -fLO "$rcedit_release/$rcedit_filename" ||
    die "Failed to download $rcedit_filename for icon embedding."
  cd - 2>/dev/null
fi

# Embed the icon into the EXE file.
step "Adding icon to EXE: $exe"
if [ "$isWindows" ]; then
  # Windows OS can run rcedit.exe directly.
  (set -x; "$rcedit" "$exe" --set-icon "$icon")
else
  # Non-Windows OS must use wine.
  if command -v wine >/dev/null; then
    (set -x; wine "$rcedit" "$exe" --set-icon "$icon") 2>&1 |
      grep -v ':fixme:'
  else
    warn "Cannot embed icon into '$exe'; please install wine."
  fi
fi
