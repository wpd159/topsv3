param(
  [string]$BaseUrl = "http://127.0.0.1:18131",
  [string]$FixturePath = "backend/src/test/resources/fixtures/v3-dados-sinteticos.json",
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-31/relatorio-api-publica-sintetica.md",
  [switch]$PermitirEvidenciaExistente
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

function Resolve-BaseUrl {
  param([string]$Value)
  $trimmed = $Value.Trim().TrimEnd("/")
  if ($trimmed -notmatch '^http://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]+)?$') {
    throw "BaseUrl deve ser HTTP localhost."
  }
  return $trimmed
}

function Invoke-LocalJson {
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
      Headers = @{ Accept = "application/json"; "X-Request-Id" = "bloco31-sintetico-local" }
      UseBasicParsing = $true
      TimeoutSec = 12
    }
    if ($Session) { $params["WebSession"] = $Session }
    if ($Body) {
      $params["Body"] = $Body
      $params["ContentType"] = "application/json"
    }
    $response = Invoke-WebRequest @params
    return [pscustomobject]@{ Url = $url; Status = [int]$response.StatusCode; Body = [string]$response.Content; ErroOperacional = $false }
  } catch [System.Net.WebException] {
    if ($_.Exception.Response) {
      $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
      try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
      return [pscustomobject]@{ Url = $url; Status = [int]$_.Exception.Response.StatusCode; Body = [string]$content; ErroOperacional = $false }
    }
    return [pscustomobject]@{ Url = $url; Status = 0; Body = $_.Exception.Message; ErroOperacional = $true }
  }
}

function Add-Check {
  param([string]$Nome, [bool]$Ok, [string]$Detalhe)
  $script:checks.Add([pscustomobject]@{ Nome = $Nome; Resultado = $(if ($Ok) { "OK" } else { "FALHA" }); Detalhe = $Detalhe })
  if (-not $Ok) { $script:failures.Add("${Nome}: ${Detalhe}") }
}

function Assert-NoSensitivePublicData {
  param([string]$Nome, [string]$Body)
  $safeBody = $Body.Replace("https://wa.me/5500000000000", "WHATSAPP_SINTETICO_AUTORIZADO")
  $safeBody = $safeBody.Replace("+5500000000000", "WHATSAPP_SINTETICO_AUTORIZADO")
  $safeBody = $safeBody.Replace("5500000000000", "WHATSAPP_SINTETICO_AUTORIZADO")
  $patterns = @(
    @{ Label = "CPF"; Regex = '\b[0-9]{3}\.?[0-9]{3}\.?[0-9]{3}-?[0-9]{2}\b' },
    @{ Label = "e-mail real"; Regex = '(?i)[a-z0-9._%+-]+@(?!example\.test\b)[a-z0-9.-]+\.[a-z]{2,}' },
    @{ Label = "telefone real"; Regex = '(?i)\+[1-9][0-9]{7,14}|wa\.me/[0-9]{8,15}' },
    @{ Label = "documento"; Regex = '(?i)documentoUsuario|documento_privado|cpf|identidade|\brg\b' },
    @{ Label = "storage"; Regex = '(?i)storageProvider|storage_provider|chaveObjeto|chave_objeto|"bucket"\s*:' },
    @{ Label = "token/segredo"; Regex = '(?i)"token"\s*:|authorization|bearer|senha|password|secret' },
    @{ Label = "payload financeiro"; Regex = '(?i)\bpix\b|\befi\b|txid|pagamentoCriado"\s*:\s*true|creditoCriado"\s*:\s*true' }
  )
  foreach ($pattern in $patterns) {
    Add-Check "$Nome sem $($pattern.Label)" (-not ($safeBody -match $pattern.Regex)) "resposta publica nao deve expor dado sensivel"
  }
}

function Save-Report {
  param([string]$Resultado)
  $reportPath = Resolve-RepoPath $RelatorioSaida
  $parent = Split-Path -Parent $reportPath
  if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
  $lines = New-Object System.Collections.Generic.List[string]
  $lines.Add("# Relatorio - API publica sintetica Bloco 31")
  $lines.Add("")
  $lines.Add("- Resultado: $Resultado")
  $lines.Add("- BaseUrl: $script:SafeBaseUrl")
  $lines.Add("- Fixture: $FixturePath")
  $lines.Add("- Smoke legado executado: $script:legacySmokeOk")
  $lines.Add("- Dados reais usados: nao")
  $lines.Add("- Producao/VPS/API externa acessadas: nao")
  $lines.Add("")
  $lines.Add("## Checks")
  foreach ($check in $checks) {
    $lines.Add("- $($check.Resultado): $($check.Nome) - $($check.Detalhe)")
  }
  $lines.Add("")
  $lines.Add("## Falhas")
  if ($failures.Count -eq 0) { $lines.Add("- Nenhuma") } else { foreach ($failure in $failures) { $lines.Add("- $failure") } }
  [System.IO.File]::WriteAllText($reportPath, (($lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
}

$script:SafeBaseUrl = Resolve-BaseUrl $BaseUrl
$fixtureFull = Resolve-RepoPath $FixturePath
$reportFull = Resolve-RepoPath $RelatorioSaida
$checks = New-Object System.Collections.Generic.List[object]
$failures = New-Object System.Collections.Generic.List[string]
$script:legacySmokeOk = $false

if (-not (Test-Path -LiteralPath $fixtureFull -PathType Leaf)) {
  Write-Host "VALIDATION_RESULT=PENDENTE_API_PUBLICA_SINTETICA_LOCAL"
  Write-Host "Motivo: fixture sintetica ausente."
  exit 2
}

try {
  Invoke-WebRequest -Uri "$SafeBaseUrl/api/health/readiness" -UseBasicParsing -TimeoutSec 3 | Out-Null
} catch {
  if ($PermitirEvidenciaExistente -and (Test-Path -LiteralPath $reportFull -PathType Leaf)) {
    $existing = Get-Content -LiteralPath $reportFull -Raw
    if ($existing -match 'Resultado:\s*OK_API_PUBLICA_SINTETICA_LOCAL') {
      Add-Check "ALERTA_EVIDENCIA_EXISTENTE_REUTILIZADA" $true "backend indisponivel em $SafeBaseUrl; reutilizacao aceita apenas por parametro explicito"
      Save-Report "OK_API_PUBLICA_SINTETICA_LOCAL_EVIDENCIA_EXISTENTE"
      Write-Host "ALERTA_EVIDENCIA_EXISTENTE_REUTILIZADA"
      Write-Host "VALIDATION_RESULT=OK_API_PUBLICA_SINTETICA_LOCAL_EVIDENCIA_EXISTENTE"
      exit 0
    }
  }
  Add-Check "backend local disponivel" $false "backend local indisponivel em $SafeBaseUrl; evidencia antiga nao e aceita por padrao"
  Save-Report "PENDENTE_API_PUBLICA_SINTETICA_LOCAL"
  Write-Host "VALIDATION_RESULT=PENDENTE_API_PUBLICA_SINTETICA_LOCAL"
  Write-Host "Motivo: backend local indisponivel em $SafeBaseUrl."
  exit 2
}

$legacyScript = Resolve-RepoPath "scripts/local/validar-api-publica-local.ps1"
$powershell = (Get-Command powershell -ErrorAction Stop).Source
$legacyOutput = & $powershell -NoProfile -ExecutionPolicy Bypass -File $legacyScript -BaseUrl $SafeBaseUrl 2>&1
if ($LASTEXITCODE -ne 0) {
  Add-Check "smoke legado API publica" $false (($legacyOutput | Select-Object -Last 8) -join " ")
  Save-Report "FALHA_API_PUBLICA_SINTETICA_LOCAL"
  Write-Host "VALIDATION_RESULT=FALHA_API_PUBLICA_SINTETICA_LOCAL"
  exit 1
}
$script:legacySmokeOk = $true
Add-Check "smoke legado API publica" $true "validar-api-publica-local.ps1 aprovado"

$data = Get-Content -LiteralPath $fixtureFull -Raw | ConvertFrom-Json
Add-Check "fixture localOnly" ($data.localOnly -eq $true) "fixture deve ser local"
Add-Check "fixture noRealData" ($data.noRealData -eq $true) "fixture nao deve usar dado real"

$idadeSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$idadeBody = (@{ dataNascimento = "1990-01-01"; declaracaoMaioridade = $true } | ConvertTo-Json -Compress)

$checksHttp = @(
  @{ Nome = "descoberta de localidades"; Path = "/api/public/localidades"; Status = 200; Session = $null; DeveConter = "goiania"; NaoConter = "wa.me/" },
  @{ Nome = "listagem estado GO"; Path = "/api/public/acompanhantes/go"; Status = 200; Session = $null; DeveConter = "demo-goiania-livre-premium"; NaoConter = "wa.me/" },
  @{ Nome = "anuncio livre demo"; Path = "/api/public/anuncios/demo-goiania-livre-premium"; Status = 200; Session = $null; DeveConter = "demo-goiania-livre-premium"; NaoConter = "wa.me/" },
  @{ Nome = "cidade Goiania"; Path = "/api/public/acompanhantes/go/goiania"; Status = 200; Session = $null; DeveConter = "demo-goiania-livre-premium"; NaoConter = "wa.me/" },
  @{ Nome = "agregado cidade Goiania"; Path = "/api/public/localidades/go/goiania"; Status = 200; Session = $null; DeveConter = "setor-bueno"; NaoConter = "wa.me/" },
  @{ Nome = "bairro Setor Bueno"; Path = "/api/public/acompanhantes/go/goiania/setor-bueno"; Status = 200; Session = $null; DeveConter = "demo-goiania-livre-premium"; NaoConter = "wa.me/" },
  @{ Nome = "cidade Brasilia"; Path = "/api/public/acompanhantes/df/brasilia"; Status = 200; Session = $null; DeveConter = "demo-brasilia-premium-topo"; NaoConter = "wa.me/" },
  @{ Nome = "midia restrita sem idade"; Path = "/api/public/anuncios/demo-goiania-midia-restrita"; Status = 200; Session = $null; DeveConter = "RESTRITA_18"; NaoConter = "wa.me/" },
  @{ Nome = "paginacao invalida"; Path = "/api/public/acompanhantes/go?pagina=-1&tamanho=20"; Status = 400; Session = $null; DeveConter = ""; NaoConter = "" },
  @{ Nome = "estado inexistente"; Path = "/api/public/acompanhantes/xy"; Status = 404; Session = $null; DeveConter = ""; NaoConter = "" },
  @{ Nome = "cidade inexistente"; Path = "/api/public/acompanhantes/go/cidade-inexistente"; Status = 404; Session = $null; DeveConter = ""; NaoConter = "" },
  @{ Nome = "bairro inexistente"; Path = "/api/public/acompanhantes/go/goiania/bairro-inexistente"; Status = 404; Session = $null; DeveConter = ""; NaoConter = "" },
  @{ Nome = "detalhe inexistente"; Path = "/api/public/anuncios/slug-inexistente"; Status = 404; Session = $null; DeveConter = ""; NaoConter = "" },
  @{ Nome = "pendente nao publicado"; Path = "/api/public/anuncios/demo-goiania-pendente"; Status = 404; Session = $null; DeveConter = ""; NaoConter = "" },
  @{ Nome = "rejeitado nao publicado"; Path = "/api/public/anuncios/demo-goiania-rejeitado"; Status = 404; Session = $null; DeveConter = ""; NaoConter = "" }
)

foreach ($item in $checksHttp) {
  $response = Invoke-LocalJson -Path $item.Path -Session $item.Session
  Add-Check "$($item.Nome) status" ($response.Status -eq $item.Status) "status obtido: $($response.Status)"
  if ($item.DeveConter) { Add-Check "$($item.Nome) contem esperado" ($response.Body -match [regex]::Escape($item.DeveConter)) "esperado: $($item.DeveConter)" }
  if ($item.NaoConter) { Add-Check "$($item.Nome) sem proibido" (-not ($response.Body -match [regex]::Escape($item.NaoConter))) "proibido: $($item.NaoConter)" }
  Assert-NoSensitivePublicData -Nome $item.Nome -Body $response.Body
}

$idade = Invoke-LocalJson -Path "/api/public/idade/confirmar" -Method "POST" -Body $idadeBody -Session $idadeSession
Add-Check "idade sintetica confirmada" ($idade.Status -eq 200) "status obtido: $($idade.Status)"

$restritaComIdade = Invoke-LocalJson -Path "/api/public/anuncios/demo-goiania-midia-restrita" -Session $idadeSession
Add-Check "midia restrita com idade status" ($restritaComIdade.Status -eq 200) "status obtido: $($restritaComIdade.Status)"
Assert-NoSensitivePublicData -Nome "midia restrita com idade" -Body $restritaComIdade.Body

$cliqueRestritoSemIdade = Invoke-LocalJson -Path "/api/public/anuncios/demo-goiania-midia-restrita/clique-whatsapp" -Method "POST" -Body (@{ visitanteLocalId = "visitante-bloco31"; origemPais = "BR"; origemUf = "GO"; origemCidade = "Goiania"; dispositivo = "DESKTOP" } | ConvertTo-Json -Compress)
Add-Check "contato com midia restrita independe da idade" ($cliqueRestritoSemIdade.Status -eq 200 -and $cliqueRestritoSemIdade.Body -match '"disponivel"\s*:\s*true') "contato mediado permanece disponivel sem cookie de idade"

$seoScript = Resolve-RepoPath "scripts/local/validar-seo-sintetico-local.ps1"
if (Test-Path -LiteralPath $seoScript -PathType Leaf) {
  $seoOutput = & $powershell -NoProfile -ExecutionPolicy Bypass -File $seoScript -BaseUrl $SafeBaseUrl 2>&1
  Add-Check "SEO sintetico via API" ($LASTEXITCODE -eq 0) (($seoOutput | Select-Object -Last 6) -join " ")
}

if ($failures.Count -gt 0) {
  Save-Report "FALHA_API_PUBLICA_SINTETICA_LOCAL"
  Write-Host "VALIDATION_RESULT=FALHA_API_PUBLICA_SINTETICA_LOCAL"
  exit 1
}

Save-Report "OK_API_PUBLICA_SINTETICA_LOCAL"
Write-Host "VALIDATION_RESULT=OK_API_PUBLICA_SINTETICA_LOCAL"
exit 0
