param(
  [string]$ContainerName = "topsv3-bloco29-pg17-quarentena",
  [string]$DatabaseName = "topsv3_quarentena",
  [string]$RelatorioValidacaoDados = "docs/v3/evidencias/bloco-29-5/relatorio-validacao-dados-quarentena-sanitizada.md",
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-29-5/relatorio-seo-quarentena-sanitizada.md"
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
  param([string]$FileName, [string[]]$Arguments, [string]$InputText = $null)
  $previous = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    if ($null -eq $InputText) { $output = & $FileName @Arguments 2>&1 }
    else { $output = $InputText | & $FileName @Arguments 2>&1 }
    $exitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previous
  }
  return [pscustomobject]@{ ExitCode = $exitCode; Output = @($output | ForEach-Object { $_.ToString() }) }
}

function Write-Report {
  param([string[]]$Lines)
  $reportPath = Resolve-RepoPath $RelatorioSaida
  $reportDir = Split-Path -Parent $reportPath
  New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
  [System.IO.File]::WriteAllText($reportPath, (($Lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
  Write-Host "RELATORIO=$reportPath"
}

function Assert-QuarantineRuntime {
  if ($ContainerName -ne "topsv3-bloco29-pg17-quarentena" -or $DatabaseName -ne "topsv3_quarentena") {
    Write-Report -Lines @(
      "# Relatorio - SEO com quarentena sanitizada",
      "",
      "- Resultado: FALHA_RECURSO_DOCKER_FORA_DO_PREFIXO_AUTORIZADO",
      "- Detalhe: este script so pode atuar no container topsv3-bloco29-pg17-quarentena e banco topsv3_quarentena.",
      "- Slugs reais versionados: nao"
    )
    Write-Host "VALIDATION_RESULT=FALHA_RECURSO_DOCKER_FORA_DO_PREFIXO_AUTORIZADO"
    exit 2
  }
}

Assert-QuarantineRuntime

$validationReport = Resolve-RepoPath $RelatorioValidacaoDados
if (-not (Test-Path -LiteralPath $validationReport -PathType Leaf)) {
  Write-Report -Lines @("# Relatorio - SEO com quarentena sanitizada", "", "- Resultado: PENDENTE_DADOS_QUARENTENA_SANITIZADOS")
  Write-Host "VALIDATION_RESULT=PENDENTE_DADOS_QUARENTENA_SANITIZADOS"
  exit 2
}
$validationText = [System.IO.File]::ReadAllText($validationReport)
if ($validationText -notmatch "OK_DADOS_QUARENTENA_SANITIZADOS") {
  Write-Report -Lines @("# Relatorio - SEO com quarentena sanitizada", "", "- Resultado: PENDENTE_DADOS_QUARENTENA_SANITIZADOS", "- Validacao de dados sanitizados nao aprovou.")
  Write-Host "VALIDATION_RESULT=PENDENTE_DADOS_QUARENTENA_SANITIZADOS"
  exit 2
}

$seoSql = @'
CREATE TEMP TABLE topsv3_seo(k text, v bigint);
INSERT INTO topsv3_seo
SELECT 'rotas_impactadas', 6;
INSERT INTO topsv3_seo
SELECT 'urls_desconhecidas_bloco28_classificadas', 0;
INSERT INTO topsv3_seo
SELECT 'urls_desconhecidas_bloco28_pendentes', 45;
DO $topsv3$
DECLARE
  r record;
  c bigint;
  urls bigint := 0;
  decisao bigint := 0;
  noindex bigint := 0;
  cidades bigint := 0;
  bairros bigint := 0;
BEGIN
  FOR r IN
    SELECT c.table_schema, c.table_name, c.column_name
    FROM information_schema.columns c
    JOIN information_schema.tables t ON t.table_schema = c.table_schema AND t.table_name = c.table_name
    WHERE c.table_schema NOT IN ('pg_catalog','information_schema','pg_toast')
      AND c.table_schema NOT LIKE 'pg_temp_%'
      AND c.table_schema NOT LIKE 'pg_toast_temp_%'
      AND t.table_type = 'BASE TABLE'
      AND c.column_name ~* 'slug'
      AND c.data_type IN ('text','character varying','character','citext')
      AND c.table_name ~* '(anuncio|advert|listing)'
  LOOP
    EXECUTE format('SELECT count(*) FROM %I.%I WHERE %I IS NOT NULL', r.table_schema, r.table_name, r.column_name) INTO c;
    urls := urls + c;
    EXECUTE format('SELECT count(*) FROM %I.%I WHERE %I IS NULL', r.table_schema, r.table_name, r.column_name) INTO c;
    decisao := decisao + c;
  END LOOP;
  INSERT INTO topsv3_seo VALUES ('urls_anuncio_preservaveis_estimadas', urls);
  INSERT INTO topsv3_seo VALUES ('urls_exigem_decisao_estimadas', decisao);

  FOR r IN
    SELECT table_schema, table_name, column_name
    FROM information_schema.columns
    WHERE table_schema NOT IN ('pg_catalog','information_schema','pg_toast')
      AND table_schema NOT LIKE 'pg_temp_%'
      AND table_schema NOT LIKE 'pg_toast_temp_%'
      AND column_name ~* '(status|situacao)'
      AND data_type IN ('text','character varying','character','citext')
  LOOP
    EXECUTE format('SELECT count(*) FROM %I.%I WHERE %I ~* %L', r.table_schema, r.table_name, r.column_name, '(bloq|remov|inativ|rejeit|rascunho|exclu)') INTO c;
    noindex := noindex + c;
  END LOOP;
  INSERT INTO topsv3_seo VALUES ('urls_noindex_removidas_estimadas', noindex);

  FOR r IN
    SELECT table_schema, table_name, column_name
    FROM information_schema.columns
    WHERE table_schema NOT IN ('pg_catalog','information_schema','pg_toast')
      AND table_schema NOT LIKE 'pg_temp_%'
      AND table_schema NOT LIKE 'pg_toast_temp_%'
      AND column_name ~* '(cidade|city)'
      AND data_type IN ('text','character varying','character','citext')
  LOOP
    EXECUTE format('SELECT count(*) FROM (SELECT %I FROM %I.%I WHERE %I IS NOT NULL GROUP BY %I HAVING count(*) >= 3) c',
      r.column_name, r.table_schema, r.table_name, r.column_name, r.column_name) INTO c;
    cidades := cidades + c;
  END LOOP;
  INSERT INTO topsv3_seo VALUES ('cidades_com_conteudo_suficiente_estimadas', cidades);

  FOR r IN
    SELECT table_schema, table_name, column_name
    FROM information_schema.columns
    WHERE table_schema NOT IN ('pg_catalog','information_schema','pg_toast')
      AND table_schema NOT LIKE 'pg_temp_%'
      AND table_schema NOT LIKE 'pg_toast_temp_%'
      AND column_name ~* '(bairro|district|neighborhood)'
      AND data_type IN ('text','character varying','character','citext')
  LOOP
    EXECUTE format('SELECT count(*) FROM (SELECT %I FROM %I.%I WHERE %I IS NOT NULL GROUP BY %I HAVING count(*) >= 3) b',
      r.column_name, r.table_schema, r.table_name, r.column_name, r.column_name) INTO c;
    bairros := bairros + c;
  END LOOP;
  INSERT INTO topsv3_seo VALUES ('bairros_com_conteudo_suficiente_estimados', bairros);
END
$topsv3$;
SELECT k || '=' || v::text FROM topsv3_seo ORDER BY k;
'@
$seo = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", "-i", $ContainerName, "psql", "-U", "postgres", "-d", $DatabaseName, "-At", "-v", "ON_ERROR_STOP=1") -InputText $seoSql
if ($seo.ExitCode -ne 0) {
  Write-Report -Lines @("# Relatorio - SEO com quarentena sanitizada", "", "- Resultado: FALHA_SEO_QUARENTENA_SANITIZADA", "- Consulta agregada falhou sem expor valores.")
  Write-Host "VALIDATION_RESULT=FALHA_SEO_QUARENTENA_SANITIZADA"
  exit 1
}

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# Relatorio - SEO com quarentena sanitizada")
$lines.Add("")
$lines.Add("- Bloco: 29.5")
$lines.Add("- Resultado: OK_SEO_QUARENTENA_SANITIZADA")
$lines.Add("- Banco aprovado para staging final: nao")
$lines.Add("- Slugs reais versionados: nao")
$lines.Add("- Lista bruta de anuncios reais versionada: nao")
$lines.Add("- Midia real versionada: nao")
$lines.Add("- Canonical/sitemap/robots de producao alterados: nao")
$lines.Add("")
$lines.Add("## Agregados")
foreach ($line in @($seo.Output | Where-Object { $_ -match '=' })) { $lines.Add("- $line") }
$lines.Add("- paginas_fracas_vazias: dependem de regra Pro por rota antes de cutover.")
$lines.Add("")
$lines.Add("## Impacto por rota")
$lines.Add("- `/`: avaliar agregados de cidade/UF antes de cutover.")
$lines.Add("- `/anuncios/[slug]`: usar apenas contagens, sem lista bruta de slugs versionada.")
$lines.Add("- `/acompanhantes/[uf]/[cidade]`: validar conteudo suficiente por agregado.")
$lines.Add("- `/acompanhantes/[uf]/[cidade]/[bairro]`: validar conteudo suficiente por agregado.")
$lines.Add("- `/sitemap.xml`: nao alterar producao neste bloco.")
$lines.Add("- `/robots.txt`: nao alterar producao neste bloco.")
Write-Report -Lines @($lines.ToArray())
Write-Host "VALIDATION_RESULT=OK_SEO_QUARENTENA_SANITIZADA"
exit 0
