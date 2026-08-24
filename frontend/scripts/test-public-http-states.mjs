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
    "next/navigation": { notFound: () => { throw new NotFoundSignal() } },
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

function publicUrl(path) {
  return `https://topsdojob.com${path}`
}

function publicPath(...parts) {
  return `/${parts.map((part) => String(part).replace(/^\/+|\/+$/g, "")).join("/")}`
}

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
      "@/lib/seo/public-url": { buildPublicUrl: publicUrl },
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
        "@/lib/seo/public-url": {
          buildPublicPath: publicPath,
          buildPublicUrl: publicUrl,
        },
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
      "@/lib/seo/public-url": {
        buildPublicPath: publicPath,
        buildPublicUrl: publicUrl,
      },
      "./blog-post-page-client": function BlogPostPageClient() {},
    },
  })
  return { module, calls: () => calls }
}

function anunciosModule(listarAnunciosPublicos) {
  return loadTypeScriptModule("src/app/(public-routes)/anuncios/page.tsx", {
    dependencies: {
      "./anuncios-page-client": function AnunciosPageClient() {},
      "@/lib/seo/public-url": { buildPublicUrl: publicUrl },
      "@/lib/seo/search-indexing-policy": {
        buildPublicListingIndexingDecision: () => ({ indexable: true, canonicalQuery: "" }),
        buildPublicRobotsMetadata: robots,
      },
      "@/lib/public-catalog-api": {
        isPublicCatalogNotFound: (error) => error?.status === 400 || error?.status === 404,
        listarAnunciosPublicos,
      },
    },
  })
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
      ordemSeed: "seed-controlada",
    },
  }
}

function sitemapModule(fetchImpl) {
  return loadTypeScriptModule("src/app/sitemap.ts", {
    environment: {
      NEXT_PUBLIC_API_URL: "https://api.example.invalid/api/public",
    },
    fetchImpl,
    dependencies: {
      "@/lib/blog-api": { PUBLIC_BLOG_CACHE_TAG: "public-blog" },
      "@/lib/public-catalog-api": {
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
      "@/lib/seo/public-url": {
        buildPublicPath: publicPath,
        buildPublicUrl: publicUrl,
        getPublicSiteBaseUrl: () => "https://topsdojob.com",
      },
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
    assert.ok(urls.includes("https://topsdojob.com/acompanhantes/GO/goiania"))
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

assert.equal(executed, 20)
console.log(`PUBLIC_HTTP_STATES_RESULT=OK tests=${executed}`)
