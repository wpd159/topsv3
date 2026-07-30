import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const metadataSource = readFileSync('src/lib/seo/public-metadata.ts', 'utf8')
const detailPageSource = readFileSync('src/app/(public-routes)/anuncios/[slug]/page.tsx', 'utf8')
const nationalPageSource = readFileSync(
  'src/app/(public-routes)/acompanhantes/page.tsx',
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
  nationalPageSource.includes(nationalTitle) &&
    nationalPageSource.includes(nationalDescription),
  'national page must keep the exact title and description in SSR source',
)
assert.ok(
  nationalPageSource.includes('title: NATIONAL_PAGE_TITLE') &&
    nationalPageSource.includes('description: NATIONAL_PAGE_DESCRIPTION'),
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
  0,
  'national page must not duplicate the global structured data',
)

console.log('public SEO critical checks passed')
