import {
  ApiContractError,
  adminApiUrl,
  apiErrorFromResponse,
  normalizeApiError,
  requireArrayPayload,
} from '@/lib/api-contract'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'

import type {
  AdminAdDetail,
  AdminAdFilters,
  AdminAdListItem,
  AdminMediaItem,
  AdminMediaPreview,
  AdminModerationActionResponse,
  AdminModerationHistoryItem,
  AdminPage,
} from './types'

function csrfCookieName() {
  return ['XSRF', 'TOKEN'].join('-')
}

function csrfHeaderName() {
  return ['X', 'XSRF', 'TOKEN'].join('-')
}

function readCsrfValue() {
  if (typeof document === 'undefined') return null
  const name = csrfCookieName()
  const entry = document.cookie.split('; ').find((item) => item.startsWith(`${name}=`))
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : null
}

async function csrfHeaders() {
  let value = readCsrfValue()
  if (!value) {
    await fetch(adminApiUrl('/auth/me'), { credentials: 'include', cache: 'no-store' })
    value = readCsrfValue()
  }
  if (!value) {
    throw new ApiContractError('Nao foi possivel validar a seguranca da sessao.', 'ACCESS_DENIED', 403)
  }
  return {
    'Content-Type': 'application/json',
    [csrfHeaderName()]: value,
  }
}

async function readJson<T>(response: Response): Promise<T> {
  if (!response.ok) throw await apiErrorFromResponse(response)
  try {
    return corrigirEstruturaTexto(await response.json()) as T
  } catch {
    throw new ApiContractError('O servico retornou uma resposta incompativel.', 'TECHNICAL_FAILURE', 502, true)
  }
}

function pagePayload<T>(payload: unknown): AdminPage<T> {
  if (!payload || typeof payload !== 'object') {
    throw new ApiContractError('O servico retornou uma pagina incompativel.', 'TECHNICAL_FAILURE', 502, true)
  }
  const page = payload as Partial<AdminPage<T>>
  return { ...page, itens: requireArrayPayload<T>(page.itens) } as AdminPage<T>
}

async function request<T>(path: string, init: RequestInit = {}) {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const secureHeaders = await csrfHeaders()
    Object.entries(secureHeaders).forEach(([name, value]) => headers.set(name, value))
  }
  try {
    const response = await fetch(adminApiUrl(path), {
      ...init,
      headers,
      credentials: 'include',
      cache: 'no-store',
    })
    return await readJson<T>(response)
  } catch (error) {
    throw normalizeApiError(error)
  }
}

export async function listAdminAds(filters: AdminAdFilters) {
  const query = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })
  Object.entries(filters).forEach(([name, value]) => {
    if (name === 'page' || name === 'size' || value == null || String(value).trim() === '') return
    query.set(name, String(value).trim())
  })
  return pagePayload<AdminAdListItem>(await request(`/anuncios?${query.toString()}`))
}

export function getAdminAd(id: string) {
  return request<AdminAdDetail>(`/anuncios/${encodeURIComponent(id)}`)
}

export async function listAdminAdMedia(id: string) {
  const payload = await request<unknown>(`/anuncios/${encodeURIComponent(id)}/midias?page=0&size=50`)
  return pagePayload<AdminMediaItem>(payload)
}

export async function listAdminAdHistory(id: string) {
  return requireArrayPayload<AdminModerationHistoryItem>(
    await request(`/anuncios/${encodeURIComponent(id)}/historico-moderacao`)
  )
}

export function getAdminMediaPreview(id: string) {
  return request<AdminMediaPreview>(`/midias/${encodeURIComponent(id)}/preview`)
}

export function submitAdminReview(id: string, motivo: string) {
  return request<AdminModerationActionResponse>(`/anuncios/${encodeURIComponent(id)}/remeter-revisao`, {
    method: 'POST',
    body: JSON.stringify({ motivo: motivo.trim() }),
  })
}

export function decideAdminReview(
  reviewId: string,
  decisao: 'APROVAR' | 'REPROVAR' | 'SOLICITAR_AJUSTE',
  motivo?: string,
) {
  return request<AdminModerationActionResponse>(
    `/moderacao/revisoes/${encodeURIComponent(reviewId)}/decidir`,
    {
      method: 'POST',
      body: JSON.stringify({ decisao, motivo: motivo?.trim() || undefined }),
    }
  )
}

export function decideAdminMedia(
  mediaId: string,
  decisao: 'APROVAR' | 'REPROVAR',
  visibilidadeMidia?: 'LIVRE' | 'RESTRITA_18',
  motivo?: string,
) {
  return request<AdminModerationActionResponse>(`/midias/${encodeURIComponent(mediaId)}/decidir`, {
    method: 'POST',
    body: JSON.stringify({
      decisao,
      visibilidadeMidia,
      motivo: motivo?.trim() || undefined,
    }),
  })
}
