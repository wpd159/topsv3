import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import ts from 'typescript'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const sourceRoot = path.resolve(scriptDirectory, '../src')
const adminRouteRoot = path.resolve(sourceRoot, 'app/(painel-admin)/admin')

function source(relativePath) {
  return readFileSync(path.resolve(sourceRoot, relativePath), 'utf8')
}

function uniqueSourceBlock(contents, startMarker, endMarker) {
  const start = contents.indexOf(startMarker)
  assert.notEqual(start, -1, `Inicio do bloco nao localizado: ${startMarker}`)
  assert.equal(
    contents.indexOf(startMarker, start + startMarker.length),
    -1,
    `Inicio do bloco localizado mais de uma vez: ${startMarker}`,
  )
  const end = contents.indexOf(endMarker, start + startMarker.length)
  assert.notEqual(end, -1, `Fim do bloco nao localizado: ${endMarker}`)
  assert.equal(
    contents.indexOf(endMarker, end + endMarker.length),
    -1,
    `Fim do bloco localizado mais de uma vez: ${endMarker}`,
  )
  return contents.slice(start, end)
}

const listPage = source('app/(painel-admin)/admin/anuncios/page.tsx')
const detailPage = source('app/(painel-admin)/admin/anuncios/[id]/page.tsx')
const legacyListPage = source('app/(painel-admin)/admin/moderacao-v2/page.tsx')
const legacyDetailPage = source('app/(painel-admin)/admin/moderacao-v2/[anuncioId]/page.tsx')
const sidebar = source('app/(painel-admin)/admin/components/sidebar/sidebar-links.tsx')
const list = source('features/admin-anuncios/admin-anuncios-list.tsx')
const detail = source('features/admin-anuncios/admin-anuncio-moderacao.tsx')
const uploader = source('features/admin-anuncios/admin-anuncio-midia-uploader.tsx')
const documents = source('features/admin-anuncios/admin-anuncio-documentos.tsx')
const reviewGrid = source('features/admin-documentos/admin-kyc-review-grid.tsx')
const premium = source('features/admin-anuncios/admin-anuncio-premium.tsx')
const premiumQuick = source('features/admin-anuncios/admin-anuncio-premium-rapido.tsx')
const storiesPage = source('app/(painel-admin)/admin/stories/page.tsx')
const adminStories = source('components/stories/admin-stories-management.tsx')
const adminStoriesApi = source('lib/admin-story-management-api.ts')
const edit = source('features/admin-anuncios/admin-anuncio-edit-form.tsx')
const editPage = source('app/(painel-admin)/admin/anuncios/[id]/editar/page.tsx')
const api = source('features/admin-anuncios/api.ts')
const apiContract = source('lib/api-contract.ts')
const paginationBlock = uniqueSourceBlock(
  api,
  'const ADMIN_AD_MEDIA_PAGE_SIZE',
  'export async function listAdminAdMedia',
)
const uploadAdapter = api.slice(
  api.indexOf('export async function uploadAdminAdMedia'),
  api.indexOf('export async function listAdminAdHistory'),
)
const uploadSubmit = uploader.slice(
  uploader.indexOf('async function submit'),
  uploader.indexOf('\n  return ('),
)
const uploadFailure = uploadSubmit.slice(
  uploadSubmit.indexOf('} catch (uploadError)'),
  uploadSubmit.indexOf('} finally'),
)
const removalAdapter = api.slice(
  api.indexOf('export function removeAdminAd'),
  api.indexOf('export function blockAdminAd'),
)
const removalDialog = detail.slice(
  detail.indexOf('function RemovalDialog'),
  detail.indexOf('function MediaVisibilitySelector'),
)
const removalFlow = detail.slice(
  detail.indexOf('async function confirmRemoval'),
  detail.indexOf('const reviewOpen'),
)
const types = source('features/admin-anuncios/types.ts')
const queueContext = source('features/admin-anuncios/queue-context.ts')
const searchableSelect = source('features/anuncio-wizard/components/searchable-select.tsx')
const wizardUtils = source('features/anuncio-wizard/wizard-utils.ts')
const publicCatalogApi = source('lib/public-catalog-api.ts')
const publicCatalogActions = source('app/(painel-admin)/admin/anuncios/actions.ts')
const creditsPage = source('app/(painel-admin)/admin/creditos/page.tsx')
const monetizationNavigation = source('lib/admin-monetizacao-navigation.ts')

assert.ok(listPage.includes('AdminAnunciosList'), 'A rota administrativa deve usar a fila V3.')
assert.ok(detailPage.includes('AdminAnuncioModeracao'), 'O detalhe deve usar a moderacao V3.')
assert.ok(!listPage.includes('moderation-v2') && !detailPage.includes('moderation-v2'), 'A area canonica nao pode importar a V2.')
assert.ok(legacyListPage.includes("redirect('/admin/anuncios')"), 'A rota antiga deve redirecionar para a fila canonica.')
assert.ok(legacyDetailPage.includes('`/admin/anuncios/${encodeURIComponent(anuncioId)}`'), 'O detalhe antigo deve redirecionar para o detalhe canonico.')
assert.ok(sidebar.includes("href: '/admin/anuncios'"), 'A sidebar deve abrir a area canonica.')
assert.ok(!sidebar.includes("href: '/admin/moderacao-v2'"), 'A sidebar nao pode abrir a V2.')
const sidebarEntries = [...sidebar.matchAll(/label:\s*'([^']+)'[\s\S]*?href:\s*'([^']+)'[\s\S]*?section:\s*'([^']+)'/g)]
  .map((match) => ({ label: match[1], href: match[2], section: match[3] }))
const storySidebarEntries = sidebarEntries.filter((item) => item.label === 'Gestão de Stories' || item.href.includes('/admin/stories'))
assert.equal(storySidebarEntries.length, 1, 'A sidebar deve possuir exatamente uma entrada administrativa de Stories.')
assert.deepEqual(storySidebarEntries[0], {
  label: 'Gestão de Stories',
  href: '/admin/stories',
  section: 'Operação',
})
const operationEntries = sidebarEntries.filter((item) => item.section === 'Operação')
const storyOperationIndex = operationEntries.findIndex((item) => item.href === '/admin/stories')
assert.equal(operationEntries[storyOperationIndex - 1]?.label, 'Anúncios', 'Gestão de Stories deve ficar depois de Anúncios.')
assert.equal(operationEntries[storyOperationIndex + 1]?.label, 'Usuários', 'Gestão de Stories deve ficar antes de Usuários.')
assert.ok(!existsSync(path.resolve(adminRouteRoot, 'stories/selecao')), 'A rota /admin/stories/selecao não pode existir.')
assert.ok(!existsSync(path.resolve(adminRouteRoot, 'stories-v2')), 'A rota /admin/stories-v2 não pode existir.')
const storyRouteDirectories = readdirSync(adminRouteRoot, { withFileTypes: true })
  .filter((entry) => entry.isDirectory() && entry.name.toLowerCase().includes('stories'))
  .map((entry) => entry.name)
assert.deepEqual(storyRouteDirectories, ['stories'], 'Não pode existir rota administrativa paralela de Stories.')
assert.ok(monetizationNavigation.includes("'stories'"), 'A navegação comercial deve manter a aba Stories.')
assert.ok(creditsPage.includes('adminMonetizacaoAba(searchParams)') && creditsPage.includes('adminMonetizacaoQuery(searchParams, value)'), 'A configuração comercial deve permanecer em /admin/creditos?aba=stories.')

for (const contract of [
  "request(`/anuncios?${query.toString()}`)",
  "request<AdminAdDetail>(`/anuncios/${encodeURIComponent(id)}`)",
  '`/anuncios/${encodeURIComponent(id)}/proprietario`',
  '`/anuncios/${encodeURIComponent(id)}/midias?page=${page}&size=${ADMIN_AD_MEDIA_PAGE_SIZE}`',
  '`/anuncios/${encodeURIComponent(id)}/midias`',
  '`/anuncios/${encodeURIComponent(id)}/historico-moderacao`',
  '`/anuncios/${encodeURIComponent(id)}/documentos`',
  '`/premium/anuncios/${encodeURIComponent(id)}/beneficios`',
  '`/premium/anuncios/${encodeURIComponent(anuncioId)}/ativacoes/lote`',
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
assert.ok(api.includes('const ADMIN_AD_MEDIA_PAGE_SIZE = 50'), 'A leitura de midias deve respeitar o limite de pagina do backend.')
assert.ok(api.includes('const ADMIN_AD_MEDIA_MAX_PAGES = 20'), 'A leitura administrativa deve rejeitar mais de 20 paginas.')
assert.ok(api.includes('for (let requestedPage = 1; requestedPage < baseline.totalPages; requestedPage += 1)'), 'A coleta deve percorrer um total fixo de paginas, sem depender de metadado mutavel para continuar.')
assert.ok(api.includes('page.page === requestedPage') && api.includes('page.totalPages !== baseline.totalPages'), 'Cada pagina deve ser validada contra a requisicao e a linha de base autoritativa.')
assert.ok(api.includes('itens.length !== baseline.totalElements') && api.includes('uniqueIds.size !== itens.length'), 'Colecao incompleta ou duplicada deve falhar explicitamente.')

const paginationTypeScript = `
    import assert from 'node:assert/strict'
    class ApiContractError extends Error {
      constructor(message, kind, status, retryable, requestId, code) {
        super(message)
        this.kind = kind
        this.status = status
        this.retryable = retryable
        this.requestId = requestId
        this.code = code
      }
    }
    ${paginationBlock}

    const itens = Array.from({ length: 121 }, (_, index) => ({
      id: String(index).padStart(3, '0'),
      ordem: index,
    }))
    const paginas = [
      { itens: itens.slice(0, 50), page: 0, size: 50, totalElements: 121, totalPages: 3, last: false },
      { itens: itens.slice(50, 100), page: 1, size: 50, totalElements: 121, totalPages: 3, last: false },
      { itens: itens.slice(100), page: 2, size: 50, totalElements: 121, totalPages: 3, last: true },
    ]
    const requestedPages = []
    const collected = await collectAdminAdMediaPages(paginas[0], async (page) => {
      requestedPages.push(page)
      return paginas[page]
    })
    assert.deepEqual(requestedPages, [1, 2])
    assert.deepEqual(collected.itens.map((item) => item.id), itens.map((item) => item.id))
    assert.equal(collected.totalElements, 121)
    assert.equal(collected.totalPages, 3)

    await assert.rejects(
      collectAdminAdMediaPages(paginas[0], async (page) => {
        if (page !== 1) return paginas[page]
        return {
          ...paginas[1],
          itens: [...paginas[1].itens.slice(0, -1), itens[0]],
        }
      }),
      (error) => error.code === 'ADMIN_MEDIA_PAGINATION_INVALID',
    )

    let wrongPageRequests = 0
    await assert.rejects(
      collectAdminAdMediaPages(
        { itens: itens.slice(0, 50), page: 0, size: 50, totalElements: 51, totalPages: 2, last: false },
        async () => {
          wrongPageRequests += 1
          return { itens: itens.slice(50, 51), page: 0, size: 50, totalElements: 51, totalPages: 2, last: true }
        },
      ),
      (error) => error.code === 'ADMIN_MEDIA_PAGINATION_INVALID',
    )
    assert.equal(wrongPageRequests, 1)

    let inconsistentMetadataRequests = 0
    await assert.rejects(
      collectAdminAdMediaPages(
        { itens: itens.slice(0, 50), page: 0, size: 50, totalElements: 101, totalPages: 3, last: false },
        async () => {
          inconsistentMetadataRequests += 1
          return { itens: itens.slice(50, 51), page: 1, size: 50, totalElements: 51, totalPages: 2, last: true }
        },
      ),
      (error) => error.code === 'ADMIN_MEDIA_PAGINATION_INVALID',
    )
    assert.equal(inconsistentMetadataRequests, 1)

    let incompleteRequests = 0
    await assert.rejects(
      collectAdminAdMediaPages(
        { itens: itens.slice(0, 50), page: 0, size: 50, totalElements: 51, totalPages: 2, last: false },
        async () => {
          incompleteRequests += 1
          return { itens: [], page: 1, size: 50, totalElements: 51, totalPages: 2, last: true }
        },
      ),
      (error) => error.code === 'ADMIN_MEDIA_PAGINATION_INVALID',
    )
    assert.equal(incompleteRequests, 1)

    let excessiveRequests = 0
    await assert.rejects(
      collectAdminAdMediaPages(
        {
          itens: itens.slice(0, 50),
          page: 0,
          size: 50,
          totalElements: (ADMIN_AD_MEDIA_MAX_PAGES + 1) * 50,
          totalPages: ADMIN_AD_MEDIA_MAX_PAGES + 1,
          last: false,
        },
        async () => { excessiveRequests += 1; return paginas[0] },
      ),
      (error) => error.code === 'ADMIN_MEDIA_PAGINATION_INVALID',
    )
    assert.equal(excessiveRequests, 0)
    console.log('ADMIN_MEDIA_PAGINATION_RESULT=OK items=121')
`
const paginationCompilation = ts.transpileModule(paginationTypeScript, {
  compilerOptions: {
    target: ts.ScriptTarget.ES2022,
    module: ts.ModuleKind.ESNext,
  },
  fileName: 'admin-media-pagination.test.ts',
  reportDiagnostics: true,
})
const paginationCompilationErrors = (paginationCompilation.diagnostics ?? [])
  .filter((diagnostic) => diagnostic.category === ts.DiagnosticCategory.Error)
  .map((diagnostic) => ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n'))
assert.deepEqual(paginationCompilationErrors, [], 'O bloco TypeScript de paginacao deve transpilar sem erros.')
const paginationRuntime = spawnSync(process.execPath, [
  '--no-warnings',
  '--input-type=module',
  '--eval',
  paginationCompilation.outputText,
], { encoding: 'utf8' })
assert.equal(paginationRuntime.status, 0, paginationRuntime.stderr || paginationRuntime.stdout)
assert.match(paginationRuntime.stdout, /ADMIN_MEDIA_PAGINATION_RESULT=OK items=121/)
assert.ok(api.includes("csrfHeaders(multipart ? 'multipart' : 'json')"), 'Multipart deve preservar CSRF sem fixar Content-Type.')
assert.ok(uploadAdapter.includes('new FormData()') && uploadAdapter.includes("form.append('arquivo', arquivo)"), 'Upload admin deve enviar somente a parte arquivo.')
assert.ok(uploadAdapter.includes("method: 'POST'") && uploadAdapter.includes("headers: { 'Idempotency-Key': idempotencyKey }"), 'Upload admin deve usar POST e Idempotency-Key.')
assert.ok(uploadAdapter.includes('{ unsupportedPhotoUpload: true }'), 'Upload admin de foto deve ativar explicitamente o fallback fotografico.')
assert.equal((api.match(/unsupportedPhotoUpload: true/g) ?? []).length, 1, 'Somente o upload admin de foto pode ativar o fallback fotografico.')
assert.ok(api.includes('unsupportedPhotoUpload: options.unsupportedPhotoUpload === true'), 'O adapter deve propagar o opt-in fotografico explicitamente ao contrato HTTP.')
assert.ok(!uploadAdapter.includes("'Content-Type'") && !uploadAdapter.match(/objectKey|storage|bucket|nomeArquivo|arquivoNome/), 'Upload admin nao pode fixar boundary nem expor metadados internos.')

assert.ok(types.includes("tipo: 'FOTO' | 'VIDEO'"), 'A fila de midia nao pode tipar Story.')
assert.ok(!types.includes("'FOTO' | 'VIDEO' | 'STORY'"), 'Story nao pode integrar o contrato V3 da fila.')
for (const field of [
  'fotosAprovadasTotal: number',
  'fotosAguardandoDecisaoTotal: number',
  'midiaId: string',
  'anuncioId: string',
  "tipo: 'FOTO'",
  "finalidade: 'GALERIA'",
  'ordem: number',
  'status: string',
  'statusArquivo: string',
  'idempotente: boolean',
  'requestId: string',
]) {
  assert.ok(types.includes(field), `Campo do contrato de upload/fotos ausente: ${field}`)
}
assert.ok(detail.includes("item.tipo === 'VIDEO' ? 'RESTRITA_18'"), 'Video aprovado deve permanecer RESTRITA_18.')
assert.ok(detail.includes('Sempre RESTRITA_18'), 'A interface deve informar a classificacao fixa do video.')
assert.ok(detail.includes('PendingPhotoDecisionSelector'), 'Foto pendente deve preservar a classificacao local.')
assert.ok(detail.includes('MediaVisibilitySelector') && detail.includes("['LIVRE', 'RESTRITA_18']"), 'Foto finalizada deve preservar a reclassificacao individual.')
assert.ok(detail.includes('reclassifyAdminMedia') && detail.includes("kind: 'RECLASSIFY'"), 'Foto finalizada deve usar a operacao canonica propria de reclassificacao.')
assert.ok(detail.includes('deletablePhoto') && detail.includes("item.status !== 'REMOVIDA'"), 'Toda foto existente deve preservar a desvinculacao administrativa.')
assert.ok(detail.includes('PhotoDeleteDialog') && detail.includes('Excluir foto'), 'A exclusao deve exigir confirmacao propria e independente da classificacao.')
assert.ok(detail.includes("decisao: 'EXCLUIR'") && detail.includes('decideAdminPhotosBatch'), 'A exclusao individual deve reutilizar o contrato canonico do lote.')
assert.ok(detail.includes('photoDeleteLock.current') && detail.includes("result.resultado === 'FALHA'"), 'A exclusao deve bloquear duplo clique e nao simular sucesso em falha.')
const photoDeleteFlow = detail.slice(
  detail.indexOf('async function confirmPhotoDelete'),
  detail.indexOf('async function confirmDecision'),
)
assert.ok(detail.includes('também estiver vinculado a um documento KYC ou a outro registro'), 'O modal deve informar a preservacao de referencias legitimas.')
assert.ok(!photoDeleteFlow.includes('setMedia('), 'A exclusao nao pode remover o card de forma otimista.')
assert.ok(photoDeleteFlow.includes('await load(undefined, false)') && photoDeleteFlow.indexOf('await load(undefined, false)') < photoDeleteFlow.indexOf('setPhotoDeleteTarget(null)'), 'A exclusao deve recarregar detalhe e lista antes de fechar o modal.')
assert.ok(photoDeleteFlow.includes('revalidarCacheCatalogoPublico()'), 'A exclusao confirmada deve invalidar o cache publico aplicavel.')
assert.ok(api.includes('if (!response.ok && respostaValida)') && api.includes('falha?.motivo'), 'Falha funcional HTTP deve preservar a mensagem sanitizada do backend.')
assert.ok(types.includes('codigo: string | null'), 'O contrato deve transportar o codigo funcional sanitizado.')
assert.ok(detail.includes("media.tipo === 'FOTO' ? 'aspect-video' : 'aspect-[16/7]'") && detail.includes('object-contain'), 'A foto deve ganhar area util sem cortar a midia e sem alterar o video.')
assert.ok(detail.includes('data-admin-media-card') && detail.includes('data-admin-media-preview'), 'O card e a previa devem permanecer mensuraveis no teste visual.')
assert.ok(detail.includes('data-admin-media-metadata') && detail.includes('data-admin-photo-decision'), 'Metadados e decisao devem ter blocos compactos identificaveis.')
assert.ok(detail.includes('<legend className="sr-only">Decisão individual da foto</legend>') && detail.includes('>Decisão:</span>'), 'O grupo compacto deve preservar fieldset e legend acessiveis.')
assert.ok(detail.includes('grid min-w-[13rem] flex-1 grid-cols-2') && detail.includes('focus-visible:ring-2'), 'Radios devem ficar lado a lado, envolver de forma controlada e manter foco visivel.')
assert.ok(detail.includes("title: 'Aplicar e aprovar vídeo'") && detail.includes('Aplicar classificação'), 'Video e foto finalizada devem preservar suas operacoes fora do lote.')
assert.ok(detail.includes("title: 'Rejeitar foto'") && detail.includes("action: 'REPROVAR'"), 'Foto pendente deve oferecer rejeicao pela API canonica com motivo.')
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
assert.ok(!detail.includes('applyConfirmedPhotoBatch'), 'O lote nao pode marcar fotos como aprovadas de forma otimista.')
const confirmedPhotoBatchFlow = detail.slice(
  detail.indexOf('async function confirmPhotoBatch'),
  detail.indexOf('async function confirmPhotoDelete'),
)
assert.ok(confirmedPhotoBatchFlow.includes('await load(undefined, false)') && confirmedPhotoBatchFlow.indexOf('await load(undefined, false)') < confirmedPhotoBatchFlow.indexOf('setPhotoBatchResult(response)'), 'O lote deve recarregar detalhe e midias antes de confirmar sucesso.')
assert.ok(detail.includes('item.codigo ? ` (code: ${item.codigo})` : null') && detail.includes('requestId: {photoBatchResult.requestId}'), 'Falha parcial deve exibir code e requestId devolvidos pelo backend.')
assert.ok(detail.includes('Editar dados do usuário') && detail.includes('<OwnerEditDialog'), 'O detalhe deve oferecer a correcao cadastral no contexto do proprietario.')
assert.ok(detail.includes('isAdmin && canModerateAd && ad.anunciante'), 'A acao cadastral deve permanecer invisivel para MODERADOR.')
assert.ok(list.includes('Proprietário suspenso') && list.includes("owner?.status === 'SUSPENSO'"), 'A visao administrativa deve sinalizar o proprietario suspenso.')
assert.ok(detail.includes('ownerEligibleForModeration') && detail.includes('Este anúncio não pode ser aprovado enquanto o proprietário estiver suspenso.'), 'O detalhe deve ocultar a aprovacao e explicar o bloqueio do proprietario.')
assert.match(detail, /const canApproveAd = canDecideAdReview\s+&& ownerEligibleForModeration/, 'A UI nao pode oferecer aprovacao ao proprietario inelegivel.')
assert.ok(detail.includes('updateAdminAdOwner(ad.id, { nome, cpf })'), 'Nome e CPF devem ser enviados pela operacao vinculada ao anuncio.')
assert.ok(detail.includes('nomeCivil: updated.nomeCivil ?? nome') && detail.includes('cpf: updated.cpf'), 'A resposta confirmada deve atualizar Proprietario e Dados para conferencia sem reload.')
assert.ok(detail.includes('isValidCpf(cpf)') && detail.includes('maskCpf(ad.anunciante.cpf'), 'A interface deve reutilizar mascara e validacao de CPF existentes.')
const ownerDialog = detail.slice(
  detail.indexOf('function OwnerEditDialog'),
  detail.indexOf('function PhotoDeleteDialog'),
)
assert.ok(ownerDialog.includes('>Nome</Label>') && ownerDialog.includes('>CPF</Label>'), 'O modal deve exibir somente Nome e CPF.')
for (const forbiddenOwnerField of ['E-mail', 'Telefone', 'Senha', 'Data de nascimento', 'Status', 'KYC', 'Role']) {
  assert.ok(!ownerDialog.includes(`>${forbiddenOwnerField}</Label>`), `Campo indevido no modal do proprietario: ${forbiddenOwnerField}`)
}
const ownerUpdateAdapter = api.slice(
  api.indexOf('export function updateAdminAdOwner'),
  api.indexOf('export function reactivateAdminAd'),
)
assert.ok(ownerUpdateAdapter.includes("method: 'PATCH'") && ownerUpdateAdapter.includes('JSON.stringify(payload)'), 'A correcao cadastral deve usar PATCH com CSRF.')
assert.ok(!ownerUpdateAdapter.includes('usuarioId'), 'O cliente nao pode escolher o usuario atualizado.')
assert.ok(api.includes('AdminAdOwnerFormError') && api.includes('body.mensagem'), 'Conflitos cadastrais devem preservar a mensagem e os erros de campo do backend.')
assert.ok(!detail.includes('CPF já vinculado a outro usuário.'), 'A mensagem de conflito nao pode ser fabricada no componente.')
const confirmedMediaFlow = detail.slice(
  detail.indexOf("} else if (intent.kind === 'MEDIA')"),
  detail.indexOf('} else {', detail.indexOf("} else if (intent.kind === 'MEDIA')")),
)
assert.ok(!confirmedMediaFlow.includes('setMedia('), 'A decisao individual nao pode simular status local.')
assert.ok(confirmedMediaFlow.includes('await load(undefined, false)') && confirmedMediaFlow.indexOf('await load(undefined, false)') < confirmedMediaFlow.indexOf('setIntent(null)'), 'A decisao individual deve recarregar detalhe e midias antes de fechar.')
const confirmedReclassificationFlow = detail.slice(
  detail.indexOf('} else {', detail.indexOf("} else if (intent.kind === 'MEDIA')")),
  detail.indexOf('\n      }\n      await load()', detail.indexOf("} else if (intent.kind === 'MEDIA')")),
)
assert.ok(!confirmedReclassificationFlow.includes('setMedia('), 'A reclassificacao nao pode simular status local.')
assert.ok(confirmedReclassificationFlow.includes('await load(undefined, false)') && confirmedReclassificationFlow.indexOf('await load(undefined, false)') < confirmedReclassificationFlow.indexOf('setIntent(null)'), 'A reclassificacao deve recarregar detalhe e midias antes de fechar.')
assert.ok(detail.includes('else delete next[intent.media.id]'), 'Falha deve restaurar o estado persistido da classificacao, sem simular sucesso.')
assert.ok(detail.includes("normalized.kind === 'CONFLICT'") && detail.includes('O estado da mídia mudou.'), 'Conflito real deve atualizar o detalhe e explicar a mudanca de estado.')
assert.ok(detail.includes('preserveSelection.mode') && detail.includes('selectionStillApplies'), 'A selecao deve ser preservada somente enquanto a decisao continuar aplicavel.')
assert.ok(detail.includes('await load()') && detail.includes('setIntent(null)'), 'Sucesso deve atualizar o card antes de fechar o modal.')
assert.ok(detail.includes('const canUploadAdminMedia = isAdmin && canModerateAd && canModerateMedia && !removed'), 'Upload deve exigir cumulativamente ADMIN, ANUNCIO_MODERAR e MIDIA_REVISAR.')
assert.ok(detail.includes('<AdminAnuncioMidiaUploader') && detail.includes('onReload={() => load(undefined, false)}'), 'A aba Midias deve integrar o uploader com recarga autoritativa.')
assert.ok(detail.includes('ad.fotosAprovadasTotal === 0') && detail.includes('ad.fotosAguardandoDecisaoTotal > 0'), 'A aprovacao do anuncio deve respeitar os contadores do backend.')
assert.ok(detail.includes('disabled={headerBusy || photoApprovalBlocked}'), 'O botao Aprovar anuncio deve ficar visualmente desabilitado quando houver bloqueio de fotos.')
assert.ok(detail.includes('Aprove ao menos uma foto antes de aprovar o anúncio.'), 'Mensagem de ausencia de foto aprovada deve ser exata.')
assert.ok(detail.includes('Conclua a análise de todas as fotos antes de aprovar o anúncio.'), 'Mensagem de fotos pendentes deve ser exata.')
assert.ok(uploader.includes('const [arquivo, setArquivo] = useState<File | null>(null)') && uploader.includes('const [idempotencyKey, setIdempotencyKey] = useState<string | null>(null)'), 'Uploader deve manter arquivo e chave como estado da tentativa logica.')
assert.ok(uploader.includes('setIdempotencyKey(selected ? crypto.randomUUID() : null)'), 'Selecionar outro arquivo deve iniciar nova tentativa logica.')
assert.ok(uploadSubmit.indexOf('await uploadAdminAdMedia') < uploadSubmit.indexOf('await onReload()') && uploadSubmit.indexOf('await onReload()') < uploadSubmit.indexOf('setArquivo(null)') && uploadSubmit.indexOf('setArquivo(null)') < uploadSubmit.indexOf("setSuccess('Foto enviada"), 'Arquivo e chave so podem ser limpos apos 2xx e recarga bem-sucedida.')
assert.ok(!uploadFailure.includes('setArquivo(null)') && !uploadFailure.includes('setIdempotencyKey(null)') && uploadFailure.includes('setError(normalizeApiError(uploadError))'), 'Falha deve preservar arquivo e Idempotency-Key para retry.')
assert.ok(uploader.includes("error?.retryable ? 'Tentar novamente' : 'Enviar foto'"), 'Uploader deve oferecer retry explicito somente para falha transitória.')
assert.ok(uploader.includes('error.code') && uploader.includes('error.requestId'), 'Falha de upload deve exibir code e requestId.')
assert.ok(apiContract.includes('const requestId = bodyRequestId || response.headers.get(\'X-Request-Id\')'), 'requestId do corpo deve preceder o cabecalho.')
assert.ok(apiContract.includes('UNSUPPORTED_PHOTO_UPLOAD_MESSAGE') && apiContract.includes('Não conseguimos enviar esta foto. Abra a imagem em um editor e salve uma nova cópia em JPG ou PNG. Depois, selecione essa cópia.'), 'Fallback 415 deve orientar uma nova cópia sem acusar o arquivo.')
assert.ok(apiContract.includes("'formato de arquivo nao permitido'") && apiContract.includes("'unsupported media type'"), 'Resolver 415 deve reconhecer as mensagens genericas conhecidas independentemente de caixa e acentos.')
assert.ok(apiContract.includes('resolveUnsupportedPhotoUploadMessage(serverMessage)'), 'Erro HTTP administrativo 415 deve usar o resolver central.')
assert.ok(apiContract.includes('? UNSUPPORTED_PHOTO_UPLOAD_MESSAGE\n    : candidate'), 'Mensagem 415 especifica deve ser preservada.')
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
assert.ok(premiumQuick.includes('grid-cols-1') && premiumQuick.includes('sm:grid-cols-2'), 'Premium e Stories devem usar uma grade uniforme e responsiva.')
assert.ok(premiumQuick.includes('h-10 w-full') && premiumQuick.includes('line-clamp-2'), 'As opcoes devem preservar altura, largura e texto consistentes.')
assert.ok(list.includes('min-w-[280px]'), 'A coluna Premium/Stories deve reservar espaco suficiente no desktop.')
assert.ok(premiumQuick.includes("item?.status === 'PENDENTE'") && premiumQuick.includes('border-amber-300'), 'Ativacao pendente deve permanecer distinta de beneficio ativo.')
assert.ok(premiumQuick.includes('idempotencyKey.current ?? operationKey()'), 'Retry do Premium rapido deve reutilizar Idempotency-Key.')
assert.ok(api.includes('request<AdminPremiumActivationBatch>'), 'A mutacao Premium deve tipar a confirmacao devolvida pelo servidor.')
assert.ok(premiumQuick.includes("item.codigo === FOTOS_EXTRA_CODE && item.status === 'PENDENTE'"), 'Fotos extras aguardando a quinta aprovacao devem aparecer como capacidade concedida.')
assert.ok(premiumQuick.includes('onChanged(mergeConfirmedPremiumBenefit(benefits, confirmed))'), 'Fotos extras devem atualizar a linha assim que o POST confirmar a ativacao.')
assert.ok(premiumQuick.includes('void refreshRow(confirmed, generation).catch'), 'O refetch de fotos deve ocorrer em segundo plano sem reabrir erro apos sucesso.')
assert.ok(list.includes('premiumBenefitGranted(benefit)') && list.includes('onOperationStart={() => setActionError(null)}'), 'A fila deve limpar conflito residual e preservar a capacidade confirmada no resumo.')
assert.ok(list.includes('publishingStoryIds.current.has(item.id)') && list.includes('storyIdempotencyKeys.current.get(item.id)'), 'Stories deve bloquear duplo clique e reutilizar a chave idempotente.')
assert.ok(list.includes("estado: 'ATIVO'") && list.includes('storyId: story.storyId'), 'Stories deve atualizar a linha imediatamente a partir da mutacao confirmada.')
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
assert.ok(api.includes("apiErrorFromResponse(response, { preserveServerMessage: true })"), 'A API administrativa deve preservar a mensagem segura retornada pelo backend.')
assert.ok(apiContract.includes('options.preserveServerMessage') && apiContract.includes('body.message.trim()'), 'O contrato deve ler a mensagem do backend somente quando solicitado pelo adapter.')
assert.ok(detail.includes('specificConflictMessage(') && detail.includes('normalized.requestId'), 'Conflito 409 deve exibir a causa especifica e preservar o requestId.')
assert.ok(detail.includes('O estado do anúncio mudou.'), 'Conflito sem detalhe deve manter fallback controlado após atualizar o anúncio.')
assert.ok(detail.includes('decisionLock.current') && detail.includes('disabled={headerBusy}'), 'A aprovacao deve impedir duplo clique durante a decisao.')

assert.ok(
  documents.includes('AdminKycReviewGrid') && reviewGrid.includes('AdminKycDocumentGrid'),
  'Documento deve reutilizar a grade documental protegida por meio do componente compartilhado.',
)
assert.ok(documents.includes('listAdminAdDocuments'), 'Documentos devem continuar vinculados ao proprietario do anuncio pelo backend.')
assert.ok(
  !documents.includes('objectKey') && !documents.includes('bucket')
    && !reviewGrid.includes('objectKey') && !reviewGrid.includes('bucket'),
  'Documento nao pode expor bucket ou object key.',
)

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
assert.ok(removalFlow.includes('await removeAdminAd(ad.id, reason.trim())'), 'Tela deve refletir REMOVIDO somente depois da resposta do backend.')
assert.ok(removalFlow.includes("window.dispatchEvent(new CustomEvent('admin-revisions-updated'))"), 'Sucesso deve atualizar o contador administrativo sem aguardar polling.')
assert.ok(removalFlow.includes("toast.success('An\\u00fancio removido da plataforma.')"), 'Sucesso deve apresentar confirmacao curta e especifica.')
assert.ok(removalFlow.includes('router.replace(navigation?.proximo ? targetHref(navigation.proximo) : backHref)'), 'Sucesso deve sair do item removido para o proximo anuncio ou para a fila.')
assert.ok(!removalFlow.includes('await load()'), 'Sucesso nao deve recarregar o detalhe removido nem exigir F5.')
assert.ok(
  removalFlow.indexOf('await removeAdminAd(ad.id, reason.trim())') < removalFlow.indexOf('void enviarIndexNowNoCliente'),
  'IndexNow deve ser acionado somente depois da confirmacao logica do backend.',
)
assert.equal((removalFlow.match(/enviarIndexNowNoCliente/g) ?? []).length, 1, 'O fluxo de remocao deve emitir no maximo uma notificacao IndexNow por sucesso.')
assert.ok(detail.includes("const canRemove = canManageLegalStatus && !removed && ad.status !== 'BLOQUEADO'"), 'Somente ADMIN autorizado deve ver a remocao em transicao valida.')
assert.ok(detail.includes("ad.status === 'REMOVIDO'") && detail.includes('disabledReason={removed'), 'REMOVIDO deve bloquear novas ativacoes Premium na interface.')
assert.ok(detail.includes('reason.trim().length < 5') && detail.includes('maxLength={1000}'), 'Exclusao deve exigir motivo dentro do contrato.')
assert.ok(detail.includes('border border-red-950 bg-red-700'), 'Exclusao deve possuir tratamento destrutivo distinto do bloqueio juridico.')
assert.ok(removalDialog.includes('Fotos e v\\u00eddeos exclusivos ser\\u00e3o exclu\\u00eddos'), 'Confirmacao deve limitar a exclusao fisica a fotos e videos exclusivos.')
assert.ok(removalDialog.includes('documentos KYC, revis\\u00f5es, Stories ou outros registros permanecer\\u00e3o preservados'), 'Confirmacao deve informar a preservacao de arquivos compartilhados.')
assert.ok(removalDialog.includes('hist\\u00f3rico administrativo ser\\u00e1 mantido'), 'Confirmacao deve informar a preservacao do historico.')
assert.ok(!detail.includes('benef\\u00edcios e m\\u00eddias permanecer\\u00e3o preservados'), 'Confirmacao antiga nao pode afirmar que as midias serao preservadas.')
assert.ok(detailHeader.includes("Excluir an\\u00fancio"), 'Excluir anuncio deve permanecer no cabecalho administrativo.')
assert.ok(api.includes('JSON.stringify({ motivo: motivo.trim() })'), 'Adapter deve enviar somente o motivo sanitizado da remocao.')
assert.ok(removalAdapter.includes("method: 'POST'") && !removalAdapter.includes("method: 'DELETE'"), 'Remocao administrativa deve usar a mutacao logica, nunca DELETE.')
assert.ok(types.includes('objetosCleanupAgendados: number'), 'Contrato frontend deve distinguir cleanup fisico encaminhado ao pos-commit.')

assert.ok(!detail.includes('AdminAnuncioStory') && !api.includes('/stories/selecao'), 'O detalhe de anuncio nao pode manter um fluxo administrativo paralelo de Stories.')
assert.ok(storiesPage.includes('AdminStoriesManagement'), 'A rota administrativa de Stories deve usar a gestao canonica.')
assert.ok(adminStoriesApi.includes('`/gestao?${query.toString()}`'), 'A gestao administrativa deve consultar o contrato canonico de Stories.')
assert.ok(adminStoriesApi.includes('`/${encodeURIComponent(storyId)}/remover`'), 'A remocao administrativa deve usar o contrato canonico por Story.')
assert.ok(adminStories.includes("story.modoConteudo === 'ANUNCIO' ? 'Anúncio' : 'Mídia enviada'"), 'A gestao deve distinguir os dois modos canonicos de conteudo.')
assert.ok(adminStories.includes('disabled={removing}') && adminStories.includes('Remover Story'), 'A remocao administrativa deve impedir duplo clique.')

assert.ok(editPage.includes('AdminAnuncioEditForm') && !editPage.includes('moderation-v2'), 'A edicao deve usar contrato administrativo V3 proprio.')
assert.ok(edit.includes('updateAdminAd') && edit.includes("session?.papeis.includes('ADMIN')"), 'Somente ADMIN deve editar pelo adapter canonico.')
for (const field of ['titulo', 'descricao', 'categoria', 'preco', 'uf', 'cidade', 'bairro', 'enderecoResumido', 'servicos', 'locaisAtendimento', 'whatsapp']) {
  assert.ok(edit.includes(field), `Campo canonico ausente no editor administrativo: ${field}`)
}

// Same deterministic hook/module boundary used by test-photo-upload-validation:
// execute the real components, load effects and submit callbacks with synthetic
// administrative responses. This is not a browser or production validation.
function runtimeModule(name, imports = {}) {
  const compiled = ts.transpileModule(source(name), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
    fileName: name,
    reportDiagnostics: true,
  })
  assert.deepEqual((compiled.diagnostics ?? []).filter((item) => item.category === ts.DiagnosticCategory.Error), [])
  const module = { exports: {} }
  new Function('require', 'module', 'exports', compiled.outputText)((specifier) => {
    assert.ok(Object.hasOwn(imports, specifier), `Fronteira não declarada: ${name}: ${specifier}`)
    return imports[specifier]
  }, module, module.exports)
  return module.exports
}

function componentHooks() {
  const slots = [], effects = []
  let index = 0, dirty = false, component, tree
  const changed = (previous, next) => !previous || !next || next.some((value, i) => !Object.is(value, previous[i]))
  const react = {
    useState(initialValue) {
      const slot = index++
      if (!slots[slot]) slots[slot] = { value: typeof initialValue === 'function' ? initialValue() : initialValue }
      return [slots[slot].value, (next) => {
        const value = typeof next === 'function' ? next(slots[slot].value) : next
        dirty ||= !Object.is(value, slots[slot].value)
        slots[slot].value = value
      }]
    },
    useRef(initialValue) { const slot = index++; return slots[slot] ??= { current: initialValue } },
    useMemo(callback, dependencies) {
      const slot = index++
      if (!slots[slot] || changed(slots[slot].dependencies, dependencies)) slots[slot] = { value: callback(), dependencies }
      return slots[slot].value
    },
    useCallback(callback, dependencies) { return react.useMemo(() => callback, dependencies) },
    useEffect(callback, dependencies) {
      const slot = index++
      if (!slots[slot] || changed(slots[slot].dependencies, dependencies)) {
        const old = slots[slot]
        slots[slot] = { dependencies, cleanup: old?.cleanup }
        effects.push(() => { old?.cleanup?.(); slots[slot].cleanup = callback() })
      }
    },
  }
  function render() {
    index = 0; dirty = false; tree = component({ anuncioId: 'synthetic-ad' })
    effects.splice(0).forEach((effect) => effect())
  }
  return {
    react,
    mount(next) { component = next; render() },
    async settle() {
      for (let iteration = 0; iteration < 12; iteration++) {
        await new Promise((resolve) => setImmediate(resolve))
        if (dirty) render()
      }
      assert.equal(dirty, false, 'O componente deve estabilizar sem efeitos em ciclo.')
    },
    get tree() { return tree },
    unmount() { slots.forEach((slot) => slot?.cleanup?.()) },
  }
}

const element = (type, props) => ({ type, props: props ?? {} })
const jsxRuntime = { jsx: element, jsxs: element, Fragment: 'Fragment' }
const icons = new Proxy({}, { get: (_, key) => String(key) })
const contractRuntime = runtimeModule('lib/api-contract.ts')
const feedbackRuntime = runtimeModule('components/feedback/contract-state.tsx', {
  react: {}, 'react/jsx-runtime': jsxRuntime, 'lucide-react': icons,
  '@/components/ui/button': { Button: 'Button' }, '@/lib/api-contract': contractRuntime,
})
function renderedNodes(tree) {
  if (tree == null || typeof tree === 'boolean') return []
  if (Array.isArray(tree)) return tree.flatMap(renderedNodes)
  if (typeof tree !== 'object') return [tree]
  if (tree.type === feedbackRuntime.ContractState) return renderedNodes(tree.type(tree.props))
  return [tree, ...renderedNodes(tree.props?.children)]
}
const visibleText = (tree) => renderedNodes(tree).filter((node) => typeof node === 'string' || typeof node === 'number').join(' ')
const controls = (tree, predicate) => renderedNodes(tree).filter((node) => typeof node === 'object' && predicate(node))
const editableForms = (tree) => controls(tree, (node) => node.type === 'form')
const removedMessage = 'Este anúncio foi removido e não pode ser editado.'
const syntheticAd = {
  id: 'synthetic-ad', slug: 'anuncio-sintetico', titulo: 'Anúncio sintético para edição',
  status: 'PUBLICADO', statusModeracao: 'APROVADO', descricao: 'Descrição sintética preservada para edição.',
  categoria: 'ACOMPANHANTE', preco: 99, whatsapp: null, atendimentoExclusivamenteVirtual: false,
  localizacao: { uf: 'SP', cidade: 'Cidade sintética', bairro: 'Bairro sintético' },
  locaisAtendimento: [], servicos: [], fotosAprovadasTotal: 1, fotosAguardandoDecisaoTotal: 0,
  anunciante: { id: 'synthetic-owner', status: 'ATIVO' },
  metricas: { visualizacoes: { total: 0, situacao: 'ZERO_LEGITIMO' }, cliquesWhatsapp: 0, beneficiosPremiumVigentes: [] },
}
const adminSession = { papeis: ['ADMIN'], permissoes: ['ANUNCIO_MODERAR'] }
const removedAd = { ...syntheticAd, status: 'REMOVIDO' }

async function mountAdministrativeComponent({ mode = 'edit', reads = [syntheticAd], update, session = adminSession } = {}) {
  const runner = componentHooks(), calls = [], effects = [], unexpected = []
  let readIndex = 0
  const apiBoundary = new Proxy({
    async getAdminAd(id) {
      assert.equal(id, syntheticAd.id)
      calls.push('GET')
      assert.ok(readIndex < reads.length, 'Consulta administrativa adicional inesperada.')
      const response = reads[readIndex++]
      if (response instanceof Error) throw response
      return typeof response === 'function' ? response() : response
    },
    async updateAdminAd(id, payload) {
      calls.push('UPDATE')
      assert.equal(id, syntheticAd.id)
      assert.ok(update, 'Envio inesperado do editor bloqueado.')
      return update(payload)
    },
    async listAdminAdHistory(id) {
      assert.equal(id, syntheticAd.id)
      return [{ id: 'synthetic-history', acao: 'REMOVER', alvoTipo: 'ANUNCIO', motivo: 'Histórico sintético preservado' }]
    },
  }, { get: (target, name) => name in target ? target[name] : () => { unexpected.push(String(name)); throw new Error(`Operação inesperada: ${String(name)}`) } })
  const imports = {
    react: runner.react, 'react/jsx-runtime': jsxRuntime, 'lucide-react': icons,
    'next/link': { default: 'Link' },
    'next/navigation': { useRouter: () => Object.fromEntries(['push', 'replace', 'refresh'].map((method) => [method, (...args) => effects.push([method, ...args])])) },
    '@/lib/admin-auth-api': { getAdminSession: async () => session },
    '@/lib/api-contract': contractRuntime,
    '@/components/feedback/contract-state': feedbackRuntime,
    '@/components/forms/masked-phone-input': { MaskedPhoneInput: 'MaskedPhoneInput' },
    '@/lib/phone-mask': { maskPhoneBR: (value) => value, phoneToE164BR: (value) => value || null },
    '@/features/anuncio-wizard/wizard-constants': { categorias: [], locais: [], servicos: [] },
    '@/lib/seo/indexnow-client': {
      anuncioEstaPublicamenteIndexavel: (status) => status === 'PUBLICADO',
      montarEventoIndexNowAnuncio: (event) => event,
      enviarIndexNowNoCliente: (event) => { effects.push(['INDEXNOW', event]); return Promise.resolve() },
    },
    './api': apiBoundary,
  }
  for (const [module, names] of Object.entries({
    button: ['Button'], input: ['Input'], textarea: ['Textarea'], badge: ['Badge'], label: ['Label'],
    dialog: ['Dialog', 'DialogContent', 'DialogDescription', 'DialogFooter', 'DialogHeader', 'DialogTitle'],
    select: ['Select', 'SelectContent', 'SelectItem', 'SelectTrigger', 'SelectValue'],
    tabs: ['Tabs', 'TabsContent', 'TabsList', 'TabsTrigger'],
  })) imports[`@/components/ui/${module}`] = Object.fromEntries(names.map((name) => [name, name]))
  if (mode === 'detail') Object.assign(imports, {
    sonner: { toast: { success: (message) => effects.push(['TOAST', message]) } },
    '@/app/(painel-admin)/admin/anuncios/actions': {}, '@/features/admin-usuarios/api': {}, '@/lib/cpf-mask': {},
    './admin-anuncio-documentos': { AdminAnuncioDocumentos: 'AdminAnuncioDocumentos' },
    './admin-anuncio-midia-uploader': { AdminAnuncioMidiaUploader: 'AdminAnuncioMidiaUploader' },
    './admin-anuncio-premium': { AdminAnuncioPremium: 'AdminAnuncioPremium' },
    './queue-context': runtimeModule('features/admin-anuncios/queue-context.ts'),
  })
  const module = runtimeModule(`features/admin-anuncios/admin-anuncio-${mode === 'edit' ? 'edit-form' : 'moderacao'}.tsx`, imports)
  runner.mount(mode === 'edit' ? module.AdminAnuncioEditForm : module.AdminAnuncioModeracao)
  await runner.settle()
  return { runner, calls, effects, finish() { assert.deepEqual(unexpected, []); runner.unmount() } }
}

for (const ad of [removedAd, syntheticAd]) {
  const detailCase = await mountAdministrativeComponent({ mode: 'detail', reads: [ad] })
  const tree = detailCase.runner.tree
  assert.equal(controls(tree, (node) => node.type === 'Link' && node.props.href === '/admin/anuncios/synthetic-ad/editar').length, ad.status === 'REMOVIDO' ? 0 : 1)
  if (ad.status === 'REMOVIDO') assert.match(visibleText(tree), /Anúncio removido/)
  assert.ok(visibleText(tree).includes(ad.titulo), 'O detalhe deve preservar os dados para consulta.')
  assert.ok(visibleText(tree).includes('Histórico sintético preservado'), 'O histórico permanece consultável após remoção.')
  assert.deepEqual(detailCase.calls, ['GET'])
  assert.deepEqual(detailCase.effects, [])
  detailCase.finish()
}

const directRemoved = await mountAdministrativeComponent({ reads: [removedAd] })
assert.ok(visibleText(directRemoved.runner.tree).includes(removedMessage))
assert.equal(editableForms(directRemoved.runner.tree).length, 0, 'URL direta removida não monta formulário editável.')
assert.equal(controls(directRemoved.runner.tree, (node) => ['Input', 'Textarea', 'MaskedPhoneInput'].includes(node.type)).length, 0)
assert.equal(controls(directRemoved.runner.tree, (node) => node.type === 'Link' && node.props.href === '/admin/anuncios/synthetic-ad').length, 1)
assert.deepEqual(directRemoved.calls, ['GET'])
assert.deepEqual(directRemoved.effects, [])
directRemoved.finish()

const editable = await mountAdministrativeComponent({ update: async (payload) => {
  assert.equal(payload.titulo, 'Título sintético alterado')
  assert.equal(payload.descricao, syntheticAd.descricao)
  return { ...syntheticAd, ...payload, atualizadoEm: '2026-01-01T00:00:00Z' }
} })
controls(editable.runner.tree, (node) => node.type === 'Input' && node.props.value === syntheticAd.titulo)[0].props.onChange({ target: { value: 'Título sintético alterado' } })
await editable.runner.settle()
await editableForms(editable.runner.tree)[0].props.onSubmit({ preventDefault() {} })
await editable.runner.settle()
assert.deepEqual(editable.calls, ['GET', 'UPDATE'])
assert.deepEqual(editable.effects.map(([kind]) => kind), ['INDEXNOW', 'push', 'refresh'])
assert.deepEqual(editable.effects[1], ['push', '/admin/anuncios/synthetic-ad'])
editable.finish()

const notFound = await contractRuntime.apiErrorFromResponse(
  new Response(JSON.stringify({ message: 'Recurso não encontrado.' }), { status: 404 }),
  { preserveServerMessage: true },
)
assert.equal(notFound.kind, 'INTEGRATION_MISSING', 'A fixture 404 deve usar o mesmo contrato recebido pelo editor.')
let confirmRemoval
const confirmation = new Promise((resolve) => { confirmRemoval = resolve })
const concurrentRemoval = await mountAdministrativeComponent({ reads: [syntheticAd, () => confirmation], update: async () => { throw notFound } })
const pendingSubmit = editableForms(concurrentRemoval.runner.tree)[0].props.onSubmit({ preventDefault() {} })
await concurrentRemoval.runner.settle()
assert.deepEqual(concurrentRemoval.calls, ['GET', 'UPDATE', 'GET'])
assert.ok(!visibleText(concurrentRemoval.runner.tree).includes(removedMessage), '404 isolado não comprova remoção antes do GET administrativo.')
assert.deepEqual(concurrentRemoval.effects, [], 'Falha nunca pode navegar ou emitir IndexNow de sucesso.')
confirmRemoval(removedAd)
await pendingSubmit
await concurrentRemoval.runner.settle()
assert.ok(visibleText(concurrentRemoval.runner.tree).includes(removedMessage))
assert.equal(editableForms(concurrentRemoval.runner.tree).length, 0)
assert.deepEqual(concurrentRemoval.effects, [])
concurrentRemoval.finish()

const offline = new contractRuntime.ApiContractError('Falha sintética de rede.', 'NETWORK_FAILURE', null, true)
const invalid = new contractRuntime.ApiContractError('Revise o título sintético.', 'INVALID_REQUEST', 400, false)
const accessDenied = new contractRuntime.ApiContractError('Permissão de edição revogada.', 'ACCESS_DENIED', 403, false)
const serverFailure = await contractRuntime.apiErrorFromResponse(new Response(null, { status: 500 }))
for (const [failure, recheck, expectedMessage] of [
  [notFound, syntheticAd, 'O salvamento não foi confirmado. Tente novamente.'],
  [notFound, offline, 'O salvamento não foi confirmado. Tente novamente.'],
  [offline, syntheticAd, 'O salvamento não foi confirmado. Tente novamente.'],
  [serverFailure, syntheticAd, 'O salvamento não foi confirmado. Tente novamente.'],
  [invalid, syntheticAd, invalid.message],
  [accessDenied, accessDenied, accessDenied.message],
]) {
  const failed = await mountAdministrativeComponent({ reads: [syntheticAd, recheck], update: async () => { throw failure } })
  await editableForms(failed.runner.tree)[0].props.onSubmit({ preventDefault() {} })
  await failed.runner.settle()
  const message = visibleText(failed.runner.tree)
  assert.ok(message.includes('Não foi possível salvar o anúncio'))
  assert.ok(message.includes(expectedMessage))
  assert.ok(!message.includes(removedMessage), 'Falha comum/consulta inconclusiva não pode ser classificada como remoção.')
  assert.doesNotMatch(message, /N[aã]o foi poss[ií]vel carregar/)
  assert.equal(editableForms(failed.runner.tree).length, 1, 'Falha de salvamento deve preservar o formulário preenchido.')
  assert.deepEqual(failed.calls, ['GET', 'UPDATE', 'GET'])
  assert.deepEqual(failed.effects, [])
  failed.finish()
}

const loadFailure = await mountAdministrativeComponent({ reads: [notFound] })
assert.match(visibleText(loadFailure.runner.tree), /Nao foi possivel carregar/)
assert.doesNotMatch(visibleText(loadFailure.runner.tree), /Não foi possível salvar|Este anúncio foi removido/)
assert.equal(editableForms(loadFailure.runner.tree).length, 0)
assert.deepEqual(loadFailure.calls, ['GET'])
loadFailure.finish()
for (const session of [{ papeis: ['MODERADOR'], permissoes: ['ANUNCIO_MODERAR'] }, { papeis: ['ADMIN'], permissoes: [] }]) {
  const denied = await mountAdministrativeComponent({ reads: [syntheticAd], session })
  assert.equal(editableForms(denied.runner.tree).length, 0, 'A correção deve preservar ADMIN + ANUNCIO_MODERAR para edição.')
  assert.deepEqual(denied.calls, ['GET'])
  assert.deepEqual(denied.effects, [])
  denied.finish()
}
console.log('ADMIN_REMOVED_EDITOR_RESULT=OK cases=14 componentCallbacks=real transport=synthetic loadSaveErrors=distinct')
console.log('Moderacao V3 de anuncios e midias: contrato frontend aprovado.')
