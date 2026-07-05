param(
  [string]$ContainerName = "topsv3-bloco29-pg17-quarentena",
  [string]$DatabaseName = "topsv3_quarentena",
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-29-5/relatorio-validacao-dados-quarentena-sanitizada.md"
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

function Write-Pending {
  param([string]$Result, [string]$Detail)
  Write-Report -Lines @(
    "# Relatorio - validacao de dados da quarentena sanitizada",
    "",
    "- Bloco: 29.5",
    "- Resultado: $Result",
    "- Detalhe: $Detail",
    "- Banco de quarentena validado: nao",
    "- Valores reais listados: nao"
  )
}

function Assert-QuarantineRuntime {
  if ($ContainerName -ne "topsv3-bloco29-pg17-quarentena" -or $DatabaseName -ne "topsv3_quarentena") {
    Write-Pending -Result "FALHA_RECURSO_DOCKER_FORA_DO_PREFIXO_AUTORIZADO" -Detail "Este script so pode validar o container topsv3-bloco29-pg17-quarentena e banco topsv3_quarentena."
    Write-Host "VALIDATION_RESULT=FALHA_RECURSO_DOCKER_FORA_DO_PREFIXO_AUTORIZADO"
    exit 2
  }
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
  Write-Pending -Result "PENDENTE_DOCKER_LOCAL" -Detail "Docker nao encontrado no PATH."
  Write-Host "VALIDATION_RESULT=PENDENTE_DOCKER_LOCAL"
  exit 2
}
Assert-QuarantineRuntime
$running = @(Invoke-NativeCapture -FileName "docker" -Arguments @("ps", "--filter", "name=^/${ContainerName}$", "--format", "{{.Names}}")).Output
if (-not ($running -contains $ContainerName)) {
  Write-Pending -Result "PENDENTE_CONTAINER_QUARENTENA" -Detail "Container de quarentena nao esta em execucao."
  Write-Host "VALIDATION_RESULT=PENDENTE_CONTAINER_QUARENTENA"
  exit 2
}

$scanSql = @'
CREATE TEMP TABLE topsv3_scan_sensivel(categoria text, qtd bigint);
DO $topsv3$
DECLARE
  r record;
  expr text;
BEGIN
  FOR r IN
    SELECT table_schema, table_name, column_name, data_type, udt_name
    FROM information_schema.columns
    WHERE table_schema NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
      AND table_schema NOT LIKE 'pg_temp_%'
      AND table_schema NOT LIKE 'pg_toast_temp_%'
      AND EXISTS (
        SELECT 1
        FROM information_schema.tables t
        WHERE t.table_schema = columns.table_schema
          AND t.table_name = columns.table_name
          AND t.table_type = 'BASE TABLE'
      )
      AND (data_type IN ('text', 'character varying', 'character', 'json', 'jsonb') OR udt_name IN ('json', 'jsonb', 'citext'))
  LOOP
    expr := format('%I::text', r.column_name);
    EXECUTE format('INSERT INTO topsv3_scan_sensivel SELECT %L, count(*) FROM %I.%I WHERE %s ~* %L', 'email', r.table_schema, r.table_name, expr, '[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}');
    EXECUTE format('INSERT INTO topsv3_scan_sensivel SELECT %L, count(*) FROM %I.%I WHERE %s ~* %L', 'cpf', r.table_schema, r.table_name, expr, '(^|[^0-9])[0-9]{3}\.?[0-9]{3}\.?[0-9]{3}-?[0-9]{2}([^0-9]|$)');
    EXECUTE format('INSERT INTO topsv3_scan_sensivel SELECT %L, count(*) FROM %I.%I WHERE %s ~* %L', 'telefone_whatsapp', r.table_schema, r.table_name, expr, '(^|[^0-9])(\+?55)?[[:space:]-]?\(?[1-9]{2}\)?[[:space:]-]?9?[0-9]{4}[[:space:]-]?[0-9]{4}([^0-9]|$)');
    EXECUTE format('INSERT INTO topsv3_scan_sensivel SELECT %L, count(*) FROM %I.%I WHERE %s ~* %L', 'ip_bruto', r.table_schema, r.table_name, expr, '(^|[^0-9])([0-9]{1,3}\.){3}[0-9]{1,3}([^0-9]|$)');
    EXECUTE format('INSERT INTO topsv3_scan_sensivel SELECT %L, count(*) FROM %I.%I WHERE %s ~* %L', 'url_midia_storage', r.table_schema, r.table_name, expr, '(https?://|s3://|r2://|storage|bucket|cloudflare|amazonaws)');
    EXECUTE format('INSERT INTO topsv3_scan_sensivel SELECT %L, count(*) FROM %I.%I WHERE %s ~* %L', 'token_secret_pix_efi', r.table_schema, r.table_name, expr, '((token|secret|senha|password|cert)[^[:space:]]{6,}|(pix|efi|txid)[A-Za-z0-9_-]{8,}|payload)');
  END LOOP;
END
$topsv3$;
SELECT categoria || '=' || COALESCE(sum(qtd),0)::text
FROM topsv3_scan_sensivel
GROUP BY categoria
ORDER BY categoria;
'@
$scan = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", "-i", $ContainerName, "psql", "-U", "postgres", "-d", $DatabaseName, "-At", "-v", "ON_ERROR_STOP=1") -InputText $scanSql
if ($scan.ExitCode -ne 0) {
  Write-Pending -Result "FALHA_SCAN_DADOS_QUARENTENA" -Detail "Scan agregado de dados sensiveis falhou sem expor valores."
  Write-Host "VALIDATION_RESULT=FALHA_SCAN_DADOS_QUARENTENA"
  exit 1
}

$counts = @{}
foreach ($line in $scan.Output) {
  if ($line -match '^([^=]+)=(\d+)$') { $counts[$Matches[1]] = [int64]$Matches[2] }
}
foreach ($key in @("cpf", "email", "telefone_whatsapp", "ip_bruto", "url_midia_storage", "token_secret_pix_efi")) {
  if (-not $counts.ContainsKey($key)) { $counts[$key] = 0 }
}
$sensitiveTotal = 0L
foreach ($value in $counts.Values) { $sensitiveTotal += [int64]$value }

$aggregateSql = @'
CREATE TEMP TABLE topsv3_agregados(k text, v bigint);
INSERT INTO topsv3_agregados
SELECT 'tabelas_total', count(*)
FROM information_schema.tables
WHERE table_schema NOT IN ('pg_catalog','information_schema','pg_toast')
  AND table_schema NOT LIKE 'pg_temp_%'
  AND table_schema NOT LIKE 'pg_toast_temp_%'
  AND table_type = 'BASE TABLE';
INSERT INTO topsv3_agregados
SELECT 'colunas_status', count(*)
FROM information_schema.columns
WHERE table_schema NOT IN ('pg_catalog','information_schema','pg_toast')
  AND table_schema NOT LIKE 'pg_temp_%'
  AND table_schema NOT LIKE 'pg_toast_temp_%'
  AND column_name ~* '(status|situacao)';
INSERT INTO topsv3_agregados
SELECT 'colunas_classificacao', count(*)
FROM information_schema.columns
WHERE table_schema NOT IN ('pg_catalog','information_schema','pg_toast')
  AND table_schema NOT LIKE 'pg_temp_%'
  AND table_schema NOT LIKE 'pg_toast_temp_%'
  AND column_name ~* '(classificacao|conteudo|moderacao)';
INSERT INTO topsv3_agregados
SELECT 'colunas_cidade_uf_bairro', count(*)
FROM information_schema.columns
WHERE table_schema NOT IN ('pg_catalog','information_schema','pg_toast')
  AND table_schema NOT LIKE 'pg_temp_%'
  AND table_schema NOT LIKE 'pg_toast_temp_%'
  AND column_name ~* '(cidade|uf|estado|bairro)';
INSERT INTO topsv3_agregados
SELECT 'colunas_slug', count(*)
FROM information_schema.columns
WHERE table_schema NOT IN ('pg_catalog','information_schema','pg_toast')
  AND table_schema NOT LIKE 'pg_temp_%'
  AND table_schema NOT LIKE 'pg_toast_temp_%'
  AND column_name ~* 'slug';
INSERT INTO topsv3_agregados
SELECT 'colunas_premium', count(*)
FROM information_schema.columns
WHERE table_schema NOT IN ('pg_catalog','information_schema','pg_toast')
  AND table_schema NOT LIKE 'pg_temp_%'
  AND table_schema NOT LIKE 'pg_toast_temp_%'
  AND column_name ~* 'premium';
DO $topsv3$
DECLARE
  r record;
  c bigint;
  anuncios bigint := 0;
  duplicate_slugs bigint := 0;
BEGIN
  FOR r IN
    SELECT table_schema, table_name
    FROM information_schema.tables
    WHERE table_schema NOT IN ('pg_catalog','information_schema','pg_toast')
      AND table_schema NOT LIKE 'pg_temp_%'
      AND table_schema NOT LIKE 'pg_toast_temp_%'
      AND table_type = 'BASE TABLE'
      AND table_name ~* '(anuncio|advert|listing)'
  LOOP
    EXECUTE format('SELECT count(*) FROM %I.%I', r.table_schema, r.table_name) INTO c;
    anuncios := anuncios + c;
  END LOOP;
  INSERT INTO topsv3_agregados VALUES ('linhas_tabelas_anuncio', anuncios);

  FOR r IN
    SELECT table_schema, table_name, column_name
    FROM information_schema.columns
    WHERE table_schema NOT IN ('pg_catalog','information_schema','pg_toast')
      AND table_schema NOT LIKE 'pg_temp_%'
      AND table_schema NOT LIKE 'pg_toast_temp_%'
      AND column_name ~* 'slug'
      AND data_type IN ('text','character varying','character','citext')
  LOOP
    EXECUTE format('SELECT count(*) FROM (SELECT %I FROM %I.%I WHERE %I IS NOT NULL GROUP BY %I HAVING count(*) > 1) d',
      r.column_name, r.table_schema, r.table_name, r.column_name, r.column_name) INTO c;
    duplicate_slugs := duplicate_slugs + c;
  END LOOP;
  INSERT INTO topsv3_agregados VALUES ('slugs_duplicados_agregado', duplicate_slugs);
END
$topsv3$;
SELECT k || '=' || v::text FROM topsv3_agregados ORDER BY k;
'@
$aggregates = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", "-i", $ContainerName, "psql", "-U", "postgres", "-d", $DatabaseName, "-At", "-v", "ON_ERROR_STOP=1") -InputText $aggregateSql
$aggregateLines = if ($aggregates.ExitCode -eq 0) { @($aggregates.Output | Where-Object { $_ -match '=' }) } else { @("agregados_estruturais=indisponivel") }

$result = if ($sensitiveTotal -eq 0) { "OK_DADOS_QUARENTENA_SANITIZADOS" } else { "FALHA_DADOS_SENSIVEIS_REMANESCENTES_QUARENTENA" }
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# Relatorio - validacao de dados da quarentena sanitizada")
$lines.Add("")
$lines.Add("- Bloco: 29.5")
$lines.Add("- Resultado: $result")
$lines.Add("- Banco de quarentena: ``$DatabaseName``")
$lines.Add("- Container de quarentena: ``$ContainerName``")
$lines.Add("- Banco aprovado para staging final: nao")
$lines.Add("- Valores brutos listados: nao")
$lines.Add("- Slugs reais listados: nao")
$lines.Add("- Backup/dump/midia/documento real versionado: nao")
$lines.Add("")
$lines.Add("## Achados sensiveis agregados")
foreach ($key in @("cpf", "email", "telefone_whatsapp", "ip_bruto", "url_midia_storage", "token_secret_pix_efi")) {
  $lines.Add("- ${key}: $($counts[$key])")
}
$lines.Add("- total_sensivel: $sensitiveTotal")
$lines.Add("")
$lines.Add("## Agregados estruturais e SEO")
foreach ($line in $aggregateLines) { $lines.Add("- $line") }
$lines.Add("")
$lines.Add("## Observacoes")
$lines.Add("- Contagem total de anuncios, status, classificacao LIVRE/BLOQUEADO, cidade/UF/bairro e Premium dependem de mapeamento Pro se o schema legado nao usar nomes detectaveis.")
$lines.Add("- Registros que exigem revisao manual permanecem agregados e sem valores reais.")
Write-Report -Lines @($lines.ToArray())

if ($sensitiveTotal -gt 0) {
  Write-Host "DADOS_QUARENTENA_SANITIZADOS_VALIDADOS=False"
  Write-Host "VALIDATION_RESULT=FALHA_DADOS_SENSIVEIS_REMANESCENTES_QUARENTENA"
  exit 1
}
Write-Host "DADOS_QUARENTENA_SANITIZADOS_VALIDADOS=True"
Write-Host "VALIDATION_RESULT=OK_DADOS_QUARENTENA_SANITIZADOS"
exit 0
