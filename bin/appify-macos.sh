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
  mkdir -pv "$out_dir/$app_bindir" "$out_dir/$app_resources"

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
    step 'Macifying the icon'
    # Convert .svg to intermediate .png.
    # TODO: Add the macOS-aesthetic rounded rectangle frame.
    magick=
    if command -v magick >/dev/null; then
      magick=magick
    elif command -v convert >/dev/null; then
      magick=convert
    fi
    if [ "$magick" ]; then
      "$magick" -background none -density 384 "$app_icon" \
        -define png:format=png32 -resize 1024x1024 "$out_dir/temp.png"
    else
      warn 'Cannot convert icon to PNG temp file; please install ImageMagick.'
    fi
    # Now convert .png to .icns.
    if [ -f "$out_dir/temp.png" ]; then
      case "$(uname)" in
        Darwin)
          mkdir "$out_dir/icon.iconset"
          mv "$out_dir/temp.png" "$out_dir/icon.iconset/icon_512x512@2x.png"
          iconutil -c icns -o "$out_dir/$app_resources/$app_title.icns" "$out_dir/icon.iconset"
          rm -rf "$out_dir/icon.iconset"
          ;;
        *)
          if command -v png2icns >/dev/null; then
            png2icns "$out_dir/$app_resources/$app_title.icns" "$out_dir/temp.png"
          else
            warn 'Cannot convert icon to macOS ICNS format; please install png2icns.'
          fi
          rm "$out_dir/temp.png"
          ;;
      esac
    fi
  fi

  # Copy/populate Info.plist manifest into app bundle.
  step 'Copying Info.plist'
  plist_outfile="$out_dir/$app_contents/Info.plist"
  cp -v "$info_plist" "$plist_outfile"
  step 'Populating Info.plist'
  revise_file "$plist_outfile" "s;{{APP_EXE}};$app_exe-macos;"
  if [ "$app_icon" ]; then
    revise_file "$plist_outfile" "s;{{APP_ICON}};$app_title.icns;"
  else
    revise_file "$plist_outfile" 'd;{{APP_ICON}};'
  fi
  revise_file "$plist_outfile" "s;{{APP_IDENTIFIER}};$app_id;"
  revise_file "$plist_outfile" "s;{{APP_TITLE}};$app_title;"
  revise_file "$plist_outfile" "s;{{YEAR}};$(date +%Y);"
}

if find "$out_dir" -maxdepth 1 -name "$app_exe-macos*" -quit; then
  construct_app_bundle
else
  warn "No macOS binaries -- skipping construction of $app_title.app bundle"
fi
