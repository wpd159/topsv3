import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const sourceRoot = path.join(frontendRoot, 'src')

function source(relativePath) {
  return fs.readFileSync(path.join(sourceRoot, relativePath), 'utf8')
}

const contract = source('lib/visualizacoes-canonicas.ts')
const publicApi = source('lib/public-catalog-api.ts')
const myAdsApi = source('lib/meus-anuncios-api.ts')
const publicCard = source('components/anuncios/anuncio-card.tsx')
const publicGrid = source('components/anuncios/anuncios-grid.tsx')
const publicPagedGrid = source('components/anuncios/listagem-publica-paginada.tsx')
const myAdCard = source('components/anuncios/meu-anuncio-card.tsx')
const myAdDetail = source('components/anuncios/meu-anuncio-detalhe-view.tsx')
const publicDetail = source('app/(public-routes)/anuncios/[slug]/anuncio-detalhes.tsx')
const combined = [publicApi, myAdsApi, publicCard, publicGrid, publicPagedGrid, myAdCard, myAdDetail, publicDetail].join('\n')

assert.match(contract, /'DISPONIVEL'/)
assert.match(contract, /'ZERO_LEGITIMO'/)
assert.match(contract, /'HISTORICO_PENDENTE'/)
assert.match(contract, /raw\.total !== null/)
assert.match(contract, /Number\.isSafeInteger\(raw\.total\)/)
assert.match(contract, /throw new VisualizacoesCanonicasContractError/)
assert.match(contract, /return '\\u2014'/)

assert.match(publicApi, /visualizacoes: parseVisualizacoesCanonicas\(raw\.visualizacoes\)/)
assert.match(myAdsApi, /visualizacoes: parseVisualizacoesCanonicas\(raw\.visualizacoes\)/)
assert.match(myAdsApi, /throw new MeusAnunciosApiError\('O servico retornou visualizacoes/)
assert.match(publicGrid, /visualizacoes=\{anuncio\.visualizacoes\}/)
assert.match(publicPagedGrid, /visualizacoes=\{anuncio\.visualizacoes\}/)
assert.match(publicCard, /formatarVisualizacoesCanonicas\(visualizacoes\)/)
assert.match(myAdCard, /formatarVisualizacoesCanonicas\(anuncio\.visualizacoes\)/)
assert.match(myAdDetail, /formatarVisualizacoesCanonicas\(anuncio\.visualizacoes\)/)
assert.match(publicDetail, /visualizacoes: data\.visualizacoes/)

assert.doesNotMatch(combined, /visualizacoes\s*=\s*0/)
assert.doesNotMatch(combined, /visualizacoes\s*\?\?\s*0/)
assert.doesNotMatch(combined, /visualizacoes\s*\|\|\s*0/)

console.log('VISUALIZACOES_CANONICAS_FRONTEND_RESULT=OK')
