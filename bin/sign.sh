#!/usr/bin/env bash

. "${0%/*}/common.include"

# Check arguments.
test $# -ge 1 || {
  echo 'Usage: sign.sh executable-or-app [executable-or-app ...]'
  echo 'Perform code signing on executables and/or apps using platform-specific tools.'
  exit 1
}

sign_linux() {
  step 'Signing complete! Nothing was signed, because Linux binaries just work,'
  step 'without invasively asking for permission from corporate overlords.'
}

sign_macos() {
  test "$DEV_ID" ||
    die 'Please set DEV_ID environment variable; see doc/MACOS.md#code-signing'

  for f in "$@"; do
    case "$f" in
      *.app)
        # Sign and verify the app bundle recursively.
        step "Signing app: $f"
        codesign --force --timestamp --options runtime \
          --entitlements "$basedir/configs/entitlements.plist" \
          --sign "Developer ID Application: $DEV_ID" --deep "$f"
        step 'Verifying signature'
        codesign -vv --deep "$f"

        # Submit the app for notarization.
        (
          fn=$(basename "$f")
          step "Submitting $fn for notarization (this may take several minutes)"
          cd "$(dirname "$f")"
          zipFile="${fn%.app}.zip"
          ditto -c -k --keepParent "$fn" "$zipFile"
          xcrun notarytool submit "$zipFile" --wait --keychain-profile notarytool-password
          step "Notarization successful; stapling ticket to $fn"
          xcrun stapler staple "$fn"
        )
        ;;
      *)
        # Assume standalone binary; just sign and verify.
        step "Signing executable: $f"
        codesign --force --timestamp --options runtime \
          --entitlements "$basedir/configs/entitlements.plist" \
          --sign "$DEV_ID" "$f"
        step 'Verifying signature'
        codesign -vv "$f"
        ;;
    esac
  done
  step 'Signing complete!'
}

sign_windows() {
  test "$THUMBPRINT" ||
    die 'Please set THUMBPRINT environment variable; see doc/WINDOWS.md#code-signing'

  # Find the correct signtool.exe.
  arch=$(uname -m)
  case "$arch" in
    x86_64) arch=x64 ;;
  esac
  signtool=$(
    find '/c/Program Files'*'/Windows Kits' -name signtool.exe |
      grep "/$arch/" | head -n1
  )

  step 'Locating signtool.exe'
  test "$signtool" || die "signtool.exe not found"
  test -f "$signtool" || die "signtool.exe is not a file: $signtool"
  echo "$signtool"

  test "$TIMESTAMP_SERVER" || TIMESTAMP_SERVER='http://time.certum.pl/'

  step 'Signing binaries'

  # Note: The MSYS2_ARG_CONV_EXCL variable setting tells Git Bash not
  # to attempt any conversion on arguments containing the / character,
  # so that the signtool parameters are preserved correctly.

  MSYS2_ARG_CONV_EXCL=/ "$signtool" sign /sha1 "$THUMBPRINT" \
    /tr "$TIMESTAMP_SERVER" \
    /td SHA256 /fd SHA256 /v "$@"

  step 'Verifying signatures'

  MSYS2_ARG_CONV_EXCL=/ "$signtool" verify /pa /all "$@"

  step 'Signing complete!'
  echo 'Now consider zipping up the EXE files and submitting to the'
  echo 'Microsoft Windows Defender Security Intelligence portal at'
  echo 'https://www.microsoft.com/en-us/wdsi/filesubmission?persona=SoftwareDeveloper'
  echo '* security product = Microsoft Defender Smartscreen'
  echo '* "Incorrectly detected as malware/malicious"'
  echo '* Detection name = SmartScreen warning'
  echo '* Additional information = Signed binaries for MyApp: https://mycompany.com/myapp'
  echo 'Replacing "MyApp" and URL with the title and URL of your app.'
}

case "$(uname)" in
  Linux) sign_linux "$@" ;;
  Darwin) sign_macos "$@" ;;
  MINGW*|MSYS*) sign_windows "$@" ;;
  *) warn "Don't know how to sign binaries for platform: $(uname)" ;;
esac
