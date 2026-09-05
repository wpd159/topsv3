Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (& git rev-parse --show-toplevel 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
  Write-Host "VALIDATION_RESULT=FALHA_DEPLOY_PRODUCTION_LOCAL"
  exit 1
}

function Read-RepoFile {
  param([string]$Path)
  $fullPath = Join-Path $repoRoot ($Path -replace '/', [IO.Path]::DirectorySeparatorChar)
  if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
    throw "Arquivo obrigatorio ausente: $Path"
  }
  return [IO.File]::ReadAllText($fullPath, [Text.UTF8Encoding]::new($false, $true))
}

$workflow = Read-RepoFile ".github/workflows/deploy-production.yml"
$compose = Read-RepoFile "deploy/production/docker-compose.yml"
$databaseGate = Read-RepoFile "scripts/deploy/validar-gate-banco-production.sh"
$databaseGateSnapshot = Read-RepoFile "scripts/deploy/capturar-snapshot-gate-banco-production.sql"
$databaseGateTests = Read-RepoFile "scripts/deploy/testar-gate-banco-production.sh"
$flywayGate = Read-RepoFile "scripts/deploy/validar-gate-flyway-production.sh"
$flywayGateTests = Read-RepoFile "scripts/deploy/testar-gate-flyway-production.sh"
$backupProducer = Read-RepoFile "scripts/deploy/criar-backup-validado-production.sh"
$operationHelper = Read-RepoFile "scripts/deploy/proteger-operacao-production.sh"
$stdinRegressionTests = Read-RepoFile "scripts/deploy/testar-stdin-deploy-production.sh"
$backupIntegrationTests = Read-RepoFile "scripts/deploy/testar-backup-validado-production.sh"
$preprodWorkflow = Read-RepoFile ".github/workflows/deploy-preprod.yml"
$rootLayout = Read-RepoFile "frontend/src/app/layout.tsx"
$analyticsComponent = Read-RepoFile "frontend/src/components/analytics/consent-aware-analytics.tsx"
$checks = [Collections.Generic.List[object]]::new()

function Add-Check {
  param([string]$Name, [bool]$Ok)
  $checks.Add([pscustomobject]@{ Name = $Name; Ok = $Ok })
}

foreach ($required in @(
    "workflow_dispatch:",
    "environment: production",
    "group: topsv3-production",
    "/opt/topsv3/production/releases",
    "/opt/topsv3/production/current",
    "/opt/topsv3/secrets/production.env",
    "deploy/production/docker-compose.yml",
    "StrictHostKeyChecking=yes",
    "UserKnownHostsFile=",
    "HostKeyAlias=",
    "flyway migrate </dev/null",
    "flyway validate </dev/null",
    "proteger-operacao-production.sh",
    'op_begin "${deploy_root}" "${release_sha}"',
    'op_run mutating "${compose[@]}"',
    'op_smoke "${release_sha}"',
    "op_finish",
    "snapshot_after",
    "snapshot_before",
    "validar-gate-banco-production.sh",
    "validar-gate-flyway-production.sh",
    "criar-backup-validado-production.sh",
    "testar-stdin-deploy-production.sh",
    "testar-backup-validado-production.sh",
    "backups/postgresql"
  )) {
  Add-Check "workflow contem $required" ($workflow.Contains($required))
}

foreach ($secret in @(
    "PRODUCTION_HOST",
    "PRODUCTION_USER",
    "PRODUCTION_SSH_PORT",
    "PRODUCTION_SSH_IDENTITY",
    "PRODUCTION_SSH_HOST_KEY",
    "INDEXNOW_KEY"
  )) {
  Add-Check "workflow referencia $secret" ($workflow.Contains("secrets.$secret"))
}

foreach ($forbidden in @(
    "ssh-keyscan",
    "accept-new",
    "StrictHostKeyChecking=no",
    "docker compose down",
    "--volumes",
    "docker volume rm",
    "systemctl reload nginx",
    "sites-enabled",
    "v3.esle.cloud",
    "topsv3-preprod",
    "/opt/topsv3/preprod"
  )) {
  Add-Check "workflow nao contem $forbidden" (-not $workflow.Contains($forbidden))
}

Add-Check "workflow nao executa automaticamente em push" (-not ($workflow -match '(?m)^\s+push:\s*$'))
Add-Check "workflow nao altera manutencao ou indexacao externa" (
  -not ($workflow -match 'maintenance|manutencao|robots\.txt.*(write|cat|printf)|nginx.*reload')
)
Add-Check "workflow preserva PostgreSQL" (
  ($workflow -match 'postgres_id_before') -and
  ($workflow -match 'postgres_volume_before') -and
  ($workflow -match 'up -d --no-deps --force-recreate backend frontend gateway')
)
Add-Check "workflow testa gate de banco antes do deploy" (
  ($workflow.Contains("Test production database safety gate")) -and
  ($workflow.IndexOf("Test production database safety gate") -lt $workflow.IndexOf("Validate pinned SSH host key"))
)
Add-Check "workflow nao exige igualdade absoluta de contagens mutaveis" (
  -not ($workflow -match 'test\s+"\$\{counts_after\}"\s+=\s+"\$\{counts_before\}"')
)
Add-Check "workflow preserva health e rollback no novo gate" (
  ($workflow -match 'capture_database_snapshot\s+"\$\{snapshot_before\}"') -and
  ($workflow -match 'capture_database_snapshot\s+"\$\{snapshot_after\}"\s+UP') -and
  ($workflow -match 'bash\s+"\$\{database_gate\}"') -and
  ($workflow.Contains('op_smoke "${release_sha}"')) -and
  ($workflow.Contains('source "${release_dir}/scripts/deploy/proteger-operacao-production.sh"'))
)
Add-Check "workflow deriva versao Flyway sem hardcode" (
  ($workflow.Contains('expected_flyway="$(bash "${flyway_gate}" expected "${migration_dir}")"')) -and
  (-not ($workflow -match 'database_gate[^\r\n]*(051|052)')) -and
  (-not ($workflow -match 'flyway_gate[^\r\n]*(before|after)[^\r\n]*(051|052)'))
)
Add-Check "workflow exige backup antes de migration pendente" (
  ($workflow.Contains('if [ "${migrations_pending}" = true ]; then')) -and
  ($workflow.Contains('test "${backup_status}" = VALIDATED')) -and
  ($workflow.IndexOf('bash "${backup_producer}"') -lt $workflow.IndexOf('flyway migrate </dev/null'))
)
Add-Check "workflow valida Flyway antes do startup" (
  ($workflow.IndexOf('bash "${flyway_gate}" after') -gt $workflow.IndexOf('flyway migrate </dev/null')) -and
  ($workflow.IndexOf('bash "${flyway_gate}" after') -lt $workflow.LastIndexOf('up -d --no-deps --force-recreate backend frontend gateway'))
)
Add-Check "workflow troca release somente depois de health e readiness" (
  ($workflow.IndexOf('op_smoke "${release_sha}"') -gt $workflow.IndexOf('op_phase ACTIVATING')) -and
  ($workflow.LastIndexOf('mv -Tf "${current_link}"') -gt $workflow.IndexOf('op_smoke "${release_sha}"')) -and
  ($operationHelper.Contains('api/health/readiness'))
)
Add-Check "workflow empacota e carrega o helper compartilhado antes da posse" (
  ($workflow.Contains('tar -tzf "${ARCHIVE}" | grep -Fx ''./scripts/deploy/proteger-operacao-production.sh''')) -and
  ($workflow.IndexOf('source "${release_dir}/scripts/deploy/proteger-operacao-production.sh"') -lt
    $workflow.IndexOf('op_begin "${deploy_root}" "${release_sha}"'))
)
Add-Check "workflow mantem uma unica operacao ate os checks finais" (
  ([regex]::Matches($workflow, '(?m)^\s+op_begin\s').Count -eq 1) -and
  ([regex]::Matches($workflow, '(?m)^\s+op_finish\s*$').Count -eq 1) -and
  ($workflow.IndexOf('op_begin "${deploy_root}" "${release_sha}"') -lt $workflow.IndexOf('op_smoke "${previous_sha}"')) -and
  ($workflow.LastIndexOf('op_finish') -gt $workflow.LastIndexOf('mv -Tf "${current_link}"')) -and
  ($workflow.LastIndexOf('op_finish') -gt $workflow.IndexOf("grep -qx 'SEARCH_INDEXING_MODE=public'")) -and
  ($workflow.LastIndexOf('op_finish') -gt $workflow.IndexOf("IMPORTACAO_.*(INICIO|EXECUTADA)")) -and
  (-not ($workflow -match '(?m)^\s+- name: Smoke production release\s*$')) -and
  (-not ($workflow -match '(?m)^\s+trap\s+-\s+ERR\s*$'))
)
Add-Check "workflow preserva gates reais de processos e containers antes do SSH" (
  (Test-Path -LiteralPath (Join-Path $repoRoot 'scripts/deploy/testar-operacao-production.sh') -PathType Leaf) -and
  (Test-Path -LiteralPath (Join-Path $repoRoot 'scripts/deploy/testar-operacao-containers-production.sh') -PathType Leaf) -and
  ($workflow.Contains('bash ./scripts/deploy/testar-operacao-production.sh')) -and
  ($workflow.Contains('bash ./scripts/deploy/testar-operacao-containers-production.sh')) -and
  ($workflow.IndexOf('bash ./scripts/deploy/testar-operacao-production.sh') -lt $workflow.IndexOf('name: Validate pinned SSH host key')) -and
  ($workflow.IndexOf('bash ./scripts/deploy/testar-operacao-containers-production.sh') -lt $workflow.IndexOf('name: Validate pinned SSH host key'))
)
foreach ($required in @(
    'mvn --batch-mode --no-transfer-progress verify',
    'LOCALIDADES_POSTGRES17_ENABLED: "true"',
    'FOTO_ELEGIVEL_POSTGRES17_ENABLED: "true"',
    'npm ci',
    'npm audit --omit=dev --audit-level=high',
    'npm audit --audit-level=high',
    'npm run test:json-ld-security',
    'npm run test:public-ordering',
    'npm run test:public-http-states',
    'npm run test:public-data-access',
    'node scripts/test-public-seo-critical.mjs',
    'npm run test:search-indexing',
    'npm run test:seo-ai',
    'npm run test:sharp-security',
    'node scripts/test-ga4-production.mjs',
    'npm run lint',
    'npm run build',
    'node scripts/test-search-indexing-artifact.mjs public'
  )) {
  Add-Check "workflow preserva gate obrigatorio $required" (
    ($workflow.Contains($required)) -and
    ($workflow.IndexOf($required) -lt $workflow.IndexOf('name: Validate pinned SSH host key'))
  )
}

function Read-ShellFunction {
  param([string]$Content, [string]$Name)
  $pattern = '(?ms)^(?<indent>[ \t]*)' + [regex]::Escape($Name) + '\(\) \{\r?\n.*?^\k<indent>\}[ \t]*\r?$'
  return [regex]::Match($Content, $pattern).Value
}
$operationBegin = Read-ShellFunction $operationHelper 'op_begin'
$operationManual = Read-ShellFunction $operationHelper '_op_manual'
$operationRestore = Read-ShellFunction $operationHelper '_op_restore'
$operationExit = Read-ShellFunction $operationHelper '_op_exit'
$operationState = Read-ShellFunction $operationHelper '_op_state'
$operationWatcher = Read-ShellFunction $operationHelper '_op_watch_parent'
foreach ($name in @('op_begin', 'op_run', 'op_phase', 'op_expect_indexnow', 'op_expect_candidate', 'op_smoke', 'op_finish', '_op_manual', '_op_restore', '_op_exit')) {
  Add-Check "helper define contrato $name" (-not [string]::IsNullOrWhiteSpace((Read-ShellFunction $operationHelper $name)))
}
Add-Check "helper compartilha lock entre ativacao e rollback manual sem readquirir na restauracao" (
  ($operationBegin.Contains('_op_lock "$1"')) -and
  ($operationManual.Contains('_op_lock "${root}"')) -and
  ($operationHelper.Contains('flock -n "${OP_LOCK_FD}"')) -and
  (-not $operationRestore.Contains('_op_lock')) -and
  (-not ($operationHelper -match '(?m)^\s*flock\s+-u\b'))
)
Add-Check "helper persiste identidade e fase atomicamente" (
  ($operationState.Contains('operations/active.state')) -and
  ($operationState.Contains('mv -f -- "${temporary}"')) -and
  ($operationState.Contains('"candidate=${OP_CANDIDATE}"')) -and
  ($operationState.Contains('"previous=${OP_PREVIOUS_SHA:-}"')) -and
  ($operationState.Contains('"phase=${OP_PHASE}"')) -and
  ($operationState.Contains('"pid=${OP_PID}"')) -and
  ($operationState.Contains('"start=${OP_START}"')) -and
  ($operationState.Contains('"boot=${OP_BOOT}"')) -and
  ($operationHelper.Contains('/proc/sys/kernel/random/boot_id')) -and
  ($operationHelper.Contains('images.tsv')) -and
  ($operationHelper.Contains('config.sha256'))
)
Add-Check "helper trata EXIT e sinais preservando erro e estado incompleto" (
  ($operationHelper.Contains("trap '_op_signal 130' INT")) -and
  ($operationHelper.Contains("trap '_op_signal 143' TERM")) -and
  ($operationHelper.Contains("trap '_op_signal 129' HUP")) -and
  ($operationHelper.Contains("trap '_op_signal 141' PIPE")) -and
  ($operationHelper.Contains('trap ''_op_exit "$?"'' EXIT')) -and
  ($operationExit.Contains('[ "${BASHPID}" = "${OP_PID:-}" ]')) -and
  ($operationExit.Contains('OP_ORIGINAL_RC="${rc}"')) -and
  ($operationExit.Contains('OP_RESULT=INCOMPLETE')) -and
  ($operationExit.Contains('exit "${rc}"')) -and
  ($operationWatcher.Contains('exec {OP_LOCK_FD}>&-'))
)
Add-Check "helper exige reconciliacao explicita e recupera sem reconstruir imagens" (
  ($operationBegin.Contains('COMPLETED|ROLLED_BACK|ABORTED|RECONCILED')) -and
  ($operationManual.Contains('--confirm-daemon-quiescent')) -and
  ($operationManual.Contains('_op_alive')) -and
  ($operationRestore.Contains('--no-build --pull never backend frontend gateway')) -and
  ($operationRestore.Contains('sha256sum -c --status')) -and
  ($operationRestore.Contains('_op_verify_runtime')) -and
  ($operationRestore.Contains('op_smoke "${OP_PREVIOUS_SHA}"')) -and
  ($operationRestore.Contains('"${OP_ROOT}/current"'))
)
function Convert-ToLogicalShellLines {
  param([string]$Content)
  return [regex]::Replace($Content, '\\\r?\n\s*', ' ')
}

$deploySources = @(
  Convert-ToLogicalShellLines $workflow
  Convert-ToLogicalShellLines $preprodWorkflow
  Convert-ToLogicalShellLines $backupProducer
  Convert-ToLogicalShellLines $operationHelper
) -join "`n"
$unsafeInteractiveCommandPattern = '(?m)docker\s+exec(?=[^\r\n]*\bpsql\b)(?=[^\r\n]*\s-i(?:\s|$))[^\r\n]*\bpsql\b[^\r\n]*(?:\s-c(?:\s|$)|\s--command(?:=|\s))'
$flywayReader = Read-ShellFunction $workflow 'read_flyway_state'
Add-Check "nenhum psql command reutiliza stdin interativo" (
  -not [regex]::IsMatch($deploySources, $unsafeInteractiveCommandPattern)
)
Add-Check "leitura Flyway isola stdin e falha no primeiro erro SQL" (
  (-not [string]::IsNullOrWhiteSpace($flywayReader)) -and
  (-not ($flywayReader -match 'docker\s+exec\s+-i')) -and
  ($flywayReader.Contains('--no-psqlrc')) -and
  ($flywayReader.Contains('--set=ON_ERROR_STOP=1')) -and
  ($flywayReader.Contains('--command')) -and
  ($flywayReader.Contains('</dev/null')) -and
  ($workflow.Contains('flyway_before_state="$(read_flyway_state "${expected_flyway}")"')) -and
  ($workflow.Contains('flyway_after_state="$(read_flyway_state "${expected_flyway}")"'))
)
Add-Check "workflow registra mutacoes de configuracao e servicos sob a mesma posse" (
  ($workflow.Contains('op_phase CONFIGURING')) -and
  ($workflow.Contains('op_run mutating docker run --rm -i --pull never')) -and
  ($workflow.Contains('op_run mutating "${compose[@]}" run --rm -T --no-deps flyway migrate </dev/null')) -and
  ($workflow.Contains('op_run mutating "${compose[@]}" build backend frontend')) -and
  ($workflow.Contains('op_run mutating "${compose[@]}" up -d --no-deps --force-recreate backend frontend gateway')) -and
  ($workflow.IndexOf('op_begin "${deploy_root}" "${release_sha}"') -lt $workflow.IndexOf('op_phase CONFIGURING')) -and
  ($workflow.IndexOf('op_phase ACTIVATING') -lt $workflow.IndexOf('op_run mutating "${compose[@]}" up -d'))
)
Add-Check "workflow fixa identidades esperadas antes de configurar e ativar" (
  ($workflow.Contains('op_expect_indexnow "${indexnow_key}"')) -and
  ($workflow.Contains('op_expect_candidate')) -and
  ($workflow.IndexOf('op_expect_indexnow "${indexnow_key}"') -gt $workflow.IndexOf('op_begin "${deploy_root}" "${release_sha}"')) -and
  ($workflow.IndexOf('op_expect_indexnow "${indexnow_key}"') -lt $workflow.IndexOf('op_run mutating docker run --rm -i --pull never')) -and
  ($workflow.IndexOf('op_expect_candidate') -gt $workflow.IndexOf('op_run mutating "${compose[@]}" build backend frontend')) -and
  ($workflow.IndexOf('op_expect_candidate') -lt $workflow.IndexOf('op_run mutating "${compose[@]}" up -d'))
)
Add-Check "workflow valida host key antes do upload" (
  $workflow.IndexOf('name: Validate pinned SSH host key') -lt
  $workflow.IndexOf('name: Upload immutable release')
)
Add-Check "workflow limita retries SSH" (
  ($workflow -match 'SSH_RETRY_BACKOFF=\(2 4 8 16\)') -and
  ($workflow -match 'for attempt in 1 2 3 4 5; do')
)
Add-Check "workflow rejeita residuos de homologacao no runtime" (
  $workflow -match "grep -Eqi 'v3\\.esle\\.cloud\|mailpit\|homologacao\|sandbox'"
)
Add-Check "workflow compila frontend com GA4 habilitado" (
  ($workflow -match 'NEXT_PUBLIC_ANALYTICS_ENABLED:\s+"true"') -and
  ($workflow.Contains("node scripts/test-ga4-production.mjs"))
)
Add-Check "workflow valida GA4 habilitado no runtime" (
  $workflow.Contains("grep -qx 'NEXT_PUBLIC_ANALYTICS_ENABLED=true'")
)
Add-Check "workflow sincroniza IndexNow sem expor a chave em argumento" (
  ($workflow.Contains("Synchronize IndexNow key in production runtime")) -and
  ($workflow.Contains("cat <<'REMOTE_HEAD'")) -and
  ($workflow.Contains('printf ''%s\n'' "${INDEXNOW_KEY}"')) -and
  ($workflow.Contains('IFS= read -r indexnow_key <<''__INDEXNOW_VALUE__''')) -and
  ($workflow.Contains(''' <<<"${indexnow_key}"')) -and
  ($workflow.Contains("} | ssh")) -and
  ($workflow.Contains("--network none")) -and
  ($workflow.Contains("--pull never")) -and
  ($workflow.Contains('test -n "${key}"')) -and
  ($workflow.Contains('case "${key}" in')) -and
  ($workflow.Contains('awk "!/^INDEXNOW_KEY=/"')) -and
  ($workflow.IndexOf('op_phase CONFIGURING') -gt $workflow.IndexOf('op_begin "${deploy_root}" "${release_sha}"')) -and
  (-not ($workflow -match '(?m)^\s+- name: Synchronize IndexNow key in production runtime\s*$')) -and
  (-not $workflow.Contains("''|*[!A-Za-z0-9-]*")) -and
  (-not $workflow.Contains('test -w /opt/topsv3/secrets/production.env'))
)
$activationBlock = [regex]::Match(
  $workflow,
  '(?ms)^\s+- name: Build and activate production release\r?\n.*?(?=^\s{6}- name:|\z)'
).Value
$indexnowLocalValidation = '[[ "${INDEXNOW_KEY}" =~ ^[A-Za-z0-9-]{8,128}$ ]]'
Add-Check "workflow valida chave localmente antes de interpolar heredoc SSH" (
  (-not [string]::IsNullOrWhiteSpace($activationBlock)) -and
  ($activationBlock.Contains($indexnowLocalValidation)) -and
  ($activationBlock.IndexOf($indexnowLocalValidation) -lt $activationBlock.IndexOf("cat <<'REMOTE_HEAD'")) -and
  ($activationBlock.IndexOf($indexnowLocalValidation) -lt $activationBlock.IndexOf('printf ''%s\n'' "${INDEXNOW_KEY}"'))
)
Add-Check "workflow valida IndexNow no runtime sem imprimir o valor" (
  $workflow.Contains("grep -q '^INDEXNOW_KEY='")
)

foreach ($required in @(
    "MASS_LOSS_MINIMUM_ROWS=50",
    "MASS_LOSS_PERCENT=20",
    "database_identity",
    "flyway_version",
    "movimento_credito",
    "ledger canonico perdeu movimentos",
    "ATIVIDADE_LEGITIMA_OBSERVADA",
    "DATABASE_SAFETY_GATE=PASS"
  )) {
  Add-Check "gate de banco contem $required" ($databaseGate.Contains($required))
}

foreach ($required in @(
    "tabelas_criticas_ausentes",
    "constraints_nao_validadas",
    "anuncio_sem_usuario",
    "documento_sem_arquivo",
    "movimento_saldo_incoerente",
    "movimento_idempotencia_duplicada",
    "saldo_credito_negativo",
    "eventos_metricas_estimados"
  )) {
  Add-Check "snapshot de banco contem $required" ($databaseGateSnapshot.Contains($required))
}

foreach ($required in @(
    "crescimento_legitimo",
    "mudanca_status_anuncio",
    "crescimento_metricas",
    "reducao_operacional_pequena",
    "truncamento",
    "perda_macica",
    "tabela_critica_ausente",
    "flyway_inesperado",
    "transicao_flyway_051_052",
    "health_indisponivel",
    "database_trocado",
    "relacionamento_orfao",
    "reducao_ledger",
    "DATABASE_SAFETY_GATE_TESTS=PASS"
  )) {
  Add-Check "testes do gate contem $required" ($databaseGateTests.Contains($required))
}

foreach ($required in @(
    "MIGRATIONS_PENDING",
    "banco acima da versao do repositorio",
    "backup validado obrigatorio antes do Flyway",
    "versao esperada deve estar aplicada exatamente uma vez",
    "FLYWAY_PRE_MIGRATION_GATE=PASS",
    "FLYWAY_POST_MIGRATION_GATE=PASS",
    "R__"
  )) {
  Add-Check "gate Flyway contem $required" ($flywayGate.Contains($required))
}

foreach ($required in @(
    "banco_051_repositorio_051",
    "banco_051_repositorio_052_backup",
    "backup_ausente",
    "backup_invalido",
    "migration_falha",
    "banco_acima_repositorio",
    "versao_final_divergente",
    "repeatable_nao_altera_versao",
    "FLYWAY_DYNAMIC_GATE_TESTS=PASS"
  )) {
  Add-Check "testes Flyway contem $required" ($flywayGateTests.Contains($required))
}

foreach ($required in @(
    "pg_dump",
    "--format=custom",
    "--no-owner",
    "--no-acl",
    "--serializable-deferrable",
    "pg_restore --list",
    "--exit-on-error",
    "--network none",
    "sha256sum",
    "chmod 600",
    "docker volume rm",
    "BACKUP_STATUS=VALIDATED"
  )) {
  Add-Check "backup validado contem $required" ($backupProducer.Contains($required))
}
foreach ($required in @(
    "marcador_posterior",
    "falha_psql_interrompe",
    "funcao_flyway_real_propaga_erro",
    "backup_antes_flyway",
    "flyway_antes_startup",
    "health_antes_troca",
    "DEPLOY_STDIN_REGRESSION_TESTS=PASS"
  )) {
  Add-Check "regressao de stdin contem $required" ($stdinRegressionTests.Contains($required))
}
foreach ($required in @(
    "postgres:17.10-alpine",
    "BACKUP_STATUS=VALIDATED",
    "backup_restaurado_flyway_051",
    "pg_restore --list",
    "BACKUP_RESTORE_INTEGRATION_TEST=PASS"
  )) {
  Add-Check "integracao de backup contem $required" ($backupIntegrationTests.Contains($required))
}
Add-Check "snapshot Flyway ignora repeatables" (
  ($databaseGateSnapshot.Contains("version ~ '^[0-9]+$'")) -and
  ($databaseGateSnapshot.Contains("ORDER BY version::integer DESC"))
)

foreach ($required in @(
    "name: topsv3-production",
    "postgres:17.10-alpine",
    "flyway/flyway:12.10.0-alpine",
    'topsv3-production-backend:${TOPSV3_RELEASE_SHA:',
    'topsv3-production-frontend:${TOPSV3_RELEASE_SHA:',
    "SPRING_PROFILES_ACTIVE: production",
    "APP_ENV: producao",
    "APP_CANONICAL_DOMAIN: https://topsdojob.com",
    "EFI_ENVIRONMENT: producao",
    "EFI_BASE_URL: https://pix.api.efipay.com.br",
    'OUTBOX_EMAIL_ENABLED: ${OUTBOX_EMAIL_ENABLED:-true}',
    'EFI_RECONCILIATION_ENABLED: ${EFI_RECONCILIATION_ENABLED:-true}',
    'EFI_WEBHOOK_REGISTRATION_ENABLED: ${EFI_WEBHOOK_REGISTRATION_ENABLED:-false}',
    "SEARCH_INDEXING_MODE: public",
    '127.0.0.1:28080:8080',
    '127.0.0.1:23000:23000',
    '/opt/topsv3/secrets/application-production.yml:',
    '/opt/topsv3/secrets/efi:',
    '/opt/topsv3/secrets/nginx-production-local.conf:',
    '/opt/topsv3/secrets/efi-webhook-allowlist.conf:'
  )) {
  Add-Check "compose contem $required" ($compose.Contains($required))
}

foreach ($forbidden in @(
    "v3.esle.cloud",
    "mailpit",
    "homologacao",
    "sandbox",
    "hml/",
    "StrictHostKeyChecking=no",
    "docker.sock"
  )) {
  Add-Check "compose nao contem $forbidden" (-not $compose.Contains($forbidden))
}

Add-Check "compose nao contem segredo literal" (-not ($compose -match '(?i)(password|secret|signing-value|client-secret):\s+[A-Za-z0-9+/=_-]{16,}\s*$'))
$postgresCompose = [regex]::Match($compose, '(?ms)^  postgres:\s.*?(?=^  flyway:)').Value
Add-Check "compose nao publica PostgreSQL" (-not ($postgresCompose -match '(?m)^\s+ports:'))
Add-Check "compose desabilita fixtures" (
  ($compose -match '--app\.fixture\.stories\.enabled=false') -and
  ($compose -match '--app\.fixture\.auth-smoke\.enabled=false')
)
Add-Check "compose preserva webhook mTLS" ($compose -match 'EFI_WEBHOOK_SKIP_MTLS_CHECKING:\s+"false"')
Add-Check "compose usa frontend standalone sem privilegio" (
  ($compose -match 'COPY --from=build --chown=node:node /app/\.next/standalone ./') -and
  ($compose -match '(?m)^\s+USER node\s*$')
)
Add-Check "compose habilita GA4 no build e runtime de producao" (
  ([regex]::Matches($compose, 'NEXT_PUBLIC_ANALYTICS_ENABLED:\s+"true"').Count -eq 2) -and
  ($compose -match 'ENV NEXT_PUBLIC_ANALYTICS_ENABLED=true') -and
  (-not ($compose -match 'NEXT_PUBLIC_ANALYTICS_ENABLED[^\r\n]*false')) -and
  (-not ($compose -match 'NEXT_PUBLIC_ANALYTICS_ENABLED:\s+\$\{'))
)
Add-Check "layout monta uma unica integracao consent-aware" (
  ([regex]::Matches($rootLayout, '<ConsentAwareAnalytics\s*/>').Count -eq 1) -and
  (-not $rootLayout.Contains("googletagmanager.com/gtag/js")) -and
  (-not ($rootLayout -match "gtag\('config'"))
)
Add-Check "componente GA4 respeita ambiente e consentimento" (
  ($analyticsComponent.Contains("NEXT_PUBLIC_ANALYTICS_ENABLED")) -and
  ($analyticsComponent.Contains("cookie_consent")) -and
  ($analyticsComponent.Contains("tops:cookie-consent-updated")) -and
  ([regex]::Matches($analyticsComponent, 'googletagmanager\.com/gtag/js').Count -eq 1) -and
  ([regex]::Matches($analyticsComponent, "gtag\('config'").Count -eq 1) -and
  (-not $analyticsComponent.Contains("@vercel/analytics"))
)

$failed = @($checks | Where-Object { -not $_.Ok })
foreach ($check in $checks) {
  $status = if ($check.Ok) { "OK" } else { "FALHA" }
  Write-Host "$status`: $($check.Name)"
}

if ($failed.Count -gt 0) {
  Write-Host "VALIDATION_RESULT=FALHA_DEPLOY_PRODUCTION_LOCAL"
  exit 1
}

Write-Host "CONTRACT_SCOPE=STATIC_REQUIRES_OPERATIONAL_PROCESS_AND_CONTAINER_GATES"
Write-Host "VALIDATION_RESULT=OK_DEPLOY_PRODUCTION_LOCAL"
