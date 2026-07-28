'use client'

import {
  ApiContractError,
  apiErrorFromResponse,
  normalizeApiError,
  publicApiUrl,
} from '@/lib/api-contract'

export type SugestaoCriacao = {
  id: string
  protocolo: string
  status: string
  statusRotulo: string
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

export async function enviarSugestao(input: {
  tipo: 'FEATURE' | 'BUG'
  titulo: string
  descricao: string
  idempotencyKey: string
}) {
  const headers = new Headers({
    'Content-Type': 'application/json',
    'Idempotency-Key': input.idempotencyKey,
  })
  const csrf = await ensureCsrfToken()
  if (csrf) headers.set(csrfHeaderName, csrf)
  try {
    const response = await fetch(publicApiUrl('/sugestoes'), {
      method: 'POST',
      headers,
      credentials: 'include',
      cache: 'no-store',
      body: JSON.stringify({
        tipo: input.tipo,
        titulo: input.titulo,
        descricao: input.descricao,
      }),
    })
    if (!response.ok) throw await apiErrorFromResponse(response)
    return await response.json() as SugestaoCriacao
  } catch (error) {
    throw normalizeApiError(error)
  }
}

export function novaSugestaoIdempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `sugestao-${crypto.randomUUID()}`
  }
  return `sugestao-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export function sugestaoError(error: unknown): ApiContractError {
  return normalizeApiError(error)
}
