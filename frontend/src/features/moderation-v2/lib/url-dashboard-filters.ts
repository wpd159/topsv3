/** Query params suportados na Moderação V2 a partir do dashboard estratégico. */

import type { PremiumBenefitDashboardFilter } from '@/lib/admin-premium-benefits-api'

export const MOD_V2_QUERY_FILTRO = 'filtro'
export const MOD_V2_QUERY_CIDADE = 'cidade'

export type DashboardStrategicFiltro =
  | 'sem-upsell'
  | 'alto-trafego'
  | 'baixa-eficiencia'
  | 'vencendo-em-breve'
  | 'com-views-sem-clique'
  | 'alto-trafego-zero-clique'

const FILTRO_VALUES = new Set<string>([
  'sem-upsell',
  'alto-trafego',
  'baixa-eficiencia',
  'vencendo-em-breve',
  'com-views-sem-clique',
  'alto-trafego-zero-clique',
])

export function parseDashboardStrategicFiltro(raw: string | null): DashboardStrategicFiltro | null {
  if (!raw) return null
  const v = raw.trim().toLowerCase()
  if (FILTRO_VALUES.has(v)) return v as DashboardStrategicFiltro
  return null
}

export function parseCidadeQuery(raw: string | null): string | null {
  if (raw == null) return null
  const t = raw.trim()
  return t || null
}

/** Filtros da API admin/premium-benefits/dashboard/anuncios (lista por IDs). */
export function premiumDashboardFilterForStrategic(
  filtro: DashboardStrategicFiltro | null
): PremiumBenefitDashboardFilter | null {
  if (filtro === 'sem-upsell') return 'SEM_UPSELL'
  if (filtro === 'vencendo-em-breve') return 'VENCENDO_EM_BREVE'
  return null
}

export function hasDashboardUrlDrilldown(
  filtro: DashboardStrategicFiltro | null,
  cidade: string | null
): boolean {
  return !!(filtro || cidade)
}
