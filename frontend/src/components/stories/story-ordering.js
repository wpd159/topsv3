const STORAGE_PREFIX = 'topsv3:stories:v1:'
const FALLBACK_TTL_MS = 24 * 60 * 60 * 1000

function dayInSaoPaulo(nowMs) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Sao_Paulo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date(nowMs))
  const value = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${value.year}-${value.month}-${value.day}`
}

function hash(value) {
  let result = 2166136261
  for (let index = 0; index < value.length; index += 1) {
    result ^= value.charCodeAt(index)
    result = Math.imul(result, 16777619)
  }
  return result >>> 0
}

function expirationMs(item, nowMs) {
  if (!item?.expiraEm) return nowMs + FALLBACK_TTL_MS
  const parsed = new Date(item.expiraEm).getTime()
  return Number.isFinite(parsed) ? parsed : nowMs
}

export function storyIdentity(item) {
  const expiration = item?.expiraEm ? new Date(item.expiraEm).getTime() : 0
  return `${String(item?.storyId ?? '')}@${Number.isFinite(expiration) ? expiration : 0}`
}

export function storyStorageKey(namespace) {
  return `${STORAGE_PREFIX}${namespace || 'visitor'}`
}

function markerPrefix(namespace) {
  return `${storyStorageKey(namespace)}:seen:`
}

function markerKey(namespace, identity) {
  return `${markerPrefix(namespace)}${encodeURIComponent(identity)}`
}

function safeGet(storage, key) {
  try {
    return storage?.getItem?.(key) ?? null
  } catch {
    return null
  }
}

function safeSet(storage, key, value) {
  try {
    storage?.setItem?.(key, value)
    return true
  } catch {
    return false
  }
}

function cleanupMarkers(storage, namespace, active, nowMs) {
  try {
    const prefix = markerPrefix(namespace)
    for (let index = Number(storage?.length || 0) - 1; index >= 0; index -= 1) {
      const key = storage.key(index)
      if (!key?.startsWith(prefix)) continue
      const identity = decodeURIComponent(key.slice(prefix.length))
      const expiry = Number(storage.getItem(key))
      if (!active.has(identity) || !Number.isFinite(expiry) || expiry <= nowMs) {
        storage.removeItem(key)
      }
    }
  } catch {
    // Storage can be unavailable in private browsing. Ordering remains in memory.
  }
}

export function readStoryState(storage, namespace, bundles, nowMs = Date.now(), seedFactory = () => crypto.randomUUID()) {
  const window = dayInSaoPaulo(nowMs)
  let parsed = null
  try {
    parsed = JSON.parse(safeGet(storage, storyStorageKey(namespace)) || 'null')
  } catch {
    parsed = null
  }

  const seed = parsed?.window === window && typeof parsed?.seed === 'string'
    ? parsed.seed
    : seedFactory()
  const previousSeen = parsed?.seen && typeof parsed.seen === 'object'
    ? parsed.seen
    : {}
  const active = new Map()
  for (const bundle of bundles || []) {
    for (const item of bundle?.itens || []) {
      const expiry = expirationMs(item, nowMs)
      if (expiry > nowMs) active.set(storyIdentity(item), expiry)
    }
  }
  cleanupMarkers(storage, namespace, active, nowMs)
  const seen = {}
  for (const [identity, expiry] of active) {
    const markerExpiry = Number(safeGet(storage, markerKey(namespace, identity)))
    if (previousSeen[identity] || (Number.isFinite(markerExpiry) && markerExpiry > nowMs)) {
      seen[identity] = expiry
    }
  }
  const state = { window, seed, seen }
  safeSet(storage, storyStorageKey(namespace), JSON.stringify(state))
  return state
}

export function markStorySeen(storage, namespace, item, bundles, nowMs = Date.now(), seedFactory) {
  const identity = storyIdentity(item)
  const expiry = expirationMs(item, nowMs)
  if (expiry > nowMs) safeSet(storage, markerKey(namespace, identity), String(expiry))
  const state = readStoryState(storage, namespace, bundles, nowMs, seedFactory)
  if (expiry > nowMs) state.seen[identity] = expiry
  safeSet(storage, storyStorageKey(namespace), JSON.stringify(state))
  return state
}

export function orderStoryBundles(bundles, state) {
  const seed = state?.seed || 'stories'
  const seen = state?.seen || {}
  return (bundles || [])
    .map((bundle) => {
      const itens = [...(bundle?.itens || [])].sort((left, right) => {
        const leftSeen = Boolean(seen[storyIdentity(left)])
        const rightSeen = Boolean(seen[storyIdentity(right)])
        if (leftSeen !== rightSeen) return leftSeen ? 1 : -1
        return hash(`${seed}:item:${storyIdentity(left)}`) - hash(`${seed}:item:${storyIdentity(right)}`)
      })
      const visto = itens.length > 0 && itens.every((item) => Boolean(seen[storyIdentity(item)]))
      return { ...bundle, visto, itens }
    })
    .sort((left, right) => {
      if (Boolean(left.visto) !== Boolean(right.visto)) return left.visto ? 1 : -1
      const leftKey = `${left.usuarioId}:${left.itens.map(storyIdentity).join(',')}`
      const rightKey = `${right.usuarioId}:${right.itens.map(storyIdentity).join(',')}`
      return hash(`${seed}:bundle:${leftKey}`) - hash(`${seed}:bundle:${rightKey}`)
    })
}
