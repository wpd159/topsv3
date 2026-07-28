import {
  apiErrorFromResponse,
  publicApiUrl,
  requireArrayPayload,
} from '@/lib/api-contract'

export type FaqPublica = {
  id: string
  pergunta: string
  resposta: string
  categoria: 'GERAL' | 'CONTA' | 'PAGAMENTOS' | 'SEGURANCA' | 'ANUNCIOS'
  categoriaRotulo: string
  status: 'PUBLICADO'
  ordem: number
  publicadoEm: string
  atualizadoEm: string
  versao: number
}

export async function listarFaqsPublicadas(): Promise<FaqPublica[]> {
  const response = await fetch(publicApiUrl('/faqs'), {
    cache: 'no-store',
    signal: AbortSignal.timeout(8000),
  })
  if (!response.ok) {
    throw await apiErrorFromResponse(response)
  }
  return requireArrayPayload<FaqPublica>(await response.json())
}
