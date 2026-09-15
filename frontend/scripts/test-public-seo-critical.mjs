import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const metadataSource = readFileSync('src/lib/seo/public-metadata.ts', 'utf8')
const detailPageSource = readFileSync('src/app/(public-routes)/anuncios/[slug]/page.tsx', 'utf8')
const nationalPageSource = readFileSync(
  'src/app/(public-routes)/acompanhantes/page.tsx',
  'utf8',
)
const nationalSeoSource = readFileSync(
  'src/lib/seo/acompanhantes-national-seo.ts',
  'utf8',
)
const listingPageSource = readFileSync(
  'src/app/(public-routes)/anuncios/page.tsx',
  'utf8',
)
const statePageSource = readFileSync(
  'src/app/(public-routes)/acompanhantes/[estado]/page.tsx',
  'utf8',
)
const cityPageSource = readFileSync(
  'src/app/(public-routes)/acompanhantes/[estado]/[cidade]/page.tsx',
  'utf8',
)
const neighborhoodPageSource = readFileSync(
  'src/app/(public-routes)/acompanhantes/[estado]/[cidade]/[bairro]/page.tsx',
  'utf8',
)

for (const expected of [
  'categoria?: string | null',
  'ACOMPANHANTE_MASCULINO',
  'Acompanhante masculino',
  'TRANSEX_TRAVESTIS',
  'Acompanhante trans',
  'MASSAGENS',
  'Massagista',
  'VENDA_DE_CONTEUDO',
  'Sexo virtual com',
  '${prefixo} em ${local} – ${titulo} | Tops do Job',
]) {
  assert.ok(metadataSource.includes(expected), `metadata must include ${expected}`)
}

assert.ok(
  detailPageSource.includes('categoria: data?.categoria') &&
    detailPageSource.includes('categoria: initialData?.categoria'),
  'detail metadata must pass canonical category to title builder',
)

assert.ok(
  detailPageSource.includes('primaryImageOfPage') &&
    detailPageSource.includes('url: imagemPublica') &&
    detailPageSource.includes('images: [{ url: imagemPublica'),
  'JSON-LD and og:image must use the same real public image',
)

assert.ok(
  !detailPageSource.includes('2151117281.jpg') &&
    !detailPageSource.includes('SAFE_COMPLIANCE_IMAGE'),
  'ad detail SEO must not use institutional/compliance image fallback',
)

const nationalTitle = 'Acompanhantes em Todo o Brasil por Cidade | Tops do Job'
const nationalDescription =
  'Encontre acompanhantes em todo o Brasil por estado, cidade e bairro. Consulte anúncios ativos e descubra opções disponíveis na sua região.'

assert.equal(
  (nationalPageSource.match(/<h1\b/g) ?? []).length,
  1,
  'national page must render exactly one H1',
)
assert.ok(
  nationalPageSource.includes('Acompanhantes - Cidades do Brasil'),
  'national page must render the canonical H1',
)
assert.ok(
  nationalSeoSource.includes(nationalTitle) &&
    nationalSeoSource.includes(nationalDescription),
  'national page must keep the exact title and description in SSR source',
)
assert.ok(
  nationalPageSource.includes('title: ACOMPANHANTES_NATIONAL_TITLE') &&
    nationalPageSource.includes('description: ACOMPANHANTES_NATIONAL_DESCRIPTION'),
  'national metadata must reuse one title and description source',
)
assert.ok(
  nationalPageSource.includes('canonical: buildPublicUrl("/acompanhantes")') &&
    nationalPageSource.includes('url: buildPublicUrl("/acompanhantes")'),
  'national canonical and Open Graph URL must remain self-referential',
)
assert.ok(
  nationalPageSource.includes('openGraph: {') &&
    nationalPageSource.includes('twitter: {') &&
    nationalPageSource.includes('card: "summary"'),
  'national Open Graph and Twitter metadata must be aligned',
)
assert.ok(
  nationalPageSource.includes('<Link href="/"') &&
    nationalPageSource.includes('>Acompanhantes</span>'),
  'national breadcrumb must remain unchanged',
)
assert.equal(
  (nationalPageSource.match(/application\/ld\+json/g) ?? []).length,
  1,
  'national page must render one route-specific JSON-LD graph',
)
for (const schemaType of ['CollectionPage', 'BreadcrumbList', 'ItemList', 'FAQPage']) {
  assert.ok(nationalSeoSource.includes(`"@type": "${schemaType}"`), `${schemaType} is required`)
}
assert.ok(!nationalSeoSource.includes('"@type": "WebSite"'), 'global WebSite must not be duplicated')
assert.ok(!nationalSeoSource.includes('LocalBusiness'), 'national page is not a LocalBusiness')
assert.ok(
  nationalPageSource.includes('buildAcompanhantesNationalCoverage') &&
    nationalPageSource.includes('listarAnunciosPublicos("TODOS", "", 0, 1)') &&
    nationalSeoSource.includes('paginacao.totalItens'),
  'coverage must use real public contracts',
)
assert.ok(
  nationalSeoSource.includes('throw new Error') &&
    !nationalPageSource.includes('catch') &&
    !nationalPageSource.includes('totalItens ?? 0'),
  'contract failures must not be converted to false zeroes',
)
assert.ok(
  nationalPageSource.includes('cidades.filter(isCidadeIndexavelLocal)'),
  'national SEO links must consume the backend local indexability decision',
)
assert.ok(
  !nationalPageSource.includes('{cidade.totalAnunciosAtivos} anúncios publicados'),
  'city cards must not expose published ad counts',
)
for (const heading of [
  'Cobertura nacional atual',
  'Como encontrar acompanhantes por cidade',
  'Segurança e verificação no Tops do Job',
  'Perguntas frequentes',
]) {
  assert.ok(nationalPageSource.includes(heading), `${heading} must be visible in SSR`)
}
for (const question of [
  'Como encontrar acompanhantes na minha cidade?',
  'Posso pesquisar acompanhantes por bairro?',
  'Quais cidades possuem anúncios ativos?',
  'Há anúncios para atendimento virtual?',
  'Como denunciar um anúncio?',
  'Como funciona a proteção de conteúdo restrito?',
]) {
  assert.ok(nationalSeoSource.includes(question), `${question} must be present`)
}
for (const href of [
  '/aviso-seguranca-whatsapp',
  '/termos-de-uso',
  '/anuncios',
  '/blog',
  '/faq',
]) {
  assert.ok(nationalPageSource.includes(`href="${href}"`), `${href} must be crawlable`)
}
assert.ok(
  nationalPageSource.includes('<details') &&
    nationalPageSource.includes('<summary') &&
    nationalPageSource.includes('faq.resposta'),
  'visible FAQ answers must remain readable without JavaScript',
)
assert.ok(
  nationalPageSource.includes('__html: serializeJsonLd(structuredData)') &&
    nationalPageSource.includes('serializeJsonLd'),
  'JSON-LD must use the central safe serializer',
)

assert.ok(
  listingPageSource.includes('buildPublicListingIndexingDecision(searchParams, page)') &&
    listingPageSource.includes('parsePublicPage(searchParams.page, 1)') &&
    listingPageSource.includes('parsePublicOrderSeed(searchParams.ordemSeed)'),
  'the one-based listing must reject ambiguous pages and use the indexing policy',
)
for (const localityPageSource of [statePageSource, cityPageSource, neighborhoodPageSource]) {
  assert.ok(
    localityPageSource.includes('isCleanPublicFirstPage(pageValue, page)'),
    'only the clean locality first page may be indexable',
  )
  assert.match(localityPageSource, /parsePublicPage\(query\.page\)/)
  assert.doesNotMatch(localityPageSource, /permanentRedirect/)
}
assert.match(listingPageSource, /permanentRedirect\(buildPublicPageHref\(/)
for (const pageSource of [listingPageSource, statePageSource, cityPageSource, neighborhoodPageSource]) {
  assert.match(pageSource, /parsePublicOrderSeed\(/)
  assert.doesNotMatch(pageSource, /buildPublicUrl\([^)]*ordemSeed/)
}

console.log('public SEO critical checks passed')
