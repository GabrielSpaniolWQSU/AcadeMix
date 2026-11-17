param([switch]$Verificar)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'iniciar-banco.ps1')
$cliente=Join-Path $PSScriptRoot '.tools\mysql-8.4.13-winx64\bin\mysql.exe'
$env:MYSQL_PWD=$env:DB_PASSWORD
try {
    $argumentos=@('--no-defaults','--protocol=TCP','--host=127.0.0.1','--port=3307','--user=gestao_app','--default-character-set=utf8mb4','gestao_academica')
    if($Verificar){
        $argumentos+=@('--table','--execute',"SELECT DATABASE() AS banco, VERSION() AS versao; SHOW TABLES; SELECT 'usuarios' AS tabela, COUNT(*) AS registros FROM usuarios UNION ALL SELECT 'avisos',COUNT(*) FROM avisos UNION ALL SELECT 'documentos',COUNT(*) FROM documentos UNION ALL SELECT 'atas_atividades',COUNT(*) FROM atas_atividades UNION ALL SELECT 'certificados',COUNT(*) FROM certificados;")
    } else {
        Write-Host 'Banco gestao_academica. Cada consulta termina com ponto e virgula.'
        Write-Host 'Exemplo: SHOW TABLES;'
        Write-Host 'Para sair: exit'
    }
    & $cliente @argumentos
    if($LASTEXITCODE -ne 0){throw 'Nao foi possivel consultar o banco. Inicie o projeto para criar as tabelas.'}
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
}
