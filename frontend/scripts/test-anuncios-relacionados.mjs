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

function workspaceSource(relativePath) {
  return readFileSync(new URL('../../' + relativePath, import.meta.url), 'utf8')
}

const backendService = workspaceSource(
  'backend/src/main/java/br/com/topsdojob/v3/application/publico/service/AnuncioPublicoConsultaService.java',
)
const backendRepository = workspaceSource(
  'backend/src/main/java/br/com/topsdojob/v3/persistence/repository/AnuncioRepository.java',
)
const openapi = workspaceSource('contracts/openapi/topsdojob-v3-local.yaml')

const relatedMethodPosition = backendRepository.indexOf('findRelacionadosComBeneficioVigente')
assert.ok(relatedMethodPosition >= 0, 'canonical related-ad repository method must exist')
const relatedQueryStart = backendRepository.lastIndexOf('@Query', relatedMethodPosition)
const relatedQueryEnd = backendRepository.indexOf('@Query', relatedMethodPosition)
const relatedQuery = backendRepository.slice(
  relatedQueryStart,
  relatedQueryEnd === -1 ? undefined : relatedQueryEnd,
)
const relatedOrderPosition = relatedQuery.indexOf('order by')
assert.ok(relatedQueryStart >= 0 && relatedOrderPosition >= 0, 'related-ad query must be discoverable')
const relatedEligibility = relatedQuery.slice(0, relatedOrderPosition)

const relatedSchemaStart = openapi.indexOf('\n        relacionados:')
const relatedSchemaEnd = openapi.indexOf('\n    AnuncioRelacionadoPublico:', relatedSchemaStart)
assert.ok(relatedSchemaStart >= 0 && relatedSchemaEnd > relatedSchemaStart)
const relatedSchema = openapi.slice(relatedSchemaStart, relatedSchemaEnd)

assert.doesNotMatch(page, /listarPublicosPorCidade/)
assert.match(page, /<AnuncioDetalhesPageClient initialData=\{initialData\}/)
assert.match(api, /relacionados: Array\.isArray\(raw\.relacionados\)/)
assert.match(detail, /initialData\?\.relacionados \?\? \[\]/)
assert.match(detail, /<MainContent anuncio=\{anuncio\} relacionados=\{relacionados\}/)

assert.match(backendService, /PageRequest\.of\(0, 6\)/)
assert.match(backendService, /findRelacionadosComBeneficioVigente/)
assert.match(backendService, /if \(candidatos\.isEmpty\(\)\)/)
assert.match(backendService, /findByAnuncioIdIn\(anuncioIds\)/)
assert.match(backendService, /flagsPorAnuncios\(candidatos\)/)
assert.match(backendService, /resolverPorAnuncios\(candidatos, premium\)/)
assert.match(relatedEligibility, /a\.id <> :anuncioAtualId/)
assert.match(relatedEligibility, /a\.categoria = :categoria/)
assert.match(relatedEligibility, /bp\.ativo = true/)
assert.match(relatedEligibility, /bp\.escopo = 'ANUNCIO'/)
assert.match(relatedEligibility, /ab\.status = 'ATIVA'/)
assert.match(relatedEligibility, /ab\.inicio_em <= :agora/)
assert.match(relatedEligibility, /ab\.fim_em > :agora/)
assert.match(relatedEligibility, /gb\.status = 'ATIVO'/)
assert.doesNotMatch(
  relatedEligibility,
  /movimento_credito|preco_snapshot|custo_creditos_snapshot|bp\.codigo in|ab\.origem = '(?:COMPRA|CREDITO|CORTESIA|CAMPANHA|ADMIN|IMPORTACAO)'/,
)
assert.match(relatedSchema, /maxItems: 6/)
assert.match(relatedSchema, /independentemente da origem da ativacao/)
assert.match(relatedSchema, /fallback integral/)

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
assert.match(related, /sm:grid/)
assert.match(related, /w-\[190px\]/)
assert.doesNotMatch(related, /useEffect|Math\.random|Chat|WhatsApp|Favorit|evento_visualizacao|registrarVisualizacao/)
assert.doesNotMatch(related, /origem|COMPRA|CREDITO|Premium|Em destaque|Com local|Faz anal/)

console.log('ANUNCIOS_RELACIONADOS_RESULT=OK')
