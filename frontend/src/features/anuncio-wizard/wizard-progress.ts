'use client'

import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export type WizardProgressMode = 'create' | 'edit'
export type WizardProgressStep =
  | 'perfil'
  | 'localizacao'
  | 'servicos'
  | 'fotos'
  | 'revisao'
  | 'premium'
  | 'kyc'
  | 'concluido'
export type WizardProgressStatus =
  | 'EM_PREENCHIMENTO'
  | 'AGUARDANDO_MODERACAO'
  | 'PUBLICADO'
  | 'REJEITADO'

export function createWizardProgressSessionId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `wiz-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

export async function syncWizardProgress(input: {
  sessionId: string
  mode: WizardProgressMode
  ultimoStep: WizardProgressStep
  status?: WizardProgressStatus
  anuncioId?: number | string | null
}) {
  void input
  return {
    status: 'CONTRATO_BACKEND_AUSENTE' as const,
    module: PENDING_BACKEND_CONTRACTS.wizardProgress,
  }
}
