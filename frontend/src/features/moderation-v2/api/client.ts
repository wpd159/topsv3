import { corrigirEstruturaTexto } from '@/lib/text/encoding'
import type {
  AdminAuditLogItem,
  ModerationMediaItem,
  ModerationAnuncioDetail,
  ModerationRevisionDetail,
  ModerationRevisionQueueItem,
  ModerationStaffListItem,
  VisitorVerificationAuditItem,
} from './types'

function apiBase() {
  const base = (process.env.NEXT_PUBLIC_API_URL || '').replace(/\/$/, '')
  if (!base) throw new Error('NEXT_PUBLIC_API_URL não configurado.')
  return base
}

export async function fetchStaffAnunciosList(): Promise<ModerationStaffListItem[]> {
  const res = await fetch(`${apiBase()}/anuncios/staff`, {
    credentials: 'include',
    cache: 'no-store',
  })
  if (!res.ok) throw new Error(`Falha ao carregar lista (${res.status})`)
  const data = await res.json()
  return corrigirEstruturaTexto(Array.isArray(data) ? data : [])
}

/** Fila oficial de moderação (revisões PENDENTE / EM_REVISAO) — não reinventar no cliente. */
export async function fetchStaffRevisions(): Promise<ModerationRevisionQueueItem[]> {
  const res = await fetch(`${apiBase()}/anuncios/staff/revisions`, {
    credentials: 'include',
    cache: 'no-store',
  })
  if (!res.ok) throw new Error(`Falha ao carregar fila de revisões (${res.status})`)
  const data = await res.json()
  return corrigirEstruturaTexto(Array.isArray(data) ? data : [])
}

export async function fetchStaffAnuncioDetail(id: number): Promise<ModerationAnuncioDetail> {
  const res = await fetch(`${apiBase()}/anuncios/staff/${id}`, {
    credentials: 'include',
    cache: 'no-store',
  })
  if (!res.ok) throw new Error(`Falha ao carregar anúncio (${res.status})`)
  return corrigirEstruturaTexto(await res.json()) as ModerationAnuncioDetail
}

export async function fetchStaffRevision(id: number): Promise<ModerationRevisionDetail | null> {
  const res = await fetch(`${apiBase()}/anuncios/staff/${id}/revision`, {
    credentials: 'include',
    cache: 'no-store',
  })
  if (res.status === 204) return null
  if (!res.ok) return null
  return corrigirEstruturaTexto(await res.json()) as ModerationRevisionDetail
}

export function notifyModerationDataUpdated() {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent('admin-revisions-updated'))
}

export async function approveAnuncioApi(
  id: number,
  reason: string
): Promise<Record<string, unknown>> {
  const res = await fetch(`${apiBase()}/anuncios/${id}/aprovar`, {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reason }),
  })
  if (!res.ok) {
    const t = await res.text().catch(() => '')
    throw new Error(t || `Aprovação falhou (${res.status})`)
  }
  return corrigirEstruturaTexto(await res.json()) as Record<string, unknown>
}

export async function rejectAnuncioApi(id: number, justificativa: string): Promise<Record<string, unknown>> {
  const res = await fetch(`${apiBase()}/anuncios/${id}/rejeitar`, {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ justificativa }),
  })
  if (!res.ok) {
    const t = await res.text().catch(() => '')
    throw new Error(t || `Rejeição falhou (${res.status})`)
  }
  return corrigirEstruturaTexto(await res.json()) as Record<string, unknown>
}

export async function alterarStatusStaffApi(
  id: number,
  status: 'ATIVO' | 'PAUSADO' | 'REJEITADO',
  justificativa?: string
): Promise<Record<string, unknown>> {
  const res = await fetch(`${apiBase()}/anuncios/staff/${id}/status`, {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status, justificativa: justificativa ?? '' }),
  })
  if (!res.ok) {
    const t = await res.text().catch(() => '')
    throw new Error(t || `Status falhou (${res.status})`)
  }
  return corrigirEstruturaTexto(await res.json()) as Record<string, unknown>
}

export async function decidirMidiaApi(
  id: string | number,
  acao: 'APROVAR' | 'REPROVAR' | 'SOLICITAR_AJUSTE',
  visibilidadeMidia?: 'LIVRE' | 'RESTRITA_18',
  motivo?: string
): Promise<Record<string, unknown>> {
  const res = await fetch(`${apiBase()}/api/admin/midias/${encodeURIComponent(String(id))}/decidir`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ acao, visibilidadeMidia, motivo: motivo?.trim() || undefined }),
  })
  if (!res.ok) {
    const t = await res.text().catch(() => '')
    throw new Error(t || `Decisão de mídia falhou (${res.status})`)
  }
  return corrigirEstruturaTexto(await res.json()) as Record<string, unknown>
}

export async function fetchAdminMidiasV3(): Promise<ModerationMediaItem[]> {
  const res = await fetch(`${apiBase()}/api/admin/midias?page=0&size=100`, {
    credentials: 'include',
    cache: 'no-store',
  })
  if (!res.ok) throw new Error(`Falha ao carregar mídias (${res.status})`)
  const data = corrigirEstruturaTexto(await res.json()) as { itens?: ModerationMediaItem[] }
  return Array.isArray(data.itens) ? data.itens : []
}

export async function fetchComplianceAuditForAnuncio(anuncioId: number): Promise<AdminAuditLogItem[]> {
  const q = new URLSearchParams({
    entityType: 'ANUNCIO',
    entityId: String(anuncioId),
  })
  const res = await fetch(`${apiBase()}/admin/compliance/audit-logs?${q.toString()}`, {
    credentials: 'include',
    cache: 'no-store',
  })
  if (res.status === 403) throw new Error('Sem permissão para auditoria.')
  if (!res.ok) throw new Error(`Auditoria (${res.status})`)
  const data = await res.json()
  return corrigirEstruturaTexto(Array.isArray(data) ? data : []) as AdminAuditLogItem[]
}

export async function fetchVisitorVerificationEventsForAnuncio(
  anuncioId: number
): Promise<VisitorVerificationAuditItem[]> {
  const q = new URLSearchParams({ anuncioId: String(anuncioId) })
  const res = await fetch(`${apiBase()}/admin/compliance/visitor-events?${q.toString()}`, {
    credentials: 'include',
    cache: 'no-store',
  })
  if (res.status === 403) throw new Error('Sem permissão para eventos de verificação.')
  if (!res.ok) throw new Error(`Eventos de verificação (${res.status})`)
  const data = await res.json()
  return corrigirEstruturaTexto(Array.isArray(data) ? data : []) as VisitorVerificationAuditItem[]
}

export async function removerAnuncioLogicamenteStaffApi(anuncioId: number, motivo: string): Promise<void> {
  const q = new URLSearchParams()
  if (motivo.trim()) q.set('motivo', motivo.trim())
  const res = await fetch(`${apiBase()}/anuncios/staff/${anuncioId}?${q.toString()}`, {
    method: 'DELETE',
    credentials: 'include',
  })
  if (!res.ok) {
    const t = await res.text().catch(() => '')
    throw new Error(t || `Remoção falhou (${res.status})`)
  }
}

export async function fetchPremiumAnuncioDetailAdmin(anuncioId: number): Promise<Record<string, unknown>> {
  const res = await fetch(`${apiBase()}/admin/premium-benefits/anuncios/${anuncioId}`, {
    credentials: 'include',
    cache: 'no-store',
  })
  if (res.status === 403) throw new Error('Sem permissão (apenas administrador).')
  if (!res.ok) throw new Error(`Benefícios (${res.status})`)
  return corrigirEstruturaTexto(await res.json()) as Record<string, unknown>
}

export async function removerFotosStaffApi(anuncioId: number, fotosParaRemover: string[]): Promise<void> {
  const res = await fetch(`${apiBase()}/anuncios/staff/${anuncioId}/fotos/remover`, {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ fotosParaRemover }),
  })
  if (!res.ok) {
    const t = await res.text().catch(() => '')
    throw new Error(t || `Remoção de fotos falhou (${res.status})`)
  }
}

export async function removerMidiaRevisaoStaffApi(
  anuncioId: number,
  mediaItemId: number
): Promise<Record<string, unknown>> {
  const res = await fetch(`${apiBase()}/anuncios/staff/${anuncioId}/revision/media/${mediaItemId}`, {
    method: 'DELETE',
    credentials: 'include',
  })
  if (!res.ok) {
    const t = await res.text().catch(() => '')
    throw new Error(t || `Remoção de mídia da revisão falhou (${res.status})`)
  }
  return corrigirEstruturaTexto(await res.json()) as Record<string, unknown>
}

export async function ativarPremiumBeneficioAdmin(
  anuncioId: number,
  body: { codigo: string; motivo?: string; observacaoInterna?: string }
): Promise<Record<string, unknown>> {
  const res = await fetch(`${apiBase()}/admin/premium-benefits/anuncios/${anuncioId}/ativar`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      codigo: body.codigo,
      motivo: body.motivo ?? 'Ativação manual — moderação v2',
      observacaoInterna: body.observacaoInterna ?? '',
    }),
  })
  if (!res.ok) {
    const t = await res.text().catch(() => '')
    throw new Error(t || `Ativação falhou (${res.status})`)
  }
  return corrigirEstruturaTexto(await res.json()) as Record<string, unknown>
}

/** Id da ativação (ou id sintético negativo para destaque manual — mesmo contrato do backend). */
export async function desativarPremiumBeneficioAdmin(
  ativacaoId: number,
  body?: { motivo?: string; observacaoInterna?: string }
): Promise<Record<string, unknown>> {
  const res = await fetch(`${apiBase()}/admin/premium-benefits/ativacoes/${ativacaoId}/desativar`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      motivo: body?.motivo ?? 'Desativação manual — moderação v2',
      observacaoInterna: body?.observacaoInterna ?? '',
    }),
  })
  if (!res.ok) {
    const t = await res.text().catch(() => '')
    throw new Error(t || `Desativação falhou (${res.status})`)
  }
  return corrigirEstruturaTexto(await res.json()) as Record<string, unknown>
}
