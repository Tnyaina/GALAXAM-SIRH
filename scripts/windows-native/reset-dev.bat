@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0reset-database.ps1" -Mode dev
exit /b %ERRORLEVEL%
