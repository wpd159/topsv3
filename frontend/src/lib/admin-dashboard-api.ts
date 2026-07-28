import {
  adminApiUrl,
  apiErrorFromResponse,
  normalizeApiError,
} from '@/lib/api-contract'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'

export type AdminDashboardAnuncios = {
  totalAtivos: number
  publicados: number
  pendentesRevisao: number
  pausados: number
  comContatoConfigurado: number
}

export type AdminDashboardModeracao = {
  revisoesAbertas: number
  revisoesEmAnalise: number
  anunciosPendentesModeracao: number
  anunciosRejeitados: number
  documentosPendentes: number
}

export type AdminDashboardMidias = {
  arquivosTotal: number
  arquivosPendentes: number
  arquivosValidados: number
  midiasPublicaveis: number
  midiasPendentes: number
  midiasRestritas18: number
  storiesPublicados: number
  storiesPendentes: number
}

export type AdminDashboardHoje = {
  dataReferencia: string
  fusoHorario: string
  visualizacoes: number
  cliquesWhatsapp: number
  beneficiosPremiumVigentes: number
  calculadoEm: string
}

export type AdminDashboardDailyPoint = {
  data: string
  visualizacoes: number
  cliquesWhatsapp: number
  conversaoPct: number
}

export type AdminDashboardDailySummary = AdminDashboardDailyPoint

export type AdminDashboardDailyPerformance = {
  dias: 7 | 15 | 30
  inicio: string
  fim: string
  fusoHorario: string
  serieDiaria: AdminDashboardDailyPoint[]
  hoje: AdminDashboardDailySummary
  ontem: AdminDashboardDailySummary
  variacaoVisualizacoesPct: number
  variacaoCliquesPct: number
  totalVisualizacoes: number
  totalCliquesWhatsapp: number
  calculadoEm: string
}

export type AdminDashboardTopWhatsappItem = {
  anuncioId: string
  titulo: string
  slug: string
  cidade?: string | null
  uf?: string | null
  cliquesWhatsappHoje: number
  miniaturaUrl?: string | null
  publicado: boolean
}

export type AdminDashboardTopWhatsapp = {
  dataReferencia: string
  fusoHorario: string
  limite: number
  temMais: boolean
  itens: AdminDashboardTopWhatsappItem[]
  calculadoEm: string
}

export type AdminDashboardInsight = {
  codigo: string
  titulo: string
  descricao: string
  href?: string | null
}

export type AdminDashboardAdPerformance = {
  anuncioId: string
  titulo: string
  slug: string
  cidade?: string | null
  uf?: string | null
  visualizacoes: number
  cliquesWhatsapp: number
  conversaoPct: number
}

export type AdminDashboardCityPerformance = {
  cidade: string
  cidadeSlug?: string | null
  uf: string
  anunciosPublicadosAtivos: number
  visualizacoes: number
  cliquesWhatsapp: number
  conversaoPct: number
}

export type AdminDashboardClassificationPerformance = {
  classificacao: 'LIVRE' | 'RESTRITA_18'
  anunciosPublicadosAtivos: number
  visualizacoes: number
  cliquesWhatsapp: number
  conversaoPct: number
}

export type AdminDashboardAnalyses = {
  alertasPrioritarios: AdminDashboardInsight[]
  oportunidadesComerciais: AdminDashboardInsight[]
  topConversao: AdminDashboardAdPerformance[]
  piorConversaoComTrafego: AdminDashboardAdPerformance[]
  desempenhoPorCidade: AdminDashboardCityPerformance[]
  desempenhoPorClassificacao: AdminDashboardClassificationPerformance[]
  minimoVisualizacoesPiorConversao: number
  calculadoEm: string
}

async function request<T>(path: string, signal?: AbortSignal): Promise<T> {
  try {
    const response = await fetch(adminApiUrl(path), {
      credentials: 'include',
      cache: 'no-store',
      signal,
    })
    if (!response.ok) throw await apiErrorFromResponse(response)
    return corrigirEstruturaTexto(await response.json()) as T
  } catch (error) {
    throw normalizeApiError(error)
  }
}

export function fetchDashboardAnuncios(signal?: AbortSignal) {
  return request<AdminDashboardAnuncios>('/anuncios/resumo', signal)
}

export function fetchDashboardModeracao(signal?: AbortSignal) {
  return request<AdminDashboardModeracao>('/moderacao/resumo', signal)
}

export function fetchDashboardMidias(signal?: AbortSignal) {
  return request<AdminDashboardMidias>('/midias/resumo', signal)
}

export function fetchDashboardHoje(signal?: AbortSignal) {
  return request<AdminDashboardHoje>('/dashboard/hoje', signal)
}

export function fetchDashboardDailyPerformance(
  dias: 7 | 15 | 30,
  signal?: AbortSignal,
) {
  return request<AdminDashboardDailyPerformance>(
    `/dashboard/desempenho-diario?dias=${dias}`,
    signal,
  )
}

export function fetchDashboardTopWhatsapp(
  limite: number,
  signal?: AbortSignal,
) {
  return request<AdminDashboardTopWhatsapp>(
    `/dashboard/top-whatsapp-hoje?limite=${limite}`,
    signal,
  )
}

export function fetchDashboardAnalyses(signal?: AbortSignal) {
  return request<AdminDashboardAnalyses>('/dashboard/analises', signal)
}
