import { corrigirEstruturaTexto } from '@/lib/text/encoding'

const apiBase = () => {
  const base = (process.env.NEXT_PUBLIC_API_URL || '').replace(/\/$/, '')
  if (!base) throw new Error('NEXT_PUBLIC_API_URL não configurado.')
  return base
}

export type AdminPerformanceItem = {
  id: number
  titulo: string
  visualizacoes: number
  cliquesWhatsapp: number
  taxaConversao: number
  posicaoRanking?: number
  cidadeNome?: string | null
}

export type AdminPerformanceResponse = {
  totalVisualizacoes?: number
  totalCliquesWhatsapp?: number
  rankingPorCliques?: AdminPerformanceItem[]
  topPorConversao?: AdminPerformanceItem[]
}

export type DesempenhoDiarioPonto = {
  data: string
  visualizacoes: number
  cliquesWhatsapp: number
}

export type DesempenhoDiarioResponse = {
  serieDiaria: DesempenhoDiarioPonto[]
  hojeVisualizacoes?: number
  hojeCliquesWhatsapp?: number
  hojeConversaoPct?: number
  ontemVisualizacoes?: number
  ontemCliquesWhatsapp?: number
  ontemConversaoPct?: number
  varVisualizacoesPct?: number | null
  varCliquesPct?: number | null
}

export async function fetchAdminPerformanceAnuncios(): Promise<AdminPerformanceResponse | null> {
  const res = await fetch(`${apiBase()}/estatisticas/performance-anuncios`, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
  })
  if (!res.ok) return null
  return corrigirEstruturaTexto(await res.json()) as AdminPerformanceResponse
}

export async function fetchDesempenhoDiario(dias: 7 | 15 | 30): Promise<DesempenhoDiarioResponse | null> {
  const res = await fetch(`${apiBase()}/estatisticas/desempenho-diario?dias=${dias}`, {
    cache: 'no-store',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
  })
  if (!res.ok) return null
  return corrigirEstruturaTexto(await res.json()) as DesempenhoDiarioResponse
}

export type TopWhatsappHojeItem = {
  anuncioId: number
  titulo?: string | null
  cidadeNome?: string | null
  thumbnailUrl?: string | null
  cliquesWhatsappHoje: number
  slug?: string | null
}

/** Admin only — rota protegida em `/admin/**` no backend. */
export async function fetchTopWhatsappHoje(limit = 5): Promise<TopWhatsappHojeItem[] | null> {
  const res = await fetch(
    `${apiBase()}/admin/dashboard/anuncios-top-whatsapp-hoje?limit=${encodeURIComponent(String(limit))}`,
    {
      cache: 'no-store',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
    }
  )
  if (!res.ok) return null
  const raw = corrigirEstruturaTexto(await res.json()) as unknown
  if (!Array.isArray(raw)) return null
  return raw as TopWhatsappHojeItem[]
}
