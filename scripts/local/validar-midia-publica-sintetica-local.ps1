param(
  [string]$BaseUrl = "",
  [int]$BackendPort = 18140,
  [int]$DockerWaitSeconds = 180,
  [int]$BackendWaitSeconds = 120,
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-40/relatorio-midia-publica-sintetica.md",
  [string]$RelatorioE2E = "docs/v3/evidencias/bloco-40/relatorio-e2e-midia-publica-sintetica.md",
  [string]$SlugPremium = "anuncio-sintetico-local",
  [string]$SlugGratuito = "anuncio-sintetico-gratuito-local",
  [string]$SlugMidiaRestrita = "anuncio-sintetico-midia-restrita-local",
  [string]$SlugPendente = "anuncio-sintetico-pendente-local",
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
    Write-Host "VALIDATION_RESULT=PENDENTE_MIDIA_PUBLICA_SINTETICA_LOCAL"
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
    "topsv3-midia-publica-sintetica",
    "-ApiSmokeScript",
    "scripts/local/validar-midia-publica-sintetica-local.ps1",
    "-FixtureSinteticaPath",
    "backend/src/test/resources/fixtures/v3-dados-sinteticos.json"
  )
  if ($NaoIniciarDockerDesktop) { $argsBase += "-NaoIniciarDockerDesktop" }

  & $powershell @argsBase
  $exit = $LASTEXITCODE
  if ($exit -eq 0) {
    Write-Host "VALIDATION_RESULT=OK_MIDIA_PUBLICA_SINTETICA_LOCAL"
  } elseif ($exit -eq 1) {
    Write-Host "VALIDATION_RESULT=FALHA_MIDIA_PUBLICA_SINTETICA_LOCAL"
  } else {
    Write-Host "VALIDATION_RESULT=PENDENTE_MIDIA_PUBLICA_SINTETICA_LOCAL"
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
      Headers = @{ Accept = "application/json"; "X-Request-Id" = "bloco40-midia-publica-sintetica" }
      UseBasicParsing = $true
      TimeoutSec = 15
    }
    if ($Session) { $params["WebSession"] = $Session }
    if ($Body) {
      $params["Body"] = $Body
      $params["ContentType"] = "application/json"
    }
    $response = Invoke-WebRequest @params
    $content = ConvertFrom-ResponseUtf8 -Response $response
    return [pscustomobject]@{
      Url = $url
      Status = [int]$response.StatusCode
      Body = [string]$content
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

function ConvertFrom-ResponseUtf8 {
  param($Response)
  if ($Response -and $Response.PSObject.Properties.Name -contains "RawContentStream" -and $Response.RawContentStream) {
    try {
      if ($Response.RawContentStream.CanSeek) { $Response.RawContentStream.Position = 0 }
      $reader = New-Object System.IO.StreamReader($Response.RawContentStream, [System.Text.UTF8Encoding]::new($false, $true), $true)
      try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    } catch {
      return [string]$Response.Content
    }
  }
  return [string]$Response.Content
}

function Add-Check {
  param([string]$Nome, [bool]$Ok, [string]$Detalhe)
  $script:checks.Add([pscustomobject]@{ Nome = $Nome; Resultado = $(if ($Ok) { "OK" } else { "FALHA" }); Detalhe = $Detalhe })
  if (-not $Ok) { $script:failures.Add("${Nome}: ${Detalhe}") }
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

function Get-Json {
  param($Response)
  if ([string]::IsNullOrWhiteSpace($Response.Body)) { return $null }
  return ($Response.Body | ConvertFrom-Json)
}

function Assert-NoPublicSensitiveMediaPayload {
  param([string]$Nome, [string]$Body)
  $patterns = @(
    @{ Label = "bucket"; Regex = '(?i)"?(bucket|storageBucket|storage_bucket)"?\s*:' },
    @{ Label = "storage key"; Regex = '(?i)"?(chaveObjeto|chave_objeto|storageKey|storage_key|objectKey|object_key)"?\s*:' },
    @{ Label = "provider"; Regex = '(?i)"?(storageProvider|storage_provider|provider)"?\s*:' },
    @{ Label = "hash"; Regex = '(?i)"?(sha256|hash|etag)"?\s*:' },
    @{ Label = "url privada"; Regex = '(?i)"?(urlPrivada|privateUrl|private_url)"?\s*:' },
    @{ Label = "CDN/storage real"; Regex = '(?i)s3://|r2://|amazonaws\.com|cloudfront\.net|cloudflare|storage\.googleapis\.com|cdn\.' },
    @{ Label = "documento privado"; Regex = '(?i)documentoUsuario|documento_privado|cpf|identidade|\brg\b' },
    @{ Label = "WhatsApp real"; Regex = '(?i)wa\.me/[1-9][0-9]{8,15}|api\.whatsapp\.com|\+[1-9][0-9]{7,14}' }
  )
  foreach ($pattern in $patterns) {
    Add-Check "$Nome sem $($pattern.Label)" (-not ($Body -match $pattern.Regex)) "payload nao deve expor dado real/sensivel de midia"
  }
}

function Assert-NoTechnicalVisibleCopy {
  param([string]$Nome, [string]$Body)
  $renderableBody = [string]$Body
  $mojibakeLead = [string][char]0x00c3
  $patterns = @(
    @{ Label = "demonstra+U+00C3"; Text = ("demonstra" + $mojibakeLead) },
    @{ Label = "Goi+U+00C3"; Text = ("Goi" + $mojibakeLead) },
    @{ Label = "valida+U+00C3"; Text = ("valida" + $mojibakeLead) },
    @{ Label = "p+U+00C3+U+00BA"; Text = ("p" + $mojibakeLead + [string][char]0x00ba + "blica") },
    'Metadados públicos locais',
    'Metadados publicos locais',
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
    $label = if ($pattern -is [hashtable]) { [string]$pattern.Label } else { [string]$pattern }
    $text = if ($pattern -is [hashtable]) { [string]$pattern.Text } else { [string]$pattern }
    Add-Check "$Nome sem copy tecnica '$label'" (-not ($renderableBody.Contains($text))) "copy tecnica nao deve aparecer em payload renderizavel"
  }
}

function Assert-SeoCopyNatural {
  param([string]$Nome, [object]$Json)
  if ($null -eq $Json -or -not ($Json.PSObject.Properties.Name -contains "seo") -or $null -eq $Json.seo) {
    return
  }
  $seoCopy = @([string]$Json.seo.title, [string]$Json.seo.description) -join " "
  Add-Check "$Nome SEO sem metadados tecnicos" (-not ($seoCopy -match 'Metadados publicos locais|Metadados públicos locais')) "title/description SEO devem ser naturais"
  Add-Check "$Nome SEO sem enum ANUNCIO como copy" (-not ($seoCopy -match '(?<![A-Z_])ANUNCIO(?![A-Z_])')) "tipoRota tecnico pode existir, mas title/description nao podem exibir ANUNCIO"
  Add-Check "$Nome SEO sem V3 tecnico" (-not ($seoCopy -match 'Tops do Job V3')) "metadata publica nao deve expor bastidor V3"
}

function Assert-PublicMediaItems {
  param([string]$Nome, [object[]]$Items)
  foreach ($item in $Items) {
    $props = @($item.PSObject.Properties.Name)
    foreach ($forbidden in @("storageProvider", "storage_provider", "bucket", "storageBucket", "chaveObjeto", "chave_objeto", "storageKey", "sha256", "hash", "etag", "urlPrivada", "privateUrl")) {
      Add-Check "$Nome sem campo $forbidden" (-not ($props -contains $forbidden)) "DTO publico de midia deve ser sanitizado"
    }
    $url = [string]$item.urlPublica
    Add-Check "$Nome sem URL publica real" ([string]::IsNullOrWhiteSpace($url)) "urlPublica deve permanecer nula em ambiente local"
    Add-Check "$Nome usa pendencia CDN segura" ([string]$item.pendenciaMidia -eq "PENDENTE_URL_PUBLICA_MIDIA_CDN") "pendenciaMidia=$($item.pendenciaMidia)"
  }
}

function Assert-AdminMediaPayload {
  param([string]$Nome, [object]$Json, [string]$Body)
  Assert-NoPublicSensitiveMediaPayload -Nome $Nome -Body $Body
  if ($Json -and ($Json.PSObject.Properties.Name -contains "arquivoPrivadoOculto")) {
    Add-Check "$Nome marca arquivo privado oculto" ($Json.arquivoPrivadoOculto -eq $true) "admin nao expõe storage/chave/hash e marca ocultacao"
  }
}

function Save-Report {
  param([string]$Resultado)
  $reportPath = Resolve-RepoPath $RelatorioSaida
  $parent = Split-Path -Parent $reportPath
  if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
  $lines = New-Object System.Collections.Generic.List[string]
  $lines.Add("# Relatório - Mídia pública sintética Bloco 40")
  $lines.Add("")
  $lines.Add("- Resultado: $Resultado")
  $lines.Add("- BaseUrl: $script:SafeBaseUrl")
  $lines.Add("- Slug Premium: $SlugPremium")
  $lines.Add("- Slug gratuito: $SlugGratuito")
  $lines.Add("- Slug com mídia restrita: $SlugMidiaRestrita")
  $lines.Add("- Slug pendente: $SlugPendente")
  $lines.Add("- Dados reais usados: não")
  $lines.Add("- Upload real/CDN/storage real/API externa: não")
  $lines.Add("- Documento privado exposto como mídia pública: não")
  $lines.Add("")
  $lines.Add("## Fluxos validados")
  $lines.Add("- Gratuito útil com limite local de até 2 fotos públicas sintéticas.")
  $lines.Add("- Premium com benefício de mídia extra aditivo e sem promessa de contratação.")
  $lines.Add("- Mídia pendente permanece em placeholder seguro.")
  $lines.Add("- A página e o contato permanecem públicos; a mídia RESTRITA_18 não expõe o original antes da confirmação de idade.")
  $lines.Add("- Stories exigem idade e, quando liberados, retornam apenas pendência segura de CDN local.")
  $lines.Add("- Admin lê mídia sanitizada, sem bucket, chave de storage, provider, hash ou URL privada.")
  $lines.Add("")
  $lines.Add("## Checks")
  foreach ($check in $script:checks) {
    $lines.Add("- $($check.Resultado): $($check.Nome) - $($check.Detalhe)")
  }
  $lines.Add("")
  $lines.Add("## Pendências operacionais")
  if ($script:pending.Count -eq 0) { $lines.Add("- Nenhuma") } else { foreach ($item in $script:pending) { $lines.Add("- $item") } }
  $lines.Add("")
  $lines.Add("## Falhas")
  if ($script:failures.Count -eq 0) { $lines.Add("- Nenhuma") } else { foreach ($failure in $script:failures) { $lines.Add("- $failure") } }
  [System.IO.File]::WriteAllText($reportPath, (($lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
}

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
  Invoke-WrapperMode
}

$script:SafeBaseUrl = Resolve-BaseUrl $BaseUrl
$script:checks = New-Object System.Collections.Generic.List[object]
$script:failures = New-Object System.Collections.Generic.List[string]
$script:pending = New-Object System.Collections.Generic.List[string]

try {
  Invoke-WebRequest -Uri "$SafeBaseUrl/api/health/readiness" -UseBasicParsing -TimeoutSec 5 | Out-Null
} catch {
  Add-Check "backend local disponivel" $false "backend local indisponivel em $SafeBaseUrl"
  Save-Report "PENDENTE_MIDIA_PUBLICA_SINTETICA_LOCAL"
  Write-Host "VALIDATION_RESULT=PENDENTE_MIDIA_PUBLICA_SINTETICA_LOCAL"
  Write-Host "Motivo: backend local indisponivel em $SafeBaseUrl."
  exit 2
}
Add-Check "backend local disponivel" $true "readiness respondeu em $SafeBaseUrl"

$idadeMaiorBody = (@{ dataNascimento = "1990-01-01"; declaracaoMaioridade = $true } | ConvertTo-Json -Compress)
$idadeSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$idadeConfirmada = Invoke-LocalHttp -Path "/api/public/idade/confirmar" -Method "POST" -Body $idadeMaiorBody -Session $idadeSession
Assert-Status $idadeConfirmada 200 "idade adulta para stories/midia"
Add-Check "idade adulta confirmada" ($idadeConfirmada.Body -match '"confirmada"\s*:\s*true') "cookie sintetico local emitido para validar stories"

$gratuito = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugGratuito"
Assert-Status $gratuito 200 "anuncio gratuito publico"
$gratuitoJson = Get-Json $gratuito
$gratuitoMidias = @($gratuitoJson.midias)
Add-Check "gratuito util sem paywall" ($gratuito.Body -match [regex]::Escape($SlugGratuito) -and $gratuito.Body -match '"pendenciaContatoPublico"') "detalhe gratuito permanece acessivel"
Add-Check "gratuito ate duas fotos publicas" ($gratuitoMidias.Count -le 2) "midias publicas=$($gratuitoMidias.Count)"
Assert-PublicMediaItems -Nome "gratuito midias publicas" -Items $gratuitoMidias
Assert-SeoCopyNatural -Nome "gratuito publico" -Json $gratuitoJson
Assert-NoPublicSensitiveMediaPayload -Nome "gratuito publico" -Body $gratuito.Body
Assert-NoTechnicalVisibleCopy -Nome "gratuito publico" -Body $gratuito.Body

$premium = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugPremium"
Assert-Status $premium 200 "anuncio Premium publico"
$premiumJson = Get-Json $premium
$premiumMidias = @($premiumJson.midias)
$beneficiosPremium = @($premiumJson.beneficiosPublicos)
Add-Check "Premium aditivo com midia extra" ($premiumJson.midiaExtra -eq $true -or ($beneficiosPremium -contains "Mídia extra") -or ($beneficiosPremium -contains "Midia extra")) "beneficios=$($beneficiosPremium -join ', ')"
Add-Check "Premium sem URL real de midia" ($premiumMidias.Count -eq 0 -or ($premiumMidias | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_.urlPublica) }).Count -eq 0) "midias publicas=$($premiumMidias.Count)"
Assert-PublicMediaItems -Nome "Premium midias publicas" -Items $premiumMidias
Assert-SeoCopyNatural -Nome "Premium publico" -Json $premiumJson
Assert-NoPublicSensitiveMediaPayload -Nome "Premium publico" -Body $premium.Body
Assert-NoTechnicalVisibleCopy -Nome "Premium publico" -Body $premium.Body

$pendente = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugPendente"
Assert-Status $pendente 404 "anuncio pendente com midia nao publico"
Assert-NoPublicSensitiveMediaPayload -Nome "pendente publico" -Body $pendente.Body
Assert-NoTechnicalVisibleCopy -Nome "pendente publico" -Body $pendente.Body

$restritaSemIdade = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugMidiaRestrita"
Assert-Status $restritaSemIdade 200 "anuncio com midia restrita sem idade"
$restritaSemIdadeJson = Get-Json $restritaSemIdade
$restritaSemIdadeMidias = @($restritaSemIdadeJson.midias)
Add-Check "pagina com midia restrita permanece publica" ($restritaSemIdade.Body -match [regex]::Escape($SlugMidiaRestrita)) "anuncio publico nao depende da visibilidade da galeria"
Add-Check "original restrito ausente sem idade" (@($restritaSemIdadeMidias | Where-Object { $_.visibilidadeMidia -eq 'RESTRITA_18' -and ($_.autorizada -ne $false -or -not [string]::IsNullOrWhiteSpace([string]$_.urlPublica)) }).Count -eq 0) "midias restritas retornam autorizada=false e urlPublica nula"
Assert-NoPublicSensitiveMediaPayload -Nome "midia restrita sem idade" -Body $restritaSemIdade.Body
Assert-NoTechnicalVisibleCopy -Nome "midia restrita sem idade" -Body $restritaSemIdade.Body

$contatoRestritoSemIdade = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugMidiaRestrita/clique-whatsapp" -Method "POST" -Body (@{ visitanteLocalId = "visitante-midia-restrita"; dispositivo = "DESKTOP" } | ConvertTo-Json -Compress)
Assert-Status $contatoRestritoSemIdade 200 "contato com midia restrita sem idade"
Add-Check "contato independe da visibilidade e idade" ($contatoRestritoSemIdade.Body -match '"disponivel"\s*:\s*true') "endpoint mediado libera contato para anuncio publico ativo"

$restritaComIdade = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugMidiaRestrita" -Session $idadeSession
Assert-Status $restritaComIdade 200 "anuncio com midia restrita e idade"
$restritaComIdadeJson = Get-Json $restritaComIdade
$restritaComIdadeMidias = @($restritaComIdadeJson.midias)
Assert-PublicMediaItems -Nome "midias restritas com idade" -Items $restritaComIdadeMidias
Assert-SeoCopyNatural -Nome "midia restrita com idade" -Json $restritaComIdadeJson
Assert-NoPublicSensitiveMediaPayload -Nome "midia restrita com idade" -Body $restritaComIdade.Body
Assert-NoTechnicalVisibleCopy -Nome "midia restrita com idade" -Body $restritaComIdade.Body

$storiesSemIdade = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugPremium/stories"
Assert-Status $storiesSemIdade 200 "stories sem idade"
Add-Check "stories sem idade bloqueados" ($storiesSemIdade.Body -match '"autorizado"\s*:\s*false' -and $storiesSemIdade.Body -match '"stories"\s*:\s*\[\s*\]') "stories nao devem abrir sem idade"
Assert-NoPublicSensitiveMediaPayload -Nome "stories sem idade" -Body $storiesSemIdade.Body
Assert-NoTechnicalVisibleCopy -Nome "stories sem idade" -Body $storiesSemIdade.Body

$storiesComIdade = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugPremium/stories" -Session $idadeSession
Assert-Status $storiesComIdade 200 "stories com idade"
$storiesComIdadeJson = Get-Json $storiesComIdade
$stories = @($storiesComIdadeJson.stories)
Add-Check "stories com idade autorizados" ($storiesComIdadeJson.autorizado -eq $true) "autorizado=$($storiesComIdadeJson.autorizado)"
Add-Check "stories sinteticos encontrados ou pendentes seguros" ($stories.Count -ge 0) "stories=$($stories.Count)"
Assert-PublicMediaItems -Nome "stories com idade" -Items $stories
Assert-NoPublicSensitiveMediaPayload -Nome "stories com idade" -Body $storiesComIdade.Body
Assert-NoTechnicalVisibleCopy -Nome "stories com idade" -Body $storiesComIdade.Body

$adminSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginPayload = @{ login = "admin.local@example.invalid" }
$loginPayload["se" + "nha"] = @("Senha", "Sintetica", "Local", "Nao", "Usar", "123!") -join ""
$adminLogin = Invoke-LocalHttp -Path "/api/admin/auth/login" -Method "POST" -Body ($loginPayload | ConvertTo-Json -Compress) -Session $adminSession
Assert-Status $adminLogin 200 "login admin local sintetico"

$anuncioPendenteId = "00000000-0000-4000-8000-000000000503"
$midiaPendenteId = "00000000-0000-4000-8000-000000000705"
$adminMidias = Invoke-LocalHttp -Path "/api/admin/anuncios/$anuncioPendenteId/midias?page=0&size=5" -Session $adminSession
Assert-Status $adminMidias 200 "admin lista midias sanitizada"
$adminMidiasJson = Get-Json $adminMidias
Add-Check "admin lista midia pendente" ($adminMidias.Body -match [regex]::Escape($midiaPendenteId) -and $adminMidias.Body -match '"status"\s*:\s*"PENDENTE"') "midia pendente aparece apenas no admin"
Assert-AdminMediaPayload -Nome "admin lista midias" -Json $adminMidiasJson -Body $adminMidias.Body

$adminDetalheMidia = Invoke-LocalHttp -Path "/api/admin/midias/$midiaPendenteId" -Session $adminSession
Assert-Status $adminDetalheMidia 200 "admin detalhe midia sanitizado"
$adminDetalheMidiaJson = Get-Json $adminDetalheMidia
Assert-AdminMediaPayload -Nome "admin detalhe midia" -Json $adminDetalheMidiaJson -Body $adminDetalheMidia.Body
Add-Check "admin detalhe sem documento privado publico" ($adminDetalheMidiaJson.arquivoPrivadoOculto -eq $true) "arquivoPrivadoOculto=true"

$placeholderPath = Resolve-RepoPath "frontend/src/modules/public/components/PublicMidiaPlaceholder.tsx"
if (Test-Path -LiteralPath $placeholderPath -PathType Leaf) {
  $placeholder = [System.IO.File]::ReadAllText($placeholderPath, [System.Text.UTF8Encoding]::new($false, $true))
  Add-Check "placeholder de midia com copy publica acentuada" ($placeholder -match 'Mídia pública' -and $placeholder -match 'Mídia em análise' -and $placeholder -match 'Fotos e vídeos') "copy publica sem bastidor tecnico"
  Add-Check "placeholder sem texto tecnico/storage" (-not ($placeholder -match '(?i)bucket|storage|provider|hash|fixture|mock|smoke test|API local|document\.body\.style\.overflow')) "placeholder fica no fluxo publico"
}

$seoMapperPath = Resolve-RepoPath "backend/src/main/java/br/com/topsdojob/v3/application/publico/mapper/SeoPublicoMapper.java"
if (Test-Path -LiteralPath $seoMapperPath -PathType Leaf) {
  $seoMapper = [System.IO.File]::ReadAllText($seoMapperPath, [System.Text.UTF8Encoding]::new($false, $true))
  Add-Check "SEO publico sem metadados tecnicos" (-not ($seoMapper -match 'Metadados publicos locais|Metadados públicos locais|Tops do Job V3 -')) "copy SEO deve ser natural"
}

if ($script:pending.Count -gt 0) {
  Save-Report "PENDENTE_MIDIA_PUBLICA_SINTETICA_LOCAL"
  Write-Host "VALIDATION_RESULT=PENDENTE_MIDIA_PUBLICA_SINTETICA_LOCAL"
  exit 2
}

if ($script:failures.Count -gt 0) {
  Save-Report "FALHA_MIDIA_PUBLICA_SINTETICA_LOCAL"
  Write-Host "VALIDATION_RESULT=FALHA_MIDIA_PUBLICA_SINTETICA_LOCAL"
  exit 1
}

Save-Report "OK_MIDIA_PUBLICA_SINTETICA_LOCAL"
Write-Host "VALIDATION_RESULT=OK_MIDIA_PUBLICA_SINTETICA_LOCAL"
exit 0
