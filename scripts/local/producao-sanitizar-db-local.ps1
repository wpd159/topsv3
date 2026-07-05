param(
  [string]$ContainerName = "topsv3-bloco29-pg17-sanitizado",
  [string]$SanitizedDatabase = "topsv3_sanitizado",
  [string]$RelatorioSaida = "docs/v3/evidencias/bloco-29-4/relatorio-sanitizacao-db-local.md",
  [string]$RelatorioCamposSaida = "docs/v3/evidencias/bloco-29-4/relatorio-campos-sensiveis-sanitizados.md"
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

function Write-TextReport {
  param(
    [string]$Path,
    [string[]]$Lines
  )
  $reportPath = Resolve-RepoPath $Path
  $reportDir = Split-Path -Parent $reportPath
  New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
  [System.IO.File]::WriteAllText($reportPath, (($Lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
  Write-Host "RELATORIO=$reportPath"
}

function Write-PendingReports {
  param([string]$Result, [string]$Detail)
  Write-TextReport -Path $RelatorioSaida -Lines @(
    "# Relatorio - sanitizacao DB local",
    "",
    "- Bloco: 29.4",
    "- Resultado: $Result",
    "- Detalhe: $Detail",
    "- Container esperado: ``$ContainerName``",
    "- Banco sanitizado esperado: ``$SanitizedDatabase``",
    "- Banco/container bruto alterado: nao",
    "- Recursos TopsWI/terceiros alterados: nao",
    "- Producao alterada: nao",
    "- Banco de producao acessado: nao",
    "- Midia real copiada: nao",
    "- Documento real copiado: nao",
    "- Conteudo sensivel versionado: nao"
  )
  Write-TextReport -Path $RelatorioCamposSaida -Lines @(
    "# Relatorio - campos sensiveis sanitizados",
    "",
    "- Bloco: 29.4",
    "- Resultado: $Result",
    "- Detalhe: $Detail",
    "- CPF/RG/documento/selfie: pendente.",
    "- Nome civil: pendente.",
    "- E-mail/telefone/WhatsApp: pendente.",
    "- Endereco especifico/IP/user-agent: pendente.",
    "- Token/senha/certificado/storage/bucket/URL: pendente.",
    "- Payload financeiro/Pix/Efi/log sensivel: pendente."
  )
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
  Write-PendingReports -Result "PENDENTE_DOCKER_LOCAL" -Detail "Docker nao encontrado no PATH."
  Write-Host "SANITIZACAO_EXECUTADA=False"
  Write-Host "VALIDATION_RESULT=PENDENTE_DOCKER_LOCAL"
  exit 2
}

$runningResult = Invoke-NativeCapture -FileName "docker" -Arguments @("ps", "--filter", "name=^/${ContainerName}$", "--format", "{{.Names}}")
$running = @($runningResult.Output)
if (-not ($running -contains $ContainerName)) {
  Write-PendingReports -Result "PENDENTE_RESTORE_LOCAL_ISOLADO" -Detail "Container de restore local isolado nao esta em execucao."
  Write-Host "SANITIZACAO_EXECUTADA=False"
  Write-Host "VALIDATION_RESULT=PENDENTE_RESTORE_LOCAL_ISOLADO"
  exit 2
}

$dbExists = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", $ContainerName, "psql", "-U", "postgres", "-At", "-c", "SELECT 1 FROM pg_database WHERE datname = '$SanitizedDatabase'")
if (($dbExists.Output -join "").Trim() -ne "1") {
  Write-PendingReports -Result "PENDENTE_BANCO_SANITIZADO_LOCAL" -Detail "Banco sanitizado local nao existe."
  Write-Host "SANITIZACAO_EXECUTADA=False"
  Write-Host "VALIDATION_RESULT=PENDENTE_BANCO_SANITIZADO_LOCAL"
  exit 2
}

$countSql = @"
WITH cols AS (
  SELECT count(*) AS total_colunas_sensiveis
  FROM information_schema.columns
  WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
    AND column_name ~* '(cpf|rg|document|selfie|nome_civil|email|mail|telefone|whats|celular|phone|endereco|address|ip|user_agent|agent|token|senha|password|cert|storage|bucket|url|midia|media|payload|pix|efi|log|secret|chave|key|etag|provider)'
)
SELECT total_colunas_sensiveis FROM cols;
"@
$sensitiveColumns = (Invoke-NativeCapture -FileName "docker" -Arguments @("exec", "-i", $ContainerName, "psql", "-U", "postgres", "-d", $SanitizedDatabase, "-At", "-v", "ON_ERROR_STOP=1") -InputText $countSql).Output -join ""
if ([string]::IsNullOrWhiteSpace($sensitiveColumns)) { $sensitiveColumns = "0" }

$sanitizeSql = @'
DO $topsv3$
DECLARE
  r record;
  replacement text;
  generic_pattern text := '([A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}|[0-9]{3}\.?[0-9]{3}\.?[0-9]{3}-?[0-9]{2}|https?://[^[:space:]"'']+|[0-9]{1,3}(\.[0-9]{1,3}){3}|(token|secret|senha|password|pix|efi)[^[:space:]]{6,})';
BEGIN
  FOR r IN
    SELECT table_schema, table_name, column_name, data_type, is_nullable
    FROM information_schema.columns
    WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
      AND data_type IN ('text', 'character varying', 'character', 'citext')
  LOOP
    IF r.column_name ~* '(cpf|rg|document|selfie|nome_civil|email|mail|telefone|whats|celular|phone|endereco|address|ip|user_agent|agent|token|senha|password|cert|storage|bucket|url|midia|media|payload|pix|efi|log|secret|chave|key|etag|provider)' THEN
      replacement := CASE WHEN r.is_nullable = 'YES' THEN NULL ELSE '[sanitizado]' END;
      EXECUTE format('UPDATE %I.%I SET %I = %L WHERE %I IS NOT NULL', r.table_schema, r.table_name, r.column_name, replacement, r.column_name);
    ELSE
      EXECUTE format('UPDATE %I.%I SET %I = regexp_replace(%I, %L, ''[sanitizado]'', ''gi'') WHERE %I ~* %L',
        r.table_schema, r.table_name, r.column_name, r.column_name, generic_pattern, r.column_name, generic_pattern);
    END IF;
  END LOOP;

  FOR r IN
    SELECT table_schema, table_name, column_name, data_type, is_nullable
    FROM information_schema.columns
    WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
      AND data_type IN ('bytea')
      AND column_name ~* '(document|selfie|foto|image|imagem|cert|secret|key|payload)'
  LOOP
    IF r.is_nullable = 'YES' THEN
      EXECUTE format('UPDATE %I.%I SET %I = NULL WHERE %I IS NOT NULL', r.table_schema, r.table_name, r.column_name, r.column_name);
    ELSE
      EXECUTE format('UPDATE %I.%I SET %I = decode('''', ''hex'') WHERE %I IS NOT NULL', r.table_schema, r.table_name, r.column_name, r.column_name);
    END IF;
  END LOOP;
END
$topsv3$;
'@

$sanitize = Invoke-NativeCapture -FileName "docker" -Arguments @("exec", "-i", $ContainerName, "psql", "-U", "postgres", "-d", $SanitizedDatabase, "-v", "ON_ERROR_STOP=1", "-q") -InputText $sanitizeSql
if ($sanitize.ExitCode -ne 0) {
  Write-PendingReports -Result "FALHA_SANITIZACAO_DB_LOCAL" -Detail "SQL generico de sanitizacao falhou; detalhes brutos nao foram versionados para evitar exposicao."
  Write-Host "SANITIZACAO_EXECUTADA=False"
  Write-Host "VALIDATION_RESULT=FALHA_SANITIZACAO_DB_LOCAL"
  exit 1
}

Write-TextReport -Path $RelatorioSaida -Lines @(
  "# Relatorio - sanitizacao DB local",
  "",
  "- Bloco: 29.4",
  "- Resultado: OK_SANITIZACAO_DB_LOCAL",
  "- Container: ``$ContainerName``",
  "- Banco sanitizado: ``$SanitizedDatabase``",
  "- Banco/container bruto alterado: nao",
  "- Recursos TopsWI/terceiros alterados: nao",
  "- Colunas sensiveis candidatas tratadas: $sensitiveColumns",
  "- Producao alterada: nao",
  "- Banco de producao acessado: nao",
  "- Midia real copiada: nao",
  "- Documento real copiado: nao",
  "- Conteudo sensivel versionado: nao"
)
Write-TextReport -Path $RelatorioCamposSaida -Lines @(
  "# Relatorio - campos sensiveis sanitizados",
  "",
  "- Bloco: 29.4",
  "- Resultado: OK_CAMPOS_SENSIVEIS_SANITIZADOS",
  "- Colunas sensiveis candidatas tratadas: $sensitiveColumns",
  "- Valores brutos listados: nao",
  "- Slugs reais listados: nao",
  "- CPF/RG/documento/selfie: sanitizados quando presentes.",
  "- Nome civil: sanitizado quando presente.",
  "- E-mail/telefone/WhatsApp: sanitizados quando presentes.",
  "- Endereco especifico/IP/user-agent: sanitizados quando presentes.",
  "- Token/senha/certificado/storage/bucket/URL: sanitizados quando presentes.",
  "- Payload financeiro/Pix/Efi/log sensivel: sanitizados quando presentes."
)
Write-Host "SANITIZACAO_EXECUTADA=True"
Write-Host "VALIDATION_RESULT=OK_SANITIZACAO_DB_LOCAL"
exit 0
