'use client'

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

function apiBase() {
  return process.env.NEXT_PUBLIC_API_URL || ''
}

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
  try {
    await fetch(`${apiBase()}/wizard-progress/sync`, {
      method: 'POST',
      credentials: 'include',
      keepalive: true,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        ...input,
        status: input.status || 'EM_PREENCHIMENTO',
      }),
    })
  } catch {
    // progresso do wizard nao pode quebrar UX
  }
}
