import "server-only"

const INDEXNOW_ENDPOINT = "https://api.indexnow.org/indexnow"
const INDEXNOW_CANONICAL_SITE_URL = "https://topsdojob.com"
const INDEXNOW_DEDUP_WINDOW_MS = 5 * 60 * 1000
const INDEXNOW_MAX_URLS_PER_EVENT = 20
const INDEXNOW_MAX_ATTEMPTS = 3
const INDEXNOW_TIMEOUT_MS = 4_000
const INDEXNOW_KEY_PATTERN = /^[A-Za-z0-9-]{8,128}$/

const NON_INDEXABLE_PREFIXES = [
  "/admin", "/anunciar", "/api", "/chat", "/checkout", "/creditos",
  "/documentos", "/favoritos", "/internal", "/kyc", "/meus-anuncios",
  "/meus-tickets", "/minha-conta", "/painel", "/planos-e-creditos",
  "/preview", "/registrar", "/uploads", "/webhooks",
] as const

type IndexNowFetch = typeof fetch

type IndexNowDependencies = {
  fetchImpl?: IndexNowFetch
  sleep?: (delayMs: number) => Promise<void>
  now?: () => number
  timeoutMs?: number
}

export type IndexNowEventType = "PUBLICACAO" | "ATUALIZACAO" | "RETIRADA"

export type IndexNowPublicEvent = {
  eventType: IndexNowEventType
  eventFingerprint: string
  urls: string[]
}

export type IndexNowSubmissionResult = {
  ok: boolean
  status: number
  attempts: number
  urlCount: number
  deduplicatedCount: number
  externalRequest: boolean
  eventType: IndexNowEventType
  reason?: "DISABLED" | "EMPTY" | "HTTP" | "NETWORK"
}

type SuccessfulSubmission = {
  eventType: IndexNowEventType
  eventFingerprint: string
  successAt: number
}

const successfulSubmissionByUrl = new Map<string, SuccessfulSubmission>()
let submissionQueue: Promise<void> = Promise.resolve()

function normalizarBaseUrl(value?: string) {
  return (value ?? "").trim().replace(/\/$/, "")
}

function isNonIndexablePath(pathname: string) {
  return NON_INDEXABLE_PREFIXES.some(
    (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`)
  )
}

function defaultSleep(delayMs: number) {
  return new Promise<void>((resolve) => setTimeout(resolve, delayMs))
}

function normalizedFingerprint(value: string) {
  return value.trim().slice(0, 512)
}

export function getIndexNowConfig() {
  const key = (process.env.INDEXNOW_KEY ?? "").trim()
  const siteUrl = normalizarBaseUrl(process.env.INDEXNOW_SITE_URL || process.env.NEXT_PUBLIC_SITE_URL)
  return {
    key,
    siteUrl,
    enabled: siteUrl === INDEXNOW_CANONICAL_SITE_URL && INDEXNOW_KEY_PATTERN.test(key),
  }
}

export function getIndexNowKeyLocation() {
  const config = getIndexNowConfig()
  return config.enabled ? `${config.siteUrl}/${config.key}.txt` : ""
}

export function filtrarUrlsDoHost(urls: string[]) {
  const { siteUrl } = getIndexNowConfig()
  if (siteUrl !== INDEXNOW_CANONICAL_SITE_URL) return []

  const filtered = new Set<string>()
  for (const value of urls) {
    if (filtered.size >= INDEXNOW_MAX_URLS_PER_EVENT) break
    try {
      const url = new URL(value)
      if (
        url.protocol !== "https:" || url.origin !== INDEXNOW_CANONICAL_SITE_URL ||
        url.username || url.password || url.search || url.hash ||
        isNonIndexablePath(url.pathname)
      ) continue
      filtered.add(url.href)
    } catch {
      // URL invalida fica fora do lote.
    }
  }
  return Array.from(filtered)
}

async function fetchWithTimeout(fetchImpl: IndexNowFetch, init: RequestInit, timeoutMs: number) {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), timeoutMs)
  try {
    return await fetchImpl(INDEXNOW_ENDPOINT, { ...init, signal: controller.signal })
  } finally {
    clearTimeout(timeout)
  }
}

function resultWithoutRequest(
  eventType: IndexNowEventType,
  reason: "DISABLED" | "EMPTY",
  deduplicatedCount = 0
): IndexNowSubmissionResult {
  return {
    ok: reason === "EMPTY", status: 0, attempts: 0, urlCount: 0,
    deduplicatedCount, externalRequest: false, eventType, reason,
  }
}

async function enviarUrlsSerializado(
  event: IndexNowPublicEvent,
  dependencies: IndexNowDependencies
): Promise<IndexNowSubmissionResult> {
  const config = getIndexNowConfig()
  if (!config.enabled) return resultWithoutRequest(event.eventType, "DISABLED")

  const now = dependencies.now ?? Date.now
  const currentTime = now()
  const eventFingerprint = normalizedFingerprint(event.eventFingerprint)
  const urls = filtrarUrlsDoHost(event.urls)
  const pendingUrls = urls.filter((url) => {
    const lastSuccess = successfulSubmissionByUrl.get(url)
    return !lastSuccess ||
      currentTime - lastSuccess.successAt >= INDEXNOW_DEDUP_WINDOW_MS ||
      lastSuccess.eventType !== event.eventType ||
      lastSuccess.eventFingerprint !== eventFingerprint
  })

  const deduplicatedCount = urls.length - pendingUrls.length
  if (!pendingUrls.length) return resultWithoutRequest(event.eventType, "EMPTY", deduplicatedCount)

  const fetchImpl = dependencies.fetchImpl ?? fetch
  const sleep = dependencies.sleep ?? defaultSleep
  const timeoutMs = dependencies.timeoutMs ?? INDEXNOW_TIMEOUT_MS
  let attempts = 0

  while (attempts < INDEXNOW_MAX_ATTEMPTS) {
    attempts += 1
    try {
      const response = await fetchWithTimeout(fetchImpl, {
        method: "POST",
        headers: { "Content-Type": "application/json; charset=utf-8" },
        body: JSON.stringify({
          host: new URL(config.siteUrl).host,
          key: config.key,
          keyLocation: getIndexNowKeyLocation(),
          urlList: pendingUrls,
        }),
        cache: "no-store",
      }, timeoutMs)

      if (response.status === 200 || response.status === 202) {
        const successAt = now()
        pendingUrls.forEach((url) => successfulSubmissionByUrl.set(url, {
          eventType: event.eventType,
          eventFingerprint,
          successAt,
        }))
      }

      const retryable = response.status === 429 || response.status >= 500
      if (!retryable || attempts >= INDEXNOW_MAX_ATTEMPTS) {
        return {
          ok: response.status === 200 || response.status === 202,
          status: response.status,
          attempts,
          urlCount: pendingUrls.length,
          deduplicatedCount,
          externalRequest: true,
          eventType: event.eventType,
          reason: response.status === 200 || response.status === 202 ? undefined : "HTTP",
        }
      }
    } catch {
      if (attempts >= INDEXNOW_MAX_ATTEMPTS) {
        return {
          ok: false, status: 0, attempts, urlCount: pendingUrls.length,
          deduplicatedCount, externalRequest: true, eventType: event.eventType,
          reason: "NETWORK",
        }
      }
    }
    await sleep(250 * 2 ** (attempts - 1))
  }

  return {
    ok: false, status: 0, attempts, urlCount: pendingUrls.length,
    deduplicatedCount, externalRequest: true, eventType: event.eventType,
    reason: "NETWORK",
  }
}

export function enviarUrlsParaIndexNow(
  event: IndexNowPublicEvent,
  dependencies: IndexNowDependencies = {}
) {
  const task = submissionQueue.then(
    () => enviarUrlsSerializado(event, dependencies),
    () => enviarUrlsSerializado(event, dependencies)
  )
  submissionQueue = task.then(() => undefined, () => undefined)
  return task
}

export function resetIndexNowStateForTests() {
  successfulSubmissionByUrl.clear()
  submissionQueue = Promise.resolve()
}
