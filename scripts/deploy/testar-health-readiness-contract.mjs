import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../..', import.meta.url))
const read = (relativePath) => readFileSync(`${root}/${relativePath}`, 'utf8')

function service(compose, name, nextName) {
  const end = nextName ? `(?=^  ${nextName}:)` : '(?=^networks:|^volumes:|(?![\\s\\S]))'
  const match = compose.match(new RegExp(`^  ${name}:\\s[\\s\\S]*?${end}`, 'm'))
  assert.ok(match, `servico ${name} ausente do Compose`)
  return match[0]
}

const backendHealth = read('backend/src/main/java/br/com/topsdojob/v3/platform/health/HealthController.java')
const databaseProbe = read('backend/src/main/java/br/com/topsdojob/v3/platform/health/DatabaseReadinessProbe.java')
const readinessService = read('backend/src/main/java/br/com/topsdojob/v3/platform/health/BackendReadinessService.java')
const frontendLiveness = read('frontend/src/app/health/liveness/route.ts')
const frontendReadiness = read('frontend/src/app/health/readiness/route.ts')
const frontendProbe = read('frontend/src/lib/server-health.ts')
const productionCompose = read('deploy/production/docker-compose.yml')
const preprodCompose = read('deploy/preprod/docker-compose.yml')
const hmlCompose = read('deploy/hml/docker-compose.yml')
const productionWorkflow = read('.github/workflows/deploy-production.yml')
const operationHelper = read('scripts/deploy/proteger-operacao-production.sh')
const ciWorkflow = read('.github/workflows/ci.yml')

assert.match(backendHealth, /@GetMapping\("\/liveness"\)/)
assert.match(backendHealth, /@GetMapping\("\/readiness"\)/)
assert.match(backendHealth, /HttpStatus\.SERVICE_UNAVAILABLE/)
assert.match(databaseProbe, /SELECT 1 AS probe/)
assert.match(databaseProbe, /FROM flyway_schema_history/)
assert.match(databaseProbe, /setReadOnly\(true\)/)
assert.match(databaseProbe, /setQueryTimeout/)
assert.match(readinessService, /TimeUnit\.(?:MILLISECONDS|NANOSECONDS)/)
assert.doesNotMatch(`${databaseProbe}\n${readinessService}`, /R2|Efi|Cloudflare|MailSender/)

assert.match(frontendLiveness, /status:\s*'UP'/)
assert.doesNotMatch(frontendLiveness, /INTERNAL_API_URL|probeInternalBackendReadiness/)
assert.match(frontendReadiness, /internalApiReady \? 200 : 503/)
assert.match(frontendReadiness, /'internal-api'/)
assert.match(frontendProbe, /resolveInternalPublicApiBase/)
assert.match(frontendProbe, /\/api\/health\/readiness/)
assert.match(frontendProbe, /cache:\s*'no-store'/)
assert.match(frontendProbe, /AbortController/)
assert.doesNotMatch(frontendProbe, /\bNEXT_PUBLIC_API_URL\b|\bR2_[A-Z_]+\b|\bEFI_[A-Z_]+\b|Cloudflare/)

for (const [name, compose, hasGateway] of [
  ['production', productionCompose, true],
  ['preprod', preprodCompose, true],
  ['hml', hmlCompose, false],
]) {
  const backend = service(compose, 'backend', 'frontend')
  const frontend = service(compose, 'frontend', hasGateway ? 'gateway' : null)
  assert.match(backend, /healthcheck:[\s\S]*\/api\/health\/liveness/)
  assert.doesNotMatch(backend.match(/healthcheck:[\s\S]*/)?.[0] ?? '', /\/readiness/)
  assert.match(frontend, /healthcheck:[\s\S]*\/health\/liveness/)
  assert.doesNotMatch(frontend.match(/healthcheck:[\s\S]*/)?.[0] ?? '', /\/readiness/)
  assert.match(frontend, /backend:[\s\S]*condition:\s*service_healthy/)
  if (hasGateway) {
    const gateway = service(compose, 'gateway', null)
    assert.match(gateway, /\/api\/health\/liveness/)
    assert.match(gateway, /\/health\/liveness/)
    assert.doesNotMatch(gateway, /\/readiness/)
  }
  process.stdout.write(`ok - Compose ${name} usa liveness sem dependencia externa\n`)
}

assert.match(ciWorkflow, /npm run test:health-readiness/)
assert.doesNotMatch(ciWorkflow, /\b(?:ssh|scp|rsync)\b|PREPROD_|environment:\s*preprod/)
process.stdout.write('ok - CI preserva os testes sem acesso remoto\n')

assert.match(productionWorkflow, /npm run test:health-readiness/)
assert.match(productionWorkflow, /\/api\/health\/liveness/)
assert.match(productionWorkflow, /\/api\/health\/readiness/)
assert.match(productionWorkflow, /\/health\/liveness/)
assert.match(productionWorkflow, /\/health\/readiness/)
process.stdout.write('ok - workflow production aguarda readiness real\n')

const activation = productionWorkflow.match(
  /name: Build and activate production release[\s\S]*?(?=\n      - name:|$)/,
)?.[0]
assert.ok(activation, 'etapa de ativacao ausente')
const begin = activation.indexOf('op_begin "${deploy_root}" "${release_sha}"')
const baseline = activation.indexOf('op_smoke "${previous_sha}"')
const mutation = activation.indexOf('op_phase CONFIGURING')
assert.ok(begin >= 0 && baseline > begin && mutation > baseline)
assert.match(operationHelper, /a60b1e74978017a5bba1577f58804933b347c790/)
assert.match(operationHelper, /previous\.health-profile/)
assert.match(operationHelper, /candidate\.health-profile/)
assert.match(operationHelper, /main-v1/)
assert.match(operationHelper, /legacy-a60/)
assert.match(operationHelper, /recovery_started_monotonic_ms="\$\(_op_monotonic_ms\)"/)
assert.match(operationHelper, /recovery_deadline_ms=\$\(\(recovery_started_monotonic_ms \+ 300000\)\)/)
assert.match(operationHelper, /RECOVERY_WINDOW event=start[^\n]*duration_ms=300000/)
assert.match(
  operationHelper,
  /\[ "\$\{recovery_finished_monotonic_ms\}" -ge "\$\{recovery_deadline_ms\}" \]/,
)
assert.doesNotMatch(operationHelper, /recovery_deadline=\$\(\(SECONDS \+ 300\)\)/)
const startup = activation.indexOf('op_phase ACTIVATING')
const health = activation.indexOf('op_smoke "${release_sha}"')
const switchLink = activation.indexOf('mv -Tf "${current_link}"')
assert.ok(startup >= 0 && health > startup && switchLink > health)
for (const endpoint of ['28080/api/health/readiness', '23010/health/readiness', '23000/health/readiness']) {
  assert.ok(operationHelper.includes(endpoint), `sonda obrigatoria ausente: ${endpoint}`)
}
assert.doesNotMatch(activation, /trap on_error ERR|rollback_application|application_started/)
assert.match(operationHelper, /trap '_op_exit "\$\?"' EXIT/)
const startupSequence = activation.slice(startup, health)
const restoration = operationHelper.match(/_op_restore\(\) \{[\s\S]*?\n\}/)?.[0]
assert.ok(restoration, 'restauracao ausente')
for (const [name, sequence] of [['ativacao', startupSequence], ['recuperacao', restoration]]) {
  assert.match(sequence, /for service in backend frontend gateway; do\s+op_run mutating/)
  assert.match(sequence, /up -d --no-deps --force-recreate --no-build --pull never "\$\{service\}"/)
  assert.doesNotMatch(sequence, /up[^\n]*backend frontend gateway/)
  process.stdout.write(`ok - ${name} inicia cada servico sem bloquear o callback na saude do backend\n`)
}
assert.ok(restoration.indexOf('_op_verify_runtime') > restoration.indexOf('up -d --no-deps'))
assert.ok(restoration.lastIndexOf('op_smoke "${OP_PREVIOUS_SHA}"') > restoration.indexOf('up -d --no-deps'))
assert.ok(activation.indexOf('op_finish') > activation.indexOf('IMPORTACAO_.*(INICIO|EXECUTADA)'))
assert.ok(
  activation.indexOf('capture_database_snapshot "${snapshot_after}" UP') < switchLink,
  'a release deve passar health e gate de banco antes da confirmacao do symlink',
)

console.log('HEALTH_READINESS_CONTRACT_TESTS=PASS')
