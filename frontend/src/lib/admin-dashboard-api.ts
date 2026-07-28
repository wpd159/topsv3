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
