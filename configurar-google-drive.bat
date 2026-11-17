@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0iniciar-backend.ps1" -ConfigurarGoogleDrive
set "RESULTADO=%ERRORLEVEL%"
pause
exit /b %RESULTADO%
