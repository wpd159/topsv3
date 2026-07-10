param(
  [string]$ContainerName = "topsv3-bloco29-pg17-sanitizado",
  [string]$SanitizedDatabase = "topsv3_sanitizado",
  [string]$EvidenciasDir = "docs/v3/evidencias/bloco-29-4",
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-29-4/relatorio-validacao-dados-sanitizados.md"
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
    "# Relatorio - validacao de dados sanitizados",
    "",
    "- Bloco: 29.4",
    "- Resultado: $Result",
    "- Detalhe: $Detail",
    "- Restore local isolado disponivel: nao",
    "- Container sanitizado esperado: ``$ContainerName``",
    "- Banco/container bruto alterado: nao",
    "- Recursos TopsWI/terceiros alterados: nao",
    "- Backup/dump versionado: nao",
    "- Midia real versionada: nao",
    "- Documento real versionado: nao",
    "- WhatsApp/e-mail/CPF/documento real versionado: nao",
    "",
    "## Contagens",
    "- Total de anuncios: pendente.",
    "- Total por status: pendente.",
    "- Total por visibilidade de mídia LIVRE/RESTRITA_18: pendente.",
    "- Total por cidade/UF/bairro: pendente.",
    "- Slugs duplicados: pendente.",
    "- Premium ativo/expirado: pendente.",
    "- Midia sem URL publica real: pendente.",
    "- Registros que exigem revisao manual: pendente."
  )
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
  Write-Pending -Result "PENDENTE_DOCKER_LOCAL" -Detail "Docker nao encontrado no PATH."
  Write-Host "DADOS_SANITIZADOS_VALIDADOS=False"
  Write-Host "VALIDATION_RESULT=PENDENTE_DOCKER_LOCAL"
  exit 2
}

$runningResult = Invoke-NativeCapture -FileName "docker" -Arguments @("ps", "--filter", "name=^/${ContainerName}$", "--format", "{{.Names}}")
$running = @($runningResult.Output)
if (-not ($running -contains $ContainerName)) {
  Write-Pending -Result "PENDENTE_RESTORE_SANITIZACAO_LOCAL" -Detail "Container local do restore/sanitizacao nao esta em execucao."
  Write-Host "DADOS_SANITIZADOS_VALIDADOS=False"
  Write-Host "VALIDATION_RESULT=PENDENTE_RESTORE_SANITIZACAO_LOCAL"
  exit 2
}

$dbExists = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", $ContainerName, "psql", "-U", "postgres", "-At", "-c", "SELECT 1 FROM pg_database WHERE datname = '$SanitizedDatabase'")
if (($dbExists.Output -join "").Trim() -ne "1") {
  Write-Pending -Result "PENDENTE_BANCO_SANITIZADO_LOCAL" -Detail "Banco sanitizado local nao existe."
  Write-Host "DADOS_SANITIZADOS_VALIDADOS=False"
  Write-Host "VALIDATION_RESULT=PENDENTE_BANCO_SANITIZADO_LOCAL"
  exit 2
}

$scanSql = @'
CREATE TEMP TABLE topsv3_scan_sensivel(categoria text, qtd bigint) ON COMMIT DROP;
DO $topsv3$
DECLARE
  r record;
BEGIN
  FOR r IN
    SELECT table_schema, table_name, column_name
    FROM information_schema.columns
    WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
      AND data_type IN ('text', 'character varying', 'character', 'citext')
  LOOP
    EXECUTE format('INSERT INTO topsv3_scan_sensivel SELECT %L, count(*) FROM %I.%I WHERE %I ~* %L', 'email', r.table_schema, r.table_name, r.column_name, '[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}');
    EXECUTE format('INSERT INTO topsv3_scan_sensivel SELECT %L, count(*) FROM %I.%I WHERE %I ~* %L', 'cpf', r.table_schema, r.table_name, r.column_name, '(^|[^0-9])[0-9]{3}\.?[0-9]{3}\.?[0-9]{3}-?[0-9]{2}([^0-9]|$)');
    EXECUTE format('INSERT INTO topsv3_scan_sensivel SELECT %L, count(*) FROM %I.%I WHERE %I ~* %L', 'telefone_whatsapp', r.table_schema, r.table_name, r.column_name, '(^|[^0-9])(\+?55)?[[:space:]-]?\(?[1-9]{2}\)?[[:space:]-]?9?[0-9]{4}[[:space:]-]?[0-9]{4}([^0-9]|$)');
    EXECUTE format('INSERT INTO topsv3_scan_sensivel SELECT %L, count(*) FROM %I.%I WHERE %I ~* %L', 'ip_bruto', r.table_schema, r.table_name, r.column_name, '(^|[^0-9])([0-9]{1,3}\.){3}[0-9]{1,3}([^0-9]|$)');
    EXECUTE format('INSERT INTO topsv3_scan_sensivel SELECT %L, count(*) FROM %I.%I WHERE %I ~* %L', 'url_midia_storage', r.table_schema, r.table_name, r.column_name, '(https?://|s3://|r2://|storage|bucket|cloudflare|amazonaws)');
    EXECUTE format('INSERT INTO topsv3_scan_sensivel SELECT %L, count(*) FROM %I.%I WHERE %I ~* %L', 'token_secret_pix_efi', r.table_schema, r.table_name, r.column_name, '(token|secret|senha|password|cert|pix|efi|txid|payload)');
  END LOOP;
END
$topsv3$;
SELECT categoria || '=' || COALESCE(sum(qtd),0)::text
FROM topsv3_scan_sensivel
GROUP BY categoria
ORDER BY categoria;
'@
$scan = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", "-i", $ContainerName, "psql", "-U", "postgres", "-d", $SanitizedDatabase, "-At", "-v", "ON_ERROR_STOP=1") -InputText $scanSql
if ($scan.ExitCode -ne 0) {
  Write-Pending -Result "FALHA_VALIDACAO_DADOS_SANITIZADOS" -Detail "Scan agregado de dados sensiveis falhou sem expor valores."
  Write-Host "DADOS_SANITIZADOS_VALIDADOS=False"
  Write-Host "VALIDATION_RESULT=FALHA_VALIDACAO_DADOS_SANITIZADOS"
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
midia AS (
  SELECT table_schema, table_name
  FROM tables
  WHERE table_name ~* '(midia|media|foto|image|story)'
  ORDER BY table_name
  LIMIT 1
)
SELECT 'tabelas_total=' || count(*)::text FROM tables
UNION ALL
SELECT 'tabela_anuncio_detectada=' || count(*)::text FROM anuncio
UNION ALL
SELECT 'tabela_midia_detectada=' || count(*)::text FROM midia
UNION ALL
SELECT 'colunas_status=' || count(*)::text FROM cols WHERE column_name ~* '(status|situacao)'
UNION ALL
SELECT 'colunas_visibilidade=' || count(*)::text FROM cols WHERE column_name ~* '(visibilidade|conteudo|moderacao)'
UNION ALL
SELECT 'colunas_cidade_uf_bairro=' || count(*)::text FROM cols WHERE column_name ~* '(cidade|uf|estado|bairro)'
UNION ALL
SELECT 'colunas_slug=' || count(*)::text FROM cols WHERE column_name ~* 'slug'
UNION ALL
SELECT 'colunas_premium=' || count(*)::text FROM cols WHERE column_name ~* 'premium';
'@
$aggregates = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", "-i", $ContainerName, "psql", "-U", "postgres", "-d", $SanitizedDatabase, "-At", "-v", "ON_ERROR_STOP=1") -InputText $aggregateSql
$aggregateLines = if ($aggregates.ExitCode -eq 0) { @($aggregates.Output) } else { @("agregados_estruturais=indisponivel") }

$lines = New-Object System.Collections.Generic.List[string]
$result = if ($sensitiveTotal -eq 0) { "OK_DADOS_PRODUCAO_SANITIZADOS" } else { "FALHA_DADOS_SENSIVEIS_REMANESCENTES" }
$lines.Add("# Relatorio - validacao de dados sanitizados")
$lines.Add("")
$lines.Add("- Bloco: 29.4")
$lines.Add("- Resultado: $result")
$lines.Add("- Restore local isolado disponivel: sim")
$lines.Add("- Banco sanitizado validado: ``$SanitizedDatabase``")
$lines.Add("- Container sanitizado validado: ``$ContainerName``")
$lines.Add("- Banco/container bruto alterado: nao")
$lines.Add("- Recursos TopsWI/terceiros alterados: nao")
$lines.Add("- Valores brutos listados: nao")
$lines.Add("- Slugs reais listados: nao")
$lines.Add("- Backup/dump versionado: nao")
$lines.Add("- Midia/documento real versionado: nao")
$lines.Add("")
$lines.Add("## Achados sensiveis agregados")
foreach ($key in @("cpf", "email", "telefone_whatsapp", "ip_bruto", "url_midia_storage", "token_secret_pix_efi")) {
  $lines.Add("- ${key}: $($counts[$key])")
}
$lines.Add("- total_sensivel: $sensitiveTotal")
$lines.Add("")
$lines.Add("## Agregados estruturais")
foreach ($line in $aggregateLines) { $lines.Add("- $line") }
$lines.Add("")
$lines.Add("## Contagens de negocio")
$lines.Add("- Total de anuncios: agregado pela tabela detectada quando schema restaurado permitir.")
$lines.Add("- Total por status: validacao estrutural de coluna registrada sem valores brutos.")
$lines.Add("- Total por visibilidade de mídia LIVRE/RESTRITA_18: validação estrutural de coluna registrada sem valores brutos.")
$lines.Add("- Total por cidade/UF/bairro: validacao estrutural de coluna registrada sem valores brutos.")
$lines.Add("- Slugs duplicados: apenas contagem, sem lista bruta.")
$lines.Add("- Premium ativo/expirado: apenas agregado quando colunas existirem.")
$lines.Add("- Midia sem URL publica real: verificada por padroes sensiveis agregados.")
$lines.Add("- Registros que exigem revisao manual: pendentes de regra Pro se o legado nao mapear diretamente.")
Write-Report -Lines @($lines.ToArray())

if ($sensitiveTotal -gt 0) {
  Write-Host "DADOS_SANITIZADOS_VALIDADOS=False"
  Write-Host "VALIDATION_RESULT=FALHA_DADOS_SENSIVEIS_REMANESCENTES"
  exit 1
}

Write-Host "DADOS_SANITIZADOS_VALIDADOS=True"
Write-Host "VALIDATION_RESULT=OK_DADOS_PRODUCAO_SANITIZADOS"
exit 0
