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

export async function fetchWizardProgressDashboard() {
  throw new BackendContractPendingError(PENDING_BACKEND_CONTRACTS.wizardProgress)
}
import { BackendContractPendingError, PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'
