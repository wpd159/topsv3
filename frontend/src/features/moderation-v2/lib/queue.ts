import type { ModerationStaffListItem } from '../api/types'

export type ModerationListFilter = 'fila' | 'todos' | 'pendente' | 'ativo' | 'rejeitado' | 'revisao'

/** Ordenação client-side da fila (dados já carregados). Padrão: mais recentes (alinhado ao backend). */
export type ModerationListSort =
  | 'recent'
  | 'oldest'
  | 'views_desc'
  | 'views_asc'
  | 'wa_desc'
  | 'wa_asc'

function nMetric(v: unknown): number {
  if (typeof v === 'number' && Number.isFinite(v)) return Math.max(0, Math.floor(v))
  if (typeof v === 'string' && v.trim() !== '' && !Number.isNaN(Number(v))) {
    return Math.max(0, Math.floor(Number(v)))
  }
  return 0
}

function parseCreatedMs(r: ModerationStaffListItem): number {
  const t = new Date(r.dataCriacao ?? '').getTime()
  return Number.isNaN(t) ? 0 : t
}

/**
 * Reordena cópia da lista. Métricas ausentes/zeradas ordenam de forma estável (empate → mais recente).
 */
export function sortModerationRows(rows: ModerationStaffListItem[], sort: ModerationListSort): ModerationStaffListItem[] {
  const copy = [...rows]
  const v = (r: ModerationStaffListItem) => nMetric(r.visualizacoes)
  const w = (r: ModerationStaffListItem) => nMetric(r.cliquesWhatsapp)
  const tieRecent = (a: ModerationStaffListItem, b: ModerationStaffListItem) => parseCreatedMs(b) - parseCreatedMs(a)

  switch (sort) {
    case 'recent':
      copy.sort((a, b) => tieRecent(a, b))
      break
    case 'oldest':
      copy.sort((a, b) => parseCreatedMs(a) - parseCreatedMs(b))
      break
    case 'views_desc':
      copy.sort((a, b) => v(b) - v(a) || tieRecent(a, b))
      break
    case 'views_asc':
      copy.sort((a, b) => v(a) - v(b) || tieRecent(a, b))
      break
    case 'wa_desc':
      copy.sort((a, b) => w(b) - w(a) || tieRecent(a, b))
      break
    case 'wa_asc':
      copy.sort((a, b) => w(a) - w(b) || tieRecent(a, b))
      break
    default:
      copy.sort((a, b) => tieRecent(a, b))
  }
  return copy
}

/**
 * `revisionAnuncioIds` = anúncios com revisão aberta (GET /anuncios/staff/revisions).
 * Fila operacional: pendentes de aprovação OU com revisão na fila do backend.
 */
export function matchesFilter(
  row: ModerationStaffListItem,
  filter: ModerationListFilter,
  revisionAnuncioIds: Set<number>
): boolean {
  if (filter === 'todos') return true
  if (filter === 'fila') {
    if (row.removidoLogicamente) return false
    const st = (row.status || '').toUpperCase()
    if (st === 'PENDENTE') return true
    return revisionAnuncioIds.has(row.id)
  }
  if (filter === 'revisao') return revisionAnuncioIds.has(row.id)
  return (row.status || '').toUpperCase() === filter.toUpperCase()
}

export function paginate<T>(items: T[], page: number, pageSize: number): T[] {
  const p = Math.max(1, page)
  const start = (p - 1) * pageSize
  return items.slice(start, start + pageSize)
}

export function totalPages(count: number, pageSize: number): number {
  return Math.max(1, Math.ceil(count / pageSize))
}
