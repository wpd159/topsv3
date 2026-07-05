param(
  [string]$BackupPath = "C:\topsv3-auditoria-local\backups\topsdojob-db-20260529-163722.dump",
  [string]$ContainerName = "topsv3-bloco29-pg17-quarentena",
  [string]$DatabaseName = "topsv3_quarentena",
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-29-5/relatorio-diagnostico-fks-quarentena.md"
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

function Get-Domain {
  param([string]$Text)
  $lower = $Text.ToLowerInvariant()
  if ($lower -match 'anuncio|advert|listing') { return "anuncio" }
  if ($lower -match 'midia|media|foto|image|story') { return "midia" }
  if ($lower -match 'cidade|bairro|city|district|neighborhood') { return "cidade_bairro" }
  if ($lower -match 'premium|plano|beneficio') { return "premium" }
  if ($lower -match 'pagamento|payment|pix|efi|credito|ledger') { return "pagamentos" }
  if ($lower -match 'auditoria|audit|evento|log') { return "auditoria" }
  if ($lower -match 'moderacao|revisao|review') { return "moderacao" }
  if ($lower -match 'usuario|user|admin') { return "usuario_admin" }
  return "desconhecido"
}

function Assert-QuarantineRuntime {
  if ($ContainerName -ne "topsv3-bloco29-pg17-quarentena" -or $DatabaseName -ne "topsv3_quarentena") {
    Write-Report -Lines @(
      "# Relatorio - diagnostico agregado FK quarentena",
      "",
      "- Resultado: FALHA_RECURSO_DOCKER_FORA_DO_PREFIXO_AUTORIZADO",
      "- Detalhe: este script so pode atuar no container topsv3-bloco29-pg17-quarentena e banco topsv3_quarentena.",
      "- Dados reais listados: nao"
    )
    Write-Host "VALIDATION_RESULT=FALHA_RECURSO_DOCKER_FORA_DO_PREFIXO_AUTORIZADO"
    exit 2
  }
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
  Write-Report -Lines @("# Relatorio - diagnostico agregado FK quarentena", "", "- Resultado: PENDENTE_DOCKER_LOCAL")
  Write-Host "VALIDATION_RESULT=PENDENTE_DOCKER_LOCAL"
  exit 2
}
Assert-QuarantineRuntime

$backupFull = [System.IO.Path]::GetFullPath($BackupPath)
$backupDir = Split-Path -Parent $backupFull
$backupName = Split-Path -Leaf $backupFull
$toc = Invoke-NativeCapture -FileName "docker" -Arguments @("run", "--pull=never", "--rm", "-v", "${backupDir}:/backup:ro", "postgres:17", "pg_restore", "-l", "/backup/$backupName")
if ($toc.ExitCode -ne 0) {
  Write-Report -Lines @("# Relatorio - diagnostico agregado FK quarentena", "", "- Resultado: FALHA_TOC_BACKUP", "- Log bruto versionado: nao")
  Write-Host "VALIDATION_RESULT=FALHA_TOC_BACKUP"
  exit 1
}

$domainCounts = [ordered]@{
  anuncio = 0
  midia = 0
  cidade_bairro = 0
  premium = 0
  pagamentos = 0
  auditoria = 0
  moderacao = 0
  usuario_admin = 0
  desconhecido = 0
}
$fkLines = @($toc.Output | Where-Object { $_ -match '(?i)FK CONSTRAINT|FOREIGN KEY|ADD CONSTRAINT' })
foreach ($line in $fkLines) {
  $domain = Get-Domain -Text $line
  $domainCounts[$domain] = [int]$domainCounts[$domain] + 1
}

$heuristicSql = @'
CREATE TEMP TABLE topsv3_fk_heuristica(dominio text, relacoes bigint, orfaos bigint);
DO $topsv3$
DECLARE
  r record;
  parent_schema text;
  parent_table text;
  parent_column text;
  prefix text;
  c bigint;
  d text;
BEGIN
  FOR r IN
    SELECT c.table_schema, c.table_name, c.column_name
    FROM information_schema.columns c
    JOIN information_schema.tables t ON t.table_schema = c.table_schema AND t.table_name = c.table_name
    WHERE c.table_schema NOT IN ('pg_catalog','information_schema','pg_toast')
      AND c.table_schema NOT LIKE 'pg_temp_%'
      AND c.table_schema NOT LIKE 'pg_toast_temp_%'
      AND t.table_type = 'BASE TABLE'
      AND c.column_name ~* '.+_id$'
      AND c.column_name <> 'id'
  LOOP
    prefix := regexp_replace(r.column_name, '_id$', '', 'i');
    SELECT c.table_schema, c.table_name, c.column_name
      INTO parent_schema, parent_table, parent_column
    FROM information_schema.columns c
    JOIN information_schema.tables t ON t.table_schema = c.table_schema AND t.table_name = c.table_name
    WHERE c.table_schema NOT IN ('pg_catalog','information_schema','pg_toast')
      AND c.table_schema NOT LIKE 'pg_temp_%'
      AND c.table_schema NOT LIKE 'pg_toast_temp_%'
      AND t.table_type = 'BASE TABLE'
      AND c.column_name = 'id'
      AND (lower(c.table_name) = lower(prefix) OR lower(c.table_name) = lower(prefix || 's'))
    LIMIT 1;
    IF parent_table IS NOT NULL THEN
      d := CASE
        WHEN (r.table_name || ' ' || r.column_name) ~* '(anuncio|advert|listing)' THEN 'anuncio'
        WHEN (r.table_name || ' ' || r.column_name) ~* '(midia|media|foto|image|story)' THEN 'midia'
        WHEN (r.table_name || ' ' || r.column_name) ~* '(cidade|bairro|city|district|neighborhood)' THEN 'cidade_bairro'
        WHEN (r.table_name || ' ' || r.column_name) ~* '(premium|plano|beneficio)' THEN 'premium'
        WHEN (r.table_name || ' ' || r.column_name) ~* '(pagamento|payment|pix|efi|credito|ledger)' THEN 'pagamentos'
        WHEN (r.table_name || ' ' || r.column_name) ~* '(auditoria|audit|evento|log)' THEN 'auditoria'
        WHEN (r.table_name || ' ' || r.column_name) ~* '(moderacao|revisao|review)' THEN 'moderacao'
        WHEN (r.table_name || ' ' || r.column_name) ~* '(usuario|user|admin)' THEN 'usuario_admin'
        ELSE 'desconhecido'
      END;
      EXECUTE format('SELECT count(*) FROM %I.%I c WHERE c.%I IS NOT NULL AND NOT EXISTS (SELECT 1 FROM %I.%I p WHERE p.%I = c.%I)',
        r.table_schema, r.table_name, r.column_name, parent_schema, parent_table, parent_column, r.column_name) INTO c;
      INSERT INTO topsv3_fk_heuristica VALUES (d, 1, c);
    END IF;
    parent_schema := NULL;
    parent_table := NULL;
    parent_column := NULL;
  END LOOP;
END
$topsv3$;
SELECT dominio || '=' || COALESCE(sum(relacoes),0)::text || ':' || COALESCE(sum(orfaos),0)::text
FROM topsv3_fk_heuristica
GROUP BY dominio
ORDER BY dominio;
'@
$heuristic = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", "-i", $ContainerName, "psql", "-U", "postgres", "-d", $DatabaseName, "-At", "-v", "ON_ERROR_STOP=1") -InputText $heuristicSql
$heuristicLines = if ($heuristic.ExitCode -eq 0) { @($heuristic.Output | Where-Object { $_ -match '=' }) } else { @("heuristica_indisponivel=0:0") }

$diagnostic29_4 = Join-Path $repoRoot "docs/v3/evidencias/bloco-29-4/relatorio-diagnostico-pg-restore-raw.md"
$classifiedFailures = 0
if (Test-Path -LiteralPath $diagnostic29_4 -PathType Leaf) {
  $text = [System.IO.File]::ReadAllText($diagnostic29_4)
  if ($text -match 'CONSTRAINT/FK') { $classifiedFailures = 1 }
}

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# Relatorio - diagnostico agregado FK quarentena")
$lines.Add("")
$lines.Add("- Bloco: 29.5")
$lines.Add("- Resultado: OK_DIAGNOSTICO_FKS_QUARENTENA")
$lines.Add("- Banco de quarentena sanitizada: ``$DatabaseName``")
$lines.Add("- POST_DATA restaurado: nao")
$lines.Add("- Banco aprovado para staging final: nao")
$lines.Add("- FKs previstas no TOC do dump: $($fkLines.Count)")
$lines.Add("- Falhas `POST_DATA / FK` classificadas no diagnostico bruto sanitizado: $classifiedFailures")
$lines.Add("- Nomes de tabela/constraint/chave/slug/ID real listados: nao")
$lines.Add("")
$lines.Add("## FKs previstas por dominio inferido do TOC")
foreach ($key in $domainCounts.Keys) { $lines.Add("- ${key}: $($domainCounts[$key])") }
$lines.Add("")
$lines.Add("## Heuristica de orfandade na quarentena sanitizada")
$lines.Add("- Formato: dominio=relacoes_avaliadas:registros_orfaos_agregados")
foreach ($line in $heuristicLines) { $lines.Add("- $line") }
$lines.Add("")
$lines.Add("## Limites")
$lines.Add("- Como `POST_DATA` nao foi restaurado, as FKs finais nao existem no banco de quarentena.")
$lines.Add("- A heuristica nao substitui validacao final com restore completo consistente.")
$lines.Add("- Qualquer correcao local de orfaos exige novo bloco e decisao humana/Pro.")
Write-Report -Lines @($lines.ToArray())
Write-Host "VALIDATION_RESULT=OK_DIAGNOSTICO_FKS_QUARENTENA"
exit 0
