export type PainelNavigationItem = {
  id: 'overview' | 'ads' | 'performance' | 'credits' | 'recommendations' | 'account'
  label: string
  href: string
  match: 'exact' | 'prefix' | 'anchor'
}

export const PAINEL_NAV_ITEMS = [
  { id: 'overview', label: 'Visão geral', href: '/painel', match: 'exact' },
  { id: 'ads', label: 'Meus anúncios', href: '/meus-anuncios', match: 'prefix' },
  { id: 'performance', label: 'Performance', href: '/painel/performance', match: 'prefix' },
  { id: 'credits', label: 'Créditos e planos', href: '/creditos', match: 'prefix' },
  { id: 'recommendations', label: 'Recomendações', href: '/painel#recomendacoes', match: 'anchor' },
  { id: 'account', label: 'Conta e configurações', href: '/minha-conta', match: 'prefix' },
] as const satisfies readonly PainelNavigationItem[]

export function isPainelNavigationItemActive(item: PainelNavigationItem, pathname: string) {
  if (item.match === 'anchor') return false
  if (item.match === 'exact') return pathname === item.href
  return pathname === item.href || pathname.startsWith(`${item.href}/`)
}
