import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [serviceSource, barSource, viewerSource, typesSource, orderingSource] = await Promise.all([
  readFile(new URL('../../backend/src/main/java/br/com/topsdojob/v3/application/publico/service/StoryFeedPublicoService.java', import.meta.url), 'utf8'),
  readFile(new URL('../src/components/stories/stories-bar.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/components/stories/story-viewer-dialog.tsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/components/stories/stories-types.ts', import.meta.url), 'utf8'),
  readFile(new URL('../src/components/stories/story-ordering.js', import.meta.url), 'utf8'),
])

assert.match(serviceSource, /return stories\.stream\(\)\.map\(item -> \{/)
assert.match(serviceSource, /new StoryFeedBundleDto\(\s*feed\.storyId\(\)/s)
assert.match(serviceSource, /List\.of\(feed\)/)
assert.doesNotMatch(serviceSource, /porUsuario|story-owner:/)
assert.match(serviceSource, /private String usernamePublico\(UsuarioEntity usuario\)[\s\S]*usuario\.getNome\(\)/)
assert.doesNotMatch(serviceSource, /MessageDigest|HexFormat|topsv3-public-user-v1/)

assert.match(barSource, /key=\{String\(primeiro\?\.storyId \?\? index\)\}/)
assert.doesNotMatch(barSource, /key=\{`\$\{bundle\.bundleKey/)
assert.match(barSource, /onClick=\{\(\) => openBundleAt\(index\)\}/)
assert.match(viewerSource, /const rotuloPerfil = loginViewer \? `@\$\{loginViewer\}` : rotuloBundle/)
assert.match(viewerSource, /\{viewerItem\.anuncioTitulo \|\| "Anúncio"\}/)
assert.doesNotMatch(viewerSource, /@\$\{viewerItem\.displayUsername\}/)
assert.equal((typesSource.match(/anuncioTitulo\?: string \| null/g) || []).length, 2)

const orderingUrl = `data:text/javascript;base64,${Buffer.from(orderingSource).toString('base64')}`
const {
  markStorySeen,
  orderStoryBundles,
  readStoryState,
  storyIdentity,
} = await import(orderingUrl)

class MemoryStorage {
  constructor() {
    this.values = new Map()
  }

  get length() {
    return this.values.size
  }

  key(index) {
    return [...this.values.keys()][index] ?? null
  }

  getItem(key) {
    return this.values.get(key) ?? null
  }

  setItem(key, value) {
    this.values.set(key, value)
  }

  removeItem(key) {
    this.values.delete(key)
  }
}

const now = Date.parse('2026-08-05T15:00:00Z')
const expiraEm = '2026-08-06T15:00:00Z'
const storyAnuncio = {
  storyId: 'story-anuncio',
  modoConteudo: 'ANUNCIO',
  usuarioUsername: 'wesley',
  displayUsername: 'wesley',
  anuncioTitulo: 'Título separado',
  expiraEm,
}
const storyMidia = {
  storyId: 'story-midia',
  modoConteudo: 'MIDIA_UPLOAD',
  usuarioUsername: 'wesley',
  displayUsername: 'wesley',
  anuncioTitulo: null,
  expiraEm,
}
const bundles = [
  { bundleKey: storyAnuncio.storyId, usuarioUsername: 'wesley', itens: [storyAnuncio] },
  { bundleKey: storyMidia.storyId, usuarioUsername: 'wesley', itens: [storyMidia] },
]

assert.equal(bundles.length, 2)
assert.ok(bundles.every((bundle) => bundle.itens.length === 1))
assert.deepEqual(bundles.map((bundle) => bundle.itens[0].storyId), ['story-anuncio', 'story-midia'])
assert.ok(bundles.every((bundle) => bundle.usuarioUsername === 'wesley'))
assert.equal(storyAnuncio.displayUsername, 'wesley')
assert.equal(storyAnuncio.anuncioTitulo, 'Título separado')
assert.doesNotMatch(JSON.stringify(bundles), /[0-9a-f]{32}/)

const storage = new MemoryStorage()
readStoryState(storage, 'visitor', bundles, now, () => 'seed-independent')
markStorySeen(storage, 'visitor', storyAnuncio, bundles, now, () => 'seed-independent')
const state = readStoryState(storage, 'visitor', bundles, now, () => 'seed-independent')
assert.equal(Boolean(state.seen[storyIdentity(storyAnuncio)]), true)
assert.equal(Boolean(state.seen[storyIdentity(storyMidia)]), false)

const ordered = orderStoryBundles(bundles, state)
assert.equal(ordered.find((bundle) => bundle.bundleKey === 'story-anuncio').visto, true)
assert.equal(ordered.find((bundle) => bundle.bundleKey === 'story-midia').visto, false)
assert.deepEqual(
  ordered.map((bundle) => bundle.itens.map((item) => item.storyId)),
  ordered.map((bundle) => [bundle.bundleKey]),
)

console.log('Stories independentes: círculos, username público e vistos por storyId validados.')
