@echo off
if "%PROCESSOR_ARCHITECTURE%" == "AMD64" (
    @"%~dp0jy-windows-x64.exe" %*
) else if "%PROCESSOR_ARCHITECTURE%" == "ARM64" (
    @"%~dp0jy-windows-arm64.exe" %*
) else (
    echo Unsupported CPU architecture: %PROCESSOR_ARCHITECTURE%
    exit /b 1
)
