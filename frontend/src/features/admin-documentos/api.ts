import {
  ApiContractError,
  adminApiUrl,
  apiErrorFromResponse,
  normalizeApiError,
} from '@/lib/api-contract'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'

type AdminKycTemporaryUrl = {
  url: string
  expiraEm: string
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
