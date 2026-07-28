'use client'

import { getAdminMutationHeaders } from '@/features/admin-anuncios/api'
import {
  ApiContractError,
  adminApiUrl,
  apiErrorFromResponse,
  normalizeApiError,
  requireArrayPayload,
} from '@/lib/api-contract'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'

export type AdminSugestaoResumo = {
  id: string
  protocolo: string
  titulo: string
  tipo: 'FEATURE' | 'BUG'
  tipoRotulo: string
  status: 'PENDENTE' | 'EM_ANALISE' | 'RESOLVIDO' | 'RECUSADO'
  statusRotulo: string
  usuarioNome: string
  usuarioEmail: string | null
  criadoEm: string
  atualizadoEm: string
}

export type AdminSugestaoHistorico = {
  id: string
  acao: string
  acaoRotulo: string
  status: string | null
  providencia: string | null
  atorNome: string
  criadoEm: string
  requestId: string
}

export type AdminSugestaoDetalhe = {
  sugestao: AdminSugestaoResumo
  descricao: string
  providencia: string | null
  responsavelNome: string | null
  decididoEm: string | null
  historico: AdminSugestaoHistorico[]
}

export type AdminSugestaoPagina = {
  itens: AdminSugestaoResumo[]
  pagina: number
  tamanho: number
  totalElementos: number
  totalPaginas: number
}

export type AdminSugestaoIndicadores = {
  total: number
  novas: number
  emAnalise: number
  resolvidas: number
  recusadas: number
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

export async function listarAdminSugestoes(filters: {
  termo?: string
  status: string
  pagina: number
  tamanho: number
}, signal?: AbortSignal) {
  const query = new URLSearchParams({
    status: filters.status,
    page: String(filters.pagina),
    size: String(filters.tamanho),
  })
  if (filters.termo?.trim()) query.set('termo', filters.termo.trim())
  const payload = await request<AdminSugestaoPagina>(
    `/sugestoes?${query.toString()}`,
    { signal },
  )
  return {
    ...payload,
    itens: requireArrayPayload<AdminSugestaoResumo>(payload.itens),
  }
}

export function buscarIndicadoresSugestoes(signal?: AbortSignal) {
  return request<AdminSugestaoIndicadores>('/sugestoes/indicadores', { signal })
}

export function detalharAdminSugestao(id: string, signal?: AbortSignal) {
  return request<AdminSugestaoDetalhe>(
    `/sugestoes/${encodeURIComponent(id)}`,
    { signal },
  )
}

export function alterarStatusAdminSugestao(
  id: string,
  status: 'EM_ANALISE' | 'RESOLVIDO' | 'RECUSADO',
  providencia: string,
) {
  return request<AdminSugestaoDetalhe>(
    `/sugestoes/${encodeURIComponent(id)}/status`,
    {
      method: 'PATCH',
      body: JSON.stringify({ status, providencia }),
    },
  )
}

export function adminSugestaoError(error: unknown): ApiContractError {
  return normalizeApiError(error)
}
