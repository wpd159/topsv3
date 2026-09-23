'use client'

import { useCallback, useEffect, useReducer, useRef, useState } from 'react'
import { isValidCpf } from '@/lib/cpf-mask'
import {
  clearWizardCache,
  discardUnsafeLegacyWizardCache,
  loadWizardCache,
  saveWizardCache,
  wizardCacheKey,
  type WizardCacheScope,
} from './wizard-storage'
import { initialWizardState, wizardStepIds, type EditPendingMedia, type WizardFormState, type WizardKycState, type WizardState, type WizardStepId } from './types'

type Action =
  | { type: 'hydrate'; payload: WizardState }
  | { type: 'patch-form'; payload: Partial<WizardFormState> }
  | { type: 'patch-kyc'; payload: Partial<WizardKycState> }
  | { type: 'set-step'; payload: WizardStepId }
  | { type: 'set-fotos'; payload: File[] }
  | { type: 'set-videos'; payload: File[] }
  | { type: 'set-edit-pending-media'; payload: { scope: string; files: EditPendingMedia[]; unconfirmed: boolean } }
  | { type: 'set-edit-upload-unconfirmed'; payload: boolean }
  | { type: 'set-documentos'; payload: File[] }
  | { type: 'reset' }

export const wizardSteps: Array<{ id: WizardStepId; title: string; eyebrow: string }> = [
  { id: 'perfil', title: 'Perfil do anúncio', eyebrow: 'Comece pelo essencial' },
  { id: 'localizacao', title: 'Área de atendimento', eyebrow: 'Região de atendimento' },
  { id: 'servicos', title: 'Atendimento', eyebrow: 'Serviços e experiência' },
  { id: 'fotos', title: 'Fotos', eyebrow: 'O anúncio ganha forma' },
  { id: 'revisao', title: 'Revise seu anúncio', eyebrow: 'Revise antes de avançar' },
  { id: 'premium', title: 'Escolha como deseja publicar', eyebrow: 'Publicação do anúncio' },
  { id: 'kyc', title: 'Confirmação de identidade', eyebrow: 'Última etapa' },
]

function reducer(state: WizardState, action: Action): WizardState {
  switch (action.type) {
    case 'hydrate':
      return action.payload
    case 'patch-form':
      return { ...state, form: { ...state.form, ...action.payload } }
    case 'patch-kyc':
      return { ...state, kyc: { ...state.kyc, ...action.payload } }
    case 'set-step':
      return { ...state, currentStep: action.payload }
    case 'set-fotos':
      return {
        ...state,
        form: {
          ...state.form,
          fotos: action.payload,
          fotoNomes: action.payload.map((file) => file.name),
        },
      }
    case 'set-videos':
      return {
        ...state,
        form: {
          ...state.form,
          videos: action.payload,
          videoNomes: action.payload.map((file) => file.name),
        },
      }
    case 'set-edit-pending-media':
      return {
        ...state,
        form: {
          ...state.form,
          editPendingMedia: action.payload.files,
          editPendingMediaScope: action.payload.scope,
          editUploadUnconfirmed: action.payload.unconfirmed,
        },
      }
    case 'set-edit-upload-unconfirmed':
      return { ...state, form: { ...state.form, editUploadUnconfirmed: action.payload } }
    case 'set-documentos':
      return {
        ...state,
        kyc: {
          ...state.kyc,
          documentos: action.payload,
          documentoNomes: action.payload.map((file) => file.name),
        },
      }
    case 'reset':
      return initialWizardState
    default:
      return state
  }
}

export function stepIndexFromId(step: WizardStepId) {
  return Math.max(0, wizardStepIds.indexOf(step))
}

// Mirrors the backend's default Java regex classes and String.trim() before
// comparing UTF-16 code units. The submitted text itself is never truncated.
export function normalizeEditDescription(value: string) {
  return value.replace(/[\x00-\x1f\x7f]/g, ' ')
    .replace(/[ \t\n\v\f\r]+/g, ' ')
    .replace(/^[\x00-\x20]+|[\x00-\x20]+$/g, '')
}

export function validateWizardStep(
  state: WizardState,
  step: WizardStepId,
  mode: 'create' | 'edit' = 'create'
): string | null {
  const { form } = state
  if (step === 'perfil') {
    const tituloLength = form.titulo.trim().length
    if (tituloLength < 10 || tituloLength > 80) {
      return 'O nome do anúncio deve ter entre 10 e 80 caracteres.'
    }
    if (!form.categoria) {
      return 'Escolha uma categoria para continuar.'
    }
    const descricao = form.descricao.trim() || form.descricaoPerfil.trim()
    if (mode === 'create' && (descricao.length < 20 || descricao.length > 500)) {
      return 'A descrição deve ter entre 20 e 500 caracteres.'
    }
  }
  if (step === 'localizacao') {
    if (!form.estadoId || !form.cidadeId || (mode === 'create' && !form.bairroId)) {
      return 'Escolha estado, cidade e bairro para continuar.'
    }
  }
  if (step === 'servicos') {
    const preco = Number(form.preco.replace(/\D/g, '')) / 100
    const editDescriptionLength = mode === 'edit' ? normalizeEditDescription(form.descricao).length : 0
    if (form.atendimentoExclusivamenteVirtual && !form.servicos.includes('VIDEOCHAMADA')) {
      return 'Atendimento exclusivamente virtual exige o serviço Atendimento Virtual.'
    }
    if (mode === 'edit' && (editDescriptionLength < 20 || editDescriptionLength > 500)) {
      return 'A descrição deve ter entre 20 e 500 caracteres.'
    }
    if (
      !Number.isFinite(preco) ||
      preco <= 0 ||
      (mode === 'create' && !form.horario) ||
      form.locaisAtendimento.length === 0 ||
      form.servicos.length === 0
    ) {
      return mode === 'create'
        ? 'Informe preço, horário, local de atendimento e ao menos um serviço.'
        : 'Informe preço, descrição, local de atendimento e ao menos um serviço.'
    }
  }
  if (mode === 'create' && step === 'fotos' && form.fotos.length === 0) {
    return form.fotoNomes.length > 0
      ? 'Selecione novamente as fotos antes de publicar. Arquivos locais não ficam salvos no navegador.'
      : 'Envie ao menos uma foto para seguir.'
  }
  return null
}

export function validateWizardKycState(
  state: WizardState,
  persisted?: { nomeCivil: boolean; cpf: boolean; dataNascimento: boolean }
): string | null {
  const { kyc } = state
  if (
    (!persisted?.nomeCivil && !kyc.nomeCompleto.trim()) ||
    (!persisted?.dataNascimento && !kyc.dataNascimento) ||
    (!persisted?.cpf && !isValidCpf(kyc.cpf)) ||
    !kyc.documentoModo ||
    kyc.documentos.length < 1
  ) {
    return 'Preencha os dados obrigatórios.'
  }
  return null
}

type WizardStoreOptions = {
  cacheScope: WizardCacheScope | null
  backendFirst?: boolean
}

export function useAnuncioWizardStore({ cacheScope, backendFirst = false }: WizardStoreOptions) {
  const [state, dispatch] = useReducer(reducer, initialWizardState)
  const [hydrated, setHydrated] = useState(false)
  const [cacheActive, setCacheActive] = useState(false)
  const [lastSavedAt, setLastSavedAt] = useState<string | null>(null)
  const sourceVersionRef = useRef<string | null>(null)
  const cacheKey = cacheScope ? wizardCacheKey(cacheScope) : null

  useEffect(() => {
    sourceVersionRef.current = null
    setLastSavedAt(null)
    setCacheActive(Boolean(cacheScope))
    discardUnsafeLegacyWizardCache()

    if (!cacheScope) {
      dispatch({ type: 'reset' })
      setHydrated(true)
      return
    }
    if (backendFirst) {
      dispatch({ type: 'reset' })
      setHydrated(false)
      return
    }

    const cached = loadWizardCache(cacheScope)
    if (cached) dispatch({ type: 'hydrate', payload: cached.state })
    else dispatch({ type: 'reset' })
    if (cached) {
      sourceVersionRef.current = cached.sourceVersion
      setLastSavedAt(new Date(cached.savedAt).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }))
    }
    setHydrated(true)
  }, [backendFirst, cacheKey, cacheScope])

  useEffect(() => {
    if (!hydrated || !cacheActive || !cacheScope) return
    const timer = window.setTimeout(() => {
      saveWizardCache(cacheScope, state, sourceVersionRef.current)
      setLastSavedAt(new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }))
    }, 500)
    return () => window.clearTimeout(timer)
  }, [cacheActive, cacheKey, cacheScope, hydrated, state])

  const currentIndex = stepIndexFromId(state.currentStep)

  const setStep = useCallback((step: WizardStepId) => {
    dispatch({ type: 'set-step', payload: step })
  }, [])

  const nextStep = useCallback(() => {
    const next = wizardStepIds[Math.min(currentIndex + 1, wizardStepIds.length - 1)]
    dispatch({ type: 'set-step', payload: next })
  }, [currentIndex])

  const previousStep = useCallback(() => {
    const previous = wizardStepIds[Math.max(currentIndex - 1, 0)]
    dispatch({ type: 'set-step', payload: previous })
  }, [currentIndex])

  const clearCurrentCache = useCallback(() => {
    setCacheActive(false)
    if (cacheScope) clearWizardCache(cacheScope)
    setLastSavedAt(null)
  }, [cacheScope])

  const reset = useCallback(() => {
    clearCurrentCache()
    dispatch({ type: 'reset' })
  }, [clearCurrentCache])

  const hydrateFromBackend = useCallback((payload: WizardState, sourceVersion: string) => {
    const cached = cacheScope ? loadWizardCache(cacheScope) : null
    const canRestoreDraft = cached?.sourceVersion === sourceVersion
    if (cacheScope && cached && !canRestoreDraft) clearWizardCache(cacheScope)
    sourceVersionRef.current = sourceVersion
    dispatch({ type: 'hydrate', payload: canRestoreDraft ? cached.state : payload })
    setCacheActive(Boolean(cacheScope))
    setLastSavedAt(canRestoreDraft
      ? new Date(cached.savedAt).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
      : null)
    setHydrated(true)
  }, [cacheScope])

  const updateForm = useCallback((payload: Partial<WizardFormState>) => {
    dispatch({ type: 'patch-form', payload })
  }, [])
  const updateKyc = useCallback((payload: Partial<WizardKycState>) => {
    dispatch({ type: 'patch-kyc', payload })
  }, [])
  const setFotos = useCallback((payload: File[]) => dispatch({ type: 'set-fotos', payload }), [])
  const setVideos = useCallback((payload: File[]) => dispatch({ type: 'set-videos', payload }), [])
  const setEditPendingMedia = useCallback((scope: string, files: EditPendingMedia[], unconfirmed = false) => {
    dispatch({ type: 'set-edit-pending-media', payload: { scope, files, unconfirmed } })
  }, [])
  const setEditUploadUnconfirmed = useCallback((value: boolean) => {
    dispatch({ type: 'set-edit-upload-unconfirmed', payload: value })
  }, [])
  const setDocumentos = useCallback((payload: File[]) => dispatch({ type: 'set-documentos', payload }), [])

  return {
    state,
    hydrated,
    lastSavedAt,
    currentIndex,
    currentStep: state.currentStep,
    updateForm,
    updateKyc,
    setFotos,
    setVideos,
    setEditPendingMedia,
    setEditUploadUnconfirmed,
    setDocumentos,
    setStep,
    nextStep,
    previousStep,
    reset,
    clearCurrentCache,
    hydrateFromBackend,
  }
}
