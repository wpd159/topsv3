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
$backupProducer = Read-RepoFile "scripts/deploy/criar-backup-validado-production.sh"
$ephemeralPostgresWaiter = Read-RepoFile "scripts/deploy/aguardar-postgres-efemero.sh"
$remoteDeploy = Read-RepoFile "scripts/deploy/executar-deploy-remoto-production.sh"
$remoteInvoker = Read-RepoFile "scripts/deploy/invocar-deploy-remoto-production.sh"
$previewBackfill = Read-RepoFile "scripts/deploy/executar-backfill-previews-production.sh"
$stdinRegressionTests = Read-RepoFile "scripts/deploy/testar-stdin-deploy-production.sh"
$backupIntegrationTests = Read-RepoFile "scripts/deploy/testar-backup-validado-production.sh"
$atomicActivator = Read-RepoFile "scripts/deploy/ativar-release-atomica-production.sh"
$atomicActivatorTests = Read-RepoFile "scripts/deploy/testar-release-atomica-production.sh"
$ciWorkflow = Read-RepoFile ".github/workflows/ci.yml"
$rootLayout = Read-RepoFile "frontend/src/app/layout.tsx"
$analyticsComponent = Read-RepoFile "frontend/src/components/analytics/consent-aware-analytics.tsx"
$deploymentContract = $workflow, $remoteDeploy, $remoteInvoker, $previewBackfill -join "`n"
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
    '${DEPLOY_ROOT}/releases',
    '${DEPLOY_ROOT}/current',
    '${SECRETS_ROOT}/production.env',
    "deploy/production/docker-compose.yml",
    "StrictHostKeyChecking=yes",
    "UserKnownHostsFile=",
    "HostKeyAlias=",
    "flyway migrate </dev/null",
    "flyway validate </dev/null",
    "ativar-release-atomica-production.sh",
    "snapshot_after",
    "snapshot_before",
    "validar-gate-banco-production.sh",
    "validar-gate-flyway-production.sh",
    "criar-backup-validado-production.sh",
    "testar-stdin-deploy-production.sh",
    "testar-backup-validado-production.sh",
    "testar-release-atomica-production.sh",
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
  ($remoteDeploy -match 'postgres_id_before') -and
  ($remoteDeploy -match 'postgres_volume_before') -and
  ($remoteDeploy.Contains('bash "${atomic_activator}"')) -and
  (-not ($deploymentContract -match 'up -d --no-deps --force-recreate backend frontend gateway'))
)
Add-Check "workflow testa gate de banco antes do deploy" (
  ($workflow.Contains("Test production database safety gate")) -and
  ($workflow.IndexOf("Test production database safety gate") -lt $workflow.IndexOf("Validate pinned SSH host key"))
)
Add-Check "workflow nao exige igualdade absoluta de contagens mutaveis" (
  -not ($deploymentContract -match 'test\s+"\$\{counts_after\}"\s+=\s+"\$\{counts_before\}"')
)
Add-Check "workflow preserva health e rollback no novo gate" (
  ($remoteDeploy -match 'capture_database_snapshot\s+"\$\{snapshot_before\}"') -and
  ($remoteDeploy -match 'capture_database_snapshot\s+"\$\{snapshot_after\}"\s+UP') -and
  ($remoteDeploy -match 'bash\s+"\$\{database_gate\}"') -and
  ($atomicActivator.Contains('verify_candidate')) -and
  ($atomicActivator.Contains('restore_gateway'))
)
Add-Check "workflow deriva versao Flyway sem hardcode" (
  ($remoteDeploy.Contains('expected_flyway="$(bash "${flyway_gate}" expected "${migration_dir}" </dev/null)"')) -and
  (-not ($remoteDeploy -match 'database_gate[^\r\n]*(051|052)')) -and
  (-not ($remoteDeploy -match 'flyway_gate[^\r\n]*(before|after)[^\r\n]*(051|052)'))
)
Add-Check "workflow exige backup antes de migration pendente" (
  ($remoteDeploy.Contains('if [ "${migrations_pending}" = true ]; then')) -and
  ($remoteDeploy.Contains('test "${backup_status}" = VALIDATED')) -and
  ($remoteDeploy.IndexOf('bash "${backup_producer}"') -lt $remoteDeploy.IndexOf('flyway migrate </dev/null'))
)
Add-Check "workflow valida Flyway antes do startup" (
  ($remoteDeploy.IndexOf('bash "${flyway_gate}" after') -gt $remoteDeploy.IndexOf('flyway migrate </dev/null')) -and
  ($remoteDeploy.IndexOf('bash "${flyway_gate}" after') -lt $remoteDeploy.IndexOf('bash "${atomic_activator}"'))
)
Add-Check "ativador troca trafego somente depois de health e readiness" (
  ($atomicActivator.IndexOf('verify_candidate') -lt $atomicActivator.IndexOf('switch_gateway')) -and
  ($atomicActivator.Contains('/api/health/readiness')) -and
  ($atomicActivator.Contains('/health/readiness')) -and
  ($atomicActivator.IndexOf('smoke_public') -lt $atomicActivator.IndexOf('finalize_activation'))
)
function Convert-ToLogicalShellLines {
  param([string]$Content)
  return [regex]::Replace($Content, '\\\r?\n\s*', ' ')
}

$deploySources = @(
  Convert-ToLogicalShellLines $workflow
  Convert-ToLogicalShellLines $ciWorkflow
  Convert-ToLogicalShellLines $backupProducer
  Convert-ToLogicalShellLines $atomicActivator
  Convert-ToLogicalShellLines $remoteDeploy
  Convert-ToLogicalShellLines $remoteInvoker
  Convert-ToLogicalShellLines $previewBackfill
) -join "`n"
$unsafeInteractiveCommandPattern = '(?m)docker\s+exec(?=[^\r\n]*\bpsql\b)(?=[^\r\n]*\s-i(?:\s|$))[^\r\n]*\bpsql\b[^\r\n]*(?:\s-c(?:\s|$)|\s--command(?:=|\s))'
$flywayReader = [regex]::Match(
  $remoteDeploy,
  '(?ms)^\s{2}read_flyway_state\(\) \{.*?^\s{2}\}'
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
Add-Check "ativador preserva aplicacao anterior se candidata falhar" (
  ($atomicActivator.Contains('remove_candidate')) -and
  ($atomicActivator.Contains('restore_gateway')) -and
  ($atomicActivator.Contains('verify_active_release')) -and
  (-not ($atomicActivator -match 'force-recreate[^\r\n]*(ACTIVE|active)'))
)
Add-Check "workflow valida host key antes do upload" (
  $workflow.IndexOf('name: Validate pinned SSH host key') -lt
  $workflow.IndexOf('name: Upload verified remote deploy controller')
)
Add-Check "workflow valida identidade canonica antes de qualquer mutacao remota" (
  ($workflow.IndexOf('name: Validate canonical production target') -gt
    $workflow.IndexOf('name: Validate pinned SSH host key')) -and
  ($workflow.IndexOf('name: Validate canonical production target') -lt
    $workflow.IndexOf('name: Upload verified remote deploy controller')) -and
  ($workflow.IndexOf('name: Validate canonical production target') -lt
    $workflow.IndexOf('name: Synchronize IndexNow key in production runtime')) -and
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
  $remoteDeploy -match "grep -Eqi 'v3\\.esle\\.cloud\|mailpit\|homologacao\|sandbox'"
)
Add-Check "workflow compila frontend com GA4 habilitado" (
  ($workflow -match 'NEXT_PUBLIC_ANALYTICS_ENABLED:\s+"true"') -and
  ($workflow.Contains("node scripts/test-ga4-production.mjs"))
)
Add-Check "workflow valida GA4 habilitado no runtime" (
  $remoteDeploy.Contains("grep -qx 'NEXT_PUBLIC_ANALYTICS_ENABLED=true'")
)
Add-Check "workflow sincroniza IndexNow sem expor a chave em argumento" (
  ($workflow.Contains("Synchronize IndexNow key in production runtime")) -and
  ($workflow.Contains('LOCAL_INDEXNOW_MANIFEST=')) -and
  ($workflow.Contains('REMOTE_INDEXNOW_MANIFEST=')) -and
  ($workflow.Contains('INDEXNOW_MANIFEST_SHA256=')) -and
  ($workflow.Contains('indexnow "${REMOTE_INDEXNOW_MANIFEST}" "${INDEXNOW_MANIFEST_SHA256}"')) -and
  (-not ($workflow -match 'ssh[^\r\n]*bash\s+-s')) -and
  (-not ($workflow -match '\}\s*\|\s*s[s]h\b')) -and
  ($remoteDeploy.Contains("--network none")) -and
  ($remoteDeploy.Contains("--pull never")) -and
  ($remoteDeploy.Contains('test -n "${key}"')) -and
  ($remoteDeploy.Contains('case "${key}" in')) -and
  ($remoteDeploy.Contains('awk "!/^INDEXNOW_KEY=/"')) -and
  ($remoteDeploy.Contains('--volume "${manifest}:/run/indexnow-key:ro"')) -and
  (-not $remoteDeploy.Contains('test -w /opt/topsv3/secrets/production.env'))
)
Add-Check "workflow valida IndexNow no runtime sem imprimir o valor" (
  $remoteDeploy.Contains("grep -q '^INDEXNOW_KEY='")
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
    "--command 'SELECT 1;' </dev/null",
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
    "plan_apply_validate_e_candidate_gates_alcancados",
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
    "marcador_posterior_gate_backup",
    "falha_real_postgres_bloqueia",
    "pg_restore --list",
    "BACKUP_RESTORE_INTEGRATION_TEST=PASS"
  )) {
  Add-Check "integracao de backup contem $required" ($backupIntegrationTests.Contains($required))
}
foreach ($required in @(
    "CANDIDATE_PROJECT",
    "CANDIDATE_NETWORK",
    "CANDIDATE_BACKEND_PORT",
    "CANDIDATE_FRONTEND_PORT",
    "CANDIDATE_GATEWAY_PORT",
    "verify_candidate_dns",
    "verify_database_gate",
    "validate_gateway_candidate",
    "systemctl reload nginx",
    "restore_gateway",
    "verify_active_release",
    "drain_window",
    "verify_coexistence_capacity",
    "old_gateway_connection_count",
    'TOPSV3_DRAIN_MIN_SECONDS:-600',
    'TOPSV3_RELEASE_SHUTDOWN_TIMEOUT_SECONDS',
    "shutdown_old_release_gracefully",
    "OLD_RELEASE_RUNTIME_RESIDUALS=0",
    "CONTINUITY_FAILURES"
  )) {
  Add-Check "ativador atomico contem $required" ($atomicActivator.Contains($required))
}
foreach ($required in @(
    "unhealthy",
    "readiness-503",
    "healthy",
    "nginx-invalid",
    "post-switch-failure",
    "seq 1 200",
    "OLD_LONG_REQUESTS_STARTED",
    "LONG_REQUEST_COMPLETED_SECONDS",
    "VIDEO_STREAM_COMPLETED",
    "NEW_REQUESTS_CANDIDATE_ONLY",
    "OLD_RELEASE_RUNNING_DURING_MONITOR",
    "OLD_GRACEFUL_SHUTDOWN_COMPLETED",
    "OLD_RUNTIME_RESIDUALS_0",
    "SMTP lento concluiu aceite e commit antes da remocao normal",
    "falha graciosa preservou container sem remocao forcada",
    "zero 500/502/504",
    "ATOMIC_RELEASE_DEPLOY_TESTS=PASS"
  )) {
  Add-Check "harness atomico contem $required" ($atomicActivatorTests.Contains($required))
}
Add-Check "ativador nao recria gateway ativo" (
  (-not ($atomicActivator -match 'force-recreate[^\r\n]*\$\{ACTIVE_PREFIX\}')) -and
  ($atomicActivator.Contains('sudo -n systemctl reload nginx'))
)
Add-Check "drenagem nao usa janela fixa insegura" (
  (-not $atomicActivator.Contains('TOPSV3_DRAIN_SECONDS:-15')) -and
  (-not ($atomicActivator -match 'docker stop --time 30')) -and
  ($atomicActivator.Contains('DRAIN_RESULT=CONNECTIONS_DRAINED')) -and
  ($atomicActivator.Contains('DRAIN_RESULT=MINIMUM_WINDOW_WITHOUT_CONNECTION_PROBE')) -and
  ($atomicActivator.Contains('docker stop --signal=TERM --time -1'))
)
Add-Check "releases removidas sem force somente apos shutdown" (
  ($atomicActivator.Contains('OLD_RELEASE_SHUTDOWN=GRACEFUL')) -and
  ($atomicActivator.Contains('docker rm "${container}"')) -and
  ($atomicActivator.Contains('CONTAINER_REMOVAL_BLOCKED')) -and
  (-not $atomicActivator.Contains('docker rm -f'))
)
Add-Check "candidata neutraliza registro Efi antes do startup" (
  ($atomicActivator.Contains('EFI_WEBHOOK_REGISTRATION_ENABLED=false')) -and
  ($atomicActivator.Contains('verify_candidate_job_isolation')) -and
  ($atomicActivator.IndexOf('verify_candidate_job_isolation') -lt $atomicActivator.IndexOf('switch_gateway'))
)
Add-Check "shutdown excede ciclo Spring e timeouts SMTP" (
  ($atomicActivator.Contains('SPRING_SHUTDOWN_PHASE_TIMEOUT_SECONDS=120')) -and
  ($atomicActivator.Contains('SMTP_CONNECTION_TIMEOUT_SECONDS=5')) -and
  ($atomicActivator.Contains('SMTP_READ_TIMEOUT_SECONDS=10')) -and
  ($atomicActivator.Contains('SMTP_WRITE_TIMEOUT_SECONDS=10')) -and
  ($atomicActivator.Contains('timeout de shutdown deve superar o ciclo gracioso do Spring')) -and
  ($compose.Contains('SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE: 120s')) -and
  ($compose.Contains('SPRING_TASK_SCHEDULING_SHUTDOWN_AWAIT_TERMINATION: "true"')) -and
  ($compose.Contains('OUTBOX_SMTP_CONNECTION_TIMEOUT_MS: "5000"')) -and
  ($compose.Contains('OUTBOX_SMTP_TIMEOUT_MS: "10000"')) -and
  ($compose.Contains('OUTBOX_SMTP_WRITE_TIMEOUT_MS: "10000"'))
)
Add-Check "candidata usa aliases em rede isolada" (
  ($atomicActivator.Contains('TOPSV3_APP_NETWORK="${CANDIDATE_NETWORK}"')) -and
  ($atomicActivator.Contains('docker network connect --alias postgres')) -and
  ($atomicActivator.Contains('http://backend:8080/api/public'))
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
    '127.0.0.1:${TOPSV3_BACKEND_BIND_PORT:-28080}:8080',
    '127.0.0.1:${TOPSV3_FRONTEND_BIND_PORT:-23010}:3000',
    '127.0.0.1:${TOPSV3_GATEWAY_BIND_PORT:-23000}:23000',
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
