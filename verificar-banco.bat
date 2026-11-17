@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0acessar-banco.ps1" -Verificar
pause
