#!/bin/sh

# This shell script is a shortcut for launching the application
# without regard for the underlying operating system (Linux, macOS,
# or Windows via Git Bash) or architecture (x86-64 or arm64).

dir=$(dirname "$0")
name=$(basename "$0")
name=${name%.sh}

die() { echo "$*" >&2; exit 1; }

# Glean CPU architecture.
arch=$(uname -m 2>/dev/null)
case "$arch" in
  arm64) arch=arm64 ;;
  x86_64) arch=x64 ;;
  *) die "Unsupported CPU architecture: $arch" ;;
esac

# Glean operating system.
os=$(uname -s 2>/dev/null)
case "$os" in
  Linux) launcher="$dir/$name-linux-$arch" ;;
  Darwin) launcher="$dir/$name-macos-$arch" ;;
  MINGW*|MSYS*) launcher="$dir/$name-windows-$arch" ;;
  *) die "Unsupported operating system: $os" ;;
esac

# Launch with the appropriate executable.
test -e "$launcher" || {
  # On macOS, try harder to locate the appropriate executable.
  case "$os" in
    Darwin)
      # Toplevel launcher or symlink not present; look in .app bundles.
      for macAppDir in "$dir"/*.app; do
        candidate="$macAppDir"/Contents/MacOS/"$name-macos-$arch"
        if [ -e "$candidate" ]; then
          launcher="$candidate"
          break
        fi
      done
      ;;
  esac
  test -e "$launcher" || die "Launcher not available: $launcher"
}
"$launcher" "$@"
