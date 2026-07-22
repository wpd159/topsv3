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
const documents = source('features/admin-anuncios/admin-anuncio-documentos.tsx')
const premium = source('features/admin-anuncios/admin-anuncio-premium.tsx')
const story = source('features/admin-anuncios/admin-anuncio-story.tsx')
const edit = source('features/admin-anuncios/admin-anuncio-edit-form.tsx')
const editPage = source('app/(painel-admin)/admin/anuncios/[id]/editar/page.tsx')
const api = source('features/admin-anuncios/api.ts')
const types = source('features/admin-anuncios/types.ts')
const queueContext = source('features/admin-anuncios/queue-context.ts')

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
  '`/anuncios/${encodeURIComponent(id)}/documentos`',
  '`/premium/anuncios/${encodeURIComponent(id)}/beneficios`',
  '`/premium/anuncios/${encodeURIComponent(anuncioId)}/ativacoes/lote`',
  "request<AdminStorySelection>('/stories/selecao')",
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
assert.ok(detail.includes('MediaVisibilitySelector') && detail.includes('type="radio"') && detail.includes("['LIVRE', 'RESTRITA_18']"), 'Fotos devem oferecer classificacao individual, clicavel e explicita.')
assert.ok(detail.includes("filter((item) => String(item.tipo) !== 'STORY')"), 'Story nao pode entrar na secao de midias.')
assert.ok(detail.includes("action: 'APROVAR'") && detail.includes("action: 'REPROVAR'"), 'Aprovar e rejeitar devem permanecer disponiveis.')
assert.ok(detail.includes('Motivo obrigatório'), 'Rejeicao deve coletar motivo.')
assert.ok(detail.includes('disabled={busy'), 'A interface deve bloquear repeticao durante a mutacao.')
assert.ok(detail.includes('<ContractState error={error}'), 'Falha de carregamento deve ser explicita e repetivel.')
assert.ok(detail.includes('listAdminAdHistory'), 'O historico auditavel deve ser carregado no detalhe.')
assert.ok(detail.includes("canReadMedia ? listAdminAdMedia(anuncioId) : Promise.resolve(null)"), 'A tela nao deve falhar inteira quando o perfil nao possui MIDIA_REVISAR.')
assert.ok(detail.includes("canReadHistory ? listAdminAdHistory(anuncioId) : Promise.resolve([])"), 'O historico deve respeitar as autoridades granulares.')
assert.ok(detail.includes('Seu perfil não possui MIDIA_REVISAR.'), 'A ausencia de permissao de midia nao pode parecer fila vazia.')
assert.ok(list.includes('ADMIN_AD_PAGE_SIZE_OPTIONS') && list.includes('totalPages'), 'A fila deve manter paginacao backend configuravel.')
assert.ok(list.includes('statusModeracao') && list.includes('termo'), 'A fila deve manter filtros e busca.')
assert.ok(!list.includes('Estado do anúncio'), 'O filtro redundante de estado do anuncio deve ser removido.')
assert.ok(list.includes('const duplicated = item.status === item.statusModeracao'), 'O badge APROVADO duplicado deve ser omitido.')
assert.deepEqual(
  [...queueContext.matchAll(/\{ value: '([^']+)', label:/g)].map((match) => match[1]),
  ['MAIS_RECENTES', 'MAIS_ANTIGOS', 'MAIS_VISUALIZACOES', 'MENOS_VISUALIZACOES', 'MAIS_CLIQUES_WHATSAPP', 'MENOS_CLIQUES_WHATSAPP'],
  'A fila deve expor exatamente as seis ordenacoes canonicas.',
)
assert.ok(queueContext.includes('[20, 30, 50, 100]'), 'Itens por pagina devem reproduzir as opcoes reais da producao.')
assert.ok(list.includes('md:hidden') && list.includes('hidden overflow-x-auto') && list.includes('md:block'), 'A fila deve oferecer apresentacoes distintas para 390 px e desktop.')
assert.ok(detail.includes('overflow-x-auto') && detail.includes('sm:grid-cols-2'), 'O detalhe deve preservar navegacao e conteudo sem overflow em viewport estreito.')
for (const queueField of ['miniaturaUrl', 'beneficiosPremiumVigentes', 'visualizacoes', 'cliquesWhatsapp', 'criadoEm']) {
  assert.ok(list.includes(queueField), `Campo operacional ausente na fila: ${queueField}`)
}
assert.ok(!list.includes('.cpf') && !list.includes('.whatsapp'), 'A fila nao pode exibir CPF ou WhatsApp.')

for (const ownerField of ['nomeCivil', '.email', '.cpf', '.whatsapp', '.status']) {
  assert.ok(detail.includes(ownerField), `Dado integral do proprietario ausente no detalhe: ${ownerField}`)
}
assert.ok(detail.includes('https://wa.me/'), 'O WhatsApp do proprietario deve abrir conversa com numero normalizado.')
assert.ok(detail.includes("views.situacao === 'HISTORICO_PENDENTE' ? '—'"), 'Historico pendente nao pode virar zero.')
assert.ok(detail.includes('ad.metricas.ctr == null ?'), 'CTR indisponivel nao pode virar zero.')
assert.ok(detail.includes('AdminAnuncioDocumentos') && detail.includes('AdminAnuncioPremium'), 'Documentos e Premium devem compor o detalhe canonico.')
assert.ok(detail.includes('Região') && detail.includes('enderecoResumido'), 'A Regiao canonica deve aparecer no detalhe.')
assert.ok(detail.includes('getAdminAdQueueNavigation') && detail.includes('Anterior') && detail.includes('Próximo'), 'O detalhe deve navegar no contexto da fila.')
assert.ok(detail.includes('Próximo da fila') && detail.includes('Fim da fila'), 'A decisao deve oferecer avancar manualmente ou encerrar a fila.')
assert.ok(api.includes('currentPage * context.size + index + 1'), 'A posicao deve considerar pagina e tamanho atuais.')
assert.ok(api.includes('context.page - 1, context.page + 1'), 'A navegacao pode consultar apenas paginas adjacentes, sem carregar toda a fila.')

assert.ok(documents.includes('getAdminDocumentTemporaryUrl'), 'Documento deve ser aberto por URL temporaria administrativa.')
assert.ok(documents.includes('Visualizar') && documents.includes('Baixar'), 'Documento deve permitir visualizacao e download autorizados.')
assert.ok(!documents.includes('objectKey') && !documents.includes('bucket'), 'Documento nao pode expor bucket ou object key.')

assert.ok(premium.includes('listAdminPremiumCatalog'), 'Beneficios e duracoes devem vir do catalogo backend.')
assert.ok(premium.includes('activateAdminPremiumBatch') && premium.includes('cancelAdminPremium'), 'ADMIN deve ativar varios e desativar beneficios.')
assert.ok(premium.includes('type="checkbox"') && premium.includes('selectedItems'), 'O catalogo deve ser visivel e permitir selecao multipla.')
assert.ok(premium.includes('latestByCode') && premium.includes('latest?.statusCalculado'), 'O catalogo deve exibir o estado exato da ativacao mais recente.')
assert.ok(premium.includes('MODERADOR possui acesso somente para leitura.'), 'MODERADOR deve permanecer somente leitura no Premium.')
assert.ok(premium.includes('activationKey.current ?? operationKey()') && premium.includes('cancellationKeys.current[item.id] ?? operationKey()'), 'Retry deve reutilizar a mesma Idempotency-Key.')
assert.ok(!premium.match(/duracaoDias\s*:\s*(1|7|14|30)/), 'Duracoes nao podem ser hardcoded na interface.')

assert.ok(story.includes('Colocar nos Stories') && story.includes('Remover dos Stories'), 'A acao de Story administrativo deve existir.')
assert.ok(story.includes('selection?.expiraEm') && story.includes('Restrita 18+'), 'Story deve mostrar expiracao e classificacao restrita.')
assert.ok(story.includes('disabled={busy}'), 'Story deve bloquear duplo clique.')

assert.ok(editPage.includes('AdminAnuncioEditForm') && !editPage.includes('moderation-v2'), 'A edicao deve usar contrato administrativo V3 proprio.')
assert.ok(edit.includes('updateAdminAd') && edit.includes("session?.papeis.includes('ADMIN')"), 'Somente ADMIN deve editar pelo adapter canonico.')
for (const field of ['titulo', 'descricao', 'categoria', 'preco', 'uf', 'cidade', 'bairro', 'enderecoResumido', 'servicos', 'locaisAtendimento', 'whatsapp']) {
  assert.ok(edit.includes(field), `Campo canonico ausente no editor administrativo: ${field}`)
}

console.log('Moderacao V3 de anuncios e midias: contrato frontend aprovado.')
