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
$preflightJob = [regex]::Match(
  $workflow,
  '(?ms)^  preflight-production:\r?\n.*?(?=^  deploy-production:)'
).Value
$deployJob = [regex]::Match(
  $workflow,
  '(?ms)^  deploy-production:\r?\n.*\z'
).Value
$compose = Read-RepoFile "deploy/production/docker-compose.yml"
$databaseGate = Read-RepoFile "scripts/deploy/validar-gate-banco-production.sh"
$databaseGateSnapshot = Read-RepoFile "scripts/deploy/capturar-snapshot-gate-banco-production.sql"
$databaseGateTests = Read-RepoFile "scripts/deploy/testar-gate-banco-production.sh"
$flywayGate = Read-RepoFile "scripts/deploy/validar-gate-flyway-production.sh"
$flywayGateTests = Read-RepoFile "scripts/deploy/testar-gate-flyway-production.sh"
$operationHelper = Read-RepoFile "scripts/deploy/proteger-operacao-production.sh"
$backupProducer = Read-RepoFile "scripts/deploy/criar-backup-validado-production.sh"
$ephemeralPostgresWaiter = Read-RepoFile "scripts/deploy/aguardar-postgres-efemero.sh"
$previewBackfill = Read-RepoFile "scripts/deploy/executar-backfill-previews-production.sh"
$previewTransition = Read-RepoFile "scripts/deploy/coordenar-transicao-previews-production.sh"
$previewRuntime = Read-RepoFile "scripts/deploy/validar-transicao-previews-runtime.py"
$previewPublicSql = Read-RepoFile "scripts/deploy/validar-previews-publicos-production.sql"
$stdinRegressionTests = Read-RepoFile "scripts/deploy/testar-stdin-deploy-production.sh"
$backupIntegrationTests = Read-RepoFile "scripts/deploy/testar-backup-validado-production.sh"
$ciWorkflow = Read-RepoFile ".github/workflows/ci.yml"
$rootLayout = Read-RepoFile "frontend/src/app/layout.tsx"
$analyticsComponent = Read-RepoFile "frontend/src/components/analytics/consent-aware-analytics.tsx"
$deploymentContract = $workflow, $previewBackfill -join "`n"
$checks = [Collections.Generic.List[object]]::new()

function Add-Check {
  param([string]$Name, [bool]$Ok)
  $checks.Add([pscustomobject]@{ Name = $Name; Ok = $Ok })
}

foreach ($required in @(
    "workflow_dispatch:",
    "environment: production",
    "group: topsv3-production",
    "inputs.mode",
    "inputs.deploy_sha",
    "inputs.confirmation",
    "DEPLOY_PRODUCTION",
    "TOPSDOJOB_PROD_TARGET_SHA256",
    "/etc/topsdojob/target.env",
    "TOPSDOJOB_TARGET=production",
    "TOPSDOJOB_PROJECT=topsdojob-v3",
    "TARGET_VERIFIED=production",
    '/opt/topsv3/production/releases',
    '/opt/topsv3/production/current',
    '/opt/topsv3/secrets/production.env',
    "deploy/production/docker-compose.yml",
    "StrictHostKeyChecking=yes",
    "UserKnownHostsFile=",
    "HostKeyAlias=",
    "flyway migrate </dev/null",
    "flyway validate </dev/null",
    "proteger-operacao-production.sh",
    "snapshot_after",
    "snapshot_before",
    "validar-gate-banco-production.sh",
    "validar-gate-flyway-production.sh",
    "criar-backup-validado-production.sh",
    "testar-stdin-deploy-production.sh",
    "testar-backup-validado-production.sh",
    "BACKUP_RESTORE_RUNS=10/10",
    "backups/postgresql",
    "api/health/readiness"
  )) {
  Add-Check "contrato de deploy contem $required" ($deploymentContract.Contains($required))
}

foreach ($secret in @(
    "TOPSDOJOB_PROD_SSH_HOST",
    "TOPSDOJOB_PROD_SSH_USER",
    "TOPSDOJOB_PROD_SSH_PORT",
    "TOPSDOJOB_PROD_SSH_PRIVATE_KEY",
    "TOPSDOJOB_PROD_SSH_HOST_KEY",
    "TOPSDOJOB_PROD_TARGET_SHA256",
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
    "v3.esle.cloud",
    "topsv3-preprod",
    "/opt/topsv3/preprod"
  )) {
  Add-Check "deploy nao contem $forbidden" (-not $deploymentContract.Contains($forbidden))
}

Add-Check "workflow nao executa automaticamente em push" (-not ($workflow -match '(?m)^\s+push:\s*$'))
Add-Check "workflow separa preflight e deploy" (
  (-not [string]::IsNullOrWhiteSpace($preflightJob)) -and
  (-not [string]::IsNullOrWhiteSpace($deployJob)) -and
  ($deployJob.Contains("needs: preflight-production")) -and
  ($deployJob.Contains('if: ${{ inputs.mode == ''deploy'' }}'))
)
Add-Check "preflight fixa o SHA e exige confirmacao literal" (
  ($preflightJob.Contains('test "${DEPLOY_SHA}" = "${GITHUB_SHA}"')) -and
  ($preflightJob.Contains('test "${CONFIRMATION}" = "DEPLOY_PRODUCTION"'))
)
Add-Check "preflight valida somente acesso e destino canonico" (
  ($preflightJob.Contains("Preflight validate pinned SSH host key")) -and
  ($preflightJob.Contains("Preflight validate canonical production target")) -and
  ($preflightJob.Contains("/etc/topsdojob/target.env")) -and
  ($preflightJob.Contains("TOPSDOJOB_TARGET=production")) -and
  ($preflightJob.Contains("TOPSDOJOB_PROJECT=topsdojob-v3")) -and
  ($preflightJob.Contains("TARGET_VERIFIED=production"))
)
foreach ($forbiddenPreflight in @(
    "actions/checkout",
    "scp ",
    "Upload immutable release",
    "Synchronize IndexNow",
    "flyway",
    "backfill",
    "docker ",
    "nginx",
    "systemctl",
    "mvn ",
    "npm "
  )) {
  Add-Check "preflight nao contem $forbiddenPreflight" (-not $preflightJob.Contains($forbiddenPreflight))
}
Add-Check "deploy faz checkout do SHA autorizado" (
  ($deployJob.Contains('ref: ${{ inputs.deploy_sha }}')) -and
  ($deployJob.Contains('run: test "$(git rev-parse HEAD)" = "${DEPLOY_SHA}"'))
)
Add-Check "workflow nao altera manutencao ou indexacao externa" (
  -not ($deploymentContract -match 'maintenance|manutencao|robots\.txt.*(write|cat|printf)')
)
Add-Check "workflow preserva PostgreSQL" (
  ($workflow -match 'postgres_id_before') -and
  ($workflow -match 'postgres_volume_before') -and
  ($workflow.Contains('for service in backend frontend gateway; do')) -and
  ($workflow.Contains('op_run mutating "${compose[@]}" up -d --no-deps --force-recreate --no-build --pull never "${service}"'))
)
Add-Check "workflow testa gate de banco antes do deploy" (
  ($workflow.Contains("Test production database safety gate")) -and
  ($workflow.IndexOf("Test production database safety gate") -lt $workflow.IndexOf("Validate pinned SSH host key"))
)
Add-Check "workflow nao exige igualdade absoluta de contagens mutaveis" (
  -not ($deploymentContract -match 'test\s+"\$\{counts_after\}"\s+=\s+"\$\{counts_before\}"')
)
Add-Check "workflow preserva health e rollback no novo gate" (
  ($workflow -match 'capture_database_snapshot\s+"\$\{snapshot_before\}"') -and
  ($workflow -match 'capture_database_snapshot\s+"\$\{snapshot_after\}"\s+UP') -and
  ($workflow -match 'bash\s+"\$\{database_gate\}"') -and
  ($workflow.Contains('op_smoke "${release_sha}"'))
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
  ($workflow.IndexOf('bash "${flyway_gate}" after') -lt $workflow.LastIndexOf('up -d --no-deps --force-recreate --no-build --pull never "${service}"'))
)
Add-Check "workflow troca release somente depois de health e readiness" (
  ($workflow.LastIndexOf('mv -Tf "${current_link}"') -gt $workflow.IndexOf('op_smoke "${release_sha}"')) -and
  ($workflow.Contains('curl -fsS http://127.0.0.1:28080/api/health/readiness'))
)
function Convert-ToLogicalShellLines {
  param([string]$Content)
  return [regex]::Replace($Content, '\\\r?\n\s*', ' ')
}

$deploySources = @(
  Convert-ToLogicalShellLines $workflow
  Convert-ToLogicalShellLines $ciWorkflow
  Convert-ToLogicalShellLines $backupProducer
  Convert-ToLogicalShellLines $previewBackfill
) -join "`n"
$unsafeInteractiveCommandPattern = '(?m)docker\s+exec(?=[^\r\n]*\bpsql\b)(?=[^\r\n]*\s-i(?:\s|$))[^\r\n]*\bpsql\b[^\r\n]*(?:\s-c(?:\s|$)|\s--command(?:=|\s))'
$flywayReader = [regex]::Match(
  $workflow,
  '(?ms)^\s{10}read_flyway_state\(\) \{.*?^\s{10}\}'
).Value
Add-Check "nenhum psql command reutiliza stdin interativo" (
  -not [regex]::IsMatch($deploySources, $unsafeInteractiveCommandPattern)
)
Add-Check "leitura Flyway isola stdin e falha no primeiro erro SQL" (
  (-not [string]::IsNullOrWhiteSpace($flywayReader)) -and
  (-not ($flywayReader -match 'docker\s+exec\s+-i')) -and
  ($flywayReader.Contains('--no-psqlrc')) -and
  ($flywayReader.Contains('--set=ON_ERROR_STOP=1')) -and
  ($flywayReader.Contains('--command')) -and
  ($flywayReader.Contains('</dev/null'))
)
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
  ($operationRestore.Contains('for service in backend frontend gateway; do')) -and
  ($operationRestore.Contains('up -d --no-deps --force-recreate --no-build --pull never "${service}" || return 1')) -and
  ($operationRestore.Contains('sha256sum -c --status')) -and
  ($operationRestore.Contains('_op_verify_runtime')) -and
  ($operationRestore.Contains('op_smoke "${OP_PREVIOUS_SHA}"')) -and
  ($operationRestore.Contains('"${OP_ROOT}/current"'))
)

Add-Check "workflow valida host key antes do upload" (
  $workflow.IndexOf('name: Validate pinned SSH host key') -lt
  $workflow.IndexOf('name: Upload immutable release')
)
Add-Check "workflow valida identidade canonica antes de qualquer mutacao remota" (
  ($workflow.IndexOf('name: Validate canonical production target') -gt
    $workflow.IndexOf('name: Validate pinned SSH host key')) -and
  ($workflow.IndexOf('name: Validate canonical production target') -lt
    $workflow.IndexOf('op_phase CONFIGURING')) -and
  ($workflow.IndexOf('name: Validate canonical production target') -lt
    $workflow.IndexOf('name: Upload immutable release'))
)
foreach ($legacySecret in @(
    'secrets.PRODUCTION_HOST',
    'secrets.PRODUCTION_USER',
    'secrets.PRODUCTION_SSH_PORT',
    'secrets.PRODUCTION_SSH_IDENTITY',
    'secrets.PRODUCTION_SSH_HOST_KEY',
    'secrets.VPS_HOST',
    'TOPSDOJOB_PROD_TARGET_IDENTITY_SHA256',
    '/opt/topsv3/identity/production-target'
  )) {
  Add-Check "workflow nao usa secret generico $legacySecret" (-not $workflow.Contains($legacySecret))
}
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
  ($workflow.Contains('op_expect_indexnow "${indexnow_key}"')) -and
  ($workflow.Contains("cat <<'REMOTE_HEAD'")) -and
  ($workflow.Contains('printf ''%s\n'' "${INDEXNOW_KEY}"')) -and
  ($workflow.Contains("} | ssh")) -and
  ($workflow.Contains("--network none")) -and
  ($workflow.Contains("--pull never")) -and
  ($workflow.Contains('test -n "${key}"')) -and
  ($workflow.Contains('case "${key}" in')) -and
  ($workflow.Contains('awk "!/^INDEXNOW_KEY=/"')) -and
  (-not $workflow.Contains("''|*[!A-Za-z0-9-]*")) -and
  (-not $workflow.Contains('test -w /opt/topsv3/secrets/production.env'))
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
    "PostgreSQL init process complete; ready for start up.",
    "POSTGRES_EFEMERO_STABLE_PROBES",
    "--set=ON_ERROR_STOP=1",
    "--command 'SELECT 1' </dev/null",
    "POSTGRES_EFEMERO_DIAGNOSTICO"
  )) {
  Add-Check "espera PostgreSQL efemero contem $required" ($ephemeralPostgresWaiter.Contains($required))
}
Add-Check "espera PostgreSQL exige tres probes por padrao" (
  $ephemeralPostgresWaiter.Contains('POSTGRES_EFEMERO_STABLE_PROBES:-3')
)
Add-Check "backup cria banco de restauracao no entrypoint" (
  $backupProducer.Contains("POSTGRES_DB=restore_validation")
)
Add-Check "backup nao usa createdb" (-not $backupProducer.Contains("createdb"))
foreach ($required in @(
    "marcador_posterior",
    "processo_filho_recebe_eof",
    "falha_filho_interrompe",
    "caminho_sucesso_5_5",
    "backup_antes_flyway",
    "flyway_antes_startup",
    "health_antes_troca",
    "consumo_indesejado_reproduzido",
    "apply_idempotente_sem_delta",
    "residuos_zero",
    "DEPLOY_STDIN_REGRESSION_TESTS=PASS"
)) {
  Add-Check "regressao de stdin contem $required" ($stdinRegressionTests.Contains($required))
}
foreach ($required in @(
    "postgres:17.10-alpine",
    "BACKUP_STATUS=VALIDATED",
    "backup_restaurado_flyway_051",
    "POSTGRES_EFEMERO_READY=PASS probes=3",
    "autenticacao_real_recusada",
    "pg_restore --list",
    "BACKUP_RESTORE_INTEGRATION_TEST=PASS"
  )) {
  Add-Check "integracao de backup contem $required" ($backupIntegrationTests.Contains($required))
}
Add-Check "compose preserva shutdown gracioso e timeouts SMTP" (
  ($compose.Contains('SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE: 120s')) -and
  ($compose.Contains('SPRING_TASK_SCHEDULING_SHUTDOWN_AWAIT_TERMINATION: "true"')) -and
  ($compose.Contains('OUTBOX_SMTP_CONNECTION_TIMEOUT_MS: "5000"')) -and
  ($compose.Contains('OUTBOX_SMTP_TIMEOUT_MS: "10000"')) -and
  ($compose.Contains('OUTBOX_SMTP_WRITE_TIMEOUT_MS: "10000"'))
)
Add-Check "workflow nao exige drenagem global nem executa regularizacao mutante" (
  (-not $workflow.Contains('op_preview_preflight')) -and
  (-not $workflow.Contains('op_preview_drain_and_reconcile')) -and
  (-not $previewTransition.Contains('APPLY:delta')) -and
  $previewTransition.Contains('preview_backfill_main VALIDATE final')
)
Add-Check "transicao valida metadados e contrato publico antes da ativacao" (
  $workflow.Contains('op_preview_validate_before_activation') -and
  ($workflow.IndexOf('op_expect_candidate') -lt $workflow.IndexOf('op_preview_validate_before_activation')) -and
  ($workflow.IndexOf('op_preview_validate_before_activation') -lt $workflow.IndexOf('op_phase ACTIVATING')) -and
  $previewTransition.Contains('candidate.images.tsv') -and
  $previewTransition.Contains('op_run readonly python3 "${OP_PREVIEW_HELPER}" public') -and
  $previewTransition.Contains('[ "${rc}" -eq 0 ] || return "${rc}"')
)
$previewMain = Read-ShellFunction $previewBackfill 'preview_backfill_main'
$previewRun = Read-ShellFunction $previewBackfill 'run_backfill'
$previewLock = Read-ShellFunction $previewBackfill 'standalone_lock'
$previewExit = Read-ShellFunction $previewBackfill 'on_exit'
$previewCleanup = Read-ShellFunction $previewBackfill 'cleanup_job'
Add-Check "APPLY independente exige confirmacao explicita do SHA e preserva supervisor readonly" (
  $previewRun.Contains('runner=(op_run mutating)') -and
  $previewMain.Contains('[ "${OP_ACTIVE:-0}" -ne 1 ]') -and
  $previewMain.Contains('[ "${TOPSV3_PREVIEW_BACKFILL_CONFIRM:-}" = "APPLY:${RELEASE_SHA}" ]') -and
  $previewRun.Contains('[ "${TOPSV3_PREVIEW_BACKFILL_CONFIRM:-}" = "APPLY:${RELEASE_SHA}" ]') -and
  $previewRun.Contains('local apply_confirmed=false') -and
  (-not $previewMain.Contains('preview-drained.json')) -and
  $previewRuntime.Contains('private_before_snapshot_identity_unproven')
)
Add-Check "backfill independente compartilha mutex e bloqueia operacao sem resultado apurado" (
  $previewLock.Contains('_op_lock "${root}"') -and
  $previewLock.Contains('_op_validate_state "${state}"') -and
  $previewLock.Contains('docker ps --all --quiet --filter label=topsv3.preview.operation') -and
  $previewRun.Contains('operations/preview-backfill.pending') -and
  $previewRun.Contains('set -o noclobber') -and
  $operationHelper.Contains('operations/preview-backfill.pending') -and
  ($previewRun.IndexOf('set -o noclobber') -lt $previewRun.IndexOf('"${runner[@]}" env'))
)
Add-Check "backfill observa journal antes de aceitar terminal e nao limpa falha automaticamente" (
  $previewMain.Contains('outcome "${REPORT_DIR}"') -and
  $previewMain.Contains('terminal "${REPORT_DIR}"') -and
  ($previewMain.IndexOf('outcome "${REPORT_DIR}"') -lt $previewMain.IndexOf('terminal "${REPORT_DIR}"')) -and
  $previewMain.Contains('[ "${rc}" -eq 0 ] || return "${rc}"') -and
  $previewExit.Contains('[ "${JOB_VERIFIED:-0}" -eq 1 ] && [ "${rc}" -eq 0 ]') -and
  $previewExit.Contains('retry_allowed=false') -and
  $previewCleanup.Contains('terminal_success_unproven') -and
  $previewCleanup.Contains('"${actual_owner}" != "${JOB_OWNER}"') -and
  (-not $previewCleanup.Contains('docker stop'))
)
Add-Check "imagem do backfill fica pinada antes de executar" (
  $previewBackfill.Contains('pinned_compose=(-f') -and
  $previewMain.Contains('TOPSV3_PREVIEW_BACKFILL_IMAGE_ID:-') -and
  $previewRun.Contains('pin_arguments=("${TOPSV3_PREVIEW_BACKFILL_IMAGE_ID}")') -and
  $previewBackfill.Contains('--pull never') -and
  $previewRuntime.Contains('build: !reset null') -and
  $previewRuntime.Contains('backfill_image_pin_unproven') -and
  ($previewBackfill.IndexOf('pin "${REPORT_DIR}"') -lt $previewBackfill.IndexOf('"${runner[@]}" env'))
)
Add-Check "gate readonly limpa somente job terminal comprovado e preserva ambiguidade" (
  $previewTransition.Contains('cleanup-proof') -and
  $previewTransition.Contains('op_run mutating docker rm "${identifier}"') -and
  $previewTransition.Contains('op_preview_cleanup_validate "${report}" || cleanup_rc=$?') -and
  $previewTransition.Contains('OP_AMBIGUOUS=1') -and
  (-not $previewTransition.Contains('docker stop')) -and
  (-not $previewTransition.Contains('--kill-after')) -and
  (-not $previewTransition.Contains('OP_AMBIGUOUS=0'))
)
Add-Check "job de previews fixa identidade do executor e preserva captura privada" (
  $previewRuntime.Contains('return f"{os.geteuid()}:{os.getegid()}"') -and
  $previewRuntime.Contains('service.get("user") == user == executor_user()') -and
  $previewRuntime.Contains('runtime["Config"].get("User") == proof["user"]') -and
  $previewRuntime.Contains('(metadata.st_uid, metadata.st_gid) == (os.geteuid(), os.getegid())') -and
  $previewRuntime.Contains('metadata.st_mode & 0o7777 == 0o600') -and
  $previewRuntime.Contains('private_before_snapshot_owner_or_mode_mismatch')
)
Add-Check "gate SQL conserva selecao 4/10 e contrato integral" (
  $previewPublicSql.Contains('THEN 10 ELSE 4') -and
  $previewPublicSql.Contains('pf.posicao_foto <= a.limite_fotos') -and
  $previewPublicSql.Contains('preview_restrito_confirmado_em IS NOT NULL') -and
  $previewPublicSql.Contains('PUBLIC_PREVIEW_CONTRACT_QUERY_COMPLETE') -and
  $previewRuntime.Contains('public_contract_totals_inconsistent')
)
Add-Check "CI e deploy exercitam transicao e selecao com PostgreSQL" (
  $workflow.Contains('testar-transicao-previews-production.py') -and
  $workflow.Contains('testar-gate-previews-publicos-production.sh') -and
  $workflow.Contains('testar-coordenador-transicao-previews-production.sh') -and
  $ciWorkflow.Contains('testar-transicao-previews-production.py') -and
  $ciWorkflow.Contains('testar-coordenador-transicao-previews-production') -and
  $ciWorkflow.Contains('testar-gate-previews-publicos-production')
)
Add-Check "compose aguarda executor de moderacao alem do scheduler" (
  $compose.Contains('SPRING_TASK_EXECUTION_SHUTDOWN_AWAIT_TERMINATION: "true"') -and
  $compose.Contains('SPRING_TASK_EXECUTION_SHUTDOWN_AWAIT_TERMINATION_PERIOD: 120s')
)
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
    '${TOPSV3_APP_PREFIX:-topsv3-production}-backend',
    '${TOPSV3_APP_PREFIX:-topsv3-production}-frontend',
    '${TOPSV3_APP_PREFIX:-topsv3-production}-gateway',
    '${TOPSV3_APP_NETWORK:-topsv3-production-net}',
    "SPRING_PROFILES_ACTIVE: production",
    "APP_ENV: producao",
    "APP_CANONICAL_DOMAIN: https://topsdojob.com",
    "EFI_ENVIRONMENT: producao",
    "EFI_BASE_URL: https://pix.api.efipay.com.br",
    'OUTBOX_EMAIL_ENABLED: ${OUTBOX_EMAIL_ENABLED:-true}',
    'OUTBOX_SMTP_CONNECTION_TIMEOUT_MS: "5000"',
    'OUTBOX_SMTP_TIMEOUT_MS: "10000"',
    'OUTBOX_SMTP_WRITE_TIMEOUT_MS: "10000"',
    'SERVER_SHUTDOWN: graceful',
    'SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE: 120s',
    'SPRING_TASK_SCHEDULING_SHUTDOWN_AWAIT_TERMINATION: "true"',
    'SPRING_TASK_SCHEDULING_SHUTDOWN_AWAIT_TERMINATION_PERIOD: 120s',
    'EFI_RECONCILIATION_ENABLED: ${EFI_RECONCILIATION_ENABLED:-true}',
    'EFI_WEBHOOK_REGISTRATION_ENABLED: ${EFI_WEBHOOK_REGISTRATION_ENABLED:-false}',
    "SEARCH_INDEXING_MODE: public",
    '127.0.0.1:28080:8080',
    '127.0.0.1:23010:3000',
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

Write-Host "VALIDATION_RESULT=OK_DEPLOY_PRODUCTION_LOCAL"
