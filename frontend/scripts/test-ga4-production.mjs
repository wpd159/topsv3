import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(scriptDir, '..')
const repoRoot = path.resolve(frontendRoot, '..')

function readRepoFile(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), 'utf8')
}

function collectSourceFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = path.join(directory, entry.name)
    if (entry.isDirectory()) return collectSourceFiles(fullPath)
    return /\.(?:ts|tsx)$/.test(entry.name) ? [fullPath] : []
  })
}

const layout = readRepoFile('frontend/src/app/layout.tsx')
const analytics = readRepoFile(
  'frontend/src/components/analytics/consent-aware-analytics.tsx'
)
const helper = readRepoFile('frontend/src/lib/analytics/ga4.ts')
const integration = `${helper}\n${analytics}`
const nextConfig = readRepoFile('frontend/next.config.ts')
const productionWorkflow = readRepoFile('.github/workflows/deploy-production.yml')
const ciWorkflow = readRepoFile('.github/workflows/ci.yml')
const productionCompose = readRepoFile('deploy/production/docker-compose.yml')
const runtimeSources = collectSourceFiles(path.join(frontendRoot, 'src'))
  .map((file) => readFileSync(file, 'utf8'))
  .join('\n')

assert.equal(
  (layout.match(/<ConsentAwareAnalytics\s*\/>/g) ?? []).length,
  1,
  'O layout deve montar a integracao consent-aware uma unica vez.'
)
assert.equal(
  (runtimeSources.match(/googletagmanager\.com\/gtag\/js/g) ?? []).length,
  1,
  'Deve existir um unico loader gtag no frontend.'
)
assert.equal(
  new Set(runtimeSources.match(/G-[A-Z0-9]+/g) ?? []).size,
  1,
  'Deve existir um unico Measurement ID no frontend.'
)
assert.equal(
  (runtimeSources.match(/GTM-[A-Z0-9]+/g) ?? []).length,
  0,
  'Google Tag Manager nao deve ser introduzido.'
)
assert.match(integration, /NEXT_PUBLIC_ANALYTICS_ENABLED/)
assert.match(integration, /cookie_consent/)
assert.match(integration, /tops:cookie-consent-updated/)
assert.match(analytics, /gtag\('consent', 'default'/)
assert.match(analytics, /gtag\('config'/)
assert.match(analytics, /document\.createElement\('script'\)/)
assert.doesNotMatch(analytics, /@vercel\/analytics/)
assert.doesNotMatch(helper, /setInterval|filaEventos|timerFlush/)

assert.match(
  productionWorkflow,
  /NEXT_PUBLIC_ANALYTICS_ENABLED:\s+"true"/
)
assert.match(productionWorkflow, /node scripts\/test-ga4-production\.mjs/)
assert.doesNotMatch(
  productionWorkflow,
  /NEXT_PUBLIC_ANALYTICS_ENABLED:\s+"false"/
)
assert.match(
  ciWorkflow,
  /NEXT_PUBLIC_ANALYTICS_ENABLED:\s+"false"/
)
assert.equal(
  (productionCompose.match(
    /NEXT_PUBLIC_ANALYTICS_ENABLED:\s+"true"/g
  ) ?? []).length,
  2,
  'Build e runtime do Compose devem fixar GA4 habilitado em producao.'
)
assert.match(productionCompose, /ENV NEXT_PUBLIC_ANALYTICS_ENABLED=true/)
assert.doesNotMatch(
  productionCompose,
  /NEXT_PUBLIC_ANALYTICS_ENABLED[^\r\n]*false/
)
assert.doesNotMatch(
  productionCompose,
  /NEXT_PUBLIC_ANALYTICS_ENABLED:\s+\$\{/,
  'A configuracao canonica de producao nao pode ser desabilitada por env legado.'
)

for (const requiredOrigin of [
  'https://www.googletagmanager.com',
  'https://www.google-analytics.com',
  'https://region1.google-analytics.com',
]) {
  assert.ok(nextConfig.includes(requiredOrigin), `CSP ausente para ${requiredOrigin}`)
}

// Execute the real TypeScript modules, following the existing deterministic
// hooks harness. Only React/Next and browser boundaries are simulated. No SDK,
// browser, build, network request or production property is used by this test.
const compiled = new Map()
function runtimeModule(name, source, imports, platform) {
  if (!compiled.has(name)) {
    const result = ts.transpileModule(source, {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX },
      fileName: name,
      reportDiagnostics: true,
    })
    assert.deepEqual((result.diagnostics ?? []).filter((item) => item.category === ts.DiagnosticCategory.Error), [])
    compiled.set(name, result.outputText)
  }
  const module = { exports: {} }
  const require = (specifier) => {
    assert.ok(Object.hasOwn(imports, specifier), `Import nao simulado: ${name}: ${specifier}`)
    return imports[specifier]
  }
  new Function('require', 'module', 'exports', ...Object.keys(platform), compiled.get(name))(
    require, module, module.exports, ...Object.values(platform),
  )
  return module.exports
}

function componentHooks() {
  const slots = []
  const pending = []
  let index = 0, dirty = false, component
  const changed = (before, after) => !before || !after || before.length !== after.length || after.some((item, i) => !Object.is(item, before[i]))
  const react = {
    useRef(initial) { return slots[index++] ??= { current: initial } },
    useState(initial) {
      const slot = index++
      slots[slot] ??= { value: typeof initial === 'function' ? initial() : initial }
      return [slots[slot].value, (next) => {
        const value = typeof next === 'function' ? next(slots[slot].value) : next
        dirty ||= !Object.is(value, slots[slot].value)
        slots[slot].value = value
      }]
    },
    useMemo(callback, dependencies) {
      const slot = index++
      if (!slots[slot] || changed(slots[slot].dependencies, dependencies)) slots[slot] = { value: callback(), dependencies }
      return slots[slot].value
    },
    useCallback(callback, dependencies) { return react.useMemo(() => callback, dependencies) },
    useEffect(callback, dependencies) {
      const slot = index++
      if (!slots[slot] || changed(slots[slot].dependencies, dependencies)) {
        const old = slots[slot]
        slots[slot] = { dependencies, callback, cleanup: old?.cleanup }
        pending.push(() => { old?.cleanup?.(); slots[slot].cleanup = callback() })
      }
    },
  }
  function render(commit = true) {
    index = 0
    dirty = false
    assert.equal(component(), null)
    if (commit) pending.splice(0).forEach((effect) => effect())
  }
  function settle() {
    for (let turn = 0; dirty && turn < 20; turn += 1) render()
    assert.equal(dirty, false, 'Os efeitos devem estabilizar.')
  }
  return {
    react,
    mount(next) { component = next; render(); settle() },
    render() { render(); settle() },
    // React Strict Mode replays setup/cleanup while keeping useRef identities.
    replayEffects() {
      const effects = slots.filter((slot) => slot?.callback)
      effects.forEach((slot) => slot.cleanup?.())
      effects.forEach((slot) => { slot.cleanup = slot.callback() })
      settle()
    },
    settle,
    unmount() { slots.forEach((slot) => slot?.cleanup?.()) },
  }
}

const approvedCookie = `cookie_consent=${encodeURIComponent(JSON.stringify({ analytics: true }))}`
const deniedCookie = `cookie_consent=${encodeURIComponent(JSON.stringify({ analytics: false }))}`
// Placeholder aprovado pelo scanner; continua sendo canário proibido no payload.
const sensitiveQuery = new URLSearchParams({
  email: 'pessoa@example.test', token: 'CHANGE_ME', utm_source: 'segredo',
}).toString()
let scenarios = 0
function scenario({ url = 'https://topsdojob.com/', cookie = approvedCookie, env = {}, referrer = '', browser = true } = {}) {
  scenarios += 1
  const hooks = componentHooks()
  const listeners = new Map()
  const scripts = []
  const commands = []
  const hits = []
  const timerCalls = []
  const networkCalls = []
  let config = {}, sdkLoaded = false
  const network = (...args) => { networkCalls.push(args); throw new Error('Rede proibida no teste GA4') }
  const window = {
    location: new URL(url),
    navigator: { userAgent: 'synthetic-desktop', sendBeacon: network },
    fetch: network,
    addEventListener(type, callback) {
      if (!listeners.has(type)) listeners.set(type, new Set())
      listeners.get(type).add(callback)
    },
    removeEventListener(type, callback) { listeners.get(type)?.delete(callback) },
    setInterval(...args) { timerCalls.push(args); return timerCalls.length },
    clearInterval() {},
    setTimeout(...args) { timerCalls.push(args); return timerCalls.length },
    clearTimeout() {},
  }
  const document = {
    cookie,
    title: 'Titulo privado sintetico pessoa@example.test telefone-5511999999999',
    referrer,
    getElementById: (id) => scripts.find((script) => script.id === id) ?? null,
    createElement(tagName) { assert.equal(tagName, 'script'); return { tagName, setAttribute(name, value) { this[name] = value } } },
    head: { appendChild(script) { scripts.push(script); return script } },
  }
  const platform = {
    window: browser ? window : undefined,
    document: browser ? document : undefined,
    process: { env: { NODE_ENV: 'production', NEXT_PUBLIC_ANALYTICS_ENABLED: 'true', ...env } },
    fetch: network,
    setInterval: window.setInterval,
    clearInterval: window.clearInterval,
    setTimeout: window.setTimeout,
    clearTimeout: window.clearTimeout,
  }
  const ga4 = runtimeModule('ga4.ts', helper, {}, platform)
  const { ConsentAwareAnalytics } = runtimeModule('consent-aware-analytics.tsx', analytics, {
    react: hooks.react,
    'next/navigation': {
      usePathname: () => window.location.pathname,
      useSearchParams: () => new URLSearchParams(window.location.search),
    },
    '@/lib/analytics/ga4': ga4,
  }, platform)
  const disabled = () => window[`ga-disable-${ga4.GA_MEASUREMENT_ID}`] === true
  function send(name, params = {}) {
    if (disabled()) return
    hits.push({ name, params: { ...config, ...params } })
  }
  function transport(raw) {
    const args = Array.from(raw)
    commands.push(args)
    if (args[0] === 'config') {
      assert.equal(args[1], ga4.GA_MEASUREMENT_ID)
      config = { ...config, ...args[2] }
      if (args[2]?.send_page_view !== false) send('page_view', args[2])
    }
    if (args[0] === 'event') send(args[1], args[2])
  }
  const test = {
    ga4, window, document, scripts, commands, hits, hooks,
    disabled,
    mount() { hooks.mount(ConsentAwareAnalytics) },
    navigate(next, render = true) { window.location = new URL(next, window.location); if (render) hooks.render() },
    dispatch(type) { [...(listeners.get(type) ?? [])].forEach((callback) => callback({ type })); hooks.settle() },
    consent(next, type = 'tops:cookie-consent-updated') { document.cookie = next; test.dispatch(type) },
    loadSdk() {
      assert.equal(sdkLoaded, false, 'O SDK falso so deve ser carregado uma vez por cenario.')
      sdkLoaded = true
      const queue = window.dataLayer ??= []
      const initial = queue.splice(0)
      queue.push = (...items) => { items.forEach(transport); return 0 }
      // The SDK consumes the bootstrap queue; no script is actually fetched.
      window.gtag = (...args) => window.dataLayer.push(args)
      initial.forEach(transport)
      scripts.forEach((script) => script.onload?.())
    },
    events(name = 'page_view') { return hits.filter((hit) => hit.name === name) },
    finish() {
      hooks.unmount()
      assert.equal([...listeners.values()].reduce((total, callbacks) => total + callbacks.size, 0), 0, 'Listeners devem ser removidos no unmount.')
      assert.equal(timerCalls.length, 0, 'Nao deve existir polling nem fila auxiliar de eventos.')
      assert.equal(networkCalls.length, 0, 'O teste e o helper nao podem usar transporte externo.')
    },
  }
  return test
}

function assertPrivateDataAbsent(value) {
  assert.doesNotMatch(JSON.stringify(value), /pessoa@|example\.test|5511999999999|segredo|CHANGE_ME|token-|email=|utm_|gclid|#|Titulo privado|cliente-sintetico|mensagem-sintetica/i)
}

// Initial page view, rerender, focus and Strict Mode effect replay are distinct
// from real SPA navigation and browser back navigation.
{
  const test = scenario()
  test.mount()
  assert.equal(test.scripts.length, 1)
  assert.equal(test.scripts[0].src, `https://www.googletagmanager.com/gtag/js?id=${test.ga4.GA_MEASUREMENT_ID}`)
  assert.equal(test.scripts[0].referrerPolicy, 'no-referrer')
  test.loadSdk()
  assert.equal(test.events().length, 1, 'A carga inicial elegivel envia um page_view manual.')
  assert.deepEqual(test.commands.find((args) => args[0] === 'consent' && args[1] === 'default')?.[2], {
    analytics_storage: 'denied', ad_storage: 'denied', ad_user_data: 'denied', ad_personalization: 'denied',
  })
  test.hooks.render()
  test.hooks.replayEffects()
  test.dispatch('focus')
  test.dispatch('tops:cookie-consent-updated')
  assert.equal(test.events().length, 1, 'Rerender e Strict Mode nao devem duplicar a pagina.')
  test.navigate('/anuncios')
  assert.equal(test.events().length, 2)
  test.navigate('/blog')
  assert.equal(test.events().length, 3)
  test.navigate('/anuncios')
  assert.equal(test.events().length, 4, 'Voltar a uma pagina constitui nova navegacao.')
  const configs = test.commands.filter((args) => args[0] === 'config')
  assert.ok(configs.length > 0)
  assert.ok(configs.every((args) => args[2].send_page_view === false), 'Config nunca deve gerar page_view automatico.')
  assert.ok(configs.every((args) => args[2].allow_google_signals === false && args[2].allow_ad_personalization_signals === false))
  assert.equal(test.scripts.length, 1, 'Navegacao e ressincronizacao nao recarregam o SDK.')
  assertPrivateDataAbsent(test.commands)
  test.finish()
}

// Query strings, dynamic titles and slugs cannot enter config or event payloads.
for (const [route, template] of [
  ['/anuncios/cliente-sintetico', '/anuncios/[slug]'],
  ['/blog/cliente-sintetico', '/blog/[slug]'],
  ['/blog/categoria/cliente-sintetico', '/blog/categoria/[slug]'],
  ['/blog/cidade/cliente-sintetico/mensagem-sintetica', '/blog/cidade/[tema]/[cidade]'],
  ['/acompanhantes/sp/cliente-sintetico/mensagem-sintetica', '/acompanhantes/[uf]/[cidade]/[bairro]'],
]) {
  const test = scenario({ url: `https://topsdojob.com${route}?${sensitiveQuery}#mensagem-sintetica` })
  test.mount()
  test.loadSdk()
  assert.equal(test.events().length, 1, `Rota publica sem identificadores: ${route}`)
  assertPrivateDataAbsent(test.commands)
  const params = test.events()[0].params
  assert.equal(params.page_title, `Tops do Job | ${template}`)
  assert.equal(params.page_path, template)
  assert.equal(params.page_location, `https://topsdojob.com${template}`)
  assert.equal(params.page_referrer ?? '', '')
  test.finish()
}
{
  const test = scenario({ url: 'https://topsdojob.com/anuncios/cliente-sintetico' })
  test.mount()
  test.loadSdk()
  test.navigate('/anuncios/mensagem-sintetica')
  assert.equal(test.events().length, 2, 'Slugs distintos contam navegacoes distintas mesmo usando o mesmo template seguro.')
  assert.equal(test.events()[0].params.page_path, test.events()[1].params.page_path)
  test.navigate('/anuncios/cliente-sintetico')
  assert.equal(test.events().length, 3)
  assertPrivateDataAbsent(test.commands)
  test.finish()
}
{
  const test = scenario({ url: `https://topsdojob.com/anuncios?page=2&${sensitiveQuery}` })
  test.mount()
  test.loadSdk()
  const params = test.events()[0].params
  assert.equal(new URL(params.page_location).search, '?page=2')
  assertPrivateDataAbsent(test.commands)
  test.navigate('/anuncios?page=3&email=pessoa@example.test')
  assert.equal(test.events().length, 2, 'Paginacao publica deve gerar nova visualizacao.')
  test.finish()
}
for (const page of ['-1', '1.5', 'pessoa@example.test', '2%26token%3Dsegredo']) {
  const test = scenario({ url: `https://topsdojob.com/anuncios?page=${page}` })
  test.mount()
  test.loadSdk()
  assert.equal(new URL(test.events()[0].params.page_location).search, '', 'Somente paginacao numerica valida pode sair.')
  test.finish()
}
for (const [referrer, expected] of [
  ['https://search.example.org/path?email=pessoa@example.test#segredo', 'https://search.example.org'],
  [`https://topsdojob.com/anuncios?${sensitiveQuery}`, ''],
  [`https://topsdojob.com/admin/usuarios/cliente-sintetico?${sensitiveQuery}`, ''],
  ['https://topsdojob.com/minha-conta?email=pessoa@example.test', ''],
  ['invalid-referrer-segredo', ''],
]) {
  const test = scenario({ referrer })
  test.mount()
  test.loadSdk()
  assert.equal(test.events()[0].params.page_referrer ?? '', expected)
  assertPrivateDataAbsent(test.commands)
  test.finish()
}

for (const route of [
  '/politicas/verificacao-etaria', '/politicas/termos-conteudo-restrito',
  '/politicas/privacidade-conteudo-restrito', '/politicas/aviso-legal-conteudo-restrito',
]) {
  const test = scenario({ url: `https://topsdojob.com${route}` })
  test.mount()
  test.loadSdk()
  assert.equal(test.events().length, 1)
  assert.equal(test.events()[0].params.page_path, route)
  test.finish()
}
{
  const test = scenario()
  test.mount()
  test.loadSdk()
  test.navigate('/anuncios?page=2', false)
  test.dispatch('focus')
  test.hooks.render()
  assert.equal(test.events().length, 2, 'Foco durante transicao nao pode usar a chave de uma URL antiga.')
  assert.equal(test.events()[1].params.page_path, '/anuncios?page=2')
  test.finish()
}

// All prerequisites must be explicit. Synthetic hosts and nonpublic routes fail
// closed even when a transport already exists and a caller invokes the helper.
const blockedScenarios = [
  ...['/admin', '/admin/login', '/admin/usuarios/cliente-sintetico', '/painel', '/minha-conta', '/favoritos', '/chat', '/meus-anuncios', '/meus-tickets', '/creditos', '/anunciar/wizard', '/checkout/creditos/plano', '/registrar', '/login', '/api/test', '/health/readiness', '/__fixtures/anuncios', '/rota-desconhecida'].map((route) => ({ url: `https://topsdojob.com${route}` })),
  ...['http://topsdojob.com/', 'https://www.topsdojob.com/', 'http://localhost:3000/', 'http://127.0.0.1:3000/', 'https://fixture.example.test/', 'https://topsdojob.com.example.test/'].map((url) => ({ url })),
  ...['', deniedCookie, 'cookie_consent=not-json', 'cookie_consent=%E0%A4%A', 'cookie_consent=null', 'cookie_consent=%7B%7D', 'cookie_consent=%7B%22analytics%22%3A%22true%22%7D'].map((cookie) => ({ cookie })),
  ...[undefined, '', 'false', 'TRUE'].map((value) => ({ env: { NEXT_PUBLIC_ANALYTICS_ENABLED: value } })),
  ...['development', 'test', undefined].map((value) => ({ env: { NODE_ENV: value } })),
]
for (const options of blockedScenarios) {
  const test = scenario(options)
  const direct = []
  test.window.gtag = (...args) => direct.push(args)
  test.mount()
  assert.equal(test.ga4.analyticsPermitido(), false, JSON.stringify(options))
  test.ga4.registrarEventoGA4('page_view', { page_title: 'segredo' })
  test.ga4.registrarEventoGA4('click_whatsapp', { telefone: '5511999999999' })
  assert.equal(test.scripts.length, 0, 'Contexto inelegivel nao carrega SDK.')
  assert.equal(direct.filter((args) => args[0] === 'event' || args[0] === 'config').length, 0, JSON.stringify(options))
  test.finish()
}

// Private-route transitions are blocked synchronously, before React can run an
// effect. A simulated automatic SDK event checks the same ga-disable accessor.
{
  const test = scenario()
  test.mount()
  test.loadSdk()
  test.navigate('/admin/usuarios/cliente-sintetico?email=pessoa@example.test', false)
  assert.equal(test.disabled(), true, 'A URL privada deve desabilitar o SDK antes do efeito.')
  test.window.gtag('event', 'page_view', { page_location: test.window.location.href })
  test.ga4.registrarEventoGA4('click_whatsapp')
  assert.equal(test.events().length, 1)
  assert.equal(test.events('click_whatsapp').length, 0)
  test.hooks.render()
  test.navigate('/')
  assert.equal(test.events().length, 2, 'Retorno de rota privada deve contar uma nova pagina publica.')
  test.finish()
}

// Revocation applies even before its notification. Regrant starts fresh; neither
// skipped direct calls nor an old bootstrap queue can be replayed later.
{
  const test = scenario()
  test.mount()
  test.loadSdk()
  test.document.cookie = deniedCookie
  assert.equal(test.disabled(), true)
  test.window.gtag('event', 'page_view', { page_title: 'segredo' })
  test.ga4.registrarEventoGA4('click_whatsapp')
  assert.equal(test.events().length, 1)
  test.dispatch('tops:cookie-consent-updated')
  test.consent(approvedCookie)
  assert.equal(test.events().length, 2)
  assert.equal(test.events('click_whatsapp').length, 0)
  test.ga4.registrarEventoGA4('click_whatsapp')
  assert.equal(test.events('click_whatsapp').length, 1)
  assert.equal(test.scripts.length, 1)
  test.finish()
}
{
  const test = scenario()
  test.mount()
  test.ga4.registrarEventoGA4('click_whatsapp')
  test.consent(deniedCookie)
  test.ga4.registrarEventoGA4('click_whatsapp')
  test.consent(approvedCookie)
  test.loadSdk()
  assert.equal(test.events('click_whatsapp').length, 0, 'Revogacao deve descartar eventos enfileirados antes da carga do SDK.')
  assert.equal(test.events().length, 1, 'Somente a visualizacao da nova concessao pode permanecer na fila.')
  test.finish()
}
for (const notification of ['focus', 'tops:cookie-consent-updated']) {
  const test = scenario({ cookie: deniedCookie })
  test.mount()
  test.consent(approvedCookie, notification)
  test.loadSdk()
  assert.equal(test.events().length, 1)
  test.consent(deniedCookie, notification)
  assert.equal(test.disabled(), true)
  test.ga4.registrarEventoGA4('click_whatsapp')
  assert.equal(test.events('click_whatsapp').length, 0)
  test.finish()
}

// Helper callers cannot override controlled page metadata or smuggle arbitrary
// names/parameters. A missing/throwing transport must not break application work.
{
  const test = scenario({ url: 'https://topsdojob.com/anuncios/cliente-sintetico' })
  test.mount()
  test.loadSdk()
  const untrusted = {
    page_location: `https://topsdojob.com/admin?${sensitiveQuery}`, page_path: '/admin/cliente-sintetico',
    page_title: 'Titulo privado', page_referrer: 'https://topsdojob.com/chat?email=pessoa@example.test',
    anuncio_id: 'cliente-sintetico', titulo: 'segredo', whatsapp: '5511999999999',
    telefone: '5511999999999', email: 'pessoa@example.test', mensagem: 'mensagem-sintetica',
    origem: 'segredo', dispositivo: 'segredo', arbitrary: 'segredo',
  }
  test.ga4.registrarEventoGA4('page_view', untrusted)
  test.ga4.registrarEventoGA4('click_whatsapp', untrusted)
  test.ga4.registrarEventoGA4('evento_privado', untrusted)
  assert.equal(test.events().length, 2)
  assert.equal(test.events('click_whatsapp').length, 1)
  assert.equal(test.events('evento_privado').length, 0)
  assert.equal(test.events('click_whatsapp')[0].params.event_category, 'engagement')
  assert.equal(test.events('click_whatsapp')[0].params.event_label, 'card_anuncio')
  assertPrivateDataAbsent(test.commands)
  assert.deepEqual(Object.keys(test.events('click_whatsapp')[0].params).filter((key) => key in untrusted && !key.startsWith('page_')), [], 'Parametros arbitrarios devem ser descartados.')
  test.window.gtag = () => { throw new Error('transport failure') }
  let applicationContinued = false
  assert.doesNotThrow(() => { test.ga4.registrarEventoGA4('click_whatsapp'); applicationContinued = true })
  assert.equal(applicationContinued, true)
  test.finish()
}
{
  const test = scenario()
  test.window.gtag = () => { throw new Error('SDK initialization failure') }
  assert.doesNotThrow(() => test.mount(), 'Falha na configuracao de analytics nao pode impedir a montagem da aplicacao.')
  test.window.gtag = undefined
  test.dispatch('focus')
  test.loadSdk()
  assert.equal(test.events().length, 1, 'Ressincronizacao deve permitir recuperacao depois da falha do SDK.')
  test.finish()
}
{
  const test = scenario()
  test.ga4.registrarEventoGA4('click_whatsapp', {}, { dedupeKey: 'synthetic' })
  test.mount()
  test.loadSdk()
  assert.equal(test.events('click_whatsapp').length, 0, 'Evento anterior ao transporte nao pode ser enfileirado pelo helper.')
  test.ga4.registrarEventoGA4('click_whatsapp', {}, { dedupeKey: 'synthetic' })
  test.ga4.registrarEventoGA4('click_whatsapp', {}, { dedupeKey: 'synthetic' })
  assert.equal(test.events('click_whatsapp').length, 1, 'Dedupe nao deve consumir eventos que nao foram enviados.')
  test.finish()
}
{
  const test = scenario()
  Object.defineProperty(test.document, 'cookie', { get() { throw new Error('cookie unavailable') } })
  assert.doesNotThrow(() => test.ga4.registrarEventoGA4('page_view'))
  assert.equal(test.ga4.analyticsPermitido(), false)
  test.finish()
}
{
  const test = scenario({ browser: false })
  assert.equal(test.ga4.analyticsPermitido(), false)
  assert.doesNotThrow(() => test.ga4.registrarEventoGA4('click_whatsapp'))
  test.finish()
}

console.log(`GA4 runtime: ${scenarios} cenarios aprovados; modulos reais, hooks e transporte simulados, nenhuma chamada externa.`)
console.log('GA4_PRODUCTION_TEST=PASS')
