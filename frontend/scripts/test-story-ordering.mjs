import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(
  new URL('../src/components/stories/story-ordering.js', import.meta.url),
  'utf8',
)
const moduleUrl = `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
const {
  markStorySeen,
  orderStoryBundles,
  readStoryState,
  storyIdentity,
  storyStorageKey,
} = await import(moduleUrl)

class MemoryStorage {
  constructor(values = new Map()) {
    this.values = values
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

const now = Date.parse('2026-07-31T15:00:00Z')
const expiry = '2026-08-01T15:00:00Z'
const item = (storyId) => ({ storyId, expiraEm: expiry })
const bundle = (usuarioId, ...itens) => ({ usuarioId, itens })
const storage = new MemoryStorage()
const initial = [bundle('anunciante-a', item('a')), bundle('anunciante-b', item('b'))]

const stateA = readStoryState(storage, 'user:a', initial, now, () => 'seed-a')
assert.deepEqual(
  orderStoryBundles(initial, stateA),
  orderStoryBundles(initial, stateA),
  'A ordem deve permanecer estável para a mesma audiência e janela diária.',
)

markStorySeen(storage, 'user:a', initial[0].itens[0], initial, now, () => 'seed-a')
const reorderedA = orderStoryBundles(
  initial,
  readStoryState(storage, 'user:a', initial, now, () => 'seed-a'),
)
assert.equal(reorderedA[0].usuarioId, 'anunciante-b')
assert.equal(reorderedA.at(-1).usuarioId, 'anunciante-a')
assert.equal(reorderedA.at(-1).visto, true)

const stateB = readStoryState(storage, 'user:b', initial, now, () => 'seed-b')
assert.equal(Object.keys(stateB.seen).length, 0, 'O visto de uma conta não pode vazar para outra.')

const withNewStory = [...initial, bundle('anunciante-c', item('c'))]
const reorderedWithNew = orderStoryBundles(
  withNewStory,
  readStoryState(storage, 'user:a', withNewStory, now, () => 'seed-a'),
)
assert.equal(reorderedWithNew.at(-1).usuarioId, 'anunciante-a')
assert.equal(reorderedWithNew.find((entry) => entry.usuarioId === 'anunciante-c').visto, false)

const mixedBundle = [bundle('anunciante-misto', item('vista'), item('nova'))]
markStorySeen(storage, 'visitor', mixedBundle[0].itens[0], mixedBundle, now, () => 'seed-visitor')
const mixedOrdered = orderStoryBundles(
  mixedBundle,
  readStoryState(storage, 'visitor', mixedBundle, now, () => 'seed-visitor'),
)
assert.equal(mixedOrdered[0].visto, false)
assert.equal(storyIdentity(mixedOrdered[0].itens[0]), storyIdentity(item('nova')))

storage.setItem(storyStorageKey('invalid-json'), '{invalido')
assert.doesNotThrow(() => readStoryState(storage, 'invalid-json', initial, now, () => 'seed-invalid'))

const unavailableStorage = {
  getItem() { throw new Error('storage unavailable') },
  setItem() { throw new Error('storage unavailable') },
  removeItem() { throw new Error('storage unavailable') },
  key() { throw new Error('storage unavailable') },
  get length() { throw new Error('storage unavailable') },
}
assert.doesNotThrow(() => readStoryState(unavailableStorage, 'private', initial, now, () => 'seed-private'))
assert.doesNotThrow(() => markStorySeen(
  unavailableStorage,
  'private',
  initial[0].itens[0],
  initial,
  now,
  () => 'seed-private',
))

const dailyStorage = new MemoryStorage()
const longExpiry = '2026-08-03T15:00:00Z'
const dailyBundles = [bundle('daily', { storyId: 'daily-story', expiraEm: longExpiry })]
const dailyInitial = markStorySeen(dailyStorage, 'user:daily', dailyBundles[0].itens[0], dailyBundles, now, () => 'seed-day-1')
const nextDay = now + 18 * 60 * 60 * 1000
const dailyRotated = readStoryState(dailyStorage, 'user:daily', dailyBundles, nextDay, () => 'seed-day-2')
assert.notEqual(dailyRotated.window, dailyInitial.window, 'A janela deve mudar no novo dia de Sao Paulo.')
assert.notEqual(dailyRotated.seed, dailyInitial.seed, 'A semente deve rotacionar diariamente.')
assert.equal(Object.keys(dailyRotated.seen).length, 1, 'A troca do dia nao apaga um Story ainda ativo ja visto.')

const expiringStorage = new MemoryStorage()
const shortBundles = [bundle('short', { storyId: 'short-story', expiraEm: '2026-07-31T16:00:00Z' })]
markStorySeen(expiringStorage, 'visitor-expiring', shortBundles[0].itens[0], shortBundles, now, () => 'seed-short')
const afterExpiry = readStoryState(
  expiringStorage,
  'visitor-expiring',
  shortBundles,
  Date.parse('2026-07-31T16:01:00Z'),
  () => 'seed-short',
)
assert.equal(Object.keys(afterExpiry.seen).length, 0)
assert.equal(
  [...expiringStorage.values.keys()].filter((key) => key.includes(':seen:')).length,
  0,
  'Marcadores expirados devem ser removidos.',
)

const sharedValues = new Map()
const tabA = new MemoryStorage(sharedValues)
const tabB = new MemoryStorage(sharedValues)
const multiTab = [bundle('multi', item('tab-a'), item('tab-b'))]
const staleTabB = readStoryState(tabB, 'user:multi', multiTab, now, () => 'seed-multi')
markStorySeen(tabA, 'user:multi', multiTab[0].itens[0], multiTab, now, () => 'seed-multi')
tabB.setItem(storyStorageKey('user:multi'), JSON.stringify(staleTabB))
markStorySeen(tabB, 'user:multi', multiTab[0].itens[1], multiTab, now, () => 'seed-multi')
const mergedTabs = readStoryState(tabA, 'user:multi', multiTab, now, () => 'seed-multi')
assert.equal(Object.keys(mergedTabs.seen).length, 2, 'Abas concorrentes nao podem perder marcacoes por storyId.')

assert.doesNotMatch(source, /Math\.random/)
console.log('Stories: ordem diaria, vistos por item, limpeza, storage privado e multiplas abas validados.')
