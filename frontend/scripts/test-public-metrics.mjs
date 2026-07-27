import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8')

const api = read('src/lib/public-metrics-api.ts')
const detail = read('src/app/(public-routes)/anuncios/[slug]/anuncio-detalhes.tsx')
const sidebar = read('src/app/(public-routes)/anuncios/[slug]/componentes/sidebar.tsx')
const card = read('src/components/anuncios/anuncio-card.tsx')

assert.match(api, /publicCsrfHeaders\(\)/, 'Metricas publicas devem reutilizar o CSRF canonico.')
assert.match(api, /headers\.set\('Idempotency-Key', idempotencyKey\)/)
assert.match(api, /for \(let tentativa = 0; tentativa < 2;/, 'Retry deve ser unico e reutilizar a mesma chave.')
assert.match(api, /keepalive: true/, 'Registro deve sobreviver a navegacao externa.')
assert.match(api, /\/visualizacao`/)
assert.match(api, /\/clique-whatsapp`/)

assert.match(detail, /registrarVisualizacaoPublica\(slug, chaveRegistro\.chave\)/)
assert.match(detail, /visualizacaoRegistradaParaId\.current === anuncio\.id/)
assert.match(detail, /visualizacaoFetchParaId\.current === anuncio\.id/)
assert.doesNotMatch(
  detail,
  /fetch\(.*\/visualizacao/s,
  'O detalhe nao deve manter um segundo adapter de visualizacao.',
)

for (const source of [sidebar, card]) {
  assert.match(source, /whatsappInFlight\.current/, 'Duplo clique deve ser bloqueado.')
  assert.match(source, /registrarCliqueWhatsappPublico\(/)
  assert.match(source, /if \(!payload\.registrado\)/, 'Falha isolada da metrica deve ser informada.')
  assert.match(source, /openWhatsAppWarning\(\{ url: payload\.whatsappUrl \}\)/)
  assert.match(source, /error instanceof ApiContractError && error\.status === 403/)
  assert.match(source, /void requestWhatsApp\(\)/, 'Fluxo deve retomar depois do age gate.')
}

assert.match(card, /gtag\)\s*\{[\s\S]*"click_whatsapp"/, 'GA4 existente deve permanecer inalterado.')

console.log('OK metricas publicas usam CSRF, idempotencia, retry seguro e age gate sem bloquear WhatsApp')
