#!/bin/sh
set -e
cd "$(dirname "$0")/.."

# Copy native launcher executable.
mkdir -p app
if [ -f build/launcher ]
then
  (set -x; cp build/launcher app/jy && chmod +x app/jy)
fi
if [ -f build/launcher.exe ]
then
  (set -x; cp build/launcher.exe app/jy.exe)
fi

# Copy jaunch configurator executables.
posixJaunchBinaryPath=build/bin/posix/debugExecutable/jaunch.kexe
posixJaunchBinaryType=$(file -b "$posixJaunchBinaryPath" 2>/dev/null)
case "$posixJaunchBinaryType" in
  ELF*)
    mkdir -p app/jaunch
    (set -x; cp "$posixJaunchBinaryPath" app/jaunch/jaunch)
    ;;
  Mach-O*)
    mkdir -p app/Contents/MacOS
    (set -x; cp "$posixJaunchBinaryPath" app/Contents/MacOS/jaunch)
    ;;
esac

windowsJaunchBinaryPath=build/bin/windows/debugExecutable/jaunch.exe
if [ -f "$windowsJaunchBinaryPath" ]
then
  (set -x; cp "$windowsJaunchBinaryPath" app/jaunch/jaunch.exe)
fi

# Copy TOML configuration files.
if [ ! -f app/jaunch/jaunch.toml ]
then
  (set -x; cp jaunch.toml app/jaunch/)
fi

# Copy Props.class helper program.
if [ ! -f app/jaunch/Props.class ]
then
  (set -x; cp Props.class app/jaunch/)
fi

# Install needed JAR files.
if [ ! -f app/lib/jython-2.7.3.jar ]
then
  mkdir -p app/lib
  (set -x; curl -fsL https://search.maven.org/remotecontent\?filepath\=org/python/jython/2.7.3/jython-2.7.3.jar > app/lib/jython-2.7.3.jar)
fi
