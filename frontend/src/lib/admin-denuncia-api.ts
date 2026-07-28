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

export type AdminDenunciaResumo = {
  id: string
  protocolo: string
  anuncioId: string
  anuncioTitulo: string
  anuncioSlug: string
  motivo: string
  motivoRotulo: string
  status: 'PENDENTE' | 'PUNIDA' | 'IGNORADA'
  statusRotulo: string
  denuncianteNome: string
  denuncianteEmail: string | null
  criadoEm: string
  atualizadoEm: string
}

export type AdminDenunciaHistorico = {
  id: string
  acao: string
  acaoRotulo: string
  status: string | null
  providencia: string | null
  atorNome: string
  criadoEm: string
  requestId: string
}

export type AdminDenunciaDetalhe = {
  denuncia: AdminDenunciaResumo
  descricao: string | null
  providencia: string | null
  responsavelNome: string | null
  decididaEm: string | null
  anuncio: {
    id: string
    titulo: string
    slug: string
    status: string
    statusModeracao: string
  }
  historico: AdminDenunciaHistorico[]
}

export type AdminDenunciaPagina = {
  itens: AdminDenunciaResumo[]
  pagina: number
  tamanho: number
  totalElementos: number
  totalPaginas: number
}

export type AdminDenunciaIndicadores = {
  total: number
  pendentes: number
  punidas: number
  ignoradas: number
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

export async function listarAdminDenuncias(filters: {
  termo?: string
  motivo: string
  status: string
  inicio?: string
  fim?: string
  pagina: number
  tamanho: number
}, signal?: AbortSignal) {
  const query = new URLSearchParams({
    motivo: filters.motivo,
    status: filters.status,
    page: String(filters.pagina),
    size: String(filters.tamanho),
  })
  if (filters.termo?.trim()) query.set('termo', filters.termo.trim())
  if (filters.inicio) query.set('inicio', filters.inicio)
  if (filters.fim) query.set('fim', filters.fim)
  const payload = await request<AdminDenunciaPagina>(`/denuncias?${query.toString()}`, { signal })
  return { ...payload, itens: requireArrayPayload<AdminDenunciaResumo>(payload.itens) }
}

export function buscarIndicadoresDenuncias(signal?: AbortSignal) {
  return request<AdminDenunciaIndicadores>('/denuncias/indicadores', { signal })
}

export function detalharAdminDenuncia(id: string, signal?: AbortSignal) {
  return request<AdminDenunciaDetalhe>(`/denuncias/${encodeURIComponent(id)}`, { signal })
}

export function alterarStatusAdminDenuncia(
  id: string,
  status: 'PUNIDA' | 'IGNORADA',
  providencia: string,
) {
  return request<AdminDenunciaDetalhe>(`/denuncias/${encodeURIComponent(id)}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status, providencia }),
  })
}

export function adminDenunciaError(error: unknown): ApiContractError {
  return normalizeApiError(error)
}
