param(
  [string]$BaseUrl = "http://127.0.0.1:18131",
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-31/relatorio-seo-sintetico.md",
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

function Save-Report {
  param([string]$Resultado, [object[]]$Rows)
  $reportPath = Resolve-RepoPath $RelatorioSaida
  $parent = Split-Path -Parent $reportPath
  if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
  $lines = New-Object System.Collections.Generic.List[string]
  $lines.Add("# Relatorio - SEO sintetico Bloco 31")
  $lines.Add("")
  $lines.Add("- Resultado: $Resultado")
  $lines.Add("- BaseUrl: $BaseUrl")
  $lines.Add("- Dados reais usados: nao")
  $lines.Add("- Producao/VPS/API externa acessadas: nao")
  $lines.Add("")
  $lines.Add("## Rotas")
  foreach ($row in $Rows) {
    $lines.Add("- $($row.Resultado): $($row.Caminho) - $($row.Detalhe)")
  }
  [System.IO.File]::WriteAllText($reportPath, (($lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
}

$safeBaseUrl = $BaseUrl.Trim().TrimEnd("/")
if ($safeBaseUrl -notmatch '^http://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]+)?$') {
  Write-Host "VALIDATION_RESULT=FALHA_SEO_SINTETICO_LOCAL"
  Write-Host "Motivo: BaseUrl deve ser HTTP localhost."
  exit 1
}

$reportFull = Resolve-RepoPath $RelatorioSaida
try {
  Invoke-WebRequest -Uri "$safeBaseUrl/api/health/readiness" -UseBasicParsing -TimeoutSec 3 | Out-Null
} catch {
  if ($PermitirEvidenciaExistente -and (Test-Path -LiteralPath $reportFull -PathType Leaf)) {
    $existing = Get-Content -LiteralPath $reportFull -Raw
    if ($existing -match 'Resultado:\s*OK_SEO_SINTETICO_LOCAL') {
      $rows = @([pscustomobject]@{ Caminho = "ALERTA_EVIDENCIA_EXISTENTE_REUTILIZADA"; Resultado = "OK"; Detalhe = "backend indisponivel em $safeBaseUrl; reutilizacao aceita apenas por parametro explicito" })
      Save-Report "OK_SEO_SINTETICO_LOCAL_EVIDENCIA_EXISTENTE" $rows
      Write-Host "ALERTA_EVIDENCIA_EXISTENTE_REUTILIZADA"
      Write-Host "VALIDATION_RESULT=OK_SEO_SINTETICO_LOCAL_EVIDENCIA_EXISTENTE"
      exit 0
    }
  }
  $rows = @([pscustomobject]@{ Caminho = "backend local"; Resultado = "PENDENTE"; Detalhe = "backend local indisponivel em $safeBaseUrl; evidencia antiga nao e aceita por padrao" })
  Save-Report "PENDENTE_SEO_SINTETICO_LOCAL" $rows
  Write-Host "VALIDATION_RESULT=PENDENTE_SEO_SINTETICO_LOCAL"
  Write-Host "Motivo: backend local indisponivel em $safeBaseUrl."
  exit 2
}

$rotas = @(
  "/acompanhantes/go/goiania",
  "/acompanhantes/go/goiania/setor-bueno",
  "/acompanhantes/df/brasilia",
  "/anuncios/demo-goiania-livre-premium",
  "/anuncios/demo-goiania-midia-restrita",
  "/sitemap.xml",
  "/robots.txt"
)

$rows = New-Object System.Collections.Generic.List[object]
$failures = New-Object System.Collections.Generic.List[string]
foreach ($rota in $rotas) {
  $url = "$safeBaseUrl/api/public/seo/rota?caminho=$([uri]::EscapeDataString($rota))"
  try {
    $response = Invoke-WebRequest -Uri $url -Headers @{ Accept = "application/json"; "X-Request-Id" = "bloco31-seo-sintetico" } -UseBasicParsing -TimeoutSec 10
    $body = [string]$response.Content
    $ok = ([int]$response.StatusCode -eq 200 -and $body -match [regex]::Escape($rota) -and -not ($body -match 'https?://|token|senha|password|secret'))
    $rows.Add([pscustomobject]@{ Caminho = $rota; Resultado = $(if ($ok) { "OK" } else { "FALHA" }); Detalhe = "status=$($response.StatusCode)" })
    if (-not $ok) { $failures.Add($rota) }
  } catch {
    $rows.Add([pscustomobject]@{ Caminho = $rota; Resultado = "FALHA"; Detalhe = $_.Exception.Message })
    $failures.Add($rota)
  }
}

if ($failures.Count -gt 0) {
  Save-Report "FALHA_SEO_SINTETICO_LOCAL" @($rows.ToArray())
  Write-Host "VALIDATION_RESULT=FALHA_SEO_SINTETICO_LOCAL"
  exit 1
}

Save-Report "OK_SEO_SINTETICO_LOCAL" @($rows.ToArray())
Write-Host "VALIDATION_RESULT=OK_SEO_SINTETICO_LOCAL"
exit 0
