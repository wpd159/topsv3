import assert from "node:assert/strict"
import { readdirSync, readFileSync } from "node:fs"
import ts from "typescript"

function source(relativePath) {
  return readFileSync(new URL(`../${relativePath}`, import.meta.url), "utf8")
}

function loadTypeScript(relativePath, dependencies = {}) {
  const { outputText } = ts.transpileModule(source(relativePath), {
    compilerOptions: {
      esModuleInterop: true,
      jsx: ts.JsxEmit.ReactJSX,
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
  })
  const runtimeModule = { exports: {} }
  const requireModule = (specifier) => {
    if (Object.hasOwn(dependencies, specifier)) return dependencies[specifier]
    if (specifier === "react/jsx-runtime") {
      return {
        Fragment: Symbol("Fragment"),
        jsx: (type, props) => ({ type, props }),
        jsxs: (type, props) => ({ type, props }),
      }
    }
    throw new Error(`Import inesperado em ${relativePath}: ${specifier}`)
  }
  new Function("module", "exports", "require", "process", "fetch", outputText)(
    runtimeModule,
    runtimeModule.exports,
    requireModule,
    { env: { SEARCH_INDEXING_MODE: "public", NEXT_PUBLIC_SITE_URL: "https://topsdojob.com" } },
    globalThis.fetch,
  )
  return runtimeModule.exports
}

class NotFoundSignal extends Error {}
class RedirectSignal extends Error {
  constructor(location) {
    super("NEXT_REDIRECT")
    this.location = location
    this.status = 308
  }
}
class ContractError extends Error {
  constructor(status) {
    super(`status-${status}`)
    this.status = status
  }
}

function emptyCatalog() {
  return {
    itens: [],
    categoria: null,
    paginacao: {
      pagina: 0,
      tamanho: 16,
      totalItens: 0,
      totalPaginas: 0,
      ordemSeed: "123",
    },
  }
}

function loadListingPage(listarAnunciosPublicos) {
  const policy = loadTypeScript("src/lib/seo/search-indexing-policy.ts")
  const publicUrl = loadTypeScript("src/lib/seo/public-url.ts", {
    "@/lib/seo/search-indexing-policy": policy,
  })
  return loadTypeScript("src/app/(public-routes)/anuncios/page.tsx", {
    "./anuncios-page-client": function AnunciosPageClient() {},
    "next/navigation": {
      notFound: () => { throw new NotFoundSignal() },
      permanentRedirect: (location) => { throw new RedirectSignal(location) },
    },
    "@/lib/seo/public-url": publicUrl,
    "@/lib/seo/search-indexing-policy": policy,
    "@/lib/public-catalog-server-api": {
      isPublicCatalogNotFound: (error) => error?.status === 400 || error?.status === 404,
      listarAnunciosPublicos,
    },
  })
}

const apiSource = source("src/lib/public-catalog-api.ts")
const serverApiSource = source("src/lib/public-catalog-server-api.ts")
const listingSource = source("src/app/(public-routes)/anuncios/page.tsx")
const barSource = source("src/components/anuncios/barra-localizacao.tsx")
const sitemapSource = source("src/app/sitemap.ts")
const rootLayoutSource = source("src/app/layout.tsx")
const repositorySource = source("../backend/src/main/java/br/com/topsdojob/v3/persistence/repository/AnuncioRepository.java")

let executed = 0
async function test(name, callback) {
  await callback()
  executed += 1
  process.stdout.write(`ok ${executed} - ${name}\n`)
}

await test("busca publica continua disponivel", () => {
  assert.match(barSource, /placeholder=\{placeholderBusca\}/)
  assert.match(barSource, /applyBusca\(\)/)
})

await test("termo e preservado na URL com codificacao segura", () => {
  assert.match(apiSource, /query\.set\('busca', busca\.trim\(\)\)/)
  const query = new URLSearchParams()
  const term = "café 100%_vip\\foto 💖"
  query.set("busca", term)
  assert.equal(query.get("busca"), term)
  assert.match(query.toString(), /100%25_vip%5Cfoto/)
})

await test("busca permanece noindex follow", () => {
  const policy = loadTypeScript("src/lib/seo/search-indexing-policy.ts")
  const decision = policy.buildPublicListingIndexingDecision({ busca: "goiania" }, 1)
  assert.equal(decision.indexable, false)
  assert.equal(policy.buildPublicRobotsMetadata(false).follow, true)
})

await test("busca permanece fora do sitemap", () => {
  assert.doesNotMatch(sitemapSource, /[?&]busca=/i)
})

await test("categoria valida vazia continua renderizavel", async () => {
  const page = loadListingPage(async () => emptyCatalog())
  assert.ok(await page.default({ searchParams: Promise.resolve({ categoria: "TODOS" }) }))
})

await test("categoria invalida continua distinta de zero resultado", async () => {
  const page = loadListingPage(async () => { throw new ContractError(400) })
  await assert.rejects(
    page.default({ searchParams: Promise.resolve({ categoria: "INVALIDA" }) }),
    NotFoundSignal,
  )
})

await test("falha tecnica nao vira HTTP 200 vazio", async () => {
  const failure = new ContractError(503)
  const page = loadListingPage(async () => { throw failure })
  await assert.rejects(
    page.default({ searchParams: Promise.resolve({ busca: "termo" }) }),
    (error) => error === failure,
  )
})

await test("seed nao entra no canonical", () => {
  const policy = loadTypeScript("src/lib/seo/search-indexing-policy.ts")
  const decision = policy.buildPublicListingIndexingDecision(
    { busca: "termo", seed: "123", ordemSeed: "456" },
    1,
  )
  assert.equal(decision.canonicalQuery, "busca=termo")
})

await test("busca paginada SSR continua one-based e preserva filtros, seed e canonical proprio", async () => {
  for (const publicPage of [2, 3]) {
    const requests = []
    const page = loadListingPage(async (...args) => {
      requests.push(args)
      return { ...emptyCatalog(), paginacao: { pagina: publicPage - 1, tamanho: 16, totalItens: 33, totalPaginas: 3, ordemSeed: "9007199254740993" } }
    })
    const searchParams = { page: String(publicPage), busca: "café 100%_vip\\foto", categoria: "TODOS", anunciante: "anunciante-teste", ordemSeed: "9007199254740993", utm_source: "teste" }
    const metadata = await page.generateMetadata({ searchParams: Promise.resolve(searchParams) })
    const rendered = await page.default({ searchParams: Promise.resolve(searchParams) })
    assert.deepEqual(requests, [["TODOS", "café 100%_vip\\foto", publicPage - 1, 16, "9007199254740993", "anunciante-teste"]])
    assert.equal(rendered.props.initialRequest.currentPage, publicPage)
    assert.equal(rendered.props.initialRequest.requestedSeed, "9007199254740993")
    assert.equal(rendered.props.initialData.paginacao.ordemSeed, "9007199254740993")
    const canonical = new URL(metadata.alternates.canonical)
    assert.equal(canonical.searchParams.get("page"), String(publicPage))
    assert.equal(canonical.searchParams.get("busca"), searchParams.busca)
    assert.equal(canonical.searchParams.get("anunciante"), "anunciante-teste")
    assert.equal(canonical.searchParams.has("ordemSeed"), false)
    assert.equal(canonical.searchParams.has("utm_source"), false)
    assert.equal(metadata.openGraph.url, canonical.toString())
    assert.equal(metadata.robots.index, false)
    assert.equal(metadata.robots.follow, true)
    assert.match(metadata.title, new RegExp(`Página ${publicPage}`))
  }
})

await test("page=1 remove somente page e preserva busca, filtros repetidos e seed", async () => {
  let requests = 0
  const page = loadListingPage(async () => { requests += 1; return emptyCatalog() })
  const searchParams = { page: "1", busca: "café 100%", categoria: "TODOS", ordemSeed: "9223372036854775807", filter: ["com-local", "foto"], estadoId: "estado-teste", utm_source: "teste" }
  await assert.rejects(page.default({ searchParams: Promise.resolve(searchParams) }), (error) => {
    assert.ok(error instanceof RedirectSignal)
    assert.equal(error.status, 308)
    const redirect = new URL(error.location, "https://topsdojob.com")
    assert.equal(redirect.pathname, "/anuncios")
    assert.equal(redirect.searchParams.has("page"), false)
    assert.equal(redirect.searchParams.get("busca"), searchParams.busca)
    assert.equal(redirect.searchParams.get("categoria"), "TODOS")
    assert.equal(redirect.searchParams.get("ordemSeed"), searchParams.ordemSeed)
    assert.deepEqual(redirect.searchParams.getAll("filter"), searchParams.filter)
    assert.equal(redirect.searchParams.get("estadoId"), "estado-teste")
    assert.equal(redirect.searchParams.get("utm_source"), "teste")
    return true
  })
  assert.equal(requests, 0)
})

await test("URL interna permanece fora do bundle cliente", () => {
  assert.doesNotMatch(apiSource, /INTERNAL_API|BACKEND_INTERNAL|127\.0\.0\.1/)
  assert.match(serverApiSource, /^import 'server-only'/)
  assert.match(listingSource, /public-catalog-server-api/)
})

await test("limite do campo e coerente com o backend", () => {
  const frontend = loadTypeScript("src/lib/public-search.ts")
  assert.equal(frontend.PUBLIC_SEARCH_MAX_LENGTH, 80)
  assert.match(barSource, /maxLength=\{PUBLIC_SEARCH_MAX_LENGTH\}/)
  assert.match(repositorySource, /:busca is null/)
})

await test("wildcards seguem parametrizados e literais", () => {
  assert.match(repositorySource, /like \('%' \|\| :busca \|\| '%'\) escape '\\\\'/)
  assert.doesNotMatch(repositorySource, /\+\s*:busca\s*\+/)
})

await test("nenhum SearchAction foi introduzido", () => {
  assert.doesNotMatch(rootLayoutSource, /SearchAction|potentialAction/)
})

await test("nenhuma rota SEO de termo livre foi criada", () => {
  const routeNames = readdirSync(new URL("../src/app/(public-routes)/", import.meta.url), {
    withFileTypes: true,
  }).map((entry) => entry.name.toLowerCase())
  assert.equal(routeNames.some((name) => name === "busca" || name === "search"), false)
})

await test("ordenacao comercial e seed permanecem canonicas", () => {
  assert.match(repositorySource, /bp\.codigo = 'ANUNCIO_TOPO'/)
  assert.match(repositorySource, /hashtextextended\(a\.id::text, :seed\)/)
  assert.match(repositorySource, /hashtextextended\(a\.id::text, :seed\),\s*a\.id/)
  assert.doesNotMatch(repositorySource, /order by a\.publicado_em desc/)
})

assert.equal(executed, 16)
console.log(`PUBLIC_SEARCH_RESULT=OK tests=${executed}`)
