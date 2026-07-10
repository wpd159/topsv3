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
    "backend/src/test/resources/fixtures/v3-dados-sinteticos.json"
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
  $lines.Add("- Mídia RESTRITA_18 protegida antes da confirmação de idade, sem bloquear a página ou o contato.")
  $lines.Add("- Confirmacao de idade adulta sintetica emite cookie HttpOnly SameSite=Lax.")
  $lines.Add("- Data menor de 18 anos e data invalida retornam erro 400 sem cookie de confirmacao.")
  $lines.Add("- WhatsApp publico e liberado apenas pelo endpoint backend autorizado.")
  $lines.Add("- Stories exigem confirmacao de idade quando a rota local existe.")
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
$idadeMaiorBody = (@{ dataNascimento = "1990-01-01"; declaracaoMaioridade = $true } | ConvertTo-Json -Compress)
$idadeMenorBody = (@{ dataNascimento = (Get-Date).AddYears(-17).ToString("yyyy-MM-dd"); declaracaoMaioridade = $true } | ConvertTo-Json -Compress)
$idadeInvalidaBody = '{"dataNascimento":"data-invalida","declaracaoMaioridade":true}'
$idadeSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession

$statusInicial = Invoke-LocalHttp -Path "/api/public/idade/status"
Assert-Status $statusInicial 200 "idade status inicial"
Add-Check "idade inicial nao confirmada" ($statusInicial.Body -match '"confirmada"\s*:\s*false') "sem cookie a idade deve iniciar nao confirmada"
Assert-NoSensitivePublicData -Nome "idade status inicial" -Body $statusInicial.Body
Assert-NoTechnicalCopy -Nome "idade status inicial" -Body $statusInicial.Body

$livre = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugLivre"
Assert-Status $livre 200 "anuncio LIVRE sem idade"
Add-Check "anuncio LIVRE contem slug" ($livre.Body -match [regex]::Escape($SlugLivre)) "LIVRE deve ser acessivel sem age gate"
Assert-NoSensitivePublicData -Nome "anuncio LIVRE sem idade" -Body $livre.Body
Assert-NoTechnicalCopy -Nome "anuncio LIVRE sem idade" -Body $livre.Body

$menor = Invoke-LocalHttp -Path "/api/public/idade/confirmar" -Method "POST" -Body $idadeMenorBody
Assert-Status $menor 400 "idade menor de 18"
Add-Check "idade menor sem cookie" (-not (Get-HeaderValue $menor.Headers "Set-Cookie")) "menor de idade nao deve receber cookie"
Add-Check "idade menor erro amigavel" ($menor.Body -match 'idade|confirmacao|confirmada|erro|mensagem|detail') "erro deve ser interpretavel sem stack trace"
Assert-NoSensitivePublicData -Nome "idade menor de 18" -Body $menor.Body
Assert-NoTechnicalCopy -Nome "idade menor de 18" -Body $menor.Body

$invalida = Invoke-LocalHttp -Path "/api/public/idade/confirmar" -Method "POST" -Body $idadeInvalidaBody
Assert-Status $invalida 400 "idade data invalida"
Add-Check "idade invalida sem cookie" (-not (Get-HeaderValue $invalida.Headers "Set-Cookie")) "data invalida nao deve receber cookie"
Add-Check "idade invalida erro amigavel" ($invalida.Body -match 'idade|confirmacao|erro|mensagem|detail') "erro deve ser interpretavel sem stack trace"
Assert-NoSensitivePublicData -Nome "idade data invalida" -Body $invalida.Body
Assert-NoTechnicalCopy -Nome "idade data invalida" -Body $invalida.Body

$confirmada = Invoke-LocalHttp -Path "/api/public/idade/confirmar" -Method "POST" -Body $idadeMaiorBody -Session $idadeSession
Assert-Status $confirmada 200 "idade maior confirmada"
$setCookie = Get-HeaderValue $confirmada.Headers "Set-Cookie"
Add-Check "idade maior confirmada true" ($confirmada.Body -match '"confirmada"\s*:\s*true') "idade adulta sintetica deve confirmar"
Add-Check "idade emite cookie" ($setCookie -match 'topsv3_idade_confirmada=') "cookie de idade deve ser emitido"
Add-Check "idade cookie HttpOnly" ($setCookie -match 'HttpOnly') "cookie deve ser HttpOnly"
Add-Check "idade cookie SameSite Lax" ($setCookie -match 'SameSite=Lax') "cookie deve usar SameSite=Lax"
Add-Check "idade cookie sem Secure local HTTP" (-not ($setCookie -match ';\s*Secure(?:;|$)')) "Secure deve permanecer desligado em HTTP local"
Assert-NoSensitivePublicData -Nome "idade maior confirmada" -Body $confirmada.Body
Assert-NoTechnicalCopy -Nome "idade maior confirmada" -Body $confirmada.Body

$statusConfirmado = Invoke-LocalHttp -Path "/api/public/idade/status" -Session $idadeSession
Assert-Status $statusConfirmado 200 "idade status confirmada"
Add-Check "idade status com cookie confirmada" ($statusConfirmado.Body -match '"confirmada"\s*:\s*true') "cookie assinado deve ser aceito"

$restritaSemIdade = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugMidiaRestrita"
Assert-Status $restritaSemIdade 200 "anuncio com midia restrita sem idade"
Add-Check "pagina com midia restrita permanece publica" ($restritaSemIdade.Body -match [regex]::Escape($SlugMidiaRestrita)) "titulo, descricao e pagina independem da idade"
Add-Check "visibilidade restrita exposta por midia" ($restritaSemIdade.Body -match '"visibilidadeMidia"\s*:\s*"RESTRITA_18"') "DTO identifica a regra individual"
Add-Check "midia restrita nao autorizada sem idade" ($restritaSemIdade.Body -match '"autorizada"\s*:\s*false') "backend decide autorizacao"
Add-Check "original restrito ausente sem idade" (-not ($restritaSemIdade.Body -match '"urlPublica"\s*:\s*"[^\"]+"')) "DTO preserva placeholder sem URL original"
Assert-NoSensitivePublicData -Nome "anuncio com midia restrita sem idade" -Body $restritaSemIdade.Body
Assert-NoTechnicalCopy -Nome "anuncio com midia restrita sem idade" -Body $restritaSemIdade.Body

$cliqueLivre = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugLivre/clique-whatsapp" -Method "POST" -Body $metricBody
Assert-Status $cliqueLivre 200 "clique WhatsApp LIVRE"
Add-Check "clique LIVRE disponivel" ($cliqueLivre.Body -match '"disponivel"\s*:\s*true') "LIVRE pode liberar contato pelo backend"
Add-Check "clique LIVRE URL sintetica" ($cliqueLivre.Body -match '"whatsappUrl"\s*:\s*"https://wa\.me/5500000000000"') "somente URL sintetica autorizada"
Assert-NoSensitivePublicData -Nome "clique WhatsApp LIVRE" -Body $cliqueLivre.Body -AllowSyntheticWhatsapp $true
Assert-NoTechnicalCopy -Nome "clique WhatsApp LIVRE" -Body $cliqueLivre.Body

$cliqueRestritoSemIdade = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugMidiaRestrita/clique-whatsapp" -Method "POST" -Body $metricBody
Assert-Status $cliqueRestritoSemIdade 200 "clique WhatsApp com midia restrita sem idade"
Add-Check "contato independe da idade" ($cliqueRestritoSemIdade.Body -match '"disponivel"\s*:\s*true') "backend libera contato para anuncio publico ativo sem cookie de idade"
Add-Check "contato permanece mediado" ($cliqueRestritoSemIdade.Body -match '"whatsappUrl"\s*:\s*"https://wa\.me/5500000000000"') "URL sintetica retorna apenas no endpoint de clique"
Assert-NoSensitivePublicData -Nome "clique WhatsApp com midia restrita sem idade" -Body $cliqueRestritoSemIdade.Body -AllowSyntheticWhatsapp $true
Assert-NoTechnicalCopy -Nome "clique WhatsApp com midia restrita sem idade" -Body $cliqueRestritoSemIdade.Body

$storiesSemIdade = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugStories/stories"
Assert-Status $storiesSemIdade 200 "stories sem idade"
Add-Check "stories sem idade bloqueados" ($storiesSemIdade.Body -match '"autorizado"\s*:\s*false' -and $storiesSemIdade.Body -match '"stories"\s*:\s*\[\s*\]') "stories exigem idade confirmada"
Add-Check "stories sem idade motivo atual" ($storiesSemIdade.Body -match 'IDADE_NAO_CONFIRMADA') "motivo backend deve ser atual"
Assert-NoSensitivePublicData -Nome "stories sem idade" -Body $storiesSemIdade.Body
Assert-NoTechnicalCopy -Nome "stories sem idade" -Body $storiesSemIdade.Body

$storiesComIdade = Invoke-LocalHttp -Path "/api/public/anuncios/$SlugStories/stories" -Session $idadeSession
Assert-Status $storiesComIdade 200 "stories com idade"
Add-Check "stories com idade autorizados" ($storiesComIdade.Body -match '"autorizado"\s*:\s*true' -and $storiesComIdade.Body -match '"stories"\s*:\s*\[') "backend autorizou stories apos idade"
Add-Check "stories sem midia real" ($storiesComIdade.Body -match 'PENDENTE_URL_PUBLICA_MIDIA_CDN' -or $storiesComIdade.Body -match '"stories"\s*:\s*\[\s*\]') "sem URL real de midia"
Assert-NoSensitivePublicData -Nome "stories com idade" -Body $storiesComIdade.Body
Assert-NoTechnicalCopy -Nome "stories com idade" -Body $storiesComIdade.Body

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
