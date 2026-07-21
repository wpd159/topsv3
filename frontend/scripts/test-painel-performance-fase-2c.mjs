import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

function source(relativePath) {
  return readFileSync(new URL(`../${relativePath}`, import.meta.url), 'utf8')
}

const api = source('src/lib/painel-anunciante-api.ts')
const page = source('src/app/(private-routes)/painel/performance/page.tsx')
const combined = `${api}\n${page}`

assert.match(api, /publicApiUrl\('\/painel-anunciante\/performance'\)/)
assert.match(api, /credentials: 'include'/)
assert.match(api, /cache: 'no-store'/)
assert.match(api, /apiErrorFromResponse\(response\)/)
assert.match(api, /parseVisualizacoesCanonicas\(raw\.visualizacoes\)/)
assert.match(api, /raw\.serieCliquesWhatsapp\.length !== 14/)
assert.match(api, /visualizacoes\.situacao === 'HISTORICO_PENDENTE'/)
assert.match(api, /if \(ctr !== null\) incompatiblePayload\(\)/)
assert.match(api, /if \(ctr === null \|\| ctr < 0\) incompatiblePayload\(\)/)

assert.match(page, /formatarVisualizacoesCanonicas\(data\.visualizacoes\)/)
assert.match(page, /formatarVisualizacoesCanonicas\(item\.visualizacoes\)/)
assert.match(page, /formatPercent\(data\.ctrGeral\)/)
assert.match(page, /formatPercent\(item\.ctr\)/)
assert.match(page, /if \(value === null\) return '\\u2014'/)
assert.match(page, /<ContractState/)
assert.match(page, /onRetry=/)
assert.match(page, /anunciosComBeneficioPremiumVigente/)
assert.match(page, /beneficiosPremiumVigentes/)

for (const forbidden of [
  /totalVisualizacoes/,
  /scoreVisibilidade/,
  /faixaVisibilidade/,
  /totalRecursosPremiumAtivos/,
  /anunciosComRecursosPremium/,
  /Number\(value \|\| 0\)/,
  /visualizacoes\s*(?:\?\?|\|\|)\s*0/,
  /ctr\s*(?:\?\?|\|\|)\s*0/,
  /\bGA4\b/i,
  /impressoes/i,
]) {
  assert.doesNotMatch(combined, forbidden)
}

console.log('PAINEL_PERFORMANCE_FASE_2C_RESULT=OK')
