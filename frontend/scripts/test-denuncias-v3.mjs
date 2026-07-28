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

const modal = source('src/app/(public-routes)/anuncios/[slug]/componentes/denuncia-modal.tsx')
const adminPage = source('src/app/(painel-admin)/admin/denuncias/page.tsx')
const adminDetail = source('src/app/(painel-admin)/admin/components/denuncia-details-modal.tsx')
const publicApi = source('src/lib/denuncia-api.ts')
const adminApi = source('src/lib/admin-denuncia-api.ts')
const sidebar = source('src/app/(painel-admin)/admin/components/sidebar/sidebar.tsx')
const contracts = source('src/lib/api-contract.ts')
const openapi = readFileSync(
  path.join(repositoryRoot, 'contracts/openapi/topsdojob-v3-local.yaml'),
  'utf8',
)

for (const file of [modal, adminPage, adminDetail]) {
  assert.ok(!file.includes('PENDING_BACKEND_CONTRACTS'), 'Denuncias nao podem manter contrato pendente.')
  assert.ok(!file.includes('type="file"'), 'Anexos nao comprovados nao devem ser inventados.')
}

for (const label of [
  'Conteudo inadequado',
  'Perfil falso',
  'Golpe / Scam',
  'Spam',
  'Outros',
  'Enviar denuncia',
]) {
  assert.ok(modal.includes(label), `Fluxo publico ausente: ${label}.`)
}
assert.ok(modal.includes('enviando') && modal.includes('disabled={!motivo || enviando}'))
assert.ok(modal.includes('Denuncia enviada com sucesso.'))
assert.ok(publicApi.includes("publicApiUrl('/denuncias/abrir')"))
assert.ok(publicApi.includes('Idempotency-Key'))
assert.ok(publicApi.includes("publicApiUrl('/auth/me')"), 'Mutacao publica deve obter CSRF.')

for (const label of [
  'Todas',
  'Pendentes',
  'Com providencia',
  'Sem providencia',
  'Abrir detalhe',
  'Tentar novamente',
]) {
  assert.ok(adminPage.includes(label), `Fluxo administrativo ausente: ${label}.`)
}
for (const filter of ['termo', 'motivo', 'status', 'inicio', 'fim', 'pagina', 'tamanho']) {
  assert.ok(adminPage.includes(filter), `Filtro ou paginacao ausente: ${filter}.`)
}
assert.ok(adminPage.includes('window.history.replaceState'), 'Filtros devem permanecer na URL.')
assert.ok(adminPage.includes('Nenhuma denuncia encontrada'), '200 vazio deve ser legitimo.')
assert.ok(adminDetail.includes('Historico') && adminDetail.includes('Providencia administrativa'))
assert.ok(adminDetail.includes('/admin/anuncios/'), 'Detalhe deve apontar ao anuncio canonico.')
assert.ok(adminDetail.includes('sem alterar o anuncio automaticamente'))
assert.ok(adminApi.includes('getAdminMutationHeaders'), 'Mutacao admin deve usar CSRF canonico.')
assert.ok(adminApi.includes('apiErrorFromResponse(response)'), 'Erro nao pode virar fila vazia.')
assert.ok(sidebar.includes("adminApiUrl('/denuncias/indicadores')"))
assert.ok(sidebar.includes('admin-denuncias-updated'))
assert.ok(!contracts.includes("reports: 'Denuncias'"))

for (const pathValue of [
  '/api/public/denuncias/abrir:',
  '/api/admin/denuncias:',
  '/api/admin/denuncias/indicadores:',
  '/api/admin/denuncias/{denunciaId}:',
  '/api/admin/denuncias/{denunciaId}/status:',
]) {
  assert.ok(openapi.includes(pathValue), `OpenAPI ausente: ${pathValue}`)
}
assert.ok(openapi.includes('DenunciaMotivo') && openapi.includes('AdminDenunciaDetalhe'))
assert.ok(!openapi.includes('DenunciaAnexo'), 'Contrato nao deve inventar anexos.')

console.log('Denuncias V3: fluxo publico, admin, idempotencia, filtros e contador validados.')
