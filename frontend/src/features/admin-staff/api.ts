import { getAdminMutationHeaders } from '@/features/admin-anuncios/api'
import {
  ApiContractError,
  adminApiUrl,
  apiErrorFromResponse,
  normalizeApiError,
  requireArrayPayload,
} from '@/lib/api-contract'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'

import type {
  AdminStaffCreate,
  AdminStaffDetail,
  AdminStaffIndicators,
  AdminStaffPage,
  AdminStaffSummary,
  AdminStaffUpdate,
} from './types'

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const secureHeaders = await getAdminMutationHeaders()
    Object.entries(secureHeaders).forEach(([name, value]) => headers.set(name, value))
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

export async function listAdminStaff(filters: {
  termo: string
  papel: string
  status: string
  ordenacao: string
  page: number
  size: number
}) {
  const query = new URLSearchParams({
    papel: filters.papel,
    status: filters.status,
    ordenacao: filters.ordenacao,
    page: String(filters.page),
    size: String(filters.size),
  })
  if (filters.termo.trim()) query.set('termo', filters.termo.trim())
  const payload = await request<AdminStaffPage>(`/staff?${query.toString()}`)
  if (!payload || typeof payload !== 'object') {
    throw new ApiContractError('Resposta de staff incompativel.', 'TECHNICAL_FAILURE', 502, true)
  }
  return { ...payload, itens: requireArrayPayload<AdminStaffSummary>(payload.itens) }
}

export function getAdminStaffIndicators() {
  return request<AdminStaffIndicators>('/staff/indicadores')
}

export function getAdminStaff(id: string) {
  return request<AdminStaffDetail>(`/staff/${encodeURIComponent(id)}`)
}

export function createAdminStaff(payload: AdminStaffCreate) {
  return request<AdminStaffDetail>('/staff', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateAdminStaff(id: string, payload: AdminStaffUpdate) {
  return request<AdminStaffDetail>(`/staff/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}
