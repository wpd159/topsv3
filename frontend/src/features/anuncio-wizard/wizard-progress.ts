'use client'

import { publicApiUrl } from '@/lib/api-contract'

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

const SESSION_PREFIX = 'topsdojob:wizard-progress:v1'
const CREATION_PREFIX = 'topsdojob:wizard-creation:v1'

export type WizardCreationOperation = {
  sessionId: string
  phase: 'PENDING' | 'CONFIRMED'
  anuncioId?: string
  slugLocal?: string
}

function creationMetadata(value: unknown): WizardCreationOperation {
  const item = value as Partial<WizardCreationOperation> | null
  if (!item || typeof item.sessionId !== 'string'
    || !/^[A-Za-z0-9][A-Za-z0-9._:-]{7,79}$/.test(item.sessionId)
    || !['PENDING', 'CONFIRMED'].includes(item.phase ?? '')
    || (item.phase === 'CONFIRMED' && (!item.anuncioId || !item.slugLocal
      || typeof item.anuncioId !== 'string' || typeof item.slugLocal !== 'string'))) {
    throw new Error('Não foi possível identificar a tentativa anterior. Abra Meus anúncios antes de continuar.')
  }
  // Only operation identity survives reload. Never persist form data or Files here.
  return item.phase === 'CONFIRMED'
    ? { sessionId: item.sessionId, phase: 'CONFIRMED', anuncioId: item.anuncioId, slugLocal: item.slugLocal }
    : { sessionId: item.sessionId, phase: 'PENDING' }
}

export function loadWizardCreationOperation(scope: string): WizardCreationOperation | null {
  const raw = window.sessionStorage.getItem(`${CREATION_PREFIX}:${encodeURIComponent(scope)}`)
  return raw === null ? null : creationMetadata(JSON.parse(raw))
}

export function saveWizardCreationOperation(scope: string, operation: WizardCreationOperation) {
  const key = `${CREATION_PREFIX}:${encodeURIComponent(scope)}`
  const value = JSON.stringify(creationMetadata(operation))
  try {
    window.sessionStorage.setItem(key, value)
    if (window.sessionStorage.getItem(key) !== value) throw new Error('metadata not retained')
  } catch {
    // Unlike best-effort progress, a durable operation key is required before CREATE.
    throw new Error('Não foi possível guardar a identidade desta tentativa. Permita o armazenamento da sessão e verifique a retomada antes de continuar.')
  }
}

export function clearWizardCreationOperation(scope: string) {
  try { window.sessionStorage.removeItem(`${CREATION_PREFIX}:${encodeURIComponent(scope)}`) } catch {
    // Keeping an old identity is safe: recovery must still consult the authenticated backend.
  }
}

function randomSessionId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `wiz-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

export function createWizardProgressSessionId(scope: string) {
  const key = `${SESSION_PREFIX}:${encodeURIComponent(scope)}`
  if (typeof window === 'undefined') return randomSessionId()
  try {
    const current = window.sessionStorage.getItem(key)
    if (current) return current
    const created = randomSessionId()
    window.sessionStorage.setItem(key, created)
    return created
  } catch {
    return randomSessionId()
  }
}

export function clearWizardProgressSessionId(scope: string) {
  if (typeof window === 'undefined') return
  try {
    window.sessionStorage.removeItem(`${SESSION_PREFIX}:${encodeURIComponent(scope)}`)
  } catch {
    // Observabilidade nao pode interromper a jornada principal.
  }
}

function csrfCookieName() {
  return ['XSRF', 'TOKEN'].join('-')
}

function csrfHeaderName() {
  return ['X', 'XSRF', 'TOKEN'].join('-')
}

function readCsrfValue() {
  if (typeof document === 'undefined') return null
  const name = csrfCookieName()
  const entry = document.cookie.split('; ').find((item) => item.startsWith(`${name}=`))
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : null
}

async function ensureCsrfValue() {
  const current = readCsrfValue()
  if (current) return current
  await fetch(publicApiUrl('/auth/me'), {
    credentials: 'include',
    cache: 'no-store',
  })
  return readCsrfValue()
}

export async function syncWizardProgress(input: {
  sessionId: string
  mode: WizardProgressMode
  ultimoStep: WizardProgressStep
  status?: WizardProgressStatus
  anuncioId?: number | string | null
}) {
  try {
    const csrf = await ensureCsrfValue()
    const response = await fetch(publicApiUrl('/wizard-progress/sync'), {
      method: 'POST',
      credentials: 'include',
      cache: 'no-store',
      headers: {
        'Content-Type': 'application/json',
        ...(csrf ? { [csrfHeaderName()]: csrf } : {}),
      },
      body: JSON.stringify(input),
    })
    if (!response.ok) return null
    return (await response.json()) as {
      id: string
      status: WizardProgressStatus
      ultimoStep: WizardProgressStep
      atualizadoEm: string
    }
  } catch {
    return null
  }
}
