"use client"

export const AGE_GATE_COOKIE_NAME = "age_gate_accepted"
export const AGE_GATE_TTL_DAYS = 7
export const AGE_GATE_STORAGE_KEY = "age_gate_accepted_until"
const AGE_GATE_TTL_MS = AGE_GATE_TTL_DAYS * 24 * 60 * 60 * 1000
const LEGACY_VALUE = "yes"

export type AgeGateStatus = {
  accepted: boolean
  expiresAt: number | null
}

function cookieDomainAttribute(): string {
  if (typeof window === "undefined") return ""
  const host = window.location.hostname
  if (host === "localhost" || host === "127.0.0.1") return ""
  if (host.endsWith("topsdojob.com")) return "; Domain=.topsdojob.com"
  return ""
}

function secureAttribute(): string {
  if (typeof window === "undefined") return ""
  return window.location.protocol === "https:" ? "; Secure" : ""
}

function readCookie(name: string): string | null {
  if (typeof document === "undefined") return null
  const match = document.cookie.match(new RegExp(`(?:^|; )${name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

function readStorageExpiresAt(): number | null {
  if (typeof window === "undefined") return null
  try {
    const raw = window.localStorage.getItem(AGE_GATE_STORAGE_KEY)
    if (!raw) return null
    const expiresAt = Number(raw)
    if (!Number.isFinite(expiresAt) || expiresAt <= 0) {
      window.localStorage.removeItem(AGE_GATE_STORAGE_KEY)
      return null
    }
    if (Date.now() >= expiresAt) {
      window.localStorage.removeItem(AGE_GATE_STORAGE_KEY)
      return null
    }
    return expiresAt
  } catch {
    return null
  }
}

function persistStorageExpiresAt(expiresAt: number) {
  if (typeof window === "undefined") return
  try {
    window.localStorage.setItem(AGE_GATE_STORAGE_KEY, String(expiresAt))
  } catch {
    // noop
  }
}

function clearStorageExpiresAt() {
  if (typeof window === "undefined") return
  try {
    window.localStorage.removeItem(AGE_GATE_STORAGE_KEY)
  } catch {
    // noop
  }
}

export function parseAgeGateExpiresAt(raw: string | null | undefined): number | null {
  if (!raw) return null
  const value = raw.trim()
  if (!value) return null
  if (value.toLowerCase() === LEGACY_VALUE) {
    return Date.now() + AGE_GATE_TTL_MS
  }
  if (!value.startsWith("v1.")) return null
  const expires = Number(value.slice(3))
  if (!Number.isFinite(expires) || expires <= 0) return null
  return expires
}

export function buildAgeGateCookieValue(expiresAt: number) {
  return `v1.${expiresAt}`
}

export function persistAgeGateClient(expiresAt: number) {
  if (typeof document === "undefined") return
  const maxAgeSeconds = Math.max(1, Math.floor((expiresAt - Date.now()) / 1000))
  const value = buildAgeGateCookieValue(expiresAt)
  document.cookie = [
    `${AGE_GATE_COOKIE_NAME}=${encodeURIComponent(value)}`,
    `Max-Age=${maxAgeSeconds}`,
    "Path=/",
    "SameSite=Lax",
    secureAttribute(),
    cookieDomainAttribute(),
  ]
    .filter(Boolean)
    .join("; ")
  persistStorageExpiresAt(expiresAt)
}

export function clearAgeGateClient() {
  if (typeof document === "undefined") return
  document.cookie = [
    `${AGE_GATE_COOKIE_NAME}=`,
    "Max-Age=0",
    "Path=/",
    "SameSite=Lax",
    secureAttribute(),
    cookieDomainAttribute(),
  ]
    .filter(Boolean)
    .join("; ")
  clearStorageExpiresAt()
}

export function readAgeGateClientStatus(): AgeGateStatus {
  const cookieExpiresAt = parseAgeGateExpiresAt(readCookie(AGE_GATE_COOKIE_NAME))
  const storageExpiresAt = readStorageExpiresAt()
  const expiresAt = Math.max(cookieExpiresAt ?? 0, storageExpiresAt ?? 0)
  if (!expiresAt) {
    return { accepted: false, expiresAt: null }
  }
  if (Date.now() >= expiresAt) {
    clearAgeGateClient()
    return { accepted: false, expiresAt: null }
  }
  return { accepted: true, expiresAt }
}

export async function fetchAgeGateStatus(): Promise<AgeGateStatus> {
  const apiBase = (process.env.NEXT_PUBLIC_API_URL || "").replace(/\/$/, "")
  if (!apiBase) return { accepted: false, expiresAt: null }

  try {
    const res = await fetch(`${apiBase}/compliance/age-gate/status`, {
      credentials: "include",
      cache: "no-store",
    })
    if (!res.ok) return { accepted: false, expiresAt: null }
    const data = (await res.json()) as { accepted?: boolean; expiresAt?: number | null }
    if (!data?.accepted || !data.expiresAt) {
      return { accepted: false, expiresAt: null }
    }
    if (Date.now() >= data.expiresAt) {
      clearAgeGateClient()
      return { accepted: false, expiresAt: null }
    }
    persistAgeGateClient(data.expiresAt)
    return { accepted: true, expiresAt: data.expiresAt }
  } catch {
    return { accepted: false, expiresAt: null }
  }
}

export async function acceptAgeGate(originPath?: string): Promise<AgeGateStatus> {
  const apiBase = (process.env.NEXT_PUBLIC_API_URL || "").replace(/\/$/, "")
  if (!apiBase) {
    const expiresAt = Date.now() + AGE_GATE_TTL_MS
    persistAgeGateClient(expiresAt)
    return { accepted: true, expiresAt }
  }

  const query = originPath ? `?originPath=${encodeURIComponent(originPath)}` : ""
  const res = await fetch(`${apiBase}/compliance/age-gate/accept${query}`, {
    method: "POST",
    credentials: "include",
    cache: "no-store",
  })

  if (!res.ok) {
    throw new Error("Falha ao registrar aceite do aviso de idade.")
  }

  const data = (await res.json()) as { accepted?: boolean; expiresAt?: number | null }
  const expiresAt =
    data?.expiresAt && Number.isFinite(data.expiresAt)
      ? Number(data.expiresAt)
      : Date.now() + AGE_GATE_TTL_MS

  persistAgeGateClient(expiresAt)
  return { accepted: true, expiresAt }
}
