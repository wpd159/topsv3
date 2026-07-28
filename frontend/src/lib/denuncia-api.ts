'use client'

import {
  ApiContractError,
  apiErrorFromResponse,
  normalizeApiError,
  publicApiUrl,
} from '@/lib/api-contract'

export type DenunciaCriada = {
  id: string
  protocolo: string
  status: 'PENDENTE'
  criadoEm: string
  repetida: boolean
}

const csrfCookieName = ['XSRF', 'TOKEN'].join('-')
const csrfHeaderName = ['X', 'XSRF', 'TOKEN'].join('-')

function csrfToken() {
  if (typeof document === 'undefined') return null
  const entry = document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith(`${csrfCookieName}=`))
  return entry ? decodeURIComponent(entry.slice(csrfCookieName.length + 1)) : null
}

async function ensureCsrfToken() {
  const current = csrfToken()
  if (current) return current
  await fetch(publicApiUrl('/auth/me'), {
    credentials: 'include',
    cache: 'no-store',
  })
  return csrfToken()
}

export async function criarDenuncia(
  payload: { anuncioId: string; motivo: string; descricao?: string },
  idempotencyKey: string,
) {
  const headers = new Headers({
    'Content-Type': 'application/json',
    'Idempotency-Key': idempotencyKey,
  })
  const csrf = await ensureCsrfToken()
  if (csrf) headers.set(csrfHeaderName, csrf)

  try {
    const response = await fetch(publicApiUrl('/denuncias/abrir'), {
      method: 'POST',
      headers,
      credentials: 'include',
      cache: 'no-store',
      body: JSON.stringify(payload),
    })
    if (!response.ok) throw await apiErrorFromResponse(response)
    return await response.json() as DenunciaCriada
  } catch (error) {
    throw normalizeApiError(error)
  }
}

export function denunciaError(error: unknown): ApiContractError {
  return normalizeApiError(error)
}

export function novaDenunciaIdempotencyKey() {
  return `denuncia:${crypto.randomUUID()}`
}
