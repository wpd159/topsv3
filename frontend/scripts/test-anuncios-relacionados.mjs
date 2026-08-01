import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

function source(relativePath) {
  return readFileSync(new URL(`../${relativePath}`, import.meta.url), 'utf8')
}

const page = source('src/app/(public-routes)/anuncios/[slug]/page.tsx')
const detail = source('src/app/(public-routes)/anuncios/[slug]/anuncio-detalhes.tsx')
const mainContent = source('src/app/(public-routes)/anuncios/[slug]/componentes/main-content.tsx')
const related = source('src/app/(public-routes)/anuncios/[slug]/componentes/anuncios-relacionados.tsx')
const api = source('src/lib/public-catalog-api.ts')

assert.doesNotMatch(page, /listarPublicosPorCidade/)
assert.match(page, /<AnuncioDetalhesPageClient initialData=\{initialData\}/)
assert.match(api, /relacionados: Array\.isArray\(raw\.relacionados\)/)
assert.match(detail, /initialData\?\.relacionados \?\? \[\]/)
assert.match(detail, /<MainContent anuncio=\{anuncio\} relacionados=\{relacionados\}/)

const mapPosition = mainContent.indexOf('data-public-map')
const relatedPosition = mainContent.indexOf('<AnunciosRelacionados anuncios={relacionados} />')
assert.ok(mapPosition >= 0 && relatedPosition > mapPosition, 'related section must be rendered after location/map')

assert.match(related, /Você também pode gostar:/)
assert.match(related, /if \(anuncios\.length === 0\) return null/)
assert.match(related, /data-related-ads/)
assert.match(related, /href=\{href\}/)
assert.match(related, /Ver perfil/)
assert.match(related, /selecionarCapaPublicaSegura/)
assert.match(related, /fontePublicaSegura/)
assert.match(related, /overflow-x-auto/)
assert.doesNotMatch(related, /useEffect|Math\.random|Chat|WhatsApp|Favorit|evento_visualizacao|registrarVisualizacao/)
assert.doesNotMatch(related, /origem|COMPRA|CREDITO|Premium|Em destaque|Com local|Faz anal/)

console.log('ANUNCIOS_RELACIONADOS_RESULT=OK')
