param([switch]$Configurar)
$ErrorActionPreference='Stop'
$raizBanco=Join-Path $PSScriptRoot '.tools\mysql-8.4.13-winx64'
$pastaDados=Join-Path $PSScriptRoot '.tools\mysql-dados'
$config=Join-Path $PSScriptRoot '.tools\mysql-projeto.ini'
$credenciais=Join-Path $PSScriptRoot '.mysql-credenciais.xml'
$portaBanco=3307
if(!(Test-Path -LiteralPath (Join-Path $raizBanco 'bin\mysqld.exe'))){
    throw 'MySQL local nao encontrado. Prepare a distribuicao oficial em .tools/mysql-8.4.13-winx64.'
}
if(!(Test-Path -LiteralPath $credenciais)){
    if(!$Configurar){throw 'Execute configurar-banco.bat uma vez para preparar o MySQL.'}
    if(Test-Path -LiteralPath $pastaDados){throw 'Existe uma pasta de dados sem credenciais. Confira a instalacao antes de tentar novamente.'}
    $senhaApp=[Guid]::NewGuid().ToString('N')+[Guid]::NewGuid().ToString('N')
    $senhaRoot=[Guid]::NewGuid().ToString('N')+[Guid]::NewGuid().ToString('N')
    $segredoApp=ConvertTo-SecureString $senhaApp -AsPlainText -Force
    $segredoRoot=ConvertTo-SecureString $senhaRoot -AsPlainText -Force
    $contas=[PSCustomObject]@{
        App=[PSCredential]::new('gestao_app',$segredoApp)
        Root=[PSCredential]::new('root',$segredoRoot)
    }
    $contas | Export-Clixml -LiteralPath $credenciais
}
$contas=Import-Clixml -LiteralPath $credenciais
$executavel=Join-Path $raizBanco 'bin\mysqld.exe'
$cliente=Join-Path $raizBanco 'bin\mysql.exe'
$init=Join-Path $PSScriptRoot '.tools\mysql-inicializar.sql'
if(!(Test-Path -LiteralPath (Join-Path $pastaDados 'mysql'))){
    & $executavel --no-defaults --initialize-insecure "--basedir=$raizBanco" "--datadir=$pastaDados" --console
    if($LASTEXITCODE -ne 0){throw 'Nao foi possivel inicializar o MySQL.'}
}
if(!(Test-Path -LiteralPath (Join-Path $pastaDados 'projeto-configurado'))){
    $senhaApp=$contas.App.GetNetworkCredential().Password
    $senhaRoot=$contas.Root.GetNetworkCredential().Password
    @"
ALTER USER 'root'@'localhost' IDENTIFIED BY '$senhaRoot';
CREATE DATABASE IF NOT EXISTS gestao_academica CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'gestao_app'@'127.0.0.1' IDENTIFIED BY '$senhaApp';
GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES ON gestao_academica.* TO 'gestao_app'@'127.0.0.1';
"@ | Set-Content -LiteralPath $init -Encoding ascii
}
$baseConfig=@"
[mysqld]
basedir=$($raizBanco.Replace('\','/'))
datadir=$($pastaDados.Replace('\','/'))
port=$portaBanco
bind-address=127.0.0.1
mysqlx=0
skip-name-resolve
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
"@
if(Test-Path -LiteralPath $init){$baseConfig += "`ninit-file=$($init.Replace('\','/'))`n"}
[IO.File]::WriteAllText($config,$baseConfig,[Text.UTF8Encoding]::new($false))
$env:MYSQL_PWD=$contas.App.GetNetworkCredential().Password
function Testar-BancoProjeto {
    $inicio=[Diagnostics.ProcessStartInfo]::new()
    $inicio.FileName=$cliente
    $inicio.Arguments='--no-defaults --protocol=TCP --host=127.0.0.1 --port=3307 --user=gestao_app --connect-timeout=1 --execute="SELECT 1"'
    $inicio.UseShellExecute=$false
    $inicio.CreateNoWindow=$true
    $inicio.RedirectStandardError=$true
    $inicio.RedirectStandardOutput=$true
    $processo=[Diagnostics.Process]::Start($inicio)
    try {
        if(!$processo.WaitForExit(3000)){$processo.Kill();return $false}
        return $processo.ExitCode -eq 0
    } finally {$processo.Dispose()}
}
try{
    if(!(Testar-BancoProjeto)){
        $escuta=Get-NetTCPConnection -LocalPort $portaBanco -State Listen -ErrorAction SilentlyContinue
        if($escuta){throw 'A porta 3307 esta ocupada e nao aceitou as credenciais do banco do projeto.'}
        Start-Process -FilePath $executavel -ArgumentList ('--defaults-file="'+$config+'"') -WindowStyle Hidden
        $pronto=$false
        for($tentativa=0;$tentativa -lt 30;$tentativa++){
            Start-Sleep -Milliseconds 500
            if(Testar-BancoProjeto){$pronto=$true;break}
        }
        if(!$pronto){throw 'O MySQL nao iniciou. Confira o arquivo de erro em .tools/mysql-dados.'}
    }
    [IO.File]::WriteAllText((Join-Path $pastaDados 'projeto-configurado'),'ok')
    if(Test-Path -LiteralPath $init){Remove-Item -LiteralPath $init}
    [IO.File]::WriteAllText($config,($baseConfig -replace '(?m)^init-file=.*\r?\n?',''),[Text.UTF8Encoding]::new($false))
    $env:SPRING_PROFILES_ACTIVE='mysql'
    $env:DB_URL="jdbc:mysql://127.0.0.1:$portaBanco/gestao_academica?connectionTimeZone=America/Sao_Paulo"
    $env:DB_USER=$contas.App.UserName
    $env:DB_PASSWORD=$contas.App.GetNetworkCredential().Password
    Write-Host '[OK] MySQL pronto. As tabelas serao criadas ao iniciar o backend.'
}finally{
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    if(Test-Path -LiteralPath $init){Remove-Item -LiteralPath $init}
}
