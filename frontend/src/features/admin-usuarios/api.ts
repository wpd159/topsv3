import {
  ApiContractError,
  adminApiUrl,
  apiErrorFromResponse,
  normalizeApiError,
  requireArrayPayload,
} from '@/lib/api-contract'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'

import type {
  AdminUserDetail,
  AdminUserFilters,
  AdminUserPage,
  AdminUserSummary,
} from './types'

async function request<T>(path: string): Promise<T> {
  try {
    const response = await fetch(adminApiUrl(path), {
      credentials: 'include',
      cache: 'no-store',
    })
    if (!response.ok) throw await apiErrorFromResponse(response)
    try {
      return corrigirEstruturaTexto(await response.json()) as T
    } catch {
      throw new ApiContractError(
        'O servico retornou uma resposta incompativel.',
        'TECHNICAL_FAILURE',
        502,
        true,
      )
    }
  } catch (error) {
    throw normalizeApiError(error)
  }
}

function normalizePage(payload: AdminUserPage): AdminUserPage {
  if (!payload || typeof payload !== 'object') {
    throw new ApiContractError(
      'O servico retornou uma pagina incompativel.',
      'TECHNICAL_FAILURE',
      502,
      true,
    )
  }
  return {
    ...payload,
    itens: requireArrayPayload<AdminUserSummary>(payload.itens),
  }
}

export async function listAdminUsers(filters: AdminUserFilters) {
  const query = new URLSearchParams({
    status: filters.status,
    kyc: filters.kyc,
    ordenacao: filters.ordenacao,
    page: String(filters.page),
    size: String(filters.size),
  })
  if (filters.termo?.trim()) query.set('termo', filters.termo.trim())
  return normalizePage(await request<AdminUserPage>(`/usuarios?${query.toString()}`))
}

export function getAdminUser(id: string) {
  return request<AdminUserDetail>(`/usuarios/${encodeURIComponent(id)}`)
}
