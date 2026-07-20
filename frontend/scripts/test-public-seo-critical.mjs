import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const metadataSource = readFileSync('src/lib/seo/public-metadata.ts', 'utf8')
const detailPageSource = readFileSync('src/app/(public-routes)/anuncios/[slug]/page.tsx', 'utf8')

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

console.log('public SEO critical checks passed')
