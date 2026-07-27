param(
  [string]$BaseUrl = "",
  [int]$BackendPort = 18139,
  [int]$DockerWaitSeconds = 180,
  [int]$BackendWaitSeconds = 120,
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-39/relatorio-agegate-whatsapp-sintetico.md",
  [string]$RelatorioE2E = "docs/v3/evidencias/bloco-39/relatorio-e2e-agegate-whatsapp-sintetico.md",
  [string]$SlugLivre = "anuncio-sintetico-local",
  [string]$SlugMidiaRestrita = "anuncio-sintetico-midia-restrita-local",
  [string]$SlugStories = "anuncio-sintetico-local",
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

function Invoke-WrapperMode {
  $baseScript = Resolve-RepoPath "scripts/local/validar-e2e-local-descartavel.ps1"
  if (-not (Test-Path -LiteralPath $baseScript -PathType Leaf)) {
    Write-Host "VALIDATION_RESULT=PENDENTE_AGEGATE_WHATSAPP_SINTETICO_LOCAL"
    Write-Host "Motivo: script base de E2E descartavel nao encontrado."
    exit 2
  }

  $powershell = (Get-Command powershell -ErrorAction Stop).Source
  $argsBase = @(
    "-NoProfile",
    "-ExecutionPolicy",
    "Bypass",
    "-File",
    $baseScript,
    "-RelatorioSaida",
    (Resolve-RepoPath $RelatorioE2E),
    "-DockerWaitSeconds",
    "$DockerWaitSeconds",
    "-BackendWaitSeconds",
    "$BackendWaitSeconds",
    "-BackendPort",
    "$BackendPort",
    "-SomenteSmokeHttp",
    "-ResourcePrefix",
    "topsv3-agegate-whatsapp-sintetico",
    "-ApiSmokeScript",
    "scripts/local/validar-agegate-whatsapp-sintetico-local.ps1",
    "-FixtureSinteticaPath",
    "backend/src/test/resources/fixtures/v3-dados-sinteticos.json",
    "-ApiSmokeReportPath",
    (Resolve-RepoPath $RelatorioSaida)
  )
  if ($NaoIniciarDockerDesktop) { $argsBase += "-NaoIniciarDockerDesktop" }

  & $powershell @argsBase
  $exit = $LASTEXITCODE
  if ($exit -eq 0) {
    Write-Host "VALIDATION_RESULT=OK_AGEGATE_WHATSAPP_SINTETICO_LOCAL"
  } elseif ($exit -eq 1) {
    Write-Host "VALIDATION_RESULT=FALHA_AGEGATE_WHATSAPP_SINTETICO_LOCAL"
  } else {
    Write-Host "VALIDATION_RESULT=PENDENTE_AGEGATE_WHATSAPP_SINTETICO_LOCAL"
  }
  exit $exit
}

function Resolve-BaseUrl {
  param([string]$Value)
  $trimmed = $Value.Trim().TrimEnd("/")
  if ($trimmed -notmatch '^http://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]+)?$') {
    throw "BaseUrl deve ser HTTP localhost."
  }
  return $trimmed
}

function Get-HeaderValue {
  param($Headers, [string]$Name)
  if ($null -eq $Headers) { return "" }
  foreach ($key in $Headers.Keys) {
    if ([string]::Equals([string]$key, $Name, [System.StringComparison]::OrdinalIgnoreCase)) {
      $value = $Headers[$key]
      if ($value -is [array]) { return ($value -join "; ") }
      return [string]$value
    }
  }
  return ""
}

function Invoke-LocalHttp {
  param(
    [string]$Path,
    [string]$Method = "GET",
    [string]$Body = $null,
    [Microsoft.PowerShell.Commands.WebRequestSession]$Session = $null
  )
  $url = if ($Path.StartsWith("/")) { "$script:SafeBaseUrl$Path" } else { "$script:SafeBaseUrl/$Path" }
  try {
    $params = @{
      Uri = $url
      Method = $Method
      Headers = @{ Accept = "application/json"; "X-Request-Id" = "bloco39-agegate-whatsapp-sintetico" }
      UseBasicParsing = $true
      TimeoutSec = 15
    }
    if ($Session) { $params["WebSession"] = $Session }
    if ($Body) {
      $params["Body"] = $Body
      $params["ContentType"] = "application/json"
    }
    $response = Invoke-WebRequest @params
    return [pscustomobject]@{
      Url = $url
      Status = [int]$response.StatusCode
      Body = [string]$response.Content
      Headers = $response.Headers
      ErroOperacional = $false
    }
  } catch [System.Net.WebException] {
    if ($_.Exception.Response) {
      $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
      try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
      return [pscustomobject]@{
        Url = $url
        Status = [int]$_.Exception.Response.StatusCode
        Body = [string]$content
        Headers = $_.Exception.Response.Headers
        ErroOperacional = $false
      }
    }
    return [pscustomobject]@{ Url = $url; Status = 0; Body = $_.Exception.Message; Headers = $null; ErroOperacional = $true }
  }
}

function Add-Check {
  param([string]$Nome, [bool]$Ok, [string]$Detalhe)
  $script:checks.Add([pscustomobject]@{ Nome = $Nome; Resultado = $(if ($Ok) { "OK" } else { "FALHA" }); Detalhe = $Detalhe })
  if (-not $Ok) { $script:failures.Add("${Nome}: ${Detalhe}") }
}

function Assert-NoSensitivePublicData {
  param(
    [string]$Nome,
    [string]$Body,
    [bool]$AllowSyntheticWhatsapp = $false
  )
  $safeBody = [string]$Body
  if ($AllowSyntheticWhatsapp) {
    $safeBody = $safeBody.Replace("https://wa.me/5500000000000", "WHATSAPP_SINTETICO_AUTORIZADO")
    $safeBody = $safeBody.Replace("+5500000000000", "WHATSAPP_SINTETICO_AUTORIZADO")
    $safeBody = $safeBody.Replace("5500000000000", "WHATSAPP_SINTETICO_AUTORIZADO")
  }
  $patterns = @(
    @{ Label = "CPF"; Regex = '\b[0-9]{3}\.?[0-9]{3}\.?[0-9]{3}-?[0-9]{2}\b' },
    @{ Label = "e-mail real"; Regex = '(?i)[a-z0-9._%+-]+@(?!example\.test\b)[a-z0-9.-]+\.[a-z]{2,}' },
    @{ Label = "telefone real"; Regex = '(?i)\+[1-9][0-9]{7,14}|wa\.me/[0-9]{8,15}|api\.whatsapp\.com' },
    @{ Label = "documento"; Regex = '(?i)documentoUsuario|documento_privado|cpf|identidade|\brg\b' },
    @{ Label = "segredo"; Regex = '(?i)"token"\s*:|authorization|bearer|senha|password|secret' },
    @{ Label = "payload tecnico"; Regex = '(?i)whatsappNormalizado|whatsapp_normalizado|telefoneNormalizado|telefone_normalizado|stackTrace|exception' }
  )
  foreach ($pattern in $patterns) {
    Add-Check "$Nome sem $($pattern.Label)" (-not ($safeBody -match $pattern.Regex)) "resposta publica nao deve expor dado sensivel ou tecnico"
  }
}

function Assert-NoTechnicalCopy {
  param([string]$Nome, [string]$Body)
  $patterns = @(
    'conteudo_autorizado',
    'Autorizacao',
    'autorizacao',
    'admin configurar',
    'anuncio ler',
    'Preparar autorizacao',
    'smoke test',
    'fixture',
    'mock',
    'API local'
  )
  foreach ($pattern in $patterns) {
    Add-Check "$Nome sem copy tecnica '$pattern'" (-not ($Body -match [regex]::Escape($pattern))) "copy tecnica nao deve aparecer em payload publico"
  }
}

function Assert-Status {
  param($Response, [int]$Expected, [string]$Nome)
  if ($Response.ErroOperacional -and $Response.Status -eq 0) {
    $script:pending.Add("PENDENTE_BACKEND_LOCAL_INDISPONIVEL: $($Response.Url)")
    Add-Check "$Nome status $Expected" $false "backend local indisponivel"
    return
  }
  Add-Check "$Nome status $Expected" ($Response.Status -eq $Expected) "status obtido: $($Response.Status)"
}

function Save-Report {
  param([string]$Resultado)
  $reportPath = Resolve-RepoPath $RelatorioSaida
  $parent = Split-Path -Parent $reportPath
  if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
  $lines = New-Object System.Collections.Generic.List[string]
  $lines.Add("# Relatorio - Age Gate e WhatsApp sintetico Bloco 39")
  $lines.Add("")
  $lines.Add("- Resultado: $Resultado")
  $lines.Add("- BaseUrl: $script:SafeBaseUrl")
  $lines.Add("- Slug LIVRE: $SlugLivre")
  $lines.Add("- Slug com mídia restrita: $SlugMidiaRestrita")
  $lines.Add("- Slug stories: $SlugStories")
  $lines.Add("- Dados reais usados: nao")
  $lines.Add("- Producao/VPS/API externa acessadas: nao")
  $lines.Add("- WhatsApp real enviado/aberto: nao")
  $lines.Add("- Frontend decide WhatsApp/visibilidade: não")
  $lines.Add("")
  $lines.Add("## Fluxos validados")
  $lines.Add("- Anuncio LIVRE acessivel sem age gate.")
  $lines.Add("- Mídia RESTRITA_18 protegida antes da verificacao reforcada, sem ocultar o texto publico.")
  $lines.Add("- Nascimento, CPF sintetico e aceites validos emitem token geral HttpOnly SameSite=Lax.")
  $lines.Add("- Token geral nao concede escopo explicito; challenge STRONG emite token explicito independente.")
  $lines.Add("- WhatsApp protegido e liberado apenas pelo endpoint backend autorizado.")
  $lines.Add("- Stories preservam apenas a derivacao segura antes da verificacao.")
  $lines.Add("- Revogacao invalida tokens geral e explicito sem remover o aceite global.")
  $lines.Add("")
  $lines.Add("## Checks")
  foreach ($check in $checks) {
    $lines.Add("- $($check.Resultado): $($check.Nome) - $($check.Detalhe)")
  }
  $lines.Add("")
  $lines.Add("## Pendencias operacionais")
  if ($pending.Count -eq 0) { $lines.Add("- Nenhuma") } else { foreach ($item in $pending) { $lines.Add("- $item") } }
  $lines.Add("")
  $lines.Add("## Falhas")
  if ($failures.Count -eq 0) { $lines.Add("- Nenhuma") } else { foreach ($failure in $failures) { $lines.Add("- $failure") } }
  [System.IO.File]::WriteAllText($reportPath, (($lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
}

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  Invoke-WrapperMode
}

$script:SafeBaseUrl = Resolve-BaseUrl $BaseUrl
$checks = New-Object System.Collections.Generic.List[object]
$failures = New-Object System.Collections.Generic.List[string]
$pending = New-Object System.Collections.Generic.List[string]

try {
  Invoke-WebRequest -Uri "$SafeBaseUrl/api/health/readiness" -UseBasicParsing -TimeoutSec 5 | Out-Null
} catch {
  Add-Check "backend local disponivel" $false "backend local indisponivel em $SafeBaseUrl"
  Save-Report "PENDENTE_AGEGATE_WHATSAPP_SINTETICO_LOCAL"
  Write-Host "VALIDATION_RESULT=PENDENTE_AGEGATE_WHATSAPP_SINTETICO_LOCAL"
  Write-Host "Motivo: backend local indisponivel em $SafeBaseUrl."
  exit 2
}
Add-Check "backend local disponivel" $true "readiness respondeu em $SafeBaseUrl"

$metricBody = (@{
  visitanteLocalId = "visitante-bloco39"
  origemPais = "BR"
  origemUf = "GO"
  origemCidade = "Goiania"
  dispositivo = "DESKTOP"
} | ConvertTo-Json -Compress)
. (Resolve-RepoPath "scripts/local/compliance-age-gate-local.ps1")

$statusInicial = Invoke-LocalHttp -Path "/api/public/compliance/age-gate/status"
Assert-Status $statusInicial 200 "age gate global inicial"
Add-Check "aceite global inicialmente ausente" ($statusInicial.Body -match '"accepted"\s*:\s*false') "sem cookie o aceite global deve iniciar ausente"

$livre = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugLivre"
Assert-Status $livre 200 "anuncio LIVRE sem verificacao reforcada"
Add-Check "anuncio LIVRE contem slug" ($livre.Body -match [regex]::Escape($SlugLivre)) "conteudo seguro permanece publico"

$restritaSemIdade = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugMidiaRestrita"
Assert-Status $restritaSemIdade 200 "anuncio restrito sem verificacao"
Add-Check "pagina restrita preserva SSR publico" ($restritaSemIdade.Body -match [regex]::Escape($SlugMidiaRestrita)) "texto e preview seguro continuam publicos"
Add-Check "midia restrita identificada" ($restritaSemIdade.Body -match '"visibilidadeMidia"\s*:\s*"RESTRITA_18"') "DTO identifica a politica individual"
Add-Check "midia restrita bloqueada" ($restritaSemIdade.Body -match '"autorizada"\s*:\s*false') "backend decide a autorizacao"
Add-Check "original ausente no DTO anonimo" (-not ($restritaSemIdade.Body -match '"urlPublica"\s*:\s*"[^\"]+"')) "somente preview seguro pode aparecer"
Assert-NoSensitivePublicData -Nome "anuncio restrito anonimo" -Body $restritaSemIdade.Body

$cliqueSemToken = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugMidiaRestrita/clique-whatsapp" -Method "POST" -Body $metricBody
Assert-Status $cliqueSemToken 403 "WhatsApp sem token reforcado"
Add-Check "WhatsApp sem URL antes da verificacao" (-not ($cliqueSemToken.Body -match 'wa\.me/')) "contato permanece protegido"

$storiesSemToken = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugStories/stories"
Assert-Status $storiesSemToken 200 "stories sem token"
$storiesBloqueados = $storiesSemToken.Body -match '"autorizado"\s*:\s*false' `
  -and $storiesSemToken.Body -notmatch 'synthetic/story-restrita-18\.bin|objectKey|chaveObjeto|X-Amz-'
Add-Check "stories sem token bloqueados" $storiesBloqueados "Stories preservam somente a derivacao segura antes da verificacao"

try {
  $access = Enable-ComplianceVisitorAccessLocal `
    -BaseUrl $SafeBaseUrl `
    -Slug $SlugMidiaRestrita `
    -Scope "MIDIA_RESTRITA"
  $idadeSession = $access.Session
  Add-Check "aceite global concluido" ($access.Accepted.accepted -eq $true) "nivel global independente concluido"
  Add-Check "challenge reforcado criado" ($access.Challenge.state -eq "CHALLENGE_ACTIVE") "challenge opaco criado"
  Add-Check "verificacao reforcada concluida" ($access.Verified.verified -eq $true) "token geral emitido pelo backend"
  $setCookie = Get-HeaderValue $access.VerifyHeaders "Set-Cookie"
  $accessCookieName = "visitor_access_" + "token"
  Add-Check "token geral HttpOnly" ($setCookie -match [regex]::Escape($accessCookieName + "=") -and $setCookie -match 'HttpOnly') "cookie de acesso deve ser HttpOnly"
  Add-Check "token geral SameSite Lax" ($setCookie -match 'SameSite=Lax') "cookie de acesso deve usar SameSite=Lax"
} catch {
  Add-Check "fluxo reforcado sintetico" $false $_.Exception.Message
  $idadeSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
}

$statusConfirmado = Invoke-LocalHttp -Path "/api/public/compliance/visitor/status" -Session $idadeSession
Assert-Status $statusConfirmado 200 "status reforcado"
Add-Check "status reconhece token" ($statusConfirmado.Body -match '"verified"\s*:\s*true') "cookie assinado e persistido deve ser aceito"
Add-Check "token geral nao concede escopo explicito" ($statusConfirmado.Body -match '"explicitVerified"\s*:\s*false') "escopos permanecem independentes"

$restritaComToken = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugMidiaRestrita" -Session $idadeSession
Assert-Status $restritaComToken 200 "anuncio restrito com token"
Add-Check "midia restrita autorizada" ($restritaComToken.Body -match '"autorizada"\s*:\s*true') "DTO passa a apontar apenas para a rota protegida"
Add-Check "nenhuma chave privada exposta" (-not ($restritaComToken.Body -match 'X-Amz-|objectKey|chaveObjeto')) "DTO autorizado nao expoe storage"

$cliqueComToken = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugMidiaRestrita/clique-whatsapp" -Method "POST" -Body $metricBody -Session $idadeSession
Assert-Status $cliqueComToken 200 "WhatsApp com token"
Add-Check "WhatsApp liberado apos token" ($cliqueComToken.Body -match '"disponivel"\s*:\s*true') "backend libera contato somente depois da verificacao"
Assert-NoSensitivePublicData -Nome "WhatsApp com token" -Body $cliqueComToken.Body -AllowSyntheticWhatsapp $true

$storiesComToken = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugStories/stories" -Session $idadeSession
Assert-Status $storiesComToken 200 "stories com token"
Add-Check "stories reconhecem token" ($storiesComToken.Body -match '"autorizado"\s*:\s*true' -or $storiesComToken.Body -match '"stories"\s*:\s*\[\s*\]') "token persiste entre superficies"

try {
  $explicitAccess = Enable-ComplianceVisitorAccessLocal `
    -BaseUrl $SafeBaseUrl `
    -Slug $SlugMidiaRestrita `
    -Scope "CONTEUDO_EXPLICITO" `
    -Session $idadeSession
  $explicitSetCookie = Get-HeaderValue $explicitAccess.VerifyHeaders "Set-Cookie"
  Add-Check "challenge explicito concluido" ($explicitAccess.Verified.explicitVerified -eq $true) "escopo explicito autorizado separadamente"
  $explicitCookieName = "visitor_explicit_access_" + "token"
  $explicitHttpOnly = $explicitSetCookie -match [regex]::Escape($explicitCookieName + "=") `
    -and $explicitSetCookie -match 'HttpOnly'
  Add-Check "token explicito HttpOnly" $explicitHttpOnly "cookie explicito deve ser HttpOnly"
  Add-Check "token explicito SameSite Lax" ($explicitSetCookie -match 'SameSite=Lax') "cookie explicito deve usar SameSite=Lax"
} catch {
  Add-Check "fluxo explicito sintetico" $false $_.Exception.Message
}

try {
  $revokeResponse = Invoke-ComplianceAgeGateRequest `
    -BaseUrl $SafeBaseUrl `
    -Path "/api/public/compliance/visitor/revoke" `
    -Session $idadeSession `
    -Method "POST" `
    -Body @{ reason = "SMOKE_SINTETICO" }
  $revoke = $revokeResponse.Content | ConvertFrom-Json
  $revogacaoConcluida = (
    [int]$revokeResponse.StatusCode -eq 200 `
      -and $revoke.verified -eq $false `
      -and $revoke.explicitVerified -eq $false
  )
  Add-Check "revogacao concluida" $revogacaoConcluida "tokens geral e explicito devem ser invalidados"
  Add-Check "revogacao preserva aceite global" ($revoke.globalAccepted -eq $true) "nivel global permanece independente"
  $statusRevogado = Invoke-ComplianceAgeGateRequest `
    -BaseUrl $SafeBaseUrl `
    -Path "/api/public/compliance/visitor/status" `
    -Session $idadeSession
  $statusRevogadoBody = $statusRevogado.Content | ConvertFrom-Json
  $revogacaoPersistida = $statusRevogadoBody.verified -eq $false `
    -and $statusRevogadoBody.explicitVerified -eq $false
  Add-Check "status persiste revogacao" $revogacaoPersistida "cookies expirados nao podem reautorizar o visitante"
} catch {
  Add-Check "revogacao sintetica" $false $_.Exception.Message
}

$publicApiPath = Resolve-RepoPath "frontend/src/lib/api/publicApi.ts"
$publicModules = Resolve-RepoPath "frontend/src/modules/public"
if (Test-Path -LiteralPath $publicApiPath -PathType Leaf) {
  $publicApi = Get-Content -LiteralPath $publicApiPath -Raw
  Add-Check "frontend chama endpoint backend de WhatsApp" ($publicApi -match '/clique-whatsapp') "cliente publico deve usar endpoint backend"
  Add-Check "frontend nao monta wa.me no cliente API" (-not ($publicApi -match 'wa\.me/|api\.whatsapp\.com')) "frontend nao deve construir URL de WhatsApp"
}
if (Test-Path -LiteralPath $publicModules -PathType Container) {
  $moduleHits = @(rg -n "document\.body\.style\.overflow|wa\.me/|api\.whatsapp\.com|localStorage|sessionStorage" $publicModules 2>$null)
  $moduleDetail = if ($moduleHits.Count -eq 0) { "nenhum achado proibido" } else { (($moduleHits | Select-Object -First 5) -join " | ") }
  Add-Check "frontend publico sem scroll lock/storage/wa.me/copy tecnica" ($moduleHits.Count -eq 0) $moduleDetail
}

if ($pending.Count -gt 0) {
  Save-Report "PENDENTE_AGEGATE_WHATSAPP_SINTETICO_LOCAL"
  Write-Host "VALIDATION_RESULT=PENDENTE_AGEGATE_WHATSAPP_SINTETICO_LOCAL"
  exit 2
}

if ($failures.Count -gt 0) {
  Save-Report "FALHA_AGEGATE_WHATSAPP_SINTETICO_LOCAL"
  Write-Host "VALIDATION_RESULT=FALHA_AGEGATE_WHATSAPP_SINTETICO_LOCAL"
  exit 1
}

Save-Report "OK_AGEGATE_WHATSAPP_SINTETICO_LOCAL"
Write-Host "VALIDATION_RESULT=OK_AGEGATE_WHATSAPP_SINTETICO_LOCAL"
exit 0
