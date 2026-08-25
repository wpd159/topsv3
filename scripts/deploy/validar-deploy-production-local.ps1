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
    "rollback_application",
    "snapshot_after",
    "snapshot_before",
    "validar-gate-banco-production.sh",
    "validar-gate-flyway-production.sh",
    "criar-backup-validado-production.sh",
    "backups/postgresql",
    "api/health/readiness"
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
  ($workflow -match 'test\s+"\$\{healthy\}"\s+-eq\s+1')
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
  ($workflow.LastIndexOf('mv -Tf "${current_link}"') -gt $workflow.IndexOf('test "${healthy}" -eq 1')) -and
  ($workflow.Contains('curl -fsS http://127.0.0.1:28080/api/health/readiness'))
)
Add-Check "workflow restaura aplicacao anterior se startup falhar" (
  ($workflow.Contains('application_started=0')) -and
  ($workflow.Contains('application_started=1')) -and
  ($workflow.Contains('if [ "${application_started}" -eq 1 ]; then'))
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

Write-Host "VALIDATION_RESULT=OK_DEPLOY_PRODUCTION_LOCAL"
