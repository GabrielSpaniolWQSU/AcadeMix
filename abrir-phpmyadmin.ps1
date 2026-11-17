param([switch]$SemAbrir)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'iniciar-banco.ps1')
$php=Join-Path $PSScriptRoot '.tools\php\php.exe'
$phpIni=Join-Path $PSScriptRoot '.tools\php\php.ini'
$site=Join-Path $PSScriptRoot '.tools\phpMyAdmin-5.2.3-all-languages'
$url='http://127.0.0.1:8090/'
if(!(Test-Path -LiteralPath $php) -or !(Test-Path -LiteralPath (Join-Path $site 'index.php'))){
    throw 'PHP ou phpMyAdmin nao encontrado em .tools.'
}
$temporarios=Join-Path $PSScriptRoot '.tools\phpmyadmin-temp'
[void][IO.Directory]::CreateDirectory($temporarios)
$pastas=@('sessoes','uploads','cache')
foreach($nome in $pastas){[void][IO.Directory]::CreateDirectory((Join-Path $temporarios $nome))}
$extensoes=(Join-Path $PSScriptRoot '.tools\php\ext').Replace('\','/')
$tempUnix=$temporarios.Replace('\','/')
$configPhp=@"
extension_dir="$extensoes"
extension=mysqli
extension=mbstring
extension=openssl
extension=curl
extension=zip
extension=fileinfo
date.timezone=America/Sao_Paulo
session.save_path="$tempUnix/sessoes"
upload_tmp_dir="$tempUnix/uploads"
upload_max_filesize=100M
post_max_size=101M
memory_limit=256M
max_execution_time=120
display_errors=Off
log_errors=On
error_log="$tempUnix/php-errors.log"
expose_php=Off
"@
[IO.File]::WriteAllText($phpIni,$configPhp,[Text.UTF8Encoding]::new($false))
$configSite=@'
<?php
$cfg['blowfish_secret'] = getenv('GESTAO_PMA_SECRET');
$cfg['DefaultLang'] = 'pt_BR';
$cfg['ServerDefault'] = 1;
$cfg['Servers'][1]['auth_type'] = 'config';
$cfg['Servers'][1]['host'] = '127.0.0.1';
$cfg['Servers'][1]['port'] = '3307';
$cfg['Servers'][1]['user'] = getenv('GESTAO_PMA_USER');
$cfg['Servers'][1]['password'] = getenv('GESTAO_PMA_PASSWORD');
$cfg['Servers'][1]['only_db'] = 'gestao_academica';
$cfg['Servers'][1]['verbose'] = 'Banco do projeto';
$cfg['Servers'][1]['AllowNoPassword'] = false;
$cfg['AllowArbitraryServer'] = false;
$cfg['VersionCheck'] = false;
$cfg['TempDir'] = getenv('GESTAO_PMA_TEMP');
'@
[IO.File]::WriteAllText((Join-Path $site 'config.inc.php'),$configSite,[Text.UTF8Encoding]::new($false))
$rodando=$false
try{
    $resposta=Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2
    $rodando=$resposta.Content.Contains('phpMyAdmin')
}catch{}
if(!$rodando){
    if(Get-NetTCPConnection -LocalPort 8090 -State Listen -ErrorAction SilentlyContinue){throw 'A porta 8090 esta sendo usada por outro aplicativo.'}
    $env:GESTAO_PMA_USER=$env:DB_USER
    $env:GESTAO_PMA_PASSWORD=$env:DB_PASSWORD
    $env:GESTAO_PMA_SECRET=[Guid]::NewGuid().ToString('N')
    $env:GESTAO_PMA_TEMP=(Join-Path $temporarios 'cache').Replace('\','/')
    try{
        Start-Process -FilePath $php -ArgumentList ('-c "'+$phpIni+'" -S 127.0.0.1:8090 -t "'+$site+'"') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $temporarios 'servidor.log') -RedirectStandardError (Join-Path $temporarios 'servidor-erros.log')
    }finally{
        foreach($nome in @('GESTAO_PMA_USER','GESTAO_PMA_PASSWORD','GESTAO_PMA_SECRET','GESTAO_PMA_TEMP','DB_PASSWORD')){
            Remove-Item -LiteralPath ('Env:'+ $nome) -ErrorAction SilentlyContinue
        }
    }
    for($tentativa=0;$tentativa -lt 20;$tentativa++){
        Start-Sleep -Milliseconds 400
        try{
            $resposta=Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2
            if($resposta.Content.Contains('phpMyAdmin')){$rodando=$true;break}
        }catch{}
    }
    if(!$rodando){throw 'O phpMyAdmin nao iniciou. Confira os arquivos em .tools/phpmyadmin-temp.'}
}
Write-Host '[OK] phpMyAdmin disponivel em http://127.0.0.1:8090/'
if(!$SemAbrir){Start-Process $url}
