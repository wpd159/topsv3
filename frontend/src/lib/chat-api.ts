'use client'

import {
  ApiContractError,
  apiErrorFromResponse,
  normalizeApiError,
  publicApiUrl,
} from '@/lib/api-contract'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'

export type ChatConversa = {
  id: string
  participanteUsername: string
  ultimaMensagem: string | null
  ultimaMensagemEm: string | null
  naoLidas: number
}

export type ChatMensagem = {
  id: string
  conversaId: string
  remetenteUsername: string
  corpo: string
  enviadoEm: string
  lidoEm: string | null
  minha: boolean
  repetida: boolean
}

export type ChatConversaDetalhe = {
  conversa: ChatConversa
  mensagens: ChatMensagem[]
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

async function chatRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const method = (init.method || 'GET').toUpperCase()
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrfValue = await ensureCsrfToken()
    if (csrfValue) headers.set(csrfHeaderName, csrfValue)
  }

  try {
    const response = await fetch(publicApiUrl(path), {
      ...init,
      headers,
      credentials: 'include',
      cache: 'no-store',
    })
    if (!response.ok) {
      if (response.status === 429) {
        throw new ApiContractError(
          'Muitas mensagens em pouco tempo. Aguarde e tente novamente.',
          'TECHNICAL_FAILURE',
          429,
          true
        )
      }
      if (response.status === 404) {
        throw new ApiContractError(
          'A conversa ou o usuario informado nao foi encontrado.',
          'INVALID_REQUEST',
          404
        )
      }
      throw await apiErrorFromResponse(response)
    }
    return corrigirEstruturaTexto(await response.json()) as T
  } catch (error) {
    throw normalizeApiError(error)
  }
}

export function fetchChatConversas(signal?: AbortSignal) {
  return chatRequest<ChatConversa[]>('/chat/conversas', { signal })
}

export function iniciarChatConversa(username: string) {
  return chatRequest<ChatConversa>('/chat/conversas', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username }),
  })
}

export function iniciarChatConversaPorAnuncio(anuncioId: string | number) {
  return chatRequest<ChatConversa>('/chat/conversas', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ anuncioId: String(anuncioId) }),
  })
}

export function fetchChatConversa(conversaId: string, signal?: AbortSignal) {
  return chatRequest<ChatConversaDetalhe>(
    `/chat/conversas/${encodeURIComponent(conversaId)}/mensagens`,
    { signal }
  )
}

export function enviarChatMensagem(
  conversaId: string,
  corpo: string,
  idempotencyKey: string
) {
  return chatRequest<ChatMensagem>(
    `/chat/conversas/${encodeURIComponent(conversaId)}/mensagens`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify({ corpo }),
    }
  )
}

export function marcarChatConversaComoLida(conversaId: string) {
  return chatRequest<{ total: number }>(
    `/chat/conversas/${encodeURIComponent(conversaId)}/leitura`,
    { method: 'POST' }
  )
}

export function fetchChatNaoLidas(signal?: AbortSignal) {
  return chatRequest<{ total: number }>('/chat/nao-lidas', { signal })
}

export function chatError(error: unknown) {
  return normalizeApiError(error)
}
