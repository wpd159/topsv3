import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const sourceRoot = path.resolve(scriptDirectory, '../src')

function source(relativePath) {
  return readFileSync(path.resolve(sourceRoot, relativePath), 'utf8')
}

const listPage = source('app/(painel-admin)/admin/anuncios/page.tsx')
const detailPage = source('app/(painel-admin)/admin/anuncios/[id]/page.tsx')
const legacyListPage = source('app/(painel-admin)/admin/moderacao-v2/page.tsx')
const legacyDetailPage = source('app/(painel-admin)/admin/moderacao-v2/[anuncioId]/page.tsx')
const sidebar = source('app/(painel-admin)/admin/components/sidebar/sidebar-links.tsx')
const list = source('features/admin-anuncios/admin-anuncios-list.tsx')
const detail = source('features/admin-anuncios/admin-anuncio-moderacao.tsx')
const api = source('features/admin-anuncios/api.ts')
const types = source('features/admin-anuncios/types.ts')

assert.ok(listPage.includes('AdminAnunciosList'), 'A rota administrativa deve usar a fila V3.')
assert.ok(detailPage.includes('AdminAnuncioModeracao'), 'O detalhe deve usar a moderacao V3.')
assert.ok(!listPage.includes('moderation-v2') && !detailPage.includes('moderation-v2'), 'A area canonica nao pode importar a V2.')
assert.ok(legacyListPage.includes("redirect('/admin/anuncios')"), 'A rota antiga deve redirecionar para a fila canonica.')
assert.ok(legacyDetailPage.includes('`/admin/anuncios/${encodeURIComponent(anuncioId)}`'), 'O detalhe antigo deve redirecionar para o detalhe canonico.')
assert.ok(sidebar.includes("href: '/admin/anuncios'"), 'A sidebar deve abrir a area canonica.')
assert.ok(!sidebar.includes("href: '/admin/moderacao-v2'"), 'A sidebar nao pode abrir a V2.')

for (const contract of [
  "request(`/anuncios?${query.toString()}`)",
  "request<AdminAdDetail>(`/anuncios/${encodeURIComponent(id)}`)",
  '`/anuncios/${encodeURIComponent(id)}/midias?page=0&size=50`',
  '`/anuncios/${encodeURIComponent(id)}/historico-moderacao`',
  '`/midias/${encodeURIComponent(id)}/preview`',
  '`/moderacao/revisoes/${encodeURIComponent(reviewId)}/decidir`',
  '`/midias/${encodeURIComponent(mediaId)}/decidir`',
]) {
  assert.ok(api.includes(contract), `Contrato V3 ausente no adapter: ${contract}`)
}
assert.ok(api.includes('adminApiUrl(path)'), 'O adapter deve reutilizar o resolvedor administrativo.')
assert.ok(api.includes('[csrfHeaderName()]: value'), 'Mutacoes devem enviar CSRF.')
assert.ok(api.includes("credentials: 'include'"), 'A sessao deve ser a unica fonte do ator.')
assert.ok(api.includes('throw normalizeApiError(error)'), 'Falhas nao podem ser convertidas em sucesso ou vazio.')

assert.ok(types.includes("tipo: 'FOTO' | 'VIDEO'"), 'A fila de midia nao pode tipar Story.')
assert.ok(!types.includes("'FOTO' | 'VIDEO' | 'STORY'"), 'Story nao pode integrar o contrato V3 da fila.')
assert.ok(detail.includes("item.tipo === 'VIDEO' ? 'RESTRITA_18'"), 'Video aprovado deve permanecer RESTRITA_18.')
assert.ok(detail.includes('Sempre RESTRITA_18'), 'A interface deve informar a classificacao fixa do video.')
assert.ok(detail.includes('Cada decisão afeta somente a mídia identificada'), 'As decisoes de midia devem ser independentes.')
assert.ok(detail.includes('Stories não integram esta fila'), 'A interface deve declarar Story fora da fila.')
assert.ok(detail.includes("action: 'APROVAR'") && detail.includes("action: 'REPROVAR'"), 'Aprovar e rejeitar devem permanecer disponiveis.')
assert.ok(detail.includes('Motivo obrigatório'), 'Rejeicao deve coletar motivo.')
assert.ok(detail.includes('disabled={busy'), 'A interface deve bloquear repeticao durante a mutacao.')
assert.ok(detail.includes('<ContractState error={error}'), 'Falha de carregamento deve ser explicita e repetivel.')
assert.ok(detail.includes('listAdminAdHistory'), 'O historico auditavel deve ser carregado no detalhe.')
assert.ok(detail.includes("canReadMedia ? listAdminAdMedia(anuncioId) : Promise.resolve(null)"), 'A tela nao deve falhar inteira quando o perfil nao possui MIDIA_REVISAR.')
assert.ok(detail.includes("canReadHistory ? listAdminAdHistory(anuncioId) : Promise.resolve([])"), 'O historico deve respeitar as autoridades granulares.')
assert.ok(detail.includes('Seu perfil não possui permissão para revisar mídias.'), 'A ausencia de permissao de midia nao pode parecer fila vazia.')
assert.ok(list.includes('PAGE_SIZE = 20') && list.includes('totalPages'), 'A fila deve manter paginacao backend.')
assert.ok(list.includes('statusModeracao') && list.includes('termo'), 'A fila deve manter filtros e busca.')

console.log('Moderacao V3 de anuncios e midias: contrato frontend aprovado.')
