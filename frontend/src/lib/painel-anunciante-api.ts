'use client'

import { corrigirEstruturaTexto } from '@/lib/text/encoding'
import {
  ApiContractError,
  apiErrorFromResponse,
  publicApiUrl,
} from '@/lib/api-contract'
import {
  parseVisualizacoesCanonicas,
  type VisualizacoesCanonicas,
} from '@/lib/visualizacoes-canonicas'

export type PainelPerformanceItem = {
  anuncioId: string
  anuncioSlug: string
  anuncioTitulo: string
  localizacao: string | null
  fotoCapa: string | null
  visualizacoes: VisualizacoesCanonicas
  cliquesWhatsapp: number
  ctr: number | null
  beneficiosPremiumVigentes: number
}

export type PainelPerformance = {
  visualizacoes: VisualizacoesCanonicas
  totalCliquesWhatsapp: number
  ctrGeral: number | null
  anunciosComBeneficioPremiumVigente: number
  comparativo: {
    cliquesPeriodoAtual: number
    cliquesPeriodoAnterior: number
    variacaoPercentual: number
  }
  serieCliquesWhatsapp: {
    data: string
    cliques: number
  }[]
  ranking: PainelPerformanceItem[]
}

function incompatiblePayload(): never {
  throw new ApiContractError(
    'O servico retornou a performance em formato incompativel.',
    'TECHNICAL_FAILURE',
    502,
    true
  )
}

function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) incompatiblePayload()
  return value as Record<string, unknown>
}

function nonNegativeInteger(value: unknown) {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) incompatiblePayload()
  return value
}

function finiteNumberOrNull(value: unknown) {
  if (value === null) return null
  if (typeof value !== 'number' || !Number.isFinite(value)) incompatiblePayload()
  return value
}

function requiredString(value: unknown) {
  if (typeof value !== 'string' || !value.trim()) incompatiblePayload()
  return value
}

function optionalString(value: unknown) {
  if (value === null) return null
  return requiredString(value)
}

function parseCtr(value: unknown, visualizacoes: VisualizacoesCanonicas) {
  const ctr = finiteNumberOrNull(value)
  if (visualizacoes.situacao === 'HISTORICO_PENDENTE') {
    if (ctr !== null) incompatiblePayload()
    return null
  }
  if (ctr === null || ctr < 0) incompatiblePayload()
  return ctr
}

function parseItem(value: unknown): PainelPerformanceItem {
  const raw = record(value)
  const visualizacoes = parseVisualizacoesCanonicas(raw.visualizacoes)
  return {
    anuncioId: requiredString(raw.anuncioId),
    anuncioSlug: requiredString(raw.anuncioSlug),
    anuncioTitulo: requiredString(raw.anuncioTitulo),
    localizacao: optionalString(raw.localizacao),
    fotoCapa: optionalString(raw.fotoCapa),
    visualizacoes,
    cliquesWhatsapp: nonNegativeInteger(raw.cliquesWhatsapp),
    ctr: parseCtr(raw.ctr, visualizacoes),
    beneficiosPremiumVigentes: nonNegativeInteger(raw.beneficiosPremiumVigentes),
  }
}

function parsePainelPerformance(value: unknown): PainelPerformance {
  const raw = record(value)
  const comparativo = record(raw.comparativo)
  const visualizacoes = parseVisualizacoesCanonicas(raw.visualizacoes)
  if (!Array.isArray(raw.serieCliquesWhatsapp) || raw.serieCliquesWhatsapp.length !== 14) incompatiblePayload()
  if (!Array.isArray(raw.ranking)) incompatiblePayload()

  const serieCliquesWhatsapp = raw.serieCliquesWhatsapp.map((item) => {
    const serie = record(item)
    const data = requiredString(serie.data)
    if (!/^\d{4}-\d{2}-\d{2}$/.test(data)) incompatiblePayload()
    return { data, cliques: nonNegativeInteger(serie.cliques) }
  })
  const ranking = raw.ranking.map(parseItem)
  if (new Set(ranking.map((item) => item.anuncioId)).size !== ranking.length) incompatiblePayload()

  return {
    visualizacoes,
    totalCliquesWhatsapp: nonNegativeInteger(raw.totalCliquesWhatsapp),
    ctrGeral: parseCtr(raw.ctrGeral, visualizacoes),
    anunciosComBeneficioPremiumVigente: nonNegativeInteger(raw.anunciosComBeneficioPremiumVigente),
    comparativo: {
      cliquesPeriodoAtual: nonNegativeInteger(comparativo.cliquesPeriodoAtual),
      cliquesPeriodoAnterior: nonNegativeInteger(comparativo.cliquesPeriodoAnterior),
      variacaoPercentual: finiteNumberOrNull(comparativo.variacaoPercentual) ?? incompatiblePayload(),
    },
    serieCliquesWhatsapp,
    ranking,
  }
}

export async function fetchPainelPerformance() {
  const response = await fetch(publicApiUrl('/painel-anunciante/performance'), {
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) throw await apiErrorFromResponse(response)
  return parsePainelPerformance(corrigirEstruturaTexto(await response.json()))
}
