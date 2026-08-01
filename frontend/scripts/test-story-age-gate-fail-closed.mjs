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

const released = {
  viewerState: 'LIBERADO',
  midiaUrl: '/api/public/compliance/visitor/media/synthetic-id',
}
assert.equal(canNavigateFromStory(released, false, false), false)
assert.equal(canNavigateFromStory(released, true, true), false)
assert.equal(canNavigateFromStory(released, true, false), true)
assert.equal(canNavigateFromStory({ viewerState: 'IDADE_NAO_CONFIRMADA' }, true, false), false)

assert.match(viewerSource, /if \(verificationOpen \|\| !mediaReady\) return/)
assert.match(viewerSource, /disabled=\{isLast \|\| viewerNavegacaoTravada\}/)
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

console.log('STORY_AGE_GATE_FAIL_CLOSED_RESULT=OK')
