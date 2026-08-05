import { adminApiUrl } from '@/lib/api-contract'

export type AdminStoryConfiguracao = {
  configurada: boolean
  ativo: boolean
  custoCreditos: number | null
  duracaoHoras: 24
  versao: number | null
  atualizadoEm: string | null
}

export type AdminStoryConfiguracaoWrite = {
  ativo: boolean
  custoCreditos: number
  versao: number | null
}

function adminUrl(path: string) {
  return adminApiUrl(`/stories${path}`)
}

function antiForgeryCookieName() {
  return ['XSRF', 'TOKEN'].join('-')
}

function antiForgeryHeaderName() {
  return ['X', 'XSRF', 'TOKEN'].join('-')
}

function readCookie(name: string) {
  if (typeof document === 'undefined') return null
  const prefix = `${name}${String.fromCharCode(61)}`
  const pair = document.cookie.split('; ').find((item) => item.startsWith(prefix))
  return pair ? decodeURIComponent(pair.slice(prefix.length)) : null
}

async function ensureAntiForgeryValue() {
  let value = readCookie(antiForgeryCookieName())
  if (value) return value
  await fetch(adminApiUrl('/auth/me'), {
    method: 'GET',
    credentials: 'include',
    cache: 'no-store',
  })
  value = readCookie(antiForgeryCookieName())
  return value
}

async function request<T>(path: string, init: RequestInit = {}) {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrf = await ensureAntiForgeryValue()
    if (csrf) headers.set(antiForgeryHeaderName(), csrf)
  }
  const response = await fetch(adminUrl(path), {
    ...init,
    method,
    headers,
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string } | null
    throw new Error(body?.message || `Não foi possível concluir a operação (${response.status}).`)
  }
  return await response.json() as T
}

export function fetchAdminStoryConfiguracao() {
  return request<AdminStoryConfiguracao>('/configuracao')
}

export function saveAdminStoryConfiguracao(payload: AdminStoryConfiguracaoWrite) {
  return request<AdminStoryConfiguracao>('/configuracao', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}
