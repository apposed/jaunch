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
  arm64|aarch64) arch=arm64 ;;
  x86_64|amd64) arch=x64 ;;
  *) die "Unsupported CPU architecture: $arch" ;;
esac

# Glean operating system.
os=$(uname -s 2>/dev/null)
case "$os" in
  Linux) launcher="$dir/$name-linux-$arch" ;;
  Darwin) launcher="$dir/$name-macos-$arch" ;;
  MINGW*|MSYS*) launcher="$dir/$name-windows-$arch.exe" ;;
  *) die "Unsupported operating system: $os" ;;
esac

# Verify presence of executable, looking harder if missing.
test -e "$launcher" || {
  # Executable not present; look in more platform-specific places.
  case "$os" in
    Darwin)
      # macOS: Look within app bundles.
      for macAppDir in "$dir"/*.app; do
        candidate="$macAppDir"/Contents/MacOS/"$name-macos-$arch"
        if [ -e "$candidate" ]; then
          launcher="$candidate"
          break
        fi
      done
      ;;
    MINGW*|MSYS*)
      # Windows: Look for console or gui suffix.
      candidate="$name-windows-$arch-console.exe"
      test -e "$candidate" || candidate="$name-windows-$arch-gui.exe"
      if [ -e "$candidate" ]; then launcher="$candidate"; fi
      ;;
  esac
  test -e "$launcher" || die "Launcher not available: $launcher"
}

# Launch with the discovered executable.
"$launcher" "$@"
