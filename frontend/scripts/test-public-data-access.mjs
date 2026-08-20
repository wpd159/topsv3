import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import ts from 'typescript'

function source(relativePath) {
  return readFileSync(new URL(`../${relativePath}`, import.meta.url), 'utf8')
}

class ContractError extends Error {
  constructor(message, kind, status, retryable = false, requestId = null) {
    super(message)
    this.name = 'ApiContractError'
    this.kind = kind
    this.status = status
    this.retryable = retryable
    this.requestId = requestId
  }
}

function loadServerApi({ fetchImpl, environment, logs = [] }) {
  const { outputText } = ts.transpileModule(source('src/lib/public-server-api.ts'), {
    compilerOptions: {
      esModuleInterop: true,
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
  })
  const module = { exports: {} }
  const cache = new Map()
  const requireModule = (specifier) => {
    if (specifier === 'server-only') return {}
    if (specifier === 'node:crypto') return { randomUUID: () => 'request-id-controlado' }
    if (specifier === '@/lib/api-contract') return { ApiContractError: ContractError }
    if (specifier === 'next/cache') {
      return {
        unstable_cache: (loader, keyParts) => async () => {
          const key = JSON.stringify(keyParts)
          if (!cache.has(key)) {
            const pending = Promise.resolve().then(loader)
            cache.set(key, pending)
            pending.catch(() => cache.delete(key))
          }
          return cache.get(key)
        },
      }
    }
    throw new Error(`Import inesperado no cliente server-side: ${specifier}`)
  }
  const testConsole = { error: (...args) => logs.push(args) }

  new Function(
    'module',
    'exports',
    'require',
    'process',
    'fetch',
    'console',
    outputText,
  )(
    module,
    module.exports,
    requireModule,
    { env: environment },
    fetchImpl,
    testConsole,
  )
  return module.exports
}

function response(status, payload = { ok: true }) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (name) => name.toLowerCase() === 'x-request-id' ? 'upstream-id' : null },
    json: async () => payload,
  }
}

const productionEnvironment = {
  NODE_ENV: 'production',
  INTERNAL_API_URL: 'http://backend:8080/api/public',
  NEXT_PUBLIC_API_URL: 'https://topsdojob.com/api/public',
  PUBLIC_API_TIMEOUT_MS: '5000',
  BUILD_VERSION: 'test-build',
}

let executed = 0
async function test(name, fn) {
  await fn()
  executed += 1
  process.stdout.write(`ok ${executed} - ${name}\n`)
}

await test('configuracao server-only resolve a API interna sem fallback em producao', async () => {
  const api = loadServerApi({
    fetchImpl: async () => response(200),
    environment: productionEnvironment,
  })
  assert.equal(api.resolveInternalPublicApiBase(productionEnvironment), 'http://backend:8080/api/public')
  assert.throws(
    () => api.resolveInternalPublicApiBase({ NODE_ENV: 'production' }),
    (error) => error.reason === 'CONFIGURATION',
  )
  assert.throws(
    () => loadServerApi({
      fetchImpl: async () => response(200),
      environment: { NODE_ENV: 'production' },
    }),
    (error) => error.reason === 'CONFIGURATION',
  )
  assert.throws(
    () => api.resolveInternalPublicApiBase({
      NODE_ENV: 'production',
      INTERNAL_API_URL: 'https://topsdojob.com/api/public',
      NEXT_PUBLIC_API_URL: 'https://topsdojob.com/api/public',
    }),
    (error) => error.reason === 'CONFIGURATION',
  )
})

await test('timeout e finito, configuravel e distinto de 404', async () => {
  const api = loadServerApi({
    environment: productionEnvironment,
    fetchImpl: async (_url, init) => new Promise((_, reject) => {
      init.signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
    }),
  })
  assert.equal(api.resolvePublicApiTimeoutMs(productionEnvironment), 5000)
  await assert.rejects(
    api.publicServerApiJson('/localidades', {
      endpointFamily: 'test.timeout',
      cache: { mode: 'no-store' },
      timeoutMs: 100,
      validate: (payload) => payload,
    }),
    (error) => error.reason === 'TIMEOUT' && error.status === null,
  )

  const slowBodyApi = loadServerApi({
    environment: productionEnvironment,
    fetchImpl: async (_url, init) => ({
      ...response(200),
      json: async () => new Promise((_, reject) => {
        init.signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
      }),
    }),
  })
  await assert.rejects(
    slowBodyApi.publicServerApiJson('/localidades', {
      endpointFamily: 'test.timeout-body',
      cache: { mode: 'no-store' },
      timeoutMs: 100,
      validate: (payload) => payload,
    }),
    (error) => error.reason === 'TIMEOUT' && error.status === 200,
  )
})

await test('404, 5xx, JSON invalido e contrato invalido permanecem distintos', async () => {
  for (const [status, reason] of [[404, 'NOT_FOUND'], [503, 'UPSTREAM_FAILURE']]) {
    const api = loadServerApi({
      environment: productionEnvironment,
      fetchImpl: async () => response(status),
    })
    await assert.rejects(
      api.publicServerApiJson('/teste', {
        endpointFamily: 'test.status',
        cache: { mode: 'no-store' },
        validate: (payload) => payload,
      }),
      (error) => error.reason === reason && error.status === status,
    )
  }

  const invalidJsonApi = loadServerApi({
    environment: productionEnvironment,
    fetchImpl: async () => ({ ...response(200), json: async () => { throw new Error('invalid') } }),
  })
  await assert.rejects(
    invalidJsonApi.publicServerApiJson('/teste', {
      endpointFamily: 'test.invalid-json',
      cache: { mode: 'no-store' },
      validate: (payload) => payload,
    }),
    (error) => error.reason === 'INVALID_JSON',
  )

  const invalidContractApi = loadServerApi({
    environment: productionEnvironment,
    fetchImpl: async () => response(200, {}),
  })
  await assert.rejects(
    invalidContractApi.publicServerApiJson('/teste', {
      endpointFamily: 'test.invalid-contract',
      cache: { mode: 'no-store' },
      validate: () => { throw new Error('contract') },
    }),
    (error) => error.reason === 'INVALID_CONTRACT',
  )
})

await test('cache compartilha somente sucesso validado e nunca armazena 5xx', async () => {
  let calls = 0
  const api = loadServerApi({
    environment: productionEnvironment,
    fetchImpl: async () => {
      calls += 1
      return response(200, { value: 7 })
    },
  })
  const options = {
    endpointFamily: 'test.cached',
    cache: { mode: 'revalidate', seconds: 300, tags: ['test-cache'] },
    validate: (payload) => payload.value,
  }
  assert.equal(await api.publicServerApiJson('/localidades', options), 7)
  assert.equal(await api.publicServerApiJson('/localidades', options), 7)
  assert.equal(calls, 1)

  let failureCalls = 0
  const failureApi = loadServerApi({
    environment: productionEnvironment,
    fetchImpl: async () => {
      failureCalls += 1
      return failureCalls === 1 ? response(503) : response(200, { value: 9 })
    },
  })
  await assert.rejects(failureApi.publicServerApiJson('/localidades/catalogo', options))
  assert.equal(await failureApi.publicServerApiJson('/localidades/catalogo', options), 9)
  assert.equal(failureCalls, 2)
})

await test('seed, autenticacao e URL assinada sao recusadas pelo cache compartilhado', async () => {
  const api = loadServerApi({
    environment: productionEnvironment,
    fetchImpl: async () => response(200),
  })
  const cached = {
    endpointFamily: 'test.forbidden-cache',
    cache: { mode: 'revalidate', seconds: 300, tags: ['test-cache'] },
    validate: (payload) => payload,
  }
  for (const path of [
    '/anuncios?ordemSeed=123',
    '/auth/me',
    '/midias/url-assinada',
  ]) {
    await assert.rejects(
      api.publicServerApiJson(path, cached),
      (error) => error.reason === 'CONFIGURATION',
    )
  }
})

await test('logs sanitizados nao expoem URL interna nem query', async () => {
  const logs = []
  const api = loadServerApi({
    environment: productionEnvironment,
    logs,
    fetchImpl: async () => { throw new Error('network') },
  })
  await assert.rejects(api.publicServerApiJson('/teste?busca=dado', {
    endpointFamily: 'test.logs',
    cache: { mode: 'no-store' },
    validate: (payload) => payload,
  }))
  const serialized = JSON.stringify(logs)
  assert.doesNotMatch(serialized, /backend:8080|topsdojob\.com|busca=|dado/)
  assert.match(serialized, /test\.logs|NETWORK_FAILURE|5000/)
})

await test('fronteiras server/client, caches e deduplicacao estao explicitas', async () => {
  const serverCore = source('src/lib/public-server-api.ts')
  const serverCatalog = source('src/lib/public-catalog-server-api.ts')
  const browserCatalog = source('src/lib/public-catalog-api.ts')
  const browserContract = source('src/lib/api-contract.ts')
  const blog = source('src/lib/blog-api.ts')
  const faq = source('src/lib/faq-public-api.ts')
  const siteServer = source('src/lib/site-content-server.ts')
  const sitemap = source('src/app/sitemap.ts')

  assert.match(serverCore, /import 'server-only'/)
  assert.match(serverCore, /Environment = process\.env/)
  assert.match(serverCore, /environment\.INTERNAL_API_URL/)
  assert.match(serverCore, /DEFAULT_PUBLIC_API_TIMEOUT_MS = 5_000/)
  assert.doesNotMatch(serverCore, /https:\/\/topsdojob\.com\/api/)
  assert.doesNotMatch(browserCatalog, /INTERNAL_API_URL|public-server-api/)
  assert.match(browserContract, /NEXT_PUBLIC_API_URL \|\| '\/api\/public'/)
  assert.match(serverCatalog, /catalog\.list\.(state|city|neighborhood)[\s\S]*NO_STORE/)
  assert.match(serverCatalog, /catalog\.list\.all[\s\S]*NO_STORE/)
  assert.match(serverCatalog, /catalog\.locations\.discovery[\s\S]*CATALOG_CACHE/)
  assert.match(serverCatalog, /catalog\.seo\.city[\s\S]*SEO_CACHE/)
  assert.match(blog, /PUBLIC_BLOG_REVALIDATE_SECONDS = 300/)
  assert.match(faq, /PUBLIC_FAQ_REVALIDATE_SECONDS = 3_600/)
  assert.match(siteServer, /PUBLIC_SITE_CONTENT_REVALIDATE_SECONDS/)
  assert.doesNotMatch(sitemap, /NEXT_PUBLIC_API_URL|fetch\(/)

  for (const path of [
    'src/app/(public-routes)/layout.tsx',
    'src/app/(private-routes)/layout.tsx',
  ]) {
    const layout = source(path)
    assert.match(layout, /export const dynamic = ['"]force-dynamic['"]/)
    assert.match(layout, /site-content-server/)
  }

  for (const path of [
    'src/app/(public-routes)/acompanhantes/page.tsx',
    'src/app/(public-routes)/acompanhantes/[estado]/page.tsx',
    'src/app/(public-routes)/acompanhantes/[estado]/[cidade]/page.tsx',
    'src/app/(public-routes)/acompanhantes/[estado]/[cidade]/[bairro]/page.tsx',
    'src/app/(public-routes)/anuncios/page.tsx',
    'src/app/(public-routes)/anuncios/[slug]/page.tsx',
  ]) {
    const page = source(path)
    assert.match(page, /export const dynamic = ["']force-dynamic["']/)
    assert.doesNotMatch(page, /export const revalidate/)
    assert.match(page, /public-catalog-server-api/)
  }

  for (const path of [
    'src/app/(public-routes)/acompanhantes/[estado]/page.tsx',
    'src/app/(public-routes)/acompanhantes/[estado]/[cidade]/page.tsx',
    'src/app/(public-routes)/acompanhantes/[estado]/[cidade]/[bairro]/page.tsx',
    'src/app/(public-routes)/anuncios/[slug]/page.tsx',
  ]) {
    const page = source(path)
    assert.match(page, /import \{ cache \} from ['"]react['"]/)
    assert.match(page, /cache\(async/)
  }
})

await test('configuracao versionada e gates preservam as fases anteriores', async () => {
  const packageJson = JSON.parse(source('package.json'))
  assert.equal(packageJson.scripts['test:public-data-access'], 'node scripts/test-public-data-access.mjs')
  for (const name of [
    'test:json-ld-security',
    'test:public-ordering',
    'test:public-http-states',
    'test:search-indexing',
    'test:seo-ai',
    'test:sharp-security',
  ]) {
    assert.equal(typeof packageJson.scripts[name], 'string')
  }

  for (const path of [
    '../deploy/production/docker-compose.yml',
    '../deploy/preprod/docker-compose.yml',
    '../deploy/hml/docker-compose.yml',
  ]) {
    const config = source(path)
    assert.match(config, /INTERNAL_API_URL:\s*http:\/\/backend:8080\/api\/public/)
    assert.match(config, /PUBLIC_API_TIMEOUT_MS:\s*["']?\$\{PUBLIC_API_TIMEOUT_MS:-5000\}["']?/)
  }

  for (const path of [
    '../.github/workflows/deploy-production.yml',
    '../.github/workflows/deploy-preprod.yml',
  ]) {
    const workflow = source(path)
    assert.match(workflow, /INTERNAL_API_URL:\s*http:\/\/backend:8080\/api\/public/)
    assert.match(workflow, /PUBLIC_API_TIMEOUT_MS:\s*["']?5000["']?/)
  }
})

assert.equal(executed, 8)
console.log(`PUBLIC_DATA_ACCESS_RESULT=OK tests=${executed}`)
