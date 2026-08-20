import assert from "node:assert/strict"
import { existsSync, readFileSync, readdirSync } from "node:fs"
import ts from "typescript"

function source(relativePath) {
  return readFileSync(new URL(`../${relativePath}`, import.meta.url), "utf8")
}

function loadIndexNowModule(env = {}) {
  const { outputText } = ts.transpileModule(source("src/lib/seo/indexnow.ts"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  })
  const compiledModule = { exports: {} }
  const testProcess = {
    env: {
      INDEXNOW_KEY: "INDEXNOW-TEST-KEY-2026",
      INDEXNOW_SITE_URL: "https://topsdojob.com",
      ...env,
    },
  }
  new Function("module", "exports", "require", "process", outputText)(
    compiledModule,
    compiledModule.exports,
    (specifier) => {
      if (specifier === "server-only") return {}
      throw new Error(`Unexpected module in IndexNow server test: ${specifier}`)
    },
    testProcess,
  )
  return compiledModule.exports
}

function loadIndexNowClientModule() {
  const { outputText } = ts.transpileModule(source("src/lib/seo/indexnow-client.ts"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  })
  const compiledModule = { exports: {} }
  const calls = []
  const requireStub = (specifier) => {
    if (specifier.endsWith("/admin/anuncios/actions")) {
      return { revalidarCacheCatalogoPublico: async (event) => calls.push(event) }
    }
    throw new Error(`Unexpected module in IndexNow client test: ${specifier}`)
  }
  new Function("module", "exports", "require", "process", "window", outputText)(
    compiledModule,
    compiledModule.exports,
    requireStub,
    { env: { NEXT_PUBLIC_SITE_URL: "https://topsdojob.com" } },
    undefined,
  )
  return { ...compiledModule.exports, calls }
}

function loadCatalogActionModule(sendIndexNow) {
  const { outputText } = ts.transpileModule(
    source("src/app/(painel-admin)/admin/anuncios/actions.ts"),
    { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } },
  )
  const compiledModule = { exports: {} }
  const afterCallbacks = []
  const revalidatedTags = []
  const requireStub = (specifier) => {
    if (specifier === "next/cache") {
      return { revalidateTag: (tag) => revalidatedTags.push(tag) }
    }
    if (specifier === "next/server") {
      return { after: (callback) => afterCallbacks.push(callback) }
    }
    if (specifier.endsWith("/public-catalog-api")) {
      return { PUBLIC_CATALOG_CACHE_TAG: "public-catalog" }
    }
    if (specifier.endsWith("/seo/indexnow")) {
      return { enviarUrlsParaIndexNow: sendIndexNow }
    }
    throw new Error(`Unexpected module in catalog action test: ${specifier}`)
  }
  new Function("module", "exports", "require", "console", outputText)(
    compiledModule,
    compiledModule.exports,
    requireStub,
    { info: () => {}, warn: () => {} },
  )
  return { ...compiledModule.exports, afterCallbacks, revalidatedTags }
}

function response(status) {
  return { status }
}

function event(eventType, eventFingerprint, urls) {
  return { eventType, eventFingerprint, urls }
}

const indexNow = loadIndexNowModule()
const {
  enviarUrlsParaIndexNow,
  filtrarUrlsDoHost,
  getIndexNowConfig,
  getIndexNowKeyLocation,
  resetIndexNowStateForTests,
} = indexNow

assert.match(source("src/lib/seo/indexnow.ts"), /import ["']server-only["']/)

assert.equal(getIndexNowConfig().enabled, true)
assert.equal(getIndexNowKeyLocation(), "https://topsdojob.com/INDEXNOW-TEST-KEY-2026.txt")
assert.deepEqual(
  filtrarUrlsDoHost([
    "https://topsdojob.com/anuncios/perfil-publico",
    "https://topsdojob.com/anuncios/perfil-publico",
    "https://topsdojob.com/acompanhantes/go/goiania",
    "http://topsdojob.com/anuncios/inseguro",
    "https://www.topsdojob.com/anuncios/outro-host",
    "https://topsdojob.com/anuncios?busca=privada",
    "https://topsdojob.com/anuncios/perfil#fragmento",
    "https://topsdojob.com/admin/usuarios",
    "https://topsdojob.com/api/public/anuncios",
  ]),
  [
    "https://topsdojob.com/anuncios/perfil-publico",
    "https://topsdojob.com/acompanhantes/go/goiania",
  ],
)

const disabled = loadIndexNowModule({ INDEXNOW_KEY: "" })
const disabledResult = await disabled.enviarUrlsParaIndexNow(
  event("PUBLICACAO", "disabled", ["https://topsdojob.com/anuncios/perfil-publico"]),
)
assert.equal(disabledResult.externalRequest, false)
assert.equal(disabledResult.reason, "DISABLED")

const preprod = loadIndexNowModule({ INDEXNOW_SITE_URL: "https://v3.esle.cloud" })
assert.equal(preprod.getIndexNowConfig().enabled, false)

const indexNowClient = loadIndexNowClientModule()
const publicationEvent = indexNowClient.montarEventoIndexNowAnuncio({
  eventType: "PUBLICACAO",
  current: { slug: "perfil-novo", estadoUf: "GO", cidadeNome: "Goiânia", bairroNome: "Centro" },
  changeFingerprint: "published-v1",
})
assert.deepEqual(publicationEvent.urls, [
  "https://topsdojob.com/anuncios",
  "https://topsdojob.com/acompanhantes",
  "https://topsdojob.com/anuncios/perfil-novo",
  "https://topsdojob.com/acompanhantes/go",
  "https://topsdojob.com/acompanhantes/go/goiania",
  "https://topsdojob.com/acompanhantes/go/goiania/centro",
])
const updateEvent = indexNowClient.montarEventoIndexNowAnuncio({
  eventType: "ATUALIZACAO",
  previous: { slug: "perfil-antigo", estadoUf: "SP", cidadeNome: "São Paulo", bairroNome: "Centro" },
  current: { slug: "perfil-novo", estadoUf: "RJ", cidadeNome: "Rio de Janeiro", bairroNome: "Copacabana" },
  changeFingerprint: "updated-v2",
})
for (const expectedUrl of [
  "https://topsdojob.com/anuncios/perfil-antigo",
  "https://topsdojob.com/anuncios/perfil-novo",
  "https://topsdojob.com/acompanhantes/sp/sao-paulo/centro",
  "https://topsdojob.com/acompanhantes/rj/rio-de-janeiro/copacabana",
]) assert.ok(updateEvent.urls.includes(expectedUrl))
assert.equal(indexNowClient.anuncioEstaPublicamenteIndexavel("PUBLICADO"), true)
for (const status of ["PENDENTE", "PAUSADO", "REJEITADO", "REMOVIDO", "BLOQUEADO"]) {
  assert.equal(indexNowClient.anuncioEstaPublicamenteIndexavel(status), false)
}
const clientDispatchResult = indexNowClient.enviarIndexNowNoCliente(updateEvent)
assert.equal(clientDispatchResult, undefined)
assert.deepEqual(indexNowClient.calls, [updateEvent])

const immediateAction = loadCatalogActionModule(async (actionEvent) => ({
  eventType: actionEvent.eventType,
  status: 200,
  ok: true,
  attempts: 1,
  urlCount: actionEvent.urls.length,
  deduplicatedCount: 0,
}))
const immediateStartedAt = performance.now()
await immediateAction.revalidarCacheCatalogoPublico(updateEvent)
const immediateResponseMs = performance.now() - immediateStartedAt
assert.deepEqual(immediateAction.revalidatedTags, ["public-catalog"])
assert.equal(immediateAction.afterCallbacks.length, 1)
await immediateAction.afterCallbacks[0]()

let slowNotificationStarted = false
const slowAction = loadCatalogActionModule(async () => {
  slowNotificationStarted = true
  await new Promise((resolve) => setTimeout(resolve, 4_000))
  throw new Error("controlled timeout")
})
const slowStartedAt = performance.now()
await slowAction.revalidarCacheCatalogoPublico(updateEvent)
const slowResponseMs = performance.now() - slowStartedAt
assert.equal(slowNotificationStarted, false)
assert.ok(slowResponseMs < 1_000, `business response waited ${slowResponseMs.toFixed(1)}ms`)
const slowBackgroundStartedAt = performance.now()
await slowAction.afterCallbacks[0]()
const slowBackgroundMs = performance.now() - slowBackgroundStartedAt
assert.equal(slowNotificationStarted, true)
assert.ok(slowBackgroundMs >= 3_900, `controlled timeout ended in ${slowBackgroundMs.toFixed(1)}ms`)

resetIndexNowStateForTests()
let backgroundRetryCount = 0
const retryAction = loadCatalogActionModule((actionEvent) => enviarUrlsParaIndexNow(actionEvent, {
  fetchImpl: async () => {
    backgroundRetryCount += 1
    return response(500)
  },
  sleep: async () => {},
  now: () => 50_000,
}))
const retryStartedAt = performance.now()
await retryAction.revalidarCacheCatalogoPublico(updateEvent)
const retryResponseMs = performance.now() - retryStartedAt
assert.equal(backgroundRetryCount, 0)
assert.ok(retryResponseMs < 1_000, `retry response waited ${retryResponseMs.toFixed(1)}ms`)
await retryAction.afterCallbacks[0]()
assert.equal(backgroundRetryCount, 3)

resetIndexNowStateForTests()
const bodies = []
let fetchCount = 0
const successFetch = async (_url, init) => {
  fetchCount += 1
  bodies.push(JSON.parse(init.body))
  return response(200)
}
const publicUrl = "https://topsdojob.com/anuncios/perfil-publico"
const first = await enviarUrlsParaIndexNow(
  event("PUBLICACAO", "perfil:publicado:v1", [publicUrl]),
  { fetchImpl: successFetch, sleep: async () => {}, now: () => 1_000 },
)
assert.equal(first.ok, true)
assert.equal(first.attempts, 1)
assert.equal(fetchCount, 1)
assert.equal(bodies[0].host, "topsdojob.com")
assert.equal(bodies[0].keyLocation, "https://topsdojob.com/INDEXNOW-TEST-KEY-2026.txt")
assert.deepEqual(bodies[0].urlList, [publicUrl])

const duplicate = await enviarUrlsParaIndexNow(
  event("PUBLICACAO", "perfil:publicado:v1", [publicUrl]),
  { fetchImpl: successFetch, sleep: async () => {}, now: () => 2_000 },
)
assert.equal(duplicate.externalRequest, false)
assert.equal(duplicate.deduplicatedCount, 1)
assert.equal(fetchCount, 1)

const changed = await enviarUrlsParaIndexNow(
  event("ATUALIZACAO", "perfil:publicado:v2", [publicUrl]),
  { fetchImpl: successFetch, sleep: async () => {}, now: () => 2_100 },
)
assert.equal(changed.externalRequest, true)
const removed = await enviarUrlsParaIndexNow(
  event("RETIRADA", "perfil:pausado", [publicUrl]),
  { fetchImpl: successFetch, sleep: async () => {}, now: () => 2_200 },
)
assert.equal(removed.externalRequest, true)
const republished = await enviarUrlsParaIndexNow(
  event("PUBLICACAO", "perfil:publicado:v1", [publicUrl]),
  { fetchImpl: successFetch, sleep: async () => {}, now: () => 2_300 },
)
assert.equal(republished.externalRequest, true)
assert.equal(fetchCount, 4)

resetIndexNowStateForTests()
let concurrentFetchCount = 0
const concurrentFetch = async () => {
  concurrentFetchCount += 1
  return response(202)
}
const concurrentEvent = event("ATUALIZACAO", "cidade:v2", ["https://topsdojob.com/acompanhantes/go"])
const [concurrentFirst, concurrentSecond] = await Promise.all([
  enviarUrlsParaIndexNow(concurrentEvent, {
    fetchImpl: concurrentFetch, sleep: async () => {}, now: () => 10_000,
  }),
  enviarUrlsParaIndexNow(concurrentEvent, {
    fetchImpl: concurrentFetch, sleep: async () => {}, now: () => 10_001,
  }),
])
assert.equal(concurrentFirst.status, 202)
assert.equal(concurrentSecond.externalRequest, false)
assert.equal(concurrentFetchCount, 1)

resetIndexNowStateForTests()
let retryCount = 0
const retried = await enviarUrlsParaIndexNow(
  event("ATUALIZACAO", "city:retry", ["https://topsdojob.com/acompanhantes/sp/sao-paulo"]),
  {
    fetchImpl: async () => response(++retryCount === 1 ? 429 : 200),
    sleep: async () => {}, now: () => 20_000,
  },
)
assert.equal(retried.ok, true)
assert.equal(retried.attempts, 2)

for (const status of [400, 403, 422]) {
  resetIndexNowStateForTests()
  let attempts = 0
  const rejected = await enviarUrlsParaIndexNow(
    event("RETIRADA", `status:${status}`, [`https://topsdojob.com/anuncios/rejeitado-${status}`]),
    {
      fetchImpl: async () => { attempts += 1; return response(status) },
      sleep: async () => {}, now: () => 30_000 + status,
    },
  )
  assert.equal(rejected.ok, false)
  assert.equal(rejected.status, status)
  assert.equal(attempts, 1)
}

for (const failure of ["http-500", "network", "timeout"]) {
  resetIndexNowStateForTests()
  let attempts = 0
  const failed = await enviarUrlsParaIndexNow(
    event("ATUALIZACAO", failure, [`https://topsdojob.com/anuncios/${failure}`]),
    {
      fetchImpl: async (_url, init) => {
        attempts += 1
        if (failure === "http-500") return response(500)
        if (failure === "network") throw new Error("network")
        return await new Promise((_, reject) => {
          init.signal.addEventListener("abort", () => reject(new Error("aborted")), { once: true })
        })
      },
      sleep: async () => {}, now: () => 40_000, timeoutMs: 1,
    },
  )
  assert.equal(failed.ok, false)
  assert.equal(failed.attempts, 3)
  assert.equal(attempts, 3)
}

const serverActionSource = source("src/app/(painel-admin)/admin/anuncios/actions.ts")
const clientSource = source("src/lib/seo/indexnow-client.ts")
const productionWorkflowSource = source("../.github/workflows/deploy-production.yml")
const productionComposeSource = source("../deploy/production/docker-compose.yml")
const preprodComposeSource = source("../deploy/preprod/docker-compose.yml")
const triggerSources = [
  "src/features/moderation-v2/components/moderacao-v2-detail.tsx",
  "src/features/admin-anuncios/admin-anuncio-moderacao.tsx",
  "src/features/admin-anuncios/admin-anuncio-edit-form.tsx",
  "src/features/admin-anuncios/admin-anuncios-list.tsx",
  "src/features/admin-usuarios/admin-usuario-detail.tsx",
  "src/features/admin-usuarios/admin-usuario-delete-dialog.tsx",
  "src/components/anuncios/meu-anuncio-acoes-ciclo-vida.tsx",
  "src/features/anuncio-wizard/components/wizard-step-fotos.tsx",
].map(source).join("\n")

for (const routeParts of [
  ["src", "app", "api", "indexnow", "route.ts"],
  ["src", "app", "api", "indexnow", "key", "route.ts"],
]) {
  assert.equal(existsSync(new URL(`../${routeParts.join("/")}`, import.meta.url)), false)
}
const publicKeyFiles = readdirSync(new URL("../public", import.meta.url))
  .filter((name) => /^[a-z0-9-]{8,128}\.txt$/i.test(name))
  .filter((name) => readFileSync(new URL(`../public/${name}`, import.meta.url), "utf8").trim() === name.slice(0, -4))
assert.ok(publicKeyFiles.length >= 1, "there must be a self-validating public IndexNow key file")
assert.match(serverActionSource, /enviarUrlsParaIndexNow\(event\)/)
assert.match(serverActionSource, /after\(async \(\) =>/)
assert.doesNotMatch(serverActionSource, /config\.key|keyLocation/)
assert.match(clientSource, /previous\.map\(contextFingerprint\)/)
assert.match(clientSource, /current\.map\(contextFingerprint\)/)
assert.doesNotMatch(triggerSources, /await\s+enviarIndexNowNoCliente/)
for (const eventType of ["PUBLICACAO", "ATUALIZACAO", "RETIRADA"]) {
  assert.match(triggerSources, new RegExp(`eventType: ['\"]${eventType}['\"]`))
}
assert.match(productionComposeSource, /INDEXNOW_SITE_URL:\s*https:\/\/topsdojob\.com/)
assert.match(productionComposeSource, /INDEXNOW_KEY:\s*\$\{INDEXNOW_KEY:\?INDEXNOW_KEY obrigatoria/)
assert.match(productionComposeSource, /\/app\/public \.\/public/)
assert.match(productionWorkflowSource, /INDEXNOW_KEY:\s*\$\{\{ secrets\.INDEXNOW_KEY \}\}/)
assert.match(productionWorkflowSource, /Synchronize IndexNow key in production runtime/)
assert.match(productionWorkflowSource, /cat <<'REMOTE_HEAD'/)
assert.match(productionWorkflowSource, /printf '%s\\n' "\$\{INDEXNOW_KEY\}"/)
assert.match(productionWorkflowSource, /\} \| ssh/)
assert.match(productionWorkflowSource, /--network none/)
assert.match(productionWorkflowSource, /--pull never/)
assert.match(productionWorkflowSource, /test -n "\$\{key\}"/)
assert.doesNotMatch(productionWorkflowSource, /''\|\*\[!A-Za-z0-9-\]\*/)
assert.match(productionWorkflowSource, /grep -q '\^INDEXNOW_KEY='/)
assert.doesNotMatch(productionWorkflowSource, /NEXT_PUBLIC_INDEXNOW/)
assert.doesNotMatch(preprodComposeSource, /INDEXNOW_KEY|INDEXNOW_SITE_URL/)

console.log(
  `IndexNow non-blocking: immediate=${immediateResponseMs.toFixed(1)}ms; ` +
    `slow-response=${slowResponseMs.toFixed(1)}ms; slow-background=${slowBackgroundMs.toFixed(1)}ms; ` +
    `retry-response=${retryResponseMs.toFixed(1)}ms; retries=${backgroundRetryCount}.`,
)
console.log("IndexNow V3: testes direcionados aprovados.")
