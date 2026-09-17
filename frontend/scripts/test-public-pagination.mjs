import assert from 'node:assert/strict'
import fs from 'node:fs'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

// Exercise the existing production Next build, including its route handlers,
// metadata, React SSR and hydration. Only the upstream API is synthetic.
// No backend, external API, personal browser profile or protected media is used.
const require = createRequire(import.meta.url)
const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
process.chdir(frontend)
assert.ok(fs.existsSync(path.join(frontend, '.next/BUILD_ID')), 'Build the frontend before this regression.')
const { chromium } = require(process.env.TOPS_PLAYWRIGHT_MODULE || 'playwright')
const evidence = fs.mkdtempSync(path.join(process.env.TOPS_UI_EVIDENCE_ROOT || os.tmpdir(), 'public-pagination-'))
const write = (name, value) => fs.writeFileSync(path.join(evidence, name), value, { flag: 'wx' })
const writeJson = (name, value) => write(name, JSON.stringify(value, null, 2))
const sha256 = (value) => createHash('sha256').update(value).digest('hex')
const siteOrigin = new URL(process.env.NEXT_PUBLIC_SITE_URL || 'https://example.invalid').origin
const publicApiBase = process.env.NEXT_PUBLIC_API_URL || 'https://example.invalid/api/public'
const indexingMode = process.env.SEARCH_INDEXING_MODE || 'blocked'
assert.equal(indexingMode, 'blocked', 'This fixture reuses the blocked CI build; public indexing has its own policy tests.')
const MAX_SEED = '9223372036854775807'
const MIN_SEED = '-9223372036854775808'
const routes = [
  { name: 'catalog', pathname: '/anuncios', size: 16, urlPageBase: 1 },
  { name: 'state', pathname: '/acompanhantes/go', size: 20, urlPageBase: 0 },
  { name: 'city', pathname: '/acompanhantes/go/goiania', size: 20, urlPageBase: 0 },
  { name: 'neighborhood', pathname: '/acompanhantes/go/goiania/centro', size: 20, urlPageBase: 0 },
]
// API indices are zero-based for both families. Geographic URLs retain their
// historical zero-based convention; /anuncios alone uses one-based URLs.
const pageParameter = (route, apiPage) => apiPage === 0 ? null : String(apiPage + route.urlPageBase)
const requests = [], results = [], unexpected = [], browserErrors = [], cleanupErrors = []
const syntheticLogo = await require('sharp')({ create: { width: 80, height: 24, channels: 3, background: '#fce7f3' } }).png().toBuffer()
let freshSeeds = 0
let browser, application, frontendServer, apiServer, origin, failure, activeContext
let corruptNextBrowserSeed = false

const location = {
  uf: 'GO', estado: 'Goiás', cidade: 'Goiânia', cidadeSlug: 'goiania',
  bairro: 'Centro', bairroSlug: 'centro', enderecoResumido: null,
}
const indexing = {
  indexavel: true, motivo: 'ELEGIVEL', anunciosElegiveisUnicos: 83,
  minimoNecessario: 5, canonica: true,
}
const neighborhood = { nome: 'Centro', slug: 'centro', totalAnunciosAtivos: 83, indexacao: indexing }
const city = { nome: 'Goiânia', slug: 'goiania', totalAnunciosAtivos: 83, indexacao: indexing, bairros: [neighborhood] }
const discovery = { estados: [{ uf: 'GO', nome: 'Goiás', totalAnunciosAtivos: 83, indexacao: indexing, cidades: [city] }] }
const aggregate = {
  estadoUf: 'GO', estadoNome: 'Goiás', cidadeNome: 'Goiânia', cidadeSlug: 'goiania',
  totalAnunciosAtivos: 83, ultimaAtualizacao: '2026-09-14T12:00:00Z', indexacao: indexing,
  bairros: [neighborhood], categorias: [{ codigo: 'MASSAGENS', nome: 'Massagens', totalAnunciosAtivos: 83 }],
  cidadesRelacionadas: [],
}
const inventory = Array.from({ length: 83 }, (_, index) => ({
  id: `00000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`,
  slug: `perfil-fixture-${String(index + 1).padStart(3, '0')}`,
  titulo: `Perfil fixture ${String(index + 1).padStart(3, '0')}`,
  descricaoResumo: 'Perfil de demonstração para navegação local.',
  preco: 100, idade: 25, categoria: 'MASSAGENS', localizacao: location,
  midias: [], topo: index < 33,
  // A non-ranking benefit in the free queue must never move it above topo.
  destaque: index >= 33 && index % 2 === 0,
  beneficiosPublicos: index < 33 ? ['ANUNCIO_TOPO'] : index % 2 === 0 ? ['DESTAQUE'] : [],
  visualizacoes: { total: 0, situacao: 'ZERO_LEGITIMO' },
}))
function orderedInventory(seed) {
  const rotate = (items) => {
    const length = BigInt(items.length)
    const offset = Number(((BigInt(seed) % length) + length) % length)
    return [...items.slice(offset), ...items.slice(0, offset)]
  }
  return [...rotate(inventory.slice(0, 33)), ...rotate(inventory.slice(33))]
}
const expectedSlugs = (seed, page = 1, size = 83) => orderedInventory(seed)
  .slice((page - 1) * size, page * size).map((item) => item.slug)
const contentKeys = [
  'quem-somos', 'footer-resumo-institucional', 'termos-de-uso', 'politica-privacidade',
  'politica-cookies', 'consentimento-promocional', 'verificacao', 'popup-login',
  'texto-whatsapp', 'termos-conteudo-restrito', 'privacidade-conteudo-restrito', 'aviso-legal-conteudo-restrito',
]
const contents = contentKeys.map((contentKey) => ({
  contentKey, titulo: 'Informações da demonstração', corpo: 'Conteúdo sintético para validação local.',
  contentVersion: 1, contentHash: 'synthetic-content', updatedAt: '2026-09-14T12:00:00Z',
}))

function apiReply(url, method, boundary) {
  const entry = { boundary, method, path: url.pathname, query: url.search }
  requests.push(entry)
  const response = (status, body) => ({ status, body })
  if (method !== 'GET') {
    unexpected.push(entry)
    return response(405, { message: 'Unexpected synthetic API mutation' })
  }
  const listing = routes.find((route) => url.pathname === `/api/public${route.pathname}`)
  if (listing) {
    const page = Number(url.searchParams.get('pagina'))
    const size = Number(url.searchParams.get('tamanho'))
    assert.ok(Number.isSafeInteger(page) && page >= 0, 'API page is zero-based.')
    assert.equal(size, listing.size, 'Keep the existing page size for each route.')
    const requestedSeed = url.searchParams.get('ordemSeed')
    if (requestedSeed !== null && (!/^-?\d+$/.test(requestedSeed)
      || BigInt(requestedSeed) < BigInt(MIN_SEED) || BigInt(requestedSeed) > BigInt(MAX_SEED))) {
      return response(400, { message: 'ordemSeed invalida' })
    }
    const seed = requestedSeed === null ? String(BigInt(MAX_SEED) - BigInt(++freshSeeds)) : String(BigInt(requestedSeed))
    entry.page = page
    entry.size = size
    entry.requestedSeed = requestedSeed
    entry.seed = seed
    if (url.searchParams.get('busca') === 'fixture-503') return response(503, { message: 'Falha sintética de catálogo' })
    const items = orderedInventory(seed).slice(page * size, (page + 1) * size)
    let responseSeed = seed
    if (boundary === 'browser' && corruptNextBrowserSeed) {
      corruptNextBrowserSeed = false
      responseSeed = seed === MIN_SEED ? MAX_SEED : MIN_SEED
    }
    entry.responseSeed = responseSeed
    entry.slugs = items.map((item) => item.slug)
    return response(200, {
      itens: items,
      paginacao: { pagina: page, tamanho: size, totalItens: inventory.length, totalPaginas: Math.ceil(inventory.length / size), ordemSeed: responseSeed },
      ...(listing.name === 'catalog' ? { categoria: url.searchParams.get('categoria') }
        : { localidade: location, seo: { indexavelFuturo: true } }),
    })
  }
  if (['/api/public/localidades', '/api/public/localidades/catalogo'].includes(url.pathname)) return response(200, discovery)
  if (url.pathname === '/api/public/localidades/go/goiania') return response(200, aggregate)
  if (url.pathname === '/api/public/conteudos-site') return response(200, contents)
  if (url.pathname === '/api/public/auth/me') return response(200, null)
  if (url.pathname === '/api/public/compliance/visitor/status') return response(200, {
    globalAccepted: true, verified: false, explicitVerified: false, state: 'GLOBAL_ACEITO',
  })
  if (url.pathname === '/api/public/compliance/age-gate/status') return response(200, { accepted: true, state: 'GLOBAL_ACEITO' })
  if (['/api/public/stories/ativos', '/api/public/avisos', '/api/public/categorias-home'].includes(url.pathname)) return response(200, [])
  unexpected.push(entry)
  return response(404, { message: 'Unexpected synthetic API path' })
}

const bounded = (promise, label, timeout = 20000) => {
  let timer
  return Promise.race([promise, new Promise((_, reject) => {
    timer = setTimeout(() => reject(Error(`Timeout: ${label}`)), timeout)
  })]).finally(() => clearTimeout(timer))
}
async function waitFor(predicate, label) {
  const deadline = performance.now() + 10000
  while (!(await predicate())) {
    if (performance.now() >= deadline) throw Error(`Observation timeout: ${label}`)
    await new Promise((resolve) => setTimeout(resolve, 50))
  }
}
async function closeServer(server) {
  server.closeAllConnections()
  await bounded(new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve())), 'HTTP server close')
}
async function openContext(javaScriptEnabled, viewport = { width: 1280, height: 900 }) {
  const context = await browser.newContext({ javaScriptEnabled, serviceWorkers: 'block', viewport })
  activeContext = context
  const publicApi = new URL(publicApiBase, origin)
  await context.route('**/*', async (route) => {
    const request = route.request(), url = new URL(request.url())
    // The existing footer uses this exact absolute logo URL. Substitute it
    // locally rather than contacting the production site during this fixture.
    if (url.href === 'https://topsdojob.com/logo-finallllll.webp' && request.method() === 'GET') {
      requests.push({ boundary: 'synthetic-logo', path: url.pathname })
      await route.fulfill({ status: 200, contentType: 'image/png', body: syntheticLogo })
      return
    }
    if ((url.origin === publicApi.origin || url.origin === origin) && url.pathname.startsWith('/api/public/')) {
      const reply = apiReply(url, request.method(), 'browser')
      await route.fulfill({ status: reply.status, contentType: 'application/json', body: JSON.stringify(reply.body), headers: { 'cache-control': 'no-store' } })
      return
    }
    if (url.origin === origin) { await route.continue(); return }
    unexpected.push({ boundary: 'browser-network', method: request.method(), origin: url.origin, path: url.pathname })
    await route.abort()
  })
  const page = await context.newPage()
  page.setDefaultTimeout(10000)
  page.on('pageerror', (error) => browserErrors.push(error.message))
  return { context, page }
}
async function scenario(name, javaScriptEnabled, action, viewport) {
  const before = requests.length
  const { context, page } = await openContext(javaScriptEnabled, viewport)
  try {
    await action(page)
    if (!javaScriptEnabled) assert.equal(requests.slice(before).filter((entry) => entry.boundary === 'browser').length, 0, 'No-JS traversal must use only server-rendered catalog responses.')
    results.push({ name, result: 'PASS', requests: requests.slice(before) })
    console.log(`ok ${results.length} - ${name}`)
  } catch (error) {
    write(`${name}-failure.html`, await page.content())
    await page.screenshot({ path: path.join(evidence, `${name}-failure.png`), fullPage: false })
    throw error
  } finally {
    await bounded(context.close(), 'browser context close')
    activeContext = undefined
  }
}
const pagination = (page) => page.getByRole('navigation', { name: /pagin/i })
const nextLink = (page) => pagination(page).getByRole('link', { name: /pr[oó]xim/i }).first()
async function slugsOnPage(page) {
  return page.locator('.public-anuncio-card').evaluateAll((cards) => cards.map((card) => {
    const link = card.querySelector('a[href^="/anuncios/"]')
    if (!link) throw Error('Real card is missing its HTML profile link.')
    return new URL(link.getAttribute('href'), location.origin).pathname.split('/').at(-1)
  }))
}
function serverPageRequests(start, route, apiPage, requestedSeed) {
  const observed = requests.slice(start).filter((entry) => entry.boundary === 'server' && entry.path === `/api/public${route.pathname}`)
  assert.ok(observed.length > 0, 'The actual Next route must request the selected API page.')
  for (const entry of observed) {
    assert.equal(new URLSearchParams(entry.query).get('pagina'), String(apiPage), `${route.pathname}: wrong API pagina`)
    assert.equal(entry.page, apiPage)
    assert.equal(entry.requestedSeed, requestedSeed)
  }
  return observed
}
async function metadata(page, route, apiPage, filters = {}) {
  const canonicals = await page.locator('link[rel="canonical"]').evaluateAll((nodes) => nodes.map((node) => node.href))
  assert.equal(canonicals.length, 1, 'Each HTML document has exactly one canonical.')
  const canonical = new URL(canonicals[0])
  assert.equal(canonical.origin, siteOrigin)
  assert.equal(canonical.pathname, route.pathname)
  assert.equal(canonical.searchParams.get('page'), pageParameter(route, apiPage))
  assert.equal([...canonical.searchParams.keys()].some((key) => /seed/i.test(key)), false)
  for (const [key, value] of Object.entries(filters)) assert.equal(canonical.searchParams.get(key), value)
  const robots = await page.locator('meta[name="robots"]').evaluateAll((nodes) => nodes.map((node) => node.content).join(','))
  assert.match(robots, /noindex/)
  assert.match(robots, /nofollow/)
  const relations = {}
  if (route.urlPageBase === 0) {
    for (const [rel, destination] of [['prev', apiPage - 1], ['next', apiPage + 1]]) {
      const available = destination >= 0 && destination < Math.ceil(inventory.length / route.size)
      const hrefs = await page.locator(`link[rel="${rel}"]`).evaluateAll((nodes) => nodes.map((node) => node.href))
      assert.equal(hrefs.length, available ? 1 : 0, `${route.pathname}: incorrect rel=${rel}`)
      if (available) {
        const url = new URL(hrefs[0])
        assert.equal(url.origin, siteOrigin)
        assert.equal(url.pathname, route.pathname)
        assert.equal(url.searchParams.get('page'), pageParameter(route, destination))
        assert.equal(url.searchParams.has('ordemSeed'), false)
        relations[rel] = url.href
      }
    }
  }
  return { canonical: canonical.href, robots, relations }
}
async function checkLinks(page, route, apiPage, seed, filters = {}) {
  const links = await pagination(page).locator('a[href]').evaluateAll((nodes) => nodes.map((node) => ({ href: node.getAttribute('href'), text: node.textContent.trim() })))
  assert.ok(links.length > 0, 'Pagination contains genuine HTML anchors.')
  for (const link of links) {
    const url = new URL(link.href, origin)
    assert.equal(url.origin, origin)
    assert.equal(url.pathname, route.pathname)
    assert.equal(url.searchParams.get('ordemSeed'), seed, 'Every navigation link preserves the exact response seed.')
    const pageValue = url.searchParams.get('page')
    assert.ok(pageValue === null || (/^[1-9]\d*$/.test(pageValue) && Number(pageValue) > route.urlPageBase), 'The first page link is clean; subsequent links use the route family convention.')
    for (const [key, value] of Object.entries(filters)) assert.equal(url.searchParams.get(key), value)
  }
  for (const [name, destination] of [[/anterior/i, apiPage - 1], [/pr[oó]xim/i, apiPage + 1]]) {
    const available = destination >= 0 && destination < Math.ceil(inventory.length / route.size)
    const navigationLinks = links.filter((link) => name.test(link.text))
    assert.equal(navigationLinks.length, available ? 1 : 0, `${route.pathname}: incorrect previous/next anchor`)
    if (available) assert.equal(new URL(navigationLinks[0].href, origin).searchParams.get('page'), pageParameter(route, destination))
  }
  return links
}

try {
  apiServer = http.createServer((request, response) => {
    try {
      const reply = apiReply(new URL(request.url, 'http://fixture.invalid'), request.method, 'server')
      response.writeHead(reply.status, { 'content-type': 'application/json', 'cache-control': 'no-store' })
      response.end(JSON.stringify(reply.body))
    } catch (error) {
      unexpected.push({ boundary: 'synthetic-contract', message: error.message })
      response.writeHead(500, { 'content-type': 'application/json' })
      response.end(JSON.stringify({ message: 'Synthetic contract failure' }))
    }
  })
  await new Promise((resolve) => apiServer.listen(0, '127.0.0.1', resolve))
  // Set the private endpoint before Next loads any built server modules. The
  // NEXT_PUBLIC values must match the preceding build, as they are inlined.
  process.env.INTERNAL_API_URL = `http://127.0.0.1:${apiServer.address().port}/api/public`
  process.env.NEXT_PUBLIC_SITE_URL = siteOrigin
  process.env.NEXT_PUBLIC_API_URL = publicApiBase
  process.env.SEARCH_INDEXING_MODE = indexingMode
  process.env.NODE_ENV = 'production'
  process.env.NEXT_TELEMETRY_DISABLED = '1'
  let handleNext
  frontendServer = http.createServer((request, response) => {
    if (handleNext) return handleNext(request, response)
    response.writeHead(503)
    response.end('Fixture is starting')
  })
  await new Promise((resolve) => frontendServer.listen(0, '127.0.0.1', resolve))
  origin = `http://127.0.0.1:${frontendServer.address().port}`
  // Next also needs the actual ephemeral port for its own request URL metadata.
  application = require('next')({ dev: false, dir: frontend, hostname: '127.0.0.1', port: frontendServer.address().port })
  await bounded(application.prepare(), 'production Next prepare', 30000)
  handleNext = application.getRequestHandler()
  const channel = process.env.TOPS_UI_BROWSER_CHANNEL
  browser = await chromium.launch({ headless: true, ...(channel && channel !== 'chromium' ? { channel } : {}) })
  writeJson('runtime.json', {
    node: process.version, next: require('next/package.json').version, browser: browser.version(),
    buildId: fs.readFileSync(path.join(frontend, '.next/BUILD_ID'), 'utf8').trim(),
    routesManifestSha256: sha256(fs.readFileSync(path.join(frontend, '.next/routes-manifest.json'))),
    inventorySha256: sha256(JSON.stringify(inventory)), inventory: 83, topo: 33, free: 50,
    indexingMode, siteOrigin, realNextSSR: true, realBackend: false, protectedMedia: false,
    syntheticGlobalAgeAcceptance: true, syntheticExplicitVerification: false, personalProfileUsed: false,
    syntheticLogoSha256: sha256(syntheticLogo),
    pageConventions: routes.map(({ pathname, urlPageBase }) => ({ pathname, urlPageBase, apiPageBase: 0 })),
  })

  await scenario('nojs-institutional-metadata-and-privacy-alias', false, async (page) => {
    const observations = []
    for (const route of [
      {
        pathname: '/contato', title: 'Contato e suporte | Tops do Job',
        description: 'Consulte os canais de atendimento, privacidade e segurança do Tops do Job e saiba como falar com a equipe pelo suporte interno.',
      },
      {
        pathname: '/politica-de-privacidade', title: 'Política de privacidade | Tops do Job',
        description: 'Consulte como o Tops do Job trata dados pessoais e protege a privacidade.',
      },
    ]) {
      const response = await page.goto(`${origin}${route.pathname}`, { waitUntil: 'load' })
      assert.equal(response.status(), 200)
      assert.equal(response.request().redirectedFrom(), null, 'The institutional destination must respond directly.')
      assert.match(response.headers()['content-type'], /text\/html/)
      assert.match(response.headers()['x-robots-tag'], /noindex/, 'Institutional metadata must preserve the blocked build policy.')
      assert.equal(await page.title(), route.title)
      assert.deepEqual(await page.locator('meta[name="description"]').evaluateAll((nodes) => nodes.map((node) => node.content)), [route.description])
      const canonical = `${siteOrigin}${route.pathname}`
      assert.deepEqual(await page.locator('link[rel="canonical"]').evaluateAll((nodes) => nodes.map((node) => node.href)), [canonical])
      for (const [property, expected] of [['og:title', route.title], ['og:description', route.description], ['og:url', canonical]]) {
        assert.deepEqual(await page.locator(`meta[property="${property}"]`).evaluateAll((nodes) => nodes.map((node) => node.content)), [expected])
      }
      const robots = await page.locator('meta[name="robots"]').evaluateAll((nodes) => nodes.map((node) => node.content).join(','))
      assert.match(robots, /noindex/)
      assert.match(robots, /nofollow/)
      write(`nojs-institutional-${route.pathname.slice(1)}.html`, await response.body())
      observations.push({ pathname: route.pathname, status: response.status(), title: route.title, description: route.description, canonical, robots })
    }

    const alias = await fetch(`${origin}/privacidade`, { redirect: 'manual' })
    assert.equal(alias.status, 308, 'The privacy alias must be a permanent HTTP redirect.')
    const location = alias.headers.get('location')
    assert.ok(location)
    const destination = new URL(location, origin)
    assert.equal(destination.href, `${origin}/politica-de-privacidade`, 'The alias must target the existing institutional page, not the home page.')
    await alias.arrayBuffer()
    const response = await page.goto(destination.href, { waitUntil: 'load' })
    assert.equal(response.status(), 200)
    assert.equal(response.request().redirectedFrom(), null, 'The privacy alias must complete in exactly one redirect hop.')
    assert.equal(response.headers().location, undefined)
    assert.deepEqual(await page.locator('link[rel="canonical"]').evaluateAll((nodes) => nodes.map((node) => node.href)), [`${siteOrigin}/politica-de-privacidade`])
    observations.push({ pathname: '/privacidade', status: alias.status, destination: destination.href, finalStatus: response.status(), redirectHops: 1 })
    writeJson('nojs-institutional-metadata-and-privacy-alias.json', observations)
  })

  await scenario('nojs-retired-programmatic-blog-routes', false, async (page) => {
    const observations = []
    for (const theme of ['acompanhantes', 'garotas-de-programa', 'anuncios-adultos']) {
      for (const pathname of [`/blog/${theme}/goiania`, `/blog/cidade/${theme}/goiania`]) {
        const url = `${origin}${pathname}?uf=go`
        const response = await page.goto(url, { waitUntil: 'load' })
        assert.equal(response.status(), 404, 'Unsupported programmatic blog routes must remain unavailable.')
        assert.equal(response.request().redirectedFrom(), null, 'Do not retain an unconditional redirect to another 404.')
        assert.equal(response.headers().location, undefined)
        assert.equal(page.url(), url)
        observations.push({ pathname, query: '?uf=go', status: response.status(), redirectHops: 0 })
      }
    }
    writeJson('nojs-retired-programmatic-blog-routes.json', observations)
  })

  for (const route of routes) {
    await scenario(`nojs-${route.name}-complete`, false, async (page) => {
      let requestStart = requests.length
      let response = await page.goto(`${origin}${route.pathname}`, { waitUntil: 'load' })
      const firstHref = await nextLink(page).getAttribute('href')
      const seed = new URL(firstHref, origin).searchParams.get('ordemSeed')
      assert.ok(seed && /^-?\d+$/.test(seed), 'The initial SSR response exposes its seed through navigation links.')
      assert.ok(requests.some((entry) => entry.path === `/api/public${route.pathname}` && entry.requestedSeed === null && entry.seed === seed))
      const pages = Math.ceil(inventory.length / route.size), seen = [], observations = []
      for (let number = 1; number <= pages; number++) {
        const apiPage = number - 1
        assert.equal(response.status(), 200)
        assert.match(response.headers()['content-type'], /text\/html/)
        assert.match(response.headers()['x-robots-tag'], /noindex/)
        const slugs = await slugsOnPage(page)
        assert.deepEqual(slugs, expectedSlugs(seed, number, route.size))
        assert.equal(new URL(page.url()).searchParams.get('page'), pageParameter(route, apiPage))
        serverPageRequests(requestStart, route, apiPage, number === 1 ? null : seed)
        seen.push(...slugs)
        const meta = await metadata(page, route, apiPage)
        const links = await checkLinks(page, route, apiPage, seed)
        observations.push({ position: number, apiPage, pageParameter: pageParameter(route, apiPage), url: page.url(), slugs, ...meta, links })
        write(`nojs-${route.name}-page-${number}.html`, await response.body())
        if (number === 1 || number === pages) await page.screenshot({ path: path.join(evidence, `nojs-${route.name}-page-${number}.png`), fullPage: true })
        if (number < pages) {
          const href = await nextLink(page).getAttribute('href')
          assert.equal(new URL(href, origin).searchParams.get('page'), pageParameter(route, apiPage + 1))
          requestStart = requests.length
          ;[response] = await Promise.all([page.waitForNavigation({ waitUntil: 'load' }), nextLink(page).click()])
        } else assert.equal(await pagination(page).getByRole('link', { name: /pr[oó]xim/i }).count(), 0)
      }
      assert.equal(new Set(seen).size, inventory.length)
      assert.deepEqual(seen, orderedInventory(seed).map((item) => item.slug), 'The full two-queue order must survive HTML-only traversal.')
      writeJson(`nojs-${route.name}-traversal.json`, observations)
      const cleanResponse = await page.goto(`${origin}${route.pathname}`, { waitUntil: 'load' })
      assert.equal(cleanResponse.status(), 200)
      const fresh = new URL(await nextLink(page).getAttribute('href'), origin).searchParams.get('ordemSeed')
      assert.notEqual(fresh, seed, 'An independent clean access must request a fresh backend seed.')
    })
  }

  await scenario('nojs-direct-seeds-filters-and-invalid', false, async (page) => {
    const filters = { categoria: 'MASSAGENS', busca: 'fixture café & 100%_💖', anunciante: 'fixture-owner' }
    const conventionObservations = []
    for (const route of routes) {
      const directPages = route.urlPageBase === 0 ? [undefined, '0', '1', '2'] : [undefined, '2']
      for (const value of directPages) {
        const query = new URLSearchParams({ ordemSeed: MAX_SEED })
        if (value !== undefined) query.set('page', value)
        const requestedUrl = `${origin}${route.pathname}?${query}`
        const requestStart = requests.length
        const response = await page.goto(requestedUrl, { waitUntil: 'load' })
        const apiPage = value === undefined ? 0 : Number(value) - route.urlPageBase
        assert.equal(response.status(), 200)
        assert.equal(response.request().redirectedFrom(), null, 'Valid direct pages must retain their historical URL without a redirect.')
        assert.equal(page.url(), requestedUrl)
        assert.deepEqual(await slugsOnPage(page), expectedSlugs(MAX_SEED, apiPage + 1, route.size))
        const observed = serverPageRequests(requestStart, route, apiPage, MAX_SEED)
        const meta = await metadata(page, route, apiPage)
        const links = await checkLinks(page, route, apiPage, MAX_SEED)
        conventionObservations.push({ pathname: route.pathname, requestedPage: value ?? null, url: page.url(), status: response.status(), apiPages: observed.map((entry) => entry.page), ...meta, links })
      }
      for (const [inputSeed, seed] of [[MAX_SEED, MAX_SEED], [MIN_SEED, MIN_SEED], ['00042', '42']]) {
        const params = new URLSearchParams({ page: '2', ordemSeed: inputSeed, ...(route.name === 'catalog' ? filters : {}) })
        const requestStart = requests.length
        const response = await page.goto(`${origin}${route.pathname}?${params}`, { waitUntil: 'load' })
        const apiPage = 2 - route.urlPageBase
        assert.equal(response.status(), 200)
        assert.deepEqual(await slugsOnPage(page), expectedSlugs(seed, apiPage + 1, route.size))
        serverPageRequests(requestStart, route, apiPage, seed)
        await metadata(page, route, apiPage, route.name === 'catalog' ? filters : {})
        await checkLinks(page, route, apiPage, seed, route.name === 'catalog' ? filters : {})
      }
      if (route.urlPageBase === 1) {
        const redirectQuery = new URLSearchParams({ page: '1', ordemSeed: MAX_SEED, utm_source: 'fixture', tag: 'a & b' })
        const redirect = await fetch(`${origin}${route.pathname}?${redirectQuery}`, { redirect: 'manual' })
        assert.equal(redirect.status, 308, 'Only /anuncios page one normalizes with an HTTP permanent redirect.')
        const destination = new URL(redirect.headers.get('location'), origin)
        redirectQuery.delete('page')
        assert.equal(destination.pathname, route.pathname)
        assert.deepEqual([...destination.searchParams].sort(), [...redirectQuery].sort(), 'Page-one redirect removes only page.')
        conventionObservations.push({ pathname: route.pathname, requestedPage: '1', status: redirect.status, destination: destination.href })
        await redirect.arrayBuffer()
      }
      const invalidPages = [...(route.urlPageBase === 1 ? ['page=0'] : []), 'page=-1', 'page=', 'page=1.5', 'page=9007199254740992', 'page=2&page=3', 'page=99', 'ordemSeed=', 'ordemSeed=nope', 'ordemSeed=9223372036854775808', 'ordemSeed=-9223372036854775809', 'ordemSeed=1&ordemSeed=2']
      for (const query of invalidPages) {
        const response = await page.goto(`${origin}${route.pathname}?${query}`, { waitUntil: 'load' })
        assert.equal(response.status(), 404, `${route.pathname}?${query} must not render a valid catalog.`)
        assert.equal(await page.locator('.public-anuncio-card').count(), 0)
        if (query === 'page=0') conventionObservations.push({ pathname: route.pathname, requestedPage: '0', status: response.status() })
      }
    }
    writeJson('nojs-page-conventions.json', conventionObservations)
    const failed = await page.goto(`${origin}/anuncios?busca=fixture-503`, { waitUntil: 'load' })
    assert.equal(failed.status(), 500, 'An upstream 503 remains a technical failure, not a successful empty listing.')
  })

  await scenario('js-load-more-and-link-continuity', true, async (page) => {
    await page.goto(`${origin}/anuncios`, { waitUntil: 'load' })
    const seed = new URL(await nextLink(page).getAttribute('href'), origin).searchParams.get('ordemSeed')
    for (const count of [32, 48]) {
      await page.locator('.public-anuncio-card').last().scrollIntoViewIfNeeded()
      await page.evaluate(() => window.scrollBy(0, 600))
      await waitFor(async () => (await slugsOnPage(page)).length === count, `automatic append to ${count}`)
    }
    const loadMore = page.getByRole('button', { name: 'Ver mais resultados', exact: true })
    await loadMore.waitFor({ state: 'visible' })
    assert.deepEqual(await slugsOnPage(page), orderedInventory(seed).slice(0, 48).map((item) => item.slug))
    assert.equal(requests.some((entry) => entry.boundary === 'browser' && entry.path === '/api/public/anuncios' && entry.page > 2), false, 'Automatic loading stops after three pages.')
    await loadMore.click()
    await waitFor(async () => (await slugsOnPage(page)).length === 64, 'manual append to page four')
    assert.deepEqual(await slugsOnPage(page), orderedInventory(seed).slice(0, 64).map((item) => item.slug))
    const continuation = new URL(await nextLink(page).getAttribute('href'), origin)
    assert.equal(continuation.searchParams.get('page'), '5', 'HTML continuation advances after appended pages.')
    assert.equal(continuation.searchParams.get('ordemSeed'), seed)
    await page.screenshot({ path: path.join(evidence, 'js-load-more-page-four.png'), fullPage: false })
    await nextLink(page).click()
    await page.waitForURL(continuation.href)
    await waitFor(async () => (await slugsOnPage(page))[0] === expectedSlugs(seed, 5, 16)[0], 'deep link renders the selected page')
    await metadata(page, routes[0], 4)
    const browserPages = requests.filter((entry) => entry.boundary === 'browser' && entry.path === '/api/public/anuncios')
    assert.ok(browserPages.length >= 3)
    assert.equal(browserPages.every((entry) => entry.requestedSeed === seed), true)
  })

  await scenario('js-seed-mismatch-retry', true, async (page) => {
    await page.goto(`${origin}/anuncios?page=4&ordemSeed=${MAX_SEED}`, { waitUntil: 'load' })
    assert.deepEqual(await slugsOnPage(page), expectedSlugs(MAX_SEED, 4, 16))
    corruptNextBrowserSeed = true
    await page.locator('.public-anuncio-card').last().scrollIntoViewIfNeeded()
    await page.evaluate(() => window.scrollBy(0, 600))
    const retry = page.getByRole('button', { name: /tentar novamente/i })
    await retry.waitFor({ state: 'visible' })
    assert.deepEqual(await slugsOnPage(page), expectedSlugs(MAX_SEED, 4, 16), 'A mismatched response must not alter the rendered inventory.')
    await retry.click()
    await waitFor(async () => (await slugsOnPage(page)).length >= 32, 'retry appends using the original seed')
    const slugs = await slugsOnPage(page)
    assert.deepEqual(slugs, orderedInventory(MAX_SEED).slice(48, 48 + slugs.length).map((item) => item.slug))
  })

  await scenario('js-filter-changes-request-fresh-seeds', true, async (page) => {
    const initial = new URLSearchParams({ page: '2', ordemSeed: MIN_SEED, anunciante: 'fixture-owner', busca: 'fixture original' })
    await page.goto(`${origin}/anuncios?${initial}`, { waitUntil: 'load' })
    const beforeRemove = requests.length
    await page.getByRole('button', { name: 'Remover filtro', exact: true }).click()
    await page.waitForURL((url) => !url.searchParams.has('anunciante') && !url.searchParams.has('page') && !url.searchParams.has('ordemSeed'))
    await waitFor(async () => {
      const href = await nextLink(page).getAttribute('href')
      return new URL(href, origin).searchParams.get('ordemSeed') !== MIN_SEED
    }, 'removing advertiser resets the ordering chain')
    const afterRemoveSeed = new URL(await nextLink(page).getAttribute('href'), origin).searchParams.get('ordemSeed')
    assert.equal(new URL(page.url()).searchParams.get('busca'), 'fixture original')
    assert.ok(requests.slice(beforeRemove).some((entry) => entry.path === '/api/public/anuncios' && entry.requestedSeed === null && entry.seed === afterRemoveSeed))

    // Start a second independent filter action from a seeded deep link.
    await page.goto(`${origin}/anuncios?page=2&ordemSeed=${MAX_SEED}`, { waitUntil: 'load' })
    const beforeSearch = requests.length
    await page.getByPlaceholder('Buscar acompanhantes, bairro ou nome...', { exact: true }).fill('fixture outra busca')
    await page.getByRole('button', { name: 'Buscar', exact: true }).click()
    await page.waitForURL((url) => url.searchParams.get('busca') === 'fixture outra busca' && !url.searchParams.has('page') && !url.searchParams.has('ordemSeed'))
    await waitFor(async () => {
      const href = await nextLink(page).getAttribute('href')
      return new URL(href, origin).searchParams.get('ordemSeed') !== MAX_SEED
    }, 'new search resets the ordering chain')
    const afterSearchSeed = new URL(await nextLink(page).getAttribute('href'), origin).searchParams.get('ordemSeed')
    assert.ok(requests.slice(beforeSearch).some((entry) => entry.path === '/api/public/anuncios' && entry.requestedSeed === null && entry.seed === afterSearchSeed))
    assert.notEqual(afterSearchSeed, afterRemoveSeed)
    await metadata(page, routes[0], 0, { busca: 'fixture outra busca' })
    await page.screenshot({ path: path.join(evidence, 'js-new-search-fresh-chain.png'), fullPage: false })
  })

  await scenario('js-locality-next-updates-url-and-metadata', true, async (page) => {
    const route = routes[2]
    await page.goto(`${origin}${route.pathname}?ordemSeed=${MIN_SEED}`, { waitUntil: 'load' })
    const href = new URL(await nextLink(page).getAttribute('href'), origin).href
    assert.equal(new URL(href).searchParams.get('page'), '1', 'The second geographic result page keeps its historical page=1 URL.')
    const before = requests.length
    await nextLink(page).click()
    await page.waitForURL(href)
    await waitFor(async () => (await slugsOnPage(page))[0] === expectedSlugs(MIN_SEED, 2, 20)[0], 'geographic page two')
    assert.deepEqual(await slugsOnPage(page), expectedSlugs(MIN_SEED, 2, 20))
    serverPageRequests(before, route, 1, MIN_SEED)
    await metadata(page, route, 1)
    assert.equal(requests.slice(before).some((entry) => entry.boundary === 'browser' && entry.path.startsWith('/api/public/acompanhantes/')), false, 'Locality links use Next navigation, not a second manual API path.')
    await page.screenshot({ path: path.join(evidence, 'js-city-page-two-mobile.png'), fullPage: true })
  }, { width: 390, height: 844 })

  assert.deepEqual(unexpected, [], 'Every transport request must remain inside the explicit synthetic fixture.')
  assert.deepEqual(browserErrors, [], 'There must be no hydration or browser runtime errors.')
} catch (error) {
  failure = error
} finally {
  if (activeContext) try { await bounded(activeContext.close(), 'active context') } catch (error) { cleanupErrors.push(String(error)) }
  if (browser) try { await bounded(browser.close(), 'browser close') } catch (error) { cleanupErrors.push(String(error)) }
  if (frontendServer) try { await closeServer(frontendServer) } catch (error) { cleanupErrors.push(String(error)) }
  if (application) try { await bounded(application.close(), 'Next close') } catch (error) { cleanupErrors.push(String(error)) }
  if (apiServer) try { await closeServer(apiServer) } catch (error) { cleanupErrors.push(String(error)) }
  writeJson('outcome.json', {
    result: !failure && !cleanupErrors.length ? 'PASS' : 'FAIL', cases: results, failure: failure?.stack,
    cleanupErrors, unexpected, browserErrors, requests, evidence,
    proof: 'Actual blocked Next build with synthetic fixed inventory; no production or backend-ordering claim.',
  })
}
if (failure || cleanupErrors.length) {
  console.error(failure?.stack || cleanupErrors.join('\n'))
  console.error(`PUBLIC_PAGINATION_RESULT=FAIL evidence=${evidence}`)
  process.exitCode = 1
} else console.log(`PUBLIC_PAGINATION_RESULT=OK cases=${results.length} evidence=${evidence}`)
