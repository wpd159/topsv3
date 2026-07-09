import type { AdminPerformanceItem } from '@/lib/admin-estatisticas-api'

/** Labels de negócio para enum `ContentClassification` (apenas apresentação — backend inalterado). */
export const CLASSIFICATION_LABEL: Record<string, string> = {
  SAFE_PUBLIC: 'Livre',
  ADULT_NON_EXPLICIT: 'Semiexplícito',
  ADULT_RESTRICTED: 'Restrito',
  ADULT_EXPLICIT_BLOCKED: 'Bloqueado',
}

/** Ordem fixa das classificações usadas na moderação v3 v2; outras ficam depois por views. */
const CLASSIFICATION_SORT_ORDER = [
  'SAFE_PUBLIC',
  'ADULT_NON_EXPLICIT',
  'ADULT_EXPLICIT_BLOCKED',
  'ADULT_RESTRICTED',
] as const

/** Exibe label amigável; valores desconhecidos mantêm o código original. */
export function labelClassificacaoDashboard(codigo: string): string {
  const k = (codigo ?? '').trim()
  if (!k || k === '—') return 'Sem classificação'
  return CLASSIFICATION_LABEL[k] ?? k
}

export function worstConversionWithTraffic(
  items: AdminPerformanceItem[],
  minViews = 100,
  take = 5
): AdminPerformanceItem[] {
  return [...items]
    .filter((i) => (i.visualizacoes ?? 0) >= minViews)
    .sort((a, b) => (a.taxaConversao ?? 0) - (b.taxaConversao ?? 0))
    .slice(0, take)
}

export type CidadeAggRow = {
  cidade: string
  ativos: number
  views: number
  cliques: number
  conversao: number
}

export function aggregateByCity(items: AdminPerformanceItem[]): CidadeAggRow[] {
  const m = new Map<string, { views: number; clicks: number; ativos: number }>()
  for (const i of items) {
    const k = (i.cidadeNome ?? '').trim() || '—'
    const cur = m.get(k) ?? { views: 0, clicks: 0, ativos: 0 }
    cur.views += Number(i.visualizacoes ?? 0)
    cur.clicks += Number(i.cliquesWhatsapp ?? 0)
    cur.ativos += 1
    m.set(k, cur)
  }
  return [...m.entries()]
    .map(([cidade, v]) => ({
      cidade,
      ativos: v.ativos,
      views: v.views,
      cliques: v.clicks,
      conversao: v.views <= 0 ? 0 : Math.round((v.clicks / v.views) * 10000) / 100,
    }))
    .sort((a, b) => b.views - a.views)
    .slice(0, 12)
}

export type ClassificacaoAggRow = {
  classe: string
  ativos: number
  views: number
  cliques: number
  conversao: number
}

export function aggregateByClassification(items: AdminPerformanceItem[]): ClassificacaoAggRow[] {
  const m = new Map<string, { views: number; clicks: number; ativos: number }>()
  for (const i of items) {
    const k = (i.contentClassification ?? '').trim() || '—'
    const cur = m.get(k) ?? { views: 0, clicks: 0, ativos: 0 }
    cur.views += Number(i.visualizacoes ?? 0)
    cur.clicks += Number(i.cliquesWhatsapp ?? 0)
    cur.ativos += 1
    m.set(k, cur)
  }
  const rows = [...m.entries()]
    .map(([classe, v]) => ({
      classe,
      ativos: v.ativos,
      views: v.views,
      cliques: v.clicks,
      conversao: v.views <= 0 ? 0 : Math.round((v.clicks / v.views) * 10000) / 100,
    }))
    .filter((r) => r.ativos > 0 || r.views > 0)

  const orderIndex = (c: string) => {
    const i = (CLASSIFICATION_SORT_ORDER as readonly string[]).indexOf(c)
    return i === -1 ? CLASSIFICATION_SORT_ORDER.length : i
  }

  return rows
    .sort((a, b) => {
      const da = orderIndex(a.classe)
      const db = orderIndex(b.classe)
      if (da !== db) return da - db
      return b.views - a.views
    })
    .slice(0, 16)
}

export function pctAnunciosSemClique(items: AdminPerformanceItem[]): number {
  const comViews = items.filter((i) => (i.visualizacoes ?? 0) > 0)
  if (comViews.length === 0) return 0
  const sem = comViews.filter((i) => (i.cliquesWhatsapp ?? 0) === 0).length
  return Math.round((sem / comViews.length) * 10000) / 100
}

/** Mínimo de views do alerta “muitas views e zero clique” (Prioridades). */
export const STRATEGIC_ALERT_ALTO_TRAFEGO_ZERO_MIN_VIEWS = 200

export function matchesComViewsSemClique(
  visualizacoes: number | null | undefined,
  cliquesWhatsapp: number | null | undefined
): boolean {
  const v = Number(visualizacoes ?? 0)
  const c = Number(cliquesWhatsapp ?? 0)
  return v > 0 && c === 0
}

export function matchesAltoTrafegoZeroCliqueAlert(
  visualizacoes: number | null | undefined,
  cliquesWhatsapp: number | null | undefined,
  minViews = STRATEGIC_ALERT_ALTO_TRAFEGO_ZERO_MIN_VIEWS
): boolean {
  const v = Number(visualizacoes ?? 0)
  const c = Number(cliquesWhatsapp ?? 0)
  return v >= minViews && c === 0
}

export function countAltoTrafegoZeroClique(
  items: AdminPerformanceItem[],
  minViews = STRATEGIC_ALERT_ALTO_TRAFEGO_ZERO_MIN_VIEWS
): number {
  return items.filter((i) => matchesAltoTrafegoZeroCliqueAlert(i.visualizacoes, i.cliquesWhatsapp, minViews)).length
}

/** Alinhado aos cards de monetização do dashboard estratégico (views acumuladas na API de performance). */
export const STRATEGIC_ALTO_TRAFEGO_MIN_VIEWS = 2000

/** Heurística “possível ineficiência”: volume mínimo + conversão abaixo de 1% (igual ao card). */
export const STRATEGIC_BAIXA_EFICIENCIA_MIN_VIEWS = 300
export const STRATEGIC_BAIXA_EFICIENCIA_MAX_CONV_PCT = 1

export function matchesStrategicAltoTrafego(visualizacoes: number | null | undefined): boolean {
  return Number(visualizacoes ?? 0) >= STRATEGIC_ALTO_TRAFEGO_MIN_VIEWS
}

/** Usa views e cliques do próprio registro (lista staff ou performance). */
export function matchesStrategicBaixaEficiencia(
  visualizacoes: number | null | undefined,
  cliquesWhatsapp: number | null | undefined
): boolean {
  const v = Number(visualizacoes ?? 0)
  if (v < STRATEGIC_BAIXA_EFICIENCIA_MIN_VIEWS) return false
  const c = Number(cliquesWhatsapp ?? 0)
  const pct = v <= 0 ? 0 : (c / v) * 100
  return pct < STRATEGIC_BAIXA_EFICIENCIA_MAX_CONV_PCT
}

/** Cidades com conversão agregada abaixo da média global (apenas com volume mínimo). */
export function cidadeAbaixoDaMediaResumo(
  cityRows: CidadeAggRow[],
  minAnuncios = 3
): { nome: string; conversao: number; media: number } | null {
  if (cityRows.length === 0) return null
  const tot = cityRows.reduce(
    (a, r) => ({ v: a.v + r.views, c: a.c + r.cliques }),
    { v: 0, c: 0 }
  )
  const media = tot.v <= 0 ? 0 : (tot.c / tot.v) * 100
  const candidatos = cityRows.filter((r) => r.ativos >= minAnuncios && r.conversao < media * 0.85)
  if (candidatos.length === 0) return null
  const pior = [...candidatos].sort((a, b) => a.conversao - b.conversao)[0]
  return { nome: pior.cidade, conversao: pior.conversao, media: Math.round(media * 100) / 100 }
}
