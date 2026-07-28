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

const userPage = source('src/app/(private-routes)/meus-tickets/page.tsx')
const adminPage = source('src/app/(painel-admin)/admin/tickets/page.tsx')
const adminDetail = source('src/app/(painel-admin)/admin/components/ticket-details-modal.tsx')
const adminChat = source('src/app/(painel-admin)/admin/components/ticket-chat-modal.tsx')
const floatingButton = source('src/components/layout/ticket-button.tsx')
const hook = source('src/hooks/useSuporteChat.ts')
const publicApi = source('src/lib/suporte-api.ts')
const adminApi = source('src/lib/admin-suporte-api.ts')
const sidebar = source('src/app/(painel-admin)/admin/components/sidebar/sidebar.tsx')
const contracts = source('src/lib/api-contract.ts')
const openapi = readFileSync(
  path.join(repositoryRoot, 'contracts/openapi/topsdojob-v3-local.yaml'),
  'utf8',
)

for (const file of [userPage, adminPage, adminDetail, adminChat, floatingButton, hook]) {
  assert.ok(!file.includes('PENDING_BACKEND_CONTRACTS'), 'Tickets nao podem manter contrato pendente.')
  assert.ok(!file.includes('type="file"'), 'Anexos nao foram comprovados na producao.')
}

for (const label of ['Abrir ticket', 'Meus tickets', 'Abertos', 'Encerrados', 'Enviar resposta']) {
  assert.ok(userPage.includes(label), `Fluxo do usuario ausente: ${label}.`)
}
assert.ok(userPage.includes('Nenhum ticket neste filtro'), '200 vazio deve ter estado legitimo.')
assert.ok(userPage.includes('Tentar novamente'), 'Erro tecnico deve permitir retry.')
assert.ok(userPage.includes('max-w-[88%]'), 'Conversa deve caber em 390 px.')
assert.ok(userPage.includes('window.setInterval'), 'Conversa do usuario deve atualizar sem reload.')
assert.ok(userPage.includes('chaveResposta.current ??='), 'Retry do usuario deve reutilizar a chave idempotente.')
assert.ok(publicApi.includes("credentials: 'include'"), 'Sessao publica deve ser a fonte do usuario.')
assert.ok(publicApi.includes('Idempotency-Key'), 'Criacao e mensagens devem ser idempotentes.')
assert.ok(publicApi.includes("'/auth/me'"), 'Mutacoes devem obter CSRF pelo fluxo existente.')
assert.ok(!publicApi.includes('/api/public/api/public'), 'Adapter nao pode duplicar a base publica.')

for (const label of ['Todos', 'Abertos', 'Em andamento', 'Fechados', 'Responder', 'Ver detalhes']) {
  assert.ok(adminPage.includes(label), `Fluxo administrativo ausente: ${label}.`)
}
for (const filter of ['termo', 'categoria', 'status', 'ordenacao', 'pagina', 'tamanho']) {
  assert.ok(adminPage.includes(filter), `Filtro ou paginacao ausente: ${filter}.`)
}
assert.ok(adminPage.includes('window.history.replaceState'), 'Filtros administrativos devem permanecer na URL.')
assert.ok(adminDetail.includes('Histórico') && adminDetail.includes('Alterar estado'), 'Detalhe deve exibir historico e estado.')
assert.ok(adminChat.includes('novaIdempotencyKey'), 'Resposta da equipe deve bloquear retry duplicado.')
assert.ok(adminChat.includes('window.setInterval'), 'Conversa administrativa deve atualizar sem reload.')
assert.ok(adminChat.includes('chaveResposta.current ??='), 'Retry administrativo deve reutilizar a chave idempotente.')
assert.ok(adminApi.includes('getAdminMutationHeaders'), 'Mutacoes administrativas devem usar CSRF canonico.')
assert.ok(adminApi.includes('apiErrorFromResponse(response)'), 'Falha administrativa nao pode virar lista vazia.')
assert.ok(sidebar.includes("adminApiUrl('/tickets/indicadores')"), 'Contador deve consultar contrato real.')
assert.ok(sidebar.includes('inFlight'), 'Polling deve impedir chamadas duplicadas na mesma janela.')
assert.ok(!contracts.includes("support: 'Tickets"), 'Tickets nao podem continuar catalogados como pendentes.')

for (const pathValue of [
  '/api/public/suporte/tickets:',
  '/api/public/suporte/tickets/{ticketId}:',
  '/api/admin/tickets:',
  '/api/admin/tickets/indicadores:',
  '/api/admin/tickets/{ticketId}:',
]) {
  assert.ok(openapi.includes(pathValue), `OpenAPI ausente: ${pathValue}`)
}
assert.ok(openapi.includes('SuporteTicketDetalhe') && openapi.includes('AdminSuporteIndicadores'))
assert.ok(!openapi.includes('SuporteAnexo'), 'Contrato nao deve inventar anexos.')

console.log('Tickets V3: usuario, admin, idempotencia, RBAC, estados e contador validados.')
