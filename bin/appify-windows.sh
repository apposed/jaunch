#!/usr/bin/env bash

STEP_PREFIX='[WINDOWS] '
. "${0%/*}/common.include"

test -d "$distdir" || die 'No dist folder; please `make dist` first.'

# Parse arguments.
test $# -ge 3 || {
  echo 'Usage: appify-windows.sh out-dir app-exe [app-icon]'
  echo 'Performs Windows-specific steps in application bundle assembly.'
  exit 1
}

out_dir="$1"
app_exe="$2"
app_icon="$3"

# Validate arguments.
test -d "$out_dir" || die "Not a directory: $out_dir"
test -z "$app_icon" -o -f "$app_icon" || die "Not a file: $app_icon"

if [ "$app_icon" ]; then
  step 'Embedding the icon'
  app_icon_ico=
  case "$app_icon" in
    *.ico) app_icon_ico="$app_icon" ;;
  esac
  icon_outpath="$out_dir/$app_exe.ico"
  if [ "$app_icon_ico" ]; then
    # Copy ICO image into place.
    cp -v "$app_icon_ico" "$icon_outpath"
  else
    # Convert non-ICO image to ICO format.
    magick=$(magick_command)
    if [ "$magick" ]; then
      # Convert icon to Windows ICO format using ImageMagick.
      "$magick" -background none -density 384 "$app_icon" \
        -define icon:auto-resize=256,48,32,16 "$icon_outpath"
    else
      warn 'Cannot convert icon to Windows ICO format; please install ImageMagick.'
      magick_install_help
    fi
  fi

  if [ -f "$icon_outpath" ]; then
    # Download rcedit.exe as needed.
    cachedir="$basedir/.cache"
    rcedit_filename=rcedit-x64.exe
    rcedit="$cachedir/$rcedit_filename"
    if [ ! -f "$rcedit" ]; then
      mkdir -pv "$cachedir"
      cd "$cachedir"
      curl -fLO "https://github.com/electron/rcedit/releases/download/v2.0.0/$rcedit_filename" ||
        die "Failed to download $rcedit_filename for icon embedding."
      cd - 2>/dev/null
    fi

    # Embed the icon into relevant EXE files.
    case "$(uname)" in
      MINGW*|MSYS*) wine=;; # Windows OS can run rcedit.exe directly.
      *) wine=1;; # Non-Windows OS must use wine.
    esac

    find "$out_dir" -maxdepth 1 -name "$app_exe-*.exe" |
    while read exe; do
      if [ "$wine" ]; then
        if command -v wine >/dev/null; then
          (set -x; wine "$rcedit" "$exe" --set-icon "$icon_outpath")
        else
          warn "Cannot embed icon into '$exe'; please install wine."
        fi
      else
        (set -x; "$rcedit" "$exe" --set-icon "$icon_outpath")
      fi
    done
  fi
fi
