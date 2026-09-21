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

// Real Next routing, hydration, hub, sidebar and area components. Only HTTP
// responses are synthetic. No document file, personal session or backend is used.
// The development mode reproduces the original defect without an extra full
// build; CI and the final candidate use the existing production build by default.
const require = createRequire(import.meta.url)
const dev = process.env.TOPS_UI_NEXT_DEV === 'true'
assert.ok(dev || fs.existsSync(path.join(frontendRoot, '.next/BUILD_ID')), 'Build the frontend before this regression.')
const { chromium } = require(process.env.TOPS_PLAYWRIGHT_MODULE || 'playwright')
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
const results = [], requests = [], unexpected = [], browserErrors = [], cleanupErrors = []
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
let application, server, browser, activeContext, origin, failure
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
    assert.equal(await page.getByRole('link', { name: 'Voltar ao hub', exact: true }).count(), 1)
    assert.equal(await page.locator('article').count(), 0)
  } else {
    assert.equal(await page.locator('article').count(), 3)
    assert.equal(await page.getByRole('link', { name: 'Voltar ao hub', exact: true }).count(), 0)
  }
}
const openLink = (page, area) => page.locator('article').filter({ has: page.getByRole('heading', { name: area.title, exact: true }) }).getByRole('link', { name: 'Abrir', exact: true })
async function scenario(name, action) {
  const result = { name, result: 'FAIL' }
  results.push(result)
  const start = requests.length
  const context = await browser.newContext({ viewport: { width: 1440, height: 1100 }, serviceWorkers: 'block' })
  activeContext = context
  context.setDefaultTimeout(5000)
  await context.addCookies([{ name: 'JSESSIONID', value: 'synthetic-compliance-session', url: origin, httpOnly: true, sameSite: 'Lax' }])
  context.on('page', (page) => page.on('pageerror', (error) => browserErrors.push({ scenario: name, message: error.message })))
  await context.route('**/*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (request.method() === 'GET' && fixtures.has(url.pathname)) {
      requests.push({ scenario: name, path: url.pathname, query: url.search, method: request.method() })
      return route.fulfill({ status: 200, contentType: 'application/json', headers: { 'cache-control': 'no-store' }, body: JSON.stringify(fixtures.get(url.pathname)) })
    }
    if (request.method() === 'GET' && url.pathname === '/logo-finallllll.webp') {
      return route.fulfill({ status: 200, contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="150" height="60"><rect width="150" height="60" fill="pink"/></svg>' })
    }
    if (url.origin === origin && request.method() === 'GET') {
      // Sidebar prefetch transport for unrelated routes is outside this test.
      if (request.headers()['next-router-prefetch'] === '1') return route.fulfill({ status: 204, body: '' })
      if (url.pathname === '/admin/compliance' || url.pathname.startsWith('/_next/') || url.pathname === '/favicon.ico') return route.continue()
    }
    unexpected.push({ scenario: name, method: request.method(), origin: url.origin, path: url.pathname })
    return route.abort('blockedbyclient')
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
  }
}

try {
  process.chdir(frontendRoot)
  process.env.NEXT_PUBLIC_API_URL ||= 'https://example.invalid/api/public'
  process.env.NEXT_PUBLIC_SITE_URL ||= 'https://example.invalid'
  process.env.NEXT_PUBLIC_ANALYTICS_ENABLED = 'false'
  process.env.NEXT_PUBLIC_FORCE_HTTPS = 'false'
  process.env.SEARCH_INDEXING_MODE = 'blocked'
  process.env.NEXT_TELEMETRY_DISABLED = '1'
  process.env.NODE_ENV = dev ? 'development' : 'production'
  // Next's test mode emits non-eval development bundles, preserving the real
  // site's CSP while reproducing the baseline without a second full build.
  if (dev) process.env.__NEXT_TEST_MODE = 'true'
  let handler
  server = http.createServer((request, response) => {
    if (handler) return handler(request, response)
    response.writeHead(503); response.end('Synthetic fixture is starting')
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  origin = `http://127.0.0.1:${server.address().port}`
  application = require('next')({ dev, dir: frontendRoot, hostname: '127.0.0.1', port: server.address().port })
  await bounded(application.prepare(), 'prepare Next', 60000)
  handler = application.getRequestHandler()
  const channel = process.env.TOPS_UI_BROWSER_CHANNEL
  browser = await chromium.launch({ headless: true, ...(channel && channel !== 'chromium' ? { channel } : {}) })
  writeJson('runtime.json', { node: process.version, next: require('next/package.json').version, browser: browser.version(), dev, buildId: dev ? null : fs.readFileSync(path.join(frontendRoot, '.next/BUILD_ID'), 'utf8').trim(), realNextRouting: true, realComponents: true, syntheticHttpOnly: true, personalProfileUsed: false })

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
        const link = returning ? page.getByRole('link', { name: 'Voltar ao hub', exact: true }) : openLink(page, destination)
        const check = { options, result: 'FAIL' }
        result.popupChecks.push(check)
        let popup
        try {
          check.before = await page.evaluate(() => ({ url: location.href, hash: location.hash, title: document.querySelector('h1')?.textContent.trim(), visibility: document.visibilityState, focused: document.hasFocus() }))
          const [opened, clicked] = await Promise.allSettled([context.waitForEvent('page'), link.click(options)])
          if (opened.status === 'fulfilled') popup = opened.value
          else check.pageError = opened.reason.stack || String(opened.reason)
          if (clicked.status === 'rejected') check.clickError = clicked.reason.stack || String(clicked.reason)
          // Preserve a click failure even when the page-event deadline wins first.
          if (clicked.status === 'rejected') throw clicked.reason
          if (opened.status === 'rejected') throw opened.reason
          await assertView(popup, destination)
          await assertView(page, sourceArea)
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
  writeJson('result.json', { result: failure || cleanupErrors.length ? 'FAIL' : 'PASS', results, unexpected, browserErrors, cleanupErrors, error: failure?.stack })
  console.log('ADMIN_COMPLIANCE_HUB_EVIDENCE=' + evidence)
}
if (failure || cleanupErrors.length) {
  console.error(failure?.stack || JSON.stringify(cleanupErrors))
  console.error('ADMIN_COMPLIANCE_HUB_RESULT=FAIL')
  process.exitCode = 1
} else console.log('ADMIN_COMPLIANCE_HUB_RESULT=OK scenarios=' + results.length)
// Next's development watcher retains handles after close; production exits normally.
if (dev) process.exit(process.exitCode || 0)
