import assert from "node:assert/strict"
import { existsSync, readFileSync } from "node:fs"
import ts from "typescript"

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
  buildPublicListingIndexingDecision,
  buildPublicRobotsMetadata,
  buildSearchRobotsRules,
  isNonIndexableRoute,
  isSafeSitemapUrl,
  resolveSearchIndexingPolicy,
} = policyModule
const { isBairroIndexavelLocal, isCidadeIndexavelLocal } = localIndexingModule
const {
  buildPublicUrl,
  isCleanPublicFirstPage,
  isPublicPageOutOfRange,
  parsePublicPage,
} = publicUrlModule

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
for (const invalidPage of ["", "-1", "abc", "1.5"]) {
  assert.equal(parsePublicPage(invalidPage), null)
}
assert.equal(isCleanPublicFirstPage(undefined, 0), true)
assert.equal(isCleanPublicFirstPage("0", 0), false)
assert.equal(isCleanPublicFirstPage("1", 1), false)
assert.equal(buildPublicUrl("/acompanhantes/sp/sao-paulo", 0), "https://topsdojob.com/acompanhantes/sp/sao-paulo")
assert.equal(buildPublicUrl("/acompanhantes/sp/sao-paulo", 1), "https://topsdojob.com/acompanhantes/sp/sao-paulo?page=1")
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
assert.equal(isNonIndexableRoute("/acompanhantes/go/goiania"), false)
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
const workflowSource = source("../.github/workflows/deploy-preprod.yml")

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
assert.match(listingPageSource, /if \(value === ""\) return null/)
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
assert.match(workflowSource, /X-Robots-Tag:.*noindex/)
assert.match(workflowSource, /Disallow: \//)
assert.match(nextConfigSource, /output:\s*["']standalone["']/)
assert.match(
  preprodComposeSource,
  /COPY --from=build --chown=node:node \/app\/\.next\/standalone \.\//,
)
assert.doesNotMatch(
  preprodComposeSource,
  /COPY --from=build \/app\/src\/lib\/seo\/search-indexing-policy\.ts/,
)

console.log("SEARCH_INDEXING_POLICY_RESULT=OK")
