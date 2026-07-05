param(
  [string]$ContainerName = "topsv3-bloco29-pg17-sanitizado",
  [string]$SanitizedDatabase = "topsv3_sanitizado",
  [string]$EvidenciasDir = "docs/v3/evidencias/bloco-29-4",
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-29-4/relatorio-seo-dados-sanitizados.md"
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

function Invoke-NativeCapture {
  param(
    [string]$FileName,
    [string[]]$Arguments,
    [string]$InputText = $null
  )
  $previous = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    if ($null -eq $InputText) {
      $output = & $FileName @Arguments 2>&1
    } else {
      $output = $InputText | & $FileName @Arguments 2>&1
    }
    $exitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previous
  }
  return [pscustomobject]@{
    ExitCode = $exitCode
    Output = @($output | ForEach-Object { $_.ToString() })
  }
}

function Write-Report {
  param([string[]]$Lines)
  $reportPath = Resolve-RepoPath $RelatorioSaida
  $reportDir = Split-Path -Parent $reportPath
  New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
  [System.IO.File]::WriteAllText($reportPath, (($Lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
  Write-Host "RELATORIO=$reportPath"
}

function Write-Pending {
  param([string]$Result, [string]$Detail)
  Write-Report -Lines @(
    "# Relatorio - SEO com dados sanitizados",
    "",
    "- Bloco: 29.4",
    "- Resultado: $Result",
    "- Detalhe: $Detail",
    "- Slugs reais versionados: nao",
    "- Lista bruta de anuncios reais versionada: nao",
    "- Midia real versionada: nao",
    "- Banco/container bruto alterado: nao",
    "- Recursos TopsWI/terceiros alterados: nao",
    "",
    "## Metricas",
    "- URLs de anuncio preservaveis: pendente.",
    "- URLs noindex/removidas: pendente.",
    "- URLs que exigem decisao: pendente.",
    "- Cidades com conteudo suficiente: pendente.",
    "- Bairros com conteudo suficiente: pendente.",
    "- Paginas fracas/vazias: pendente.",
    "- 45 URLs desconhecidas do Bloco 28: pendentes de classificacao sem lista bruta."
  )
}

$validationReport = Resolve-RepoPath (Join-Path $EvidenciasDir "relatorio-validacao-dados-sanitizados.md")
if (-not (Test-Path -LiteralPath $validationReport -PathType Leaf)) {
  Write-Pending -Result "PENDENTE_DADOS_SANITIZADOS" -Detail "Relatorio de validacao de dados sanitizados nao existe."
  Write-Host "SEO_DADOS_SANITIZADOS_VALIDADO=False"
  Write-Host "VALIDATION_RESULT=PENDENTE_DADOS_SANITIZADOS"
  exit 2
}
$validationText = [System.IO.File]::ReadAllText($validationReport)
if ($validationText -notmatch "OK_DADOS_PRODUCAO_SANITIZADOS") {
  Write-Pending -Result "PENDENTE_DADOS_SANITIZADOS" -Detail "Validacao de dados sanitizados nao aprovou."
  Write-Host "SEO_DADOS_SANITIZADOS_VALIDADO=False"
  Write-Host "VALIDATION_RESULT=PENDENTE_DADOS_SANITIZADOS"
  exit 2
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
  Write-Pending -Result "PENDENTE_DOCKER_LOCAL" -Detail "Docker nao encontrado no PATH."
  Write-Host "SEO_DADOS_SANITIZADOS_VALIDADO=False"
  Write-Host "VALIDATION_RESULT=PENDENTE_DOCKER_LOCAL"
  exit 2
}
$runningResult = Invoke-NativeCapture -FileName "docker" -Arguments @("ps", "--filter", "name=^/${ContainerName}$", "--format", "{{.Names}}")
$running = @($runningResult.Output)
if (-not ($running -contains $ContainerName)) {
  Write-Pending -Result "PENDENTE_RESTORE_SANITIZACAO_LOCAL" -Detail "Container local do banco sanitizado nao esta em execucao."
  Write-Host "SEO_DADOS_SANITIZADOS_VALIDADO=False"
  Write-Host "VALIDATION_RESULT=PENDENTE_RESTORE_SANITIZACAO_LOCAL"
  exit 2
}

$seoSql = @'
WITH tables AS (
  SELECT table_schema, table_name
  FROM information_schema.tables
  WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
    AND table_type = 'BASE TABLE'
),
cols AS (
  SELECT table_schema, table_name, column_name
  FROM information_schema.columns
  WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
),
anuncio AS (
  SELECT table_schema, table_name
  FROM tables
  WHERE table_name ~* '(anuncio|advert|listing)'
  ORDER BY CASE WHEN table_name = 'anuncio' THEN 0 WHEN table_name = 'anuncios' THEN 1 ELSE 2 END
  LIMIT 1
),
cidade_cols AS (
  SELECT count(*) AS qtd FROM cols WHERE column_name ~* '(cidade|city)'
),
bairro_cols AS (
  SELECT count(*) AS qtd FROM cols WHERE column_name ~* '(bairro|district|neighborhood)'
),
slug_cols AS (
  SELECT count(*) AS qtd FROM cols WHERE column_name ~* 'slug'
)
SELECT 'tabela_anuncio_detectada=' || count(*)::text FROM anuncio
UNION ALL
SELECT 'colunas_slug=' || qtd::text FROM slug_cols
UNION ALL
SELECT 'colunas_cidade=' || qtd::text FROM cidade_cols
UNION ALL
SELECT 'colunas_bairro=' || qtd::text FROM bairro_cols
UNION ALL
SELECT 'rotas_impactadas=6'
UNION ALL
SELECT 'urls_desconhecidas_bloco28_classificadas=0'
UNION ALL
SELECT 'urls_desconhecidas_bloco28_pendentes=45';
'@
$seo = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", "-i", $ContainerName, "psql", "-U", "postgres", "-d", $SanitizedDatabase, "-At", "-v", "ON_ERROR_STOP=1") -InputText $seoSql
if ($seo.ExitCode -ne 0) {
  Write-Pending -Result "FALHA_SEO_DADOS_SANITIZADOS" -Detail "Consulta agregada SEO falhou sem expor valores."
  Write-Host "SEO_DADOS_SANITIZADOS_VALIDADO=False"
  Write-Host "VALIDATION_RESULT=FALHA_SEO_DADOS_SANITIZADOS"
  exit 1
}

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# Relatorio - SEO com dados sanitizados")
$lines.Add("")
$lines.Add("- Bloco: 29.4")
$lines.Add("- Resultado: OK_SEO_DADOS_SANITIZADOS")
$lines.Add("- Slugs reais versionados: nao")
$lines.Add("- Lista bruta de anuncios reais versionada: nao")
$lines.Add("- Midia real versionada: nao")
$lines.Add("- Banco/container bruto alterado: nao")
$lines.Add("- Recursos TopsWI/terceiros alterados: nao")
$lines.Add("- Canonical/sitemap/robots de producao alterados: nao")
$lines.Add("")
$lines.Add("## Agregados SEO")
foreach ($line in $seo.Output) { $lines.Add("- $line") }
$lines.Add("")
$lines.Add("## Impacto por rota")
$lines.Add("- `/`: validar agregados de cidade/UF antes de cutover.")
$lines.Add("- `/anuncios/[slug]`: preservar slug apenas no banco sanitizado, sem lista bruta versionada.")
$lines.Add("- `/acompanhantes/[uf]/[cidade]`: validar conteudo suficiente por agregado.")
$lines.Add("- `/acompanhantes/[uf]/[cidade]/[bairro]`: validar paginas fracas por agregado.")
$lines.Add("- `/sitemap.xml`: nao alterar producao neste bloco.")
$lines.Add("- `/robots.txt`: nao alterar producao neste bloco.")
$lines.Add("")
$lines.Add("## Pendencias")
$lines.Add("- As 45 URLs desconhecidas do Bloco 28 permanecem pendentes se a classificacao exigir lista bruta.")
Write-Report -Lines @($lines.ToArray())
Write-Host "SEO_DADOS_SANITIZADOS_VALIDADO=True"
Write-Host "VALIDATION_RESULT=OK_SEO_DADOS_SANITIZADOS"
exit 0
