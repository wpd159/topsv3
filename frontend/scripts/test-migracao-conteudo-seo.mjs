import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read = (path) => readFileSync(path, 'utf8')

const homeSource = read('src/app/(public-routes)/page.tsx')
const sitemapSource = read('src/app/sitemap.ts')
const localIndexingSource = read('src/lib/seo/local-indexing.ts')
const searchPolicySource = read('src/lib/seo/search-indexing-policy.ts')
const anuncioPolicySource = read(
  '../backend/src/main/java/br/com/topsdojob/v3/application/publico/service/AnuncioSeoIndexabilidadePolicy.java',
)

assert.ok(
  homeSource.includes('{labelAcompanhantesCidade(cidade.cidadeNome)} - {cidade.estadoUf}'),
  'home must display Acompanhantes em Cidade - UF',
)
assert.ok(
  homeSource.includes(
    'href={`/acompanhantes/${cidade.estadoUf.toLowerCase()}/${cidade.cidadeSlug}`}',
  ),
  'home must preserve the canonical city URL',
)
assert.ok(
  homeSource.includes('descobrirLocalidadesPublicas()') &&
    homeSource.includes('totalAnunciosAtivos: cidade.totalAnunciosAtivos'),
  'home links must be derived from real public locality data',
)

for (const expected of [
  'isCidadeIndexavelLocal',
  'isBairroIndexavelLocal',
  'descobrirAnunciosIndexaveisSitemap()',
  '/blog-posts/public/sitemap',
  '/blog-categorias/public',
  '${baseUrl}/faq',
  '${baseUrl}/sobre',
  '${baseUrl}/termos-de-uso',
  '${baseUrl}/politica-de-privacidade',
  '.filter((route) => isSafeSitemapUrl(route.url, indexingPolicy))',
]) {
  assert.ok(sitemapSource.includes(expected), `sitemap must include ${expected}`)
}
assert.ok(
  sitemapSource.includes('if (!indexingPolicy.sitemapEnabled) return []'),
  'pre-production must keep the sitemap fail-closed',
)
assert.ok(
  sitemapSource.includes('if (cidadesIndexaveis.length === 0) continue') &&
    sitemapSource.includes(
      'if (!isBairroIndexavelLocal({ totalAnunciosAtivos: bairro.totalAnunciosAtivos })) continue',
    ),
  'empty localities must remain outside the sitemap',
)
assert.ok(
  !sitemapSource.includes('${baseUrl}/admin') &&
    !sitemapSource.includes('${baseUrl}/minha-conta') &&
    !sitemapSource.includes('${baseUrl}/checkout') &&
    !sitemapSource.includes('${baseUrl}/creditos'),
  'private and commercial routes must not be added to the sitemap',
)

assert.ok(
  localIndexingSource.includes('MIN_ANUNCIOS_CIDADE_INDEX = 5') &&
    localIndexingSource.includes('MIN_ANUNCIOS_BAIRRO_INDEX = 3'),
  'the import must preserve the audited canonical locality thresholds',
)
assert.ok(
  localIndexingSource.includes('input?.shouldIndex === true') &&
    localIndexingSource.includes('totalAnunciosLocal(input)'),
  'local indexability must remain centralized and data-driven',
)

assert.ok(
  searchPolicySource.includes('if (mode === "public" && canonicalOrigin !== FINAL_PRODUCTION_ORIGIN)') &&
    searchPolicySource.includes('if (!policy.publicIndexingEnabled)') &&
    searchPolicySource.includes('return { index: false, follow: false, noarchive: true }'),
  'pre-production must be blocked and public indexing must require the final domain',
)
assert.ok(
  searchPolicySource.includes('url.origin === policy.canonicalOrigin') &&
    searchPolicySource.includes('!url.search') &&
    searchPolicySource.includes('!url.hash') &&
    searchPolicySource.includes('!isNonIndexableRoute(url.pathname)'),
  'sitemap URLs must be canonical and free of parameters or private routes',
)

for (const expected of [
  'StatusAnuncio.PUBLICADO',
  'StatusModeracaoAnuncio.APROVADO',
  'anuncio.getRemovidoEm() == null',
  'localizacaoValida(localizacao)',
  'tituloUtil(anuncio)',
  'descricaoUtil(anuncio)',
  'fotosPublicas(midiasPublicas)',
  'MIN_DESCRICAO = 120',
  'MIN_FOTOS_PUBLICAS_REAIS = 1',
]) {
  assert.ok(anuncioPolicySource.includes(expected), `ad indexability must preserve ${expected}`)
}

console.log('content and SEO migration frontend checks passed')
