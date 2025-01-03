#!/bin/sh

# This shell script is a shortcut for launching the application
# without regard for the underlying operating system (Linux, macOS,
# or Windows via Git Bash) or architecture (x86-64 or arm64).

dir=$(dirname "$0")
name=$(basename "$0")
name=${name%.sh}

die() { echo "$*" >&2; exit 1; }

# Glean operating system.
os=$(uname -s 2>/dev/null)
case "$os" in
  Linux) os=linux; exedir=. ;;
  Darwin) os=macos; exedir=Contents/MacOS ;;
  MINGW*|MSYS*) os=windows; exedir=. ;;
  *) die "Unsupported operating system: $os" ;;
esac

# Glean CPU architecture.
arch=$(uname -m 2>/dev/null)
case "$arch" in
  arm64) arch=arm64 ;;
  x86_64) arch=x64 ;;
  *) die "Unsupported CPU architecture: $arch" ;;
esac

# Launch with the appropriate executable.
launcher="$dir/$exedir/$name-$os-$arch"
test -e "$launcher" || die "Launcher not available: $launcher"
test -x "$launcher" || die "Launcher not executable: $launcher"
"$launcher" $@
