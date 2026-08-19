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
  AdminAdQueueNavigation,
  AdminAdRemovalResponse,
  AdminAdOwnerUpdate,
  AdminAdOwnerUpdateResponse,
  AdminAdUpdate,
  AdminFilterLocation,
  AdminKycSubmission,
  AdminLegalBlockCategory,
  AdminLegalOperationResponse,
  AdminMediaItem,
  AdminMediaPreview,
  AdminModerationActionResponse,
  AdminModerationHistoryItem,
  AdminPage,
  AdminPhotoBatchDecision,
  AdminPhotoBatchResponse,
  AdminPremiumBenefit,
  AdminPremiumActivationBatch,
  AdminPremiumCatalogItem,
  AdminStoryPublication,
} from './types'
import type { AdminAdQueueContext } from './queue-context'
import { adminAdQueueFilters } from './queue-context'

function csrfCookieName() {
  return ['XSRF', 'TOKEN'].join('-')
}

function csrfHeaderName() {
  return ['X', 'XSRF', 'TOKEN'].join('-')
}

export class AdminAdOwnerFormError extends ApiContractError {
  readonly fieldErrors: Record<string, string>

  constructor(message: string, status: number, errors: Array<{ campo?: string; mensagem?: string }>) {
    super(message, status === 409 ? 'CONFLICT' : 'INVALID_REQUEST', status)
    this.name = 'AdminAdOwnerFormError'
    this.fieldErrors = Object.fromEntries(
      errors
        .filter((item) => item.campo && item.mensagem)
        .map((item) => [String(item.campo), String(item.mensagem)]),
    )
  }
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

export function getAdminMutationHeaders() {
  return csrfHeaders()
}

async function readJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw await apiErrorFromResponse(response, { preserveServerMessage: true })
  }
  try {
    return corrigirEstruturaTexto(await response.json()) as T
  } catch {
    throw new ApiContractError('O servico retornou uma resposta incompativel.', 'TECHNICAL_FAILURE', 502, true)
  }
}

async function adminAdOwnerErrorFromResponse(response: Response) {
  try {
    const body = await response.clone().json() as {
      mensagem?: string
      erros?: Array<{ campo?: string; mensagem?: string }>
    }
    if (Array.isArray(body.erros) && body.erros.length > 0) {
      return new AdminAdOwnerFormError(
        body.mensagem || 'Revise os dados informados.',
        response.status,
        body.erros,
      )
    }
  } catch {
    // A resposta generica abaixo preserva o contrato quando nao houver erro de campo.
  }
  return apiErrorFromResponse(response, { preserveServerMessage: true })
}

function pagePayload<T>(payload: unknown): AdminPage<T> {
  if (!payload || typeof payload !== 'object') {
    throw new ApiContractError('O servico retornou uma pagina incompativel.', 'TECHNICAL_FAILURE', 502, true)
  }
  const page = payload as Partial<AdminPage<T>>
  return { ...page, itens: requireArrayPayload<T>(page.itens) } as AdminPage<T>
}

async function request<T>(path: string, init: RequestInit = {}, parseOwnerFormError = false) {
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
    if (!response.ok && parseOwnerFormError) {
      throw await adminAdOwnerErrorFromResponse(response)
    }
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

export async function listAdminAdFilterLocations() {
  return requireArrayPayload<AdminFilterLocation>(await request('/anuncios/filtros/localidades'))
}

export function getAdminAd(id: string) {
  return request<AdminAdDetail>(`/anuncios/${encodeURIComponent(id)}`)
}

export function updateAdminAd(id: string, payload: AdminAdUpdate) {
  return request<AdminAdDetail>(`/anuncios/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function updateAdminAdOwner(id: string, payload: AdminAdOwnerUpdate) {
  return request<AdminAdOwnerUpdateResponse>(`/anuncios/${encodeURIComponent(id)}/proprietario`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  }, true)
}

export function reactivateAdminAd(id: string) {
  return request<AdminLegalOperationResponse>(`/anuncios/${encodeURIComponent(id)}/reativar`, {
    method: 'POST',
  })
}

export function removeAdminAd(id: string, motivo: string) {
  return request<AdminAdRemovalResponse>(`/anuncios/${encodeURIComponent(id)}/remocao-logica`, {
    method: 'POST',
    body: JSON.stringify({ motivo: motivo.trim() }),
  })
}

export function blockAdminAd(
  id: string,
  payload: { categoria: AdminLegalBlockCategory; motivo: string; observacaoInterna?: string | null },
) {
  return request<AdminLegalOperationResponse>(`/anuncios/${encodeURIComponent(id)}/bloqueio-juridico`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function blockAdminAdAndUser(
  id: string,
  payload: { categoria: AdminLegalBlockCategory; motivo: string; observacaoInterna?: string | null },
) {
  return request<AdminLegalOperationResponse>(`/anuncios/${encodeURIComponent(id)}/bloqueio-juridico/usuario`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function unblockAdminAd(id: string, motivo?: string) {
  return request<AdminLegalOperationResponse>(`/anuncios/${encodeURIComponent(id)}/desbloqueio-juridico`, {
    method: 'POST',
    body: JSON.stringify({ motivo: motivo?.trim() || null }),
  })
}

export function unblockAdminUser(id: string, motivo?: string) {
  return request<AdminLegalOperationResponse>(`/anuncios/${encodeURIComponent(id)}/desbloqueio-juridico/usuario`, {
    method: 'POST',
    body: JSON.stringify({ motivo: motivo?.trim() || null }),
  })
}

export async function getAdminAdQueueNavigation(id: string, context: AdminAdQueueContext) {
  const pages = new Map<number, AdminPage<AdminAdListItem>>()
  async function load(page: number) {
    if (page < 0) return null
    const cached = pages.get(page)
    if (cached) return cached
    const loaded = await listAdminAds(adminAdQueueFilters({ ...context, page }))
    pages.set(page, loaded)
    return loaded
  }

  let currentPage = context.page
  let current = await load(currentPage)
  let index = current?.itens.findIndex((item) => item.id === id) ?? -1
  if (index < 0) {
    for (const candidate of [context.page - 1, context.page + 1]) {
      const page = await load(candidate)
      const candidateIndex = page?.itens.findIndex((item) => item.id === id) ?? -1
      if (candidateIndex >= 0) {
        currentPage = candidate
        current = page
        index = candidateIndex
        break
      }
    }
  }
  if (!current || index < 0) return null

  let anterior = index > 0 ? { id: current.itens[index - 1].id, page: currentPage } : null
  if (!anterior && currentPage > 0) {
    const previousPage = await load(currentPage - 1)
    const previousItem = previousPage?.itens.at(-1)
    if (previousItem) anterior = { id: previousItem.id, page: currentPage - 1 }
  }

  let proximo = index < current.itens.length - 1
    ? { id: current.itens[index + 1].id, page: currentPage }
    : null
  if (!proximo && !current.last) {
    const nextPage = await load(currentPage + 1)
    const nextItem = nextPage?.itens[0]
    if (nextItem) proximo = { id: nextItem.id, page: currentPage + 1 }
  }

  return {
    anterior,
    proximo,
    posicao: currentPage * context.size + index + 1,
    total: current.totalElements,
    page: currentPage,
  } satisfies AdminAdQueueNavigation
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

export async function listAdminAdDocuments(id: string) {
  return requireArrayPayload<AdminKycSubmission>(
    await request(`/anuncios/${encodeURIComponent(id)}/documentos`)
  )
}

export async function listAdminPremiumBenefits(id: string) {
  return requireArrayPayload<AdminPremiumBenefit>(
    await request(`/premium/anuncios/${encodeURIComponent(id)}/beneficios`)
  )
}

export async function listAdminPremiumCatalog() {
  return requireArrayPayload<AdminPremiumCatalogItem>(await request('/premium/catalogo'))
}

export function activateAdminPremiumBatch(
  anuncioId: string,
  payload: { beneficios: Array<{ beneficioId: string; duracaoDias: number }>; observacao?: string | null },
  idempotencyKey: string,
) {
  return request<AdminPremiumActivationBatch>(`/premium/anuncios/${encodeURIComponent(anuncioId)}/ativacoes/lote`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(payload),
  })
}

export function cancelAdminPremium(activationId: string, motivo: string, idempotencyKey: string) {
  return request(`/premium/ativacoes/${encodeURIComponent(activationId)}/cancelar`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ motivo }),
  })
}

export function publishAdminStory(anuncioId: string, idempotencyKey: string) {
  return request<AdminStoryPublication>('/stories', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ anuncioId }),
  })
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

export function approveAdminAd(id: string) {
  return request<AdminModerationActionResponse>(`/anuncios/${encodeURIComponent(id)}/aprovar`, {
    method: 'POST',
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
  anuncioId: string,
  mediaId: string,
  decisao: 'APROVAR' | 'REPROVAR',
  visibilidadeMidia?: 'LIVRE' | 'RESTRITA_18',
  motivo?: string,
  observacao?: string,
) {
  return request<AdminModerationActionResponse>(`/midias/${encodeURIComponent(mediaId)}/decidir`, {
    method: 'POST',
    body: JSON.stringify({
      anuncioId,
      decisao,
      visibilidadeMidia,
      motivo: motivo?.trim() || undefined,
      observacao: observacao?.trim() || undefined,
    }),
  })
}

export async function decideAdminPhotosBatch(
  anuncioId: string,
  fotos: AdminPhotoBatchDecision[],
) {
  try {
    const response = await fetch(adminApiUrl(
      `/anuncios/${encodeURIComponent(anuncioId)}/midias/decisoes`,
    ), {
      method: 'POST',
      headers: await csrfHeaders(),
      body: JSON.stringify({ fotos }),
      credentials: 'include',
      cache: 'no-store',
    })
    let payload: Partial<AdminPhotoBatchResponse>
    try {
      payload = corrigirEstruturaTexto(await response.clone().json()) as Partial<AdminPhotoBatchResponse>
    } catch {
      if (!response.ok) throw await apiErrorFromResponse(response)
      throw new ApiContractError(
        'O serviço retornou uma resposta incompatível.',
        'TECHNICAL_FAILURE',
        502,
        true,
      )
    }
    const respostaValida = Array.isArray(payload.resultados)
      && typeof payload.concluido === 'boolean'
      && typeof payload.requestId === 'string'
    if (response.ok && respostaValida) {
      return payload as AdminPhotoBatchResponse
    }
    if (!response.ok && respostaValida) {
      const falha = payload.resultados?.find((item) => item.resultado === 'FALHA')
      throw new ApiContractError(
        falha?.motivo || 'A operação não pôde ser concluída no estado atual.',
        response.status === 409 ? 'CONFLICT' : response.status >= 500 ? 'TECHNICAL_FAILURE' : 'INVALID_REQUEST',
        response.status,
        response.status >= 500,
        payload.requestId || response.headers.get('X-Request-Id'),
      )
    }
    if (!response.ok) throw await apiErrorFromResponse(response)
    throw new ApiContractError(
      'O serviço retornou uma resposta incompatível.',
      'TECHNICAL_FAILURE',
      502,
      true,
    )
  } catch (error) {
    throw normalizeApiError(error)
  }
}

export function reclassifyAdminMedia(
  mediaId: string,
  visibilidadeMidia: 'LIVRE' | 'RESTRITA_18',
  motivo: string,
) {
  return request<AdminModerationActionResponse>(`/midias/${encodeURIComponent(mediaId)}/reclassificar`, {
    method: 'POST',
    body: JSON.stringify({ visibilidadeMidia, motivo: motivo.trim() }),
  })
}
