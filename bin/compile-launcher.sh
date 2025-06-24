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

find-llvm-mingw() {
  find .cache/llvm-mingw -maxdepth 1 -type d 2>/dev/null | sort -r | head -n1
}

configure-llvm-mingw() {
  cdir=$(find-llvm-mingw)
  if [ "$cdir" ]
  then
    echo "$cdir"
  else
    v=20241217
    n=llvm-mingw-$v-msvcrt-ubuntu-20.04-x86_64
    mkdir -p .cache/llvm-mingw &&
    (
      cd .cache/llvm-mingw &&
      set -x &&
      curl -fLO https://github.com/mstorsjo/llvm-mingw/releases/download/$v/$n.tar.xz &&
      tar xf $n.tar.xz
    ) &&
    echo "$(find-llvm-mingw)"
  fi
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
    compile gcc -o "build/launcher-windows-$arch-console.exe" -mconsole
    compile gcc -o "build/launcher-windows-$arch-gui.exe" -mwindows
    ;;
  *)
    # Unknown architecture; just try it.
    compile gcc -o build/launcher
    ;;
esac
