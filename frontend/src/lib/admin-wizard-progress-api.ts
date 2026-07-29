import {
  adminApiUrl,
  apiErrorFromResponse,
  normalizeApiError,
} from '@/lib/api-contract'

export type WizardProgressPeriod = {
  inicio: string
  fim: string
  fusoHorario: 'America/Sao_Paulo'
  calculadoEm: string
}

export type WizardProgressIndicators = {
  sessoesObservadas: number
  usuariosObservados: number
  kycNaoIniciado: number
  kycPendente: number
  kycAprovado: number
  emPreenchimento: number
  aguardandoModeracao: number
  anunciosRascunho: number
  anunciosRejeitados: number
  anunciosPublicados: number
  tempoMedioConclusaoMinutos?: number | null
}

export type WizardProgressFunnelStep = {
  codigo: string
  rotulo: string
  quantidade: number
  perda: number
  conversaoPercentual: number
}

export type WizardProgressCurrentStep = {
  codigo: string
  rotulo: string
  quantidade: number
}

export type WizardProgressItem = {
  id: string
  usuarioId: string
  usuario: string
  emailMascarado?: string | null
  modo: 'CREATE' | 'EDIT'
  status: 'EM_PREENCHIMENTO' | 'AGUARDANDO_MODERACAO' | 'PUBLICADO' | 'REJEITADO'
  ultimoStep: string
  kycStatus: 'NAO_INICIADO' | 'PENDENTE' | 'APROVADO' | 'REJEITADO'
  anuncioId?: string | null
  anuncioSlug?: string | null
  anuncioTitulo?: string | null
  anuncioStatus?: string | null
  criadoEm: string
  atualizadoEm: string
}

export type WizardProgressDashboardResponse = {
  periodo: WizardProgressPeriod
  indicadores: WizardProgressIndicators
  funil: WizardProgressFunnelStep[]
  etapasAtuais: WizardProgressCurrentStep[]
  progresso: {
    itens: WizardProgressItem[]
    page: number
    size: number
    totalElements: number
    totalPages: number
    last: boolean
  }
}

export type WizardProgressFilters = {
  periodo: 'HOJE' | '7_DIAS' | '30_DIAS' | 'PERSONALIZADO'
  inicio?: string
  fim?: string
  termo?: string
  modo?: string
  status?: string
  uf?: string
  cidade?: string
  kyc?: string
  anuncioStatus?: string
  page?: number
  size?: number
}

export async function fetchWizardProgressDashboard(
  filters: WizardProgressFilters,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    params.set(key, String(value))
  })
  try {
    const response = await fetch(
      adminApiUrl(`/wizard-progress/dashboard?${params.toString()}`),
      {
        credentials: 'include',
        cache: 'no-store',
        signal,
      },
    )
    if (!response.ok) throw await apiErrorFromResponse(response)
    return (await response.json()) as WizardProgressDashboardResponse
  } catch (error) {
    throw normalizeApiError(error)
  }
}
