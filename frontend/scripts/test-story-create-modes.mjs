import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

async function source(path) {
  return readFile(new URL(path, import.meta.url), 'utf8')
}

const [
  dialog,
  card,
  page,
  selector,
  entryState,
  api,
  viewer,
  accessPolicy,
  monetizationStep,
  premiumCodes,
  storyService,
  storyController,
  storyFeed,
  storyEntity,
  migration,
  openapi,
  adminStoryService,
] = await Promise.all([
  source('../src/components/stories/story-create-dialog.tsx'),
  source('../src/components/anuncios/meu-anuncio-card.tsx'),
  source('../src/app/(private-routes)/meus-anuncios/page.tsx'),
  source('../src/components/stories/story-anuncio-selector-dialog.tsx'),
  source('../src/components/stories/story-entry-state.ts'),
  source('../src/lib/meus-anuncios-api.ts'),
  source('../src/components/stories/story-viewer-dialog.tsx'),
  source('../src/components/stories/story-access-policy.js'),
  source('../src/features/monetizacao-wizard/components/monetizacao-step-anuncio.tsx'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/premium/PremiumBeneficioCodigo.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/anunciante/MeuAnuncioStoryService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/web/publico/anunciante/MeusAnunciosController.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/service/StoryFeedPublicoService.java'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/persistence/entity/midia/StoryAnuncioEntity.java'),
  source('../../backend/src/main/resources/db/migration/V048__stories_autogestao_modos_conteudo.sql'),
  source('../../contracts/openapi/topsdojob-v3-local.yaml'),
  source('../../backend/src/main/java/br/com/topsdojob/v3/application/admin/stories/AdminStorySelecaoService.java'),
])

const policyUrl = `data:text/javascript;base64,${Buffer.from(accessPolicy).toString('base64')}`
const { sanitizeStoryFeedItem, sanitizeStoryViewerItem } = await import(policyUrl)

let checks = 0

function check(name, callback) {
  try {
    callback()
    checks += 1
  } catch (error) {
    error.message = `${name}: ${error.message}`
    throw error
  }
}

function matches(value, pattern) {
  assert.match(value, pattern)
}

function excludes(value, pattern) {
  assert.doesNotMatch(value, pattern)
}

check('1. botao de Stories fica na area principal do card', () => {
  matches(card, /mt-auto grid grid-cols-2[\s\S]*storyEntry\.buttonLabel/)
  matches(page, /<StoryCreateDialog[\s\S]*anuncio=\{storyTarget\}/)
})

check('2. dialogo unico conectado ao topo e ao card', () => {
  matches(card, /onStoryOpen\(anuncio\.id, event\.currentTarget\)/)
  matches(page, /setStoryTargetId\(anuncioId\)[\s\S]*setStoryDialogOpen\(true\)/)
  matches(selector, /onSelect: \(anuncio: MeuAnuncio\) => void/)
  matches(dialog, /<Dialog[\s\S]*open=\{open\}/)
})

check('3. opcao Divulgar meu anuncio', () => {
  matches(dialog, /value="ANUNCIO"[\s\S]*Divulgar meu an.ncio/)
})

check('4. opcao Enviar uma midia', () => {
  matches(dialog, /value="MIDIA_UPLOAD"[\s\S]*Enviar uma m.dia/)
})

check('5. selecao do modo ANUNCIO', () => {
  matches(dialog, /onChange=\{\(\) => selectMode\('ANUNCIO'\)\}/)
})

check('6. selecao do modo MIDIA_UPLOAD', () => {
  matches(dialog, /onChange=\{\(\) => selectMode\('MIDIA_UPLOAD'\)\}/)
})

check('7. selecao acessivel por teclado', () => {
  assert.equal((dialog.match(/name="story-mode"/g) || []).length, 2)
  matches(dialog, /focus-within:ring-2/)
})

check('8. input aceita exatamente um arquivo', () => {
  const fileInput = dialog.match(/<input[\s\S]*?type="file"[\s\S]*?\/>/)?.[0] ?? ''
  matches(fileInput, /accept=\{ACCEPTED_MEDIA\}/)
  excludes(fileInput, /\bmultiple\b/)
})

check('9. segundo arquivo substitui antes da publicacao', () => {
  matches(dialog, /Substituir m.dia/)
  matches(dialog, /onClick=\{\(\) => inputRef\.current\?\.click\(\)\}/)
})

check('10. foto possui preview local', () => {
  matches(dialog, /<img src=\{preview \?\? undefined\}/)
  matches(dialog, /URL\.createObjectURL\(file\)/)
})

check('11. video possui preview local', () => {
  matches(dialog, /<video src=\{preview \?\? undefined\} controls/)
})

check('12. arquivo pode ser removido', () => {
  matches(dialog, /aria-label="Remover m.dia selecionada"/)
  matches(dialog, /selectFile\(null\)/)
})

check('13. ANUNCIO nao envia arquivo', () => {
  matches(dialog, /mode === 'MIDIA_UPLOAD' \? file : null/)
  matches(storyService, /modo == ModoConteudoStory\.ANUNCIO && !recebidos\.isEmpty\(\)/)
})

check('14. MIDIA_UPLOAD envia o arquivo selecionado', () => {
  matches(api, /form\.append\('modoConteudo', modoConteudo\)/)
  matches(api, /if \(arquivo\) form\.append\('arquivo', arquivo\)/)
})

check('15. frontend nao envia mediaId existente', () => {
  const storyRequest = api.slice(api.indexOf('export async function publicarMeuAnuncioStory'))
  excludes(storyRequest, /form\.append\(['"]mediaId/)
})

check('16. frontend nao envia URL, bucket ou object key', () => {
  const storyRequest = api.slice(api.indexOf('export async function publicarMeuAnuncioStory'))
  excludes(storyRequest, /form\.append\(['"](?:url|bucket|objectKey|object_key|usuarioId)/i)
})

check('17. loading impede duplo clique', () => {
  matches(dialog, /const busy = loadingOffer \|\| purchasing \|\| publishing/)
  matches(dialog, /disabled=\{busy\}/)
  matches(dialog, /if \(!busy\) onOpenChange\(next\)/)
})

check('18. progresso acessivel', () => {
  matches(api, /xhr\.upload\.onprogress/)
  matches(api, /Math\.min\(99, Math\.round/)
  matches(dialog, /role="progressbar"[\s\S]*aria-valuenow=\{progress\}/)
  matches(dialog, /mode === 'ANUNCIO'[\s\S]*Publicando Story[\s\S]*Processando m.dia/)
})

check('19. sucesso atualiza status sem reload', () => {
  matches(dialog, /setResult\(story\)[\s\S]*onSuccess\(story\)/)
  matches(page, /storyAtivo: story/)
  excludes(page, /location\.reload/)
})

check('20. expiracao vem do backend e usa timezone canonico', () => {
  matches(dialog, /formatStoryDate\(activeStory\.fimEm\)/)
  matches(entryState, /timeZone: 'America\/Sao_Paulo'/)
  excludes(dialog + entryState, /setHours|setDate|24 \* 60/)
})

check('21. Story ativo nao pode ser substituido pela UI', () => {
  matches(entryState, /if \(anuncio\.storyAtivo\)[\s\S]*kind: 'ACTIVE'/)
  matches(dialog, /\{activeStory \? \([\s\S]*Story ativo[\s\S]*\) : entry\.kind === 'UNAVAILABLE'/)
})

check('22. erros 400, 401 e 403 sao mantidos no dialogo', () => {
  matches(dialog, /error\.status === 401/)
  matches(dialog, /error\.status === 403/)
  matches(api, /xhr\.status < 200 \|\| xhr\.status >= 300/)
})

check('23. conflito 409 possui mensagem de Story ativo', () => {
  matches(dialog, /STORY_JA_ATIVO/)
  matches(dialog, /error\.status === 409/)
})

check('24. erros 413, 415 e 422 sao especificos', () => {
  matches(dialog, /error\.status === 413/)
  matches(dialog, /error\.status === 415/)
  matches(dialog, /error\.status === 422/)
})

check('25. erro 500 preserva dialogo e selecao segura', () => {
  const publishBlock = dialog.slice(
    dialog.indexOf('async function publishStory'),
    dialog.indexOf('async function activateAndPublish')
  )
  matches(publishBlock, /catch \(cause\) \{[\s\S]*setError\(activatedNow/)
  excludes(publishBlock, /setMode\(null\)/)
})

check('26. mobile sem overflow e com 100dvh', () => {
  matches(dialog, /max-h-\[100dvh\]/)
  matches(dialog, /w-\[calc\(100vw-1rem\)\]/)
  matches(dialog, /overflow-y-auto/)
})

check('27. foco, Escape e fechamento usam Dialog canonico', () => {
  matches(dialog, /from '@\/components\/ui\/dialog'/)
  matches(dialog, /onOpenChange=\{\(next\) =>/)
  matches(dialog, /onOpenAutoFocus=\{\(event\)/)
})

check('28. sucesso e erro possuem papeis acessiveis', () => {
  matches(dialog, /role="status"/)
  matches(dialog, /role="alert"/)
})

check('29. limites exibidos vem do backend', () => {
  matches(dialog, /consultarLimitesMinhasMidias\(anuncio\.slug\)/)
  matches(dialog, /limits\.maxFotoBytes/)
  matches(dialog, /limits\.maxVideoBytes/)
})

check('30. contrato usa sessao, CSRF e idempotencia', () => {
  matches(api, /bootstrapCsrfValue\(\)/)
  matches(api, /setRequestHeader\('Idempotency-Key', idempotencyKey\)/)
  matches(api, /xhr\.withCredentials = true/)
})

check('31. backend rejeita campos multipart extras e mais de um arquivo', () => {
  matches(storyController, /getParameterMap|parameterMap|multipart/i)
  matches(storyService, /recebidos\.size\(\) != 1/)
})

check('32. backend valida proprietaria e estado publicavel', () => {
  matches(storyService, /usuarioId/)
  matches(storyService, /!usuarioId\.equals\(anuncio\.getUsuarioId\(\)\)/)
  matches(storyService, /StatusAnuncio\.PUBLICADO/)
})

check('33. MIDIA_UPLOAD usa storage e pipeline canonicos', () => {
  matches(storyService, /MidiaUploadValidator/)
  matches(storyService, /FotoUploadProcessor/)
  matches(storyService, /storage\.putIfAbsent\(StorageArea\.PRIVATE_MEDIA/)
})

check('34. upload fica restrito ao Story e fora da galeria', () => {
  matches(storyService, /FinalidadeAnuncioMidia\.STORY/)
  matches(storyService, /criarStoryUploadValidado/)
  matches(storyEntity, /modoConteudo/)
})

check('35. feed anonimo permanece sem midia e apresentacao antes do gate', () => {
  const feed = sanitizeStoryFeedItem({
    modoConteudo: 'ANUNCIO',
    previewState: 'IDADE_NAO_CONFIRMADA',
    previewUrl: 'https://private.invalid/story.jpg',
    displayUsername: 'nome restrito',
    idade: 25,
  })
  assert.equal(feed.previewUrl, null)
  assert.equal(feed.displayUsername, null)
  assert.equal(feed.idade, null)
})

check('36. viewer so libera URL depois do gate', () => {
  assert.equal(sanitizeStoryViewerItem({
    modoConteudo: 'MIDIA_UPLOAD',
    viewerState: 'IDADE_NAO_CONFIRMADA',
    midiaUrl: '/privada/story.jpg',
  }).midiaUrl, null)
  assert.equal(sanitizeStoryViewerItem({
    modoConteudo: 'MIDIA_UPLOAD',
    viewerState: 'LIBERADO',
    midiaUrl: '/api/public/compliance/visitor/media/story-id',
  }).midiaUrl, '/api/public/compliance/visitor/media/story-id')
})

check('37. visto depende da renderizacao autorizada', () => {
  matches(viewer, /onLoad=\{markCurrentStoryVisible\}/)
  matches(viewer, /onPlaying=\{markCurrentStoryVisible\}/)
  matches(viewer, /IntersectionObserver/)
})

check('38. administracao de Stories permanece cumulativa', () => {
  matches(adminStoryService, /StorySelecaoAdministrativa/)
  excludes(adminStoryService, /deleteAll\s*\(/)
})

check('39. STORIES pertence ao dominio canonico, mas segue fora do wizard generico', () => {
  matches(monetizationStep, /feature\.codigo === 'STORIES'\) continue/)
  const genericCodes = premiumCodes.match(/TODOS = Set\.of\(([\s\S]*?)\);/)?.[1] ?? ''
  matches(genericCodes, /STORIES/)
})

check('40. migration preserva historico e limita um ativo por anuncio', () => {
  matches(migration, /modo_conteudo/)
  matches(migration, /WHERE[\s\S]*PUBLICADO/i)
  excludes(migration, /UPDATE\s+story_anuncio/i)
})

check('41. OpenAPI documenta somente os dois modos e nenhum segredo de storage', () => {
  matches(openapi, /\/minha-conta\/anuncios\/\{slug\}\/stories:/)
  matches(openapi, /enum:\s*\[ANUNCIO, MIDIA_UPLOAD\]/)
  const storySchema = openapi.slice(openapi.indexOf('MeuAnuncioStory:'))
  excludes(storySchema.slice(0, 1800), /objectKey|bucket|urlPrivada|usuarioId/)
  matches(storyFeed, /ModoConteudoStory\.ANUNCIO/)
})

check('42. Story aceita somente MP4 web compativel e nao anuncia MOV', () => {
  matches(dialog, /const ACCEPTED_MEDIA = [^\n]*\.mp4/)
  excludes(dialog, /\.mov|video\/quicktime|MP4 ou MOV/i)
  matches(openapi, /video MP4 com H\.264 e audio AAC-LC opcional/)
  excludes(openapi.slice(openapi.indexOf('/minha-conta/anuncios/{slug}/stories:'), openapi.indexOf('/minha-conta/anuncios/{slug}/stories:') + 5000), /MP4\/MOV|video\/quicktime/)
})

check('43. botao permanece visivel e estado utiliza elegibilidade canonica', () => {
  matches(entryState, /anuncio\.status !== 'PUBLICADO' \|\| anuncio\.statusModeracao !== 'APROVADO'/)
  matches(entryState, /new Set\(\['ATIVO', 'DISPONIVEL_PARA_PUBLICAR'\]\)/)
  excludes(entryState, /'PENDENTE'\]/)
  matches(card, /disabled=\{storyEntry\.disabled\}/)
  excludes(card, /aria-disabled=\{storyEntry\.disabled\}/)
  matches(card, /storyEntry\.buttonLabel/)
})

assert.equal(checks, 43)
console.log(`STORY_CREATE_MODES_CHECKS=${checks}`)
console.log('STORY_CREATE_MODES_RESULT=OK')
