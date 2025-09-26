#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
echo
echo -e "\033[1;33m[compile-launcher]\033[0m"

# Locate Java installation. We need this for the jni.h include,
# even though Java will be loaded via dlopen at runtime.
jdkdir=$JAVA_HOME
test -d "$jdkdir" || jdkdir=$(test ! -x /usr/sbin/update-java-alternatives || /usr/sbin/update-java-alternatives -l | head -n1 | sed 's/.* //') || true
test -d "$jdkdir" || jdkdir=$(test ! -x /usr/libexec/java_home || /usr/libexec/java_home) || true
test -d "$jdkdir" || {
  echo '[ERROR] The jni.h header is needed to compile Jaunch.'
  echo '[ERROR] Please set JAVA_HOME to point to an OpenJDK installation.'
  exit 1
}

mkdir -p build

# -----===== FUNCTIONS =====-----

find-llvm-mingw() {
  find .cache/llvm-mingw -maxdepth 1 -type d 2>/dev/null | sort -r | head -n1
}

configure-llvm-mingw() {
  cdir=$(find-llvm-mingw)
  if [ "$cdir" ]
  then
    echo "$cdir"
  else
    v=20250613
    platform="$(uname -s):$(uname -m)"
    case "$platform" in
      Linux:aarch64|Linux:arm64)
        archive=llvm-mingw-$v-ucrt-ubuntu-22.04-aarch64.tar.xz
        sha=60c6135aeb90e115f9b9e58d61375fff51a4b8d4a2b66352fef0ce47bcc24dc3
        ;;
      Linux:x86_64|Linux:amd64)
        archive=llvm-mingw-$v-ucrt-ubuntu-22.04-x86_64.tar.xz
        sha=936f82221fa4ad4ff1829f28f1cdf4c1e304cfd589323212e2b7ef8be428784a
        ;;
      Darwin:*)
        archive=llvm-mingw-$v-ucrt-macos-universal.tar.xz
        sha=25f93d2ab2d75903a282cf2ec620caa175afbfc19f705767224d124892fecc76
        ;;
      MINGW*:aarch64|MSYS*:aarch64|MINGW*:arm64|MSYS*:arm64)
        archive=llvm-mingw-$v-ucrt-aarch64.zip
        sha=b1dcfe18854bdf5e719064df35417bf4e485e1973996c0acc4e1ec193d09de53
        ;;
      MINGW*:x86_64|MSYS*:x86_64|MINGW*:amd64|MSYS*:amd64)
        archive=llvm-mingw-$v-ucrt-x86_64.zip
        sha=45145c035d9246e1de16f1873aa9afa863d93909f4a8f363e2eb38a04031d3c3
        ;;
      MINGW*:armv7|MSYS*:armv7)
        archive=llvm-mingw-$v-ucrt-armv7.zip
        sha=sha256:7ed9b24d4b3304752370ff76fec8e3ff51a57b4788bd82fb32fcbf9f9aab256b
        ;;
      MINGW*:i686|MSYS*:i686)
        archive=llvm-mingw-$v-ucrt-i686.zip
        sha=4ab5fb78880f3321c801162da91f1c3cb894b0537735db342b9d38ade1a370d0
        ;;
      *)
        echo "[WARNING] No cross-compilation to Windows for platform $platform"
        return
        ;;
    esac

    mkdir -p .cache/llvm-mingw &&
    (
      cd .cache/llvm-mingw &&
      set -x &&
      curl -fLO "https://github.com/mstorsjo/llvm-mingw/releases/download/$v/$archive" &&
      verify-checksum "$archive" "$sha" &&
      case "$archive" in
        *.tar|*.tar.*) tar xf "$archive" ;;
        *.zip) unzip "$archive" ;;
      esac
    ) &&
    echo "$(find-llvm-mingw)"
  fi
}

verify-checksum() {
  file=$1
  sha=$2
  test "$(sha256sum "$file" | sed 's/ .*//')" = "$sha" ||
    echo "[WARNING] Mismatched checksum: $file"
}

compile() {
  compiler=$1
  command -v "$compiler" >/dev/null 2>&1 || {
    echo "[WARNING] No compiler installed: $compiler"
    echo "[WARNING] Skipping invocation: $@"
    return
  }
  shift
  (
    set -x
    "$compiler" \
      -I"$jdkdir/include" \
      -I"$jdkdir/include/linux" \
      -I"$jdkdir/include/darwin" \
      -I"$jdkdir/include/win32" \
      -fPIC -fno-stack-protector \
      src/c/jaunch.c $@
  ) || {
    echo '[ERROR] Compilation failed.'
    exit 1
  }
}

# -----===== MAIN PLATFORM COMPILATION =====-----

case "$(uname -s)" in

  Linux)
    # Compile Linux targets.
    #
    # Note: Ubuntu 25.04 has these gcc-<target>-linux-gcc packages:
    #
    #   aarch64, alpha, arc, arm (eabi), arm (eabihf), hppa, i686,
    #   loongarch64, m68k, mips, mipsel, mipsisa32r6, mipsisa32r6el,
    #   multilib-i686, multilib-mips, multilib-mipsel,
    #   multilib-mipsisa32r6, multilib-mipsisa32r6el,
    #   multilib-powerpc, multilib-sparc64, multilib-x86-64,
    #   powerpc, powerpc64le, riscv64, s390x, sh4, sparc64, x86-64
    #
    # In particular, Jaunch could compile its native launcher
    # for 32-bit x86 and arm platforms using these tools.
    # But for now, we compile only for 64-bit platforms.
    gcc_arm64=aarch64-linux-gnu-gcc
    gcc_x64=x86_64-linux-gnu-gcc
    gcc_other=
    case "$(uname -m)" in
      aarch64|arm64) gcc_arm64=gcc ;;
      x86_64|amd64) gcc_x64=gcc ;;
    esac
    compile "$gcc_arm64" -o build/launcher-linux-arm64 &&
    compile "$gcc_x64" -o build/launcher-linux-x64
    ;;

  Darwin)
    # Compile macOS targets.
    compile gcc -o build/launcher-macos-arm64 -framework CoreFoundation -framework AppKit -target arm64-apple-macos11 &&
    compile gcc -o build/launcher-macos-x64 -framework CoreFoundation -framework AppKit -target x86_64-apple-macos10.12 &&
    (set -x; lipo -create -output build/launcher-macos build/launcher-macos-x64 build/launcher-macos-arm64)

    # Note: Homebrew has these <target>-elf-gcc packages:
    #
    #   aarch64, i686, m68k, riscv64, x86_64
    #
    # Jaunch could potentially cross-compile for Linux targets
    # on macOS systems using these tools. But for now, nah.
    ;;

  MINGW*|MSYS*)
    # Compile Windows targets (current arch only).
    arch=$(uname -m)
    if [ "$arch" = "x86_64" ]; then arch=x64; fi
    compile gcc -o "build/launcher-windows-$arch-console.exe" -lpthread -mconsole
    compile gcc -o "build/launcher-windows-$arch-gui.exe" -lpthread -mwindows
    ;;

  *)
    # Unknown architecture; just try it.
    compile gcc -o build/launcher
    ;;
esac

# -----===== WINDOWS CROSS-COMPILATION =====-----

# Cross-compile Windows targets (thanks, llvm-mingw!).
cdir=$(configure-llvm-mingw)
if [ -d "$cdir" ]
then
  # windows-arm64 (console and GUI builds)
  compile "$cdir/bin/aarch64-w64-mingw32-clang" \
    -o build/launcher-windows-arm64-console.exe -lpthread -mconsole &&
  compile "$cdir/bin/aarch64-w64-mingw32-clang" \
    -o build/launcher-windows-arm64-gui.exe -lpthread -mwindows &&
  # windows-x64 (console and GUI builds)
  compile "$cdir/bin/x86_64-w64-mingw32-clang" \
    -o build/launcher-windows-x64-console.exe -lpthread -mconsole &&
  compile "$cdir/bin/x86_64-w64-mingw32-clang" \
    -o build/launcher-windows-x64-gui.exe -lpthread -mwindows
else
  echo '[WARNING] Failed to set up llvm-mingw; skipping Windows cross-compilation'
fi
