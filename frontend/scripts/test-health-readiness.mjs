import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import ts from 'typescript'

function source(relativePath) {
  return readFileSync(new URL(`../${relativePath}`, import.meta.url), 'utf8')
}

function resolveInternalPublicApiBase(environment) {
  const configured = environment.INTERNAL_API_URL?.trim()
  if (!configured) throw new Error('configuracao ausente')
  const parsed = new URL(configured)
  if (
    !['http:', 'https:'].includes(parsed.protocol)
    || parsed.username
    || parsed.password
    || parsed.pathname.replace(/\/+$/, '') !== '/api/public'
    || parsed.search
    || parsed.hash
  ) {
    throw new Error('configuracao invalida')
  }
  return configured.replace(/\/+$/, '')
}

function loadHealthModule(environment, fetchImplementation) {
  const { outputText } = ts.transpileModule(source('src/lib/server-health.ts'), {
    compilerOptions: {
      esModuleInterop: true,
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
  })
  const module = { exports: {} }
  const requireModule = (specifier) => {
    if (specifier === 'server-only') return {}
    if (specifier === '@/lib/public-server-api') return { resolveInternalPublicApiBase }
    throw new Error(`Import inesperado no health server-side: ${specifier}`)
  }

  new Function('module', 'exports', 'require', 'process', 'fetch', outputText)(
    module,
    module.exports,
    requireModule,
    { env: environment },
    fetchImplementation,
  )
  return module.exports
}

function response(status, payload) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => payload,
  }
}

const environment = {
  NODE_ENV: 'production',
  INTERNAL_API_URL: 'http://backend:8080/api/public',
  NEXT_PUBLIC_API_URL: 'https://topsdojob.com/api/public',
  HEALTH_READINESS_TIMEOUT_MS: '100',
}
const readyPayload = {
  status: 'UP',
  components: { application: 'UP', database: 'UP', migrations: 'UP' },
}

let executed = 0
async function test(name, fn) {
  await fn()
  executed += 1
  process.stdout.write(`ok ${executed} - ${name}\n`)
}

await test('usa somente a origem interna e o endpoint de readiness do backend', async () => {
  let request
  const health = loadHealthModule(environment, async (url, init) => {
    request = { url, init }
    return response(200, readyPayload)
  })

  assert.equal(await health.probeInternalBackendReadiness(environment), true)
  assert.equal(request.url, 'http://backend:8080/api/health/readiness')
  assert.equal(request.init.cache, 'no-store')
  assert.equal(request.init.redirect, 'error')
  assert.notEqual(request.url, environment.NEXT_PUBLIC_API_URL)
})

await test('retorna indisponivel para backend 503 ou contrato invalido', async () => {
  const unavailable = loadHealthModule(
    environment,
    async () => response(503, { status: 'DOWN' }),
  )
  const invalid = loadHealthModule(
    environment,
    async () => response(200, { status: 'UP', components: { application: 'UP' } }),
  )

  assert.equal(await unavailable.probeInternalBackendReadiness(environment), false)
  assert.equal(await invalid.probeInternalBackendReadiness(environment), false)
})

await test('aplica timeout curto e preserva liveness fora da dependencia', async () => {
  const health = loadHealthModule(
    environment,
    async (_url, init) => new Promise((_, reject) => {
      init.signal.addEventListener(
        'abort',
        () => reject(new DOMException('aborted', 'AbortError')),
      )
    }),
  )
  const started = performance.now()

  assert.equal(await health.probeInternalBackendReadiness(environment), false)
  assert.ok(performance.now() - started < 1_000)

  const livenessRoute = source('src/app/health/liveness/route.ts')
  assert.match(livenessRoute, /status:\s*'UP'/)
  assert.doesNotMatch(livenessRoute, /probeInternalBackendReadiness|INTERNAL_API_URL|R2|EFI/i)
})

await test('recupera automaticamente quando o backend volta', async () => {
  let available = false
  const health = loadHealthModule(environment, async () => available
    ? response(200, readyPayload)
    : response(503, { status: 'DOWN' }))

  assert.equal(await health.probeInternalBackendReadiness(environment), false)
  available = true
  assert.equal(await health.probeInternalBackendReadiness(environment), true)
})

await test('rotas publicam somente componentes sanitizados e 503 fail-closed', async () => {
  const readinessRoute = source('src/app/health/readiness/route.ts')
  const livenessRoute = source('src/app/health/liveness/route.ts')
  const combined = `${readinessRoute}\n${livenessRoute}`

  assert.match(readinessRoute, /internalApiReady \? 200 : 503/)
  assert.match(readinessRoute, /'internal-api'/)
  assert.match(combined, /Cache-Control': 'no-store'/)
  assert.doesNotMatch(combined, /backend:8080|INTERNAL_API_URL|postgres|flyway_schema_history/i)
})

console.log(`health/readiness frontend contract: ${executed} tests OK`)
