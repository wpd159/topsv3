'use client'

import { corrigirEstruturaTexto } from '@/lib/text/encoding'

const API = process.env.NEXT_PUBLIC_API_URL

export type PainelPerformanceItem = {
  anuncioId: number
  anuncioSlug?: string | null
  anuncioTitulo: string
  localizacao?: string | null
  fotoCapa?: string | null
  visualizacoes: number
  cliquesWhatsapp: number
  ctr: number
  scoreVisibilidade: number
  faixaVisibilidade: string
  impulsionado: boolean
  expiraImpulsionamentoEm?: string | null
  totalRecursosPremium: number
  recursosAtivos: string[]
}

export type PainelPerformance = {
  totalVisualizacoes: number
  totalCliquesWhatsapp: number
  ctrGeral: number
  anunciosComRecursosPremium: number
  totalRecursosPremiumAtivos: number
  comparativo: {
    cliquesPeriodoAtual: number
    cliquesPeriodoAnterior: number
    variacaoPercentual: number
    tendencia: string
  }
  serieCliquesWhatsapp: {
    data: string
    label: string
    cliques: number
  }[]
  ranking: PainelPerformanceItem[]
}

async function fetchPainelJson<T>(path: string): Promise<T> {
  if (!API) {
    throw new Error('NEXT_PUBLIC_API_URL não definido')
  }

  const res = await fetch(`${API}${path}`, {
    credentials: 'include',
    cache: 'no-store',
  })

  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }

  return corrigirEstruturaTexto(await res.json()) as T
}

export function fetchPainelPerformance() {
  return fetchPainelJson<PainelPerformance>('/painel-anunciante/performance')
}
