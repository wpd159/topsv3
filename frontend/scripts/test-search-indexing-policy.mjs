import assert from "node:assert/strict"
import { existsSync, readFileSync } from "node:fs"
import ts from "typescript"

function source(relativePath) {
  return readFileSync(new URL(`../${relativePath}`, import.meta.url), "utf8")
}

function loadPolicyModule() {
  const { outputText } = ts.transpileModule(
    source("src/lib/seo/search-indexing-policy.ts"),
    {
      compilerOptions: {
        module: ts.ModuleKind.CommonJS,
        target: ts.ScriptTarget.ES2022,
      },
    },
  )
  const module = { exports: {} }
  new Function("module", "exports", "process", outputText)(
    module,
    module.exports,
    { env: {} },
  )
  return module.exports
}

const policyModule = loadPolicyModule()
const preprodComposeSource = source("../deploy/preprod/docker-compose.yml")
const {
  buildSearchRobotsRules,
  isNonIndexableRoute,
  isSafeSitemapUrl,
  resolveSearchIndexingPolicy,
} = policyModule

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
assert.match(sitemapSource, /isCidadeIndexavelLocal/)
assert.match(sitemapSource, /isBairroIndexavelLocal/)
assert.match(sitemapSource, /isSafeSitemapUrl/)
assert.match(rootLayoutSource, /searchIndexingPolicy\.publicIndexingEnabled/)
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
assert.match(
  preprodComposeSource,
  /COPY --from=build \/app\/src\/lib\/seo\/search-indexing-policy\.ts \.\/src\/lib\/seo\/search-indexing-policy\.ts/,
)

console.log("SEARCH_INDEXING_POLICY_RESULT=OK")
