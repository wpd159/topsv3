import {
  ApiContractError,
  apiErrorFromResponse,
  normalizeApiError,
  publicApiUrl,
} from '@/lib/api-contract'
import { publicCsrfHeaders } from '@/lib/compliance/age-gate-api'

export type PublicViewMetricResponse = {
  registrado: boolean
  slug: string
  eventoId: string
  status: 'REGISTRADO'
}

export type PublicWhatsAppMetricResponse = {
  registrado: boolean
  disponivel: boolean
  whatsappUrl?: string | null
  status: 'REGISTRADO' | 'CONTATO_INDISPONIVEL' | 'METRICA_INDISPONIVEL'
}

export function novaChaveMetricaPublica(tipo: 'visualizacao' | 'clique-whatsapp') {
  const random = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `metrica:${tipo}:${random}`
}

async function postMetrica<T>(path: string, idempotencyKey: string): Promise<T> {
  for (let tentativa = 0; tentativa < 2; tentativa += 1) {
    let response: Response
    try {
      const headers = await publicCsrfHeaders()
      headers.set('Idempotency-Key', idempotencyKey)
      response = await fetch(publicApiUrl(path), {
        method: 'POST',
        credentials: 'include',
        cache: 'no-store',
        keepalive: true,
        headers,
        body: '{}',
      })
    } catch (error) {
      const failure = normalizeApiError(error)
      if (tentativa === 0 && failure.retryable) continue
      throw failure
    }

    if (!response.ok) {
      const failure = await apiErrorFromResponse(response)
      if (tentativa === 0 && failure.retryable) continue
      throw failure
    }

    try {
      return (await response.json()) as T
    } catch {
      throw new ApiContractError(
        'O servico de metricas retornou uma resposta incompativel.',
        'TECHNICAL_FAILURE',
        502,
        true,
      )
    }
  }

  throw new ApiContractError(
    'Nao foi possivel registrar a metrica.',
    'TECHNICAL_FAILURE',
    503,
    true,
  )
}

export function registrarVisualizacaoPublica(slug: string, idempotencyKey: string) {
  return postMetrica<PublicViewMetricResponse>(
    `/anuncios/${encodeURIComponent(slug)}/visualizacao`,
    idempotencyKey,
  )
}

export function registrarCliqueWhatsappPublico(slug: string, idempotencyKey: string) {
  return postMetrica<PublicWhatsAppMetricResponse>(
    `/anuncios/${encodeURIComponent(slug)}/clique-whatsapp`,
    idempotencyKey,
  )
}
