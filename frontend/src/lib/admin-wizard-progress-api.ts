export type WizardProgressDashboardResponse = {
  emPreenchimento: number
  aguardandoModeracao: number
  publicados: number
  rejeitados: number
  itens: Array<{
    id: number
    usuarioId: number
    usuario: string
    email?: string | null
    telefone?: string | null
    modo: string
    status: 'EM_PREENCHIMENTO' | 'AGUARDANDO_MODERACAO' | 'PUBLICADO' | 'REJEITADO'
    ultimoStep: string
    concluido: boolean
    anuncioId?: number | null
    anuncioSlug?: string | null
    createdAt: string
    updatedAt: string
  }>
}

const API_URL = process.env.NEXT_PUBLIC_API_URL || ''

async function readAdminApiError(response: Response, fallback: string) {
  const raw = await response.text().catch(() => '')
  if (!raw.trim()) return `${fallback} (HTTP ${response.status})`

  try {
    const parsed = JSON.parse(raw) as { message?: string; error?: string }
    return parsed.message || parsed.error || `${fallback} (HTTP ${response.status})`
  } catch {
    return raw.trim() || `${fallback} (HTTP ${response.status})`
  }
}

export async function fetchWizardProgressDashboard() {
  const response = await fetch(`${API_URL}/admin/wizard-progress/dashboard`, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    cache: 'no-store',
  })

  if (!response.ok) {
    throw new Error(await readAdminApiError(response, 'Falha ao carregar progresso do wizard'))
  }

  return (await response.json()) as WizardProgressDashboardResponse
}
