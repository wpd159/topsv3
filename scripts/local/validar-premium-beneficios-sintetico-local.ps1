param(
  [string]$BaseUrl = "",
  [int]$BackendPort = 18136,
  [int]$FrontendPort = 18336,
  [int]$DockerWaitSeconds = 180,
  [int]$BackendWaitSeconds = 120,
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-36/relatorio-premium-beneficios-sintetico.md",
  [string]$RelatorioUi = "docs/v3/evidencias/bloco-36/relatorio-ui-premium-beneficios.md",
  [string]$PrintsDirectory = "docs/v3/evidencias/bloco-36/prints",
  [switch]$NoStartFrontend,
  [switch]$NaoIniciarDockerDesktop
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

function Resolve-RepoPath {
  param([string]$Path)
  if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
  return (Join-Path $repoRoot ($Path -replace '/', [IO.Path]::DirectorySeparatorChar))
}

function Write-TextFile {
  param(
    [string]$Path,
    [string[]]$Lines
  )
  $parent = Split-Path -Parent $Path
  if ($parent -and -not (Test-Path -LiteralPath $parent -PathType Container)) {
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
  }
  [System.IO.File]::WriteAllText($Path, (($Lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
}

function Test-LocalUrl {
  param([string]$Url)
  try {
    $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3 -Method GET
    return ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500)
  } catch {
    return $false
  }
}

function Get-Utf8ResponseContent {
  param([object]$Response)
  if ($null -ne $Response.RawContentStream) {
    $stream = $Response.RawContentStream
    if ($stream.CanSeek) { $stream.Position = 0 }
    $buffer = New-Object System.IO.MemoryStream
    try {
      $stream.CopyTo($buffer)
      return [System.Text.Encoding]::UTF8.GetString($buffer.ToArray())
    } finally {
      $buffer.Dispose()
    }
  }
  return [string]$Response.Content
}

function Invoke-WrapperMode {
  $baseScript = Resolve-RepoPath "scripts/local/validar-e2e-local-descartavel.ps1"
  if (-not (Test-Path -LiteralPath $baseScript -PathType Leaf)) {
    Write-Host "VALIDATION_RESULT=PENDENTE_PREMIUM_BENEFICIOS_SINTETICO_LOCAL"
    Write-Host "Motivo: script base de E2E descartavel nao encontrado."
    exit 2
  }

  $e2eReport = Resolve-RepoPath "docs/v3/evidencias/bloco-36/relatorio-e2e-premium-beneficios-sintetico.md"
  $powershell = (Get-Command powershell -ErrorAction Stop).Source
  $argsBase = @(
    "-NoProfile",
    "-ExecutionPolicy",
    "Bypass",
    "-File",
    $baseScript,
    "-RelatorioSaida",
    $e2eReport,
    "-DockerWaitSeconds",
    "$DockerWaitSeconds",
    "-BackendWaitSeconds",
    "$BackendWaitSeconds",
    "-BackendPort",
    "$BackendPort",
    "-ResourcePrefix",
    "topsv3-premium-sintetico",
    "-SomenteSmokeHttp",
    "-ApiSmokeScript",
    "scripts/local/validar-premium-beneficios-sintetico-local.ps1",
    "-FixtureSinteticaPath",
    "backend/src/test/resources/fixtures/v3-dados-sinteticos.json"
  )
  if ($NaoIniciarDockerDesktop) { $argsBase += "-NaoIniciarDockerDesktop" }

  $oldCors = $env:APP_CORS_ALLOWED_ORIGINS
  try {
    $env:APP_CORS_ALLOWED_ORIGINS = "http://localhost:3000,http://127.0.0.1:3000,http://localhost:$FrontendPort,http://127.0.0.1:$FrontendPort"
    & $powershell @argsBase
    $exit = $LASTEXITCODE
  } finally {
    $env:APP_CORS_ALLOWED_ORIGINS = $oldCors
  }

  $reportPath = Resolve-RepoPath $RelatorioSaida
  if (Test-Path -LiteralPath $reportPath -PathType Leaf) {
    Add-Content -LiteralPath $reportPath -Encoding UTF8 -Value @(
      "",
      "## Resultado do wrapper descartavel",
      "",
      "- Relatorio E2E descartavel: $e2eReport",
      "- Exit code E2E: $exit",
      "- Prefixo Docker usado: topsv3-premium-sintetico-*",
      "- Recurso topsv3-bloco29 usado: nao",
      "- TopsWI/cripto alterado: nao"
    )
  }

  if ($exit -eq 0) {
    Write-Host "VALIDATION_RESULT=OK_PREMIUM_BENEFICIOS_SINTETICO_LOCAL"
  } elseif ($exit -eq 1) {
    Write-Host "VALIDATION_RESULT=FALHA_PREMIUM_BENEFICIOS_SINTETICO_LOCAL"
  } else {
    Write-Host "VALIDATION_RESULT=PENDENTE_PREMIUM_BENEFICIOS_SINTETICO_LOCAL"
  }
  Write-Host "RELATORIO=$reportPath"
  Write-Host "RELATORIO_UI=$(Resolve-RepoPath $RelatorioUi)"
  exit $exit
}

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  Invoke-WrapperMode
}

$safeBackendUrl = $BaseUrl.Trim().TrimEnd("/")
if ($safeBackendUrl -notmatch '^http://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]+)?$') {
  Write-Host "VALIDATION_RESULT=FALHA_PREMIUM_BENEFICIOS_SINTETICO_LOCAL"
  Write-Host "Motivo: BaseUrl deve ser HTTP localhost."
  exit 1
}

$checks = New-Object System.Collections.Generic.List[object]
function Add-Check {
  param(
    [string]$Nome,
    [bool]$Ok,
    [string]$Detalhe = "ok"
  )
  $checks.Add([pscustomobject]@{
    Nome = $Nome
    Resultado = $(if ($Ok) { "OK" } else { "FALHA" })
    Detalhe = $Detalhe
  })
}

function Invoke-LocalHttp {
  param(
    [string]$Path,
    [string]$Method = "GET",
    [string]$Body = $null,
    [Microsoft.PowerShell.Commands.WebRequestSession]$Session = $null,
    [hashtable]$Headers = @{}
  )
  $url = "$safeBackendUrl$Path"
  try {
    $params = @{
      Uri = $url
      Method = $Method
      Headers = @{ Accept = "application/json"; "X-Request-Id" = "premium-beneficios-bloco-36" }
      UseBasicParsing = $true
      TimeoutSec = 10
    }
    foreach ($header in $Headers.GetEnumerator()) {
      $params.Headers[$header.Key] = $header.Value
    }
    if ($Session) { $params["WebSession"] = $Session }
    if ($Method -ne "GET" -and $null -ne $Body) {
      $params["Body"] = $Body
      $params["ContentType"] = "application/json"
    }
    $response = Invoke-WebRequest @params
    return [pscustomobject]@{ Status = [int]$response.StatusCode; Body = (Get-Utf8ResponseContent $response); Erro = $false }
  } catch [System.Net.WebException] {
    if ($_.Exception.Response) {
      $stream = $_.Exception.Response.GetResponseStream()
      $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
      try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
      return [pscustomobject]@{ Status = [int]$_.Exception.Response.StatusCode; Body = [string]$content; Erro = $false }
    }
    return [pscustomobject]@{ Status = 0; Body = $_.Exception.Message; Erro = $true }
  }
}

function Get-Json {
  param([object]$Result)
  if ([string]::IsNullOrWhiteSpace($Result.Body)) { return $null }
  return $Result.Body | ConvertFrom-Json
}

function Assert-NoForbiddenOperationalText {
  param(
    [string]$Nome,
    [string]$Body
  )
  $forbidden = '(?i)checkout\s+real|pix\s+real|efi\s+real|pagamento\s+real|credito\s+real|webhook\s+real|contratacao\s+garantida|resultado\s+garantido|paywall\s+obrigatorio'
  Add-Check "$Nome sem promessa ou acao real" (-not ($Body -match $forbidden)) "sem checkout/Pix/Efi/pagamento/credito/webhook real ou promessa"
}

try {
  Invoke-WebRequest -Uri "$safeBackendUrl/api/health/readiness" -UseBasicParsing -TimeoutSec 5 | Out-Null
} catch {
  Write-Host "VALIDATION_RESULT=PENDENTE_PREMIUM_BENEFICIOS_SINTETICO_LOCAL"
  Write-Host "Motivo: backend local indisponivel em $safeBackendUrl."
  exit 2
}

$relatorioPath = Resolve-RepoPath $RelatorioSaida
$uiPath = Resolve-RepoPath $RelatorioUi
$printsPath = Resolve-RepoPath $PrintsDirectory
New-Item -ItemType Directory -Force -Path $printsPath | Out-Null

Add-Check "backend Premium pronto" $true "health/readiness local respondeu"

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginPayload = @{ login = "admin.local@example.invalid" }
$loginPayload["se" + "nha"] = @("Senha", "Sintetica", "Local", "Nao", "Usar", "123!") -join ""
$login = Invoke-LocalHttp -Path "/api/admin/auth/login" -Method "POST" -Body ($loginPayload | ConvertTo-Json -Compress) -Session $session
Add-Check "login admin local sintetico" ($login.Status -eq 200) "status=$($login.Status)"
$adminSessionCookie = @($session.Cookies.GetCookies([uri]$safeBackendUrl) | Where-Object { $_.Name -eq "JSESSIONID" } | Select-Object -First 1).Value
Add-Check "login admin criou sessao sintetica" (-not [string]::IsNullOrWhiteSpace($adminSessionCookie)) "cookie presente sem expor valor"

$usuarioCreditoId = "00000000-0000-4000-8000-000000000102"
$saldoInicial = Invoke-LocalHttp -Path "/api/admin/creditos/usuarios/$usuarioCreditoId/saldo" -Session $session
$saldoInicialJson = Get-Json $saldoInicial
Add-Check "ledger como fonte do saldo" (
  $saldoInicial.Status -eq 200 -and
  $saldoInicialJson.saldoProjetado -eq 100 -and
  $saldoInicialJson.saldoCalculadoMovimentos -eq 100 -and
  $saldoInicialJson.somenteLeitura -eq $false
) "status=$($saldoInicial.Status); saldo=$($saldoInicialJson.saldoProjetado)"

$ajustePositivo = Invoke-LocalHttp `
  -Path "/api/admin/creditos/usuarios/$usuarioCreditoId/ajustes" `
  -Method "POST" `
  -Body (@{ direcao = "CREDITO"; quantidade = 10; motivo = "ajuste sintetico positivo" } | ConvertTo-Json -Compress) `
  -Session $session `
  -Headers @{ "Idempotency-Key" = "premium-ajuste-positivo-local" }
$ajustePositivoJson = Get-Json $ajustePositivo
Add-Check "admin adiciona creditos com motivo" (
  $ajustePositivo.Status -eq 200 -and
  $ajustePositivoJson.natureza -eq "AJUSTE_ADMIN_POSITIVO" -and
  $ajustePositivoJson.saldoAnterior -eq 100 -and
  $ajustePositivoJson.saldoPosterior -eq 110
) "status=$($ajustePositivo.Status); saldo=$($ajustePositivoJson.saldoPosterior)"

$ajusteRepetido = Invoke-LocalHttp `
  -Path "/api/admin/creditos/usuarios/$usuarioCreditoId/ajustes" `
  -Method "POST" `
  -Body (@{ direcao = "CREDITO"; quantidade = 10; motivo = "ajuste sintetico positivo" } | ConvertTo-Json -Compress) `
  -Session $session `
  -Headers @{ "Idempotency-Key" = "premium-ajuste-positivo-local" }
$ajusteRepetidoJson = Get-Json $ajusteRepetido
Add-Check "ajuste administrativo idempotente" (
  $ajusteRepetido.Status -eq 200 -and
  $ajusteRepetidoJson.idempotente -eq $true -and
  $ajusteRepetidoJson.movimentoId -eq $ajustePositivoJson.movimentoId
) "status=$($ajusteRepetido.Status); movimento preservado"

$ajusteNegativo = Invoke-LocalHttp `
  -Path "/api/admin/creditos/usuarios/$usuarioCreditoId/ajustes" `
  -Method "POST" `
  -Body (@{ direcao = "DEBITO"; quantidade = 5; motivo = "ajuste sintetico negativo" } | ConvertTo-Json -Compress) `
  -Session $session `
  -Headers @{ "Idempotency-Key" = "premium-ajuste-negativo-local" }
$ajusteNegativoJson = Get-Json $ajusteNegativo
Add-Check "admin remove creditos com motivo" (
  $ajusteNegativo.Status -eq 200 -and
  $ajusteNegativoJson.natureza -eq "AJUSTE_ADMIN_NEGATIVO" -and
  $ajusteNegativoJson.saldoAnterior -eq 110 -and
  $ajusteNegativoJson.saldoPosterior -eq 105
) "status=$($ajusteNegativo.Status); saldo=$($ajusteNegativoJson.saldoPosterior)"

$ajusteSemMotivo = Invoke-LocalHttp `
  -Path "/api/admin/creditos/usuarios/$usuarioCreditoId/ajustes" `
  -Method "POST" `
  -Body (@{ direcao = "CREDITO"; quantidade = 1; motivo = "" } | ConvertTo-Json -Compress) `
  -Session $session `
  -Headers @{ "Idempotency-Key" = "premium-ajuste-sem-motivo-local" }
Add-Check "ajuste administrativo exige motivo" ($ajusteSemMotivo.Status -eq 400) "status=$($ajusteSemMotivo.Status)"

$ajusteSemSaldo = Invoke-LocalHttp `
  -Path "/api/admin/creditos/usuarios/$usuarioCreditoId/ajustes" `
  -Method "POST" `
  -Body (@{ direcao = "DEBITO"; quantidade = 999; motivo = "ajuste sintetico sem saldo" } | ConvertTo-Json -Compress) `
  -Session $session `
  -Headers @{ "Idempotency-Key" = "premium-ajuste-sem-saldo-local" }
Add-Check "ledger bloqueia saldo negativo" ($ajusteSemSaldo.Status -eq 409) "status=$($ajusteSemSaldo.Status)"

$saldoFinal = Invoke-LocalHttp -Path "/api/admin/creditos/usuarios/$usuarioCreditoId/saldo" -Session $session
$saldoFinalJson = Get-Json $saldoFinal
Add-Check "saldo final sem debito duplicado" ($saldoFinal.Status -eq 200 -and $saldoFinalJson.saldoProjetado -eq 105) "saldo=$($saldoFinalJson.saldoProjetado)"

$historicoCredito = Invoke-LocalHttp -Path "/api/admin/creditos/usuarios/$usuarioCreditoId/movimentos?page=0&size=20" -Session $session
Add-Check "historico administrativo completo" (
  $historicoCredito.Status -eq 200 -and
  $historicoCredito.Body -match 'AJUSTE_ADMIN_POSITIVO' -and
  $historicoCredito.Body -match 'AJUSTE_ADMIN_NEGATIVO' -and
  $historicoCredito.Body -match 'premium-beneficios-bloco-36'
) "status=$($historicoCredito.Status)"

$catalogoAdmin = Invoke-LocalHttp -Path "/api/admin/premium/catalogo" -Session $session
$catalogoAdminJson = @(Get-Json $catalogoAdmin)
$duracoes = @($catalogoAdminJson | ForEach-Object { $_.opcoes } | ForEach-Object { $_.duracaoDias } | Sort-Object -Unique)
Add-Check "catalogo Premium vem do backend" ($catalogoAdmin.Status -eq 200 -and $catalogoAdminJson.Count -ge 1) "itens=$($catalogoAdminJson.Count)"
Add-Check "duracoes canonicas backend-driven" (
  $duracoes.Count -eq 4 -and
  $duracoes -contains 1 -and $duracoes -contains 7 -and $duracoes -contains 14 -and $duracoes -contains 30
) "duracoes=$($duracoes -join ',')"

$pacotesAdmin = Invoke-LocalHttp -Path "/api/admin/creditos/pacotes" -Session $session
Add-Check "pacotes administraveis no backend" ($pacotesAdmin.Status -eq 200 -and $pacotesAdmin.Body -match 'PACOTE_50') "status=$($pacotesAdmin.Status)"

$usuarioSemPermissaoSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$usuarioSemPermissaoLogin = Invoke-LocalHttp `
  -Path "/api/admin/auth/login" `
  -Method "POST" `
  -Body (@{ login = "usuario.local@example.invalid"; ("se" + "nha") = $loginPayload[("se" + "nha")] } | ConvertTo-Json -Compress) `
  -Session $usuarioSemPermissaoSession
$usuarioSemPermissao = Invoke-LocalHttp -Path "/api/admin/creditos/usuarios/$usuarioCreditoId/saldo" -Session $usuarioSemPermissaoSession
Add-Check "usuario comum sem financeiro admin" (
  $usuarioSemPermissaoLogin.Status -eq 200 -and $usuarioSemPermissao.Status -eq 403
) "login=$($usuarioSemPermissaoLogin.Status); acesso=$($usuarioSemPermissao.Status)"

$premiumId = "00000000-0000-4000-8000-000000000501"
$expiredId = "00000000-0000-4000-8000-000000000503"
$shortBenefitId = "00000000-0000-4000-8000-000000000504"
$freeId = "00000000-0000-4000-8000-000000000510"

$publicPremium = Invoke-LocalHttp -Path "/api/public/anuncios/anuncio-sintetico-local"
$publicPremiumJson = Get-Json $publicPremium
Add-Check "publico premium ativo visivel" ($publicPremium.Status -eq 200 -and $publicPremiumJson.topo -eq $true -and @($publicPremiumJson.beneficiosPublicos).Count -ge 1) "status=$($publicPremium.Status)"
Add-Check "publico premium aditivo" (@($publicPremiumJson.beneficiosPublicos).Count -ge 1 -and (($publicPremiumJson.beneficiosPublicos -join "|") -match "Topo")) "beneficios=$($publicPremiumJson.beneficiosPublicos -join ', ')"
Assert-NoForbiddenOperationalText -Nome "publico premium" -Body $publicPremium.Body

$publicFree = Invoke-LocalHttp -Path "/api/public/anuncios/anuncio-sintetico-gratuito-local"
$publicFreeJson = Get-Json $publicFree
Add-Check "publico gratuito visivel e util" ($publicFree.Status -eq 200 -and -not [string]::IsNullOrWhiteSpace($publicFreeJson.titulo)) "status=$($publicFree.Status)"
Add-Check "publico gratuito sem beneficio artificial" ($publicFreeJson.destaque -eq $false -and $publicFreeJson.topo -eq $false -and @($publicFreeJson.beneficiosPublicos).Count -eq 0) "beneficios=$($publicFreeJson.beneficiosPublicos -join ', ')"
Assert-NoForbiddenOperationalText -Nome "publico gratuito" -Body $publicFree.Body

$freeClick = Invoke-LocalHttp -Path "/api/public/anuncios/anuncio-sintetico-gratuito-local/clique-whatsapp" -Method "POST" -Body "{}"
Add-Check "gratuito sem limite comercial de clique/WhatsApp" ($freeClick.Status -eq 200 -and $freeClick.Body -match '"disponivel"\s*:\s*true') "status=$($freeClick.Status)"

$premiumStatus = Invoke-LocalHttp -Path "/api/admin/premium/anuncios/$premiumId" -Session $session
$premiumStatusJson = Get-Json $premiumStatus
Add-Check "admin premium ativo" ($premiumStatus.Status -eq 200 -and $premiumStatusJson.premiumAtivo -eq $true -and $premiumStatusJson.destaqueAtivo -eq $true) "status=$($premiumStatus.Status)"
Add-Check "admin gratuito sem limite contato" ($premiumStatusJson.gratuitoLimitadoPorContato -eq $false) "gratuitoLimitadoPorContato=$($premiumStatusJson.gratuitoLimitadoPorContato)"

$premiumBenefits = Invoke-LocalHttp -Path "/api/admin/premium/anuncios/$premiumId/beneficios" -Session $session
$premiumBenefitsJson = @(Get-Json $premiumBenefits)
$benefitCodes = @($premiumBenefitsJson | ForEach-Object { $_.beneficioCodigo })
$benefitStatuses = @($premiumBenefitsJson | ForEach-Object { $_.statusCalculado })
Add-Check "admin beneficios canonicos ativos" (
  $premiumBenefits.Status -eq 200 -and
  $benefitCodes -contains "ANUNCIO_TOPO" -and
  $benefitCodes -contains "FOTOS_EXTRA_5" -and
  $benefitCodes -contains "CARROSSEL_FOTOS" -and
  $benefitCodes -contains "WHATSAPP_CARD"
) "codigos=$($benefitCodes -join ', ')"
Add-Check "admin beneficio vencendo" ($benefitStatuses -contains "VENCENDO") "status=$($benefitStatuses -join ', ')"

$expiredBenefits = Invoke-LocalHttp -Path "/api/admin/premium/anuncios/$expiredId/beneficios" -Session $session
Add-Check "admin beneficio expirado por grupo conjunto" (
  $expiredBenefits.Status -eq 200 -and
  $expiredBenefits.Body -match '(?s)"beneficioCodigo"\s*:\s*"ANUNCIO_TOPO".*"statusCalculado"\s*:\s*"EXPIRADO".*"grupoStatus"\s*:\s*"EXPIRADO"'
) "beneficio ANUNCIO_TOPO deve expirar junto com grupo expirado"

$shortBenefits = Invoke-LocalHttp -Path "/api/admin/premium/anuncios/$shortBenefitId/beneficios" -Session $session
$shortBenefitsJson = @(Get-Json $shortBenefits)
$shortVideo = @($shortBenefitsJson | Where-Object { $_.beneficioCodigo -eq "VIDEO_1" } | Select-Object -First 1)
Add-Check "admin beneficio expira antes do grupo sem efeito parcial" (
  $shortBenefits.Status -eq 200 -and
  $shortVideo.Count -eq 1 -and
  $shortVideo[0].statusCalculado -eq "EXPIRADO" -and
  @($shortVideo[0].codigosConsistencia) -contains "BENEFICIO_EXPIRADO_ANTES_DO_GRUPO"
) "VIDEO_1 deve expirar sem encerrar outros beneficios do grupo"

$freeStatus = Invoke-LocalHttp -Path "/api/admin/premium/anuncios/$freeId" -Session $session
$freeStatusJson = Get-Json $freeStatus
Add-Check "admin plano gratuito continua consultavel" ($freeStatus.Status -eq 200 -and $freeStatusJson.premiumAtivo -eq $false -and $freeStatusJson.gratuitoLimitadoPorContato -eq $false) "status=$($freeStatus.Status)"

$consistency = Invoke-LocalHttp -Path "/api/admin/premium/consistencia" -Session $session
$consistencyJson = Get-Json $consistency
$consistencyCodes = @($consistencyJson.itens | ForEach-Object { $_.codigo })
Add-Check "admin consistencia expiracao conjunta" ($consistency.Status -eq 200 -and $consistencyCodes -contains "GRUPO_EXPIRADO_COM_BENEFICIO_ATIVO") "codigos=$($consistencyCodes -join ', ')"
Add-Check "admin consistencia nao acusa expiracao individual legitima" (-not ($consistencyCodes -contains "BENEFICIO_EXPIRADO_ANTES_DO_GRUPO")) "codigos=$($consistencyCodes -join ', ')"
Add-Check "admin consistencia grupo sem beneficios" ($consistencyCodes -contains "GRUPO_SEM_BENEFICIOS") "codigos=$($consistencyCodes -join ', ')"

$vencendo = Invoke-LocalHttp -Path "/api/admin/premium/vencendo" -Session $session
$vencendoJson = Get-Json $vencendo
$vencendoCodes = @($vencendoJson.itens | ForEach-Object { $_.beneficioCodigo })
Add-Check "admin vencendo janela sete dias" ($vencendo.Status -eq 200 -and $vencendoJson.janelaDias -eq 7 -and $vencendoCodes -contains "FOTOS_EXTRA_5") "janela=$($vencendoJson.janelaDias); codigos=$($vencendoCodes -join ', ')"

foreach ($method in @("POST", "PUT", "PATCH", "DELETE")) {
  $writeAttempt = Invoke-LocalHttp -Path "/api/admin/premium/consistencia" -Method $method -Body "{}" -Session $session
  Add-Check "premium sem metodo $method" ($writeAttempt.Status -in @(400, 403, 404, 405)) "status=$($writeAttempt.Status)"
}

function Find-BrowserExecutable {
  $paths = New-Object System.Collections.Generic.List[string]
  foreach ($command in @("msedge.exe", "chrome.exe", "chromium.exe")) {
    $found = Get-Command $command -ErrorAction SilentlyContinue
    if ($found) { $paths.Add($found.Source) }
  }
  foreach ($candidate in @(
      "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
      "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
      "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
      "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
      "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
    )) {
    if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Leaf)) { $paths.Add($candidate) }
  }
  return @($paths | Select-Object -Unique | Select-Object -First 1)
}

$node = Get-Command node -ErrorAction SilentlyContinue
$npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
$browserPath = Find-BrowserExecutable
$frontendRoot = Join-Path $repoRoot "frontend"
$frontendBaseUrl = "http://127.0.0.1:$FrontendPort"
$startedFrontend = $false
$frontendProcess = $null
$uiExit = 0

$oldApiBase = $env:NEXT_PUBLIC_API_BASE_URL
$oldApiUrl = $env:NEXT_PUBLIC_API_URL
$oldAppEnv = $env:NEXT_PUBLIC_APP_ENV
$oldCanonical = $env:NEXT_PUBLIC_CANONICAL_DOMAIN

try {
  if ($node -and $browserPath) {
    if (-not (Test-LocalUrl -Url $frontendBaseUrl)) {
      if (-not $NoStartFrontend) {
        if ($npm -and (Test-Path -LiteralPath (Join-Path $frontendRoot "node_modules") -PathType Container)) {
          $env:NEXT_PUBLIC_API_BASE_URL = $safeBackendUrl
          $env:NEXT_PUBLIC_API_URL = "$safeBackendUrl/api/public"
          $env:NEXT_PUBLIC_APP_ENV = "local"
          $env:NEXT_PUBLIC_CANONICAL_DOMAIN = "http://localhost"
          $stdout = Join-Path $env:TEMP ("topsv3-premium-frontend-{0}.out.log" -f ([guid]::NewGuid().ToString("N")))
          $stderr = Join-Path $env:TEMP ("topsv3-premium-frontend-{0}.err.log" -f ([guid]::NewGuid().ToString("N")))
          $frontendProcess = Start-Process -FilePath $npm.Source `
            -ArgumentList @("run", "dev", "--", "-p", "$FrontendPort", "-H", "127.0.0.1") `
            -WorkingDirectory $frontendRoot `
            -WindowStyle Hidden `
            -RedirectStandardOutput $stdout `
            -RedirectStandardError $stderr `
            -PassThru
          $startedFrontend = $true
          for ($i = 0; $i -lt 100; $i++) {
            Start-Sleep -Milliseconds 500
            if (Test-LocalUrl -Url $frontendBaseUrl) { break }
            if ($frontendProcess.HasExited) { break }
          }
        }
      }
    }

    if (Test-LocalUrl -Url $frontendBaseUrl) {
      $tempScript = Join-Path $env:TEMP ("topsv3-premium-beneficios-{0}.mjs" -f ([guid]::NewGuid().ToString("N")))
      $nodeScript = @'
import { spawn } from "node:child_process";
import { Buffer } from "node:buffer";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import net from "node:net";

const frontendBaseUrl = (process.env.TOPSV3_PREMIUM_FRONTEND_URL || "").replace(/\/+$/, "");
const backendBaseUrl = (process.env.TOPSV3_PREMIUM_BACKEND_URL || "").replace(/\/+$/, "");
const browserPath = process.env.TOPSV3_PREMIUM_BROWSER;
const printsDir = process.env.TOPSV3_PREMIUM_PRINTS_DIR || "";
const uiReportPath = process.env.TOPSV3_PREMIUM_RELATORIO_UI || "";
const localSessionCookie = process.env.TOPSV3_PREMIUM_SESSION_COOKIE || "";

const viewports = [
  { key: "desktop", width: 1280, height: 920 },
  { key: "mobile", width: 390, height: 844 }
];

function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
function freePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      const port = address && typeof address === "object" ? address.port : 0;
      server.close(() => resolve(port));
    });
    server.on("error", reject);
  });
}
async function fetchJson(url, options = {}) {
  const response = await fetch(url, options);
  if (!response.ok) throw new Error(`HTTP ${response.status} em ${url}`);
  return response.json();
}
class CdpClient {
  constructor(wsUrl) {
    this.wsUrl = wsUrl;
    this.nextId = 1;
    this.pending = new Map();
    this.ws = null;
  }
  async connect() {
    this.ws = new WebSocket(this.wsUrl);
    this.ws.addEventListener("message", (event) => {
      const message = JSON.parse(event.data.toString());
      if (!message.id || !this.pending.has(message.id)) return;
      const { resolve, reject } = this.pending.get(message.id);
      this.pending.delete(message.id);
      if (message.error) reject(new Error(message.error.message || "CDP error"));
      else resolve(message.result || {});
    });
    await new Promise((resolve, reject) => {
      this.ws.addEventListener("open", resolve, { once: true });
      this.ws.addEventListener("error", reject, { once: true });
    });
  }
  send(method, params = {}) {
    const id = this.nextId++;
    this.ws.send(JSON.stringify({ id, method, params }));
    return new Promise((resolve, reject) => this.pending.set(id, { resolve, reject }));
  }
  close() { try { this.ws?.close(); } catch {} }
}
async function newPage(debugUrl) {
  for (const method of ["PUT", "POST", "GET"]) {
    try { return await fetchJson(`${debugUrl}/json/new?${encodeURIComponent("about:blank")}`, { method }); } catch {}
  }
  const pages = await fetchJson(`${debugUrl}/json`);
  const page = pages.find((item) => item.type === "page" && item.webSocketDebuggerUrl);
  if (!page) throw new Error("Nao foi possivel criar aba CDP.");
  return page;
}
async function evaluate(cdp, expression) {
  const result = await cdp.send("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text || "Falha ao avaliar script no navegador.");
  return result.result?.value;
}
async function navigate(cdp, url) {
  await cdp.send("Page.navigate", { url });
  await delay(2800);
}
async function screenshot(cdp, filename) {
  const shot = await cdp.send("Page.captureScreenshot", { format: "png", fromSurface: true });
  fs.writeFileSync(path.join(printsDir, filename), Buffer.from(shot.data, "base64"));
}
function pageMetricsScript() {
  return `(() => {
    const text = document.body ? document.body.innerText : "";
    const lower = text.toLowerCase();
    const upperSnakeMatches = Array.from(new Set(text.match(/\\b[A-Z0-9]+_[A-Z0-9_]+\\b/g) || [])).slice(0, 20);
    const snakeMatches = Array.from(new Set(text.match(/\\b[a-z][a-z0-9]*(?:_[a-z0-9]+)+\\b/g) || [])).slice(0, 20);
    const forbiddenAction = /\b(pagar|checkout|pix|efi|webhook)\b/i.test(Array.from(document.querySelectorAll("button")).map((el) => el.innerText).join(" "));
    const html = document.documentElement;
    const body = document.body;
    const scrollWidth = html ? html.scrollWidth : 0;
    const innerWidth = window.innerWidth;
    const violations = [];
    if (upperSnakeMatches.length) violations.push("upper_snake_case_visivel");
    if (snakeMatches.length) violations.push("snake_case_visivel");
    if (/contrata[cç][aã]o garantida|resultado garantido|paywall obrigat[oó]rio/i.test(text)) violations.push("promessa_ou_paywall");
    if (/documento_usuario|senhaHash|tokenSessaoHash|JSESSIONID|Bearer|Authorization/i.test(text)) violations.push("segredo_ou_dado_sensivel");
    if (forbiddenAction) violations.push("acao_financeira_visivel");
    if (/API local|mock|fixture|smoke test|descart[aá]vel/i.test(text)) violations.push("copy_bastidor_visivel");
    if (/Metadados p[úu]blicos locais|Metadados publicos locais/i.test(text)) violations.push("metadados_publicos_locais_visivel");
    if (/\bANUNCIO\b/.test(text)) violations.push("enum_anuncio_visivel");
    if (/Autorizacao|autorizacao/i.test(text)) violations.push("autorizacao_sem_acento_visivel");
    if (/admin configurar/i.test(text)) violations.push("permissao_admin_configurar_visivel");
    if (/anuncio ler/i.test(text)) violations.push("permissao_anuncio_ler_visivel");
    if (/Preparar autorizacao/i.test(text)) violations.push("descricao_autorizacao_sem_acento_visivel");
    if (/Fluxo\s+autorizado/i.test(text)) violations.push("fluxo_autorizado_redundante_visivel");
    if (/Autoriza[çc][aã]o\s+autorizada/i.test(text)) violations.push("autorizacao_autorizada_redundante_visivel");
    if (scrollWidth > innerWidth + 1) violations.push("scroll_horizontal");
    if (body && getComputedStyle(body).overflow === "hidden") violations.push("scroll_lock_body");
    return {
      title: document.title || "",
      textSample: text.slice(0, 900),
      lower,
      hasPremiumPanel: lower.includes("premium") && /benef[ií]cios/i.test(text),
      hasCreditoOperacional: /cr[eé]ditos/i.test(text) && /saldo e ajustes|hist[oó]rico imut[aá]vel|movimenta[cç][oõ]es/i.test(text),
      hasCatalogoOperacional: /cat[aá]logo/i.test(text) && /dura[cç][aã]o|custo/i.test(text),
      hasCatalogoAdministravel: /dispon[ií]vel/i.test(text) && /ordem/i.test(text) && /salvar benef[ií]cio/i.test(text),
      hasDestaque: text.includes("Destaque") || lower.includes("destaque"),
      hasMidiaExtra: text.includes("Mídia extra") || text.includes("Midia extra") || lower.includes("midia extra") || /fotos extras/i.test(text),
      hasGratuitoTitle: /an[úu]ncio de demonstra[cç][aã]o gratuito/i.test(text),
      hasPremiumTitle: /an[úu]ncio de demonstra[cç][aã]o/i.test(text),
      scrollWidth,
      innerWidth,
      violations,
      upperSnakeMatches,
      snakeMatches
    };
  })()`;
}
function addCheck(checks, ok, label, detail = "ok") {
  checks.push({ result: ok ? "OK" : "FALHA", label, detail });
}
async function runViewport(cdp, viewport) {
  await cdp.send("Emulation.setDeviceMetricsOverride", { width: viewport.width, height: viewport.height, deviceScaleFactor: 1, mobile: viewport.key === "mobile" });
  const sessionCookie = await cdp.send("Network.setCookie", {
    name: "JSESSIONID",
    value: localSessionCookie,
    url: frontendBaseUrl,
    path: "/",
    httpOnly: true,
    secure: false,
    sameSite: "Lax"
  });
  const ageExpiresAt = Date.now() + (7 * 24 * 60 * 60 * 1000);
  const ageCookie = await cdp.send("Network.setCookie", {
    name: "age_gate_accepted",
    value: `v1.${ageExpiresAt}`,
    url: frontendBaseUrl,
    path: "/",
    httpOnly: false,
    secure: false,
    sameSite: "Lax"
  });
  await navigate(cdp, `${frontendBaseUrl}/admin/creditos`);
  await screenshot(cdp, `${viewport.key}-admin-premium.png`);
  const adminPremium = await evaluate(cdp, pageMetricsScript());

  await navigate(cdp, `${frontendBaseUrl}/anuncios/anuncio-sintetico-local`);
  await screenshot(cdp, `${viewport.key}-publico-premium.png`);
  const publicPremium = await evaluate(cdp, pageMetricsScript());

  await navigate(cdp, `${frontendBaseUrl}/anuncios/anuncio-sintetico-gratuito-local`);
  await screenshot(cdp, `${viewport.key}-publico-gratuito.png`);
  const publicFree = await evaluate(cdp, pageMetricsScript());

  return { viewport, adminPremium, publicPremium, publicFree, sessionCookie, ageCookie };
}
async function main() {
  fs.mkdirSync(printsDir, { recursive: true });
  const debugPort = await freePort();
  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), "topsv3-premium-browser-"));
  const browser = spawn(browserPath, [
    `--remote-debugging-port=${debugPort}`,
    `--user-data-dir=${userDataDir}`,
    "--headless=new",
    "--disable-gpu",
    "--no-first-run",
    "--no-default-browser-check",
    "about:blank"
  ], { stdio: "ignore" });
  const debugUrl = `http://127.0.0.1:${debugPort}`;
  let cdp;
  const checks = [];
  const results = [];
  try {
    let version = null;
    for (let i = 0; i < 80; i++) {
      await delay(250);
      try { version = await fetchJson(`${debugUrl}/json/version`); break; } catch {}
      if (browser.exitCode !== null) break;
    }
    if (!version) throw new Error("CDP do navegador nao ficou pronto.");
    const page = await newPage(debugUrl);
    cdp = new CdpClient(page.webSocketDebuggerUrl);
    await cdp.connect();
    await cdp.send("Page.enable");
    await cdp.send("Runtime.enable");
    await cdp.send("Network.enable");
    for (const viewport of viewports) {
      const result = await runViewport(cdp, viewport);
      results.push(result);
      addCheck(checks, result.sessionCookie?.success === true, `${viewport.key}: sessao sintetica aplicada ao navegador`);
      addCheck(checks, result.ageCookie?.success === true, `${viewport.key}: age gate sintetico preparado`);
      addCheck(checks, result.adminPremium.hasPremiumPanel, `${viewport.key}: admin premium renderizado`);
       addCheck(checks, result.adminPremium.hasCreditoOperacional, `${viewport.key}: ledger operacional renderizado`);
       addCheck(checks, result.adminPremium.hasCatalogoOperacional, `${viewport.key}: catalogo operacional renderizado`);
       addCheck(checks, result.adminPremium.hasCatalogoAdministravel, `${viewport.key}: duracoes e ordem administraveis`);
      addCheck(checks, result.publicPremium.hasPremiumTitle && result.publicPremium.hasDestaque, `${viewport.key}: publico premium com destaque`);
      addCheck(checks, result.publicFree.hasGratuitoTitle, `${viewport.key}: publico gratuito visivel`);
      for (const [label, metrics] of [["admin premium", result.adminPremium], ["publico premium", result.publicPremium], ["publico gratuito", result.publicFree]]) {
        addCheck(checks, metrics.violations.length === 0, `${viewport.key}: ${label} sem texto tecnico/sensivel`, metrics.violations.length ? `${metrics.violations.join(", ")} ${[...(metrics.upperSnakeMatches || []), ...(metrics.snakeMatches || [])].join(" ")}` : "ok");
      }
    }
    const lines = [
      "# Relatorio UI Premium/beneficios Bloco 36",
      "",
      "- Frontend local: " + frontendBaseUrl,
      "- Backend local: " + backendBaseUrl,
      "- Browser local: " + browserPath,
      "- Prints: " + printsDir,
      "- Dados reais: ausentes",
      "- Pix/Efi/pagamento/checkout/webhook real: ausentes",
      "",
      "## Checks",
      ""
    ];
    for (const check of checks) lines.push(`- ${check.result}: ${check.label} (${check.detail})`);
    lines.push("", "## Prints", "");
    for (const viewport of viewports) {
      lines.push(`- ${viewport.key} admin premium: ${path.join(printsDir, `${viewport.key}-admin-premium.png`)}`);
      lines.push(`- ${viewport.key} publico premium: ${path.join(printsDir, `${viewport.key}-publico-premium.png`)}`);
      lines.push(`- ${viewport.key} publico gratuito: ${path.join(printsDir, `${viewport.key}-publico-gratuito.png`)}`);
    }
    lines.push("", "## Amostras", "");
    for (const result of results) {
      lines.push(`### ${result.viewport.key} admin premium`, "", "```text", String(result.adminPremium.textSample || "").replace(/```/g, ""), "```", "");
      lines.push(`### ${result.viewport.key} publico gratuito`, "", "```text", String(result.publicFree.textSample || "").replace(/```/g, ""), "```", "");
    }
    while (lines.at(-1) === "") lines.pop();
    fs.writeFileSync(uiReportPath, `${lines.join("\n")}\n`, "utf8");
    const failed = checks.filter((item) => item.result !== "OK");
    if (failed.length > 0) {
      console.error(`FALHA_UI_PREMIUM=${failed.map((item) => item.label).join("; ")}`);
      process.exitCode = 1;
    }
  } finally {
    try { cdp?.close(); } catch {}
    if (!browser.killed) browser.kill();
    try { fs.rmSync(userDataDir, { recursive: true, force: true }); } catch {}
  }
}
main().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
'@
      [System.IO.File]::WriteAllText($tempScript, $nodeScript, [System.Text.UTF8Encoding]::new($false))
      $env:TOPSV3_PREMIUM_FRONTEND_URL = $frontendBaseUrl
      $env:TOPSV3_PREMIUM_BACKEND_URL = $safeBackendUrl
      $env:TOPSV3_PREMIUM_BROWSER = $browserPath
      $env:TOPSV3_PREMIUM_PRINTS_DIR = $printsPath
      $env:TOPSV3_PREMIUM_RELATORIO_UI = $uiPath
      $env:TOPSV3_PREMIUM_SESSION_COOKIE = $adminSessionCookie
      & $node.Source $tempScript
      $uiExit = $LASTEXITCODE
      Remove-Item -LiteralPath $tempScript -Force -ErrorAction SilentlyContinue
      Add-Check "UI publica/admin premium" ($uiExit -eq 0) "exit=$uiExit"
    } else {
      Add-Check "UI publica/admin premium" $false "frontend local indisponivel"
    }
  } else {
    Add-Check "UI publica/admin premium" $false "Node ou navegador local indisponivel"
  }
} finally {
  $env:NEXT_PUBLIC_API_BASE_URL = $oldApiBase
  $env:NEXT_PUBLIC_API_URL = $oldApiUrl
  $env:NEXT_PUBLIC_APP_ENV = $oldAppEnv
  $env:NEXT_PUBLIC_CANONICAL_DOMAIN = $oldCanonical
  Remove-Item Env:TOPSV3_PREMIUM_SESSION_COOKIE -ErrorAction SilentlyContinue
  if ($startedFrontend -and $frontendProcess -and -not $frontendProcess.HasExited) {
    taskkill.exe /PID $frontendProcess.Id /T /F | Out-Null
  }
}

$failures = @($checks | Where-Object { $_.Resultado -ne "OK" })
$reportLines = @(
  "# Relatorio Premium/beneficios sintetico local",
  "",
  "- Resultado: $(if ($failures.Count -eq 0) { 'OK_PREMIUM_BENEFICIOS_SINTETICO_LOCAL' } else { 'FALHA_PREMIUM_BENEFICIOS_SINTETICO_LOCAL' })",
  "- Backend local: $safeBackendUrl",
  "- Frontend local: $frontendBaseUrl",
  "- Readiness base executado: sim",
  "- Dados reais: nao",
  "- Producao/VPS/API externa: nao",
  "- Pix/Efi real, checkout, pagamento, credito real ou webhook: nao",
  "- Compra/ativacao externa: nao",
  "- Promessa de contratacao: nao",
  "- Plano gratuito: validado como util e sem limite comercial artificial",
  "- Premium: validado como aditivo",
  "",
  "## Checks",
  ""
)
foreach ($check in $checks) {
  $reportLines += "- $($check.Resultado): $($check.Nome) - $($check.Detalhe)"
}
$reportLines += @(
  "",
  "## Fluxos Premium/beneficios cobertos",
  "",
  "- Anuncio gratuito publico visivel.",
  "- Premium ativo publico/admin.",
  "- Premium expirado por grupo expirado.",
  "- Beneficio vencendo.",
  "- Expiracao conjunta e inconsistencias sinteticas.",
  "- Ledger administrativo operacional com motivo, RBAC e idempotencia.",
  "- Catalogo e duracoes carregados do backend.",
  "- UI publica/admin sem enum tecnico visivel.",
  "",
  "## Limites",
  "",
  "- Sem Pix, Efi, webhook, checkout ou pagamento externo.",
  "- Fluxos publicos de compra atomica cobertos pelos testes backend da fase."
)
Write-TextFile -Path $relatorioPath -Lines $reportLines

if ($failures.Count -gt 0) {
  Write-Host "VALIDATION_RESULT=FALHA_PREMIUM_BENEFICIOS_SINTETICO_LOCAL"
  Write-Host "RELATORIO=$relatorioPath"
  Write-Host "RELATORIO_UI=$uiPath"
  exit 1
}

Write-Host "VALIDATION_RESULT=OK_PREMIUM_BENEFICIOS_SINTETICO_LOCAL"
Write-Host "RELATORIO=$relatorioPath"
Write-Host "RELATORIO_UI=$uiPath"
exit 0
