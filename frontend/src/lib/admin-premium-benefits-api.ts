'use client'

import { BackendContractPendingError, PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export type PremiumBenefitUserSummary = {
  id: number
  nomeCompleto?: string | null
  username?: string | null
  email?: string | null
  status?: string | null
}
export type PremiumBenefitAnuncioSummary = {
  id: number
  titulo?: string | null
  slug?: string | null
  status?: string | null
  usuarioId?: number | null
  usuarioNome?: string | null
  usuarioUsername?: string | null
  cidadeNome?: string | null
  bairroNome?: string | null
  impulsionado?: boolean | null
}

export type PremiumBenefitCatalogItem = {
  id: number
  codigo: string
  nome: string
  descricao?: string | null
  custoCreditos: number
  duracaoHoras?: number | null
  escopo?: string | null
  ativo?: boolean | null
}

export type PremiumBenefitItem = {
  id: number
  codigo: string
  nome: string
  status: string
  creditosCobrados: number
  dataInicio?: string | null
  dataFim?: string | null
  origem: string
  observacao?: string | null
  manual: boolean
  podeDesativar: boolean
}

export type PremiumBenefitHistoryItem = {
  acao: string
  actorEmail?: string | null
  dataHora?: string | null
  codigo?: string | null
  valorAnterior?: string | null
  valorNovo?: string | null
  duracaoHoras?: number | null
  dataInicio?: string | null
  dataFim?: string | null
  motivo?: string | null
  observacaoInterna?: string | null
}

export type PremiumBenefitDashboard = {
  beneficiosAtivos: number
  beneficiosExpirados: number
  beneficiosVencendoEmBreve: number
  anunciosSemUpsell: number
  anunciosComOcultarIdade: number
}

export type PremiumBenefitDashboardFilter = 'ATIVOS' | 'VENCENDO_EM_BREVE' | 'SEM_UPSELL' | 'OCULTAR_IDADE'

export type PremiumBenefitSearchResponse = {
  usuarios: PremiumBenefitUserSummary[]
  anuncios: PremiumBenefitAnuncioSummary[]
}

export type PremiumBenefitAnuncioDetail = {
  anuncio: PremiumBenefitAnuncioSummary
  usuario: PremiumBenefitUserSummary
  catalogo: PremiumBenefitCatalogItem[]
  beneficiosAtivos: PremiumBenefitItem[]
  beneficiosExpirados: PremiumBenefitItem[]
  historico: PremiumBenefitHistoryItem[]
}

async function pendingContract<T>(): Promise<T> {
  throw new BackendContractPendingError(PENDING_BACKEND_CONTRACTS.premiumLegacyDashboard)
}

export function fetchPremiumBenefitsDashboard() {
  return pendingContract<PremiumBenefitDashboard>()
}

export function searchPremiumBenefits(q: string) {
  void q
  return pendingContract<PremiumBenefitSearchResponse>()
}

export function fetchPremiumBenefitsDashboardAds(filtro: PremiumBenefitDashboardFilter) {
  void filtro
  return pendingContract<PremiumBenefitAnuncioSummary[]>()
}

export function fetchPremiumBenefitsUserAds(usuarioId: number) {
  void usuarioId
  return pendingContract<PremiumBenefitAnuncioSummary[]>()
}

export function fetchPremiumBenefitsAnuncio(anuncioId: number) {
  void anuncioId
  return pendingContract<PremiumBenefitAnuncioDetail>()
}
