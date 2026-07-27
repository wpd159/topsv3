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
const premiumQuick = source('features/admin-anuncios/admin-anuncio-premium-rapido.tsx')
const story = source('features/admin-anuncios/admin-anuncio-story.tsx')
const edit = source('features/admin-anuncios/admin-anuncio-edit-form.tsx')
const editPage = source('app/(painel-admin)/admin/anuncios/[id]/editar/page.tsx')
const api = source('features/admin-anuncios/api.ts')
const removalAdapter = api.slice(
  api.indexOf('export function removeAdminAd'),
  api.indexOf('export function blockAdminAd'),
)
const types = source('features/admin-anuncios/types.ts')
const queueContext = source('features/admin-anuncios/queue-context.ts')
const searchableSelect = source('features/anuncio-wizard/components/searchable-select.tsx')
const wizardUtils = source('features/anuncio-wizard/wizard-utils.ts')
const publicCatalogApi = source('lib/public-catalog-api.ts')
const publicCatalogActions = source('app/(painel-admin)/admin/anuncios/actions.ts')

assert.ok(listPage.includes('AdminAnunciosList'), 'A rota administrativa deve usar a fila V3.')
assert.ok(detailPage.includes('AdminAnuncioModeracao'), 'O detalhe deve usar a moderacao V3.')
assert.ok(!listPage.includes('moderation-v2') && !detailPage.includes('moderation-v2'), 'A area canonica nao pode importar a V2.')
assert.ok(legacyListPage.includes("redirect('/admin/anuncios')"), 'A rota antiga deve redirecionar para a fila canonica.')
assert.ok(legacyDetailPage.includes('`/admin/anuncios/${encodeURIComponent(anuncioId)}`'), 'O detalhe antigo deve redirecionar para o detalhe canonico.')
assert.ok(sidebar.includes("href: '/admin/anuncios'"), 'A sidebar deve abrir a area canonica.')
assert.ok(!sidebar.includes("href: '/admin/moderacao-v2'"), 'A sidebar nao pode abrir a V2.')
assert.ok(!sidebar.includes("href: '/admin/stories'") && !sidebar.includes('Stories administrativos'), 'A sidebar nao deve exibir o atalho de Stories administrativos.')

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
  '`/anuncios/${encodeURIComponent(id)}/aprovar`',
  '`/moderacao/revisoes/${encodeURIComponent(reviewId)}/decidir`',
  '`/midias/${encodeURIComponent(mediaId)}/decidir`',
  '`/anuncios/${encodeURIComponent(anuncioId)}/midias/decisoes`',
  '`/midias/${encodeURIComponent(mediaId)}/reclassificar`',
  "request('/anuncios/filtros/localidades')",
  '`/anuncios/${encodeURIComponent(id)}/reativar`',
  '`/anuncios/${encodeURIComponent(id)}/remocao-logica`',
  '`/anuncios/${encodeURIComponent(id)}/bloqueio-juridico`',
  '`/anuncios/${encodeURIComponent(id)}/bloqueio-juridico/usuario`',
  '`/anuncios/${encodeURIComponent(id)}/desbloqueio-juridico`',
  '`/anuncios/${encodeURIComponent(id)}/desbloqueio-juridico/usuario`',
]) {
  assert.ok(api.includes(contract), `Contrato V3 ausente no adapter: ${contract}`)
}
assert.ok(api.includes('adminApiUrl(path)'), 'O adapter deve reutilizar o resolvedor administrativo.')
assert.ok(
  api.includes('anuncioId,') && api.includes('body: JSON.stringify({\n      anuncioId,'),
  'A decisao de midia deve permanecer vinculada ao anuncio exibido.',
)
assert.ok(api.includes('[csrfHeaderName()]: value'), 'Mutacoes devem enviar CSRF.')
assert.ok(api.includes("credentials: 'include'"), 'A sessao deve ser a unica fonte do ator.')
assert.ok(api.includes('throw normalizeApiError(error)'), 'Falhas nao podem ser convertidas em sucesso ou vazio.')

assert.ok(types.includes("tipo: 'FOTO' | 'VIDEO'"), 'A fila de midia nao pode tipar Story.')
assert.ok(!types.includes("'FOTO' | 'VIDEO' | 'STORY'"), 'Story nao pode integrar o contrato V3 da fila.')
assert.ok(detail.includes("item.tipo === 'VIDEO' ? 'RESTRITA_18'"), 'Video aprovado deve permanecer RESTRITA_18.')
assert.ok(detail.includes('Sempre RESTRITA_18'), 'A interface deve informar a classificacao fixa do video.')
assert.ok(detail.includes('PendingPhotoDecisionSelector'), 'Foto pendente deve preservar a classificacao local.')
assert.ok(detail.includes('MediaVisibilitySelector') && detail.includes("['LIVRE', 'RESTRITA_18']"), 'Foto finalizada deve preservar a reclassificacao individual.')
assert.ok(detail.includes('reclassifyAdminMedia') && detail.includes("kind: 'RECLASSIFY'"), 'Foto finalizada deve usar a operacao canonica propria de reclassificacao.')
assert.ok(detail.includes('deletablePhoto') && detail.includes("item.status !== 'REMOVIDA'"), 'Toda foto existente deve preservar a exclusao fisica.')
assert.ok(detail.includes('PhotoDeleteDialog') && detail.includes('Excluir foto'), 'A exclusao deve exigir confirmacao propria e independente da classificacao.')
assert.ok(detail.includes("decisao: 'EXCLUIR'") && detail.includes('decideAdminPhotosBatch'), 'A exclusao individual deve reutilizar o contrato canonico do lote.')
assert.ok(detail.includes('photoDeleteLock.current') && detail.includes("result.resultado === 'FALHA'"), 'A exclusao deve bloquear duplo clique e nao simular sucesso em falha.')
assert.ok(detail.includes('aspect-[16/7]') && detail.includes('object-contain'), 'A previa deve ser compacta sem cortar a midia.')
assert.ok(detail.includes("title: 'Aplicar e aprovar vídeo'") && detail.includes('Aplicar classificação'), 'Video e foto finalizada devem preservar suas operacoes fora do lote.')
assert.ok(!detail.includes('Rejeitar foto'), 'Foto pendente nao pode manter a rejeicao logica anterior.')
assert.ok(detail.includes('Confirmar decisões das fotos ({selectedPhotoCount})'), 'O lote deve usar um unico botao com a quantidade selecionada.')
assert.ok(detail.includes('<PhotoBatchDialog') && detail.includes('Confirmar decisões das fotos'), 'O lote deve usar um unico modal de confirmacao.')
assert.ok(detail.includes('decideAdminPhotosBatch') && detail.includes('pendingPhotos.map'), 'O frontend deve enviar uma unica requisicao batch.')
const localPhotoSelection = detail.slice(
  detail.indexOf('function selectPhotoDecision'),
  detail.indexOf('async function confirmPhotoBatch'),
)
assert.ok(!localPhotoSelection.includes('decideAdminPhotosBatch') && !localPhotoSelection.includes('decideAdminMedia'), 'Selecionar uma opcao nao pode chamar o backend.')
assert.ok(detail.includes('allPendingPhotosSelected') && detail.includes('selectedPhotoCount === pendingPhotos.length'), 'Todas as fotos pendentes devem receber decisao antes da confirmacao.')
assert.ok(!localPhotoSelection.includes("'EXCLUIR'") && detail.includes("choice === 'RESTRITA_18'"), 'A exclusao deve permanecer independente da classificacao.')
assert.ok(detail.includes("choice === 'RESTRITA_18' ? previous?.observacao ?? '' : ''"), 'Trocar para LIVRE deve limpar observacao residual.')
assert.ok(detail.includes("decision.classificacao === 'RESTRITA_18'") && detail.includes('decision.observacao.trim() || undefined'), 'Somente RESTRITA_18 pode enviar observacao individual.')
assert.ok(detail.includes("filter((item) => item.resultado === 'FALHA')") && detail.includes('failedIds.has(mediaId)'), 'Falha parcial deve preservar somente itens que exigem retry.')
assert.ok(detail.includes('else delete next[intent.media.id]'), 'Falha deve restaurar o estado persistido da classificacao, sem simular sucesso.')
assert.ok(detail.includes("normalized.kind === 'CONFLICT'") && detail.includes('O estado da mídia mudou.'), 'Conflito real deve atualizar o detalhe e explicar a mudanca de estado.')
assert.ok(detail.includes('preserveSelection.mode') && detail.includes('selectionStillApplies'), 'A selecao deve ser preservada somente enquanto a decisao continuar aplicavel.')
assert.ok(detail.includes('await load()') && detail.includes('setIntent(null)'), 'Sucesso deve atualizar o card antes de fechar o modal.')
assert.ok(detail.includes('mediaOrdinal[item.id]') && !detail.includes('item.ordem + 1'), 'Capa e galeria nao podem repetir a mesma numeracao visual.')
assert.ok(detail.includes("filter((item) => String(item.tipo) !== 'STORY')"), 'Story nao pode entrar na secao de midias.')
assert.ok(detail.includes("action: 'APROVAR'") && detail.includes("action: 'REPROVAR'"), 'Aprovar e rejeitar devem permanecer disponiveis.')
assert.ok(detail.includes('Motivo obrigatório'), 'Rejeicao deve coletar motivo.')
assert.ok(detail.includes("selected === 'RESTRITA_18'") && detail.includes('Observações'), 'Somente foto RESTRITA_18 deve oferecer observacao no lote.')
assert.ok(detail.includes("useEffect(() => { setReason('') }, [intent])"), 'Trocar a decisao deve limpar imediatamente qualquer texto residual.')
assert.ok(api.includes('observacao: observacao?.trim() || undefined'), 'O adapter deve omitir observacao vazia sem fabricar texto.')
assert.ok(detail.includes('disabled={busy'), 'A interface deve bloquear repeticao durante a mutacao.')
assert.ok(detail.includes('<ContractState error={error}'), 'Falha de carregamento deve ser explicita e repetivel.')
assert.ok(detail.includes('listAdminAdHistory'), 'O historico auditavel deve ser carregado no detalhe.')
assert.ok(detail.includes("canReadMedia ? listAdminAdMedia(anuncioId) : Promise.resolve(null)"), 'A tela nao deve falhar inteira quando o perfil nao possui MIDIA_REVISAR.')
assert.ok(detail.includes("canReadHistory ? listAdminAdHistory(anuncioId) : Promise.resolve([])"), 'O historico deve respeitar as autoridades granulares.')
assert.ok(detail.includes('Seu perfil não possui MIDIA_REVISAR.'), 'A ausencia de permissao de midia nao pode parecer fila vazia.')
assert.ok(list.includes('ADMIN_AD_PAGE_SIZE_OPTIONS') && list.includes('totalPages'), 'A fila deve manter paginacao backend configuravel.')
assert.ok(list.includes('SITUATION_OPTIONS') && list.includes('termo'), 'A fila deve manter o filtro operacional unico e a busca.')
assert.deepEqual(
  [...list.matchAll(/\{ value: '([^']+)', label: '[^']+' \}/g)].slice(0, 6).map((match) => match[1]),
  ['TODOS', 'PENDENTES_MODERACAO', 'APROVADOS', 'PAUSADOS', 'REJEITADOS', 'BLOQUEADOS'],
  'A fila deve expor as seis situacoes administrativas, incluindo Aprovados.',
)
assert.ok(list.includes('listAdminAdFilterLocations()'), 'Localidades devem vir do backend protegido.')
assert.ok(list.includes("uf: value === 'TODOS' ? '' : value, cidade: '', bairro: ''"), 'Trocar UF deve limpar Cidade e Bairro.')
assert.ok(list.includes("cidade: value === 'TODAS' ? '' : value, bairro: ''"), 'Trocar Cidade deve limpar Bairro.')
assert.ok(!list.includes('Slug da cidade') && !list.includes('Slug do bairro'), 'A interface nao pode exigir slugs manuais.')
assert.equal((list.match(/<SearchableSelect/g) ?? []).length, 3, 'Estado, Cidade e Bairro devem usar busca digitavel canonica.')
assert.ok(list.includes('options={stateOptions}') && list.includes('options={cityOptions}') && list.includes('options={neighborhoodOptions}'), 'Localidades devem aceitar apenas opcoes do backend.')
assert.ok(list.includes('loadLocations') && list.includes('loadSupport'), 'Falha de servico opcional nao pode esvaziar os filtros de localidades.')
assert.ok(searchableSelect.includes('role="combobox"') && searchableSelect.includes('<CommandInput'), 'A busca de localidade deve ser acessivel por teclado.')
assert.ok(searchableSelect.includes('onSelect(item.id)') && !searchableSelect.includes('onSelect(query)'), 'Texto livre nao pode ser aplicado como filtro canonico.')
assert.ok(wizardUtils.includes("normalize('NFD')") && wizardUtils.includes("toLowerCase()"), 'Busca deve ignorar acentos e caixa.')
assert.ok(list.includes('lg:grid-cols-[minmax('), 'Os oito controles devem compartilhar uma unica grade no desktop.')
assert.ok(!list.includes('Estado do anúncio'), 'O filtro redundante de estado do anuncio deve ser removido.')
assert.ok(list.includes("!(item.status === 'PUBLICADO' && item.statusModeracao === 'APROVADO')"), 'A fila deve exibir apenas Publicado depois da aprovacao.')
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
for (const ownerField of ['nomeCivil', '.email', '.whatsapp']) {
  assert.ok(list.includes(ownerField), `Dado protegido do proprietario ausente na fila: ${ownerField}`)
}
assert.ok(list.includes('https://wa.me/'), 'O WhatsApp da fila deve abrir conversa com numero normalizado.')
assert.ok(!list.includes('.cpf'), 'CPF deve permanecer fora da fila administrativa.')

assert.ok(list.includes('listAdminPremiumCatalog()'), 'O catalogo Premium deve ser carregado uma unica vez pela fila.')
assert.ok(list.includes('item.beneficiosPremium'), 'Os estados Premium devem chegar em lote com cada linha.')
assert.ok(premiumQuick.includes('activateAdminPremiumBatch') && premiumQuick.includes('cancelAdminPremium'), 'Premium rapido deve ativar e desativar pelos contratos existentes.')
assert.ok(premiumQuick.includes('item.opcoes.filter') && premiumQuick.includes('duracaoDias'), 'Duracoes do Premium rapido devem vir do backend.')
assert.ok(premiumQuick.includes('Observa') && premiumQuick.includes('(opcional)') && premiumQuick.includes('Motivo da desativa'), 'Ativacao deve aceitar observacao opcional sem alterar a justificativa da desativacao.')
assert.ok(premiumQuick.includes('observation.trim() || null'), 'Premium rapido nao pode fabricar observacao.')
assert.ok(!premiumQuick.includes('observation.trim().length < 3'), 'Premium rapido nao pode exigir observacao na ativacao.')
assert.ok(premiumQuick.includes("item?.status === 'PENDENTE'") && premiumQuick.includes('border-amber-300'), 'Ativacao pendente deve permanecer distinta de beneficio ativo.')
assert.ok(premiumQuick.includes('idempotencyKey.current ?? operationKey()'), 'Retry do Premium rapido deve reutilizar Idempotency-Key.')
assert.ok(!premiumQuick.match(/duracaoDias\s*:\s*(1|7|14|30)/), 'Premium rapido nao pode hardcodar duracoes.')

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
const decisionPanel = detail.slice(
  detail.indexOf('<h2 className="font-semibold text-zinc-950">Decisão do anúncio</h2>'),
  detail.indexOf('</aside>', detail.indexOf('<h2 className="font-semibold text-zinc-950">Decisão do anúncio</h2>')),
)
assert.ok(decisionPanel.includes('Abrir revisão'), 'O quadro de decisao deve preservar a abertura da revisao.')
for (const duplicate of ['Aprovar anúncio', 'Rejeitar anúncio', 'Reprovar anúncio']) {
  assert.ok(!decisionPanel.includes(duplicate), `A acao ${duplicate} nao pode permanecer duplicada no quadro de decisao.`)
}
assert.ok(detail.includes("const canDecideAdReview = canModerateAd && !removed && ad.status !== 'BLOQUEADO'"), 'Anuncio bloqueado ou removido nao pode exibir acoes de decisao.')
assert.ok(detail.includes("const legacyApprovalWithoutPublication = ad.status === 'APROVADO'") && detail.includes('legacyApprovalWithoutPublication || ('), 'A operacao canonica deve permanecer acessivel para regularizar pares legados APROVADO/APROVADO.')
assert.ok(detail.includes("kind: 'APPROVE_AD'") && detail.includes("kind: 'REPROVE_AD'"), 'Aprovacao e reprovacao devem permanecer como intencoes distintas.')
assert.ok(detail.includes('await approveAdminAd(ad.id)') && api.includes('export function approveAdminAd'), 'Toda aprovacao deve usar a operacao unica por anuncio.')
assert.ok(!detail.includes('AUTOMATIC_REVIEW_REASON') && !detail.includes("decideAdminReview(reviewId, 'APROVAR'"), 'A aprovacao nao pode persistir uma etapa intermediaria no frontend.')
assert.ok(detail.includes('AUTOMATIC_REPROVAL_REVIEW_REASON') && detail.includes('const refreshedAd = await getAdminAd(ad.id)'), 'A reprovacao deve continuar vinculada a uma revisao canonica.')
assert.ok(detail.includes("let reviewId = reviewOpen ? ad.revisaoAberta?.id : null"), 'Revisao ja aberta deve ser reutilizada pela reprovacao.')
assert.ok(detail.includes("await decideAdminReview(reviewId, 'REPROVAR', reason)") && detail.includes('await load()'), 'A reprovacao deve reutilizar o adapter canonico e atualizar o detalhe imediatamente.')
assert.ok(detail.includes('await revalidarCacheCatalogoPublico()'), 'A aprovacao deve invalidar o cache publico de catalogo e localidades.')
assert.ok(detail.includes('Anúncio aprovado e publicado com sucesso.'), 'O sucesso deve confirmar aprovacao e publicacao como uma unica operacao.')
assert.ok(detail.includes("!(ad.status === 'PUBLICADO' && ad.statusModeracao === 'APROVADO')"), 'Publicado deve aparecer como o unico estado principal depois da aprovacao.')
assert.ok(detail.includes('Anúncio reprovado. O anunciante foi informado sobre as alterações necessárias.'), 'A reprovacao deve confirmar a orientacao ao anunciante.')
assert.ok(detail.includes('Motivo e alterações necessárias') && detail.includes('Confirmar reprovação'), 'O modal deve exigir o motivo integral da reprovacao.')
assert.ok(detail.includes('O anúncio ficará indisponível e o anunciante receberá um e-mail com o motivo e as alterações necessárias.'), 'O modal deve explicar o efeito da reprovacao.')
assert.ok(publicCatalogApi.includes("PUBLIC_CATALOG_CACHE_TAG = 'public-catalog'") && publicCatalogApi.includes('tags: [PUBLIC_CATALOG_CACHE_TAG]'), 'Catalogo, localidades e sitemap devem compartilhar a tag canonica de cache.')
assert.ok(publicCatalogActions.includes('revalidateTag(PUBLIC_CATALOG_CACHE_TAG)'), 'A acao administrativa deve reutilizar a invalidacao de cache do Next.')
assert.ok(detail.includes("intent.kind === 'OPEN_REVIEW' || intent.kind === 'APPROVE_AD' || intent.kind === 'REPROVE_AD'") && detail.includes('O estado do anúncio mudou.'), 'Conflito 409 da decisao deve atualizar o detalhe e informar a causa correta.')
assert.ok(detail.includes('decisionLock.current') && detail.includes('disabled={headerBusy}'), 'A aprovacao deve impedir duplo clique durante a decisao.')

assert.ok(documents.includes('getAdminDocumentTemporaryUrl'), 'Documento deve ser aberto por URL temporaria administrativa.')
assert.ok(documents.includes('Visualizar') && documents.includes('Baixar'), 'Documento deve permitir visualizacao e download autorizados.')
assert.ok(!documents.includes('objectKey') && !documents.includes('bucket'), 'Documento nao pode expor bucket ou object key.')

assert.ok(premium.includes('listAdminPremiumCatalog'), 'Beneficios e duracoes devem vir do catalogo backend.')
assert.ok(premium.includes('activateAdminPremiumBatch') && premium.includes('cancelAdminPremium'), 'ADMIN deve ativar varios e desativar beneficios.')
assert.ok(premium.includes('type="checkbox"') && premium.includes('selectedItems'), 'O catalogo deve ser visivel e permitir selecao multipla.')
assert.ok(premium.includes('latestByCode') && premium.includes('latest?.statusCalculado'), 'O catalogo deve exibir o estado exato da ativacao mais recente.')
assert.ok(premium.includes('MODERADOR possui acesso somente para leitura.'), 'MODERADOR deve permanecer somente leitura no Premium.')
assert.ok(premium.includes('activationKey.current ?? operationKey()') && premium.includes('cancellationKeys.current[item.id] ?? operationKey()'), 'Retry deve reutilizar a mesma Idempotency-Key.')
assert.ok(premium.includes('observation.trim() || null') && premium.includes('Observa') && premium.includes('(opcional)'), 'Ativacao multipla deve persistir observacao opcional como null.')
assert.ok(!premium.includes('observation.trim().length < 3'), 'Ativacao multipla nao pode exigir observacao.')
assert.ok(!premium.match(/duracaoDias\s*:\s*(1|7|14|30)/), 'Duracoes nao podem ser hardcoded na interface.')

for (const action of ['Reativar', 'Bloquear anúncio', 'Bloquear anúncio e usuário', 'Desbloquear anúncio', 'Desbloquear usuário']) {
  assert.ok(detail.includes(action) || list.includes(action), `Acao administrativa ausente: ${action}`)
}
const detailHeader = detail.slice(detail.indexOf('<header'), detail.indexOf('</header>') + '</header>'.length)
for (const action of ['Aprovar anúncio', 'Reprovar anúncio', 'Reativar', 'Bloquear anúncio', 'Bloquear anúncio e usuário', 'Desbloquear anúncio', 'Desbloquear usuário', 'Editar anúncio']) {
  assert.ok(detailHeader.includes(action), `Acao administrativa deve permanecer no cabecalho: ${action}`)
}
const defaultHeaderActionOrder = ['Aprovar anúncio', 'Reprovar anúncio', 'Bloquear anúncio', 'Bloquear anúncio e usuário', "Excluir an\\u00fancio", 'Editar anúncio']
for (let index = 1; index < defaultHeaderActionOrder.length; index += 1) {
  assert.ok(
    detailHeader.indexOf(defaultHeaderActionOrder[index - 1]) < detailHeader.indexOf(defaultHeaderActionOrder[index]),
    `Ordem do cabecalho incorreta entre ${defaultHeaderActionOrder[index - 1]} e ${defaultHeaderActionOrder[index]}.`,
  )
}
assert.ok(list.includes("item.status === 'PAUSADO'") && list.includes("item.statusModeracao === 'APROVADO'") && list.includes("item.anunciante?.status === 'ATIVO'"), 'A fila deve exibir reativacao somente na transicao canonica.')
assert.ok(detail.includes('LegalActionDialog') && detail.includes('legalBusy'), 'Acoes juridicas devem ter confirmacao e protecao contra duplo clique.')
assert.ok(detail.includes("isAdmin && canModerateAd"), 'Somente ADMIN com ANUNCIO_MODERAR pode executar intervencao juridica.')
assert.ok(detail.includes('categoria obrigatória') || detail.includes('Categoria obrigatória'), 'Bloqueio deve exigir categoria.')
assert.ok(detail.includes('Motivo obrigatório') && detail.includes('Observação interna opcional'), 'Bloqueio deve coletar motivo e observacao interna opcional.')
assert.ok(detail.includes('ad.bloqueioJuridico') && detailHeader.includes('ad.status'), 'Cabecalho protegido deve mostrar o estado juridico por meio do status canonico.')
assert.ok(detailHeader.includes('Ações jurídicas e administrativas') && detailHeader.includes('xl:flex-nowrap') && detailHeader.includes('flex-wrap'), 'Acoes devem ficar em uma linha no desktop amplo e quebrar de forma organizada sem overflow abaixo desse breakpoint.')
assert.ok(!detailHeader.includes('overflow-x-auto'), 'O cabecalho nao pode criar rolagem horizontal em viewport estreito.')
assert.ok(detail.includes("ad.statusModeracao === 'PENDENTE'") && detail.includes("ad.status === 'PENDENTE_REVISAO' || reviewOpen"), 'A aprovacao deve aparecer somente para dados pendentes ou revisao aberta pendente.')
assert.ok(!detail.includes('Situação jurídica') && !detail.includes('legal-status-title'), 'O bloco juridico separado nao pode permanecer no corpo.')
assert.ok(detail.includes('item.categoria') && detail.includes('item.motivo') && detail.includes('item.observacaoInterna') && detail.includes('item.atorId') && detail.includes('item.criadoEm') && detail.includes('item.requestId'), 'Historico deve preservar categoria, motivo, responsavel, data e requestId juridicos.')
assert.ok(detail.includes('await load()') && !detail.includes('status: \'BLOQUEADO\''), 'Interface juridica deve atualizar somente apos resposta do backend.')
assert.ok(detail.includes('RemovalDialog') && detail.includes('removalBusy'), 'Exclusao logica deve ter confirmacao propria e protecao contra duplo clique.')
assert.ok(detail.includes('await removeAdminAd(ad.id, reason.trim())') && detail.includes('await load()'), 'Tela deve refletir REMOVIDO somente depois da resposta do backend.')
assert.ok(detail.includes("const canRemove = canManageLegalStatus && !removed && ad.status !== 'BLOQUEADO'"), 'Somente ADMIN autorizado deve ver a remocao em transicao valida.')
assert.ok(detail.includes("ad.status === 'REMOVIDO'") && detail.includes('disabledReason={removed'), 'REMOVIDO deve bloquear novas ativacoes Premium na interface.')
assert.ok(detail.includes('reason.trim().length < 5') && detail.includes('maxLength={1000}'), 'Exclusao deve exigir motivo dentro do contrato.')
assert.ok(detail.includes('border border-red-950 bg-red-700'), 'Exclusao deve possuir tratamento destrutivo distinto do bloqueio juridico.')
assert.ok(detail.includes('excluir\\u00e1 definitivamente suas fotos e v\\u00eddeos') && detail.includes('hist\\u00f3rico administrativo ser\\u00e1 preservado'), 'Confirmacao deve informar a limpeza definitiva das midias e a preservacao do historico.')
assert.ok(!detail.includes('benef\\u00edcios e m\\u00eddias permanecer\\u00e3o preservados'), 'Confirmacao antiga nao pode afirmar que as midias serao preservadas.')
assert.ok(detailHeader.includes("Excluir an\\u00fancio"), 'Excluir anuncio deve permanecer no cabecalho administrativo.')
assert.ok(api.includes('JSON.stringify({ motivo: motivo.trim() })'), 'Adapter deve enviar somente o motivo sanitizado da remocao.')
assert.ok(removalAdapter.includes("method: 'POST'") && !removalAdapter.includes("method: 'DELETE'"), 'Remocao administrativa deve usar a mutacao logica, nunca DELETE.')

assert.ok(story.includes('Colocar nos Stories') && story.includes('Remover dos Stories'), 'A acao de Story administrativo deve existir.')
assert.ok(story.includes('selection?.expiraEm') && story.includes('Restrita 18+'), 'Story deve mostrar expiracao e classificacao restrita.')
assert.ok(story.includes('disabled={busy}'), 'Story deve bloquear duplo clique.')

assert.ok(editPage.includes('AdminAnuncioEditForm') && !editPage.includes('moderation-v2'), 'A edicao deve usar contrato administrativo V3 proprio.')
assert.ok(edit.includes('updateAdminAd') && edit.includes("session?.papeis.includes('ADMIN')"), 'Somente ADMIN deve editar pelo adapter canonico.')
for (const field of ['titulo', 'descricao', 'categoria', 'preco', 'uf', 'cidade', 'bairro', 'enderecoResumido', 'servicos', 'locaisAtendimento', 'whatsapp']) {
  assert.ok(edit.includes(field), `Campo canonico ausente no editor administrativo: ${field}`)
}

console.log('Moderacao V3 de anuncios e midias: contrato frontend aprovado.')
