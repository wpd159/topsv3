export type ConsentState = {
  necessary: boolean
  functional: boolean
  analytics: boolean
  marketing: boolean
  ts?: number
}

export const CONSENT_EVENT = 'tops:cookie-consent-updated'
const CONSENT_COOKIE = 'cookie_consent'
const DAYS_180 = 60 * 60 * 24 * 180

export const defaultConsent: ConsentState = {
  necessary: true,
  functional: false,
  analytics: false,
  marketing: false,
}

export const allCookiesConsent: ConsentState = {
  necessary: true,
  functional: true,
  analytics: true,
  marketing: true,
}

export function readCookieConsent(): ConsentState | null {
  if (typeof document === 'undefined') return null
  try {
    const cookie = document.cookie.match(/(?:^|;\s*)cookie_consent=([^;]*)/)
    if (!cookie) return null
    const value = JSON.parse(decodeURIComponent(cookie[1])) as Partial<ConsentState> | null
    if (
      !value || value.necessary !== true ||
      typeof value.functional !== 'boolean' ||
      typeof value.analytics !== 'boolean' ||
      typeof value.marketing !== 'boolean' ||
      (value.ts !== undefined && (typeof value.ts !== 'number' || !Number.isFinite(value.ts)))
    ) return null
    return {
      necessary: true,
      functional: value.functional,
      analytics: value.analytics,
      marketing: value.marketing,
      ...(value.ts !== undefined ? { ts: value.ts } : {}),
    }
  } catch {
    return null // Ausência ou preferência inválida nunca representa consentimento.
  }
}

export function persistCookieConsent(choice: ConsentState): ConsentState {
  const next = { ...choice, necessary: true, ts: Date.now() }
  document.cookie = `${CONSENT_COOKIE}=${encodeURIComponent(JSON.stringify(next))}; Path=/; Max-Age=${DAYS_180}; SameSite=Lax`
  const saved = readCookieConsent()
  if (!saved || saved.ts !== next.ts || saved.functional !== next.functional ||
    saved.analytics !== next.analytics || saved.marketing !== next.marketing) {
    throw new Error('COOKIE_CONSENT_NOT_SAVED')
  }
  ;(window as Window & { dataLayer?: unknown[] }).dataLayer?.push({
    event: 'consent_update',
    consent: {
      necessary: next.necessary,
      functional: next.functional,
      analytics: next.analytics,
      marketing: next.marketing,
    },
  })
  window.dispatchEvent(new CustomEvent(CONSENT_EVENT, { detail: next }))
  return next
}
