import "server-only"
import { createHash } from "node:crypto"

const INDEXNOW_ENDPOINT = "https://api.indexnow.org/indexnow"
const INDEXNOW_CANONICAL_SITE_URL = "https://topsdojob.com"
const INDEXNOW_DEDUP_WINDOW_MS = 5 * 60 * 1000
const INDEXNOW_MAX_URLS_PER_BATCH = 20
const INDEXNOW_MAX_ATTEMPTS = 3
const INDEXNOW_TIMEOUT_MS = 4_000
// Mantem o teto anterior de tres timeouts + backoffs para a execucao inteira.
const INDEXNOW_EXECUTION_BUDGET_MS = INDEXNOW_MAX_ATTEMPTS * INDEXNOW_TIMEOUT_MS + 750
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
  acceptedUrlCount: number
  failedUrlCount: number
  unattemptedUrlCount: number
  deduplicatedCount: number
  externalRequest: boolean
  eventType: IndexNowEventType
  reason?: "DISABLED" | "EMPTY" | "HTTP" | "NETWORK" | "BUDGET"
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
  return createHash("sha256").update(value).digest("hex")
}

function retryAfterMs(value: string | null, now: number) {
  if (!value) return 0
  const trimmed = value.trim()
  if (/^\d+$/.test(trimmed)) return Number(trimmed) * 1_000
  // HTTP-date (incluindo os formatos legados), nunca numero decimal ou sinalizado.
  if (!/^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)/.test(trimmed)) return 0
  const timestamp = Date.parse(trimmed)
  return Number.isFinite(timestamp) ? Math.max(0, timestamp - now) : 0
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
    acceptedUrlCount: 0, failedUrlCount: 0, unattemptedUrlCount: 0,
    deduplicatedCount, externalRequest: false, eventType, reason,
  }
}

async function enviarLote(
  urls: string[],
  config: ReturnType<typeof getIndexNowConfig>,
  deadline: number,
  dependencies: IndexNowDependencies
): Promise<Pick<IndexNowSubmissionResult, "ok" | "status" | "attempts" | "reason">> {
  const now = dependencies.now ?? Date.now
  const fetchImpl = dependencies.fetchImpl ?? fetch
  const sleep = dependencies.sleep ?? defaultSleep
  const timeoutMs = dependencies.timeoutMs ?? INDEXNOW_TIMEOUT_MS
  let attempts = 0
  let status = 0
  let reason: "HTTP" | "NETWORK" = "NETWORK"

  while (attempts < INDEXNOW_MAX_ATTEMPTS) {
    const remainingMs = deadline - now()
    if (remainingMs <= 0) return { ok: false, status, attempts, reason: "BUDGET" }
    attempts += 1
    let delayMs = 250 * 2 ** (attempts - 1)
    try {
      const response = await fetchWithTimeout(fetchImpl, {
        method: "POST",
        headers: { "Content-Type": "application/json; charset=utf-8" },
        body: JSON.stringify({
          host: new URL(config.siteUrl).host,
          key: config.key,
          keyLocation: getIndexNowKeyLocation(),
          urlList: urls,
        }),
        cache: "no-store",
      }, Math.min(timeoutMs, remainingMs))

      status = response.status
      if (response.status === 200 || response.status === 202) {
        return { ok: true, status, attempts }
      }

      reason = "HTTP"
      const retryable = response.status === 429 || response.status >= 500
      if (!retryable || attempts >= INDEXNOW_MAX_ATTEMPTS) {
        return { ok: false, status, attempts, reason }
      }
      delayMs = Math.max(delayMs, retryAfterMs(response.headers.get("Retry-After"), now()))
    } catch {
      status = 0
      reason = "NETWORK"
      if (attempts >= INDEXNOW_MAX_ATTEMPTS) {
        return { ok: false, status, attempts, reason }
      }
    }
    if (delayMs >= deadline - now()) return { ok: false, status, attempts, reason: "BUDGET" }
    await sleep(delayMs)
  }
  return { ok: false, status, attempts, reason }
}

async function enviarUrlsSerializado(
  event: IndexNowPublicEvent,
  dependencies: IndexNowDependencies
): Promise<IndexNowSubmissionResult> {
  const config = getIndexNowConfig()
  if (!config.enabled) return resultWithoutRequest(event.eventType, "DISABLED")

  const now = dependencies.now ?? Date.now
  const currentTime = now()
  const deadline = currentTime + INDEXNOW_EXECUTION_BUDGET_MS
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

  let attempts = 0
  let acceptedUrlCount = 0
  let status = 0
  for (let offset = 0; offset < pendingUrls.length; offset += INDEXNOW_MAX_URLS_PER_BATCH) {
    const batch = pendingUrls.slice(offset, offset + INDEXNOW_MAX_URLS_PER_BATCH)
    const result = await enviarLote(batch, config, deadline, dependencies)
    attempts += result.attempts
    status = result.status
    if (!result.ok) {
      const failedUrlCount = result.attempts ? batch.length : 0
      // Nao avanca para outro lote apos recusa, inclusive durante Retry-After.
      return {
        ...result, attempts, urlCount: pendingUrls.length, acceptedUrlCount, failedUrlCount,
        unattemptedUrlCount: pendingUrls.length - acceptedUrlCount - failedUrlCount,
        deduplicatedCount, externalRequest: attempts > 0, eventType: event.eventType,
      }
    }
    const successAt = now()
    batch.forEach((url) => successfulSubmissionByUrl.set(url, {
      eventType: event.eventType, eventFingerprint, successAt,
    }))
    acceptedUrlCount += batch.length
  }
  return {
    ok: true, status, attempts, urlCount: pendingUrls.length, acceptedUrlCount,
    failedUrlCount: 0, unattemptedUrlCount: 0,
    deduplicatedCount, externalRequest: true, eventType: event.eventType,
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
