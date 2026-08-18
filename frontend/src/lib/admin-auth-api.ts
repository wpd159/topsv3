export type AdminSession = {
  autenticado: boolean
  usuarioId: string
  nome: string | null
  email: string
  papeis: string[]
  permissoes: string[]
}

export type AdminAccountAction = {
  message: string
}

type AdminCredentialField = 'senhaAtual' | 'novaSenha' | 'confirmarSenha'

export type ChangeAdminPasswordPayload = Record<AdminCredentialField, string>

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

const ADMIN_PASSWORD_FUNCTIONAL_ERRORS = new Set([
  'Senha atual incorreta.',
  'A nova senha não atende aos requisitos de segurança.',
  'As senhas não coincidem.',
  'A nova senha deve ser diferente da senha atual.',
  'Preencha os dados obrigatórios.',
])

async function passwordErrorFromResponse(response: Response) {
  const contractError = await apiErrorFromResponse(response)
  try {
    const body = (await response.json()) as { message?: unknown }
    if (
      typeof body.message === 'string' &&
      ADMIN_PASSWORD_FUNCTIONAL_ERRORS.has(body.message)
    ) {
      return new ApiContractError(
        body.message,
        contractError.kind,
        contractError.status,
        contractError.retryable,
        contractError.requestId,
      )
    }
  } catch {
    // Mantem o erro contratual sanitizado quando o corpo nao for JSON.
  }
  return contractError
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  preservePasswordError = false,
) {
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
    if (preservePasswordError) {
      throw await passwordErrorFromResponse(response)
    }
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

export function changeAdminPassword(
  currentCredential: string,
  nextCredential: string,
  confirmation: string,
) {
  const payload = Object.fromEntries([
    ['senhaAtual', currentCredential],
    ['novaSenha', nextCredential],
    ['confirmarSenha', confirmation],
  ]) as ChangeAdminPasswordPayload
  return request<AdminAccountAction>('/password', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }, true)
}
import { adminApiUrl, ApiContractError, apiErrorFromResponse } from '@/lib/api-contract'
