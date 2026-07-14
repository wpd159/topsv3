'use client'

import { corrigirEstruturaTexto } from '@/lib/text/encoding'

const API_URL = process.env.NEXT_PUBLIC_API_URL

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

async function adminFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    credentials: 'include',
    cache: 'no-store',
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers || {}),
    },
  })

  const raw = await response.text().catch(() => '')
  let payload: unknown = null

  if (raw.trim()) {
    try {
      payload = corrigirEstruturaTexto(JSON.parse(raw))
    } catch {
      payload = { error: raw }
    }
  }

  if (!response.ok) {
    const message =
      payload && typeof payload === 'object' && 'message' in payload
        ? String((payload as { message?: string }).message || '')
        : payload && typeof payload === 'object' && 'error' in payload
          ? String((payload as { error?: string }).error || '')
          : ''
    throw new Error(message || 'Falha ao consultar benefícios premium.')
  }

  return payload as T
}

export function fetchPremiumBenefitsDashboard() {
  return adminFetch<PremiumBenefitDashboard>('/admin/premium-benefits/dashboard')
}

export function searchPremiumBenefits(q: string) {
  return adminFetch<PremiumBenefitSearchResponse>(`/admin/premium-benefits/search?q=${encodeURIComponent(q)}`)
}

export function fetchPremiumBenefitsDashboardAds(filtro: PremiumBenefitDashboardFilter) {
  return adminFetch<PremiumBenefitAnuncioSummary[]>(
    `/admin/premium-benefits/dashboard/anuncios?filtro=${encodeURIComponent(filtro)}`
  )
}

export function fetchPremiumBenefitsUserAds(usuarioId: number) {
  return adminFetch<PremiumBenefitAnuncioSummary[]>(`/admin/premium-benefits/usuarios/${usuarioId}/anuncios`)
}

export function fetchPremiumBenefitsAnuncio(anuncioId: number) {
  return adminFetch<PremiumBenefitAnuncioDetail>(`/admin/premium-benefits/anuncios/${anuncioId}`)
}
