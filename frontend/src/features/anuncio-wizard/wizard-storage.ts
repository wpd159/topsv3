import { initialWizardState, wizardStepIds, type WizardFormState, type WizardKycState, type WizardState } from './types'

const STORAGE_PREFIX = 'topsdojob:anuncio-wizard:v3'
const UNSAFE_LEGACY_STORAGE_KEY = 'topsdojob:anuncio-wizard:v2'
const STORAGE_VERSION = 3

export type WizardCacheScope = {
  userId: string
  mode: 'create' | 'edit'
  slug?: string
}

export type WizardCacheEntry = {
  state: WizardState
  savedAt: number
  sourceVersion: string | null
}

type PersistedWizardState = {
  version: typeof STORAGE_VERSION
  savedAt: number
  sourceVersion: string | null
  state: {
    currentStep: WizardState['currentStep']
    form: Omit<WizardState['form'], 'fotos' | 'videos'> & { fotos?: never; videos?: never }
    kyc: Omit<WizardState['kyc'], 'documentos'> & { documentos?: never }
  }
}

type StoredWizardState = {
  currentStep?: unknown
  form?: Partial<Record<keyof WizardFormState, unknown>>
  kyc?: Partial<Record<keyof WizardKycState, unknown>>
}

const stepSet = new Set<string>(wizardStepIds)

function asString(value: unknown) {
  return typeof value === 'string' ? value : ''
}

function asStringArray(value: unknown) {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

function sanitizeState(input: StoredWizardState | null | undefined): WizardState {
  const form = input?.form
  const kyc = input?.kyc
  const rawStep = input?.currentStep
  const currentStep = rawStep === 'kyc'
    ? 'premium'
    : typeof rawStep === 'string' && stepSet.has(rawStep)
      ? (rawStep as WizardState['currentStep'])
      : 'perfil'

  return {
    currentStep,
    form: {
      ...initialWizardState.form,
      titulo: asString(form?.titulo),
      categoria: asString(form?.categoria),
      descricaoPerfil: asString(form?.descricaoPerfil),
      preco: asString(form?.preco),
      horario: asString(form?.horario),
      locaisAtendimento: asStringArray(form?.locaisAtendimento),
      servicos: asStringArray(form?.servicos),
      descricao: asString(form?.descricao),
      linkConteudo: asString(form?.linkConteudo),
      whatsapp: asString(form?.whatsapp),
      estadoId: asString(form?.estadoId),
      cidadeId: asString(form?.cidadeId),
      bairroId: asString(form?.bairroId),
      estadoNome: asString(form?.estadoNome),
      estadoUf: asString(form?.estadoUf),
      cidadeNome: asString(form?.cidadeNome),
      bairroNome: asString(form?.bairroNome),
      pontoReferenciaTexto: asString(form?.pontoReferenciaTexto),
      fotoNomes: asStringArray(form?.fotoNomes),
      videoNomes: asStringArray(form?.videoNomes),
      premiumChoice: form?.premiumChoice === 'destaque' ? 'destaque' : 'gratis',
      fotos: [],
      videos: [],
    },
    kyc: {
      ...initialWizardState.kyc,
      nomeCompleto: asString(kyc?.nomeCompleto),
      dataNascimento: asString(kyc?.dataNascimento),
      cpf: asString(kyc?.cpf),
      documentoNomes: asStringArray(kyc?.documentoNomes),
      documentos: [],
    },
  }
}

function toPersistedState(state: WizardState, sourceVersion: string | null): PersistedWizardState {
  const { fotos: _fotos, videos: _videos, ...form } = state.form
  const { documentos: _documentos, ...kyc } = state.kyc

  return {
    version: STORAGE_VERSION,
    savedAt: Date.now(),
    sourceVersion,
    state: {
      currentStep: state.currentStep,
      form,
      kyc,
    },
  }
}

export function wizardCacheKey(scope: WizardCacheScope) {
  const userId = scope.userId.trim()
  if (!userId) throw new Error('Escopo do rascunho exige usuário autenticado.')
  if (scope.mode === 'edit' && !scope.slug?.trim()) {
    throw new Error('Escopo de edição exige slug.')
  }
  const base = `${STORAGE_PREFIX}:${encodeURIComponent(userId)}:${scope.mode}`
  return scope.mode === 'edit' ? `${base}:${encodeURIComponent(scope.slug!.trim())}` : base
}

export function discardUnsafeLegacyWizardCache() {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.removeItem(UNSAFE_LEGACY_STORAGE_KEY)
  } catch {
    // Falha de storage não pode interromper o wizard.
  }
}

export function loadWizardCache(scope: WizardCacheScope): WizardCacheEntry | null {
  if (typeof window === 'undefined') return null
  try {
    const raw = window.localStorage.getItem(wizardCacheKey(scope))
    if (!raw) return null
    const parsed = JSON.parse(raw) as PersistedWizardState
    if (parsed?.version !== STORAGE_VERSION || typeof parsed.savedAt !== 'number') return null
    return {
      state: sanitizeState(parsed.state),
      savedAt: parsed.savedAt,
      sourceVersion: typeof parsed.sourceVersion === 'string' ? parsed.sourceVersion : null,
    }
  } catch {
    return null
  }
}

export function saveWizardCache(
  scope: WizardCacheScope,
  state: WizardState,
  sourceVersion: string | null
) {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(
      wizardCacheKey(scope),
      JSON.stringify(toPersistedState(state, sourceVersion))
    )
  } catch {
    // Falha de storage não pode interromper o wizard.
  }
}

export function clearWizardCache(scope: WizardCacheScope) {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.removeItem(wizardCacheKey(scope))
  } catch {
    // Falha de storage não pode interromper o wizard.
  }
}
