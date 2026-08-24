import 'server-only'

import { requireArrayPayload } from '@/lib/api-contract'
import { publicServerApiJson } from '@/lib/public-server-api'

export const PUBLIC_FAQ_CACHE_TAG = 'public-faq'
export const PUBLIC_FAQ_REVALIDATE_SECONDS = 3_600

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
  return publicServerApiJson('/faqs', {
    endpointFamily: 'faq.published',
    cache: {
      mode: 'revalidate',
      seconds: PUBLIC_FAQ_REVALIDATE_SECONDS,
      tags: [PUBLIC_FAQ_CACHE_TAG],
    },
    validate: (payload) => requireArrayPayload<FaqPublica>(payload),
  })
}
