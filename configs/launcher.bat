@echo off

rem # This batch file is a shortcut for launching the application
rem # without regard for the underlying architecture (x86-64 or arm64).
rem # It also forces a new console to be allocated if the parent process
rem # does not already have one, whichis especially useful for executables
rem # compiled in GUI mode rather than console mode, so that:
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
rem # Or from an existing Command Prompt via:
rem #
rem #     start /wait launcher-windows-x64.exe
rem #
rem # Where `launcher-windows-x64.exe` is the GUI executable to run.

if "%PROCESSOR_ARCHITECTURE%" == "AMD64" (
    @"%~dp0%~n0-windows-x64.exe" %*
) else if "%PROCESSOR_ARCHITECTURE%" == "ARM64" (
    @"%~dp0%~n0-windows-arm64.exe" %*
) else (
    echo Unsupported CPU architecture: %PROCESSOR_ARCHITECTURE%
    exit /b 1
)
