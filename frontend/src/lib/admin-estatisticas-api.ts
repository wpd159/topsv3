import {
  BackendContractPendingError,
  PENDING_BACKEND_CONTRACTS,
  adminApiUrl,
  apiErrorFromResponse,
} from '@/lib/api-contract'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'

export type AdminPerformanceItem = {
  id: string | number
  titulo: string
  visualizacoes: number
  cliquesWhatsapp: number
  taxaConversao: number
  posicaoRanking?: number
  cidadeNome?: string | null
}

export type AdminPerformanceResponse = {
  totalVisualizacoes?: number
  totalCliquesWhatsapp?: number
  rankingPorCliques?: AdminPerformanceItem[]
  topPorConversao?: AdminPerformanceItem[]
}

export type AdminPerformanceSummary = {
  anunciosComMetricas: number
  visualizacoesTotal: number
  cliquesWhatsappTotal: number
  taxaCliqueView: number
  visualizacoesOrganicas: number
  visualizacoesComPremium: number
  calculadoEm: string
}

export type DesempenhoDiarioPonto = {
  data: string
  visualizacoes: number
  cliquesWhatsapp: number
}

export type DesempenhoDiarioResponse = {
  serieDiaria: DesempenhoDiarioPonto[]
  hojeVisualizacoes?: number
  hojeCliquesWhatsapp?: number
  hojeConversaoPct?: number
  ontemVisualizacoes?: number
  ontemCliquesWhatsapp?: number
  ontemConversaoPct?: number
  varVisualizacoesPct?: number | null
  varCliquesPct?: number | null
}

export async function fetchAdminPerformanceSummary(): Promise<AdminPerformanceSummary> {
  const response = await fetch(adminApiUrl('/desempenho/resumo'), {
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) throw await apiErrorFromResponse(response)
  return corrigirEstruturaTexto(await response.json()) as AdminPerformanceSummary
}

export async function fetchAdminPerformanceAnuncios(): Promise<AdminPerformanceResponse> {
  throw new BackendContractPendingError('Ranking administrativo de desempenho por anuncio')
}

export async function fetchDesempenhoDiario(_dias: 7 | 15 | 30): Promise<DesempenhoDiarioResponse> {
  throw new BackendContractPendingError('Serie diaria agregada do dashboard')
}

export type TopWhatsappHojeItem = {
  anuncioId: string | number
  titulo?: string | null
  cidadeNome?: string | null
  thumbnailUrl?: string | null
  cliquesWhatsappHoje: number
  slug?: string | null
}

export async function fetchTopWhatsappHoje(_limit = 5): Promise<TopWhatsappHojeItem[]> {
  throw new BackendContractPendingError(PENDING_BACKEND_CONTRACTS.adminAnalytics)
}
