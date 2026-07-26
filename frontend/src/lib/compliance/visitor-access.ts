"use client"

import { apiErrorFromResponse, publicApiUrl } from '@/lib/api-contract'

export type StatusVisitante = {
  verified: boolean
  level?: string | null
  expiresAt?: string | null
  explicitVerified?: boolean
  explicitLevel?: string | null
  explicitExpiresAt?: string | null
  riskScore?: number | null
  decision?: string | null
}

/**
 * Verificação etária suficiente para tratar a sessão como liberada no UI (espelha cookie/token do backend).
 *
 * Não usa isUserVerified()/localStorage isolado: pode estar dessincronizado dos cookies httpOnly; nesse caso
 * o frontend trocaria preview por /stream, a mídia falharia e o card poderia marcar o src como inválido.
 */
export function liberacaoMidiaRestritaVisitante(params: {
  viewerAuthorized: boolean
  requiresStrongVerification: boolean
  statusVisitante: StatusVisitante | null
}): boolean {
  const { viewerAuthorized, requiresStrongVerification, statusVisitante } = params
  if (viewerAuthorized) return true

  if (statusVisitante?.explicitVerified) return true
  if (
    statusVisitante?.explicitLevel === "STRONG" ||
    statusVisitante?.explicitLevel === "REINFORCED"
  ) {
    return true
  }
  if (statusVisitante?.level === "STRONG" || statusVisitante?.level === "REINFORCED") {
    return true
  }
  if (!requiresStrongVerification && statusVisitante?.verified) {
    return true
  }
  return false
}

let cacheStatus: StatusVisitante | null = null
let cacheExpiraEm = 0
let requisicaoPendente: Promise<StatusVisitante> | null = null

const TTL_CACHE_MS = 30_000
export const AGE_VERIFICATION_CHANGED_EVENT = "topsv3:age-verification-changed"
const VERIFIED_18_UNTIL_STORAGE_NAME = ["verified", "18", "until"].join("_")
const EXPLICIT_VERIFIED_UNTIL_STORAGE_KEY = "explicit_verified_until"
const MAX_CLIENT_MIRROR_MS = 7 * 24 * 60 * 60 * 1000

declare global {
  interface Window {
    __USER_VERIFIED_18_UNTIL__?: number
  }
}

function parseExpiresAtMillis(value?: string | null): number | null {
  if (!value) return null
  const parsed = Date.parse(value)
  if (!Number.isFinite(parsed) || parsed <= 0) return null
  return parsed
}

function toIsoString(value?: number | null): string | null {
  if (typeof value !== "number" || !Number.isFinite(value) || value <= 0) return null
  return new Date(value).toISOString()
}

function readStoredExpiry(key: string): number | null {
  if (typeof window === "undefined") return null

  try {
    const value = window.localStorage.getItem(key)
    if (!value) return null

    const expires = Number(value)
    if (!Number.isFinite(expires) || expires <= 0) {
      window.localStorage.removeItem(key)
      return null
    }

    if (Date.now() >= expires) {
      window.localStorage.removeItem(key)
      return null
    }

    return expires
  } catch {
    return null
  }
}

function writeStoredExpiry(key: string, expiresAt?: string | null): number | null {
  if (typeof window === "undefined") return null

  const backendExpiry = parseExpiresAtMillis(expiresAt)
  const capped = Date.now() + MAX_CLIENT_MIRROR_MS
  const expires = backendExpiry ? Math.min(backendExpiry, capped) : capped

  try {
    window.localStorage.setItem(key, String(expires))
  } catch {
    // sem bloquear a navegação
  }

  return expires
}

function clearStoredExpiry(key: string) {
  if (typeof window === "undefined") return
  try {
    window.localStorage.removeItem(key)
  } catch {
    // sem bloquear a navegação
  }
}

export function readMirroredVisitorStatus(): StatusVisitante | null {
  if (typeof window === "undefined") return null

  const generalExpires = readStoredExpiry(VERIFIED_18_UNTIL_STORAGE_NAME)
  const explicitExpires = readStoredExpiry(EXPLICIT_VERIFIED_UNTIL_STORAGE_KEY)

  if (!generalExpires && !explicitExpires) {
    return null
  }

  const effectiveGeneralExpires = generalExpires ?? explicitExpires

  return {
    verified: Boolean(effectiveGeneralExpires),
    expiresAt: toIsoString(effectiveGeneralExpires),
    explicitVerified: Boolean(explicitExpires),
    explicitExpiresAt: toIsoString(explicitExpires),
  }
}

export function isUserVerified(): boolean {
  if (typeof window === "undefined") return false

  const memo = window.__USER_VERIFIED_18_UNTIL__
  if (typeof memo === "number" && Number.isFinite(memo)) {
    if (Date.now() >= memo) {
      clearVerified()
      clearExplicitVerified()
      return false
    }
    return true
  }

  const mirrored = readMirroredVisitorStatus()
  if (!mirrored?.verified) {
    return false
  }

  const expires = parseExpiresAtMillis(mirrored.expiresAt)
  if (!expires) {
    clearVerified()
    clearExplicitVerified()
    return false
  }

  window.__USER_VERIFIED_18_UNTIL__ = expires
  return true
}

/** Espelho local opcional; cookies httpOnly do backend continuam sendo a fonte de verdade. */
export function setVerified(expiresAt?: string | null, explicitExpiresAt?: string | null) {
  if (typeof window === "undefined") return

  const generalMirrorExpires = expiresAt ?? explicitExpiresAt ?? null
  const expires = writeStoredExpiry(VERIFIED_18_UNTIL_STORAGE_NAME, generalMirrorExpires)
  if (typeof expires === "number" && Number.isFinite(expires)) {
    window.__USER_VERIFIED_18_UNTIL__ = expires
  }

  if (explicitExpiresAt) {
    writeStoredExpiry(EXPLICIT_VERIFIED_UNTIL_STORAGE_KEY, explicitExpiresAt)
  } else {
    clearExplicitVerified()
  }

  window.dispatchEvent(new CustomEvent(AGE_VERIFICATION_CHANGED_EVENT))
}

export function clearVerified() {
  if (typeof window === "undefined") return
  window.__USER_VERIFIED_18_UNTIL__ = undefined
  clearStoredExpiry(VERIFIED_18_UNTIL_STORAGE_NAME)
}

export function clearExplicitVerified() {
  if (typeof window === "undefined") return
  clearStoredExpiry(EXPLICIT_VERIFIED_UNTIL_STORAGE_KEY)
}

// Backwards compatibility for older imports (to be removed gradually).
export function isUsuarioVerificado18() {
  return isUserVerified()
}

export function limparCacheStatusVisitante() {
  cacheStatus = null
  cacheExpiraEm = 0
  requisicaoPendente = null
}

function persistStatusMirror(status: StatusVisitante) {
  if (status.verified || status.explicitVerified) {
    setVerified(
      status.expiresAt ?? status.explicitExpiresAt ?? null,
      status.explicitVerified ? status.explicitExpiresAt ?? null : null
    )
  } else {
    clearVerified()
  }

  if (!status.explicitVerified) {
    clearExplicitVerified()
  }
}

function refreshStatusFromApi(): Promise<StatusVisitante> {
  const pending = fetch(publicApiUrl('/idade/status'), {
    credentials: "include",
    cache: "no-store",
  })
    .then(async (res) => {
      if (!res.ok) {
        throw await apiErrorFromResponse(res)
      }

      const response = (await res.json()) as {
        confirmada: boolean
        expiraEm?: string | null
      }
      const data: StatusVisitante = {
        verified: response.confirmada,
        expiresAt: response.expiraEm ?? null,
        explicitVerified: response.confirmada,
        explicitExpiresAt: response.expiraEm ?? null,
      }
      cacheStatus = data
      cacheExpiraEm = Date.now() + TTL_CACHE_MS
      persistStatusMirror(data)
      return data
    })
    .finally(() => {
      if (requisicaoPendente === pending) {
        requisicaoPendente = null
      }
    })

  requisicaoPendente = pending
  return pending
}

export async function obterStatusVisitante(force = false): Promise<StatusVisitante> {
  const agora = Date.now()

  if (!force && cacheStatus && cacheExpiraEm > agora) {
    persistStatusMirror(cacheStatus)
    return cacheStatus
  }

  if (!force && requisicaoPendente) {
    return requisicaoPendente
  }

  if (force) {
    return refreshStatusFromApi()
  }

  // O espelho local serve apenas para renderizacao; nunca autoriza sem validar o cookie HttpOnly.
  return refreshStatusFromApi()

  /*
        // Mantém o espelho local durante falhas transitórias do bootstrap.
  */
}
