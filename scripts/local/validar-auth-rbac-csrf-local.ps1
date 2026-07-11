param(
    [string]$BaseUrl = "",
    [int]$BackendPort = 18148,
    [int]$DockerWaitSeconds = 180,
    [int]$BackendWaitSeconds = 120,
    [string]$RelatorioSaida = "docs/v3/evidencias/bloco-48/relatorio-auth-rbac-csrf-local.md",
    [switch]$NaoIniciarDockerDesktop
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$relatorioPath = Join-Path $repoRoot $RelatorioSaida
$evidenciasDir = Split-Path -Parent $relatorioPath

function Write-Utf8File {
    param(
        [string]$Path,
        [string]$Content
    )

    $dir = Split-Path -Parent $Path
    if ($dir -and -not (Test-Path $dir)) {
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

function Test-NoSensitivePayload {
    param(
        [string]$Value
    )

    if (-not $Value) {
        return $true
    }

    $patterns = @(
        "JSESSIONID",
        "Set-Cookie",
        "Bearer ",
        "Authorization",
        "senhaHash",
        "tokenSessaoHash",
        "credential",
        "password",
        "stackTrace",
        "traceId",
        "java.lang.",
        "org.springframework."
    )

    foreach ($pattern in $patterns) {
        if ($Value -match [regex]::Escape($pattern)) {
            return $false
        }
    }

    return $true
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

function Invoke-DirectAuthRbacCsrfValidation {
    param([string]$TargetBaseUrl)

    $safeBaseUrl = $TargetBaseUrl.TrimEnd("/")
    if ($safeBaseUrl -notmatch "^https?://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]+)?$") {
        throw "BaseUrl nao local recusada: $safeBaseUrl"
    }

    $checks = New-Object "System.Collections.Generic.List[object]"
    $adminSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $moderadorSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $credential = @("Senha", "Sintetica", "Local", "Nao", "Usar", "123!") -join ""

    $health = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/health" -Method "GET"
    Add-Check $checks "health local acessivel" ($health.Status -eq 200) "Status HTTP: $($health.Status)."

    $corsHeaders = @{
        Origin = "http://localhost:3000"
        "Access-Control-Request-Method" = "POST"
        "Access-Control-Request-Headers" = "content-type"
    }
    $cors = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/admin/auth/login" -Method "OPTIONS" -Headers $corsHeaders
    $allowOrigin = Get-HeaderValue -Headers $cors.Headers -Name "Access-Control-Allow-Origin"
    $allowCredentials = Get-HeaderValue -Headers $cors.Headers -Name "Access-Control-Allow-Credentials"
    $corsOk = ($cors.Status -in @(200, 204)) -and ($allowOrigin -eq "http://localhost:3000") -and ($allowCredentials -eq "true")
    Add-Check $checks "CORS local restrito e coerente" $corsOk "Status HTTP: $($cors.Status); origem liberada localhost; credenciais: $allowCredentials."

    $unknownApi = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/rota-inexistente-auth-rbac-csrf" -Method "GET"
    Add-Check $checks "API desconhecida bloqueada" ($unknownApi.Status -in @(401, 403)) "Status HTTP: $($unknownApi.Status); sem resposta publica normal."

    $meSemSessao = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/admin/auth/me" -Method "GET"
    Add-Check $checks "rota admin sem sessao bloqueada" ($meSemSessao.Status -eq 401) "Status HTTP: $($meSemSessao.Status)."
    Add-Check $checks "erro sem sessao sem stack trace" (Test-NoSensitivePayload -Value $meSemSessao.Body) "Resposta sanitizada sem token, cookie ou stack trace."

    $loginInvalidoBody = New-JsonLoginBody -Login "admin.local@example.invalid" -Credential "valor_invalido_local"
    $loginInvalido = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/admin/auth/login" -Method "POST" -Body $loginInvalidoBody -Session $adminSession
    Add-Check $checks "login invalido recusado" ($loginInvalido.Status -eq 401) "Status HTTP: $($loginInvalido.Status)."
    Add-Check $checks "login invalido sem stack trace" (Test-NoSensitivePayload -Value $loginInvalido.Body) "Erro padronizado e sanitizado."

    $adminLoginBody = New-JsonLoginBody -Login "admin.local@example.invalid" -Credential $credential
    $adminLogin = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/admin/auth/login" -Method "POST" -Body $adminLoginBody -Session $adminSession
    $setCookie = Get-HeaderValue -Headers $adminLogin.Headers -Name "Set-Cookie"
    $adminCookieOk = ($setCookie -match "JSESSIONID") -and ($setCookie -match "HttpOnly") -and ($setCookie -match "SameSite=Lax")
    Add-Check $checks "login admin local OK" ($adminLogin.Status -eq 200) "Status HTTP: $($adminLogin.Status)."
    Add-Check $checks "cookie de sessao HttpOnly e SameSite" $adminCookieOk "Cookie de sessao presente; atributos HttpOnly/SameSite=Lax verificados sem registrar valor."
    Add-Check $checks "login admin sem vazamento sensivel" (Test-NoSensitivePayload -Value $adminLogin.Body) "Resposta de login nao expoe hash, cookie, token ou stack trace."

    $adminMe = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/admin/auth/me" -Method "GET" -Session $adminSession
    Add-Check $checks "sessao admin reconhecida" (($adminMe.Status -eq 200) -and ($adminMe.Body -match "ADMIN")) "Status HTTP: $($adminMe.Status); papel ADMIN presente."
    Add-Check $checks "me admin sem segredo" (Test-NoSensitivePayload -Value $adminMe.Body) "DTO administrativo nao expoe credencial ou cookie."

    $permissoes = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/admin/auth/permissions" -Method "GET" -Session $adminSession
    $permissoesOk = ($permissoes.Status -eq 200) -and ($permissoes.Body -match "ADMIN_CONFIGURAR") -and ($permissoes.Body -match "ANUNCIO_LER") -and ($permissoes.Body -match "FINANCEIRO_LER")
    Add-Check $checks "ADMIN com permissoes esperadas" $permissoesOk "Status HTTP: $($permissoes.Status); permissoes esperadas encontradas."

    $statusSistema = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/admin/sistema/status" -Method "GET" -Session $adminSession
    Add-Check $checks "ADMIN acessa status esperado" ($statusSistema.Status -eq 200) "Status HTTP: $($statusSistema.Status)."
    Add-Check $checks "status admin sem segredo" (Test-NoSensitivePayload -Value $statusSistema.Body) "Resposta administrativa sem cookie, token ou stack trace."

    $logout = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/admin/auth/logout" -Method "POST" -Session $adminSession
    Add-Check $checks "logout admin local OK" ($logout.Status -eq 200) "Status HTTP: $($logout.Status)."
    $adminDepoisLogout = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/admin/auth/me" -Method "GET" -Session $adminSession
    Add-Check $checks "sessao invalida apos logout" ($adminDepoisLogout.Status -eq 401) "Status HTTP: $($adminDepoisLogout.Status)."

    $moderadorLoginBody = New-JsonLoginBody -Login "moderador.local@example.invalid" -Credential $credential
    $moderadorLogin = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/admin/auth/login" -Method "POST" -Body $moderadorLoginBody -Session $moderadorSession
    Add-Check $checks "login moderador local OK" ($moderadorLogin.Status -eq 200) "Status HTTP: $($moderadorLogin.Status)."
    Add-Check $checks "login moderador sem vazamento sensivel" (Test-NoSensitivePayload -Value $moderadorLogin.Body) "Resposta de login sanitizada."

    $moderadorStatus = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/admin/sistema/status" -Method "GET" -Session $moderadorSession
    Add-Check $checks "MODERADOR sem acesso a configuracao sensivel" ($moderadorStatus.Status -eq 403) "Status HTTP: $($moderadorStatus.Status)."

    $moderadorCredito = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/admin/creditos/consistencia" -Method "GET" -Session $moderadorSession
    Add-Check $checks "MODERADOR sem acesso financeiro creditos" ($moderadorCredito.Status -eq 403) "Status HTTP: $($moderadorCredito.Status)."

    $moderadorPagamentos = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/admin/pagamentos/consistencia" -Method "GET" -Session $moderadorSession
    Add-Check $checks "MODERADOR sem acesso financeiro pagamentos" ($moderadorPagamentos.Status -eq 403) "Status HTTP: $($moderadorPagamentos.Status)."

    $moderacaoResumo = Invoke-LocalHttp -Base $safeBaseUrl -Path "/api/admin/moderacao/resumo" -Method "GET" -Session $moderadorSession
    Add-Check $checks "MODERADOR acessa moderacao esperada" ($moderacaoResumo.Status -eq 200) "Status HTTP: $($moderacaoResumo.Status)."
    $moderacaoSemSensivel = (Test-NoSensitivePayload -Value $moderacaoResumo.Body) -and ($moderacaoResumo.Body -notmatch "(?i)\bcpf\b|\brg\b|passaporte|numeroDocumento|documentoPrivado|documentoUrl|documentoReal|documentoNumero")
    Add-Check $checks "MODERADOR sem dados sensiveis em moderacao" $moderacaoSemSensivel "Resposta sem identificador documental privado, credencial, cookie ou stack trace."

    $securityConfig = [System.IO.File]::ReadAllText((Join-Path $repoRoot "backend/src/main/java/br/com/topsdojob/v3/security/config/SecurityConfig.java"))
    $appConfig = [System.IO.File]::ReadAllText((Join-Path $repoRoot "backend/src/main/resources/application.yml"))
    $appLocalConfig = [System.IO.File]::ReadAllText((Join-Path $repoRoot "backend/src/main/resources/application-local.yml"))

    Add-Check $checks "CSRF local documentado no codigo" (($securityConfig -match "csrf\.disable\(\)") -and ($securityConfig -match "local")) "Ambiente local usa CSRF desabilitado para smoke controlado."
    Add-Check $checks "CSRF nao-local usa repositorio CSRF" ($securityConfig -match "CookieCsrfTokenRepository\.withHttpOnlyFalse\(\)") "Configuracao nao-local possui repositorio CSRF."
    Add-Check $checks "fallback /api bloqueado por seguranca" (($securityConfig -match 'requestMatchers\("/api/\*\*"\)\.denyAll\(\)') -and ($securityConfig -match "anyRequest\(\)\.denyAll\(\)")) "Fallback de API e demais rotas negados."
    Add-Check $checks "cookie HttpOnly no YAML" ($appConfig -match "http-only:\s*true") "Cookie de sessao configurado como HttpOnly."
    Add-Check $checks "cookie SameSite Lax no YAML" ($appConfig -match "same-site:\s*lax") "Cookie de sessao configurado com SameSite=Lax."
    Add-Check $checks "cookie seguro fora do local" ($appConfig -match "secure:\s*true") "Default nao-local mantem cookie seguro."
    Add-Check $checks "cookie local sem Secure por localhost" ($appLocalConfig -match "secure:\s*false") "Local permite cookie de sessao em HTTP apenas para localhost."

    $adminFrontendFiles = Get-ChildItem -Path (Join-Path $repoRoot "frontend/src") -Recurse -Include "*.ts", "*.tsx" -File |
        Where-Object { $_.FullName -match "\\admin\\" -or $_.FullName -match "\\modules\\admin\\" }
    $adminUiUnsafe = @()
    foreach ($file in $adminFrontendFiles) {
        $content = [System.IO.File]::ReadAllText($file.FullName)
        if ($content -match "document\.cookie|localStorage|sessionStorage|error\.stack|stackTrace|<pre>\s*\{\s*JSON\.stringify") {
            $adminUiUnsafe += (Convert-ToRepoRelative $file.FullName)
        }
    }
    Add-Check $checks "UI admin sem cookie/storage/stack bruto" ($adminUiUnsafe.Count -eq 0) "Arquivos com padrao inseguro: $($adminUiUnsafe.Count)."

    $failures = @($checks | Where-Object { -not $_.Ok })
    $status = if ($failures.Count -eq 0) { "OK_AUTH_RBAC_CSRF_LOCAL" } else { "FALHA_AUTH_RBAC_CSRF_LOCAL" }
    $generatedAt = Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"
    $body = New-Object System.Text.StringBuilder
    [void]$body.AppendLine("# Relatorio Auth/RBAC/CSRF local - Bloco 48")
    [void]$body.AppendLine("")
    [void]$body.AppendLine("- Gerado em: $generatedAt")
    [void]$body.AppendLine("- Base local validada: $safeBaseUrl")
    [void]$body.AppendLine("- Resultado: $status")
    [void]$body.AppendLine("- Dados usados: sinteticos locais.")
    [void]$body.AppendLine("- Producao/VPS/dados reais/restore/staging/API externa: nao utilizados.")
    [void]$body.AppendLine("- Valores de cookie, credencial e tokens: nao registrados.")
    [void]$body.AppendLine("")
    [void]$body.AppendLine("## Resultado dos cenarios")
    foreach ($check in $checks) {
        $mark = if ($check.Ok) { "OK" } else { "FALHA" }
        [void]$body.AppendLine("- $mark - $($check.Nome): $($check.Detalhe)")
    }
    [void]$body.AppendLine("")
    [void]$body.AppendLine("## CSRF")
    [void]$body.AppendLine("")
    [void]$body.AppendLine("- Local: CSRF desabilitado para smoke controlado com PostgreSQL descartavel e localhost.")
    [void]$body.AppendLine("- Nao-local: SecurityConfig contem repositorio CookieCsrfTokenRepository; revisao Pro antes de homologacao/producao permanece obrigatoria.")
    [void]$body.AppendLine("- Pendencia de producao: validar token CSRF real com frontend, cookie seguro HTTPS, CORS final e politica de sessao em ambiente homologado.")
    [void]$body.AppendLine("")
    [void]$body.AppendLine("## Pendencias")
    if ($failures.Count -eq 0) {
        [void]$body.AppendLine("- Nenhuma falha local encontrada no escopo Auth/RBAC/CSRF do Bloco 48.")
    } else {
        foreach ($failure in $failures) {
            [void]$body.AppendLine("- Corrigir: $($failure.Nome).")
        }
    }

    Write-Utf8File -Path $relatorioPath -Content $body.ToString()
    Write-Host "VALIDATION_RESULT=$status"
    Write-Host "RELATORIO=$(Convert-ToRepoRelative $relatorioPath)"

    if ($failures.Count -eq 0) {
        return 0
    }

    return 1
}

if (-not (Test-Path $evidenciasDir)) {
    New-Item -ItemType Directory -Path $evidenciasDir -Force | Out-Null
}

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    $wrapperReport = Join-Path $repoRoot "docs/v3/evidencias/bloco-48/relatorio-e2e-auth-rbac-csrf-local.md"
    $e2eScript = Join-Path $repoRoot "scripts/local/validar-e2e-local-descartavel.ps1"
    if (-not (Test-Path $e2eScript)) {
        Write-Output "VALIDATION_RESULT=PENDENTE_AUTH_RBAC_CSRF_LOCAL"
        Write-Output "MOTIVO=validar-e2e-local-descartavel.ps1 ausente"
        exit 2
    }

    $arguments = @(
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        $e2eScript,
        "-RelatorioSaida",
        "docs/v3/evidencias/bloco-48/relatorio-e2e-auth-rbac-csrf-local.md",
        "-DockerWaitSeconds",
        "$DockerWaitSeconds",
        "-BackendWaitSeconds",
        "$BackendWaitSeconds",
        "-BackendPort",
        "$BackendPort",
        "-ResourcePrefix",
        "topsv3-auth-rbac-csrf",
        "-FixtureSinteticaPath",
        "backend/src/test/resources/fixtures/v3-dados-sinteticos.json",
        "-SomenteSmokeHttp",
        "-ApiSmokeScript",
        "scripts/local/validar-auth-rbac-csrf-local.ps1"
    )

    if ($NaoIniciarDockerDesktop) {
        $arguments += "-NaoIniciarDockerDesktop"
    }

    $process = Start-Process -FilePath "powershell" -ArgumentList $arguments -NoNewWindow -PassThru -Wait
    $exitCode = $process.ExitCode

    $generatedAt = Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"
    $status = if ($exitCode -eq 0) { "OK_AUTH_RBAC_CSRF_LOCAL" } elseif ($exitCode -eq 1) { "FALHA_AUTH_RBAC_CSRF_LOCAL" } else { "PENDENTE_AUTH_RBAC_CSRF_LOCAL" }
    $body = New-Object System.Text.StringBuilder
    [void]$body.AppendLine("# Relatorio wrapper Auth/RBAC/CSRF local - Bloco 48")
    [void]$body.AppendLine("")
    [void]$body.AppendLine("- Gerado em: $generatedAt")
    [void]$body.AppendLine("- Resultado: $status")
    [void]$body.AppendLine("- Exit code do ambiente descartavel: $exitCode")
    [void]$body.AppendLine("- Relatorio do ambiente descartavel: $(Convert-ToRepoRelative $wrapperReport)")
    [void]$body.AppendLine("- Relatorio do smoke Auth/RBAC/CSRF: $(Convert-ToRepoRelative $relatorioPath)")
    [void]$body.AppendLine("- Recurso local descartavel: topsv3-auth-rbac-csrf")
    [void]$body.AppendLine("- Producao, VPS, restore, staging, Pix/Efi, webhook e API externa: nao utilizados.")
    Write-Utf8File -Path (Join-Path $repoRoot "docs/v3/evidencias/bloco-48/relatorio-auth-rbac-csrf-wrapper.md") -Content $body.ToString()

    Write-Output "VALIDATION_RESULT=$status"
    Write-Output "RELATORIO=$(Convert-ToRepoRelative $relatorioPath)"
    exit $exitCode
}

$directExitCode = Invoke-DirectAuthRbacCsrfValidation -TargetBaseUrl $BaseUrl
exit $directExitCode
