@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0reinstall-grh-fresh.ps1"
set "result=%ERRORLEVEL%"
echo.
if not "%result%"=="0" (
    echo Reinstallation GRH echouee. Code : %result%
) else (
    echo Reinstallation GRH terminee.
)
pause
exit /b %result%
