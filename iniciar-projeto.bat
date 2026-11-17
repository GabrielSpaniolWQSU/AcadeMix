@echo off
setlocal

set "ROOT=%~dp0"
set "BACKEND=%ROOT%backend"
set "FRONTEND=%ROOT%frontend"
set "MAVEN_REPO=%ROOT%.m2\repository"
set "MAVEN_VERSION=3.9.16"
set "MAVEN_HOME=%ROOT%.tools\apache-maven-%MAVEN_VERSION%"
set "MAVEN_CMD=mvn"

where java >nul 2>&1 || (
  echo [ERRO] Java nao foi encontrado no PATH.
  pause
  exit /b 1
)

where npm >nul 2>&1 || (
  echo [ERRO] Node.js/NPM nao foi encontrado no PATH.
  pause
  exit /b 1
)

if not exist "%BACKEND%\pom.xml" (
  echo [ERRO] Nao foi encontrado: %BACKEND%\pom.xml
  pause
  exit /b 1
)

if not exist "%FRONTEND%\package.json" (
  echo [ERRO] Nao foi encontrado: %FRONTEND%\package.json
  pause
  exit /b 1
)

where mvn >nul 2>&1 || (
  set "MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
  if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    call :baixar_maven
    if errorlevel 1 exit /b 1
  )
)

echo Iniciando backend e frontend...
start "Backend - Estagio" /D "%BACKEND%" cmd /k powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%iniciar-backend.ps1"
start "Frontend - Estagio" /D "%FRONTEND%" cmd /k npm start

echo.
echo Projeto iniciado em duas janelas.
echo Feche as janelas do Backend e Frontend para encerrar os servidores.
timeout /t 3 >nul
exit /b 0

:baixar_maven
where powershell >nul 2>&1 || (
  echo [ERRO] PowerShell nao foi encontrado para baixar o Maven.
  pause
  exit /b 1
)

echo Maven nao encontrado. Baixando Maven %MAVEN_VERSION% para o projeto...
if not exist "%ROOT%.tools" mkdir "%ROOT%.tools"
set "MAVEN_ZIP=%ROOT%.tools\apache-maven-%MAVEN_VERSION%-bin.zip"
set "MAVEN_URL=https://dlcdn.apache.org/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue';" ^
  "Invoke-WebRequest '%MAVEN_URL%' -OutFile '%MAVEN_ZIP%';" ^
  "$expected=((Invoke-WebRequest '%MAVEN_URL%.sha512').Content -split '\s+')[0].Trim().ToLower();" ^
  "$actual=(Get-FileHash '%MAVEN_ZIP%' -Algorithm SHA512).Hash.ToLower();" ^
  "if ($actual -ne $expected) { throw 'Falha na verificacao SHA-512 do Maven.' };" ^
  "Expand-Archive -LiteralPath '%MAVEN_ZIP%' -DestinationPath '%ROOT%.tools' -Force"

if errorlevel 1 (
  echo [ERRO] Nao foi possivel baixar ou validar o Maven.
  pause
  exit /b 1
)

del /q "%MAVEN_ZIP%" >nul 2>&1
echo Maven preparado com sucesso.
exit /b 0
