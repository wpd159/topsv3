param(
  [string]$FixturePath = "backend/src/test/resources/fixtures/v3-dados-sinteticos.json",
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-30/relatorio-base-sintetica-local.md"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "ERRO: repositorio Git nao encontrado."
  exit 2
}

function Resolve-RepoPath {
  param([string]$Path)
  if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
  return (Join-Path $repoRoot ($Path -replace '/', [IO.Path]::DirectorySeparatorChar))
}

function Count-Items {
  param([object]$Value)
  return @($Value).Count
}

$fixtureFull = Resolve-RepoPath $FixturePath
if (-not (Test-Path -LiteralPath $fixtureFull -PathType Leaf)) {
  Write-Host "VALIDATION_RESULT=PENDENTE_FIXTURE_SINTETICA"
  exit 2
}

$data = Get-Content -LiteralPath $fixtureFull -Raw | ConvertFrom-Json
$cidades = @($data.cidades)
$bairros = @($data.bairros)
$anuncios = @($data.anuncios)
$midias = @($data.midias)
$beneficios = @($data.premiumBeneficios)
$metricas = @($data.metricasAgregadas)
$rotas = @($data.rotasCobertas)
$adminCasos = @($data.adminCasos)

$reportPath = Resolve-RepoPath $RelatorioSaida
$reportDir = Split-Path -Parent $reportPath
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

$lines = @(
  "# Relatorio - base sintetica local",
  "",
  "- Bloco: 30",
  "- Resultado: BASE_SINTETICA_LOCAL_CONSOLIDADA",
  "- Fixture: ``$FixturePath``",
  "- Banco acessado: nao",
  "- Docker acessado: nao",
  "- Producao/VPS acessados: nao",
  "- Dados reais usados: nao",
  "- Midia real/documento real usados: nao",
  "",
  "## Contagens",
  "",
  "- cidades: $($cidades.Count)",
  "- bairros: $($bairros.Count)",
  "- anuncios: $($anuncios.Count)",
  "- midias_livre: $(@($midias | Where-Object { $_.visibilidade -eq 'LIVRE' }).Count)",
  "- midias_restrita_18: $(@($midias | Where-Object { $_.visibilidade -eq 'RESTRITA_18' }).Count)",
  "- anuncios_ativos: $(@($anuncios | Where-Object { $_.status -eq 'ATIVO' }).Count)",
  "- anuncios_pausados: $(@($anuncios | Where-Object { $_.status -eq 'PAUSADO' }).Count)",
  "- anuncios_pendentes: $(@($anuncios | Where-Object { $_.status -eq 'PENDENTE' }).Count)",
  "- anuncios_rejeitados: $(@($anuncios | Where-Object { $_.status -eq 'REJEITADO' }).Count)",
  "- anuncios_controle_negativo: $(@($anuncios | Where-Object { $_.status -eq 'INVALIDO_CONTROLE' }).Count)",
  "- anuncios_premium_ativo: $(@($anuncios | Where-Object { $_.plano -eq 'PREMIUM_ATIVO' }).Count)",
  "- anuncios_premium_expirado: $(@($anuncios | Where-Object { $_.plano -eq 'PREMIUM_EXPIRADO' }).Count)",
  "- anuncios_gratuitos: $(@($anuncios | Where-Object { $_.plano -eq 'GRATUITO' }).Count)",
  "- beneficios_premium: $($beneficios.Count)",
  "- metricas_agregadas: $($metricas.Count)",
  "- rotas_cobertas: $($rotas.Count)",
  "- casos_admin: $($adminCasos.Count)",
  "",
  "## Uso previsto",
  "",
  "- Desenvolvimento local da V3 sem dependencia de backup/restauracao de producao.",
  "- Testes de rotas publicas, SEO, wizard `/anunciar`, admin read-only, moderacao local, Premium read-only, age gate e metricas agregadas.",
  "- A fixture nao substitui dados sanitizados de pre-staging/cutover."
)

[System.IO.File]::WriteAllText($reportPath, (($lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))

Write-Host "RELATORIO=$reportPath"
Write-Host "CIDADES=$($cidades.Count)"
Write-Host "BAIRROS=$($bairros.Count)"
Write-Host "ANUNCIOS=$($anuncios.Count)"
Write-Host "BENEFICIOS_PREMIUM=$($beneficios.Count)"
Write-Host "METRICAS_AGREGADAS=$($metricas.Count)"
Write-Host "ROTAS_COBERTAS=$($rotas.Count)"
Write-Host "VALIDATION_RESULT=OK_BASE_SINTETICA_LOCAL"
exit 0
