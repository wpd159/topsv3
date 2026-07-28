'use client'

import { getAdminMutationHeaders } from '@/features/admin-anuncios/api'
import {
  ApiContractError,
  adminApiUrl,
  apiErrorFromResponse,
  normalizeApiError,
  requireArrayPayload,
} from '@/lib/api-contract'
import type { FaqPublica } from '@/lib/faq-public-api'

export type AdminFaq = Omit<FaqPublica, 'status' | 'publicadoEm'> & {
  status: 'RASCUNHO' | 'PUBLICADO' | 'ARQUIVADO'
  publicadoEm?: string | null
}

export type AdminFaqEdicao = {
  pergunta: string
  resposta: string
  categoria: AdminFaq['categoria']
  ordem: number
  versao?: number | null
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
    return (await response.json()) as T
  } catch (error) {
    throw normalizeApiError(error)
  }
}

export async function listarAdminFaqs(filters: {
  termo: string
  status: string
  categoria: string
}, signal?: AbortSignal) {
  const query = new URLSearchParams()
  if (filters.termo.trim()) query.set('termo', filters.termo.trim())
  if (filters.status !== 'TODOS') query.set('status', filters.status)
  if (filters.categoria !== 'TODAS') query.set('categoria', filters.categoria)
  const suffix = query.size ? `?${query.toString()}` : ''
  return requireArrayPayload<AdminFaq>(
    await request<unknown>(`/faqs${suffix}`, { signal }),
  )
}

export function criarAdminFaq(payload: AdminFaqEdicao) {
  return request<AdminFaq>('/faqs', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function atualizarAdminFaq(id: string, payload: AdminFaqEdicao) {
  return request<AdminFaq>(`/faqs/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function publicarAdminFaq(id: string, versao: number) {
  return acao(id, 'publicar', versao)
}

export function retirarAdminFaq(id: string, versao: number) {
  return acao(id, 'retirar', versao)
}

export function arquivarAdminFaq(id: string, versao: number) {
  return acao(id, 'arquivar', versao)
}

export function reordenarAdminFaq(id: string, ordem: number, versao: number) {
  return request<AdminFaq>(`/faqs/${encodeURIComponent(id)}/ordem`, {
    method: 'PATCH',
    body: JSON.stringify({ ordem, versao }),
  })
}

function acao(id: string, nome: string, versao: number) {
  return request<AdminFaq>(`/faqs/${encodeURIComponent(id)}/${nome}`, {
    method: 'POST',
    body: JSON.stringify({ versao }),
  })
}

export function adminFaqError(error: unknown): ApiContractError {
  return normalizeApiError(error)
}
