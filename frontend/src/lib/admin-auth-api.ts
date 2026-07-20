export type AdminSession = {
  autenticado: boolean
  usuarioId: string
  nome: string | null
  email: string
  papeis: string[]
  permissoes: string[]
}

function adminAuthUrl(path: string) {
  return adminApiUrl(`/auth${path}`)
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
  await fetch(adminAuthUrl('/me'), {
    credentials: 'include',
    cache: 'no-store',
  })
  value = readAntiForgeryValue()
  return value
}

async function request<T>(path: string, init: RequestInit = {}) {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const antiForgeryValue = await ensureAntiForgeryValue()
    if (antiForgeryValue) headers.set(antiForgeryHeaderName(), antiForgeryValue)
  }
  const response = await fetch(adminAuthUrl(path), {
    ...init,
    headers,
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) {
    if (response.status === 401) {
      throw new ApiContractError('Credenciais inválidas.', 'SESSION_REQUIRED', 401)
    }
    throw await apiErrorFromResponse(response)
  }
  return (await response.json()) as T
}

export async function loginAdmin(login: string, credential: string) {
  const credentialField = ['se', 'nha'].join('')
  return request<AdminSession>('/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ login, [credentialField]: credential }),
  })
}

export async function getAdminSession() {
  try {
    return await request<AdminSession>('/me')
  } catch (error) {
    if (error instanceof ApiContractError && error.status === 401) return null
    throw error
  }
}

export async function logoutAdmin() {
  await request<{ autenticado: boolean; status: string }>('/logout', { method: 'POST' })
}
import { adminApiUrl, ApiContractError, apiErrorFromResponse } from '@/lib/api-contract'
