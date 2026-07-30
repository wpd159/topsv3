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
const controlledOrders = new Map([
  ["101", ["a", "b", "c", "d"]],
  ["202", ["c", "a", "d", "b"]],
])
const catalogApi = loadCatalogApi(async (url, init) => {
  const requestUrl = new URL(url)
  const requestedSeed = requestUrl.searchParams.get("ordemSeed")
  const effectiveSeed = requestedSeed ?? "303"
  const order = controlledOrders.get(effectiveSeed) ?? ["b", "d", "a", "c"]
  requests.push({ requestUrl, init })
  return {
    ok: true,
    status: 200,
    json: async () => ({
      itens: order.map(rawCard),
      paginacao: {
        pagina: Number(requestUrl.searchParams.get("pagina") ?? 0),
        tamanho: Number(requestUrl.searchParams.get("tamanho") ?? 20),
        totalItens: order.length,
        totalPaginas: 1,
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

assert.match(api, /ordemSeed: string/)
assert.match(api, /query\.set\('ordemSeed', ordemSeed\)/)
assert.match(api, /const dynamic = \{ cache: 'no-store' as const \}/)

assert.match(grid, /initialData\.paginacao\.ordemSeed/)
assert.match(grid, /setOrdemSeed\(null\)/)
assert.match(grid, /setOrdemSeed\(data\.paginacao\.ordemSeed\)/)
assert.match(grid, /ITENS_POR_PAGINA,\s+ordemSeed,/)
assert.match(grid, /initialRequestConsumedRef/)

assert.match(paginatedListing, /const ordemSeed = initialData\.paginacao\.ordemSeed/)
assert.match(paginatedListing, /resposta\.paginacao\.ordemSeed !== ordemSeed/)
assert.match(paginatedListing, /idsPorPaginaRef/)
assert.match(paginatedListing, /event\.preventDefault\(\)/)
assert.doesNotMatch(paginatedListing, /history\.(pushState|replaceState)/)
assert.doesNotMatch(paginatedListing, /localStorage|sessionStorage|document\.cookie/)

for (const page of [statePage, cityPage, neighborhoodPage]) {
  assert.match(page, /<ListagemPublicaPaginada/)
  assert.match(page, /initialData=\{data\}/)
  assert.doesNotMatch(page, /ordemSeed=/)
}

for (const file of [api, grid, paginatedListing, listingPage, statePage, cityPage, neighborhoodPage, sitemap]) {
  assert.doesNotMatch(file, /Math\.random|\.sort\(\s*\(\)\s*=>/)
}
for (const seoSource of [listingPage, statePage, cityPage, neighborhoodPage, sitemap]) {
  assert.doesNotMatch(seoSource, /[?&]ordemSeed/)
  assert.doesNotMatch(seoSource, /buildPublicUrl\([^)]*ordemSeed/)
}

console.log("PUBLIC_ORDERING_SEED_RESULT=OK")
