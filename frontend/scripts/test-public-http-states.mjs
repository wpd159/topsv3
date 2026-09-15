import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import ts from "typescript"

function source(relativePath) {
  return readFileSync(new URL(`../${relativePath}`, import.meta.url), "utf8")
}

class NotFoundSignal extends Error {
  constructor() {
    super("NEXT_NOT_FOUND")
    this.name = "NotFoundSignal"
  }
}

class RedirectSignal extends Error {
  constructor(location) {
    super("NEXT_REDIRECT")
    this.location = location
    this.status = 308
  }
}

class ContractError extends Error {
  constructor(status) {
    super(`contract-${status}`)
    this.name = "ApiContractError"
    this.status = status
  }
}

function memoize(fn) {
  const values = new Map()
  return (...args) => {
    const key = JSON.stringify(args)
    if (!values.has(key)) values.set(key, fn(...args))
    return values.get(key)
  }
}

const jsxRuntime = {
  Fragment: Symbol("Fragment"),
  jsx: (type, props, key) => ({ type, props, key }),
  jsxs: (type, props, key) => ({ type, props, key }),
}

function loadTypeScriptModule(
  relativePath,
  { dependencies = {}, environment = {}, fetchImpl = globalThis.fetch } = {},
) {
  const { outputText } = ts.transpileModule(source(relativePath), {
    compilerOptions: {
      esModuleInterop: true,
      jsx: ts.JsxEmit.ReactJSX,
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
  })
  const module = { exports: {} }
  const defaults = {
    react: { cache: memoize },
    "react/jsx-runtime": jsxRuntime,
    "next/link": function Link() {},
    "next/navigation": {
      notFound: () => { throw new NotFoundSignal() },
      permanentRedirect: (location) => { throw new RedirectSignal(location) },
    },
    "@heroicons/react/24/solid": { ArrowLeftIcon() {}, ArrowRightIcon() {} },
  }
  const requireModule = (specifier) => {
    if (Object.hasOwn(dependencies, specifier)) return dependencies[specifier]
    if (Object.hasOwn(defaults, specifier)) return defaults[specifier]
    throw new Error(`Import inesperado em ${relativePath}: ${specifier}`)
  }

  new Function(
    "module",
    "exports",
    "require",
    "process",
    "fetch",
    "AbortSignal",
    outputText,
  )(
    module,
    module.exports,
    requireModule,
    { env: environment },
    fetchImpl,
    AbortSignal,
  )
  return module.exports
}

const policyModule = loadTypeScriptModule("src/lib/seo/search-indexing-policy.ts", {
  environment: { SEARCH_INDEXING_MODE: "public", NEXT_PUBLIC_SITE_URL: "https://topsdojob.com" },
})
const publicUrlModule = loadTypeScriptModule("src/lib/seo/public-url.ts", {
  dependencies: { "@/lib/seo/search-indexing-policy": policyModule },
  environment: { NEXT_PUBLIC_SITE_URL: "https://topsdojob.com" },
})

function robots(index) {
  return { index, follow: true }
}

function publishedPost(overrides = {}) {
  return {
    id: "post-1",
    titulo: "Post publicado",
    slug: "post-publicado",
    resumo: "Resumo editorial publicado.",
    conteudo: "Conteudo editorial seguro.",
    categoria: "Seguranca",
    categoriaId: "cat-1",
    categoriaSlug: "seguranca",
    autorNome: "Equipe editorial",
    status: "PUBLICADO",
    seoTitle: "Post publicado",
    seoDescription: "Resumo editorial publicado.",
    sitemapPriority: 0.7,
    changeFrequency: "weekly",
    versao: 1,
    ...overrides,
  }
}

function category(overrides = {}) {
  return {
    id: "cat-1",
    nome: "Seguranca",
    slug: "seguranca",
    ordem: 1,
    ativa: true,
    postCountPublicados: 1,
    versao: 1,
    ...overrides,
  }
}

function blogHomeModule({ posts, categories = [category()], failure = null }) {
  let postCalls = 0
  let categoryCalls = 0
  const module = loadTypeScriptModule("src/app/(public-routes)/blog/page.tsx", {
    dependencies: {
      "@/components/blog/blog-content": { BlogContent() {} },
      "@/lib/blog-api": {
        fetchPublicBlogPosts: async () => {
          postCalls += 1
          if (failure) throw failure
          return posts
        },
        fetchPublicBlogCategorias: async () => {
          categoryCalls += 1
          return categories
        },
      },
      "@/lib/seo/public-url": publicUrlModule,
      "@/lib/seo/search-indexing-policy": { buildPublicRobotsMetadata: robots },
    },
  })
  return { module, calls: () => ({ postCalls, categoryCalls }) }
}

function blogCategoryModule({ categories, posts = [], failure = null }) {
  let categoryCalls = 0
  let postCalls = 0
  const module = loadTypeScriptModule(
    "src/app/(public-routes)/blog/categoria/[slug]/page.tsx",
    {
      dependencies: {
        "@/lib/api-contract": { ApiContractError: ContractError },
        "@/lib/blog-api": {
          fetchPublicBlogCategorias: async () => {
            categoryCalls += 1
            return categories
          },
          fetchPublicBlogPostsByCategoria: async () => {
            postCalls += 1
            if (failure) throw failure
            return posts
          },
        },
        "@/lib/public-site-assets": { getPublicLogoUrl: () => "/logo.png" },
        "@/lib/seo/public-url": publicUrlModule,
        "@/lib/seo/search-indexing-policy": { buildPublicRobotsMetadata: robots },
      },
    },
  )
  return { module, calls: () => ({ categoryCalls, postCalls }) }
}

function blogPostModule(result) {
  let calls = 0
  const module = loadTypeScriptModule("src/app/(public-routes)/blog/[slug]/page.tsx", {
    dependencies: {
      "@/lib/api-contract": { ApiContractError: ContractError },
      "@/lib/blog-api": {
        fetchPublicBlogPost: async () => {
          calls += 1
          if (result instanceof Error) throw result
          return result
        },
      },
      "@/lib/public-site-assets": { getPublicLogoUrl: () => "/logo.png" },
      "@/lib/seo/json-ld": { serializeJsonLd: JSON.stringify },
      "@/lib/seo/public-url": publicUrlModule,
      "./blog-post-page-client": function BlogPostPageClient() {},
    },
  })
  return { module, calls: () => calls }
}

function anunciosModule(listarAnunciosPublicos) {
  return loadTypeScriptModule("src/app/(public-routes)/anuncios/page.tsx", {
    dependencies: {
      "./anuncios-page-client": function AnunciosPageClient() {},
      "@/lib/seo/public-url": publicUrlModule,
      "@/lib/seo/search-indexing-policy": policyModule,
      "@/lib/public-catalog-server-api": {
        isPublicCatalogNotFound: (error) => error?.status === 400 || error?.status === 404,
        listarAnunciosPublicos,
      },
    },
  })
}

const geographicScopes = [
  { route: "[estado]", path: "/acompanhantes/go", method: "listarPublicosPorEstado", segments: ["go"] },
  { route: "[estado]/[cidade]", path: "/acompanhantes/go/goiania", method: "listarPublicosPorCidade", segments: ["go", "goiania"] },
  { route: "[estado]/[cidade]/[bairro]", path: "/acompanhantes/go/goiania/centro", method: "listarPublicosPorBairro", segments: ["go", "goiania", "centro"] },
]
const geographicDependencies = {
  "@/lib/seo/public-url": publicUrlModule,
  "@/lib/seo/search-indexing-policy": policyModule,
  "@/components/stories/stories-bar": { StoriesBar() {} },
  "@/lib/media/public-media": { selecionarCapaPublicaSegura: () => null, fontePublicaSegura: () => null },
}
for (const name of ["text/encoding", "seo/local-labels", "seo/local-indexing", "seo/public-metadata", "seo/programmatic-content", "seo/cidadeSeo", "seo/seoContentGeneratorBairro", "seo/acompanhantes-navigation", "seo/json-ld"]) {
  geographicDependencies[`@/lib/${name}`] = loadTypeScriptModule(`src/lib/${name}.ts`, {
    dependencies: geographicDependencies,
  })
}
const geographicListing = loadTypeScriptModule("src/components/anuncios/listagem-publica-paginada.tsx", {
  dependencies: { "@/lib/seo/public-url": publicUrlModule, "./anuncio-card": function AnuncioCard() {} },
})
geographicDependencies["@/components/anuncios/listagem-publica-paginada"] = geographicListing

function localityModule(scope) {
  const requests = []
  const indexacao = { indexavel: true, canonica: true, motivo: "INVENTARIO_SUFICIENTE", anunciosElegiveisUnicos: 83, minimoNecessario: 5 }
  const aggregate = {
    estadoUf: "GO", estadoNome: "Goiás", cidadeNome: "Goiânia", cidadeSlug: "goiania", totalAnunciosAtivos: 83, indexacao,
    bairros: [{ bairroNome: "Centro", bairroSlug: "centro", indexacao }], categoriasPrincipais: [], cidadesRelacionadas: [],
  }
  const module = loadTypeScriptModule(`src/app/(public-routes)/acompanhantes/${scope.route}/page.tsx`, {
    dependencies: {
      ...geographicDependencies,
      "@/lib/public-catalog-server-api": {
        isPublicCatalogNotFound: (error) => error?.status === 400 || error?.status === 404,
        descobrirLocalidadesPublicas: async () => ({ estados: [{ uf: "GO", indexacao, cidades: [] }] }),
        obterAgregadoPublicoCidade: async () => aggregate,
        [scope.method]: async (...args) => {
          requests.push(args)
          const [pagina, tamanho, ordemSeed] = args.slice(scope.segments.length)
          return {
            itens: Array.from({ length: Math.max(0, Math.min(tamanho, 83 - pagina * tamanho)) }, (_, index) => ({ id: `teste-${pagina * tamanho + index}`, slug: `teste-${pagina * tamanho + index}`, titulo: "Anúncio sintético", midias: [] })),
            localidade: { uf: "GO", estado: "Goiás", cidade: "Goiânia", bairro: "Centro" },
            paginacao: { pagina, tamanho, totalItens: 83, totalPaginas: 5, ordemSeed: ordemSeed ?? "123" },
          }
        },
      },
    },
  })
  return { module, requests }
}

function elementNodes(root, predicate) {
  if (Array.isArray(root)) return root.flatMap((child) => elementNodes(child, predicate))
  if (!root || typeof root !== "object") return []
  return [...(predicate(root) ? [root] : []), ...elementNodes(root.props?.children, predicate)]
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

function sitemapModule(fetchImpl) {
  const editorial = async (path) => {
    const response = await fetchImpl(path)
    if (!response.ok) throw new ContractError(response.status)
    return response.json()
  }
  return loadTypeScriptModule("src/app/sitemap.ts", {
    dependencies: {
      "@/lib/blog-api": {
        fetchPublicBlogSitemap: () => editorial("blog-posts"),
        fetchPublicBlogCategorias: () => editorial("blog-categorias"),
      },
      "@/lib/public-catalog-server-api": {
        descobrirLocalidadesPublicas: async () => ({
          estados: [{
            uf: "GO",
            ultimaAtualizacao: "2026-08-20T10:00:00Z",
            indexacao: { indexavel: true, canonica: true },
            cidades: [{
              slug: "goiania",
              ultimaAtualizacao: "2026-08-20T10:00:00Z",
              indexacao: { indexavel: true, canonica: true },
              bairros: [],
            }],
          }],
        }),
        descobrirAnunciosIndexaveisSitemap: async () => [{
          slug: "anuncio-publico",
          atualizadoEm: "2026-08-20T10:00:00Z",
        }],
      },
      "@/lib/seo/public-url": publicUrlModule,
      "@/lib/seo/search-indexing-policy": {
        isSafeSitemapUrl: () => true,
        resolveSearchIndexingPolicy: () => ({ sitemapEnabled: true }),
      },
    },
  })
}

let executed = 0
async function test(name, fn) {
  await fn()
  executed += 1
  process.stdout.write(`ok ${executed} - ${name}\n`)
}

await test("blog vazio recebe noindex e reutiliza a carga", async () => {
  const { module, calls } = blogHomeModule({ posts: [] })
  const metadata = await module.generateMetadata()
  await module.default()
  assert.equal(metadata.robots.index, false)
  assert.equal(metadata.robots.follow, true)
  assert.deepEqual(calls(), { postCalls: 1, categoryCalls: 1 })
})

await test("blog com post publicado recebe index", async () => {
  const { module } = blogHomeModule({ posts: [publishedPost()] })
  assert.equal((await module.generateMetadata()).robots.index, true)
})

await test("falha do blog e propagada", async () => {
  const failure = new ContractError(503)
  const { module } = blogHomeModule({ posts: [], failure })
  await assert.rejects(module.generateMetadata(), (error) => error === failure)
})

await test("categoria existente com posts recebe index e usa nome real", async () => {
  const { module, calls } = blogCategoryModule({
    categories: [category()],
    posts: [publishedPost()],
  })
  const metadata = await module.generateMetadata({ params: Promise.resolve({ slug: "seguranca" }) })
  await module.default({ params: Promise.resolve({ slug: "seguranca" }) })
  assert.equal(metadata.robots.index, true)
  assert.match(metadata.title, /Seguranca/)
  assert.deepEqual(calls(), { categoryCalls: 1, postCalls: 1 })
})

await test("categoria existente vazia recebe noindex", async () => {
  const { module } = blogCategoryModule({ categories: [category()], posts: [] })
  const metadata = await module.generateMetadata({ params: Promise.resolve({ slug: "seguranca" }) })
  assert.equal(metadata.robots.index, false)
  assert.equal(metadata.robots.follow, true)
})

await test("categoria inexistente executa notFound", async () => {
  const { module } = blogCategoryModule({ categories: [] })
  await assert.rejects(
    module.generateMetadata({ params: Promise.resolve({ slug: "inexistente" }) }),
    NotFoundSignal,
  )
})

await test("404 concorrente da categoria executa notFound", async () => {
  const { module } = blogCategoryModule({
    categories: [category()],
    failure: new ContractError(404),
  })
  await assert.rejects(
    module.default({ params: Promise.resolve({ slug: "seguranca" }) }),
    NotFoundSignal,
  )
})

await test("5xx da categoria e propagado", async () => {
  const failure = new ContractError(503)
  const { module } = blogCategoryModule({ categories: [category()], failure })
  await assert.rejects(
    module.default({ params: Promise.resolve({ slug: "seguranca" }) }),
    (error) => error === failure,
  )
})

await test("post publicado responde e reutiliza a carga", async () => {
  const { module, calls } = blogPostModule(publishedPost())
  await module.generateMetadata({ params: Promise.resolve({ slug: "post-publicado" }) })
  await module.default({ params: Promise.resolve({ slug: "post-publicado" }) })
  assert.equal(calls(), 1)
})

await test("post inexistente executa notFound", async () => {
  const { module } = blogPostModule(new ContractError(404))
  await assert.rejects(
    module.default({ params: Promise.resolve({ slug: "inexistente" }) }),
    NotFoundSignal,
  )
})

await test("post nao publicado executa notFound", async () => {
  const { module } = blogPostModule(publishedPost({ status: "ARQUIVADO" }))
  await assert.rejects(
    module.default({ params: Promise.resolve({ slug: "arquivado" }) }),
    NotFoundSignal,
  )
})

await test("5xx do post e propagado", async () => {
  const failure = new ContractError(503)
  const { module } = blogPostModule(failure)
  await assert.rejects(
    module.default({ params: Promise.resolve({ slug: "post" }) }),
    (error) => error === failure,
  )
})

await test("programatico pendente produz 404", async () => {
  const module = loadTypeScriptModule(
    "src/app/(public-routes)/blog/cidade/[tema]/[cidade]/page.tsx",
  )
  await assert.rejects(async () => module.default(), NotFoundSignal)
})

await test("catalogo vazio legitimo continua renderizavel", async () => {
  const module = anunciosModule(async () => emptyCatalog())
  const result = await module.default({ searchParams: Promise.resolve({}) })
  assert.ok(result)
})

await test("categoria invalida do catalogo produz 404", async () => {
  const module = anunciosModule(async () => { throw new ContractError(400) })
  await assert.rejects(
    module.default({
      searchParams: Promise.resolve({ categoria: "CATEGORIA_INEXISTENTE" }),
    }),
    NotFoundSignal,
  )
})

await test("5xx do catalogo e propagado", async () => {
  const failure = new ContractError(503)
  const module = anunciosModule(async () => { throw failure })
  await assert.rejects(
    module.default({ searchParams: Promise.resolve({}) }),
    (error) => error === failure,
  )
})

await test("catalogo sem seed solicita nova ordem e preserva resposta SSR", async () => {
  const requests = []
  const catalog = emptyCatalog()
  const module = anunciosModule(async (...args) => { requests.push(args); return catalog })
  const rendered = await module.default({ searchParams: Promise.resolve({}) })
  assert.deepEqual(requests, [["TODOS", "", 0, 16, undefined, ""]])
  assert.equal(rendered.props.initialData, catalog)
  assert.equal(rendered.props.initialRequest.currentPage, 1)
  assert.equal(rendered.props.initialRequest.requestedSeed, undefined)
  const metadata = await module.generateMetadata({ searchParams: Promise.resolve({}) })
  assert.equal(metadata.robots.index, true)
  assert.equal(metadata.alternates.canonical, "https://topsdojob.com/anuncios")
})

await test("catalogo rejeita paginas invalidas antes de consultar API", async () => {
  let requests = 0
  const module = anunciosModule(async () => { requests += 1; return emptyCatalog() })
  for (const page of ["0", "", "-1", "1.5", "01", "abc", "9007199254740992", ["2", "3"]]) {
    await assert.rejects(module.default({ searchParams: Promise.resolve({ page }) }), NotFoundSignal)
    const metadata = await module.generateMetadata({ searchParams: Promise.resolve({ page }) })
    assert.equal(metadata.robots.index, false)
    assert.equal(metadata.robots.follow, true)
    assert.equal(metadata.alternates, undefined)
  }
  assert.equal(requests, 0)
})

await test("catalogo rejeita seed invalida sem normalizar erro para sucesso", async () => {
  let requests = 0
  const module = anunciosModule(async () => { requests += 1; return emptyCatalog() })
  for (const ordemSeed of ["", " ", "abc", "1.5", "1e3", "9223372036854775808", "-9223372036854775809", ["1", "2"]]) {
    await assert.rejects(module.default({ searchParams: Promise.resolve({ ordemSeed }) }), NotFoundSignal)
    const metadata = await module.generateMetadata({ searchParams: Promise.resolve({ ordemSeed }) })
    assert.equal(metadata.robots.index, false)
    assert.equal(metadata.alternates, undefined)
  }
  assert.equal(requests, 0)
})

await test("catalogo preserva precisao Java Long e normaliza somente representacao decimal", async () => {
  for (const [requested, expected] of [["-000123", "-123"], ["9223372036854775807", "9223372036854775807"], ["-9223372036854775808", "-9223372036854775808"]]) {
    const requests = []
    const module = anunciosModule(async (...args) => { requests.push(args); return emptyCatalog() })
    const rendered = await module.default({ searchParams: Promise.resolve({ ordemSeed: requested }) })
    assert.equal(requests[0][4], expected)
    assert.equal(rendered.props.initialRequest.requestedSeed, expected)
    const metadata = await module.generateMetadata({ searchParams: Promise.resolve({ ordemSeed: requested }) })
    assert.equal(metadata.robots.index, false)
    assert.equal(metadata.alternates.canonical, "https://topsdojob.com/anuncios")
  }
})

await test("catalogo fora do intervalo produz 404", async () => {
  for (const totalPaginas of [0, 1, 3]) {
    const requests = []
    const page = String(Math.max(2, totalPaginas + 1))
    const module = anunciosModule(async (...args) => {
      requests.push(args)
      return { ...emptyCatalog(), paginacao: { ...emptyCatalog().paginacao, totalPaginas } }
    })
    await assert.rejects(module.default({ searchParams: Promise.resolve({ page, ordemSeed: "123" }) }), NotFoundSignal)
    assert.equal(requests[0][2], Number(page) - 1)
    assert.equal(requests[0][4], "123")
  }
})

await test("page=1 do catalogo redireciona permanentemente sem consulta API", async () => {
  let requests = 0
  const module = anunciosModule(async () => { requests += 1; return emptyCatalog() })
  await assert.rejects(module.default({ searchParams: Promise.resolve({ page: "1" }) }), (error) => {
    assert.ok(error instanceof RedirectSignal)
    assert.equal(error.status, 308)
    assert.equal(error.location, "/anuncios")
    return true
  })
  assert.equal(requests, 0)
})

for (const scope of geographicScopes) {
  await test(`${scope.path}: ausente e page=0/1/2 preservam API, canonical e navegacao zero-based sem redirect`, async () => {
    for (const pageValue of [undefined, "0", "1", "2"]) {
      const { module, requests } = localityModule(scope)
      const pageIndex = pageValue === undefined ? 0 : Number(pageValue)
      const query = { page: pageValue, ordemSeed: "9007199254740993", filter: ["com-local", "foto"] }
      const props = { params: Promise.resolve({ estado: "go", cidade: "goiania", bairro: "centro" }), searchParams: Promise.resolve(query) }
      const metadata = await module.generateMetadata(props)
      const tree = await module.default(props)
      assert.deepEqual(requests, [[...scope.segments, pageIndex, 20, "9007199254740993"]])
      const canonical = `https://topsdojob.com${scope.path}${pageIndex > 0 ? `?page=${pageIndex}` : ""}`
      assert.equal(metadata.alternates.canonical, canonical)
      assert.equal(metadata.openGraph.url, canonical)
      assert.equal(metadata.robots.index, pageValue === undefined)
      assert.equal(metadata.robots.follow, true)
      if (pageIndex > 0) assert.match(metadata.title, new RegExp(`Página ${pageIndex + 1}`))
      const previous = elementNodes(tree, (node) => node.type === "link" && node.props.rel === "prev")
      const next = elementNodes(tree, (node) => node.type === "link" && node.props.rel === "next")
      assert.equal(previous.length, pageIndex > 0 ? 1 : 0)
      if (previous.length) assert.equal(previous[0].props.href, `https://topsdojob.com${scope.path}${pageIndex > 1 ? `?page=${pageIndex - 1}` : ""}`)
      assert.equal(next.length, 1)
      assert.equal(next[0].props.href, `https://topsdojob.com${scope.path}?page=${pageIndex + 1}`)
      const listing = elementNodes(tree, (node) => node.type === geographicListing.ListagemPublicaPaginada)
      assert.equal(listing.length, 1)
      assert.equal(listing[0].props.initialData.paginacao.pagina, pageIndex)
      assert.equal(listing[0].props.searchParams, query)
      const navigation = geographicListing.ListagemPublicaPaginada(listing[0].props)
      const nextLink = elementNodes(navigation, (node) => node.props?.children === "Proxima")[0]
      const nextUrl = new URL(nextLink.props.href, "https://topsdojob.com")
      assert.equal(nextUrl.pathname, scope.path)
      assert.equal(nextUrl.searchParams.get("page"), String(pageIndex + 1))
      assert.equal(nextUrl.searchParams.get("ordemSeed"), "9007199254740993")
      assert.deepEqual(nextUrl.searchParams.getAll("filter"), ["com-local", "foto"])
      assert.equal(nextLink.props.onClick, undefined)
      assert.equal(nextLink.props.prefetch, false)
    }
  })
}

await test("blog vazio fica fora do sitemap", async () => {
  const module = sitemapModule(async (url) => ({
    ok: true,
    status: 200,
    json: async () => url.includes("blog-posts") ? [] : [category({ postCountPublicados: 0 })],
  }))
  const urls = (await module.default()).map((entry) => entry.url)
  assert.equal(urls.includes("https://topsdojob.com/blog"), false)
})

await test("sitemap inclui somente editorial publicado e categoria nao vazia", async () => {
  const module = sitemapModule(async (url) => ({
    ok: true,
    status: 200,
    json: async () => url.includes("blog-posts")
      ? [{ slug: "post-publicado", priority: 0.7 }]
      : [
          category(),
          category({ id: "cat-2", slug: "vazia", postCountPublicados: 0 }),
          category({ id: "cat-3", slug: "inativa", ativa: false }),
        ],
  }))
  const urls = (await module.default()).map((entry) => entry.url)
  assert.ok(urls.includes("https://topsdojob.com/blog"))
  assert.ok(urls.includes("https://topsdojob.com/blog/post-publicado"))
  assert.ok(urls.includes("https://topsdojob.com/blog/categoria/seguranca"))
  assert.equal(urls.includes("https://topsdojob.com/blog/categoria/vazia"), false)
  assert.equal(urls.includes("https://topsdojob.com/blog/categoria/inativa"), false)
})

await test("falha editorial preserva sitemap basico, localidades e anuncios", async () => {
  const module = sitemapModule(async () => ({ ok: false, status: 503 }))
  const logs = []
  const originalError = console.error
  console.error = (...args) => logs.push(args)
  try {
    const urls = (await module.default()).map((entry) => entry.url)
    assert.ok(urls.includes("https://topsdojob.com/"))
    assert.ok(urls.includes("https://topsdojob.com/acompanhantes/go/goiania"))
    assert.ok(urls.includes("https://topsdojob.com/anuncios/anuncio-publico"))
    assert.equal(urls.some((url) => url.includes("/blog")), false)
  } finally {
    console.error = originalError
  }
  assert.equal(logs.length, 1)
  assert.equal(JSON.stringify(logs).includes("api.example.invalid"), false)
})

await test("fontes nao mascaram falhas nem expõem contrato programatico", async () => {
  const blogPage = source("src/app/(public-routes)/blog/page.tsx")
  const categoryPage = source("src/app/(public-routes)/blog/categoria/[slug]/page.tsx")
  const programmaticPage = source("src/app/(public-routes)/blog/cidade/[tema]/[cidade]/page.tsx")
  const listingPage = source("src/app/(public-routes)/anuncios/page.tsx")
  const sitemap = source("src/app/sitemap.ts")
  const rootLayout = source("src/app/layout.tsx")
  const postPage = source("src/app/(public-routes)/blog/[slug]/page.tsx")

  assert.match(blogPage, /cache\(async/)
  assert.doesNotMatch(categoryPage, /ContractState|fetchProgrammatic|titleCaseFromSlug/)
  assert.match(programmaticPage, /notFound\(\)/)
  assert.doesNotMatch(programmaticPage, /ContractState|contentHtml|generateMetadata/)
  assert.doesNotMatch(listingPage, /initialData\s*=\s*null|catch\s*\{/)
  assert.match(listingPage, /throw error/)
  assert.doesNotMatch(sitemap, /blog-programmatic|dynamicProgBlogRoutes/)
  assert.doesNotMatch(sitemap, /throw error/)
  assert.doesNotMatch(rootLayout, /SearchAction|potentialAction/)
  assert.match(postPage, /serializeJsonLd/)
  assert.doesNotMatch(sitemap, /seed|ordemseed/i)
})

assert.equal(executed, 29)
console.log(`PUBLIC_HTTP_STATES_RESULT=OK tests=${executed}`)
