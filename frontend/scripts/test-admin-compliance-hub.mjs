import assert from 'node:assert/strict'
import fs from 'node:fs'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const sourceRoot = path.join(frontendRoot, 'src')

function source(relativePath) {
  return fs.readFileSync(path.join(sourceRoot, relativePath), 'utf8')
}

function normalize(value) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
}

const page = source('app/(painel-admin)/admin/compliance/page.tsx')
const sidebar = source('app/(painel-admin)/admin/components/sidebar/sidebar-links.tsx')
const sidebarUtils = source('app/(painel-admin)/admin/components/sidebar/sidebar-utils.ts')
const normalizedPage = normalize(page)
const normalizedSidebar = normalize(sidebar)

for (const forbidden of [
  'Integracao pendente',
  'PENDING_BACKEND_CONTRACTS',
  'usePendingContractActions',
  'Auditoria administrativa',
  'Aceites juridicos',
  'Eventos criticos',
  'Configuracoes de compliance',
  'Score minimo',
  'Retencao em dias',
  'Salvar configuracoes',
]) {
  assert.ok(!normalizedPage.includes(forbidden), 'Placeholder de Compliance ainda presente: ' + forbidden)
}

for (const control of ['<Table', '<Input', '<Select', '<Textarea']) {
  assert.ok(!page.includes(control), 'Controle simulado ainda presente no hub: ' + control)
}

const areas = [
  ['Logs visitantes', '/admin/compliance#visitor-logs'],
  ['Risco por sessao', '/admin/compliance#visitor-risk'],
  ['Documentos visitantes', '/admin/compliance#visitor-document-fallback'],
]

for (const [label, href] of areas) {
  assert.ok(normalizedPage.includes(label), 'Área real ausente: ' + label)
  assert.ok(page.includes("href: '" + href + "'"), 'Destino canônico ausente: ' + href)
}

assert.equal((page.match(/href: '\/admin\/compliance#/g) || []).length, 3)
assert.ok(normalizedPage.includes('Monitoramento'))
assert.ok(normalizedPage.includes('Evidencias e governanca'))
assert.ok(page.includes('grid grid-cols-1 gap-3 md:grid-cols-2'))
assert.ok(page.includes('w-full sm:w-auto'))
assert.ok(page.includes("addEventListener('hashchange'"))
assert.ok(page.includes("addEventListener('popstate'"))
assert.ok(normalizedPage.includes('Voltar ao hub'))
assert.ok(!page.includes('fetch('), 'O hub não deve criar uma fonte de dados paralela.')

for (const component of ['VisitorAgeLogs', 'VisitorRisk', 'VisitorDocuments']) {
  assert.ok(page.includes('import { ' + component + ' }'), 'Componente real não importado: ' + component)
  assert.ok(page.includes('<' + component + ' />'), 'Componente real não renderizado: ' + component)
}

for (const removed of ['Auditoria administrativa', 'Aceites juridicos', 'Configuracoes de compliance']) {
  assert.ok(!normalizedSidebar.includes(removed), 'Atalho sem contrato ainda presente: ' + removed)
}

for (const retained of [
  "label: 'Compliance'",
  "href: '/admin/compliance'",
  "label: 'Logs visitantes'",
  "href: '/admin/compliance#visitor-logs'",
  "label: 'Documentos visitantes'",
  "href: '/admin/compliance#visitor-document-fallback'",
]) {
  assert.ok(sidebar.includes(retained), 'Atalho real ausente: ' + retained)
}

assert.ok(sidebarUtils.includes("'/admin/compliance'"), 'RBAC do frontend deve refletir o backend ADMIN-only.')

// Real Next application; only the local server's allowlisted API responses are
// synthetic. Run through the isolated F02 executor, never against a personal session.
const require = createRequire(import.meta.url)
const dev = false
assert.equal(process.platform, 'linux', 'F02 requires the isolated Linux executor.')
assert.equal(process.version, 'v22.13.1')
assert.equal(process.env.TOPS_F02_ISOLATED, '1', 'Use run-admin-compliance-hub-isolated.sh.')
assert.notEqual(process.getuid(), 0, 'The browser and observer must not run as root.')
assert.ok(Object.values(os.networkInterfaces()).flat().every((address) => address.internal), 'F02 refuses non-loopback network interfaces.')
assert.ok(!fs.readFileSync('/proc/net/route', 'utf8').split('\n').slice(1).some((line) => line.trim() && line.split(/\s+/)[1] === '00000000'), 'F02 refuses a default route.')
const build = JSON.parse(fs.readFileSync(path.join(frontendRoot, '.f02-build.json'), 'utf8'))
assert.equal(build.apiBase, '/api/public')
assert.equal(build.logo, '/logo-finallllll.webp')
assert.equal(build.buildId, fs.readFileSync(path.join(frontendRoot, '.next/BUILD_ID'), 'utf8').trim())
const { chromium } = require(process.env.TOPS_PLAYWRIGHT_MODULE || 'playwright')
const playwrightVersion = require((process.env.TOPS_PLAYWRIGHT_MODULE || 'playwright') + '/package.json').version
assert.equal(playwrightVersion, '1.62.1')
const evidence = fs.mkdtempSync(path.join(process.env.TOPS_UI_EVIDENCE_ROOT || os.tmpdir(), 'admin-compliance-hub-'))
const writeJson = (name, value) => fs.writeFileSync(path.join(evidence, name), JSON.stringify(value, null, 2), { flag: 'wx' })
const sha256 = (value) => createHash('sha256').update(value).digest('hex')
const inputPaths = [
  'src/app/(painel-admin)/admin/compliance/page.tsx',
  'src/app/(painel-admin)/admin/components/sidebar/sidebar-nav.tsx',
  'src/app/(painel-admin)/admin/compliance/visitor-age-logs.tsx',
  'src/app/(painel-admin)/admin/compliance/visitor-risk.tsx',
  'src/app/(painel-admin)/admin/compliance/visitor-documents.tsx',
  'scripts/test-admin-compliance-hub.mjs', 'package-lock.json',
]
const inputHashes = Object.fromEntries(inputPaths.map((name) => [name, sha256(fs.readFileSync(path.join(frontendRoot, name)))]))
writeJson('inputs.json', inputHashes)
for (const [name, hash] of Object.entries(build.inputHashes)) assert.equal(sha256(fs.readFileSync(path.join(frontendRoot, name))), hash, 'F02 build input differs: ' + name)
const results = [], requests = [], unexpected = [], browserErrors = [], expectedErrors = [], cleanupErrors = [], refusals = []
// Observe before a popup Page exists, without enabling CDP Fetch interception.
const transport = [], requestIds = new WeakMap(), pageIds = new WeakMap()
let nextRequestId = 0, nextPageId = 0, nextServerId = 0
let observation = { scenario: null, gesture: null }
const trace = (event, details = {}, scope = observation) => transport.push({ at: Date.now(), ...scope, event, ...details })
const pageId = (page) => {
  if (!pageIds.has(page)) pageIds.set(page, ++nextPageId)
  return pageIds.get(page)
}
const requestDetails = (request) => {
  if (!requestIds.has(request)) requestIds.set(request, { requestId: ++nextRequestId, scope: { ...observation } })
  const { requestId, scope } = requestIds.get(request)
  let page = null
  try { page = pageId(request.frame().page()) } catch { /* Initial navigation may have no Frame yet. */ }
  const url = new URL(request.url())
  return { ...scope, requestId, page, path: url.pathname, resource: request.resourceType(), method: request.method() }
}
const relevantRequest = (request) => request.resourceType() === 'document' || request.resourceType() === 'script' || new URL(request.url()).pathname.startsWith('/api/admin/')
const areaCases = [
  { hash: '#visitor-logs', title: 'Logs visitantes', empty: 'Nenhuma verificação registrada.', endpoint: '/api/admin/compliance/verificacoes-etarias' },
  { hash: '#visitor-risk', title: 'Risco por sessão', empty: 'Nenhum perfil de risco registrado.', endpoint: '/api/admin/compliance/risco' },
  { hash: '#visitor-document-fallback', title: 'Documentos visitantes', empty: 'Nenhum documento recebido.', endpoint: '/api/admin/compliance/documentos' },
]
const fixtures = new Map([
  ['/api/admin/auth/me', { autenticado: true, usuarioId: 'synthetic-admin', nome: 'Administrador sintético', email: 'admin@example.invalid', papeis: ['ADMIN'], permissoes: ['SEGURANCA_GERENCIAR'] }],
  ['/api/admin/moderacao/resumo', { anunciosPendentesModeracao: 0 }],
  ['/api/admin/tickets/indicadores', { pendentesEquipe: 0 }],
  ['/api/admin/denuncias/indicadores', { pendentes: 0 }],
  ['/api/admin/sugestoes/indicadores', { novas: 0 }],
  ...areaCases.map(({ endpoint }) => [endpoint, []]),
])
let application, server, browser, activeContext, origin, failure, denial = null, negative = false
const bounded = (promise, label, milliseconds = 10000) => {
  let timer
  return Promise.race([promise, new Promise((_, reject) => { timer = setTimeout(() => reject(Error('Timeout: ' + label)), milliseconds) })]).finally(() => clearTimeout(timer))
}
const urlFor = (area) => `${origin}/admin/compliance${area?.hash || ''}`
async function assertView(page, area, expectedUrl = urlFor(area)) {
  const title = area?.title || 'Compliance'
  // Native popups may receive no animation frames while opened in the background.
  await page.waitForFunction(({ title, hash }) => document.querySelector('h1')?.textContent.trim() === title && location.hash === hash,
    { title, hash: area?.hash || '' }, { timeout: 5000, polling: 50 })
  assert.equal(page.url(), expectedUrl)
  assert.equal(await page.getByRole('heading', { level: 1, name: title, exact: true }).count(), 1)
  if (area) {
    await page.getByText(area.empty, { exact: true }).waitFor()
    assert.equal(await page.getByRole('status').count(), 0, 'An empty table with ContractState error is not a successfully loaded area.')
    assert.equal(await page.getByRole('link', { name: 'Voltar ao hub', exact: true }).count(), 1)
    assert.equal(await page.locator('article').count(), 0)
  } else {
    assert.equal(await page.locator('article').count(), 3)
    assert.equal(await page.getByRole('link', { name: 'Voltar ao hub', exact: true }).count(), 0)
  }
}
const openLink = (page, area) => page.locator('article').filter({ has: page.getByRole('heading', { name: area.title, exact: true }) }).getByRole('link', { name: 'Abrir', exact: true })
async function scenario(name, action, isNegative = false) {
  observation = { scenario: name, gesture: null }
  negative = isNegative
  denial = null
  const result = { name, result: 'FAIL' }
  results.push(result)
  const start = requests.length
  const context = await browser.newContext({ viewport: { width: 1440, height: 1100 }, serviceWorkers: 'block' })
  activeContext = context
  context.setDefaultTimeout(5000)
  await context.addCookies([{ name: 'JSESSIONID', value: 'synthetic-compliance-session', url: origin, httpOnly: true, sameSite: 'Lax' }])
  for (const event of ['request', 'requestfinished', 'requestfailed']) {
    context.on(event, (request) => {
      if (event === 'request' && new URL(request.url()).origin !== origin) {
        const external = { scenario: name, method: request.method(), origin: new URL(request.url()).origin, path: new URL(request.url()).pathname }
        if (name === 'external-popup-network-denied' && request.url() === 'http://198.51.100.1/f02-outbound') trace('expected-external-attempt', external)
        else unexpected.push(external)
      }
      if (relevantRequest(request)) trace(event, { ...requestDetails(request), ...(event === 'requestfailed' ? { failure: request.failure() } : {}) })
    })
  }
  context.on('response', (response) => {
    if (relevantRequest(response.request())) trace('response', { ...requestDetails(response.request()), status: response.status(), contentType: response.headers()['content-type'], disposition: response.headers()['content-disposition'] })
  })
  context.on('page', (page) => {
    trace('page', { page: pageId(page), url: page.url() })
    for (const event of ['domcontentloaded', 'load', 'close', 'crash']) page.on(event, () => trace(event, { page: pageId(page), url: page.url() }))
    page.on('crash', () => browserErrors.push({ scenario: name, message: 'Page crashed', page: pageId(page) }))
    page.on('pageerror', (error) => (isNegative ? expectedErrors : browserErrors).push({ scenario: name, message: error.message }))
  })
  const page = await context.newPage()
  try {
    await action(page, context, result)
    result.result = 'PASS'
  } catch (error) {
    result.error = error.stack
    result.observed = await page.evaluate(() => ({ url: location.href, title: document.querySelector('h1')?.textContent, text: document.body.innerText })).catch(() => null)
    await page.screenshot({ path: path.join(evidence, name + '.png'), fullPage: true }).catch(() => {})
  } finally {
    result.requests = requests.slice(start)
    await bounded(context.close(), 'close synthetic browser context')
    activeContext = null
    denial = null
    negative = false
  }
}

try {
  process.chdir(frontendRoot)
  assert.equal(process.env.NEXT_PUBLIC_API_URL, '/api/public')
  process.env.NEXT_PUBLIC_SITE_URL ||= 'https://example.invalid'
  process.env.NEXT_PUBLIC_ANALYTICS_ENABLED = 'false'
  process.env.NEXT_PUBLIC_FORCE_HTTPS = 'false'
  process.env.SEARCH_INDEXING_MODE = 'blocked'
  process.env.NEXT_TELEMETRY_DISABLED = '1'
  process.env.NODE_ENV = dev ? 'development' : 'production'
  let handler
  server = http.createServer((request, response) => {
    const scope = { ...observation }
    const url = new URL(request.url, origin || 'http://localhost')
    const details = { serverId: ++nextServerId, path: url.pathname, method: request.method }
    trace('server-start', details, scope)
    for (const event of ['finish', 'close']) response.on(event, () => trace('server-' + event, { ...details, status: response.statusCode, writableFinished: response.writableFinished }, scope))
    // Prevent a previously loaded chunk/document from invalidating a denial test.
    const setHeader = response.setHeader.bind(response)
    response.setHeader = (name, value) => setHeader(name, name.toLowerCase() === 'cache-control' ? 'no-store' : value)
    response.setHeader('cache-control', 'no-store')
    const respond = (status, body, type = 'application/json') => { response.writeHead(status, { 'content-type': type }); response.end(body) }
    const refuse = (reason, status) => {
      const entry = { ...scope, ...details, reason, status, expected: negative }
      refusals.push(entry)
      if (!negative) unexpected.push(entry)
      respond(status, JSON.stringify({ mensagem: 'F02 synthetic refusal', reason }))
    }
    if (url.origin !== origin || request.headers.host !== new URL(origin).host) return refuse('origin', 403)
    if (request.method !== 'GET') return refuse('method', 405)
    if (denial?.(url, request)) return refuse('armed-denial', 503)
    if (fixtures.has(url.pathname)) {
      requests.push({ ...scope, path: url.pathname, query: url.search, method: request.method })
      return respond(200, JSON.stringify(fixtures.get(url.pathname)))
    }
    const logo = url.pathname === '/logo-finallllll.webp' || (url.pathname === '/_next/image' && url.searchParams.get('url') === '/logo-finallllll.webp')
    if (logo) return respond(200, '<svg xmlns="http://www.w3.org/2000/svg" width="150" height="60"><rect width="150" height="60" fill="pink"/></svg>', 'image/svg+xml')
    // Only the existing sidebar's unrelated prefetch is neutralized; arbitrary
    // paths cannot enter the allowlist merely by supplying a prefetch header.
    const sidebarPaths = [...sidebar.matchAll(/href: '(\/admin[^'#]*)'/g)].map((match) => match[1])
    if (request.headers['next-router-prefetch'] === '1' && sidebarPaths.includes(url.pathname) && url.pathname !== '/admin/compliance') return respond(204, '')
    const asset = url.pathname.startsWith('/_next/static/') && !decodeURIComponent(url.pathname).includes('..') && fs.existsSync(path.join(frontendRoot, '.next/static', url.pathname.slice('/_next/static/'.length)))
    if (handler && (url.pathname === '/admin/compliance' || asset || url.pathname === '/favicon.ico')) return handler(request, response)
    return refuse('path', 404)
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  origin = `http://127.0.0.1:${server.address().port}`
  application = require('next')({ dev, dir: frontendRoot, hostname: '127.0.0.1', port: server.address().port })
  await bounded(application.prepare(), 'prepare Next', 60000)
  handler = application.getRequestHandler()
  assert.ok(!process.env.TOPS_UI_BROWSER_CHANNEL, 'Keep the approved default headless shell.')
  browser = await chromium.launch({ headless: true })
  writeJson('runtime.json', { node: process.version, platform: process.platform, arch: process.arch, next: require('next/package.json').version, playwright: playwrightVersion, browser: browser.version(), executor: 'default-headless-shell', uid: process.getuid(), gid: process.getgid(), networkInterfaces: os.networkInterfaces(), dev, buildId: build.buildId, apiBase: build.apiBase, logo: build.logo, realNextRouting: true, realComponents: true, syntheticHttpOnly: true, personalProfileUsed: false })

  await scenario('hub-opens-each-area', async (page) => {
    await page.goto(urlFor(), { timeout: 60000 })
    await assertView(page)
    for (const area of areaCases) {
      await openLink(page, area).click()
      await assertView(page, area)
      await page.getByRole('link', { name: 'Voltar ao hub', exact: true }).click()
      await assertView(page)
    }
  })
  await scenario('direct-hash-and-reload', async (page) => {
    for (const area of areaCases) {
      await page.goto(urlFor(area))
      await assertView(page, area)
      await page.reload()
      await assertView(page, area)
    }
  })
  await scenario('browser-back-and-forward', async (page) => {
    await page.goto(urlFor())
    await assertView(page)
    await openLink(page, areaCases[0]).click()
    await assertView(page, areaCases[0])
    await page.getByRole('link', { name: 'Voltar ao hub', exact: true }).click()
    await assertView(page)
    await openLink(page, areaCases[2]).click()
    await assertView(page, areaCases[2])
    for (const area of [null, areaCases[0], null]) { await page.goBack(); await assertView(page, area) }
    for (const area of [areaCases[0], null, areaCases[2]]) { await page.goForward(); await assertView(page, area) }
  })
  await scenario('sidebar-and-native-hash', async (page) => {
    await page.goto(urlFor(areaCases[0]))
    await assertView(page, areaCases[0])
    await page.getByRole('navigation').getByRole('link', { name: 'Documentos visitantes', exact: true }).click()
    await assertView(page, areaCases[2])
    await page.getByRole('navigation').getByRole('link', { name: 'Compliance', exact: true }).click()
    await assertView(page)
    await page.evaluate(() => { location.hash = 'visitor-risk' })
    await assertView(page, areaCases[1])
    await page.evaluate(() => { location.hash = '' })
    await assertView(page, null, urlFor() + '#')
  })
  for (const returning of [false, true]) {
    await scenario(returning ? 'modified-return-preserves-origin' : 'modified-open-preserves-origin', async (page, context, result) => {
      const sourceArea = returning ? areaCases[0] : null
      const destination = returning ? null : areaCases[2]
      await page.goto(urlFor(sourceArea))
      await assertView(page, sourceArea)
      result.popupChecks = []
      for (const options of [{ modifiers: ['ControlOrMeta'] }, { modifiers: ['Shift'] }, { button: 'middle' }]) {
        observation = { scenario: result.name, gesture: options.button || options.modifiers[0] }
        const link = returning ? page.getByRole('link', { name: 'Voltar ao hub', exact: true }) : openLink(page, destination)
        const check = { options, result: 'FAIL' }
        result.popupChecks.push(check)
        let popup
        try {
          check.before = await page.evaluate(() => ({ url: location.href, hash: location.hash, title: document.querySelector('h1')?.textContent.trim(), visibility: document.visibilityState, focused: document.hasFocus() }))
          trace('gesture-start', { page: pageId(page), options })
          const observe = (event, promise) => promise.then(value => {
            trace(event + '-complete', { page: event === 'open' ? pageId(value) : pageId(page) })
            return value
          }, error => { trace(event + '-error', { error: error.message }); throw error })
          const [opened, clicked] = await Promise.allSettled([observe('open', context.waitForEvent('page')), observe('click', link.click(options))])
          if (opened.status === 'fulfilled') popup = opened.value
          else check.pageError = opened.reason.stack || String(opened.reason)
          if (clicked.status === 'rejected') check.clickError = clicked.reason.stack || String(clicked.reason)
          // Preserve a click failure even when the page-event deadline wins first.
          if (clicked.status === 'rejected') throw clicked.reason
          if (opened.status === 'rejected') throw opened.reason
          await assertView(popup, destination)
          await assertView(page, sourceArea)
          trace('view-complete', { page: pageId(popup), title: destination?.title || 'Compliance' })
          check.result = 'PASS'
        } finally {
          check.popup = popup ? await popup.evaluate(() => ({ url: location.href, hash: location.hash, title: document.querySelector('h1')?.textContent.trim(), readyState: document.readyState, visibility: document.visibilityState, focused: document.hasFocus() })).catch(() => null) : null
          check.origin = await page.evaluate(() => ({ url: location.href, hash: location.hash, title: document.querySelector('h1')?.textContent.trim(), readyState: document.readyState, visibility: document.visibilityState, focused: document.hasFocus() })).catch(() => null)
          if (check.result === 'FAIL' && popup) await popup.screenshot({ path: path.join(evidence, result.name + '-popup.png'), fullPage: true }).catch(() => {})
        }
        await popup.close()
      }
    })
  }
  assert.deepEqual(unexpected, [], 'No unrecognized or external request may escape the synthetic transport.')
  assert.deepEqual(browserErrors, [], 'Real component/routing errors must fail the regression.')
  for (const area of areaCases) assert.ok(requests.some(({ path }) => path === area.endpoint), 'Each real area must fetch its existing contract.')
  assert.equal(results.filter(({ result }) => result === 'FAIL').length, 0, 'Navigation scenarios failed; inspect evidence.')
  const mustNotComplete = async (page, area, result) => {
    let error
    try { await assertView(page, area) } catch (observed) { error = observed }
    assert.ok(error, 'The denied resource must prevent the real view from completing.')
    result.expectedError = error.message
  }
  await scenario('denied-popup-document', async (page, context, result) => {
    await page.goto(urlFor())
    await assertView(page)
    const start = refusals.length
    denial = (url) => url.pathname === '/admin/compliance'
    const [opened, clicked] = await Promise.allSettled([
      context.waitForEvent('page'), openLink(page, areaCases[2]).click({ button: 'middle' }),
    ])
    if (clicked.status === 'rejected') result.clickError = clicked.reason.message
    if (opened.status === 'rejected') result.pageError = opened.reason.message
    assert.equal(clicked.status, 'fulfilled')
    assert.equal(opened.status, 'fulfilled')
    await mustNotComplete(opened.value, areaCases[2], result)
    result.blocked = refusals.slice(start)
    assert.ok(result.blocked.some(({ path, status, reason }) => path === '/admin/compliance' && status === 503 && reason === 'armed-denial'), 'The new document must reach the denying server before delivery.')
    await assertView(page)
  }, true)
  await scenario('denied-essential-script', async (page, _context, result) => {
    // Fresh browser context, no cache; deny the real webpack runtime before the
    // first document. The area hash requires real client hydration to complete.
    const start = refusals.length
    denial = (url) => /^\/_next\/static\/chunks\/webpack-[^/]+\.js$/.test(url.pathname)
    await page.goto(urlFor(areaCases[0]))
    await mustNotComplete(page, areaCases[0], result)
    result.blocked = refusals.slice(start)
    assert.ok(result.blocked.some(({ path, reason }) => path.includes('/webpack-') && reason === 'armed-denial'), 'Essential script denial was not exercised.')
    assert.ok(!requests.some(({ scenario, path }) => scenario === result.name && path === areaCases[0].endpoint), 'An area requiring the denied runtime must not hydrate/fetch its data.')
  }, true)
  await scenario('denied-known-api', async (page, _context, result) => {
    const start = refusals.length
    denial = (url) => url.pathname === areaCases[0].endpoint
    await page.goto(urlFor(areaCases[0]))
    await mustNotComplete(page, areaCases[0], result)
    result.blocked = refusals.slice(start)
    assert.ok(result.blocked.some(({ path, reason }) => path === areaCases[0].endpoint && reason === 'armed-denial'), 'Known API denial was not exercised.')
  }, true)
  await scenario('unknown-path-and-method-refused', async (page, _context, result) => {
    await page.goto(urlFor())
    await assertView(page)
    const start = refusals.length
    result.statuses = await page.evaluate(async () => [
      (await fetch('/api/admin/f02-unrecognized')).status,
      (await fetch('/api/admin/auth/me', { method: 'POST' })).status,
      (await fetch('/admin/f02-unrecognized', { headers: { 'next-router-prefetch': '1' } })).status,
    ])
    assert.deepEqual(result.statuses, [404, 405, 404])
    result.blocked = refusals.slice(start)
    assert.deepEqual(result.blocked.map(({ reason }) => reason), ['path', 'method', 'path'])
    await assertView(page)
  }, true)
  await scenario('external-popup-network-denied', async (page, context, result) => {
    await page.goto(urlFor())
    await assertView(page)
    // TEST-NET-2 is deliberately unreachable in Docker's network=none namespace.
    // This extra link is only a negative control; all six positives use real UI.
    await page.evaluate(() => {
      const link = document.createElement('a')
      link.href = 'http://198.51.100.1/f02-outbound'
      link.textContent = 'F02 outbound negative control'
      document.querySelector('h1').before(link)
    })
    result.blocked = []
    for (const options of [{ modifiers: ['ControlOrMeta'] }, { modifiers: ['Shift'] }, { button: 'middle' }]) {
      observation = { scenario: result.name, gesture: options.button || options.modifiers[0] }
      const [opened, failed, clicked] = await Promise.allSettled([
        context.waitForEvent('page'),
        context.waitForEvent('requestfailed', { predicate: (request) => request.url() === 'http://198.51.100.1/f02-outbound' }),
        page.getByRole('link', { name: 'F02 outbound negative control', exact: true }).click(options),
      ])
      if (clicked.status === 'rejected') result.clickError = clicked.reason.message
      if (opened.status === 'rejected') result.pageError = opened.reason.message
      assert.equal(clicked.status, 'fulfilled')
      assert.equal(opened.status, 'fulfilled')
      assert.equal(failed.status, 'fulfilled', 'External request must actually fail; no missing observation may pass.')
      assert.match(failed.value.failure().errorText, /ERR_(ADDRESS_UNREACHABLE|NETWORK_UNREACHABLE|INTERNET_DISCONNECTED)/)
      result.blocked.push({ options, failure: failed.value.failure() })
      await opened.value.close()
      await assertView(page)
    }
    assert.equal(result.blocked.length, 3)
  }, true)
  assert.equal(results.filter(({ result }) => result === 'FAIL').length, 0, 'A transport counterproof failed; inspect evidence.')
  assert.deepEqual(unexpected, [], 'Positive cases must never use a refused resource.')
  assert.deepEqual(browserErrors, [], 'No page crash is an expected negative response.')
  for (const [name, hash] of Object.entries(inputHashes)) assert.equal(sha256(fs.readFileSync(path.join(frontendRoot, name))), hash, 'An input changed during the regression: ' + name)
} catch (error) {
  failure = error
} finally {
  for (const [label, action] of [
    ['context', () => activeContext?.close()], ['browser', () => browser?.close()],
    ['Next', () => application?.close()], ['server', () => new Promise((resolve, reject) => {
      if (!server?.listening) return resolve()
      server.close((error) => error ? reject(error) : resolve())
      server.closeAllConnections()
    })],
  ]) {
    try { await bounded(Promise.resolve().then(action), label) } catch (error) { cleanupErrors.push({ label, message: error.message }) }
  }
  writeJson('result.json', { result: failure || cleanupErrors.length ? 'FAIL' : 'PASS', results, unexpected, browserErrors, expectedErrors, refusals, cleanupErrors, error: failure?.stack })
  writeJson('transport.json', transport)
  console.log('ADMIN_COMPLIANCE_HUB_EVIDENCE=' + evidence)
}
if (failure || cleanupErrors.length) {
  console.error(failure?.stack || JSON.stringify(cleanupErrors))
  console.error('ADMIN_COMPLIANCE_HUB_RESULT=FAIL')
  process.exitCode = 1
} else console.log('ADMIN_COMPLIANCE_HUB_RESULT=OK scenarios=' + results.length)
// Next's development watcher retains handles after close; production exits normally.
if (dev) process.exit(process.exitCode || 0)
