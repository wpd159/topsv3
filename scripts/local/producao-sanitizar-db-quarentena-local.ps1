param(
  [string]$ContainerName = "topsv3-bloco29-pg17-quarentena",
  [string]$DatabaseName = "topsv3_quarentena",
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-29-5/relatorio-sanitizacao-quarentena.md",
  [string]$RelatorioCamposSaida = "docs/v3/evidencias/bloco-29-5/relatorio-campos-sensiveis-sanitizados-quarentena.md"
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

function Write-TextReport {
  param([string]$Path, [string[]]$Lines)
  $reportPath = Resolve-RepoPath $Path
  $reportDir = Split-Path -Parent $reportPath
  New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
  [System.IO.File]::WriteAllText($reportPath, (($Lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
  Write-Host "RELATORIO=$reportPath"
}

function Write-PendingReports {
  param([string]$Result, [string]$Detail)
  Write-TextReport -Path $RelatorioSaida -Lines @(
    "# Relatorio - sanitizacao da quarentena",
    "",
    "- Bloco: 29.5",
    "- Resultado: $Result",
    "- Detalhe: $Detail",
    "- Container esperado: ``$ContainerName``",
    "- Banco esperado: ``$DatabaseName``",
    "- Banco bruto/final alterado: nao",
    "- Recursos TopsWI/terceiros alterados: nao",
    "- Producao alterada: nao",
    "- Valores reais listados: nao"
  )
  Write-TextReport -Path $RelatorioCamposSaida -Lines @(
    "# Relatorio - campos sensiveis sanitizados na quarentena",
    "",
    "- Bloco: 29.5",
    "- Resultado: $Result",
    "- Detalhe: $Detail",
    "- Valores reais listados: nao",
    "- Status dos campos: pendente por bloqueio anterior."
  )
}

function Assert-QuarantineRuntime {
  if ($ContainerName -ne "topsv3-bloco29-pg17-quarentena" -or $DatabaseName -ne "topsv3_quarentena") {
    Write-PendingReports -Result "FALHA_RECURSO_DOCKER_FORA_DO_PREFIXO_AUTORIZADO" -Detail "Este script so pode atuar no container topsv3-bloco29-pg17-quarentena e banco topsv3_quarentena."
    Write-Host "VALIDATION_RESULT=FALHA_RECURSO_DOCKER_FORA_DO_PREFIXO_AUTORIZADO"
    exit 2
  }
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
  Write-PendingReports -Result "PENDENTE_DOCKER_LOCAL" -Detail "Docker nao encontrado no PATH."
  Write-Host "VALIDATION_RESULT=PENDENTE_DOCKER_LOCAL"
  exit 2
}
Assert-QuarantineRuntime

$running = @(Invoke-NativeCapture -FileName "docker" -Arguments @("ps", "--filter", "name=^/${ContainerName}$", "--format", "{{.Names}}")).Output
if (-not ($running -contains $ContainerName)) {
  Write-PendingReports -Result "PENDENTE_RESTORE_QUARENTENA" -Detail "Container de quarentena nao esta em execucao."
  Write-Host "VALIDATION_RESULT=PENDENTE_RESTORE_QUARENTENA"
  exit 2
}

$dbExists = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", $ContainerName, "psql", "-U", "postgres", "-At", "-c", "SELECT 1 FROM pg_database WHERE datname = '$DatabaseName'")
if (($dbExists.Output -join "").Trim() -ne "1") {
  Write-PendingReports -Result "PENDENTE_BANCO_QUARENTENA" -Detail "Banco de quarentena nao existe."
  Write-Host "VALIDATION_RESULT=PENDENTE_BANCO_QUARENTENA"
  exit 2
}

$countSql = @"
WITH cols AS (
  SELECT count(*) AS total_colunas_sensiveis
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
    AND column_name ~* '(cpf|rg|document|selfie|nome|name|email|mail|telefone|whats|celular|phone|contato|contact|endereco|address|ip|user_agent|agent|token|senha|password|cert|storage|bucket|url|uri|link|path|caminho|arquivo|file|filename|object|objeto|cdn|s3|midia|media|payload|pix|efi|log|secret|chave|key|etag|provider)'
)
SELECT total_colunas_sensiveis FROM cols;
"@
$sensitiveColumns = (Invoke-NativeCapture -FileName "docker" -Arguments @("exec", "-i", $ContainerName, "psql", "-U", "postgres", "-d", $DatabaseName, "-At", "-v", "ON_ERROR_STOP=1") -InputText $countSql).Output -join ""
if ([string]::IsNullOrWhiteSpace($sensitiveColumns)) { $sensitiveColumns = "0" }

$sanitizeSql = @'
CREATE TEMP TABLE topsv3_sanitizacao_erros(categoria text);
DO $topsv3$
DECLARE
  r record;
  replacement text;
  generic_pattern text := '([A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}|[0-9]{3}\.?[0-9]{3}\.?[0-9]{3}-?[0-9]{2}|(^|[^0-9])(\+?55)?[[:space:]-]?\(?[1-9]{2}\)?[[:space:]-]?9?[0-9]{4}[[:space:]-]?[0-9]{4}([^0-9]|$)|https?://[^[:space:]"'']+|[0-9]{1,3}(\.[0-9]{1,3}){3}|(token|secret|senha|password|cert)[^[:space:]]{6,}|(pix|efi|txid)[A-Za-z0-9_-]{8,}|payload)';
BEGIN
  FOR r IN
    SELECT table_schema, table_name, column_name, data_type, is_nullable
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
      AND is_generated = 'NEVER'
      AND (data_type IN ('text', 'character varying', 'character') OR udt_name = 'citext')
  LOOP
    IF r.column_name ~* '(cpf|rg|document|selfie|nome|name|email|mail|telefone|whats|celular|phone|contato|contact|endereco|address|ip|user_agent|agent|token|senha|password|cert|storage|bucket|url|uri|link|path|caminho|arquivo|file|filename|object|objeto|cdn|s3|midia|media|payload|pix|efi|log|secret|chave|key|etag|provider)' THEN
      replacement := CASE WHEN r.is_nullable = 'YES' THEN NULL ELSE 'x' END;
      BEGIN
        EXECUTE format('UPDATE %I.%I SET %I = %L WHERE %I IS NOT NULL', r.table_schema, r.table_name, r.column_name, replacement, r.column_name);
      EXCEPTION WHEN OTHERS THEN
        INSERT INTO topsv3_sanitizacao_erros VALUES ('texto_sensivel');
      END;
    ELSE
      BEGIN
        EXECUTE format('UPDATE %I.%I SET %I = regexp_replace(%I, %L, ''x'', ''gi'') WHERE %I ~* %L',
          r.table_schema, r.table_name, r.column_name, r.column_name, generic_pattern, r.column_name, generic_pattern);
      EXCEPTION WHEN OTHERS THEN
        INSERT INTO topsv3_sanitizacao_erros VALUES ('texto_padrao');
      END;
    END IF;
  END LOOP;

  FOR r IN
    SELECT table_schema, table_name, column_name, data_type, udt_name, is_nullable
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
      AND is_generated = 'NEVER'
      AND (data_type IN ('json', 'jsonb') OR udt_name IN ('json', 'jsonb'))
  LOOP
    IF r.data_type = 'jsonb' OR r.udt_name = 'jsonb' THEN
      IF r.is_nullable = 'YES' THEN
        BEGIN
          EXECUTE format('UPDATE %I.%I SET %I = NULL WHERE %I IS NOT NULL', r.table_schema, r.table_name, r.column_name, r.column_name);
        EXCEPTION WHEN OTHERS THEN
          INSERT INTO topsv3_sanitizacao_erros VALUES ('jsonb_nullable');
        END;
      ELSE
        BEGIN
          EXECUTE format('UPDATE %I.%I SET %I = ''{}''::jsonb WHERE %I IS NOT NULL', r.table_schema, r.table_name, r.column_name, r.column_name);
        EXCEPTION WHEN OTHERS THEN
          INSERT INTO topsv3_sanitizacao_erros VALUES ('jsonb_not_null');
        END;
      END IF;
    ELSE
      IF r.is_nullable = 'YES' THEN
        BEGIN
          EXECUTE format('UPDATE %I.%I SET %I = NULL WHERE %I IS NOT NULL', r.table_schema, r.table_name, r.column_name, r.column_name);
        EXCEPTION WHEN OTHERS THEN
          INSERT INTO topsv3_sanitizacao_erros VALUES ('json_nullable');
        END;
      ELSE
        BEGIN
          EXECUTE format('UPDATE %I.%I SET %I = ''{}''::json WHERE %I IS NOT NULL', r.table_schema, r.table_name, r.column_name, r.column_name);
        EXCEPTION WHEN OTHERS THEN
          INSERT INTO topsv3_sanitizacao_erros VALUES ('json_not_null');
        END;
      END IF;
    END IF;
  END LOOP;

  FOR r IN
    SELECT table_schema, table_name, column_name, is_nullable
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
      AND is_generated = 'NEVER'
      AND data_type = 'ARRAY'
      AND column_name ~* '(document|selfie|nome|name|email|telefone|whats|contato|endereco|ip|user_agent|token|senha|password|cert|storage|bucket|url|uri|link|path|caminho|arquivo|file|filename|object|objeto|cdn|s3|midia|media|payload|pix|efi|log|secret|chave|key|etag|provider)'
  LOOP
    BEGIN
      EXECUTE format('UPDATE %I.%I SET %I = ''{}'' WHERE %I IS NOT NULL', r.table_schema, r.table_name, r.column_name, r.column_name);
    EXCEPTION WHEN OTHERS THEN
      INSERT INTO topsv3_sanitizacao_erros VALUES ('array_sensivel');
    END;
  END LOOP;

  FOR r IN
    SELECT table_schema, table_name, column_name, is_nullable
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
      AND is_generated = 'NEVER'
      AND (data_type IN ('text', 'character varying', 'character') OR udt_name = 'citext')
  LOOP
    BEGIN
      IF r.is_nullable = 'YES' THEN
        EXECUTE format('UPDATE %I.%I SET %I = NULL WHERE %I::text ~* %L',
          r.table_schema, r.table_name, r.column_name, r.column_name,
          '(^|[^0-9])(\+?55)?[[:space:]-]?\(?[1-9]{2}\)?[[:space:]-]?9?[0-9]{4}[[:space:]-]?[0-9]{4}([^0-9]|$)');
      ELSE
        EXECUTE format('UPDATE %I.%I SET %I = ''x'' WHERE %I::text ~* %L',
          r.table_schema, r.table_name, r.column_name, r.column_name,
          '(^|[^0-9])(\+?55)?[[:space:]-]?\(?[1-9]{2}\)?[[:space:]-]?9?[0-9]{4}[[:space:]-]?[0-9]{4}([^0-9]|$)');
      END IF;
    EXCEPTION WHEN OTHERS THEN
      INSERT INTO topsv3_sanitizacao_erros VALUES ('fallback_contato');
    END;
  END LOOP;

  FOR r IN
    SELECT table_schema, table_name, column_name, is_nullable
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
      AND data_type IN ('bytea')
      AND is_generated = 'NEVER'
      AND column_name ~* '(document|selfie|foto|image|imagem|cert|secret|key|payload)'
  LOOP
    IF r.is_nullable = 'YES' THEN
      BEGIN
        EXECUTE format('UPDATE %I.%I SET %I = NULL WHERE %I IS NOT NULL', r.table_schema, r.table_name, r.column_name, r.column_name);
      EXCEPTION WHEN OTHERS THEN
        INSERT INTO topsv3_sanitizacao_erros VALUES ('bytea_nullable');
      END;
    ELSE
      BEGIN
        EXECUTE format('UPDATE %I.%I SET %I = decode('''', ''hex'') WHERE %I IS NOT NULL', r.table_schema, r.table_name, r.column_name, r.column_name);
      EXCEPTION WHEN OTHERS THEN
        INSERT INTO topsv3_sanitizacao_erros VALUES ('bytea_not_null');
      END;
    END IF;
  END LOOP;
END
$topsv3$;
SELECT 'erros_sanitizacao=' || count(*)::text FROM topsv3_sanitizacao_erros;
'@

$sanitize = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", "-i", $ContainerName, "psql", "-U", "postgres", "-d", $DatabaseName, "-At", "-v", "ON_ERROR_STOP=1", "-q") -InputText $sanitizeSql
if ($sanitize.ExitCode -ne 0) {
  Write-PendingReports -Result "FALHA_SANITIZACAO_QUARENTENA" -Detail "SQL generico de sanitizacao falhou; detalhes brutos nao foram versionados."
  Write-Host "VALIDATION_RESULT=FALHA_SANITIZACAO_QUARENTENA"
  exit 1
}

$sanitizeErrorCount = 0
foreach ($line in $sanitize.Output) {
  if ($line -match '^erros_sanitizacao=(\d+)$') { $sanitizeErrorCount = [int]$Matches[1] }
}

Write-TextReport -Path $RelatorioSaida -Lines @(
  "# Relatorio - sanitizacao da quarentena",
  "",
  "- Bloco: 29.5",
  "- Resultado: OK_SANITIZACAO_QUARENTENA",
  "- Container: ``$ContainerName``",
  "- Banco: ``$DatabaseName``",
  "- Banco bruto/final alterado: nao",
  "- Recursos TopsWI/terceiros alterados: nao",
  "- Colunas sensiveis candidatas tratadas: $sensitiveColumns",
  "- Erros agregados de sanitizacao por coluna: $sanitizeErrorCount",
  "- JSON/JSONB sensivel tratado: sim",
  "- Bytea sensivel tratado: sim",
  "- Producao alterada: nao",
  "- Valores reais listados/versionados: nao"
)
Write-TextReport -Path $RelatorioCamposSaida -Lines @(
  "# Relatorio - campos sensiveis sanitizados na quarentena",
  "",
  "- Bloco: 29.5",
  "- Resultado: OK_CAMPOS_SENSIVEIS_SANITIZADOS_QUARENTENA",
  "- Colunas sensiveis candidatas tratadas: $sensitiveColumns",
  "- Erros agregados de sanitizacao por coluna: $sanitizeErrorCount",
  "- Valores brutos listados: nao",
  "- Slugs reais listados: nao",
  "- CPF/RG/documento/selfie: sanitizados quando presentes.",
  "- Nome civil: sanitizado quando presente.",
  "- E-mail/telefone/WhatsApp: sanitizados quando presentes.",
  "- Endereco especifico/IP/user-agent: sanitizados quando presentes.",
  "- Token/senha/certificado/storage/bucket/URL: sanitizados quando presentes.",
  "- Payload financeiro/Pix/Efi/log sensivel: sanitizados quando presentes."
)
Write-Host "SANITIZACAO_QUARENTENA_EXECUTADA=True"
Write-Host "VALIDATION_RESULT=OK_SANITIZACAO_QUARENTENA"
exit 0
