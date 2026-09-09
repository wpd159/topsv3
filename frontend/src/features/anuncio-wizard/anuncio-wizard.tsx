'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { toast } from 'sonner'
import { revalidarCacheCatalogoPublico } from '@/app/(painel-admin)/admin/anuncios/actions'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/context/AuthContext'
import { useLocalidades } from '@/hooks/useLocalidades'
import { isoToBirthDate } from '@/lib/date/birth-date'
import { cn } from '@/lib/utils'
import { validatePhotoUpload, type PhotoUploadValidationResult } from '@/lib/photo-upload-validation'
import {
  anuncioEstaPublicamenteIndexavel,
  enviarIndexNowNoCliente,
  montarEventoIndexNowAnuncio,
} from '@/lib/seo/indexnow-client'
import {
  atualizarMeuAnuncio,
  buscarMeuAnuncio,
  consultarLimitesMinhasMidias,
  enviarMinhasMidiasEmLote,
  listarMinhasMidias,
  meusAnunciosErrorMessage,
  MeusAnunciosApiError,
  type MeuAnuncio,
  type MeuAnuncioAtualizacao,
  type MeuAnuncioCicloVida,
  type MinhasMidiasResponse,
} from '@/lib/meus-anuncios-api'
import { formatCurrencyBRL } from '@/utils/formatter'
import {
  fetchWizardCategories,
  fetchWizardKycStatus,
  submitWizardKyc,
  submitWizardAnuncio,
  type WizardCategoryOption,
  type WizardKycStatus,
} from './api'
import {
  stepCopy,
  type SearchableSelectOption,
} from './wizard-constants'
import { calculateAge, formatWizardCategory } from './wizard-utils'
import {
  useAnuncioWizardStore,
  validateWizardKycState,
  validateWizardStep,
  wizardSteps,
} from './use-anuncio-wizard-store'
import {
  initialWizardFormState,
  initialWizardKycState,
  type WizardFormState,
  type WizardStepId,
} from './types'
import { WizardFinalReview } from './components/wizard-final-review'
import { WizardPreview } from './components/wizard-preview'
import { WizardStepFotos } from './components/wizard-step-fotos'
import { WizardStepKyc } from './components/wizard-step-kyc'
import { WizardStepLocalizacao } from './components/wizard-step-localizacao'
import { WizardStepPerfil } from './components/wizard-step-perfil'
import { WizardStepPremium } from './components/wizard-step-premium'
import { WizardStepServicos } from './components/wizard-step-servicos'
import {
  clearWizardProgressSessionId,
  createWizardProgressSessionId,
  syncWizardProgress,
  type WizardProgressStatus,
  type WizardProgressStep,
} from './wizard-progress'

type AnuncioWizardProps = {
  mode?: 'create' | 'edit'
  slug?: string
}

function editErrorMessage(error: unknown) {
  if (!(error instanceof MeusAnunciosApiError)) {
    return error instanceof Error ? error.message : 'Não foi possível carregar o anúncio.'
  }
  if (error.status === 401) return 'Sua sessão expirou. Entre novamente para continuar.'
  if (error.status === 403) return 'Você não tem permissão para editar este anúncio.'
  if (error.status === 404) return 'Anúncio não encontrado.'
  if (error.status === 409) return 'Este anúncio está em análise e não pode ser alterado agora.'
  return error.message
}

function editPayload(state: WizardFormState): MeuAnuncioAtualizacao {
  const preco = Number(state.preco.replace(/\D/g, '')) / 100
  return {
    titulo: state.titulo,
    descricao: state.descricao.trim() || state.descricaoPerfil.trim(),
    categoria: state.categoria,
    preco: Number.isFinite(preco) && preco > 0 ? preco : null,
    uf: state.estadoUf,
    cidade: state.cidadeNome,
    bairro: state.bairroNome.trim() || null,
    enderecoResumido: state.pontoReferenciaTexto.trim() || null,
    locaisAtendimento: state.locaisAtendimento,
    servicos: state.servicos,
    atendimentoExclusivamenteVirtual: state.atendimentoExclusivamenteVirtual,
    linkConteudo: state.linkConteudo.trim() || null,
  }
}

function editDraftSourceVersion(anuncio: MeuAnuncio) {
  return [
    anuncio.id,
    anuncio.slug,
    anuncio.atualizadoEm ?? 'sem-atualizacao',
    anuncio.status,
    anuncio.statusModeracao,
  ].join(':')
}

function uploadFileKey(file: File) {
  return `${file.name}:${file.size}:${file.lastModified}`
}

export default function AnuncioWizard({ mode = 'create', slug }: AnuncioWizardProps) {
  const router = useRouter()
  const { usuario, carregando, refresh } = useAuth()
  const isEdit = mode === 'edit'
  const localidades = useLocalidades()
  const { loadBairros, loadCidades } = localidades
  const cacheUserId = usuario ? String(usuario.id) : null
  const cacheScope = useMemo(() => cacheUserId ? {
    userId: cacheUserId,
    mode,
    ...(isEdit && slug ? { slug } : {}),
  } : null, [cacheUserId, isEdit, mode, slug])
  const progressScope = `${cacheUserId ?? 'anonimo'}:${mode}:${slug ?? 'novo'}`
  const progressScopeRef = useRef(progressScope)
  progressScopeRef.current = progressScope
  const store = useAnuncioWizardStore({ cacheScope, backendFirst: isEdit })
  const {
    state: wizardState,
    hydrated,
    lastSavedAt,
    currentIndex,
    updateForm,
    updateKyc,
    setFotos,
    setVideos,
    setDocumentos,
    setStep,
    nextStep,
    previousStep,
    reset,
    clearCurrentCache,
    hydrateFromBackend,
  } = store

  const state = wizardState.form
  const kyc = wizardState.kyc
  const currentStep = wizardSteps[currentIndex]
  const [publishing, setPublishing] = useState(false)
  const [editLoading, setEditLoading] = useState(isEdit)
  const [editError, setEditError] = useState<string | null>(null)
  const [editAnuncio, setEditAnuncio] = useState<MeuAnuncio | null>(null)
  const previousAnuncioRef = useRef<MeuAnuncio | null>(null)
  const [editMedia, setEditMedia] = useState<MinhasMidiasResponse | null>(null)
  const [mediaBusy, setMediaBusy] = useState(false)
  const mediaBusyRef = useRef(false)
  const [terminalAnuncio, setTerminalAnuncio] = useState<MeuAnuncioCicloVida | null>(null)
  const terminalAnuncioRef = useRef<MeuAnuncioCicloVida | null>(null)
  const flowGenerationRef = useRef(0)
  const mountedRef = useRef(true)
  const [fotoPreviewUrls, setFotoPreviewUrls] = useState<string[]>([])
  const [kycStatus, setKycStatus] = useState<WizardKycStatus | null>(null)
  const [kycLoading, setKycLoading] = useState(true)
  const [kycError, setKycError] = useState<string | null>(null)
  const [categoryCatalog, setCategoryCatalog] = useState<WizardCategoryOption[]>([])
  const [categoryLoading, setCategoryLoading] = useState(true)
  const [categoryError, setCategoryError] = useState<string | null>(null)
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewHintDismissed, setPreviewHintDismissed] = useState(false)
  const [createMediaProgress, setCreateMediaProgress] = useState<Record<string, number>>({})
  const [createMediaErrors, setCreateMediaErrors] = useState<Record<string, string>>({})
  const [serverRejectedPhotos, setServerRejectedPhotos] = useState<File[]>([])
  const [photoValidation, setPhotoValidation] = useState<{
    files: File[]
    results: PhotoUploadValidationResult[]
  }>({ files: [], results: [] })
  const photoSelectionRef = useRef(state.fotos)
  photoSelectionRef.current = state.fotos
  const photoValidationMatches = photoValidation.files.length === state.fotos.length
    && photoValidation.files.every((file, index) => file === state.fotos[index])
  const photoValidationPending = !isEdit && state.fotos.length > 0 && !photoValidationMatches
  const invalidPhotos = !isEdit && photoValidationMatches && photoValidation.results.some((result) => !result.valid)
  const hasServerRejectedPhotos = !isEdit && state.fotos.some((file) => serverRejectedPhotos.includes(file))
  const photoSelectionBlocked = photoValidationPending || invalidPhotos || hasServerRejectedPhotos || (!isEdit && state.fotos.length > 4)
  const wizardTopRef = useRef<HTMLDivElement | null>(null)
  const publishLockRef = useRef(false)
  const stepDidMountRef = useRef(false)
  const wizardSessionIdRef = useRef<string | null>(null)
  const lastSyncedStepRef = useRef<WizardProgressStep | null>(null)
  const editLoadGenerationRef = useRef(0)
  const createdSlugRef = useRef<string | null>(null)
  const createdAnuncioIdRef = useRef<string | null>(null)
  const createdDetailsSyncedRef = useRef(false)
  const kycLoadedUserRef = useRef<string | null>(null)

  useEffect(() => {
    mountedRef.current = true
    terminalAnuncioRef.current = null
    setTerminalAnuncio(null)
    setEditMedia(null)
    previousAnuncioRef.current = null
    mediaBusyRef.current = false
    setMediaBusy(false)
    flowGenerationRef.current += 1
    return () => {
      mountedRef.current = false
      flowGenerationRef.current += 1
      editLoadGenerationRef.current += 1
    }
  }, [progressScope])

  const closeWizard = useCallback((anuncio: MeuAnuncioCicloVida) => {
    if (!mountedRef.current || terminalAnuncioRef.current || progressScopeRef.current !== progressScope) return
    terminalAnuncioRef.current = anuncio
    flowGenerationRef.current += 1
    setTerminalAnuncio(anuncio)
    clearCurrentCache()
    clearWizardProgressSessionId(progressScope)
    const previous = previousAnuncioRef.current
    if (previous?.id === anuncio.id && previous.slug === anuncio.slug
      && anuncioEstaPublicamenteIndexavel(previous.status)) {
      // Reuse the lifecycle withdrawal invalidation without fetching a removed
      // detail. Its best-effort result cannot undo the already latched terminal.
      void enviarIndexNowNoCliente(montarEventoIndexNowAnuncio({
        eventType: 'RETIRADA',
        previous: {
          slug: previous.slug,
          estadoUf: previous.localizacao?.uf,
          cidadeNome: previous.localizacao?.cidade,
          bairroNome: previous.localizacao?.bairro,
        },
        changeFingerprint: anuncio.atualizadoEm,
      }))
    } else {
      // An old pending snapshot cannot rule out a later publication. Invalidate
      // the catalog without sending unproven-public URLs or locations to IndexNow.
      void revalidarCacheCatalogoPublico().catch(() => {
        // The canonical closure remains terminal even if invalidation fails.
      })
    }
  }, [clearCurrentCache, progressScope])

  const acceptMediaResponse = useCallback((response: MinhasMidiasResponse) => {
    if (!mountedRef.current || terminalAnuncioRef.current || progressScopeRef.current !== progressScope) return
    const targetSlug = slug ?? createdSlugRef.current
    if (response.anuncio.slug !== targetSlug) throw new Error('A resposta não corresponde ao anúncio em edição.')
    setEditMedia(response)
    setEditAnuncio((current) => current ? { ...current, ...response.anuncio } : current)
    if (response.anuncio.status === 'REMOVIDO') closeWizard(response.anuncio)
  }, [closeWizard, progressScope, slug])

  const beginMediaInteraction = useCallback(() => {
    if (publishLockRef.current || mediaBusyRef.current || terminalAnuncioRef.current || !mountedRef.current || progressScopeRef.current !== progressScope) return false
    mediaBusyRef.current = true
    flowGenerationRef.current += 1
    setMediaBusy(true)
    return true
  }, [progressScope])

  const endMediaInteraction = useCallback(() => {
    if (progressScopeRef.current !== progressScope) return
    mediaBusyRef.current = false
    if (mountedRef.current) setMediaBusy(false)
  }, [progressScope])

  const flowIsCurrent = (generation: number) => mountedRef.current
    && flowGenerationRef.current === generation && !terminalAnuncioRef.current && !mediaBusyRef.current

  useEffect(() => {
    let current = true
    const files = state.fotos
    void Promise.all(files.map(validatePhotoUpload)).then((results) => {
      if (current) setPhotoValidation({ files, results })
    })
    return () => { current = false }
  }, [state.fotos])

  const handlePhotoChange = (files: File[]) => {
    if (publishLockRef.current) return
    photoSelectionRef.current = files
    setFotos(files)
    setServerRejectedPhotos((current) => current.filter((file) => files.includes(file)))
    setCreateMediaErrors({})
    setCreateMediaProgress({})
  }

  const photoSelectionMessage = photoValidationPending
    ? 'Verificando foto…'
    : state.fotos.length > 4
      ? 'Você pode adicionar até 4 fotos gratuitamente. Remova o excedente para continuar.'
      : 'Remova ou substitua as fotos recusadas antes de continuar.'

  const idade = calculateAge(kycStatus?.dataNascimento || (usuario as any)?.dataNascimento)
  const hasExistingKyc = Boolean(kycStatus?.prontoParaEnviarAnuncio)
  const previewTitle = state.titulo.trim() || 'Seu nome em destaque'
  const profileDescription = state.descricaoPerfil.trim()
  const previewLocation = [state.cidadeNome || 'Cidade', state.bairroNome || 'Bairro']
    .filter(Boolean)
    .join(' · ')
  const previewReference = state.pontoReferenciaTexto.trim()
  const hasVirtual = state.servicos.includes('VIDEOCHAMADA')
  const quietPreview = currentStep.id === 'perfil' || currentStep.id === 'localizacao'
  const highlightedPreview =
    currentStep.id === 'fotos' ||
    currentStep.id === 'revisao' ||
    currentStep.id === 'premium' ||
    currentStep.id === 'kyc'
  const selectedStateLabel = state.estadoNome
    ? `${state.estadoNome}${state.estadoUf ? ` · ${state.estadoUf}` : ''}`
    : 'Selecione o estado'
  const descricaoPerfilCount = state.descricaoPerfil.trim().length
  const descricaoPerfilNeedsMore = descricaoPerfilCount > 0 && descricaoPerfilCount < 20
  const descricaoPerfilRemaining = Math.max(0, 20 - descricaoPerfilCount)
  const previewDescription =
    state.descricao.trim() ||
    profileDescription ||
    'Seu texto de apresentação aparece aqui para aproximar o preview do anúncio real.'
  const previewPrice = state.preco.trim() || 'Consulte valores'
  const currentMediaUrls = (editMedia ? editMedia.midias.map((midia) => midia.previewUrl) : editAnuncio?.midias.map((midia) => midia.urlPublica))
    ?.filter((url): url is string => Boolean(url)) ?? []
  const previewMedia = fotoPreviewUrls.length ? fotoPreviewUrls : currentMediaUrls
  const syncProgress = useCallback(
    (
      ultimoStep: WizardProgressStep,
      status: WizardProgressStatus = 'EM_PREENCHIMENTO',
      anuncioId?: number | string | null
    ) => {
      if (terminalAnuncioRef.current || !mountedRef.current || progressScopeRef.current !== progressScope) return Promise.resolve(null)
      lastSyncedStepRef.current = ultimoStep
      const sessionId = wizardSessionIdRef.current
        ?? createWizardProgressSessionId(progressScope)
      wizardSessionIdRef.current = sessionId
      return syncWizardProgress({
        sessionId,
        mode,
        ultimoStep,
        status,
        anuncioId,
      })
    },
    [mode, progressScope]
  )

  useEffect(() => {
    const urls = state.fotos.map((file) => URL.createObjectURL(file))
    setFotoPreviewUrls(urls)
    return () => urls.forEach((url) => URL.revokeObjectURL(url))
  }, [state.fotos])

  useEffect(() => {
    if (!isEdit || carregando || !cacheUserId) return
    if (!slug) {
      setEditError('Anúncio não encontrado.')
      setEditLoading(false)
      return
    }
    const generation = ++editLoadGenerationRef.current
    setEditLoading(true)
    setEditError(null)

    void Promise.all([buscarMeuAnuncio(slug), listarMinhasMidias(slug)])
      .then(([anuncio, media]) => {
        if (!mountedRef.current || generation !== editLoadGenerationRef.current || terminalAnuncioRef.current) return
        if (anuncio.id !== media.anuncio.id || anuncio.slug !== media.anuncio.slug) throw new Error('As respostas não correspondem ao mesmo anúncio.')
        previousAnuncioRef.current = anuncio
        setEditAnuncio(anuncio)
        acceptMediaResponse(media)
        hydrateFromBackend({
          currentStep: 'perfil',
          form: {
            ...initialWizardFormState,
            titulo: anuncio.titulo || '',
            categoria: anuncio.categoria || '',
            preco: formatCurrencyBRL(anuncio.preco),
            locaisAtendimento: anuncio.locaisAtendimento || [],
            servicos: anuncio.servicos || [],
            atendimentoExclusivamenteVirtual: anuncio.atendimentoExclusivamenteVirtual,
            descricao: anuncio.descricao || '',
            linkConteudo: anuncio.linkConteudo || '',
            estadoId: anuncio.localizacao?.uf || '',
            cidadeId: anuncio.localizacao?.cidadeSlug || '',
            bairroId: anuncio.localizacao?.bairroSlug || '',
            estadoNome: anuncio.localizacao?.uf || '',
            estadoUf: anuncio.localizacao?.uf || '',
            cidadeNome: anuncio.localizacao?.cidade || '',
            bairroNome: anuncio.localizacao?.bairro || '',
            pontoReferenciaTexto: anuncio.localizacao?.enderecoResumido || '',
          },
          kyc: { ...initialWizardKycState, documentos: [], documentoNomes: [] },
        }, editDraftSourceVersion(anuncio))
      })
      .catch((error) => {
        if (mountedRef.current && generation === editLoadGenerationRef.current) setEditError(editErrorMessage(error))
      })
      .finally(() => {
        if (mountedRef.current && generation === editLoadGenerationRef.current) setEditLoading(false)
      })
    return () => { editLoadGenerationRef.current += 1 }
  }, [acceptMediaResponse, cacheUserId, carregando, hydrateFromBackend, isEdit, slug])

  useEffect(() => {
    if (!hydrated) return
    void (async () => {
      if (state.estadoId) await loadCidades(state.estadoId)
      if (state.cidadeId) await loadBairros(state.cidadeId)
    })()
  }, [hydrated, loadBairros, loadCidades, state.cidadeId, state.estadoId])

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

  const loadKycStatus = useCallback(async () => {
    if (!usuario) return
    setKycLoading(true)
    setKycError(null)
    try {
      const status = await fetchWizardKycStatus()
      setKycStatus(status)
      updateKyc({
        nomeCompleto: status.nomeCivil || '',
        dataNascimento: isoToBirthDate(status.dataNascimento),
        cpf: '',
        documentos: [],
        documentoNomes: [],
      })
    } catch (error) {
      setKycStatus(null)
      setKycError(error instanceof Error ? error.message : 'Não foi possível carregar sua verificação.')
    } finally {
      setKycLoading(false)
    }
  }, [updateKyc, usuario])

  useEffect(() => {
    if (carregando || !usuario) return
    const userId = String(usuario.id)
    if (kycLoadedUserRef.current === userId) return
    kycLoadedUserRef.current = userId
    void loadKycStatus()
  }, [carregando, loadKycStatus, usuario])

  const loadCategoryCatalog = useCallback(async () => {
    setCategoryLoading(true)
    setCategoryError(null)
    try {
      const options = await fetchWizardCategories()
      if (!options.length) throw new Error('Nenhuma categoria está disponível para publicação.')
      setCategoryCatalog(options)
    } catch (error) {
      setCategoryError(
        error instanceof Error
          ? error.message
          : 'Não foi possível carregar as categorias. Tente novamente.'
      )
    } finally {
      setCategoryLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadCategoryCatalog()
  }, [loadCategoryCatalog])

  useEffect(() => {
    if (!hydrated || isEdit || categoryLoading || categoryError || !state.categoria) return
    if (!categoryCatalog.some((item) => item.value === state.categoria)) {
      updateForm({ categoria: '' })
    }
  }, [
    categoryCatalog,
    categoryError,
    categoryLoading,
    hydrated,
    isEdit,
    state.categoria,
    updateForm,
  ])

  useEffect(() => {
    if (!hydrated) return
    if (!usuario) return
    const stepId = currentStep.id as WizardProgressStep
    if (lastSyncedStepRef.current === stepId) return
    syncProgress(stepId)
  }, [currentStep.id, hydrated, syncProgress, usuario])

  const categoriaOptions = useMemo(
    () =>
      isEdit
        && state.categoria
        && !categoryCatalog.some((item) => item.value === state.categoria)
        ? [
            ...categoryCatalog,
            { value: state.categoria, label: formatWizardCategory(state.categoria) },
          ]
        : categoryCatalog,
    [categoryCatalog, isEdit, state.categoria]
  )
  const categoriaLabel = useMemo(
    () => categoriaOptions.find((item) => item.value === state.categoria)?.label || 'Categoria',
    [categoriaOptions, state.categoria]
  )

  const stateOptions = useMemo<SearchableSelectOption[]>(
    () => {
      const options = localidades.estados.map((item) => ({
        id: String(item.id),
        label: item.nome,
        searchLabel: `${item.nome} ${item.uf ?? ''}`,
        subtitle: item.uf ? `UF ${item.uf}` : undefined,
      }))
      if (state.estadoId && !options.some((item) => item.id === state.estadoId)) {
        options.push({
          id: state.estadoId,
          label: state.estadoNome || state.estadoUf,
          searchLabel: `${state.estadoNome} ${state.estadoUf}`,
          subtitle: state.estadoUf ? `UF ${state.estadoUf}` : undefined,
        })
      }
      return options
    },
    [localidades.estados, state.estadoId, state.estadoNome, state.estadoUf]
  )

  const cidadeOptions = useMemo<SearchableSelectOption[]>(
    () => {
      const options = localidades.cidades.map((item) => ({
        id: String(item.id),
        label: item.nome,
        searchLabel: `${item.nome} ${state.estadoNome} ${state.estadoUf}`,
      }))
      if (state.cidadeId && !options.some((item) => item.id === state.cidadeId)) {
        options.push({
          id: state.cidadeId,
          label: state.cidadeNome,
          searchLabel: `${state.cidadeNome} ${state.estadoNome} ${state.estadoUf}`,
        })
      }
      return options
    },
    [localidades.cidades, state.cidadeId, state.cidadeNome, state.estadoNome, state.estadoUf]
  )

  const bairroOptions = useMemo<SearchableSelectOption[]>(
    () => {
      const options = localidades.bairros.map((item) => ({
        id: String(item.id),
        label: item.nome,
        searchLabel: `${item.nome} ${state.cidadeNome} ${state.estadoNome} ${state.estadoUf}`,
      }))
      if (state.bairroId && !options.some((item) => item.id === state.bairroId)) {
        options.push({
          id: state.bairroId,
          label: state.bairroNome,
          searchLabel: `${state.bairroNome} ${state.cidadeNome} ${state.estadoNome} ${state.estadoUf}`,
        })
      }
      return options
    },
    [localidades.bairros, state.bairroId, state.bairroNome, state.cidadeNome, state.estadoNome, state.estadoUf]
  )

  const toggle = (field: 'locaisAtendimento' | 'servicos', value: string) => {
    const set = new Set(state[field])
    if (set.has(value)) set.delete(value)
    else set.add(value)
    if (field === 'locaisAtendimento') updateForm({ locaisAtendimento: Array.from(set) })
    else updateForm({
      servicos: Array.from(set),
      ...(value === 'VIDEOCHAMADA' && !set.has(value)
        ? { atendimentoExclusivamenteVirtual: false }
        : {}),
    })
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
    if (currentStep.id === 'fotos' && photoSelectionBlocked) {
      toast.warning(photoSelectionMessage)
      return false
    }
    const message = validateWizardStep(wizardState, currentStep.id, mode)
    if (message) {
      toast.warning(message)
      return false
    }
    return true
  }

  const canMoveToStep = (targetIndex: number) => {
    if (targetIndex <= currentIndex) return true
    for (let i = 0; i < targetIndex; i += 1) {
      if (wizardSteps[i].id === 'fotos' && photoSelectionBlocked) {
        toast.warning(photoSelectionMessage)
        return false
      }
      const message = validateWizardStep(wizardState, wizardSteps[i].id, mode)
      if (message) {
        toast.warning(message)
        return false
      }
    }
    return true
  }

  const goToStep = (step: WizardStepId, index: number) => {
    if (publishLockRef.current || mediaBusyRef.current || terminalAnuncioRef.current) return
    if (!canMoveToStep(index)) return
    setStep(step)
  }

  const goNext = () => {
    if (publishLockRef.current || mediaBusyRef.current || terminalAnuncioRef.current) return
    if (!validateCurrentStep()) return
    nextStep()
  }

  const submitEdit = async (generation: number) => {
    if (!slug) {
      toast.error('Anúncio não encontrado.')
      return
    }

    const atualizado = await atualizarMeuAnuncio(slug, editPayload(state))
    if (!flowIsCurrent(generation)) return
    if (atualizado.status === 'REMOVIDO') {
      closeWizard(atualizado)
      return
    }
    setEditAnuncio(atualizado)
    await syncProgress('concluido', 'AGUARDANDO_MODERACAO', atualizado.id)
    if (!flowIsCurrent(generation)) return
    clearWizardProgressSessionId(progressScope)
    clearCurrentCache()
    toast.success('Alterações salvas e enviadas para revisão.')
    router.push(`/meus-anuncios/${encodeURIComponent(atualizado.slug)}`)
  }

  const submitAnuncio = async () => {
    if (!usuario?.id) {
      toast.error('Faça login para publicar.')
      return
    }

    if (!createdSlugRef.current) {
      const created = await submitWizardAnuncio(state)
      createdSlugRef.current = created.slugLocal
      createdAnuncioIdRef.current = created.anuncioId
    }
    const targetSlug = createdSlugRef.current
    if (!targetSlug) throw new Error('Não foi possível identificar o anúncio criado.')
    if (!createdDetailsSyncedRef.current) {
      await atualizarMeuAnuncio(targetSlug, editPayload(state))
      createdDetailsSyncedRef.current = true
    }
    const limites = await consultarLimitesMinhasMidias(targetSlug)
    if (state.fotos.length > limites.fotosDisponiveis) {
      throw new Error(`Seu limite atual permite mais ${limites.fotosDisponiveis} foto(s). Remova o excedente para continuar.`)
    }
    if (state.videos.length > limites.videosDisponiveis) {
      throw new Error('Este anúncio já atingiu o limite de vídeos.')
    }
    setCreateMediaErrors({})
    const arquivos = [...state.fotos, ...state.videos]
    const abrirMonetizacao = state.premiumChoice === 'destaque'
    arquivos.forEach((file) => {
      const key = uploadFileKey(file)
      setCreateMediaProgress((current) => ({ ...current, [key]: 0 }))
    })
    if (arquivos.length) {
      try {
        const media = await enviarMinhasMidiasEmLote(targetSlug, arquivos, (value) => {
          setCreateMediaProgress(Object.fromEntries(
            arquivos.map((file) => [uploadFileKey(file), value])
          ))
        })
        acceptMediaResponse(media)
        if (terminalAnuncioRef.current || !mountedRef.current) return
      } catch (error) {
        if (error instanceof MeusAnunciosApiError && error.unsupportedPhotoUpload) {
          // A photo-only 415 does not identify the offending part of the batch.
          // Keep its File identities blocked until removed or replaced.
          setServerRejectedPhotos(state.fotos)
        }
        setCreateMediaErrors(Object.fromEntries(
          arquivos.map((file) => [
            uploadFileKey(file),
            meusAnunciosErrorMessage(error, 'Falha ao enviar o lote.'),
          ])
        ))
        setStep('fotos')
        throw error
      }
    }
    setFotos([])
    setVideos([])
    await syncProgress('concluido', 'AGUARDANDO_MODERACAO', createdAnuncioIdRef.current)
    clearWizardProgressSessionId(progressScope)
    await refresh().catch(() => null)
    reset()
    createdSlugRef.current = null
    createdAnuncioIdRef.current = null
    createdDetailsSyncedRef.current = false
    setCreateMediaProgress({})
    setCreateMediaErrors({})
    toast.success('Anúncio enviado para moderação.')
    router.push(
      abrirMonetizacao
        ? `/meus-anuncios/${encodeURIComponent(targetSlug)}/monetizar`
        : '/meus-anuncios'
    )
  }

  const ensureKycReady = async () => {
    if (!kycStatus) {
      throw new Error(kycError || 'Aguarde a verificação dos seus dados antes de continuar.')
    }
    if (kycStatus.prontoParaEnviarAnuncio) return

    const message = validateWizardKycState(wizardState, {
      nomeCivil: Boolean(kycStatus.nomeCivil),
      cpf: kycStatus.cpfPreenchido,
      dataNascimento: Boolean(kycStatus.dataNascimento),
    })
    if (message) throw new Error(message)

    const atualizado = await submitWizardKyc(kyc)
    setKycStatus(atualizado)
    updateKyc({ documentos: [], documentoNomes: [] })
    if (!atualizado.prontoParaEnviarAnuncio) {
      throw new Error('O envio documental não foi confirmado. Revise os dados e tente novamente.')
    }
  }

  const getFirstInvalidStep = () => {
    const requiredSteps: WizardStepId[] = ['perfil', 'localizacao', 'servicos', 'fotos']
    for (const step of requiredSteps) {
      const message = validateWizardStep(wizardState, step, mode)
      if (message) return { step, message }
    }
    return null
  }

  const runFinalFlow = async () => {
    if (publishLockRef.current || mediaBusyRef.current || terminalAnuncioRef.current) return

    publishLockRef.current = true
    const generation = flowGenerationRef.current
    try {
      setPublishing(true)
      // Complete the photo check before KYC submission or advertisement creation.
      // The snapshot guard also rejects a selection changed by a stale callback.
      if (!isEdit) {
        const files = state.fotos
        const results = await Promise.all(files.map(validatePhotoUpload))
        const changed = files.length !== photoSelectionRef.current.length
          || files.some((file, index) => file !== photoSelectionRef.current[index])
        const invalidIndex = results.findIndex((result) => !result.valid)
        if (changed || files.length > 4 || invalidIndex >= 0 || files.some((file) => serverRejectedPhotos.includes(file))) {
          setStep('fotos')
          const result = results[invalidIndex]
          throw new Error(result && !result.valid
            ? `${files[invalidIndex].name}: ${result.message}`
            : 'Revise a seleção de fotos antes de continuar.')
        }
      }
      await ensureKycReady()
      if (!flowIsCurrent(generation)) return
      if (isEdit) await submitEdit(generation)
      else await submitAnuncio()
    } catch (err: any) {
      if (!flowIsCurrent(generation)) return
      const message = isEdit
        ? editErrorMessage(err)
        : err?.message || 'Não foi possível concluir a publicação agora.'
      toast.error(message)
      if (!isEdit && message.includes('Minha conta')) {
        router.push('/minha-conta')
      }
    } finally {
      publishLockRef.current = false
      if (mountedRef.current) setPublishing(false)
    }
  }

  const requestPublish = () => {
    if (publishing || publishLockRef.current || mediaBusyRef.current || terminalAnuncioRef.current || (isEdit && !editMedia)) return
    if (photoSelectionBlocked) {
      setStep('fotos')
      toast.warning(photoSelectionMessage)
      return
    }
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

    if (kycLoading || !kycStatus) {
      setStep('kyc')
      toast.warning(kycError || 'Aguarde a verificação dos seus dados.')
      return
    }

    void runFinalFlow()
  }

  const renderCurrentStep = () => {
    if (currentStep.id === 'perfil') {
      return (
        <WizardStepPerfil
          titulo={state.titulo}
          categoria={state.categoria}
          descricaoPerfil={state.descricaoPerfil}
          categorias={categoriaOptions}
          descricaoPerfilCount={descricaoPerfilCount}
          descricaoPerfilNeedsMore={descricaoPerfilNeedsMore}
          descricaoPerfilRemaining={descricaoPerfilRemaining}
          categoriasLoading={categoryLoading}
          categoriasError={categoryError}
          showProfileDescription={!isEdit}
          onTituloChange={(value) => updateForm({ titulo: value })}
          onCategoriaChange={(value) => updateForm({ categoria: value })}
          onDescricaoChange={(value) => updateForm({ descricaoPerfil: value.slice(0, 500) })}
          onReloadCategorias={() => void loadCategoryCatalog()}
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
          errorMessage={localidades.errorMessage}
          onEstado={handleEstado}
          onCidade={handleCidade}
          onBairro={handleBairro}
          onReferencia={(value) => updateForm({ pontoReferenciaTexto: value })}
          showReference
        />
      )
    }

    if (currentStep.id === 'servicos') {
      return <WizardStepServicos state={state} mode={mode} onToggle={toggle} onPatch={updateForm} />
    }

    if (currentStep.id === 'fotos') {
      return (
        <WizardStepFotos
          key={progressScope}
          slug={isEdit ? slug : undefined}
          initialFiles={state.fotos}
          fotoNomes={state.fotoNomes}
          onChange={handlePhotoChange}
          photoValidation={photoValidationMatches ? photoValidation.results.map((result, index) => serverRejectedPhotos.includes(state.fotos[index])
            ? { valid: false, message: 'O servidor recusou este lote de fotos. Remova ou substitua o arquivo por uma nova cópia em JPG ou PNG.' }
            : result) : []}
          photoValidationPending={photoValidationPending}
          disabled={publishing}
          videosNovos={state.videos}
          onChangeVideosNovos={setVideos}
          createProgress={createMediaProgress}
          createErrors={createMediaErrors}
          persistedState={isEdit ? editMedia : undefined}
          onPersistedChange={acceptMediaResponse}
          onInteractionStart={beginMediaInteraction}
          onInteractionEnd={endMediaInteraction}
          onStateUnconfirmed={() => {
            flowGenerationRef.current += 1
            setEditMedia(null)
            setEditError('Não foi possível confirmar o estado do anúncio. Volte para Meus anúncios para conferir antes de continuar.')
          }}
          onAddBenefit={() => {
            if (publishLockRef.current || mediaBusyRef.current || terminalAnuncioRef.current) return
            if (isEdit && slug) {
              router.push(`/meus-anuncios/${encodeURIComponent(slug)}/monetizar`)
              return
            }
            updateForm({ premiumChoice: 'destaque' })
            setStep('premium')
          }}
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
          photoCount={isEdit ? editMedia?.fotosValidasAtivasTotal ?? 0 : undefined}
        />
      )
    }

    if (currentStep.id === 'kyc') {
      return (
        <WizardStepKyc
          state={kyc}
          status={kycStatus}
          loading={kycLoading}
          error={kycError}
          onReload={loadKycStatus}
          onPatch={updateKyc}
          onSetDocumentos={setDocumentos}
        />
      )
    }

    return (
      <WizardStepPremium
        premiumChoice={state.premiumChoice}
        hasExistingKyc={hasExistingKyc}
        readOnly={isEdit}
        onSelect={(choice) => updateForm({ premiumChoice: choice })}
      />
    )
  }

  if (terminalAnuncio) {
    return (
      <main className="mx-auto max-w-3xl space-y-4 px-4 py-20">
        <section role="alert" className="space-y-3 rounded-xl border border-amber-300 bg-amber-50 p-6">
          <h1 className="text-2xl font-semibold">Anúncio encerrado</h1>
          <p>O servidor confirmou o encerramento do anúncio. Esta edição não será enviada para moderação.</p>
          <p className="text-sm">Volte para Meus anúncios para consultar os anúncios disponíveis.</p>
        </section>
        <Button type="button" onClick={() => router.push('/meus-anuncios')}>Voltar para Meus anúncios</Button>
      </main>
    )
  }

  if (carregando || (!hydrated && !(isEdit && !editLoading))) {
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

  if (isEdit && editLoading) {
    return (
      <div className="mx-auto max-w-5xl px-4 py-20 text-sm text-zinc-500">
        Carregando anúncio...
      </div>
    )
  }

  if (isEdit && (editError || !editAnuncio)) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-20 text-center">
        <h1 className="text-2xl font-semibold text-zinc-950">Não foi possível editar</h1>
        <p role="alert" className="mt-2 text-zinc-600">
          {editError || 'Anúncio não encontrado.'}
        </p>
        <Button
          type="button"
          variant="outline"
          className="mt-6"
          onClick={() => router.push('/meus-anuncios')}
        >
          Voltar para Meus anúncios
        </Button>
      </div>
    )
  }

  return (
    <main className="min-h-screen bg-[#f7f4ef] text-zinc-950">
      <div className="mx-auto grid max-w-7xl gap-8 px-4 py-5 sm:px-6 lg:grid-cols-[minmax(0,1fr)_380px] lg:px-8 lg:py-8">
        <section
          ref={wizardTopRef}
          className="min-w-0 rounded-[30px] border border-zinc-200/90 bg-white shadow-[0_18px_60px_rgba(24,24,27,0.08)]"
        >
          <div className="border-b border-zinc-100 px-5 py-5 sm:px-7">
            <div className="flex items-center justify-between gap-3">
              <div>
                <h1 className="text-2xl font-semibold tracking-normal text-zinc-950 sm:text-3xl">
                  {isEdit ? 'Editar anúncio' : 'Publicar anúncio'}
                </h1>
              </div>
              <div className="rounded-full border border-zinc-200 px-3 py-1.5 text-xs text-zinc-600">
                {isEdit
                  ? 'Dados carregados do anúncio'
                  : lastSavedAt
                    ? `Salvo local ${lastSavedAt}`
                    : 'Salvo neste dispositivo'}
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
                  disabled={publishing || mediaBusy}
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
                onClick={() => {
                  if (!publishLockRef.current && !mediaBusyRef.current && !terminalAnuncioRef.current) previousStep()
                }}
                disabled={currentIndex === 0 || publishing || mediaBusy}
              >
                <ChevronLeft className="mr-2 h-4 w-4" />
                Voltar
              </Button>

              {currentStep.id === 'kyc' ? (
                <Button type="button" onClick={requestPublish} disabled={publishing || mediaBusy || photoSelectionBlocked}>
                  {publishing
                    ? isEdit
                      ? 'Salvando…'
                      : 'Publicando…'
                    : isEdit
                      ? 'Salvar alterações'
                      : 'Enviar para moderação'}
                  <ChevronRight className="ml-2 h-4 w-4" />
                </Button>
              ) : (
                <Button type="button" onClick={goNext} disabled={publishing || mediaBusy || (currentStep.id === 'fotos' && photoSelectionBlocked)}>
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
