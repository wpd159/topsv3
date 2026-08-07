import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [serviceSource, anuncioServiceSource, viewerSource, policySource, typesSource, barSource, openapiSource] = await Promise.all([
  readFile(new URL('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/service/StoryFeedPublicoService.java', import.meta.url), 'utf8'),
  readFile(new URL('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/service/AnuncioPublicoConsultaService.java', import.meta.url), 'utf8'),
  readFile(new URL('../src/components/stories/story-viewer-dialog.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/components/stories/story-access-policy.js', import.meta.url), 'utf8'),
  readFile(new URL('../src/components/stories/stories-types.ts', import.meta.url), 'utf8'),
  readFile(new URL('../src/components/stories/stories-bar.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../../contracts/openapi/topsdojob-v3-local.yaml', import.meta.url), 'utf8'),
])

assert.match(serviceSource, /midiasPublicaveisDoAnuncio\(anuncio, idadeConfirmada\)/)
assert.match(serviceSource, /if \(!idadeConfirmada \|\| anuncioConsultaService == null\)[\s\S]*return List\.of\(\)/)
assert.match(serviceSource, /anuncioConsultaService\.midiasParaStory\(anuncio, true\)/)
assert.match(serviceSource, /filter\(item -> item\.urlPublica\(\) != null/)
assert.match(anuncioServiceSource, /if \(!idadeConfirmada \|\| anuncio == null \|\| anuncio\.getId\(\) == null\)[\s\S]*return List\.of\(\)/)
assert.match(anuncioServiceSource, /return midias\(anuncio\.getId\(\), true, premiumMapper\.flags\(anuncio\)\)/)
assert.match(typesSource, /midias\?: StoryViewerMedia\[\]/)
assert.match(openapiSource, /StoryViewerPublico:[\s\S]*required: \[[^\n]*midias/)
assert.match(openapiSource, /midias:[\s\S]*type: array[\s\S]*MidiaPublica/)
assert.match(viewerSource, /const \[anuncioMidiaIndex, setAnuncioMidiaIndex\] = useState\(0\)/)
assert.match(viewerSource, /const anuncioMidias = useMemo/)
assert.match(viewerSource, /setAnuncioMidiaIndex\(\(current\) => current \+ 1\)/)
assert.match(viewerSource, /setAnuncioMidiaIndex\(\(current\) => current - 1\)/)
assert.match(viewerSource, /const possuiProximoStory = Boolean\(currentBundle\)/)
const nextStoryControl = viewerSource.match(
  /\{possuiProximoStory \? \([\s\S]*?aria-label="Próximo Story"[\s\S]*?\) : null\}/,
)?.[0]
assert.ok(nextStoryControl, 'Deve existir um controle explícito para o próximo Story.')
assert.match(nextStoryControl, /onClick=\{nextStory\}/)
assert.doesNotMatch(nextStoryControl, /nextVisibleContent/)
assert.match(
  viewerSource,
  /const nextVisibleContent = useCallback\([\s\S]*setAnuncioMidiaIndex\(\(current\) => current \+ 1\)[\s\S]*nextStory\(\)/,
)
assert.match(viewerSource, /const identity = String\(currentFeedItem\.storyId\)/)
assert.match(viewerSource, /anuncioMidiaAtual\.tipo === "VIDEO"/)
assert.match(viewerSource, /src=\{anuncioMidiaAtual\.urlPublica\}/)
assert.match(viewerSource, /onLoad=\{markCurrentStoryVisible\}/)
assert.match(viewerSource, /onPlaying=\{markCurrentStoryVisible\}/)
assert.match(viewerSource, /Mídia \{anuncioMidiaIndex \+ 1\} de \{anuncioMidias\.length\}/)
assert.match(viewerSource, /\{viewerItem\.anuncioTitulo \|\| "Anúncio"\}/)
assert.match(viewerSource, />\s*Ver anúncio\s*</)
assert.match(viewerSource, /if \(!anuncioMidiaAtual\)[\s\S]*Apresentação textual do anúncio no Story/)
assert.match(viewerSource, /DialogContent className="flex h-\[100dvh\] max-h-\[100dvh\][^"]*overflow-hidden/)
assert.match(viewerSource, /"relative flex h-full min-h-0 w-full flex-col overflow-hidden"/)
assert.match(viewerSource, /className="relative z-30 shrink-0/)
assert.match(viewerSource, /className="flex min-h-0 flex-1 w-full items-stretch justify-center overflow-hidden"/)
assert.match(viewerSource, /object-contain object-top/)
assert.match(viewerSource, /pb-4 pt-12/)
assert.match(viewerSource, /pointer-events-auto mt-3 min-h-10/)
assert.equal((viewerSource.match(/overflow-y-auto/g) ?? []).length, 1)
assert.doesNotMatch(viewerSource, /h-\[100svh\]/)
assert.doesNotMatch(viewerSource, /srcSet|srcset/)
assert.doesNotMatch(viewerSource, /objectKey|chaveObjeto/)
assert.doesNotMatch(barSource, /anuncioMidias|midias\.map/)

const moduleUrl = 'data:text/javascript;base64,' + Buffer.from(policySource).toString('base64')
const { sanitizeStoryViewerItem } = await import(moduleUrl)

const locked = sanitizeStoryViewerItem({
  modoConteudo: 'ANUNCIO',
  viewerState: 'IDADE_NAO_CONFIRMADA',
  midias: [
    { tipo: 'FOTO', autorizada: true, urlPublica: 'https://media.example.invalid/original.jpg' },
  ],
})
assert.deepEqual(locked.midias, [])

const released = sanitizeStoryViewerItem({
  modoConteudo: 'ANUNCIO',
  viewerState: 'LIBERADO',
  midias: [
    { id: 'm0', tipo: 'FOTO', ordem: 0, autorizada: true, urlPublica: '/api/public/compliance/visitor/media/m0' },
    { id: 'm1', tipo: 'VIDEO', ordem: 1, autorizada: true, urlPublica: '/api/public/compliance/visitor/media/m1' },
  ],
})
assert.deepEqual(released.midias.map((item) => item.id), ['m0', 'm1'])
assert.deepEqual(released.midias.map((item) => item.ordem), [0, 1])

console.log('Story ANUNCIO: galeria publicável, navegação interna, gate e círculo único validados.')
