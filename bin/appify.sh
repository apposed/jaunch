#!/usr/bin/env bash

STEP_PREFIX=
. "${0%/*}/common.include"

# Parse arguments.

test $# -gt 0 || usage=1
while [ $# -gt 0 ]; do
  case "$1" in
    --app-exe) app_exe="$2"; shift 2;;
    --app-icon) app_icon="$2"; shift 2;;
    --app-id) app_id="$2"; shift 2;;
    --app-title) app_title="$2"; shift 2;;
    --info-plist) info_plist="$2"; shift 2;;
    --jaunch-toml) jaunch_toml="$2"; shift 2;;
    --out-dir) out_dir="$2"; shift 2;;
    --help) usage=1; shift;;
    *) die "Unknown argument: $1";;
  esac
done

if [ "$usage" ]; then
  echo 'Usage: appify.sh [OPTION]...'
  echo 'Assembles resources into a platform-specific application bundle.'
  echo
  echo '  --app-exe EXE       application executable name (required; typically low case)'
  echo '  --app-icon ICON     path to an icon to embed into the app (.svg is best)'
  echo '  --app-id ID         application identifier (macOS only; e.g. org.example.myapp)'
  echo '  --app-title TITLE   title of the application (required; typically title case)'
  echo '  --info-plist PLIST  path to Info.plist manifest (macOS only)'
  echo '  --jaunch-toml TOML  path to Jaunch TOML configuration file (required)'
  echo '  --out-dir DIR       directory where the app bundle will be written (required)'
  exit 0
fi

# Validate arguments.

test "$out_dir" || die '--out-dir is required'
test "$app_title" || die '--app-title is required'
test "$app_exe" || die '--app-exe is required'
test "$jaunch_toml" || die '--jaunch-toml is required'
test -d "$out_dir" || die "Not a directory: $out_dir"
test -f "$jaunch_toml" || die "Not a file: $jaunch_toml"
test -z "$app_icon" -o -f "$app_icon" || die "Not a file: $app_icon"
test -f "$info_plist" || info_plist="$basedir/configs/Info.plist"

# Copy files.

step 'Copying launcher binaries and scripts'
for exe in "$distdir"/launcher*; do
  exe_name=${exe##*/}
  exe_outfile="$out_dir/$app_exe${exe_name#launcher}"
  cp -pv "$exe" "$exe_outfile"
done

step 'Copying configurator binaries'
cfg_outdir="$out_dir/jaunch"
mkdir -pv "$cfg_outdir"
cp -pv "$distdir"/jaunch/jaunch* "$cfg_outdir/"

step 'Copying configuration files'
copy_toml() {
  # Copy the given TOML file to the specified directory.
  n=${1##*/}
  test -f "$1" -a ! -e "$2/$n" || return 0 # Already copied.
  cp -pv "$1" "$2/"

  # Copy dependencies from the TOML's `includes` section.
  # The following invocation hacks out the `includes` section of the
  # TOML file, splitting the comma-separated list and stripping quotes.
  # It works across newlines and with either kind of quoting (single
  # or double). It assumes entries do not contain quotes or spaces.
  grep -A99999 'includes *=' "$1" |
    grep -B99999 ']' --max-count=1 |
    tr \'\" '\n' |
    grep -v '^includes *=' |
    grep -v '^ *\(,\|]\) *$' |
  while read dep; do
    # Look for TOML dependency in same directory.
    depfile="${1%/*}/$dep"
    # Failing that, look in the dist/jaunch directory.
    test -f "$depfile" || depfile="$distdir/jaunch/$dep"
    copy_toml "$depfile" "$2"
  done
}
copy_toml "$jaunch_toml" "$cfg_outdir"
cp -pv "$distdir"/jaunch/Props.class "$cfg_outdir/"

# Perform platform-specific actions.
"$script_dir/appify-linux.sh" "$out_dir" "$app_title" "$app_exe" "$app_icon"
"$script_dir/appify-macos.sh" "$out_dir" "$info_plist" "$app_title" "$app_exe" "$app_id" "$app_icon"
"$script_dir/appify-windows.sh" "$out_dir" "$app_exe" "$app_icon"

step 'Complete!'

# Display the result.
echo ================================================================
find "$out_dir" -type f
