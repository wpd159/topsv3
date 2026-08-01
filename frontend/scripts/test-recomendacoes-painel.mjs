import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(scriptDirectory, '..')
const repositoryRoot = path.resolve(frontendRoot, '..')
const source = (relativePath) => readFileSync(path.resolve(frontendRoot, relativePath), 'utf8')

const panel = source('src/components/painel-anunciante/recommendations-panel.tsx')
const page = source('src/app/(private-routes)/painel/page.tsx')
const api = source('src/lib/recomendacoes-api.ts')
const openapi = readFileSync(
  path.join(repositoryRoot, 'contracts/openapi/topsdojob-v3-local.yaml'),
  'utf8',
)

assert.match(page, /<RecommendationsPanel \/>/)
assert.doesNotMatch(page, /recommendationsPendingError|personalizadas ainda n[aÃ£]o est[aÃ£]o dispon[iÃ­]veis/)

for (const text of [
  'Carregando recomendacoes...',
  'Nenhuma recomendacao disponivel no momento.',
  'Nao foi possivel carregar as recomendacoes.',
  'Tentar novamente',
  'indisponiveis por configuracao',
]) {
  assert.ok(panel.includes(text), `Estado de recomendacoes ausente: ${text}`)
}
assert.match(panel, /setTentativa\(\(value\) => value \+ 1\)/)
assert.match(panel, /router\.replace\(`\/\?login=1&next=/)
assert.match(panel, /controller\.abort\(\)/)
assert.match(panel, /sm:grid-cols-2/)
assert.match(panel, /break-words/)

assert.match(api, /publicApiUrl\('\/painel-anunciante\/recomendacoes'\)/)
assert.match(api, /credentials: 'include'/)
assert.match(api, /cache: 'no-store'/)
assert.match(api, /signal/)
assert.match(api, /payloadIncompativel/)
assert.match(api, /new Set\(itens\.map\(\(item\) => item\.id\)\)/)
assert.match(api, /!acaoHref\.startsWith\('\/'\) \|\| acaoHref\.startsWith\('\/\/'\)/)
assert.doesNotMatch(api, /mock|fixture|Math\.random|visualiza[cÃ§][oÃµ]es|cliques/i)
assert.ok(openapi.includes('/api/public/painel-anunciante/recomendacoes:'))
assert.ok(openapi.includes('PainelAnuncianteRecomendacoes'))
assert.match(openapi, /enum: \[RENOVACAO, PERFIL, FOTOS, VIDEO, CARROSSEL, IMPULSIONAMENTO\]/)
assert.match(openapi, /PainelAnuncianteRecomendacoes:[\s\S]*?maxItems: 6/)
assert.match(api, /'RENOVACAO'/)

console.log('Recomendacoes do painel: contrato, estados, retry, sessao e mobile validados.')
