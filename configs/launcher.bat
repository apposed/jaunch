@echo off
setlocal EnableDelayedExpansion

rem # This batch file is a shortcut for launching the application
rem # without regard for the underlying architecture (x86-64 or arm64).
rem # It also forces a new console to be allocated if the parent process
rem # does not already have one, which is especially useful for
rem # executables compiled in GUI mode rather than console mode, so that:
rem #
rem # 1) the shell prompt blocks until the program completes; and
rem #
rem # 2) the console's standard input is all fed to the launched program,
rem #    rather than haphazardly split between it and the parent shell.
rem #
rem # As an alternative to using this batch file wrapper, GUI executables
rem # can be launched in blocking mode from an existing PowerShell via:
rem #
rem #     Start-Process -Wait .\launcher-windows-x64.exe
rem #
rem # or from an existing Command Prompt via:
rem #
rem #     start /wait launcher-windows-x64.exe
rem #
rem # Where `launcher-windows-x64.exe` is the GUI executable to run.

if "%PROCESSOR_ARCHITECTURE%" == "AMD64" (
    set "arch=x64"
) else if "%PROCESSOR_ARCHITECTURE%" == "ARM64" (
    set "arch=arm64"
) else (
    echo Unsupported CPU architecture: %PROCESSOR_ARCHITECTURE%
    exit /b 1
)

set "launcher=%~dp0%~n0-windows-!arch!.exe"

rem # Verify presence of executable, looking harder if missing.
if not exist "%launcher%" (
    rem # Executable not present; look for console or gui suffix.
    set "candidate=%~dp0%~n0-windows-!arch!-console.exe"
    if not exist "!candidate!" set "candidate=%~dp0%~n0-windows-!arch!-gui.exe"
    if exist "!candidate!" set "launcher=!candidate!"
    if not exist "%launcher%" (
        echo Launcher not available: %launcher%
        exit /b 1
    )
)

rem # Launch with the discovered executable.
@"%launcher%" --jaunch-skip-console-check %*
