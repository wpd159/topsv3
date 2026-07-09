'use client'

import { useCallback, useEffect, useReducer, useState } from 'react'
import { clearWizardCache, loadWizardCache, saveWizardCache } from './wizard-storage'
import { initialWizardState, wizardStepIds, type WizardFormState, type WizardKycState, type WizardState, type WizardStepId } from './types'

type Action =
  | { type: 'hydrate'; payload: WizardState }
  | { type: 'patch-form'; payload: Partial<WizardFormState> }
  | { type: 'patch-kyc'; payload: Partial<WizardKycState> }
  | { type: 'set-step'; payload: WizardStepId }
  | { type: 'set-fotos'; payload: File[] }
  | { type: 'set-documentos'; payload: File[] }
  | { type: 'reset' }

export const wizardSteps: Array<{ id: WizardStepId; title: string; eyebrow: string }> = [
  { id: 'perfil', title: 'Perfil do anúncio', eyebrow: 'Comece pelo essencial' },
  { id: 'localizacao', title: 'Área de atendimento', eyebrow: 'Localização contextual' },
  { id: 'servicos', title: 'Atendimento', eyebrow: 'Serviços e experiência' },
  { id: 'fotos', title: 'Fotos', eyebrow: 'O anúncio ganha forma' },
  { id: 'revisao', title: 'Seu anúncio está pronto', eyebrow: 'Revise antes de avançar' },
  { id: 'premium', title: 'Impulsione se quiser', eyebrow: 'Upgrade opcional' },
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

export function validateWizardStep(state: WizardState, step: WizardStepId, perfilCompleto = false): string | null {
  const { form, kyc } = state
  if (step === 'perfil') {
    if (form.titulo.trim().length < 3 || !form.categoria) {
      return 'Preencha nome e categoria para continuar.'
    }
  }
  if (step === 'localizacao') {
    if (!form.estadoId || !form.cidadeId || !form.bairroId) {
      return 'Escolha estado, cidade e bairro para continuar.'
    }
  }
  if (step === 'servicos') {
    const preco = Number(form.preco.replace(/\D/g, '')) / 100
    if (!Number.isFinite(preco) || preco <= 0 || !form.horario || form.locaisAtendimento.length === 0 || form.servicos.length === 0) {
      return 'Informe preço, horário, local de atendimento e ao menos um serviço.'
    }
  }
  if (step === 'fotos' && form.fotos.length === 0) {
    return form.fotoNomes.length > 0
      ? 'Selecione novamente as fotos antes de publicar. Arquivos locais não ficam salvos no navegador.'
      : 'Envie ao menos uma foto para seguir.'
  }
  return null
}

export function validateWizardKycState(state: WizardState): string | null {
  const { kyc } = state
  if (!kyc.nomeCompleto.trim() || !kyc.dataNascimento || kyc.cpf.replace(/\D/g, '').length !== 11 || kyc.documentos.length < 1) {
    return 'Complete nome real, nascimento, CPF e ao menos um documento.'
  }
  return null
}

export function useAnuncioWizardStore() {
  const [state, dispatch] = useReducer(reducer, initialWizardState)
  const [hydrated, setHydrated] = useState(false)
  const [lastSavedAt, setLastSavedAt] = useState<string | null>(null)

  useEffect(() => {
    const cached = loadWizardCache()
    if (cached) dispatch({ type: 'hydrate', payload: cached })
    setHydrated(true)
  }, [])

  useEffect(() => {
    if (!hydrated) return
    const timer = window.setTimeout(() => {
      saveWizardCache(state)
      setLastSavedAt(new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }))
    }, 500)
    return () => window.clearTimeout(timer)
  }, [hydrated, state])

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

  const reset = useCallback(() => {
    clearWizardCache()
    dispatch({ type: 'reset' })
    setLastSavedAt(null)
  }, [])

  return {
    state,
    hydrated,
    lastSavedAt,
    currentIndex,
    currentStep: state.currentStep,
    updateForm: (payload: Partial<WizardFormState>) => dispatch({ type: 'patch-form', payload }),
    updateKyc: (payload: Partial<WizardKycState>) => dispatch({ type: 'patch-kyc', payload }),
    setFotos: (payload: File[]) => dispatch({ type: 'set-fotos', payload }),
    setDocumentos: (payload: File[]) => dispatch({ type: 'set-documentos', payload }),
    setStep,
    nextStep,
    previousStep,
    reset,
  }
}
