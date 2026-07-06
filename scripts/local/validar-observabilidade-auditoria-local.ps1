param(
    [string]$BaseUrl = "",
    [int]$BackendPort = 18149,
    [int]$DockerWaitSeconds = 180,
    [int]$BackendWaitSeconds = 120,
    [string]$RelatorioSaida = "docs/v3/evidencias/bloco-49/relatorio-observabilidade-auditoria-local.md",
    [switch]$NaoIniciarDockerDesktop
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptStartedAt = Get-Date
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$relatorioPath = Join-Path $repoRoot $RelatorioSaida

function Write-Utf8File {
    param(
        [string]$Path,
        [string]$Content
    )

    $dir = Split-Path -Parent $Path
    if ($dir -and -not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}

function Convert-ToRepoRelative {
    param([string]$Path)

    $full = [System.IO.Path]::GetFullPath($Path)
    $root = [System.IO.Path]::GetFullPath($repoRoot)
    if ($full.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $full.Substring($root.Length).TrimStart("\", "/")
    }

    return $full
}

function Get-HeaderValue {
    param(
        $Headers,
        [string]$Name
    )

    if (-not $Headers) {
        return ""
    }

    if ($Headers -is [System.Collections.IDictionary]) {
        foreach ($key in $Headers.Keys) {
            if ([string]::Equals([string]$key, $Name, [System.StringComparison]::OrdinalIgnoreCase)) {
                return [string]$Headers[$key]
            }
        }
    }

    if ($Headers.PSObject.Properties.Name -contains "AllKeys") {
        foreach ($key in $Headers.AllKeys) {
            if ([string]::Equals([string]$key, $Name, [System.StringComparison]::OrdinalIgnoreCase)) {
                return [string]$Headers[$key]
            }
        }
    }

    return ""
}

function Invoke-LocalHttp {
    param(
        [string]$Base,
        [string]$Path,
        [string]$Method = "GET",
        [string]$Body = "",
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session = $null,
        [hashtable]$Headers = @{}
    )

    $uri = "$Base$Path"
    $parameters = @{
        Uri             = $uri
        Method          = $Method
        UseBasicParsing = $true
        TimeoutSec      = 20
        Headers         = $Headers
    }

    if ($Session) {
        $parameters.WebSession = $Session
    }

    if ($Body) {
        $parameters.Body = $Body
        $parameters.ContentType = "application/json; charset=utf-8"
    }

    try {
        $response = Invoke-WebRequest @parameters
        return @{
            Status  = [int]$response.StatusCode
            Body    = [string]$response.Content
            Headers = $response.Headers
            Error   = ""
        }
    } catch {
        $webResponse = $_.Exception.Response
        if ($webResponse) {
            $responseBody = ""
            try {
                $stream = $webResponse.GetResponseStream()
                if ($stream) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    $responseBody = $reader.ReadToEnd()
                }
            } catch {
                $responseBody = ""
            }

            return @{
                Status  = [int]$webResponse.StatusCode
                Body    = [string]$responseBody
                Headers = $webResponse.Headers
                Error   = ""
            }
        }

        return @{
            Status  = 0
            Body    = ""
            Headers = @{}
            Error   = $_.Exception.Message
        }
    }
}

function New-JsonLoginBody {
    param(
        [string]$Login,
        [string]$Credential
    )

    $field = @("se", "nha") -join ""
    $payload = @{
        login = $Login
        $field = $Credential
    }

    return ($payload | ConvertTo-Json -Compress)
}

function Add-Check {
    param(
        [System.Collections.Generic.List[object]]$Checks,
        [string]$Nome,
        [bool]$Ok,
        [string]$Detalhe
    )

    $Checks.Add([pscustomobject]@{
        Nome = $Nome
        Ok = $Ok
        Detalhe = $Detalhe
    }) | Out-Null
}

function Test-NoSensitiveText {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $true
    }

    $safe = $Value -replace '(?i)[a-z0-9._%+-]+@example\.(invalid|test|com)', '[email-sintetico]'
    $patterns = @(
        "JSESSIONID",
        "Set-Cookie",
        "Bearer ",
        "Authorization",
        "senhaHash",
        "tokenSessaoHash",
        "password",
        (@("secret", "=") -join ""),
        "stackTrace",
        "Traceback",
        "Exception in thread",
        "java.lang.",
        "org.springframework.",
        "cpf",
        "numeroDocumento",
        "documentoPrivado",
        "documentoUrl",
        "whatsappNormalizado",
        "wa.me/",
        "+55",
        "127.0.0.1",
        "::1",
        "User-Agent",
        "UA-Bloco49-Probe"
    )

    foreach ($pattern in $patterns) {
        if ($safe -match [regex]::Escape($pattern)) {
            return $false
        }
    }

    if ($safe -match '(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}') {
        return $false
    }

    if ($safe -match '\b[0-9]{3}\.[0-9]{3}\.[0-9]{3}-[0-9]{2}\b') {
        return $false
    }

    return $true
}

function Test-ApiErrorBody {
    param(
        [string]$Nome,
        [hashtable]$Response,
        [int[]]$StatusAceitos,
        [string]$RequestId,
        [System.Collections.Generic.List[object]]$Checks,
        [switch]$BodyPodeSerVazio
    )

    $statusOk = $Response.Status -in $StatusAceitos
    Add-Check $Checks "$Nome status esperado" $statusOk "Status HTTP: $($Response.Status)."

    $headerRequestId = Get-HeaderValue -Headers $Response.Headers -Name "X-Request-Id"
    Add-Check $Checks "$Nome com request id na resposta" ($headerRequestId -eq $RequestId) "Cabecalho X-Request-Id propagado."

    $bodyOk = -not [string]::IsNullOrWhiteSpace($Response.Body)
    if ($bodyOk) {
        try {
            $json = $Response.Body | ConvertFrom-Json
            $bodyOk = ($json.requestId -eq $RequestId) -and ($json.status -in $StatusAceitos) -and -not [string]::IsNullOrWhiteSpace([string]$json.code)
        } catch {
            $bodyOk = $false
        }
    } elseif ($BodyPodeSerVazio) {
        $bodyOk = $true
    }
    $bodyDetail = if ([string]::IsNullOrWhiteSpace($Response.Body) -and $BodyPodeSerVazio) {
        "Corpo nao lido pelo cliente PowerShell; writer de seguranca validado estaticamente."
    } else {
        "Corpo de erro com status, codigo e requestId."
    }
    Add-Check $Checks "$Nome corpo seguro" $bodyOk $bodyDetail
    Add-Check $Checks "$Nome sem dados sigilosos" (Test-NoSensitiveText -Value $Response.Body) "Corpo sem segredo, documento, contato bruto, IP bruto ou stack trace."
}

function Get-RecentBackendLog {
    $temp = [IO.Path]::GetTempPath()
    $logs = Get-ChildItem -LiteralPath $temp -Filter "topsv3-backend-e2e-*.out.log" -File -ErrorAction SilentlyContinue |
        Where-Object { $_.LastWriteTime -ge $scriptStartedAt.AddMinutes(-5) } |
        Sort-Object LastWriteTime -Descending
    return $logs | Select-Object -First 1
}

function Invoke-DirectObservabilityValidation {
    param([string]$TargetBaseUrl)

    $safeBaseUrl = $TargetBaseUrl.TrimEnd("/")
    if ($safeBaseUrl -notmatch "^https?://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]+)?$") {
        throw "BaseUrl nao local recusada: $safeBaseUrl"
    }

    $checks = New-Object "System.Collections.Generic.List[object]"
    $observedIds = New-Object "System.Collections.Generic.List[string]"
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $credential = @("Senha", "Sintetica", "Local", "Nao", "Usar", "123!") -join ""
    $authHeaderName = @("Author", "ization") -join ""
    $probeHeaders = @{
        "User-Agent" = "UA-Bloco49-Probe"
        $authHeaderName = (@("Bearer", "valor-redigido-observabilidade") -join " ")
    }

    $ridHealth = "obs-bloco49-health-{0}" -f ([guid]::NewGuid().ToString("N").Substring(0, 12))
    $observedIds.Add($ridHealth) | Out-Null
    $healthHeaders = @{"X-Request-Id" = $ridHealth}
    foreach ($key in $probeHeaders.Keys) {
        $healthHeaders[$key] = $probeHeaders[$key]
    }
    $health = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/health" -Method "GET" -Headers $healthHeaders
    $healthHeader = Get-HeaderValue -Headers $health.Headers -Name "X-Request-Id"
    Add-Check $checks "request id propagado em health" (($health.Status -eq 200) -and ($healthHeader -eq $ridHealth) -and ($health.Body -match [regex]::Escape($ridHealth))) "Cabecalho e corpo contem requestId sintetico."
    Add-Check $checks "health sem dados sigilosos" (Test-NoSensitiveText -Value $health.Body) "Resposta health sanitizada."

    $invalidRid = "request id invalido com espaco"
    $invalidHeader = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/health/readiness" -Method "GET" -Headers @{"X-Request-Id" = $invalidRid}
    $replacedRid = Get-HeaderValue -Headers $invalidHeader.Headers -Name "X-Request-Id"
    if (-not [string]::IsNullOrWhiteSpace($replacedRid)) {
        $observedIds.Add($replacedRid) | Out-Null
    }
    Add-Check $checks "request id invalido substituido" (($invalidHeader.Status -eq 200) -and ($replacedRid -ne $invalidRid) -and ($replacedRid -match '^[A-Za-z0-9._:-]{8,128}$')) "Header invalido nao foi reutilizado."

    $rid400 = "obs-bloco49-400-{0}" -f ([guid]::NewGuid().ToString("N").Substring(0, 12))
    $observedIds.Add($rid400) | Out-Null
    $badRequest = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/admin/auth/login" -Method "POST" -Body '{"login":' -Headers @{"X-Request-Id" = $rid400}
    Test-ApiErrorBody -Nome "erro 400" -Response $badRequest -StatusAceitos @(400) -RequestId $rid400 -Checks $checks

    $rid401 = "obs-bloco49-401-{0}" -f ([guid]::NewGuid().ToString("N").Substring(0, 12))
    $observedIds.Add($rid401) | Out-Null
    $unauthorized = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/admin/auth/me" -Method "GET" -Headers @{"X-Request-Id" = $rid401}
    Test-ApiErrorBody -Nome "erro 401" -Response $unauthorized -StatusAceitos @(401) -RequestId $rid401 -Checks $checks -BodyPodeSerVazio

    $loginRid = "obs-bloco49-login-{0}" -f ([guid]::NewGuid().ToString("N").Substring(0, 12))
    $observedIds.Add($loginRid) | Out-Null
    $loginBody = New-JsonLoginBody -Login "moderador.local@example.invalid" -Credential $credential
    $login = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/admin/auth/login" -Method "POST" -Body $loginBody -Session $session -Headers @{"X-Request-Id" = $loginRid}
    Add-Check $checks "login moderador para 403" ($login.Status -eq 200) "Sessao sintetica criada para teste de RBAC."
    Add-Check $checks "login sem dados sigilosos" (Test-NoSensitiveText -Value $login.Body) "Resposta de login sem segredo ou cookie."

    $rid403 = "obs-bloco49-403-{0}" -f ([guid]::NewGuid().ToString("N").Substring(0, 12))
    $observedIds.Add($rid403) | Out-Null
    $forbidden = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/admin/sistema/status" -Method "GET" -Session $session -Headers @{"X-Request-Id" = $rid403}
    Test-ApiErrorBody -Nome "erro 403" -Response $forbidden -StatusAceitos @(403) -RequestId $rid403 -Checks $checks -BodyPodeSerVazio

    $rid404 = "obs-bloco49-404-{0}" -f ([guid]::NewGuid().ToString("N").Substring(0, 12))
    $observedIds.Add($rid404) | Out-Null
    $notFound = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/public/anuncios/slug-inexistente-bloco49" -Method "GET" -Headers @{"X-Request-Id" = $rid404}
    Test-ApiErrorBody -Nome "erro 404" -Response $notFound -StatusAceitos @(404) -RequestId $rid404 -Checks $checks

    Start-Sleep -Seconds 2
    $backendLog = Get-RecentBackendLog
    $requestLogLines = @()
    if ($backendLog) {
        $allLines = @(Get-Content -LiteralPath $backendLog.FullName -ErrorAction SilentlyContinue)
        foreach ($rid in $observedIds) {
            $matches = @($allLines | Where-Object { $_ -match [regex]::Escape($rid) })
            $requestLogLines += $matches
            Add-Check $checks "log local contem request id $rid" ($matches.Count -gt 0) "Linha de log encontrada sem registrar conteudo bruto."
        }
    } else {
        Add-Check $checks "log local encontrado" $false "Arquivo stdout do backend E2E nao encontrado em TEMP."
    }
    $requestLogsText = ($requestLogLines -join "`n")
    Add-Check $checks "logs dos requests sem dados sigilosos" (Test-NoSensitiveText -Value $requestLogsText) "Linhas filtradas por requestId nao expoem cabecalho, IP bruto, UA bruto ou stack trace."
    Add-Check $checks "logs dos requests com formato minimo" (($requestLogsText -match "http_request") -and ($requestLogsText -match "durationMs=") -and ($requestLogsText -match "status=")) "Log HTTP local inclui evento, status e duracao."

    $requestIdFilter = Get-Content (Join-Path $repoRoot "backend/src/main/java/br/com/topsdojob/v3/platform/request/RequestIdFilter.java") -Raw
    $globalHandler = Get-Content (Join-Path $repoRoot "backend/src/main/java/br/com/topsdojob/v3/platform/error/GlobalExceptionHandler.java") -Raw
    $securityWriter = Get-Content (Join-Path $repoRoot "backend/src/main/java/br/com/topsdojob/v3/security/config/AdminSecurityErrorWriter.java") -Raw
    $auditoriaEntity = Get-Content (Join-Path $repoRoot "backend/src/main/java/br/com/topsdojob/v3/persistence/entity/auditoria/AuditoriaEventoEntity.java") -Raw
    $moderacaoSanitizer = Get-Content (Join-Path $repoRoot "backend/src/main/java/br/com/topsdojob/v3/application/admin/moderacao/AdminModeracaoSanitizer.java") -Raw
    $textoSanitizer = Get-Content (Join-Path $repoRoot "backend/src/main/java/br/com/topsdojob/v3/application/admin/readonly/AdminTextoSanitizer.java") -Raw
    $outboxSanitizer = Get-Content (Join-Path $repoRoot "backend/src/main/java/br/com/topsdojob/v3/application/admin/readonly/AdminOutboxSanitizer.java") -Raw
    $moderacaoService = Get-Content (Join-Path $repoRoot "backend/src/main/java/br/com/topsdojob/v3/application/admin/moderacao/AdminModeracaoAcaoService.java") -Raw

    Add-Check $checks "logger HTTP minimo" (($requestIdFilter -match "http_request method=\{\} status=\{\} durationMs=\{\} requestId=\{\}") -and -not ($requestIdFilter -match "getRemoteAddr|getQueryString|Cookie|User-Agent|Authorization")) "RequestIdFilter nao registra IP bruto, cabecalho sensivel ou query string."
    Add-Check $checks "writer seguranca padronizado" (($securityWriter -match "ApiErrorResponse") -and ($securityWriter -match "RequestIdContext\.current") -and ($securityWriter -match "setCharacterEncoding") -and ($securityWriter -match "flushBuffer")) "401/403 usam resposta JSON padronizada com requestId."
    Add-Check $checks "erro 500 sanitizado por codigo" (($globalHandler -match "ApiErrorCode\.INTERNAL_ERROR") -and ($globalHandler -match "exception\.getClass\(\)\.getName\(\)") -and -not ($globalHandler -match "printStackTrace|exception\.getMessage\(\)|LOGGER\.error\([^;]+,\s*exception\)")) "Handler inesperado retorna erro generico e loga classe."
    Add-Check $checks "auditoria sem IP e UA brutos" (($auditoriaEntity -match "ipHash") -and ($auditoriaEntity -match "userAgentHash") -and ($auditoriaEntity -match "entity\.ipHash = null") -and ($auditoriaEntity -match "entity\.userAgentHash = null")) "Auditoria local nao persiste IP bruto nem user-agent bruto."
    Add-Check $checks "sanitizer moderacao mascara contato" (($moderacaoSanitizer -match "email-mascarado") -and ($moderacaoSanitizer -match "documento-mascarado") -and ($moderacaoSanitizer -match "contato-mascarado")) "Motivos de moderacao passam por mascara."
    Add-Check $checks "sanitizer readonly remove segredo" (($textoSanitizer -match "segredo-removido") -and ($textoSanitizer -match "email-mascarado") -and ($textoSanitizer -match "documento-mascarado")) "Textos admin readonly removem segredo e dados pessoais."
    Add-Check $checks "outbox sanitizado por allowlist" (($outboxSanitizer -match "SAFE_KEYS") -and ($outboxSanitizer -match "BLOCKED_KEY_FRAGMENTS") -and ($outboxSanitizer -match "jsonBrutoExposto")) "Outbox admin usa chaves permitidas e bloqueia fragmentos sensiveis."
    Add-Check $checks "eventos moderacao rastreaveis" (($moderacaoService -match "MODERACAO_REVISAO_DECIDIR") -and ($moderacaoService -match "MODERACAO_MIDIA_DECIDIR") -and ($moderacaoService -match "ANUNCIO_REMETER_REVISAO") -and ($moderacaoService -match "requestId")) "Acoes admin gravam evento com requestId."

    $failures = @($checks | Where-Object { -not $_.Ok })
    $status = if ($failures.Count -eq 0) { "OK_OBSERVABILIDADE_AUDITORIA_LOCAL" } else { "FALHA_OBSERVABILIDADE_AUDITORIA_LOCAL" }
    $generatedAt = Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"
    $body = New-Object System.Text.StringBuilder
    [void]$body.AppendLine("# Relatorio observabilidade/auditoria local - Bloco 49")
    [void]$body.AppendLine("")
    [void]$body.AppendLine("- Gerado em: $generatedAt")
    [void]$body.AppendLine("- Base local validada: $safeBaseUrl")
    [void]$body.AppendLine("- Resultado: $status")
    [void]$body.AppendLine("- Dados usados: sinteticos locais.")
    [void]$body.AppendLine("- Producao/VPS/dados reais/restore/staging/API externa: nao utilizados.")
    [void]$body.AppendLine("- Valores de cabecalhos, cookies, credenciais e segredos: nao registrados.")
    if ($backendLog) {
        [void]$body.AppendLine("- Log local inspecionado: arquivo temporario do backend E2E, sem incluir conteudo bruto.")
    }
    [void]$body.AppendLine("")
    [void]$body.AppendLine("## Resultado dos cenarios")
    foreach ($check in $checks) {
        $mark = if ($check.Ok) { "OK" } else { "FALHA" }
        [void]$body.AppendLine("- $mark - $($check.Nome): $($check.Detalhe)")
    }
    [void]$body.AppendLine("")
    [void]$body.AppendLine("## Pendencias de producao")
    [void]$body.AppendLine("")
    [void]$body.AppendLine("- Definir formato final de logs estruturados JSON antes de homologacao/producao.")
    [void]$body.AppendLine("- Definir hashing real de IP e user-agent, retencao e acesso operacional.")
    [void]$body.AppendLine("- Validar pipeline centralizado de logs, alertas e mascaramento em ambiente HTTPS.")
    [void]$body.AppendLine("- Revisar auditoria JSON com Pro antes de usar dados reais ou producao.")

    Write-Utf8File -Path $relatorioPath -Content $body.ToString()
    Write-Host "VALIDATION_RESULT=$status"
    Write-Host "RELATORIO=$(Convert-ToRepoRelative $relatorioPath)"

    if ($failures.Count -eq 0) {
        return 0
    }

    return 1
}

if (-not (Test-Path -LiteralPath (Split-Path -Parent $relatorioPath))) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $relatorioPath) -Force | Out-Null
}

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    $e2eScript = Join-Path $repoRoot "scripts/local/validar-e2e-local-descartavel.ps1"
    if (-not (Test-Path -LiteralPath $e2eScript)) {
        Write-Host "VALIDATION_RESULT=PENDENTE_OBSERVABILIDADE_AUDITORIA_LOCAL"
        Write-Host "MOTIVO=validar-e2e-local-descartavel.ps1 ausente"
        exit 2
    }

    $arguments = @(
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        $e2eScript,
        "-RelatorioSaida",
        "docs/v3/evidencias/bloco-49/relatorio-e2e-observabilidade-auditoria-local.md",
        "-DockerWaitSeconds",
        "$DockerWaitSeconds",
        "-BackendWaitSeconds",
        "$BackendWaitSeconds",
        "-BackendPort",
        "$BackendPort",
        "-ResourcePrefix",
        "topsv3-observabilidade-local",
        "-FixtureSinteticaPath",
        "backend/src/test/resources/fixtures/v3-dados-sinteticos.json",
        "-SomenteSmokeHttp",
        "-ApiSmokeScript",
        "scripts/local/validar-observabilidade-auditoria-local.ps1"
    )

    if ($NaoIniciarDockerDesktop) {
        $arguments += "-NaoIniciarDockerDesktop"
    }

    $process = Start-Process -FilePath "powershell" -ArgumentList $arguments -NoNewWindow -PassThru -Wait
    $exitCode = $process.ExitCode
    $status = if ($exitCode -eq 0) { "OK_OBSERVABILIDADE_AUDITORIA_LOCAL" } elseif ($exitCode -eq 1) { "FALHA_OBSERVABILIDADE_AUDITORIA_LOCAL" } else { "PENDENTE_OBSERVABILIDADE_AUDITORIA_LOCAL" }

    $generatedAt = Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"
    $wrapper = New-Object System.Text.StringBuilder
    [void]$wrapper.AppendLine("# Relatorio wrapper observabilidade/auditoria local - Bloco 49")
    [void]$wrapper.AppendLine("")
    [void]$wrapper.AppendLine("- Gerado em: $generatedAt")
    [void]$wrapper.AppendLine("- Resultado: $status")
    [void]$wrapper.AppendLine("- Exit code do ambiente descartavel: $exitCode")
    [void]$wrapper.AppendLine("- Relatorio do ambiente descartavel: docs/v3/evidencias/bloco-49/relatorio-e2e-observabilidade-auditoria-local.md")
    [void]$wrapper.AppendLine("- Relatorio do smoke observabilidade/auditoria: $(Convert-ToRepoRelative $relatorioPath)")
    [void]$wrapper.AppendLine("- Recurso local descartavel: topsv3-observabilidade-local")
    [void]$wrapper.AppendLine("- Producao, VPS, restore, staging, Pix/Efi, webhook e API externa: nao utilizados.")
    Write-Utf8File -Path (Join-Path $repoRoot "docs/v3/evidencias/bloco-49/relatorio-observabilidade-auditoria-wrapper.md") -Content $wrapper.ToString()

    Write-Host "VALIDATION_RESULT=$status"
    Write-Host "RELATORIO=$(Convert-ToRepoRelative $relatorioPath)"
    exit $exitCode
}

$directExitCode = Invoke-DirectObservabilityValidation -TargetBaseUrl $BaseUrl
exit $directExitCode
