'use client'

import { getAdminMutationHeaders } from '@/features/admin-anuncios/api'
import {
  ApiContractError,
  adminApiUrl,
  apiErrorFromResponse,
  normalizeApiError,
  publicApiUrl,
  requireArrayPayload,
} from '@/lib/api-contract'

export type AvisoLocal = 'SITE' | 'LOGIN_POPUP' | 'ANUNCIO_RODAPE'
export type AvisoFrequencia = 'SEMPRE' | 'UMA_VEZ' | 'DIARIO'
export type AvisoStatus = 'RASCUNHO' | 'PUBLICADO' | 'ARQUIVADO'

export type AvisoPublico = {
  id: string
  titulo: string
  descricao: string
  localExibicao: AvisoLocal
  frequenciaExibicao: AvisoFrequencia
  permiteDispensar: boolean
  ativoDe?: string | null
  ativoAte?: string | null
  publicadoEm: string
}

export type AdminAviso = Omit<AvisoPublico, 'publicadoEm'> & {
  localExibicaoRotulo: string
  frequenciaExibicaoRotulo: string
  status: AvisoStatus
  situacao: AvisoStatus | 'VIGENTE' | 'AGENDADO' | 'EXPIRADO'
  criadoPorNome: string
  publicadoEm?: string | null
  criadoEm: string
  atualizadoEm: string
  versao: number
}

export type AvisoPagina<T> = {
  itens: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export type AvisoIndicadores = {
  total: number
  rascunhos: number
  vigentes: number
  agendados: number
  expirados: number
  arquivados: number
}

export type AvisoEdicao = {
  titulo: string
  descricao: string
  localExibicao: AvisoLocal
  frequenciaExibicao: AvisoFrequencia
  permiteDispensar: boolean
  ativoDe?: string | null
  ativoAte?: string | null
  versao?: number | null
}

function pagePayload<T>(payload: unknown): AvisoPagina<T> {
  if (!payload || typeof payload !== 'object') {
    throw new ApiContractError('O servico retornou uma pagina incompativel.', 'TECHNICAL_FAILURE', 502, true)
  }
  const page = payload as Partial<AvisoPagina<T>>
  return { ...page, itens: requireArrayPayload<T>(page.itens) } as AvisoPagina<T>
}

async function adminRequest<T>(path: string, init: RequestInit = {}) {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const secure = await getAdminMutationHeaders()
    Object.entries(secure).forEach(([name, value]) => headers.set(name, value))
  }
  try {
    const response = await fetch(adminApiUrl(path), {
      ...init,
      headers,
      credentials: 'include',
      cache: 'no-store',
    })
    if (!response.ok) throw await apiErrorFromResponse(response)
    return (await response.json()) as T
  } catch (error) {
    throw normalizeApiError(error)
  }
}

export async function listarAdminAvisos(filters: {
  termo: string
  status: string
  localExibicao: string
  page: number
  size: number
}, signal?: AbortSignal) {
  const query = new URLSearchParams({
    page: String(filters.page),
    size: String(filters.size),
  })
  if (filters.termo.trim()) query.set('termo', filters.termo.trim())
  if (filters.status !== 'TODOS') query.set('status', filters.status)
  if (filters.localExibicao !== 'TODOS') query.set('localExibicao', filters.localExibicao)
  return pagePayload<AdminAviso>(
    await adminRequest<unknown>(`/avisos?${query.toString()}`, { signal }),
  )
}

export function buscarIndicadoresAvisos(signal?: AbortSignal) {
  return adminRequest<AvisoIndicadores>('/avisos/indicadores', { signal })
}

export function criarAdminAviso(payload: AvisoEdicao, idempotencyKey: string) {
  return adminRequest<AdminAviso>('/avisos', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(payload),
  })
}

export function atualizarAdminAviso(id: string, payload: AvisoEdicao) {
  return adminRequest<AdminAviso>(`/avisos/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function publicarAdminAviso(id: string, versao: number) {
  return acaoAdminAviso(id, 'publicar', versao)
}

export function retirarAdminAviso(id: string, versao: number) {
  return acaoAdminAviso(id, 'retirar', versao)
}

export function arquivarAdminAviso(id: string, versao: number) {
  return acaoAdminAviso(id, 'arquivar', versao)
}

function acaoAdminAviso(id: string, action: string, versao: number) {
  return adminRequest<AdminAviso>(`/avisos/${encodeURIComponent(id)}/${action}`, {
    method: 'POST',
    body: JSON.stringify({ versao }),
  })
}

export async function listarAvisosPublicos(
  localExibicao: AvisoLocal,
  size = 5,
  signal?: AbortSignal,
) {
  const query = new URLSearchParams({
    localExibicao,
    page: '0',
    size: String(size),
  })
  try {
    const response = await fetch(publicApiUrl(`/avisos?${query.toString()}`), {
      cache: 'no-store',
      signal,
    })
    if (!response.ok) throw await apiErrorFromResponse(response)
    return pagePayload<AvisoPublico>(await response.json()).itens
  } catch (error) {
    throw normalizeApiError(error)
  }
}

export function avisoError(error: unknown) {
  return normalizeApiError(error)
}
