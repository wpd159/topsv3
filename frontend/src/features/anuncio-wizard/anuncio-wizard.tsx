'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/context/AuthContext'
import { useLocalidades } from '@/hooks/useLocalidades'
import { cn } from '@/lib/utils'
import {
  completeWizardKyc,
  submitWizardAnuncio,
  updateWizardProfileDescription,
} from './api'
import {
  categorias,
  stepCopy,
  type SearchableSelectOption,
} from './wizard-constants'
import {
  buildPublishGuard,
  calculateAge,
  closedPublishGuard,
  hasPersistedKyc,
  type PublishGuardState,
} from './wizard-utils'
import {
  useAnuncioWizardStore,
  validateWizardKycState,
  validateWizardStep,
  wizardSteps,
} from './use-anuncio-wizard-store'
import type { WizardKycState, WizardStepId } from './types'
import { WizardFinalReview } from './components/wizard-final-review'
import { WizardKycModal } from './components/wizard-kyc-modal'
import { WizardPreview } from './components/wizard-preview'
import { WizardStepFotos } from './components/wizard-step-fotos'
import { WizardStepLocalizacao } from './components/wizard-step-localizacao'
import { WizardStepPerfil } from './components/wizard-step-perfil'
import { WizardStepPremium } from './components/wizard-step-premium'
import { WizardStepServicos } from './components/wizard-step-servicos'
import {
  createWizardProgressSessionId,
  syncWizardProgress,
  type WizardProgressStatus,
  type WizardProgressStep,
} from './wizard-progress'

export default function AnuncioWizard() {
  const router = useRouter()
  const { usuario, carregando, refresh } = useAuth()
  const API = process.env.NEXT_PUBLIC_API_URL || ''
  const localidades = useLocalidades(API)
  const store = useAnuncioWizardStore()
  const {
    state: wizardState,
    hydrated,
    lastSavedAt,
    currentIndex,
    updateForm,
    updateKyc,
    setFotos,
    setDocumentos,
    setStep,
    nextStep,
    previousStep,
    reset,
  } = store

  const state = wizardState.form
  const kyc = wizardState.kyc
  const currentStep = wizardSteps[currentIndex]
  const [publishing, setPublishing] = useState(false)
  const [fotoPreviewUrls, setFotoPreviewUrls] = useState<string[]>([])
  const [publishGuard, setPublishGuard] = useState<PublishGuardState>(closedPublishGuard)
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewHintDismissed, setPreviewHintDismissed] = useState(false)
  const wizardTopRef = useRef<HTMLDivElement | null>(null)
  const publishLockRef = useRef(false)
  const stepDidMountRef = useRef(false)
  const wizardSessionIdRef = useRef(createWizardProgressSessionId())
  const lastSyncedStepRef = useRef<WizardProgressStep | null>(null)

  const idade = calculateAge(kyc.dataNascimento || (usuario as any)?.dataNascimento)
  const hasExistingKyc = hasPersistedKyc(usuario)
  const previewTitle = state.titulo.trim() || 'Seu nome em destaque'
  const profileDescription = state.descricaoPerfil.trim()
  const previewLocation = [state.cidadeNome || 'Cidade', state.bairroNome || 'Bairro']
    .filter(Boolean)
    .join(' · ')
  const previewReference = state.pontoReferenciaTexto.trim()
  const hasVirtual = state.servicos.includes('VIDEOCHAMADA') || state.categoria === 'VENDA_DE_CONTEUDO'
  const quietPreview = currentStep.id === 'perfil' || currentStep.id === 'localizacao'
  const highlightedPreview =
    currentStep.id === 'fotos' || currentStep.id === 'revisao' || currentStep.id === 'premium'
  const selectedStateLabel = state.estadoNome
    ? `${state.estadoNome}${state.estadoUf ? ` · ${state.estadoUf}` : ''}`
    : 'Selecione o estado'
  const persistedProfileDescription = String((usuario as any)?.descricao || '').trim()
  const descricaoPerfilCount = state.descricaoPerfil.trim().length
  const descricaoPerfilNeedsMore = descricaoPerfilCount > 0 && descricaoPerfilCount < 20
  const descricaoPerfilRemaining = Math.max(0, 20 - descricaoPerfilCount)
  const previewDescription =
    state.descricao.trim() ||
    profileDescription ||
    'Seu texto de apresentação aparece aqui para aproximar o preview do anúncio real.'
  const previewPrice = state.preco.trim() || 'Consulte valores'
  const previewMedia = fotoPreviewUrls.length ? fotoPreviewUrls : []
  const syncProgress = (
    ultimoStep: WizardProgressStep,
    status: WizardProgressStatus = 'EM_PREENCHIMENTO',
    anuncioId?: number | string | null
  ) => {
    lastSyncedStepRef.current = ultimoStep
    return syncWizardProgress({
      sessionId: wizardSessionIdRef.current,
      mode: 'create',
      ultimoStep,
      status,
      anuncioId,
    })
  }

  useEffect(() => {
    const urls = state.fotos.map((file) => URL.createObjectURL(file))
    setFotoPreviewUrls(urls)
    return () => urls.forEach((url) => URL.revokeObjectURL(url))
  }, [state.fotos])

  useEffect(() => {
    if (!hydrated) return
    if (state.estadoId) void localidades.loadCidades(state.estadoId)
    if (state.cidadeId) void localidades.loadBairros(state.cidadeId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hydrated])

  useEffect(() => {
    if (!hydrated) return
    if (!persistedProfileDescription) return
    if (state.descricaoPerfil.trim().length > 0) return
    updateForm({ descricaoPerfil: persistedProfileDescription })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hydrated, persistedProfileDescription])

  useEffect(() => {
    if (!hydrated) return
    if (!stepDidMountRef.current) {
      stepDidMountRef.current = true
      return
    }

    window.requestAnimationFrame(() => {
      wizardTopRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  }, [currentIndex, hydrated])

  useEffect(() => {
    if (!hydrated) return
    if (!usuario) return
    const stepId = currentStep.id as WizardProgressStep
    if (lastSyncedStepRef.current === stepId) return
    syncProgress(stepId)
  }, [currentStep.id, hydrated, usuario])

  useEffect(() => {
    if (!publishGuard.open || hasExistingKyc) return
    if (lastSyncedStepRef.current === 'kyc') return
    syncProgress('kyc')
  }, [publishGuard.open, hasExistingKyc])

  const categoriaLabel = useMemo(
    () => categorias.find((item) => item.value === state.categoria)?.label || 'Categoria',
    [state.categoria]
  )

  const stateOptions = useMemo<SearchableSelectOption[]>(
    () =>
      localidades.estados.map((item) => ({
        id: String(item.id),
        label: item.nome,
        searchLabel: `${item.nome} ${item.uf ?? ''}`,
        subtitle: item.uf ? `UF ${item.uf}` : undefined,
      })),
    [localidades.estados]
  )

  const cidadeOptions = useMemo<SearchableSelectOption[]>(
    () =>
      localidades.cidades.map((item) => ({
        id: String(item.id),
        label: item.nome,
        searchLabel: `${item.nome} ${state.estadoNome} ${state.estadoUf}`,
      })),
    [localidades.cidades, state.estadoNome, state.estadoUf]
  )

  const bairroOptions = useMemo<SearchableSelectOption[]>(
    () =>
      localidades.bairros.map((item) => ({
        id: String(item.id),
        label: item.nome,
        searchLabel: `${item.nome} ${state.cidadeNome} ${state.estadoNome} ${state.estadoUf}`,
      })),
    [localidades.bairros, state.cidadeNome, state.estadoNome, state.estadoUf]
  )

  const toggle = (field: 'locaisAtendimento' | 'servicos', value: string) => {
    const set = new Set(state[field])
    if (set.has(value)) set.delete(value)
    else set.add(value)
    if (field === 'locaisAtendimento') updateForm({ locaisAtendimento: Array.from(set) })
    else updateForm({ servicos: Array.from(set) })
  }

  const handleEstado = (value: string) => {
    const estado = localidades.estados.find((item) => String(item.id) === value)
    updateForm({
      estadoId: value,
      estadoNome: estado?.nome ?? '',
      estadoUf: estado?.uf ?? '',
      cidadeId: '',
      cidadeNome: '',
      bairroId: '',
      bairroNome: '',
    })
    localidades.setCidades([])
    localidades.setBairros([])
    void localidades.loadCidades(value)
  }

  const handleCidade = (value: string) => {
    const cidade = localidades.cidades.find((item) => String(item.id) === value)
    updateForm({
      cidadeId: value,
      cidadeNome: cidade?.nome ?? '',
      bairroId: '',
      bairroNome: '',
    })
    localidades.setBairros([])
    void localidades.loadBairros(value)
  }

  const handleBairro = (value: string) => {
    const bairro = localidades.bairros.find((item) => String(item.id) === value)
    updateForm({ bairroId: value, bairroNome: bairro?.nome ?? '' })
  }

  const validateCurrentStep = () => {
    const message = validateWizardStep(wizardState, currentStep.id, hasExistingKyc)
    if (message) {
      toast.warning(message)
      return false
    }
    return true
  }

  const canMoveToStep = (targetIndex: number) => {
    if (targetIndex <= currentIndex) return true
    for (let i = 0; i < targetIndex; i += 1) {
      const message = validateWizardStep(wizardState, wizardSteps[i].id, hasExistingKyc)
      if (message) {
        toast.warning(message)
        return false
      }
    }
    return true
  }

  const goToStep = (step: WizardStepId, index: number) => {
    if (!canMoveToStep(index)) return
    setStep(step)
  }

  const goNext = () => {
    if (!validateCurrentStep()) return
    nextStep()
  }

  const openPublishGuard = (
    config: Omit<Extract<PublishGuardState, { open: true }>, 'open'>
  ) => {
    setPublishGuard(buildPublishGuard(config))
  }

  const syncProfileDescriptionIfNeeded = async () => {
    if (!usuario?.email) return

    const nextDescription = state.descricaoPerfil.trim()
    if (nextDescription === persistedProfileDescription) return

    await updateWizardProfileDescription({
      email: usuario.email,
      descricaoPerfil: nextDescription,
    })
  }

  const submitAnuncio = async () => {
    if (!usuario?.id) {
      toast.error('Faça login para publicar.')
      return
    }

    await syncProfileDescriptionIfNeeded()
    const created = await submitWizardAnuncio(state, usuario.id)
    await syncProgress('concluido', 'AGUARDANDO_MODERACAO', created?.id)
    await refresh().catch(() => null)
    setPublishGuard(closedPublishGuard)
    reset()
    toast.success('Anúncio enviado para moderação.')
    router.push('/meus-anuncios')
  }

  const completeKycAndPublish = async () => {
    if (!usuario?.email) {
      toast.error('Sessão inválida.')
      return
    }

    const message = validateWizardKycState(wizardState)
    if (message) {
      toast.warning(message)
      openPublishGuard({
        title: 'Sua conta ainda não foi verificada',
        description: message,
        actionLabel: 'Concluir cadastro e publicar',
      })
      return
    }

    await completeWizardKyc({
      email: usuario.email,
      nomeCompleto: kyc.nomeCompleto,
      dataNascimento: kyc.dataNascimento,
      cpf: kyc.cpf,
      estadoId: state.estadoId || String((usuario as any)?.estadoId || ''),
      cidadeId: state.cidadeId || String((usuario as any)?.cidadeId || ''),
      bairroId: state.bairroId || String((usuario as any)?.bairroId || ''),
      documentos: kyc.documentos.slice(0, 2),
    })
    await submitAnuncio()
  }

  const getFirstInvalidStep = () => {
    const requiredSteps: WizardStepId[] = ['perfil', 'localizacao', 'servicos', 'fotos']
    for (const step of requiredSteps) {
      const message = validateWizardStep(wizardState, step, hasExistingKyc)
      if (message) return { step, message }
    }
    return null
  }

  const runFinalFlow = async (requiresKyc: boolean) => {
    if (publishLockRef.current) return

    publishLockRef.current = true
    try {
      setPublishing(true)
      if (requiresKyc) await completeKycAndPublish()
      else await submitAnuncio()
    } catch (err: any) {
      setPublishGuard(closedPublishGuard)
      toast.error(err?.message || 'Não foi possível concluir a publicação agora.')
    } finally {
      publishLockRef.current = false
      setPublishing(false)
    }
  }

  const requestPublish = () => {
    if (publishing || publishLockRef.current) return
    if (!hydrated) {
      toast.warning('Aguarde o formulário terminar de carregar.')
      return
    }

    const invalid = getFirstInvalidStep()
    if (invalid) {
      setStep(invalid.step)
      toast.warning(invalid.message)
      return
    }

    if (!hasExistingKyc) {
      openPublishGuard({
        title: 'Sua conta ainda não foi verificada',
        description:
          'Vamos concluir seu cadastro com nome completo real, CPF, data de nascimento e documentos antes de enviar o anúncio para moderação.',
        actionLabel: 'Concluir cadastro e publicar',
      })
      return
    }

    toast.message('Conta verificada. Seu anúncio será enviado para moderação.')
    void runFinalFlow(false)
  }

  const handlePublishGuardAction = () => {
    void runFinalFlow(true)
  }

  const renderCurrentStep = () => {
    if (currentStep.id === 'perfil') {
      return (
        <WizardStepPerfil
          titulo={state.titulo}
          categoria={state.categoria}
          descricaoPerfil={state.descricaoPerfil}
          categorias={categorias}
          descricaoPerfilCount={descricaoPerfilCount}
          descricaoPerfilNeedsMore={descricaoPerfilNeedsMore}
          descricaoPerfilRemaining={descricaoPerfilRemaining}
          onTituloChange={(value) => updateForm({ titulo: value })}
          onCategoriaChange={(value) => updateForm({ categoria: value })}
          onDescricaoChange={(value) => updateForm({ descricaoPerfil: value.slice(0, 500) })}
        />
      )
    }

    if (currentStep.id === 'localizacao') {
      return (
        <WizardStepLocalizacao
          estadoId={state.estadoId}
          cidadeId={state.cidadeId}
          cidadeNome={state.cidadeNome}
          bairroId={state.bairroId}
          bairroNome={state.bairroNome}
          pontoReferenciaTexto={state.pontoReferenciaTexto}
          selectedStateLabel={selectedStateLabel}
          stateOptions={stateOptions}
          cidadeOptions={cidadeOptions}
          bairroOptions={bairroOptions}
          loadingEstados={localidades.loadingEstados}
          loadingCidades={localidades.loadingCidades}
          loadingBairros={localidades.loadingBairros}
          onEstado={handleEstado}
          onCidade={handleCidade}
          onBairro={handleBairro}
          onReferencia={(value) => updateForm({ pontoReferenciaTexto: value })}
        />
      )
    }

    if (currentStep.id === 'servicos') {
      return <WizardStepServicos state={state} onToggle={toggle} onPatch={updateForm} />
    }

    if (currentStep.id === 'fotos') {
      return (
        <WizardStepFotos
          initialFiles={state.fotos}
          fotoNomes={state.fotoNomes}
          onChange={setFotos}
        />
      )
    }

    if (currentStep.id === 'revisao') {
      return (
        <WizardFinalReview
          previewTitle={previewTitle}
          categoriaLabel={categoriaLabel}
          previewLocation={previewLocation}
          previewReference={previewReference}
          state={state}
          hasVirtual={hasVirtual}
        />
      )
    }

    return (
      <WizardStepPremium
        premiumChoice={state.premiumChoice}
        hasExistingKyc={hasExistingKyc}
        onSelect={(choice) => updateForm({ premiumChoice: choice })}
      />
    )
  }

  if (carregando || !hydrated) {
    return (
      <div className="mx-auto max-w-5xl px-4 py-20 text-sm text-zinc-500">
        Preparando seu anúncio...
      </div>
    )
  }

  if (!usuario) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-20 text-center">
        <h1 className="text-2xl font-semibold text-zinc-950">Acesso restrito</h1>
        <p className="mt-2 text-zinc-600">Faça login para iniciar seu anúncio.</p>
      </div>
    )
  }

  return (
    <main className="min-h-screen bg-[#f7f4ef] text-zinc-950">
      <WizardKycModal
        publishGuard={publishGuard}
        hasExistingKyc={hasExistingKyc}
        publishing={publishing}
        kyc={kyc}
        onClose={() => setPublishGuard(closedPublishGuard)}
        onConfirm={handlePublishGuardAction}
        onPatchKyc={(payload: Partial<WizardKycState>) => updateKyc(payload)}
        onSetDocumentos={setDocumentos}
      />

      <div className="mx-auto grid max-w-7xl gap-8 px-4 py-5 sm:px-6 lg:grid-cols-[minmax(0,1fr)_380px] lg:px-8 lg:py-8">
        <section
          ref={wizardTopRef}
          className="min-w-0 rounded-[30px] border border-zinc-200/90 bg-white shadow-[0_18px_60px_rgba(24,24,27,0.08)]"
        >
          <div className="border-b border-zinc-100 px-5 py-5 sm:px-7">
            <div className="flex items-center justify-between gap-3">
              <div>
                <h1 className="text-2xl font-semibold tracking-normal text-zinc-950 sm:text-3xl">
                  Publicar anúncio
                </h1>
              </div>
              <div className="rounded-full border border-zinc-200 px-3 py-1.5 text-xs text-zinc-600">
                {lastSavedAt ? `Salvo local ${lastSavedAt}` : 'Salvo neste dispositivo'}
              </div>
            </div>

            <div
              className="mt-6 grid gap-1.5"
              style={{ gridTemplateColumns: `repeat(${wizardSteps.length}, minmax(0, 1fr))` }}
            >
              {wizardSteps.map((step, idx) => (
                <button
                  key={step.id}
                  type="button"
                  onClick={() => goToStep(step.id, idx)}
                  className={cn(
                    'h-1.5 rounded-full transition-all duration-300',
                    idx < currentIndex
                      ? 'bg-zinc-950'
                      : idx === currentIndex
                        ? 'bg-gradient-to-r from-rose-500 via-fuchsia-500 to-violet-500 shadow-[0_0_0_1px_rgba(255,255,255,0.18)]'
                        : 'bg-zinc-200'
                  )}
                  aria-label={step.title}
                />
              ))}
            </div>

            <WizardPreview
              showDesktop={false}
              open={previewOpen}
              onOpenChange={setPreviewOpen}
              onOpenRequest={() => {
                setPreviewHintDismissed(true)
                setPreviewOpen(true)
              }}
              quietPreview={quietPreview}
              highlightedPreview={highlightedPreview}
              previewHintActive={!previewHintDismissed}
              previewTitle={previewTitle}
              previewPrice={previewPrice}
              previewDescription={previewDescription}
              previewMedia={previewMedia}
              previewReference={previewReference}
              idade={idade}
              hasVirtual={hasVirtual}
              hasExistingKyc={hasExistingKyc}
              premiumChoice={state.premiumChoice}
              estadoUf={state.estadoUf}
              cidadeNome={state.cidadeNome}
              bairroNome={state.bairroNome}
            />
          </div>

          <div className="px-5 py-6 sm:px-7 sm:py-8">
            <div className="mb-7 space-y-2">
              <p className="text-sm font-medium text-rose-700">{currentStep.eyebrow}</p>
              <h2 className="text-[28px] font-semibold leading-tight tracking-normal text-zinc-950">
                {stepCopy[currentStep.id].title}
              </h2>
              <p className="max-w-2xl text-sm leading-6 text-zinc-600">
                {stepCopy[currentStep.id].description}
              </p>
            </div>

            {renderCurrentStep()}

            <div className="mt-8 flex items-center justify-between gap-3">
              <Button
                type="button"
                variant="outline"
                onClick={previousStep}
                disabled={currentIndex === 0 || publishing}
              >
                <ChevronLeft className="mr-2 h-4 w-4" />
                Voltar
              </Button>

              {currentStep.id === 'premium' ? (
                <Button type="button" onClick={requestPublish} disabled={publishing}>
                  {publishing ? 'Publicando…' : 'Continuar'}
                  <ChevronRight className="ml-2 h-4 w-4" />
                </Button>
              ) : (
                <Button type="button" onClick={goNext} disabled={publishing}>
                  Continuar
                  <ChevronRight className="ml-2 h-4 w-4" />
                </Button>
              )}
            </div>
          </div>
        </section>

        <WizardPreview
          showMobile={false}
          renderDialog
          open={previewOpen}
          onOpenChange={setPreviewOpen}
          onOpenRequest={() => {
            setPreviewHintDismissed(true)
            setPreviewOpen(true)
          }}
          quietPreview={quietPreview}
          highlightedPreview={highlightedPreview}
          previewHintActive={!previewHintDismissed}
          previewTitle={previewTitle}
          previewPrice={previewPrice}
          previewDescription={previewDescription}
          previewMedia={previewMedia}
          previewReference={previewReference}
          idade={idade}
          hasVirtual={hasVirtual}
          hasExistingKyc={hasExistingKyc}
          premiumChoice={state.premiumChoice}
          estadoUf={state.estadoUf}
          cidadeNome={state.cidadeNome}
          bairroNome={state.bairroNome}
        />

      </div>
    </main>
  )
}
