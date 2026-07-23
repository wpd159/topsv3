import type { AdminAdFilters, AdminAdSituation } from './types'

export const ADMIN_AD_PAGE_SIZE_OPTIONS = [20, 30, 50, 100] as const

export const ADMIN_AD_SORT_OPTIONS = [
  { value: 'MAIS_RECENTES', label: 'Mais recentes' },
  { value: 'MAIS_ANTIGOS', label: 'Mais antigos' },
  { value: 'MAIS_VISUALIZACOES', label: 'Mais visualizações' },
  { value: 'MENOS_VISUALIZACOES', label: 'Menos visualizações' },
  { value: 'MAIS_CLIQUES_WHATSAPP', label: 'Mais cliques WhatsApp' },
  { value: 'MENOS_CLIQUES_WHATSAPP', label: 'Menos cliques WhatsApp' },
] as const

export type AdminAdSort = (typeof ADMIN_AD_SORT_OPTIONS)[number]['value']

export type AdminAdQueueContext = {
  page: number
  size: (typeof ADMIN_AD_PAGE_SIZE_OPTIONS)[number]
  situacao: AdminAdSituation
  ordenacao: AdminAdSort
  termo: string
  uf: string
  cidade: string
  bairro: string
}

type SearchParamsReader = { get(name: string): string | null }

function nonNegativeInteger(value: string | null, fallback: number) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : fallback
}

function pageSize(value: string | null): AdminAdQueueContext['size'] {
  const parsed = Number(value)
  return ADMIN_AD_PAGE_SIZE_OPTIONS.includes(parsed as AdminAdQueueContext['size'])
    ? parsed as AdminAdQueueContext['size']
    : 30
}

function sort(value: string | null): AdminAdSort {
  return ADMIN_AD_SORT_OPTIONS.some((option) => option.value === value)
    ? value as AdminAdSort
    : 'MAIS_RECENTES'
}

function situation(value: string | null): AdminAdSituation {
  return ['TODOS', 'PENDENTES_MODERACAO', 'PAUSADOS', 'REJEITADOS', 'BLOQUEADOS'].includes(value || '')
    ? value as AdminAdSituation
    : 'PENDENTES_MODERACAO'
}

export function parseAdminAdQueueContext(params: SearchParamsReader): AdminAdQueueContext {
  return {
    page: nonNegativeInteger(params.get('page'), 0),
    size: pageSize(params.get('size')),
    situacao: situation(params.get('situacao')),
    ordenacao: sort(params.get('ordenacao')),
    termo: params.get('termo')?.trim() || '',
    uf: params.get('uf')?.trim().toUpperCase().slice(0, 2) || '',
    cidade: params.get('cidade')?.trim() || '',
    bairro: params.get('bairro')?.trim() || '',
  }
}

export function adminAdQueueFilters(context: AdminAdQueueContext): AdminAdFilters {
  return {
    page: context.page,
    size: context.size,
    situacao: context.situacao,
    ordenacao: context.ordenacao,
    termo: context.termo || undefined,
    uf: context.uf || undefined,
    cidade: context.cidade || undefined,
    bairro: context.bairro || undefined,
  }
}

export function adminAdQueueSearch(context: AdminAdQueueContext) {
  const query = new URLSearchParams({
    fila: '1',
    page: String(context.page),
    size: String(context.size),
    situacao: context.situacao,
    ordenacao: context.ordenacao,
  })
  for (const [name, value] of Object.entries({
    termo: context.termo,
    uf: context.uf,
    cidade: context.cidade,
    bairro: context.bairro,
  })) {
    if (value) query.set(name, value)
  }
  return query.toString()
}

export function adminAdQueueListHref(context: AdminAdQueueContext) {
  return `/admin/anuncios?${adminAdQueueSearch(context)}`
}

export function adminAdQueueDetailHref(id: string, context: AdminAdQueueContext) {
  return `/admin/anuncios/${encodeURIComponent(id)}?${adminAdQueueSearch(context)}`
}
