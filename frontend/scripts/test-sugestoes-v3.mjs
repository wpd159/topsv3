import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(scriptDirectory, '..')
const repositoryRoot = path.resolve(frontendRoot, '..')

function source(relativePath) {
  return readFileSync(path.resolve(frontendRoot, relativePath), 'utf8')
}

const feedback = source('src/components/modals/feedback-dialog.tsx')
const publicApi = source('src/lib/sugestao-api.ts')
const adminApi = source('src/lib/admin-sugestao-api.ts')
const adminPage = source('src/app/(painel-admin)/admin/sugestoes/page.tsx')
const adminTable = source('src/app/(painel-admin)/admin/components/sugestoes-table.tsx')
const adminDetail = source('src/app/(painel-admin)/admin/components/sugestao-details-modal.tsx')
const sidebar = source('src/app/(painel-admin)/admin/components/sidebar/sidebar.tsx')
const contracts = source('src/lib/api-contract.ts')
const openapi = readFileSync(
  path.join(repositoryRoot, 'contracts/openapi/topsdojob-v3-local.yaml'),
  'utf8',
)

for (const file of [feedback, adminPage, adminTable, adminDetail]) {
  assert.ok(!file.includes('PENDING_BACKEND_CONTRACTS'), 'Sugestoes nao podem manter contrato pendente.')
  assert.ok(!file.includes('type="file"'), 'Anexos nao comprovados nao devem existir.')
}

for (const label of [
  'Sugestao',
  'Bug',
  'Titulo do feedback',
  'Enviar feedback',
  'Sugestao enviada com sucesso.',
]) {
  assert.ok(feedback.includes(label), `Fluxo autenticado ausente: ${label}.`)
}
assert.ok(feedback.includes('disabled={enviando}'), 'Duplo clique deve ser bloqueado.')
assert.ok(publicApi.includes("publicApiUrl('/sugestoes')"))
assert.ok(publicApi.includes('Idempotency-Key'))
assert.ok(publicApi.includes("publicApiUrl('/auth/me')"), 'Mutacao publica deve obter CSRF.')

for (const label of [
  'Todas',
  'Novas',
  'Em analise',
  'Aceitas',
  'Recusadas',
  'Abrir detalhe',
  'Tentar novamente',
]) {
  assert.ok(
    adminPage.includes(label) || adminTable.includes(label),
    `Fluxo administrativo ausente: ${label}.`,
  )
}
for (const filter of ['termo', 'status', 'pagina', 'tamanho']) {
  assert.ok(adminPage.includes(filter), `Filtro ou paginacao ausente: ${filter}.`)
}
assert.ok(adminPage.includes('window.history.replaceState'), 'Filtros devem permanecer na URL.')
assert.ok(adminPage.includes('Nenhuma sugestao encontrada'), '200 vazio deve ser legitimo.')
assert.ok(adminDetail.includes('Providencia administrativa'))
assert.ok(adminDetail.includes('Historico'))
assert.ok(adminDetail.includes('Iniciar analise'))
assert.ok(adminDetail.includes('Aceitar sugestao'))
assert.ok(adminDetail.includes('Recusar sugestao'))
assert.ok(adminDetail.includes('disabled={salvando}'))
assert.ok(adminApi.includes('getAdminMutationHeaders'), 'Mutacao admin deve usar CSRF canonico.')
assert.ok(adminApi.includes('apiErrorFromResponse(response)'), 'Erro nao pode virar fila vazia.')
assert.ok(sidebar.includes("adminApiUrl('/sugestoes/indicadores')"))
assert.ok(sidebar.includes('admin-sugestoes-updated'))
assert.ok(!contracts.includes("suggestions: 'Sugestoes'"))

for (const pathValue of [
  '/api/public/sugestoes:',
  '/api/admin/sugestoes:',
  '/api/admin/sugestoes/indicadores:',
  '/api/admin/sugestoes/{sugestaoId}:',
  '/api/admin/sugestoes/{sugestaoId}/status:',
]) {
  assert.ok(openapi.includes(pathValue), `OpenAPI ausente: ${pathValue}`)
}
assert.ok(!openapi.includes('SugestaoAnexo'), 'Contrato nao deve inventar anexos.')
assert.ok(!openapi.includes('SugestaoRespostaPublica'), 'Contrato nao deve inventar resposta publica.')

console.log('Sugestoes V3: envio autenticado, admin, idempotencia, filtros e contador validados.')
