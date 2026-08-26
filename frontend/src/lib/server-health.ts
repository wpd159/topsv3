import 'server-only'

import { resolveInternalPublicApiBase } from '@/lib/public-server-api'

const DEFAULT_READINESS_TIMEOUT_MS = 1_500
const MIN_READINESS_TIMEOUT_MS = 100
const MAX_READINESS_TIMEOUT_MS = 5_000

type Environment = Record<string, string | undefined>
type FetchImplementation = typeof fetch

type BackendReadinessPayload = {
  status?: unknown
  components?: Record<string, unknown>
}

export function resolveHealthReadinessTimeoutMs(
  environment: Environment = process.env,
) {
  const configured = environment.HEALTH_READINESS_TIMEOUT_MS?.trim()
  if (!configured) return DEFAULT_READINESS_TIMEOUT_MS
  if (!/^\d+$/.test(configured)) return DEFAULT_READINESS_TIMEOUT_MS

  const timeoutMs = Number(configured)
  if (
    !Number.isSafeInteger(timeoutMs) ||
    timeoutMs < MIN_READINESS_TIMEOUT_MS ||
    timeoutMs > MAX_READINESS_TIMEOUT_MS
  ) {
    return DEFAULT_READINESS_TIMEOUT_MS
  }
  return timeoutMs
}

export function resolveInternalBackendReadinessUrl(
  environment: Environment = process.env,
) {
  const internalPublicApi = new URL(resolveInternalPublicApiBase(environment))
  return new URL('/api/health/readiness', internalPublicApi.origin).toString()
}

export async function probeInternalBackendReadiness(
  environment: Environment = process.env,
  fetchImplementation: FetchImplementation = fetch,
) {
  const controller = new AbortController()
  const timeout = setTimeout(
    () => controller.abort(),
    resolveHealthReadinessTimeoutMs(environment),
  )

  try {
    const response = await fetchImplementation(
      resolveInternalBackendReadinessUrl(environment),
      {
        method: 'GET',
        cache: 'no-store',
        redirect: 'error',
        signal: controller.signal,
        headers: { Accept: 'application/json' },
      },
    )
    if (!response.ok) return false

    const payload = (await response.json()) as BackendReadinessPayload
    const components = payload.components
    return payload.status === 'UP'
      && components?.application === 'UP'
      && components.database === 'UP'
      && components.migrations === 'UP'
  } catch {
    return false
  } finally {
    clearTimeout(timeout)
  }
}
