import {
  ApiContractError,
  adminApiUrl,
  apiErrorFromResponse,
  normalizeApiError,
} from '@/lib/api-contract'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'
import { getAdminMutationHeaders } from '@/features/admin-anuncios/api'
import type { AdminKycSubmission } from '@/features/admin-anuncios/types'

type AdminKycTemporaryUrl = {
  url: string
  expiraEm: string
}

export type AdminKycDecision = 'APROVAR' | 'REPROVAR' | 'SOLICITAR_AJUSTE'

export type AdminKycDecisionResponse = {
  envioId: string
  status: string
  requestId: string
  revisadoEm: string
}

export function adminDocumentThumbnailUrl(documentId: string) {
  return adminApiUrl(`/documentos/${encodeURIComponent(documentId)}/miniatura`)
}

export async function getAdminDocumentTemporaryUrl(documentId: string) {
  try {
    const response = await fetch(
      adminApiUrl(`/documentos/${encodeURIComponent(documentId)}/url-temporaria`),
      { credentials: 'include', cache: 'no-store' },
    )
    if (!response.ok) throw await apiErrorFromResponse(response)
    try {
      return corrigirEstruturaTexto(await response.json()) as AdminKycTemporaryUrl
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

export async function decideAdminKycSubmission(
  submissionId: string,
  decision: AdminKycDecision,
  reason?: string,
) {
  try {
    const response = await fetch(
      adminApiUrl(`/documentos/envios/${encodeURIComponent(submissionId)}/decidir`),
      {
        method: 'POST',
        credentials: 'include',
        cache: 'no-store',
        headers: await getAdminMutationHeaders(),
        body: JSON.stringify({
          decisao: decision,
          motivo: reason?.trim() || null,
        }),
      },
    )
    if (!response.ok) {
      throw await apiErrorFromResponse(response, { preserveServerMessage: true })
    }
    try {
      return corrigirEstruturaTexto(await response.json()) as AdminKycDecisionResponse
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

export type AdminKycUpload = {
  tipoDocumento: 'IDENTIDADE' | 'VERIFICACAO_IDADE' | 'COMPROVANTE' | 'OUTRO'
  modoDocumento: 'UNICO' | 'FRENTE_VERSO'
  documentoUnico?: File | null
  documentoFrente?: File | null
  documentoVerso?: File | null
  envioSubstituidoId?: string | null
  idempotencyKey: string
}

export async function uploadAdminUserDocuments(usuarioId: string, upload: AdminKycUpload) {
  const form = new FormData()
  form.set('tipoDocumento', upload.tipoDocumento)
  form.set('modoDocumento', upload.modoDocumento)
  if (upload.envioSubstituidoId) form.set('envioSubstituidoId', upload.envioSubstituidoId)
  if (upload.documentoUnico) form.set('documentoUnico', upload.documentoUnico)
  if (upload.documentoFrente) form.set('documentoFrente', upload.documentoFrente)
  if (upload.documentoVerso) form.set('documentoVerso', upload.documentoVerso)
  const headers = new Headers(await getAdminMutationHeaders())
  headers.delete('Content-Type')
  headers.set('Idempotency-Key', upload.idempotencyKey)
  try {
    const response = await fetch(
      adminApiUrl(`/documentos/usuarios/${encodeURIComponent(usuarioId)}/envios`),
      {
        method: 'POST',
        credentials: 'include',
        cache: 'no-store',
        headers,
        body: form,
      },
    )
    if (!response.ok) throw await apiErrorFromResponse(response)
    return corrigirEstruturaTexto(await response.json()) as AdminKycSubmission
  } catch (error) {
    throw normalizeApiError(error)
  }
}
