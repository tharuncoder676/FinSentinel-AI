@echo off
setlocal
cd /d "%~dp0"
echo ============================================================================
echo   FinSentinel AI - One-Click Dependency Bootstrap and Startup
echo ============================================================================
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\bootstrap-and-start.ps1" %*
set EXIT_CODE=%ERRORLEVEL%
echo.
if not "%EXIT_CODE%"=="0" (
    echo [ERROR] FinSentinel startup encountered an error ^(exit code %EXIT_CODE%^).
    pause
    exit /b %EXIT_CODE%
)
echo FinSentinel AI is running! Close this window or run STOP_FINSENTINEL.bat when done.
pause
