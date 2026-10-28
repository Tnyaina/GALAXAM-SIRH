@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0reset-database.ps1" -Mode prod
exit /b %ERRORLEVEL%
