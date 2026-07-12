import { initialWizardState, wizardStepIds, type WizardFormState, type WizardKycState, type WizardState } from './types'

const STORAGE_KEY = 'topsdojob:anuncio-wizard:v2'
const STORAGE_VERSION = 2

type PersistedWizardState = {
  version: typeof STORAGE_VERSION
  savedAt: number
  state: {
    currentStep: WizardState['currentStep']
    form: Omit<WizardState['form'], 'fotos'> & { fotos?: never }
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
      premiumChoice: form?.premiumChoice === 'destaque' ? 'destaque' : 'gratis',
      fotos: [],
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

function toPersistedState(state: WizardState): PersistedWizardState {
  const { fotos: _fotos, ...form } = state.form
  const { documentos: _documentos, ...kyc } = state.kyc

  return {
    version: STORAGE_VERSION,
    savedAt: Date.now(),
    state: {
      currentStep: state.currentStep,
      form,
      kyc,
    },
  }
}

export function loadWizardCache(): WizardState | null {
  if (typeof window === 'undefined') return null
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as PersistedWizardState
    if (parsed?.version !== STORAGE_VERSION) return null
    return sanitizeState(parsed.state)
  } catch {
    return null
  }
}

export function saveWizardCache(state: WizardState) {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(toPersistedState(state)))
  } catch {}
}

export function clearWizardCache() {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.removeItem(STORAGE_KEY)
  } catch {}
}
