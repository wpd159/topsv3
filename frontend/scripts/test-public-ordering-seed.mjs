import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import ts from "typescript"

function source(relativePath) {
  return readFileSync(new URL(`../${relativePath}`, import.meta.url), "utf8")
}

const api = source("src/lib/public-catalog-api.ts")
const grid = source("src/components/anuncios/anuncios-grid.tsx")
const paginatedListing = source("src/components/anuncios/listagem-publica-paginada.tsx")
const listingPage = source("src/app/(public-routes)/anuncios/page.tsx")
const statePage = source("src/app/(public-routes)/acompanhantes/[estado]/page.tsx")
const cityPage = source("src/app/(public-routes)/acompanhantes/[estado]/[cidade]/page.tsx")
const neighborhoodPage = source(
  "src/app/(public-routes)/acompanhantes/[estado]/[cidade]/[bairro]/page.tsx",
)
const sitemap = source("src/app/sitemap.ts")

function loadTypeScript(relativePath, dependencies = {}) {
  const { outputText } = ts.transpileModule(source(relativePath), {
    compilerOptions: {
      esModuleInterop: true,
      jsx: ts.JsxEmit.ReactJSX,
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
  })
  const module = { exports: {} }
  new Function("require", "module", "exports", "process", outputText)(
    (specifier) => {
      if (Object.hasOwn(dependencies, specifier)) return dependencies[specifier]
      throw new Error(`Import inesperado em ${relativePath}: ${specifier}`)
    },
    module,
    module.exports,
    { env: { NEXT_PUBLIC_SITE_URL: "https://topsdojob.com" } },
  )
  return module.exports
}

const publicUrl = loadTypeScript("src/lib/seo/public-url.ts", {
  "@/lib/seo/search-indexing-policy": loadTypeScript("src/lib/seo/search-indexing-policy.ts"),
})

function loadCatalogApi(fetchImpl) {
  const { outputText } = ts.transpileModule(api, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
  })
  const module = { exports: {} }
  class ContractError extends Error {
    constructor(message, status) {
      super(message)
      this.status = status
    }
  }
  const require = (specifier) => {
    if (specifier === "@/lib/text/encoding") {
      return { corrigirEstruturaTexto: (value) => value }
    }
    if (specifier === "@/lib/api-contract") {
      return {
        ApiContractError: ContractError,
        apiErrorFromResponse: async (response) => new ContractError("erro", response.status),
        publicApiUrl: (path) => `https://api.example.invalid/api/public${path}`,
        requireArrayPayload: (value) => value,
      }
    }
    if (specifier === "@/lib/visualizacoes-canonicas") {
      return {
        parseVisualizacoesCanonicas: (value) =>
          value ?? { total: 0, situacao: "ZERO_LEGITIMO" },
      }
    }
    throw new Error(`Import inesperado no adapter publico: ${specifier}`)
  }
  const execute = new Function(
    "require",
    "module",
    "exports",
    "fetch",
    "URLSearchParams",
    outputText,
  )
  execute(require, module, module.exports, fetchImpl, URLSearchParams)
  return module.exports
}

function rawCard(id) {
  return {
    id,
    slug: `anuncio-${id}`,
    titulo: `Anuncio ${id}`,
    localizacao: { uf: "GO", estado: "Goias" },
    midias: [],
  }
}

const requests = []
const topIds = Array.from({ length: 33 }, (_, index) => `topo-${index + 1}`)
const freeIds = Array.from({ length: 50 }, (_, index) => `free-${index + 1}`)
// Synthetic server orders exercise transport and page slices; backend business
// rules remain covered by the existing repository and backend tests.
const rotate = (items, offset) => [...items.slice(offset), ...items.slice(0, offset)]
const controlledOrders = new Map([
  ["101", [...topIds, ...freeIds]],
  ["202", [...rotate(topIds, 5), ...rotate(freeIds, 7)]],
  ["303", [...rotate(topIds, 11), ...rotate(freeIds, 13)]],
  ["404", [...rotate(topIds, 17), ...rotate(freeIds, 19)]],
  ["9223372036854775807", [...topIds, ...freeIds]],
  ["-9223372036854775808", [...topIds, ...freeIds]],
])
let freshRequestCount = 0
const catalogApi = loadCatalogApi(async (url, init) => {
  const requestUrl = new URL(url)
  const requestedSeed = requestUrl.searchParams.get("ordemSeed")
  const effectiveSeed = requestedSeed ?? (++freshRequestCount === 1 ? "303" : "404")
  const order = controlledOrders.get(effectiveSeed)
  assert.ok(order, "fixture seed must have an explicit controlled order")
  const pagina = Number(requestUrl.searchParams.get("pagina") ?? 0)
  const tamanho = Number(requestUrl.searchParams.get("tamanho") ?? 20)
  requests.push({ requestUrl, init })
  return {
    ok: true,
    status: 200,
    json: async () => ({
      itens: order.slice(pagina * tamanho, (pagina + 1) * tamanho).map(rawCard),
      paginacao: {
        pagina,
        tamanho,
        totalItens: order.length,
        totalPaginas: Math.ceil(order.length / tamanho),
        ordemSeed: effectiveSeed,
      },
      categoria: null,
      localidade: { uf: "GO", estado: "Goias" },
      seo: {},
    }),
  }
})

const initial = await catalogApi.listarAnunciosPublicos("TODOS", "", 0, 16)
assert.equal(requests.at(-1).requestUrl.searchParams.has("ordemSeed"), false)
assert.equal(initial.paginacao.ordemSeed, "303")
assert.equal(initial.itens.length, 16)
assert.equal(initial.paginacao.totalPaginas, 6)
const freshVisit = await catalogApi.listarAnunciosPublicos("TODOS", "", 0, 16)
assert.equal(requests.at(-1).requestUrl.searchParams.has("ordemSeed"), false)
assert.equal(freshVisit.paginacao.ordemSeed, "404")
assert.notDeepEqual(initial.itens.map(({ id }) => id), freshVisit.itens.map(({ id }) => id))

const seed101First = await catalogApi.listarAnunciosPublicos("TODOS", "", 0, 16, "101")
const seed101Second = await catalogApi.listarAnunciosPublicos("TODOS", "", 0, 16, "101")
const seed202 = await catalogApi.listarAnunciosPublicos("TODOS", "", 0, 16, "202")
assert.deepEqual(
  seed101First.itens.map(({ id }) => id),
  seed101Second.itens.map(({ id }) => id),
)
assert.notDeepEqual(
  seed101First.itens.map(({ id }) => id),
  seed202.itens.map(({ id }) => id),
)
assert.equal(requests.at(-3).requestUrl.searchParams.get("ordemSeed"), "101")
assert.equal(requests.at(-2).requestUrl.searchParams.get("ordemSeed"), "101")
assert.equal(requests.at(-1).requestUrl.searchParams.get("ordemSeed"), "202")
assert.equal(requests.every(({ init }) => init.cache === "no-store"), true)

await catalogApi.listarPublicosPorEstado("go", 1, 20, "101")
await catalogApi.listarPublicosPorCidade("go", "goiania", 1, 20, "101")
await catalogApi.listarPublicosPorBairro("go", "goiania", "centro", 1, 20, "101")
assert.equal(
  requests.slice(-3).every(({ requestUrl }) => requestUrl.searchParams.get("ordemSeed") === "101"),
  true,
)

const scopes = [
  { path: "/anuncios", pageBase: 1, size: 16, fetchPage: (page, seed) => catalogApi.listarAnunciosPublicos("TODOS", "termo", page, 16, seed) },
  { path: "/acompanhantes/go", pageBase: 0, size: 20, fetchPage: (page, seed) => catalogApi.listarPublicosPorEstado("go", page, 20, seed) },
  { path: "/acompanhantes/go/goiania", pageBase: 0, size: 20, fetchPage: (page, seed) => catalogApi.listarPublicosPorCidade("go", "goiania", page, 20, seed) },
  { path: "/acompanhantes/go/goiania/centro", pageBase: 0, size: 20, fetchPage: (page, seed) => catalogApi.listarPublicosPorBairro("go", "goiania", "centro", page, 20, seed) },
]
for (const { path, pageBase, size, fetchPage } of scopes) {
  for (const seed of ["101", "202"]) {
    const seenIds = []
    let href = `${path}?ordemSeed=${seed}`
    let pagesVisited = 0
    while (href) {
      const url = new URL(href, "https://topsdojob.com")
      const pageIndex = publicUrl.parsePublicPage(url.searchParams.get("page") ?? undefined, pageBase)
      assert.equal(url.searchParams.get("page"), pagesVisited === 0 ? null : String(pagesVisited + pageBase))
      const parsedSeed = publicUrl.parsePublicOrderSeed(url.searchParams.get("ordemSeed"))
      const data = await fetchPage(pageIndex, parsedSeed)
      assert.equal(requests.at(-1).requestUrl.searchParams.get("pagina"), String(pagesVisited))
      assert.equal(data.paginacao.pagina, pagesVisited)
      assert.equal(data.paginacao.ordemSeed, seed)
      assert.deepEqual(data.itens.map(({ id }) => id), controlledOrders.get(seed).slice(pageIndex * size, (pageIndex + 1) * size))
      seenIds.push(...data.itens.map(({ id }) => id))
      pagesVisited += 1
      href = pagesVisited < data.paginacao.totalPaginas
        ? publicUrl.buildPublicPageHref(path, pagesVisited, data.paginacao.ordemSeed, {}, pageBase)
        : null
    }
    assert.ok(pagesVisited > 3)
    assert.equal(seenIds.length, 83)
    assert.equal(new Set(seenIds).size, 83, "page traversal must not duplicate cards")
    assert.deepEqual(seenIds, controlledOrders.get(seed), "page traversal must preserve every card in the response order")
    assert.ok(seenIds.slice(0, 33).every((id) => id.startsWith("topo-")))
    assert.ok(seenIds.slice(33).every((id) => id.startsWith("free-")))
  }
  for (const seed of ["9223372036854775807", "-9223372036854775808"]) {
    await fetchPage(1, seed)
    assert.equal(requests.at(-1).requestUrl.searchParams.get("ordemSeed"), seed)
  }
}

const jsx = (type, props) => ({ type, props })
function Link() {}
const listingComponent = loadTypeScript("src/components/anuncios/listagem-publica-paginada.tsx", {
  "next/link": Link,
  "react/jsx-runtime": { Fragment: Symbol("Fragment"), jsx, jsxs: jsx },
  "@/lib/seo/public-url": publicUrl,
  "./anuncio-card": function AnuncioCard() {},
})
function nodes(root, predicate) {
  if (Array.isArray(root)) return root.flatMap((child) => nodes(child, predicate))
  if (!root || typeof root !== "object") return []
  return [...(predicate(root) ? [root] : []), ...nodes(root.props?.children, predicate)]
}
for (const { path, fetchPage } of scopes.filter((scope) => scope.pageBase === 0)) {
  for (const pageIndex of [0, 1, 2, 4]) {
    const data = await fetchPage(pageIndex, "101")
    const tree = listingComponent.ListagemPublicaPaginada({
      caminhoBase: path,
      initialData: data,
      searchParams: { filter: ["com-local", "foto"], ordemSeed: "202" },
    })
    const links = nodes(tree, (node) => node.type === Link)
    const previous = links.find((link) => link.props.children === "Anterior")
    const next = links.find((link) => link.props.children === "Proxima")
    assert.equal(Boolean(previous), pageIndex > 0)
    assert.equal(Boolean(next), pageIndex < 4)
    for (const link of links) {
      const url = new URL(link.props.href, "https://topsdojob.com")
      assert.equal(url.pathname, path)
      assert.equal(url.searchParams.get("ordemSeed"), "101", "navigation uses the returned seed")
      assert.deepEqual(url.searchParams.getAll("filter"), ["com-local", "foto"])
      assert.equal(link.props.prefetch, false)
      assert.equal(link.props.onClick, undefined, "SSR navigation must reach the page and its metadata")
    }
    if (previous) assert.equal(new URL(previous.props.href, "https://topsdojob.com").searchParams.get("page"), pageIndex === 1 ? null : String(pageIndex - 1))
    if (next) assert.equal(new URL(next.props.href, "https://topsdojob.com").searchParams.get("page"), String(pageIndex + 1))
    assert.equal(links.find((link) => link.props["aria-current"] === "page").props.children, pageIndex + 1)
  }
}

assert.match(api, /ordemSeed: string/)
assert.match(api, /query\.set\('ordemSeed', ordemSeed\)/)
assert.match(api, /const dynamic = \{ cache: 'no-store' as const \}/)

assert.match(grid, /initialData\.paginacao\.ordemSeed/)
assert.match(grid, /setOrdemSeed\(null\)/)
assert.match(grid, /setOrdemSeed\(data\.paginacao\.ordemSeed\)/)
assert.match(grid, /ITENS_POR_PAGINA,\s+ordemSeed,/)
assert.match(grid, /initialRequestConsumedRef/)

assert.match(paginatedListing, /const ordemSeed = initialData\.paginacao\.ordemSeed/)
assert.match(paginatedListing, /buildPublicPageHref/)
assert.doesNotMatch(paginatedListing, /event\.preventDefault\(\)|listarPublicosPor|useEffect|useState/)
assert.doesNotMatch(paginatedListing, /history\.(pushState|replaceState)/)
assert.doesNotMatch(paginatedListing, /localStorage|sessionStorage|document\.cookie/)

for (const page of [statePage, cityPage, neighborhoodPage]) {
  assert.match(page, /<ListagemPublicaPaginada/)
  assert.match(page, /initialData=\{data\}/)
  assert.match(page, /parsePublicOrderSeed/)
  assert.match(page, /searchParams=\{query\}/)
}

for (const file of [api, grid, paginatedListing, listingPage, statePage, cityPage, neighborhoodPage, sitemap]) {
  assert.doesNotMatch(file, /Math\.random|\.sort\(\s*\(\)\s*=>/)
}
for (const seoSource of [listingPage, statePage, cityPage, neighborhoodPage, sitemap]) {
  assert.doesNotMatch(seoSource, /[?&]ordemSeed/)
  assert.doesNotMatch(seoSource, /buildPublicUrl\([^)]*ordemSeed/)
}

console.log("PUBLIC_ORDERING_SEED_RESULT=OK")
