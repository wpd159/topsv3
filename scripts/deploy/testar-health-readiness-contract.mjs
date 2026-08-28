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
const ciWorkflow = read('.github/workflows/ci.yml')
const atomicProductionActivator = read('scripts/deploy/ativar-release-atomica-production.sh')

assert.match(backendHealth, /@GetMapping\("\/liveness"\)/)
assert.match(backendHealth, /@GetMapping\("\/readiness"\)/)
assert.match(backendHealth, /HttpStatus\.SERVICE_UNAVAILABLE/)
assert.match(databaseProbe, /SELECT 1 AS probe/)
assert.match(databaseProbe, /FROM flyway_schema_history/)
assert.match(databaseProbe, /setReadOnly\(true\)/)
assert.match(databaseProbe, /setQueryTimeout/)
assert.match(readinessService, /future\.get\(timeout\.toMillis\(\), TimeUnit\.MILLISECONDS\)/)
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

assert.match(productionWorkflow, /bash "\$\{atomic_activator\}"/)
assert.match(atomicProductionActivator, /verify_candidate/)
assert.match(atomicProductionActivator, /\/api\/health\/liveness/)
assert.match(atomicProductionActivator, /\/api\/health\/readiness/)
assert.match(atomicProductionActivator, /\/health\/liveness/)
assert.match(atomicProductionActivator, /\/health\/readiness/)
assert.ok(
  atomicProductionActivator.indexOf('stage_or_abort verify_candidate') <
    atomicProductionActivator.indexOf('stage_or_abort switch_gateway'),
  'a candidata deve concluir readiness antes da troca de trafego',
)

console.log('HEALTH_READINESS_CONTRACT_TESTS=PASS')
