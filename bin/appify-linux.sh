#!/usr/bin/env bash

STEP_PREFIX='[LINUX] '
. "${0%/*}/common.include"

test -d "$distdir" || die 'No dist folder; please `make dist` first.'

# Parse arguments.
test $# -ge 3 || {
  echo 'Usage: appify-linux.sh out-dir app-title app-exe [app-icon]'
  echo 'Performs Linux-specific steps in application bundle assembly.'
  exit 1
}

out_dir="$1"
app_title="$2"
app_exe="$3"
app_icon="$4"

# Validate arguments.
test -d "$out_dir" || die "Not a directory: $out_dir"
test -z "$app_icon" -o -f "$app_icon" || die "Not a file: $app_icon"

if [ "$app_icon" ]; then
  step 'Copying icon'
  # This script is opinionated and names the icon to match the launcher.
  icon_suffix=${app_icon#${app_icon%.*}}
  icon_outname="$app_exe$icon_suffix"
  icon_outfile="$out_dir/$icon_outname"
  cp -pv "$app_icon" "$icon_outfile"
fi

step 'Creating desktop shortcut'
desktop_outfile="$out_dir/$app_exe.desktop"
cp -v "$cfgdir/launcher.desktop" "$desktop_outfile"
desktop_tmpfile="$desktop_outfile.tmp"
revise_file "$desktop_outfile" "s;{{APP_EXE}};$app_exe;"
if [ "$app_icon" ]; then
  revise_file "$desktop_outfile" "s;{{APP_ICON}};$icon_outname;"
else
  revise_file "$desktop_outfile" 'd;{{APP_ICON}};'
fi
revise_file "$desktop_outfile" "s;{{APP_IDENTIFIER}};$app_id;"
revise_file "$desktop_outfile" "s;{{APP_TITLE}};$app_title;"
revise_file "$desktop_outfile" "s;{{YEAR}};$(date +%Y);"
echo "$desktop_outfile"
