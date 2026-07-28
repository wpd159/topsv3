'use client'

import {
  ApiContractError,
  apiErrorFromResponse,
  normalizeApiError,
  publicApiUrl,
  requireArrayPayload,
} from '@/lib/api-contract'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'

export type TicketResumo = {
  id: string
  protocolo: string
  assunto: string
  categoria: string
  categoriaRotulo: string
  status: string
  statusRotulo: string
  criadoEm: string
  atualizadoEm: string
  totalMensagens: number
}

export type TicketMensagem = {
  id: string
  origem: 'USUARIO' | 'STAFF' | 'SISTEMA' | 'IMPORTACAO'
  remetente: string
  corpo: string
  criadoEm: string
  minha: boolean
  repetida: boolean
}

export type TicketDetalhe = {
  ticket: TicketResumo
  mensagens: TicketMensagem[]
}

export type TicketPagina = {
  itens: TicketResumo[]
  pagina: number
  tamanho: number
  totalElementos: number
  totalPaginas: number
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

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrf = await ensureCsrfToken()
    if (csrf) headers.set(csrfHeaderName, csrf)
  }

  try {
    const response = await fetch(publicApiUrl(path), {
      ...init,
      headers,
      credentials: 'include',
      cache: 'no-store',
    })
    if (!response.ok) throw await apiErrorFromResponse(response)
    return corrigirEstruturaTexto(await response.json()) as T
  } catch (error) {
    throw normalizeApiError(error)
  }
}

export async function listarTickets(
  grupo: 'TODOS' | 'ABERTOS' | 'ENCERRADOS',
  pagina = 0,
  signal?: AbortSignal,
) {
  const payload = await request<TicketPagina>(
    `/suporte/tickets?grupo=${grupo}&page=${pagina}&size=20`,
    { signal },
  )
  return { ...payload, itens: requireArrayPayload<TicketResumo>(payload.itens) }
}

export function detalharTicket(ticketId: string, signal?: AbortSignal) {
  return request<TicketDetalhe>(
    `/suporte/tickets/${encodeURIComponent(ticketId)}`,
    { signal },
  )
}

export function criarTicket(payload: {
  assunto: string
  categoria: string
  descricao: string
}, idempotencyKey: string) {
  return request<TicketDetalhe>('/suporte/tickets', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    },
    body: JSON.stringify(payload),
  })
}

export function responderTicket(
  ticketId: string,
  mensagem: string,
  idempotencyKey: string,
) {
  return request<TicketMensagem>(
    `/suporte/tickets/${encodeURIComponent(ticketId)}/mensagens`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify({ mensagem }),
    },
  )
}

export function encerrarTicket(ticketId: string) {
  return request<TicketDetalhe>(
    `/suporte/tickets/${encodeURIComponent(ticketId)}/encerrar`,
    { method: 'POST' },
  )
}

export function suporteError(error: unknown): ApiContractError {
  return normalizeApiError(error)
}

export function novaIdempotencyKey(prefixo: string) {
  return `${prefixo}:${crypto.randomUUID()}`
}
