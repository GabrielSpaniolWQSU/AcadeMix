param([switch]$NovaChave, [switch]$Reiniciar, [switch]$ConfigurarGoogleDrive)

$ErrorActionPreference = 'Stop'
$arquivoGoogle = Join-Path $PSScriptRoot '.google-drive-credenciais.xml'

if ($ConfigurarGoogleDrive) {
    $idGoogle = (Read-Host 'ID do cliente Google').Trim()
    if ($idGoogle -notmatch '^[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$') {
        Write-Host '[ERRO] Informe o ID do cliente OAuth que termina em .apps.googleusercontent.com.'
        exit 1
    }
    $segredoGoogle = Read-Host 'Segredo do cliente Google' -AsSecureString
    $arquivoTemporario = "$arquivoGoogle.$([Guid]::NewGuid().ToString('N')).tmp"
    try {
        if ($segredoGoogle.Length -eq 0) {
            Write-Host '[ERRO] Nenhum segredo informado.'
            exit 1
        }
        $credencialGoogle = [PSCredential]::new($idGoogle, $segredoGoogle)
        $credencialGoogle | Export-Clixml -LiteralPath $arquivoTemporario
        if (Test-Path -LiteralPath $arquivoGoogle) {
            [IO.File]::Replace($arquivoTemporario, $arquivoGoogle, [NullString]::Value)
        } else {
            Move-Item -LiteralPath $arquivoTemporario -Destination $arquivoGoogle
        }
        Write-Host '[OK] Credenciais do Google Drive salvas. Agora execute iniciar-projeto.bat.'
        exit 0
    } catch {
        Write-Host '[ERRO] Nao foi possivel salvar as credenciais do Google Drive.'
        exit 1
    } finally {
        $segredoGoogle.Dispose()
        if (Test-Path -LiteralPath $arquivoTemporario) {
            Remove-Item -LiteralPath $arquivoTemporario
        }
    }
}

if (([string]::IsNullOrWhiteSpace($env:GOOGLE_DRIVE_CLIENT_ID) -or
     [string]::IsNullOrWhiteSpace($env:GOOGLE_DRIVE_CLIENT_SECRET)) -and
    (Test-Path -LiteralPath $arquivoGoogle)) {
    try {
        $credencialGoogle = Import-Clixml -LiteralPath $arquivoGoogle
        $valorGoogle = $credencialGoogle.GetNetworkCredential().Password.Trim()
        if ([string]::IsNullOrWhiteSpace($valorGoogle) -or
            [string]::IsNullOrWhiteSpace($credencialGoogle.UserName)) {
            throw 'Credenciais do Google incompletas.'
        }
        $env:GOOGLE_DRIVE_CLIENT_ID = $credencialGoogle.UserName
        $env:GOOGLE_DRIVE_CLIENT_SECRET = $valorGoogle
    } catch {
        Write-Host '[AVISO] Nao foi possivel ler as credenciais do Google Drive. Execute configurar-google-drive.bat.'
    } finally {
        $valorGoogle = $null
    }
}

if (Test-Path -LiteralPath (Join-Path $PSScriptRoot '.mysql-credenciais.xml')) {
    . (Join-Path $PSScriptRoot 'iniciar-banco.ps1')
}

# A chave permanece apenas no ambiente deste processo e dos seus filhos.
$etapa = 'carregar a chave'
try {
    if (-not $NovaChave -and [string]::IsNullOrWhiteSpace($env:META_API_KEY)) {
        $env:META_API_KEY = [Environment]::GetEnvironmentVariable('META_API_KEY', 'User')
    }
    if (-not $NovaChave -and [string]::IsNullOrWhiteSpace($env:META_API_KEY)) {
        $env:META_API_KEY = [Environment]::GetEnvironmentVariable('META_API_KEY', 'Machine')
    }
    if ($NovaChave -or [string]::IsNullOrWhiteSpace($env:META_API_KEY)) {
        Write-Host 'Cole sua chave da Meta e pressione Enter. Os caracteres ficarao ocultos.'
        $secret = Read-Host 'META_API_KEY' -AsSecureString
        $secretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
        try {
            $env:META_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer)
        } finally {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer)
            $secret.Dispose()
        }
    }
    if ([string]::IsNullOrWhiteSpace($env:META_API_KEY)) {
        Write-Host '[ERRO] Nenhuma chave informada. Backend nao iniciado.'
        exit 1
    }
    $env:META_API_KEY = $env:META_API_KEY.Trim()
    $etapa = 'verificar a porta 8080'
    $listeners = @(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)
    if ($listeners.Count -gt 0) {
        if (-not $Reiniciar) {
            Write-Host '[ERRO] O servidor anterior ainda esta ativo. Para atualizar, execute:'
            Write-Host "powershell -NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Reiniciar"
            exit 1
        }
        $etapa = 'identificar o servidor anterior'
        $processos = @($listeners | Select-Object -ExpandProperty OwningProcess -Unique | ForEach-Object {
            Get-CimInstance Win32_Process -Filter "ProcessId=$_"
        })
        $backendPath = (Join-Path $PSScriptRoot 'backend')
        foreach ($processo in $processos) {
            $origemProjeto = [string]$processo.CommandLine
            # O Maven pode guardar o classpath em um arquivo de argumentos do Java.
            foreach ($referencia in [regex]::Matches($origemProjeto, '@(?:"([^"]+)"|([^\s"]+))')) {
                $argumentosJava = if ($referencia.Groups[1].Success) { $referencia.Groups[1].Value } else { $referencia.Groups[2].Value }
                if (Test-Path -LiteralPath $argumentosJava -PathType Leaf) {
                    $origemProjeto += [IO.File]::ReadAllText($argumentosJava)
                }
            }
            $origemProjeto = ($origemProjeto.Replace('/', '\') -replace '\\+', '\')
            if ($processo.Name -ne 'java.exe' -or
                $processo.CommandLine -notlike '*br.edu.faculdade.GestaoAcademicaApplication*' -or
                $origemProjeto.IndexOf($backendPath, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
                throw 'A porta 8080 pertence a outro aplicativo. Nenhum processo foi encerrado.'
            }
        }
        foreach ($processo in $processos) {
            $etapa = 'encerrar o servidor anterior'
            Stop-Process -Id $processo.ProcessId -ErrorAction Stop
            Wait-Process -Id $processo.ProcessId -Timeout 10 -ErrorAction SilentlyContinue
        }
        Write-Host '[OK] Instancia anterior encerrada. Carregando a versao atualizada.'
    }
    Write-Host '[OK] Chave disponivel para o backend (valor oculto).'
    $etapa = 'localizar o Maven'
    $mavenPath = Join-Path $PSScriptRoot '.tools\apache-maven-3.9.16\bin\mvn.cmd'
    if (-not (Test-Path -LiteralPath $mavenPath)) {
        $mavenPath = (Get-Command mvn.cmd -ErrorAction Stop).Source
    }
    $etapa = 'iniciar o servidor atualizado'
    Set-Location (Join-Path $PSScriptRoot 'backend')
    & $mavenPath "-Dmaven.repo.local=$PSScriptRoot\.m2\repository" spring-boot:run
    exit $LASTEXITCODE
} catch {
    $detalhe = $_.Exception.Message
    if (-not [string]::IsNullOrWhiteSpace($env:META_API_KEY)) {
        $detalhe = $detalhe.Replace($env:META_API_KEY, '[chave oculta]')
    }
    if (-not [string]::IsNullOrWhiteSpace($env:GOOGLE_DRIVE_CLIENT_SECRET)) {
        $detalhe = $detalhe.Replace($env:GOOGLE_DRIVE_CLIENT_SECRET, '[segredo oculto]')
    }
    if (-not [string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
        $detalhe = $detalhe.Replace($env:DB_PASSWORD, '[senha oculta]')
    }
    Write-Host "[ERRO] Falha ao $etapa."
    Write-Host $detalhe
    exit 1
}
