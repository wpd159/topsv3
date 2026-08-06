import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(
  new URL('../src/components/stories/story-access-policy.js', import.meta.url),
  'utf8',
)
const viewerSource = await readFile(
  new URL('../src/components/stories/story-viewer-dialog.tsx', import.meta.url),
  'utf8',
)
const modalSource = await readFile(
  new URL('../src/components/compliance/visitor-verification-modal.tsx', import.meta.url),
  'utf8',
)
const panelSource = await readFile(
  new URL('../src/components/stories/meus-stories-panel.tsx', import.meta.url),
  'utf8',
)
const apiSource = await readFile(
  new URL('../src/lib/minha-conta-stories-api.ts', import.meta.url),
  'utf8',
)
const moduleUrl = `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const {
  canNavigateFromStory,
  sanitizeStoryFeedItem,
  sanitizeStoryViewerItem,
} = await import(moduleUrl)

const leakedPublicOriginal = 'https://public.example.invalid/original.jpg'
const leakedSignedOriginal = 'https://private.example.invalid/original.jpg?X-Amz-Signature=redacted'

assert.equal(sanitizeStoryFeedItem({
  previewState: 'IDADE_NAO_CONFIRMADA',
  previewUrl: leakedPublicOriginal,
}).previewUrl, null)
assert.equal(sanitizeStoryFeedItem({
  previewState: 'UNAVAILABLE',
  previewUrl: leakedSignedOriginal,
}).previewUrl, null)
assert.equal(sanitizeStoryFeedItem({
  previewState: 'AVAILABLE',
  previewUrl: '/preview-autorizada.jpg',
}).previewUrl, '/preview-autorizada.jpg')
const lockedFeed = sanitizeStoryFeedItem({
  modoConteudo: 'MIDIA_UPLOAD',
  previewState: 'IDADE_NAO_CONFIRMADA',
  previewUrl: leakedSignedOriginal,
  usuarioUsername: 'perfil-privado',
  profileNavigable: true,
})
assert.equal(lockedFeed.previewUrl, null)
assert.equal(lockedFeed.usuarioUsername, null)
assert.equal(lockedFeed.profileNavigable, false)

assert.equal(sanitizeStoryViewerItem({
  viewerState: 'IDADE_NAO_CONFIRMADA',
  midiaUrl: leakedPublicOriginal,
}).midiaUrl, null)
assert.equal(sanitizeStoryViewerItem({
  viewerState: 'INDISPONIVEL',
  midiaUrl: leakedSignedOriginal,
}).midiaUrl, null)
assert.equal(sanitizeStoryViewerItem({
  viewerState: 'LIBERADO',
  midiaUrl: '/api/public/compliance/visitor/media/synthetic-id',
}).midiaUrl, '/api/public/compliance/visitor/media/synthetic-id')
const lockedViewer = sanitizeStoryViewerItem({
  modoConteudo: 'MIDIA_UPLOAD',
  viewerState: 'IDADE_NAO_CONFIRMADA',
  midiaUrl: leakedSignedOriginal,
  usuarioUsername: 'perfil-privado',
  profileNavigable: true,
})
assert.equal(lockedViewer.midiaUrl, null)
assert.equal(lockedViewer.usuarioUsername, null)
assert.equal(lockedViewer.profileNavigable, false)
const lockedAnuncioViewer = sanitizeStoryViewerItem({
  modoConteudo: 'ANUNCIO',
  viewerState: 'IDADE_NAO_CONFIRMADA',
  midias: [{
    id: 'midia-bloqueada',
    tipo: 'FOTO',
    autorizada: true,
    urlPublica: leakedPublicOriginal,
  }],
})
assert.deepEqual(lockedAnuncioViewer.midias, [])

const releasedAnuncioViewer = sanitizeStoryViewerItem({
  modoConteudo: 'ANUNCIO',
  viewerState: 'LIBERADO',
  midias: [
    {
      id: 'midia-valida',
      tipo: 'FOTO',
      ordem: 0,
      autorizada: true,
      urlPublica: '/api/public/compliance/visitor/media/midia-valida',
      objectKey: 'private/nao-pode-vazar.jpg',
    },
    { tipo: 'VIDEO', autorizada: false, urlPublica: '/nao-autorizada.mp4' },
    { tipo: 'FOTO', autorizada: true, urlPublica: leakedSignedOriginal },
  ],
})
assert.equal(releasedAnuncioViewer.midias.length, 1)
assert.equal(releasedAnuncioViewer.midias[0].urlPublica, '/api/public/compliance/visitor/media/midia-valida')
assert.equal('objectKey' in releasedAnuncioViewer.midias[0], false)

const released = {
  viewerState: 'LIBERADO',
  midiaUrl: '/api/public/compliance/visitor/media/synthetic-id',
}
assert.equal(canNavigateFromStory(released, false, false), false)
assert.equal(canNavigateFromStory(released, true, true), false)
assert.equal(canNavigateFromStory(released, true, false), true)
assert.equal(canNavigateFromStory({ viewerState: 'IDADE_NAO_CONFIRMADA' }, true, false), false)

assert.match(viewerSource, /if \(verificationOpen \|\| !mediaReady\) return/)
assert.match(viewerSource, /disabled=\{isLastVisibleContent \|\| viewerNavegacaoTravada\}/)
assert.equal((viewerSource.match(/disabled=\{viewerNavegacaoTravada\}/g) || []).length, 2)
assert.match(viewerSource, /podeNavegarAnuncio[\s\S]*canNavigateFromStory/)
assert.match(viewerSource, /onLoad=\{markCurrentStoryVisible\}/)
assert.match(viewerSource, /onPlaying=\{markCurrentStoryVisible\}/)
assert.match(viewerSource, /fetch\(publicApiUrl\(`\/stories\/\$\{currentFeedItem\.storyId\}`\)/)
assert.match(viewerSource, /onVerified=\{async \(\) => \{[\s\S]*await refreshViewerAndFeed\(\)[\s\S]*setVerificationOpen\(false\)/)
assert.doesNotMatch(viewerSource, /Promise\.all\([\s\S]*stories/i)
assert.doesNotMatch(viewerSource, /visitorId/)
assert.doesNotMatch(viewerSource, /backgroundImage=/)
assert.doesNotMatch(viewerSource, /secondaryAction=/)
assert.doesNotMatch(viewerSource, /if \(open && currentFeedItem\) onStoryCurrent/)

assert.match(modalSource, /const storyContext = scope === "STORY"/)
assert.match(modalSource, /if \(storyContext && !context\?\.storyId\)/)
assert.match(modalSource, /anuncioId: storyContext \? undefined : String\(context\?\.anuncioId\)/)
assert.match(modalSource, /midiaId: storyContext \? undefined : context\?\.midiaId \|\| undefined/)
assert.match(modalSource, /storyId: storyContext \? context\?\.storyId : undefined/)
const viewerChallengeContext = viewerSource.match(/context=\{\{([\s\S]*?)\}\}\s+onOpenChange/)
assert.ok(viewerChallengeContext, 'Contexto do challenge de Story ausente.')
assert.match(viewerChallengeContext[1], /storyId:/)
assert.doesNotMatch(viewerChallengeContext[1], /anuncioId:/)

assert.match(panelSource, /case 'ENCERRADO_USUARIO':/)
assert.match(panelSource, /case 'DESCARTADO_FALHA_TECNICA':/)
assert.match(panelSource, /case 'REMOVIDO_ADMIN':/)
assert.match(panelSource, /case 'EXPIRADO':/)
assert.match(panelSource, /role="status"/)
assert.match(panelSource, /role="alert"/)
assert.match(panelSource, /w-\[calc\(100vw-1rem\)\] sm:max-w-md/)
assert.doesNotMatch(panelSource, /min-w-\[\d{3,}px\]/)
assert.match(panelSource, /error\.requestId/)
const confirmEndSource = panelSource.match(/async function confirmEnd\(\) \{([\s\S]*?)\r?\n  \}\r?\n  return \(/)
assert.ok(confirmEndSource, 'Fluxo de encerramento nao encontrado.')
const mutationCatch = confirmEndSource[1].match(/catch \(cause\) \{([\s\S]*?)\r?\n    \} finally/)
assert.ok(mutationCatch, 'Reconciliacao apos erro nao encontrada.')
assert.equal((mutationCatch[1].match(/listarMeusStories\(/g) || []).length, 1)
assert.doesNotMatch(mutationCatch[1], /encerrarMeuStory\(/)
assert.match(mutationCatch[1], /encerramentoConfirmado\(reconciliado\)/)
assert.equal((panelSource.match(/await encerrarMeuStory\(/g) || []).length, 1)
assert.match(apiSource, /method: 'DELETE'/)
assert.match(apiSource, /cache: 'no-store'/)
assert.doesNotMatch(panelSource, /objectKey|X-Amz-|cloudflarestorage/i)

console.log('STORY_AGE_GATE_FAIL_CLOSED_RESULT=OK')
