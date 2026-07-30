import {
  ApiContractError,
  adminApiUrl,
  apiErrorFromResponse,
  normalizeApiError,
  requireArrayPayload,
} from '@/lib/api-contract'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'
import { getAdminMutationHeaders } from '@/features/admin-anuncios/api'

import type {
  AdminUserDetail,
  AdminUserDeletionEligibility,
  AdminUserDeletionResult,
  AdminUserFilters,
  AdminUserIndicators,
  AdminUserPage,
  AdminUserSummary,
  AdminUserUpdate,
} from './types'

export class AdminUserFormError extends ApiContractError {
  readonly fieldErrors: Record<string, string>

  constructor(message: string, status: number, errors: Array<{ campo?: string; mensagem?: string }>) {
    super(message, status === 409 ? 'CONFLICT' : 'INVALID_REQUEST', status)
    this.name = 'AdminUserFormError'
    this.fieldErrors = Object.fromEntries(
      errors
        .filter((item) => item.campo && item.mensagem)
        .map((item) => [String(item.campo), String(item.mensagem)]),
    )
  }
}

export class AdminUserDeletionError extends ApiContractError {
  readonly blockers: string[]

  constructor(message: string, status: number, blockers: string[]) {
    super(message, status === 409 ? 'CONFLICT' : 'INVALID_REQUEST', status)
    this.name = 'AdminUserDeletionError'
    this.blockers = blockers
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  try {
    const method = (init.method || 'GET').toUpperCase()
    const headers = new Headers(init.headers)
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      const secureHeaders = await getAdminMutationHeaders()
      Object.entries(secureHeaders).forEach(([name, value]) => headers.set(name, value))
    }
    const response = await fetch(adminApiUrl(path), {
      ...init,
      headers,
      credentials: 'include',
      cache: 'no-store',
    })
    if (!response.ok) {
      try {
        const body = await response.clone().json() as {
          mensagem?: string
          erros?: Array<{ campo?: string; mensagem?: string }>
          bloqueios?: string[]
        }
        if (Array.isArray(body.bloqueios)) {
          throw new AdminUserDeletionError(
            body.mensagem || 'A conta possui vinculos que precisam ser preservados.',
            response.status,
            body.bloqueios.map(String),
          )
        }
        if (Array.isArray(body.erros) && body.erros.length > 0) {
          throw new AdminUserFormError(
            body.mensagem || 'Revise os dados informados.',
            response.status,
            body.erros,
          )
        }
      } catch (error) {
        if (error instanceof AdminUserFormError || error instanceof AdminUserDeletionError) throw error
      }
      throw await apiErrorFromResponse(response)
    }
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
    grupo: filters.grupo,
    ordenacao: filters.ordenacao,
    page: String(filters.page),
    size: String(filters.size),
  })
  if (filters.termo?.trim()) query.set('termo', filters.termo.trim())
  if (filters.uf?.trim()) query.set('uf', filters.uf.trim())
  if (filters.cidade?.trim()) query.set('cidade', filters.cidade.trim())
  return normalizePage(await request<AdminUserPage>(`/usuarios?${query.toString()}`))
}

export function getAdminUserIndicators() {
  return request<AdminUserIndicators>('/usuarios/indicadores')
}

export function getAdminUser(id: string) {
  return request<AdminUserDetail>(`/usuarios/${encodeURIComponent(id)}`)
}

export function updateAdminUser(id: string, payload: AdminUserUpdate) {
  return request<AdminUserDetail>(`/usuarios/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export function getAdminUserDeletionEligibility(id: string) {
  return request<AdminUserDeletionEligibility>(
    `/usuarios/${encodeURIComponent(id)}/exclusao`,
  )
}

export function deleteAdminUser(id: string, motivo: string, idempotencyKey: string) {
  return request<AdminUserDeletionResult>(`/usuarios/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
    body: JSON.stringify({ confirmacao: 'EXCLUIR', motivo }),
  })
}
