import {
  adminApiUrl,
  apiErrorFromResponse,
  requireArrayPayload,
} from '@/lib/api-contract'
import type { SiteContentKey } from '@/lib/site-content'

type AdminSiteContentPayload = {
  contentKey: SiteContentKey
  titulo: string | null
  corpo: string | null
  contentVersion: number | null
  contentHash: string | null
  updatedAt: string | null
}

function antiForgeryCookieName() {
  return ['XSRF', 'TOKEN'].join('-')
}

function antiForgeryHeaderName() {
  return ['X', 'XSRF', 'TOKEN'].join('-')
}

function readAntiForgeryValue() {
  if (typeof document === 'undefined') return null
  const name = antiForgeryCookieName()
  const entry = document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith(`${name}${String.fromCharCode(61)}`))
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : null
}

async function ensureAntiForgeryValue() {
  let value = readAntiForgeryValue()
  if (value) return value
  await fetch(adminApiUrl('/auth/me'), {
    credentials: 'include',
    cache: 'no-store',
  })
  value = readAntiForgeryValue()
  return value
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('accept', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    headers.set('content-type', 'application/json')
    const antiForgeryValue = await ensureAntiForgeryValue()
    if (antiForgeryValue) {
      headers.set(antiForgeryHeaderName(), antiForgeryValue)
    }
  }

  const response = await fetch(adminApiUrl(`/conteudos-site${path}`), {
    ...init,
    headers,
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) {
    throw await apiErrorFromResponse(response)
  }
  return (await response.json()) as T
}

export async function listarConteudosSiteAdmin() {
  const payload = await request<unknown>('')
  return requireArrayPayload<AdminSiteContentPayload>(payload)
}

export function salvarConteudoSiteAdmin(
  contentKey: SiteContentKey,
  input: { titulo: string; corpo: string },
) {
  return request<AdminSiteContentPayload>(`/${encodeURIComponent(contentKey)}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}
