import assert from "node:assert/strict"
import { existsSync, readFileSync } from "node:fs"
import ts from "typescript"
import * as jsxRuntime from "react/jsx-runtime"

import { EXPECTED_SCOPED_NOINDEX_ROUTES } from "./test-search-indexing-artifact.mjs"

function source(relativePath) {
  return readFileSync(new URL(`../${relativePath}`, import.meta.url), "utf8")
}

function loadTypeScriptModule(relativePath, processEnvironment = {}, dependencies = {}) {
  const { outputText } = ts.transpileModule(
    source(relativePath),
    {
      compilerOptions: {
        module: ts.ModuleKind.CommonJS,
        target: ts.ScriptTarget.ES2022,
        jsx: ts.JsxEmit.ReactJSX,
      },
    },
  )
  const module = { exports: {} }
  new Function("module", "exports", "process", "require", outputText)(
    module,
    module.exports,
    { env: processEnvironment },
    (specifier) => {
      if (Object.hasOwn(dependencies, specifier)) return dependencies[specifier]
      throw new Error(`Import inesperado em ${relativePath}: ${specifier}`)
    },
  )
  return module.exports
}

const policyModule = loadTypeScriptModule("src/lib/seo/search-indexing-policy.ts", {
  NEXT_PUBLIC_SITE_URL: "https://topsdojob.com",
  SEARCH_INDEXING_MODE: "public",
})
const publicUrlModule = loadTypeScriptModule(
  "src/lib/seo/public-url.ts",
  {},
  { "@/lib/seo/search-indexing-policy": policyModule },
)
const localIndexingModule = loadTypeScriptModule("src/lib/seo/local-indexing.ts")
const preprodComposeSource = source("../deploy/preprod/docker-compose.yml")
const {
  NEXT_NOINDEX_ROUTE_SOURCES,
  buildPublicListingIndexingDecision,
  buildPublicRobotsMetadata,
  buildSearchRobotsRules,
  isNonIndexableRoute,
  isSafeSitemapUrl,
  resolveSearchIndexingPolicy,
} = policyModule
const { isBairroIndexavelLocal, isCidadeIndexavelLocal } = localIndexingModule
const {
  buildPublicPageHref,
  buildPublicUrl,
  isCleanPublicFirstPage,
  isPublicPageOutOfRange,
  parsePublicOrderSeed,
  parsePublicPage,
} = publicUrlModule

assert.deepEqual(
  NEXT_NOINDEX_ROUTE_SOURCES,
  [...EXPECTED_SCOPED_NOINDEX_ROUTES.keys()],
  "artifact gate allowlist must match the current Next.js noindex sources",
)

const defaultBlocked = resolveSearchIndexingPolicy({})
assert.equal(defaultBlocked.mode, "blocked")
assert.equal(defaultBlocked.publicIndexingEnabled, false)
assert.equal(defaultBlocked.sitemapEnabled, false)

for (const emptyMode of ["", "   "]) {
  const emptyModePolicy = resolveSearchIndexingPolicy({
    SEARCH_INDEXING_MODE: emptyMode,
  })
  assert.equal(emptyModePolicy.mode, "blocked")
  assert.equal(emptyModePolicy.publicIndexingEnabled, false)
}

const blocked = resolveSearchIndexingPolicy({
  NEXT_PUBLIC_SITE_URL: "https://v3.esle.cloud",
  SEARCH_INDEXING_MODE: "blocked",
})
assert.equal(blocked.publicIndexingEnabled, false)
assert.equal(blocked.sitemapEnabled, false)
assert.deepEqual(buildSearchRobotsRules(blocked), [
  { userAgent: "*", disallow: ["/"] },
])

assert.throws(
  () =>
    resolveSearchIndexingPolicy({
      NEXT_PUBLIC_SITE_URL: "https://v3.esle.cloud",
      SEARCH_INDEXING_MODE: "public",
    }),
  /exige o dominio final/,
)
assert.throws(
  () =>
    resolveSearchIndexingPolicy({
      NEXT_PUBLIC_SITE_URL: "https://topsdojob.com",
      SEARCH_INDEXING_MODE: "enabled",
    }),
  /blocked ou public/,
)

const production = resolveSearchIndexingPolicy({
  NEXT_PUBLIC_SITE_URL: "https://topsdojob.com",
  SEARCH_INDEXING_MODE: "public",
})
assert.equal(production.mode, "public")
assert.equal(production.publicIndexingEnabled, true)
assert.equal(production.sitemapEnabled, true)
assert.equal(production.googlebotEnabled, true)
assert.equal(production.googleExtendedEnabled, true)
assert.equal(production.oaiSearchBotEnabled, true)
assert.equal(production.gptBotEnabled, true)
assert.equal(production.chatGptUserEnabled, true)
assert.equal(production.applebotEnabled, true)
assert.equal(production.bingbotEnabled, true)

const productionRules = buildSearchRobotsRules(production)
const rule = (userAgent) =>
  productionRules.find((candidate) => candidate.userAgent === userAgent)
for (const userAgent of [
  "Googlebot",
  "Googlebot-Image",
  "Googlebot-Video",
  "Google-Extended",
  "OAI-SearchBot",
  "GPTBot",
  "ChatGPT-User",
  "Applebot",
  "bingbot",
  "*",
]) {
  assert.deepEqual(rule(userAgent).allow, ["/"], `${userAgent} must access public routes`)
  assert.ok(rule(userAgent).disallow.includes("/admin"))
  assert.ok(rule(userAgent).disallow.includes("/api/*"))
  assert.ok(rule(userAgent).disallow.includes("/painel"))
  assert.notDeepEqual(rule(userAgent).disallow, ["/"])
  assert.equal(rule(userAgent).crawlDelay, undefined)
}

for (const userAgent of ["Googlebot", "OAI-SearchBot", "GPTBot", "ChatGPT-User", "*"]) {
  assert.equal(
    rule(userAgent).disallow.some((entry) =>
      /busca|search|filter|sort|categoria|utm_|gclid|fbclid/i.test(entry),
    ),
    false,
    `${userAgent} must be able to crawl public noindex variants`,
  )
}

assert.deepEqual(buildPublicListingIndexingDecision({}, 1), {
  indexable: true,
  canonicalQuery: "",
})
assert.deepEqual(buildPublicListingIndexingDecision({ page: "1" }, 1), {
  indexable: false,
  canonicalQuery: "",
})
assert.deepEqual(buildPublicListingIndexingDecision({}, 2), {
  indexable: false,
  canonicalQuery: "page=2",
})
assert.deepEqual(buildPublicListingIndexingDecision({ page: "3" }, 3), {
  indexable: false,
  canonicalQuery: "page=3",
})
assert.equal(parsePublicPage(undefined), 0)
assert.equal(parsePublicPage("0"), 0)
assert.equal(parsePublicPage("1"), 1)
assert.equal(parsePublicPage("2"), 2)
assert.equal(parsePublicPage("9007199254740991"), Number.MAX_SAFE_INTEGER)
assert.equal(parsePublicPage(undefined, 1), 0)
assert.equal(parsePublicPage("1", 1), 0)
assert.equal(parsePublicPage("2", 1), 1)
assert.equal(parsePublicPage("9007199254740991", 1), Number.MAX_SAFE_INTEGER - 1)
assert.equal(parsePublicPage("0", 1), null)
for (const invalidPage of ["", "-1", "abc", "1.5", "01", "+1", " 1", "1 ", "1e2", "9007199254740992", ["1"], ["2", "3"]]) {
  assert.equal(parsePublicPage(invalidPage), null)
  assert.equal(parsePublicPage(invalidPage, 1), null)
}
assert.equal(isCleanPublicFirstPage(undefined, 0), true)
assert.equal(isCleanPublicFirstPage("0", 0), false)
assert.equal(isCleanPublicFirstPage("1", 0), false)
assert.equal(isCleanPublicFirstPage("2", 1), false)
assert.equal(parsePublicOrderSeed(undefined), undefined)
for (const [value, normalized] of [
  ["0", "0"], ["-0", "0"], ["000123", "123"], ["-000123", "-123"],
  ["9007199254740993", "9007199254740993"],
  ["9223372036854775807", "9223372036854775807"],
  ["-9223372036854775808", "-9223372036854775808"],
]) {
  assert.equal(parsePublicOrderSeed(value), normalized)
}
for (const value of ["", " ", "123 ", "+1", "1.5", "1e2", "abc", "0x10", "--1", "9223372036854775808", "-9223372036854775809", ["1"], ["1", "2"]]) {
  assert.equal(parsePublicOrderSeed(value), null)
}
for (const path of ["/acompanhantes/sp", "/acompanhantes/sp/sao-paulo", "/acompanhantes/sp/sao-paulo/centro"]) {
  assert.equal(buildPublicUrl(path, 0), `https://topsdojob.com${path}`)
  assert.equal(buildPublicUrl(path, 1), `https://topsdojob.com${path}?page=1`)
  assert.equal(buildPublicUrl(path, 2), `https://topsdojob.com${path}?page=2`)
  assert.doesNotMatch(buildPublicUrl(path, 2), /seed/i)
}
for (const [path, pageBase] of [["/anuncios", 1], ["/acompanhantes/sp", 0], ["/acompanhantes/sp/sao-paulo", 0], ["/acompanhantes/sp/sao-paulo/centro", 0]]) {
  const filters = { page: "1", busca: "café 100%", categoria: "TODOS", ordemSeed: "123", filter: ["com-local", "foto"], utm_source: "teste", unused: undefined }
  const first = new URL(buildPublicPageHref(path, 0, undefined, filters, pageBase), "https://topsdojob.com")
  assert.equal(first.pathname, path)
  assert.equal(first.searchParams.has("page"), false)
  assert.equal(first.searchParams.get("busca"), "café 100%")
  assert.equal(first.searchParams.get("categoria"), "TODOS")
  assert.equal(first.searchParams.get("ordemSeed"), "123")
  assert.deepEqual(first.searchParams.getAll("filter"), ["com-local", "foto"])
  assert.equal(first.searchParams.get("utm_source"), "teste")
  assert.equal(first.searchParams.has("unused"), false)
  const next = new URL(buildPublicPageHref(path, 1, "9007199254740993", filters, pageBase), "https://topsdojob.com")
  assert.equal(next.searchParams.get("page"), String(1 + pageBase))
  assert.deepEqual(next.searchParams.getAll("ordemSeed"), ["9007199254740993"])
  assert.equal(next.searchParams.get("busca"), "café 100%")
  assert.deepEqual(next.searchParams.getAll("filter"), ["com-local", "foto"])
  assert.equal(filters.page, "1", "navigation must not mutate request filters")
  assert.equal(filters.ordemSeed, "123")
}
assert.equal(isPublicPageOutOfRange(1, { totalPaginas: 2 }), false)
assert.equal(isPublicPageOutOfRange(2, { totalPaginas: 2 }), true)
assert.equal(buildPublicRobotsMetadata(false).index, false)
assert.equal(buildPublicRobotsMetadata(false).follow, true)
assert.equal(buildPublicRobotsMetadata(true).index, true)
assert.equal(buildPublicRobotsMetadata(true).follow, true)
assert.deepEqual(
  buildPublicListingIndexingDecision({ busca: "goiania" }, 1),
  { indexable: false, canonicalQuery: "busca=goiania" },
)
assert.deepEqual(
  buildPublicListingIndexingDecision({ filter: "com-local", sort: "preco" }, 1),
  { indexable: false, canonicalQuery: "filter=com-local&sort=preco" },
)
assert.deepEqual(
  buildPublicListingIndexingDecision({ categoria: "ACOMPANHANTE_FEMININA" }, 1),
  { indexable: false, canonicalQuery: "categoria=ACOMPANHANTE_FEMININA" },
)
assert.deepEqual(
  buildPublicListingIndexingDecision(
    { utm_source: "newsletter", utm_campaign: "lancamento", gclid: "tracking" },
    1,
  ),
  { indexable: true, canonicalQuery: "" },
)
assert.deepEqual(
  buildPublicListingIndexingDecision({ utm_source: "newsletter" }, 2),
  { indexable: false, canonicalQuery: "page=2" },
)
assert.deepEqual(
  buildPublicListingIndexingDecision({ busca: "goiania", utm_source: "newsletter" }, 1),
  { indexable: false, canonicalQuery: "busca=goiania" },
)
assert.deepEqual(buildPublicListingIndexingDecision({ seed: "internal" }, 1), {
  indexable: false,
  canonicalQuery: "",
})
assert.deepEqual(buildPublicListingIndexingDecision({ seed: "" }, 1), {
  indexable: false,
  canonicalQuery: "",
})
assert.deepEqual(buildPublicListingIndexingDecision({ ordemSeed: "9223372036854775807" }, 1), {
  indexable: false,
  canonicalQuery: "",
})
assert.deepEqual(buildPublicListingIndexingDecision({ page: "2", ordemSeed: "123", busca: "teste" }, 2), {
  indexable: false,
  canonicalQuery: "page=2&busca=teste",
})

const belowCityThreshold = {
  indexacao: {
    indexavel: false,
    motivo: "INVENTARIO_INSUFICIENTE",
    anunciosElegiveisUnicos: 4,
    minimoNecessario: 5,
    canonica: true,
  },
}
const eligibleCity = {
  indexacao: {
    ...belowCityThreshold.indexacao,
    indexavel: true,
    motivo: "INVENTARIO_SUFICIENTE",
    anunciosElegiveisUnicos: 5,
  },
}
const belowNeighborhoodThreshold = {
  indexacao: {
    ...belowCityThreshold.indexacao,
    anunciosElegiveisUnicos: 2,
    minimoNecessario: 3,
  },
}
assert.equal(isCidadeIndexavelLocal(belowCityThreshold), false)
assert.equal(isCidadeIndexavelLocal(eligibleCity), true)
assert.equal(isBairroIndexavelLocal(belowNeighborhoodThreshold), false)
assert.equal(
  isBairroIndexavelLocal({
    indexacao: { ...belowNeighborhoodThreshold.indexacao, indexavel: true, anunciosElegiveisUnicos: 3 },
  }),
  true,
)

for (const path of [
  "/admin",
  "/api/public/anuncios",
  "/painel/performance",
  "/minha-conta",
  "/kyc/documento",
  "/preview/protegido",
]) {
  assert.equal(isNonIndexableRoute(path), true, `${path} must remain private`)
}
for (const source of [
  "/admin/:path*",
  "/api/:path*",
  "/painel/:path*",
  "/preview/:path*",
  "/registrar",
]) {
  assert.ok(
    NEXT_NOINDEX_ROUTE_SOURCES.includes(source),
    `${source} must keep its Next.js noindex header rule`,
  )
}
for (const path of [
  "/",
  "/anuncios",
  "/acompanhantes/go/goiania",
  "/blog/guia-seguro",
  "/contato",
  "/politica-de-privacidade",
]) {
  assert.equal(isNonIndexableRoute(path), false, `${path} must remain public`)
}
assert.equal(
  isSafeSitemapUrl("https://topsdojob.com/acompanhantes/go/goiania", production),
  true,
)
assert.equal(
  isSafeSitemapUrl("https://v3.esle.cloud/acompanhantes/go/goiania", production),
  false,
)
assert.equal(
  isSafeSitemapUrl("https://topsdojob.com/creditos", production),
  false,
)
assert.equal(
  isSafeSitemapUrl("https://topsdojob.com/anuncios?busca=qa", production),
  false,
)

const robotsSource = source("src/app/robots.ts")
const sitemapSource = source("src/app/sitemap.ts")
const rootLayoutSource = source("src/app/layout.tsx")
const listingPageSource = source("src/app/(public-routes)/anuncios/page.tsx")
const publicUrlSource = source("src/lib/seo/public-url.ts")
const statePageSource = source(
  "src/app/(public-routes)/acompanhantes/[estado]/page.tsx",
)
const cityPageSource = source(
  "src/app/(public-routes)/acompanhantes/[estado]/[cidade]/page.tsx",
)
const neighborhoodPageSource = source(
  "src/app/(public-routes)/acompanhantes/[estado]/[cidade]/[bairro]/page.tsx",
)
const localIndexingSource = source("src/lib/seo/local-indexing.ts")
const publicLayoutSource = source("src/app/(public-routes)/layout.tsx")
const homeSource = source("src/app/(public-routes)/page.tsx")
const acompanhantesLayoutSource = source(
  "src/app/(public-routes)/acompanhantes/layout.tsx",
)
const anunciosLayoutSource = source("src/app/(public-routes)/anuncios/layout.tsx")
const anunciosClientSource = source(
  "src/app/(public-routes)/anuncios/anuncios-page-client.tsx",
)
const publicStaticMetadataSource = source("src/lib/seo/public-static-metadata.ts")
const middlewareSource = source("src/middleware.ts")
const nextConfigSource = source("next.config.ts")
const preprodNginxSource = source("../deploy/preprod/nginx-preprod-local.conf")
const hmlNginxSource = source("../deploy/hml/nginx-v3-esle-cloud.conf")
const workflowSource = source("../.github/workflows/ci.yml")

assert.match(robotsSource, /buildSearchRobotsRules/)
assert.match(robotsSource, /policy\.sitemapEnabled/)
assert.match(sitemapSource, /if \(!indexingPolicy\.sitemapEnabled\) return \[\]/)
assert.doesNotMatch(sitemapSource, /`\$\{baseUrl\}\/creditos`/)
assert.match(sitemapSource, /estado\.indexacao\.indexavel/)
assert.match(sitemapSource, /cidade\.indexacao\.indexavel/)
assert.match(sitemapSource, /bairro\.indexacao\.indexavel/)
assert.doesNotMatch(sitemapSource, /isCidadeIndexavelLocal|isBairroIndexavelLocal/)
assert.match(sitemapSource, /isSafeSitemapUrl/)
assert.match(rootLayoutSource, /searchIndexingPolicy\.publicIndexingEnabled/)
assert.doesNotMatch(rootLayoutSource, /SearchAction|potentialAction/)
assert.match(listingPageSource, /buildPublicListingIndexingDecision/)
assert.match(listingPageSource, /url\.search = indexingDecision\.canonicalQuery/)
assert.match(listingPageSource, /buildPublicRobotsMetadata\(indexingDecision\.indexable/)
assert.match(listingPageSource, /parsePublicPage\(searchParams\.page, 1\)/)
assert.match(listingPageSource, /parsePublicOrderSeed\(searchParams\.ordemSeed\)/)
assert.match(statePageSource, /estadoDescoberto\.indexacao\.indexavel/)
assert.match(cityPageSource, /isCidadeIndexavelLocal\(agregado\)/)
assert.match(neighborhoodPageSource, /isBairroIndexavelLocal\(bairroAgregado\)/)
for (const localityPageSource of [statePageSource, cityPageSource, neighborhoodPageSource]) {
  assert.match(localityPageSource, /isCleanPublicFirstPage\(pageValue, page\)/)
  assert.match(localityPageSource, /if \(isPublicPageOutOfRange\(page, data\.paginacao\)\) notFound\(\)/)
}
assert.match(listingPageSource, /initialData\.paginacao\.totalPaginas[\s\S]*notFound\(\)/)
assert.match(publicUrlSource, /if \(value === ""\) return null/)
assert.match(publicUrlSource, /value === undefined && page === 0/)
assert.doesNotMatch(
  `${localIndexingSource}\n${sitemapSource}\n${cityPageSource}\n${neighborhoodPageSource}`,
  /shouldIndex\s*\|\||totalAnunciosAtivos\s*>\s*0|totalAnuncios\w*\s*>=\s*[0-9]+/,
)
assert.doesNotMatch(sitemapSource, /seed|ordemseed/i)
assert.doesNotMatch(sitemapSource, /[?&]page=/)
assert.match(publicStaticMetadataSource, /alternates: \{ canonical \}/)
assert.match(publicStaticMetadataSource, /openGraph:/)
assert.equal(
  (anunciosClientSource.match(/<h1\b/g) ?? []).length,
  1,
  "public listing must render one visible H1",
)
assert.match(nextConfigSource, /NEXT_NOINDEX_ROUTE_SOURCES/)
assert.match(nextConfigSource, /X-Robots-Tag/)
assert.doesNotMatch(
  middlewareSource,
  /Googlebot|Google-Extended|OAI-SearchBot|GPTBot|ChatGPT-User|Applebot|bingbot/i,
)

assert.doesNotMatch(publicLayoutSource, /rating:\s*["']adult["']/)
for (const adultSource of [homeSource, acompanhantesLayoutSource, anunciosLayoutSource]) {
  assert.equal(
    (adultSource.match(/rating:\s*["']adult["']/g) ?? []).length,
    1,
    "each adult route tree must declare the rating once",
  )
}
assert.equal(
  existsSync(new URL("../public/llms.txt", import.meta.url)),
  false,
  "this change must not create llms.txt",
)

for (const nginxSource of [preprodNginxSource, hmlNginxSource]) {
  assert.match(nginxSource, /X-Robots-Tag\s+"noindex, nofollow, noarchive"/)
  assert.match(nginxSource, /Disallow: \//)
}
assert.match(workflowSource, /SEARCH_INDEXING_MODE:\s*blocked/)
assert.match(nextConfigSource, /output:\s*["']standalone["']/)
assert.match(
  preprodComposeSource,
  /COPY --from=build --chown=node:node \/app\/\.next\/standalone \.\//,
)
assert.doesNotMatch(
  preprodComposeSource,
  /COPY --from=build \/app\/src\/lib\/seo\/search-indexing-policy\.ts/,
)

// Exercise the real institutional metadata/config/sitemap with synthetic API
// boundaries. Public and blocked modes must retain their existing policy.
for (const mode of ["public", "blocked"]) {
  const environment = {
    NEXT_PUBLIC_SITE_URL: "https://topsdojob.com",
    SEARCH_INDEXING_MODE: mode,
  }
  const policy = loadTypeScriptModule("src/lib/seo/search-indexing-policy.ts", environment)
  const urls = loadTypeScriptModule("src/lib/seo/public-url.ts", environment, {
    "@/lib/seo/search-indexing-policy": policy,
  })
  const staticMetadata = loadTypeScriptModule("src/lib/seo/public-static-metadata.ts", environment, {
    "@/lib/seo/public-url": urls,
  })
  const contact = loadTypeScriptModule("src/app/(public-routes)/contato/page.tsx", environment, {
    "@/lib/seo/public-static-metadata": staticMetadata,
    "./contato-page-client": { default: () => null },
    "react/jsx-runtime": jsxRuntime,
  })
  assert.equal(contact.metadata.title, "Contato e suporte | Tops do Job")
  assert.equal(contact.metadata.alternates.canonical, "https://topsdojob.com/contato")
  assert.equal(contact.metadata.openGraph.url, contact.metadata.alternates.canonical)
  assert.equal(contact.metadata.openGraph.description, contact.metadata.description)
  assert.equal(contact.metadata.robots, undefined, "contact inherits the existing global indexing policy")
  assert.doesNotMatch(source("src/app/(public-routes)/contato/page.tsx"), /['"]use client['"]/)
  assert.match(source("src/app/(public-routes)/contato/contato-page-client.tsx"), /open-suporte-ticket/)

  let apiCalls = 0
  const syntheticRead = async (value) => { apiCalls += 1; return value }
  const sitemapModule = loadTypeScriptModule("src/app/sitemap.ts", environment, {
    "@/lib/blog-api": {
      fetchPublicBlogCategorias: () => syntheticRead([]),
      fetchPublicBlogSitemap: () => syntheticRead([]),
    },
    "@/lib/public-catalog-server-api": {
      descobrirAnunciosIndexaveisSitemap: () => syntheticRead([]),
      descobrirLocalidadesPublicas: () => syntheticRead({ estados: [] }),
    },
    "@/lib/seo/public-url": urls,
    "@/lib/seo/search-indexing-policy": policy,
  })
  const entries = await sitemapModule.default()
  assert.equal(entries.filter(({ url }) => url === "https://topsdojob.com/contato").length, mode === "public" ? 1 : 0)
  assert.equal(entries.some(({ url }) => url.endsWith("/privacidade")), false, "alias must not enter the sitemap")
  if (mode === "blocked") {
    assert.deepEqual(entries, [])
    assert.equal(apiCalls, 0, "blocked sitemap must not fetch content")
  }

  const config = loadTypeScriptModule("next.config.ts", environment, {
    "./src/lib/seo/search-indexing-policy": policy,
  }).default
  const redirects = await config.redirects()
  assert.deepEqual(redirects.find(({ source }) => source === "/privacidade"), {
    source: "/privacidade", destination: "/politica-de-privacidade", permanent: true,
  })
  assert.equal(redirects.some(({ source, destination }) => source.startsWith("/blog/") || destination.startsWith("/blog/cidade/")), false,
    "removed programmatic pages must not have permanent redirects ending in 404")
  const privacy = loadTypeScriptModule("src/app/(public-routes)/politica-de-privacidade/page.tsx", environment, {
    "@/lib/seo/public-static-metadata": staticMetadata,
    "@/components/site-content/site-content-page": { SiteContentPage: () => null },
    "react/jsx-runtime": jsxRuntime,
  })
  assert.equal(privacy.metadata.alternates.canonical, "https://topsdojob.com/politica-de-privacidade")
}

console.log("SEARCH_INDEXING_POLICY_RESULT=OK")
