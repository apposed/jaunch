#!/usr/bin/env bash

STEP_PREFIX='[MACOS] '
. "${0%/*}/common.include"

test -d "$distdir" || die 'No dist folder; please `make dist` first.'

# Parse arguments.
test $# -ge 5 || {
  echo 'Usage: appify-macos.sh out-dir Info.plist app-title app-exe app-id [app-icon]'
  echo 'Performs macOS-specific steps in application bundle assembly.'
  exit 1
}

out_dir="$1"
info_plist="$2"
app_title="$3"
app_exe="$4"
app_id="$5"
app_icon="$6"

# Validate arguments.
test -d "$out_dir" || die "Not a directory: $out_dir"
test -f "$info_plist" || die "Not a file: $info_plist"
test "$app_title" || die 'app-title must be given'
test "$app_exe" || die 'app-exe must be given'
test "$app_id" || die 'app-id must be given'
test -z "$app_icon" -o -f "$app_icon" || die "Not a file: $app_icon"

construct_app_bundle() {
  step "Constructing $app_title.app bundle"

  app_dir="$app_title.app"
  app_contents="$app_dir/Contents"
  app_bindir="$app_contents/MacOS"
  app_resources="$app_contents/Resources"
  mkdir -pv "$out_dir/$app_bindir"

  # Migrate launcher and configurator binaries into app bundle.
  find "$out_dir" -maxdepth 1 -name "$app_exe-macos*" -type f |
  while read exe; do
    mv -v "$exe" "$out_dir/$app_bindir"
    (cd "$out_dir" && ln -sfv "$app_bindir/$(basename "$exe")")
  done
  find "$out_dir/jaunch" -maxdepth 1 -name "jaunch-macos*" |
  while read cfg; do
    # Note: We do not move-and-symlink the Jaunch configurator binaries,
    # in case the appify script is going to be run more than once into
    # the same target directory for multiple coexistent applications.
    # If you don't like the extra jaunch-macos* binaries in the jaunch
    # folder, you can manually remove them after appifying everything.
    cp -v "$cfg" "$out_dir/$app_bindir"
  done

  # Copy icon into app bundle.
  if [ "$app_icon" ]; then
    step 'Embedding the icon'
    app_icon_png=
    png_is_temp_file=
    app_icon_icns=
    case "$app_icon" in
      *.png) app_icon_png="$app_icon" ;;
      *.icns) app_icon_icns="$app_icon" ;;
    esac
    if [ -z "$app_icon_icns" -a -z "$app_icon_png" ]; then
      # Convert non-ICNS-non-PNG image to a temporary PNG.
      magick=$(magick_command)
      if [ "$magick" ]; then
        app_icon_png="$out_dir/temp.png"
        png_is_temp_file=1
        (
          set -x
          "$magick" -background none -density 384 "$app_icon" \
            -define png:format=png32 -resize 1024x1024 "$app_icon_png"
        )
      else
        warn 'Cannot convert icon to PNG temp file; please install ImageMagick.'
        magick_install_help
      fi
    fi
    if [ -z "$app_icon_icns" -a -f "$app_icon_png" ]; then
      # Convert PNG image to ICNS.
      case "$(uname)" in
        Darwin)
          mkdir -pv "$out_dir/icon.iconset"
          cp -v "$app_icon_png" "$out_dir/icon.iconset/icon_512x512@2x.png"
          mkdir -pv "$out_dir/$app_resources"
          (set -x; iconutil -c icns -o "$out_dir/$app_resources/$app_title.icns" "$out_dir/icon.iconset")
          rm -rfv "$out_dir/icon.iconset"
          ;;
        *)
          if command -v png2icns >/dev/null; then
            mkdir -pv "$out_dir/$app_resources"
            (set -x; png2icns "$out_dir/$app_resources/$app_title.icns" "$app_icon_png")
          else
            warn 'Cannot convert icon to macOS ICNS format; please install png2icns.'
          fi
          ;;
      esac
      if [ "$png_is_temp_file" ]; then rm -v "$app_icon_png"; fi
    fi
    if [ "$app_icon_icns" ]; then
      # Copy ICNS image into place.
      mkdir -pv "$out_dir/$app_resources"
      cp -v "$app_icon_icns" "$out_dir/$app_resources/$app_title.icns"
    fi
  fi

  # Copy/populate Info.plist manifest into app bundle.
  step 'Copying Info.plist'
  plist_outfile="$out_dir/$app_contents/Info.plist"
  cp -v "$info_plist" "$plist_outfile"
  step 'Populating Info.plist'
  revise_file "$plist_outfile" "s/{{APP_EXE}}/$app_exe-macos/"
  if [ "$app_icon" ]; then
    revise_file "$plist_outfile" "s/{{APP_ICON}}/$app_title.icns/"
  else
    revise_file "$plist_outfile" '/CFBundleIconFile\|{{APP_ICON}}/d'
  fi
  revise_file "$plist_outfile" "s/{{APP_IDENTIFIER}}/$app_id/"
  revise_file "$plist_outfile" "s/{{APP_TITLE}}/$app_title/"

  # Use the Jaunch version and build hash for app version values.
  #
  # Note: If you want to override this, you are welcome to file a PR adding
  # support for --app-version and/or --app-build to the appify scripts.
  # Or you can just tweak it manually after the appify process completes.
  revise_file "$plist_outfile" "s/{{APP_VERSION}}/$version/"
  if [ "$gitHash" ]; then
    long_version="$version-$gitHash"
  else
    long_version="$version"
  fi
  revise_file "$plist_outfile" "s/{{APP_BUILD}}/$long_version/"

  revise_file "$plist_outfile" "s/{{YEAR}}/$(date +%Y)/"
}

if find "$out_dir" -maxdepth 1 -name "$app_exe-macos*" -quit; then
  construct_app_bundle
else
  warn "No macOS binaries -- skipping construction of $app_title.app bundle"
fi
