import {
  ApiContractError,
  adminApiUrl,
  apiErrorFromResponse,
  normalizeApiError,
  requireArrayPayload,
} from '@/lib/api-contract'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'
import { isSupportedUploadVideo, validatePhotoUpload } from '@/lib/photo-upload-validation'

import type {
  AdminAdDetail,
  AdminAdFilters,
  AdminAdListItem,
  AdminAdMediaUploadResponse,
  AdminAdVideoUploadResponse,
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
  AdminApprovalRequest,
  AdminApprovalStatus,
  AdminReviewState,
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

async function csrfHeaders(contentType: 'json' | 'multipart' = 'json') {
  let value = readCsrfValue()
  if (!value) {
    await fetch(adminApiUrl('/auth/me'), { credentials: 'include', cache: 'no-store' })
    value = readCsrfValue()
  }
  if (!value) {
    throw new ApiContractError('Nao foi possivel validar a seguranca da sessao.', 'ACCESS_DENIED', 403)
  }
  return contentType === 'json'
    ? { 'Content-Type': 'application/json', [csrfHeaderName()]: value }
    : { [csrfHeaderName()]: value }
}

export function getAdminMutationHeaders() {
  return csrfHeaders()
}

type AdminRequestOptions = {
  parseOwnerFormError?: boolean
  unsupportedPhotoUpload?: boolean
}

async function readJson<T>(response: Response, options: AdminRequestOptions = {}): Promise<T> {
  if (!response.ok) {
    throw await apiErrorFromResponse(response, {
      preserveServerMessage: true,
      unsupportedPhotoUpload: options.unsupportedPhotoUpload === true,
    })
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

async function request<T>(path: string, init: RequestInit = {}, options: AdminRequestOptions = {}) {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const multipart = typeof FormData !== 'undefined' && init.body instanceof FormData
    const secureHeaders = await csrfHeaders(multipart ? 'multipart' : 'json')
    Object.entries(secureHeaders).forEach(([name, value]) => headers.set(name, value))
  }
  try {
    const response = await fetch(adminApiUrl(path), {
      ...init,
      headers,
      credentials: 'include',
      cache: 'no-store',
    })
    if (!response.ok && options.parseOwnerFormError) {
      throw await adminAdOwnerErrorFromResponse(response)
    }
    return await readJson<T>(response, options)
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
  }, { parseOwnerFormError: true })
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

const ADMIN_AD_MEDIA_PAGE_SIZE = 50
const ADMIN_AD_MEDIA_MAX_PAGES = 20

function invalidAdminAdMediaPagination() {
  return new ApiContractError(
    'O serviço retornou uma paginação de mídias incompatível.',
    'TECHNICAL_FAILURE',
    502,
    true,
    null,
    'ADMIN_MEDIA_PAGINATION_INVALID',
  )
}

function validateAdminAdMediaPage(page: AdminPage<AdminMediaItem>, requestedPage: number) {
  const metadataIsValid = Number.isSafeInteger(page.page)
    && page.page === requestedPage
    && Number.isSafeInteger(page.size)
    && page.size === ADMIN_AD_MEDIA_PAGE_SIZE
    && Number.isSafeInteger(page.totalElements)
    && page.totalElements >= 0
    && Number.isSafeInteger(page.totalPages)
    && page.totalPages >= 0
    && page.totalPages <= ADMIN_AD_MEDIA_MAX_PAGES
    && typeof page.last === 'boolean'
    && page.itens.length <= page.size
  if (!metadataIsValid) throw invalidAdminAdMediaPagination()

  const expectedTotalPages = page.totalElements === 0
    ? 0
    : Math.ceil(page.totalElements / page.size)
  const expectedLast = expectedTotalPages === 0 || requestedPage === expectedTotalPages - 1
  if (
    page.totalPages !== expectedTotalPages
    || page.last !== expectedLast
    || (!page.last && page.itens.length !== page.size)
  ) {
    throw invalidAdminAdMediaPagination()
  }
}

async function collectAdminAdMediaPages(
  firstPage: AdminPage<AdminMediaItem>,
  loadPage: (page: number) => Promise<AdminPage<AdminMediaItem>>,
) {
  validateAdminAdMediaPage(firstPage, 0)
  const baseline = {
    totalElements: firstPage.totalElements,
    totalPages: firstPage.totalPages,
  }
  const itens = [...firstPage.itens]

  for (let requestedPage = 1; requestedPage < baseline.totalPages; requestedPage += 1) {
    const page = await loadPage(requestedPage)
    validateAdminAdMediaPage(page, requestedPage)
    if (
      page.totalElements !== baseline.totalElements
      || page.totalPages !== baseline.totalPages
    ) {
      throw invalidAdminAdMediaPagination()
    }
    itens.push(...page.itens)
  }

  if (itens.length !== baseline.totalElements) throw invalidAdminAdMediaPagination()
  const uniqueIds = new Set(itens.map((item) => item.id))
  if (uniqueIds.size !== itens.length) throw invalidAdminAdMediaPagination()
  return { ...firstPage, itens }
}

export async function listAdminAdMedia(id: string) {
  const loadPage = async (page: number) => pagePayload<AdminMediaItem>(
    await request<unknown>(
      `/anuncios/${encodeURIComponent(id)}/midias?page=${page}&size=${ADMIN_AD_MEDIA_PAGE_SIZE}`,
    ),
  )
  const firstPage = await loadPage(0)
  return collectAdminAdMediaPages(firstPage, loadPage)
}

export async function uploadAdminAdMedia(id: string, arquivo: File, idempotencyKey: string) {
  const validation = await validatePhotoUpload(arquivo)
  if (!validation.valid) {
    throw new ApiContractError(
      `${arquivo.name}: ${validation.message}`,
      'INVALID_REQUEST',
      null,
      false,
      null,
      'PHOTO_UPLOAD_LOCAL_INVALID',
    )
  }
  const form = new FormData()
  form.append('arquivo', arquivo)
  return request<AdminAdMediaUploadResponse>(`/anuncios/${encodeURIComponent(id)}/midias`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: form,
  }, { unsupportedPhotoUpload: true })
}

const ADMIN_VIDEO_STATUSES = new Set(['PENDENTE', 'AJUSTE_SOLICITADO', 'PUBLICAVEL', 'REJEITADA', 'REMOVIDA'])
const ADMIN_VIDEO_FILE_STATUSES = new Set(['PENDENTE', 'VALIDADO', 'REJEITADO', 'REMOVIDO'])

export function adminVideoUploadIssue(arquivo: File): string | null {
  if (arquivo.size === 0) return 'O arquivo está vazio. Selecione outro vídeo.'
  if (!isSupportedUploadVideo(arquivo)) return 'Formato de vídeo não aceito. Selecione um vídeo MP4 ou MOV.'
  return null
}

function canonicalAdminVideoUpload(payload: unknown, anuncioId: string, requestIdHeader: string | null): AdminAdVideoUploadResponse {
  const item = payload && typeof payload === 'object' ? payload as Partial<AdminAdVideoUploadResponse> : null
  if (!item || typeof item.midiaId !== 'string' || !item.midiaId.trim()
    || item.anuncioId !== anuncioId || item.tipo !== 'VIDEO' || item.finalidade !== 'GALERIA'
    || !Number.isInteger(item.ordem) || (item.ordem ?? -1) < 0
    || typeof item.idempotente !== 'boolean'
    || typeof item.status !== 'string' || !ADMIN_VIDEO_STATUSES.has(item.status)
    || typeof item.statusArquivo !== 'string' || !ADMIN_VIDEO_FILE_STATUSES.has(item.statusArquivo)
    || !item.idempotente && (item.status !== 'PENDENTE' || item.statusArquivo !== 'PENDENTE')
    || typeof item.requestId !== 'string' || !item.requestId.trim()) {
    throw new ApiContractError(
      'O serviço retornou uma confirmação de vídeo incompatível. O arquivo foi mantido para conferência e nova tentativa.',
      'TECHNICAL_FAILURE', 502, true, requestIdHeader, 'ADMIN_VIDEO_UPLOAD_INVALID_RESPONSE',
    )
  }
  return item as AdminAdVideoUploadResponse
}

export async function uploadAdminAdVideo(
  id: string,
  arquivo: File,
  idempotencyKey: string,
  context: { actorId: string; isCurrent: () => boolean },
  onProgress?: (percentual: number) => void,
): Promise<AdminAdVideoUploadResponse> {
  const issue = adminVideoUploadIssue(arquivo)
  if (issue) throw new ApiContractError(issue, 'INVALID_REQUEST', null, false, null, 'ADMIN_VIDEO_UPLOAD_LOCAL_INVALID')
  if (!/^[A-Za-z0-9._:-]{1,160}$/.test(idempotencyKey)) {
    throw new ApiContractError('Chave da operação de vídeo inválida.', 'INVALID_REQUEST', null, false)
  }
  const contextChanged = () => new ApiContractError(
    'O contexto do anúncio mudou antes do envio. Volte ao anúncio original e confira a lista antes de repetir a operação.',
    'CONFLICT', 409, true,
  )
  if (!context.actorId || !context.isCurrent()) throw contextChanged()
  const secureHeaders = await csrfHeaders('multipart')
  if (!context.isCurrent()) throw contextChanged()
  const [session, ad] = await Promise.all([
    request<{ autenticado: boolean; usuarioId: string; papeis: string[]; permissoes: string[] }>('/auth/me'),
    getAdminAd(id),
  ])
  if (!context.isCurrent()) throw contextChanged()
  if (!session?.autenticado || session.usuarioId !== context.actorId) {
    throw new ApiContractError('A sessão administrativa mudou antes do envio do vídeo.', 'SESSION_REQUIRED', 401, false)
  }
  if (!session.papeis?.includes('ADMIN') || !session.permissoes?.includes('ANUNCIO_MODERAR')
    || !session.permissoes?.includes('MIDIA_REVISAR')) {
    throw new ApiContractError('A sessão não possui permissão para enviar vídeo.', 'ACCESS_DENIED', 403, false)
  }
  if (ad?.id !== id || ad.status === 'REMOVIDO' || ad.status === 'BLOQUEADO'
    || ad.anunciante?.status !== 'ATIVO' || ad.bloqueioJuridico?.anuncioBloqueado
    || ad.bloqueioJuridico?.usuarioBloqueado || ad.revisaoAberta?.status === 'EM_ANALISE') {
    throw new ApiContractError('O estado atual do anúncio não permite enviar vídeo.', 'CONFLICT', 409, true)
  }
  return new Promise<AdminAdVideoUploadResponse>((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', adminApiUrl(`/anuncios/${encodeURIComponent(id)}/midias`))
    xhr.withCredentials = true
    xhr.setRequestHeader('Accept', 'application/json')
    xhr.setRequestHeader('Idempotency-Key', idempotencyKey)
    Object.entries(secureHeaders).forEach(([name, value]) => xhr.setRequestHeader(name, value))
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && event.total > 0) {
        onProgress?.(Math.min(99, Math.max(0, Math.floor((event.loaded / event.total) * 100))))
      }
    }
    xhr.onerror = () => reject(normalizeApiError(new Error('Falha de rede durante o envio do vídeo.')))
    xhr.onabort = () => reject(normalizeApiError(new Error('Envio do vídeo interrompido.')))
    xhr.onload = () => {
      const requestId = xhr.getResponseHeader('X-Request-Id')
      if (xhr.status === 0) {
        reject(normalizeApiError(new Error('Resposta de rede ausente durante o envio do vídeo.')))
        return
      }
      if (xhr.status < 200 || xhr.status >= 300) {
        const headers = new Headers()
        if (requestId) headers.set('X-Request-Id', requestId)
        void apiErrorFromResponse(new Response(xhr.responseText, { status: xhr.status, headers }), {
          preserveServerMessage: true,
        }).then((error) => reject(xhr.status === 413
          ? new ApiContractError('O vídeo excede o limite permitido pelo servidor.', 'INVALID_REQUEST', 413,
            false, error.requestId, error.code)
          : error), (error) => reject(normalizeApiError(error)))
        return
      }
      try {
        resolve(canonicalAdminVideoUpload(JSON.parse(xhr.responseText), id, requestId))
      } catch (error) {
        reject(error instanceof ApiContractError ? error : new ApiContractError(
          'O serviço retornou uma confirmação de vídeo incompatível. O arquivo foi mantido para conferência e nova tentativa.',
          'TECHNICAL_FAILURE', 502, true, requestId, 'ADMIN_VIDEO_UPLOAD_INVALID_RESPONSE',
        ))
      }
    }
    const form = new FormData()
    form.append('arquivo', arquivo)
    try {
      if (!context.isCurrent()) throw contextChanged()
      xhr.send(form)
    } catch (error) {
      reject(normalizeApiError(error))
    }
  })
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

export function getAdminReview(reviewId: string) {
  return request<AdminReviewState>(`/moderacao/revisoes/${encodeURIComponent(reviewId)}`)
}

export function getAdminApprovalStatus(anuncioId: string, operation: AdminApprovalRequest) {
  const query = new URLSearchParams({ versaoAnuncioEsperada: String(operation.versaoAnuncioAntes) })
  if (operation.revisaoId) query.set('revisaoIdEsperada', operation.revisaoId)
  return request<AdminApprovalStatus>(
    `/anuncios/${encodeURIComponent(anuncioId)}/aprovacao-operacoes/${encodeURIComponent(operation.operacaoId)}/status?${query.toString()}`,
  )
}

export function approveAdminAd(id: string, operation?: AdminApprovalRequest) {
  return request<AdminModerationActionResponse>(`/anuncios/${encodeURIComponent(id)}/aprovar`, {
    method: 'POST',
    ...(operation ? { body: JSON.stringify({
      operacaoIdCliente: operation.operacaoId,
      versaoAnuncioEsperada: operation.versaoAnuncioAntes,
      revisaoIdEsperada: operation.revisaoId ?? null,
    }) } : {}),
  })
}

export function decideAdminReview(
  reviewId: string,
  decisao: 'APROVAR' | 'REPROVAR' | 'SOLICITAR_AJUSTE',
  motivo?: string,
  operation?: AdminApprovalRequest,
) {
  return request<AdminModerationActionResponse>(
    `/moderacao/revisoes/${encodeURIComponent(reviewId)}/decidir`,
    {
      method: 'POST',
      body: JSON.stringify({
        decisao, motivo: motivo?.trim() || undefined,
        ...(operation ? {
          operacaoIdCliente: operation.operacaoId,
          versaoAnuncioEsperada: operation.versaoAnuncioAntes,
        } : {}),
      }),
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
        falha?.codigo ?? null,
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
