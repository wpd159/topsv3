'use client'

import {
  ApiContractError,
  adminApiUrl,
  apiErrorFromResponse,
  normalizeApiError,
  requireArrayPayload,
} from '@/lib/api-contract'
import { getAdminMutationHeaders } from '@/features/admin-anuncios/api'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'
import type { TicketMensagem } from '@/lib/suporte-api'

export type AdminTicketResumo = {
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
  usuarioId: string
  usuarioNome: string
  usuarioEmail: string
  mensagemInicial: string
}

export type AdminTicketDetalhe = {
  ticket: AdminTicketResumo
  mensagens: TicketMensagem[]
  responsavelId: string | null
  responsavelNome: string | null
  encerradoEm: string | null
}

export type AdminTicketPagina = {
  itens: AdminTicketResumo[]
  pagina: number
  tamanho: number
  totalElementos: number
  totalPaginas: number
}

export type AdminTicketIndicadores = {
  total: number
  abertos: number
  emAtendimento: number
  aguardandoUsuario: number
  resolvidos: number
  encerrados: number
  pendentesEquipe: number
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const mutationHeaders = await getAdminMutationHeaders()
    Object.entries(mutationHeaders).forEach(([name, value]) => headers.set(name, value))
  }

  try {
    const response = await fetch(adminApiUrl(path), {
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

export async function listarAdminTickets(filters: {
  termo?: string
  categoria: string
  status: string
  ordenacao: string
  pagina: number
  tamanho: number
}, signal?: AbortSignal) {
  const query = new URLSearchParams({
    categoria: filters.categoria,
    status: filters.status,
    ordenacao: filters.ordenacao,
    page: String(filters.pagina),
    size: String(filters.tamanho),
  })
  if (filters.termo?.trim()) query.set('termo', filters.termo.trim())
  const payload = await request<AdminTicketPagina>(`/tickets?${query.toString()}`, { signal })
  return { ...payload, itens: requireArrayPayload<AdminTicketResumo>(payload.itens) }
}

export function buscarIndicadoresTickets(signal?: AbortSignal) {
  return request<AdminTicketIndicadores>('/tickets/indicadores', { signal })
}

export function detalharAdminTicket(ticketId: string, signal?: AbortSignal) {
  return request<AdminTicketDetalhe>(`/tickets/${encodeURIComponent(ticketId)}`, { signal })
}

export function responderAdminTicket(
  ticketId: string,
  mensagem: string,
  idempotencyKey: string,
) {
  return request<TicketMensagem>(
    `/tickets/${encodeURIComponent(ticketId)}/mensagens`,
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

export function alterarStatusAdminTicket(ticketId: string, status: string) {
  return request<AdminTicketDetalhe>(
    `/tickets/${encodeURIComponent(ticketId)}/status`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status }),
    },
  )
}

export function adminSuporteError(error: unknown): ApiContractError {
  return normalizeApiError(error)
}
