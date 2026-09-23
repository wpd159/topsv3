'use client'

import { ArrowDown, ArrowUp, Camera, Loader2, PlayCircle, Trash2 } from 'lucide-react'
import { VideoUploader } from '@/components/anuncios/editar/video-uploader'
import { FilePicker } from '@/components/forms/file-picker'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  PHOTO_UPLOAD_ACCEPT,
  PHOTO_UPLOAD_GUIDANCE,
  isSupportedUploadVideo,
  validatePhotoUpload,
  type PhotoUploadValidationResult,
} from '@/lib/photo-upload-validation'
import {
  buscarMeuAnuncio,
  enviarMinhasMidiasEmLote,
  listarMinhasMidias,
  meusAnunciosErrorMessage,
  MeusAnunciosApiError,
  removerMinhaMidia,
  reordenarMinhasMidias,
  type MinhaMidiaGestao,
  type MinhasMidiasResponse,
} from '@/lib/meus-anuncios-api'
import {
  anuncioEstaPublicamenteIndexavel,
  enviarIndexNowNoCliente,
  montarEventoIndexNowAnuncio,
} from '@/lib/seo/indexnow-client'
import { StepPanel } from './wizard-ui'
import type { EditPendingMedia } from '../types'

type WizardStepFotosProps = {
  slug?: string
  initialFiles: File[]
  fotoNomes: string[]
  onChange: (payload: File[]) => void
  videosNovos: File[]
  onChangeVideosNovos: (payload: File[]) => void
  createProgress?: Record<string, number>
  createErrors?: Record<string, string>
  photoValidation?: PhotoUploadValidationResult[]
  photoValidationPending?: boolean
  disabled?: boolean
  onAddBenefit?: () => void
  persistedState?: MinhasMidiasResponse | null
  onPersistedChange?: (response: MinhasMidiasResponse) => void
  onInteractionStart?: () => boolean
  onInteractionEnd?: () => void
  onStateUnconfirmed?: () => void
  pendingFiles?: EditPendingMedia[]
  onPendingFilesChange?: (files: EditPendingMedia[]) => void
  uploadUnconfirmed?: boolean
  onUploadUnconfirmedChange?: (value: boolean) => void
  pendingSaveNotice?: boolean
  accountScope?: string
}

function fileKey(file: File) {
  return `${file.name}:${file.size}:${file.lastModified}`
}

function validateSelectedMedia(file: File, photo: boolean): Promise<PhotoUploadValidationResult> {
  if (photo) return validatePhotoUpload(file)
  return Promise.resolve(isSupportedUploadVideo(file)
    ? { valid: true }
    : { valid: false, message: 'Formato de vídeo não aceito. Selecione um vídeo MP4 ou MOV.' })
}

function mensagemStatus(midia: MinhaMidiaGestao) {
  if (midia.status === 'PENDENTE') return 'Aguardando moderação'
  if (midia.status === 'PUBLICAVEL') return midia.restrita ? 'Aprovada e protegida' : 'Aprovada'
  if (midia.status === 'REJEITADA') return 'Não aprovada'
  if (midia.status === 'AJUSTE_SOLICITADO') return 'Ajuste solicitado'
  return midia.status
}

export function WizardStepFotos({
  slug,
  initialFiles,
  fotoNomes,
  onChange,
  videosNovos,
  onChangeVideosNovos,
  createProgress = {},
  createErrors = {},
  photoValidation = [],
  photoValidationPending = false,
  disabled = false,
  onAddBenefit,
  persistedState,
  onPersistedChange,
  onInteractionStart,
  onInteractionEnd,
  onStateUnconfirmed,
  pendingFiles: controlledPendingFiles,
  onPendingFilesChange,
  uploadUnconfirmed = false,
  onUploadUnconfirmedChange,
  pendingSaveNotice = false,
  accountScope = '',
}: WizardStepFotosProps) {
  const [persisted, setPersisted] = useState<MinhasMidiasResponse | null>(persistedState ?? null)
  const [loading, setLoading] = useState(Boolean(slug) && persistedState === undefined)
  const [busy, setBusy] = useState(false)
  const [removalIntent, setRemovalIntent] = useState<MinhaMidiaGestao | null>(null)
  const removalIntentRef = useRef<MinhaMidiaGestao | null>(null)
  const cancelRemovalButtonRef = useRef<HTMLButtonElement | null>(null)
  const operationGenerationRef = useRef(0)
  const mountedRef = useRef(true)
  const terminalRef = useRef(persistedState?.anuncio.status === 'REMOVIDO')
  const [stateUnconfirmed, setStateUnconfirmed] = useState(false)
  const stateUnconfirmedRef = useRef(false)
  const [progress, setProgress] = useState<Record<string, number>>({})
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [uncontrolledPendingFiles, setUncontrolledPendingFiles] = useState<EditPendingMedia[]>([])
  const pendingEntries = controlledPendingFiles ?? uncontrolledPendingFiles
  const pendingPersistedFiles = pendingEntries.map((entry) => entry.file)
  const [persistedValidation, setPersistedValidation] = useState<{
    selection: EditPendingMedia[]
    results: PhotoUploadValidationResult[]
  }>({ selection: [], results: [] })
  const [retryable, setRetryable] = useState(false)
  const pendingFilesRef = useRef(pendingEntries)
  pendingFilesRef.current = pendingEntries
  const uploadLockRef = useRef(false)
  const selectionVersionRef = useRef(0)
  const pendingSaveNoticeRef = useRef<HTMLParagraphElement | null>(null)
  const validationReady = persistedValidation.selection === pendingEntries
  const validationPending = pendingPersistedFiles.length > 0 && !validationReady
  const invalidSelection = validationReady && persistedValidation.results.some((result) => !result.valid)
  // The canonical total identifies the chosen photo only when the complete
  // response has no other photo identity. Multiple candidates remain ambiguous;
  // do not reimplement eligibility from status, previews or local selections.
  const removingLastValidPhoto = removalIntent?.tipo === 'FOTO'
    && persisted?.fotosValidasAtivasTotal === 1
    && persisted.midias.some((midia) => midia.tipo === 'FOTO' && midia.id === removalIntent.id)
    && persisted.midias.every((midia) => midia.tipo !== 'FOTO' || midia.id === removalIntent.id)

  useEffect(() => {
    mountedRef.current = true
    return () => { mountedRef.current = false; operationGenerationRef.current += 1 }
  }, [slug])

  useEffect(() => {
    if (pendingSaveNotice) pendingSaveNoticeRef.current?.focus()
  }, [pendingSaveNotice])

  useEffect(() => {
    if (persistedState === undefined || terminalRef.current) return
    setPersisted(persistedState)
    if (persistedState?.anuncio.status === 'REMOVIDO') terminalRef.current = true
  }, [persistedState])

  function beginInteraction() {
    if (disabled || uploadLockRef.current || terminalRef.current || stateUnconfirmedRef.current || !mountedRef.current) return false
    if (onInteractionStart && !onInteractionStart()) return false
    uploadLockRef.current = true
    operationGenerationRef.current += 1
    return true
  }

  function endInteraction() {
    uploadLockRef.current = false
    onInteractionEnd?.()
  }

  function acceptResponse(response: MinhasMidiasResponse, generation: number) {
    if (!mountedRef.current || generation !== operationGenerationRef.current || terminalRef.current) return false
    if (response.anuncio.slug !== slug) throw new Error('A resposta não corresponde ao anúncio em edição.')
    if (response.anuncio.status === 'REMOVIDO') terminalRef.current = true
    setPersisted(response)
    onPersistedChange?.(response)
    return true
  }

  function handleUnconfirmedState(error: unknown, destructiveRequest = false) {
    // A lost DELETE response may follow a committed closure. Do not infer that
    // the old active snapshot survived; deterministic client rejections remain usable.
    const ambiguousDelete = destructiveRequest && (!(error instanceof MeusAnunciosApiError)
      || error.status === 0 || error.status === 408 || error.status >= 500)
    if (ambiguousDelete || (error instanceof MeusAnunciosApiError && error.code === 'MIDIAS_ESTADO_NAO_CONFIRMADO')) {
      stateUnconfirmedRef.current = true
      setStateUnconfirmed(true)
      onStateUnconfirmed?.()
    }
  }

  useEffect(() => {
    let current = true
    const files = pendingEntries
    void Promise.all(files.map((entry) => validateSelectedMedia(entry.file, entry.kind === 'photo')))
      .then((results) => {
        if (current) setPersistedValidation({ selection: files, results })
      })
    return () => { current = false }
  }, [pendingEntries])

  const refresh = useCallback(async () => {
    if (!slug || persistedState !== undefined || terminalRef.current) return
    const generation = ++operationGenerationRef.current
    setLoading(true)
    try {
      const response = await listarMinhasMidias(slug)
      if (mountedRef.current && generation === operationGenerationRef.current && !terminalRef.current) {
        if (response.anuncio.slug !== slug) throw new Error('A resposta não corresponde ao anúncio em edição.')
        terminalRef.current = response.anuncio.status === 'REMOVIDO'
        setPersisted(response)
        onPersistedChange?.(response)
      }
    } catch (error) {
      if (mountedRef.current && generation === operationGenerationRef.current && !terminalRef.current) {
        setErrors({ carregar: error instanceof Error ? error.message : 'Não foi possível carregar as mídias.' })
      }
    } finally {
      if (mountedRef.current && generation === operationGenerationRef.current) setLoading(false)
    }
  }, [onPersistedChange, persistedState, slug])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const notifyPublicMediaChange = useCallback(async (changeFingerprint: string) => {
    if (!slug) return
    try {
      const anuncio = await buscarMeuAnuncio(slug)
      if (!anuncioEstaPublicamenteIndexavel(anuncio.status)) return
      void enviarIndexNowNoCliente(montarEventoIndexNowAnuncio({
        eventType: 'ATUALIZACAO',
        previous: {
          slug: anuncio.slug,
          estadoUf: anuncio.localizacao?.uf,
          cidadeNome: anuncio.localizacao?.cidade,
          bairroNome: anuncio.localizacao?.bairro,
        },
        current: {
          slug: anuncio.slug,
          estadoUf: anuncio.localizacao?.uf,
          cidadeNome: anuncio.localizacao?.cidade,
          bairroNome: anuncio.localizacao?.bairro,
        },
        changeFingerprint,
      }))
    } catch {
      // A alteracao de midia ja foi concluida; notificacao segue best-effort.
    }
  }, [slug])

  const uploadPersisted = async (entries: EditPendingMedia[]) => {
    const files = entries.map((entry) => entry.file)
    if (!slug || !files.length || disabled || busy || uploadLockRef.current || !validationReady || invalidSelection
      || entries !== pendingFilesRef.current || (errors.lote && !retryable)) return
    const fotosNovas = entries.filter((entry) => entry.kind === 'photo').length
    const videosNovos = files.length - fotosNovas
    if (!uploadUnconfirmed && persisted && fotosNovas > persisted.limites.fotosDisponiveis) {
      setErrors({ lote: 'Você atingiu o limite de fotos deste anúncio.' })
      return
    }
    if (!uploadUnconfirmed && persisted && videosNovos > persisted.limites.videosDisponiveis) {
      setErrors({
        lote: persisted.limites.videoAtivo
          ? 'Este anúncio já atingiu o limite de vídeos.'
          : 'Adicione um vídeo ao seu anúncio com o benefício Vídeo.',
      })
      return
    }
    const version = selectionVersionRef.current
    if (!beginInteraction()) return
    const generation = operationGenerationRef.current
    setBusy(true)
    setErrors({})
    try {
      const results = await Promise.all(entries.map((entry) => validateSelectedMedia(entry.file, entry.kind === 'photo')))
      if (version !== selectionVersionRef.current || entries !== pendingFilesRef.current || results.some((result) => !result.valid)) return
      if (uploadUnconfirmed) {
        // A prior response may have been lost after commit. Re-read the canonical
        // ad first; the same idempotency key then resolves any remaining ambiguity.
        const fresh = await listarMinhasMidias(slug)
        if (!acceptResponse(fresh, generation) || terminalRef.current) return
      }
      const latest = await enviarMinhasMidiasEmLote(slug, files, (value) => {
        if (mountedRef.current && generation === operationGenerationRef.current
          && version === selectionVersionRef.current && entries === pendingFilesRef.current) {
          setProgress((current) => Object.fromEntries([
            ...Object.entries(current),
            ...files.map((file) => [file.name, value] as const),
          ]))
        }
      }, accountScope)
      if (version === selectionVersionRef.current && entries === pendingFilesRef.current
        && acceptResponse(latest, generation)) updatePendingFiles([])
    } catch (error) {
      if (!mountedRef.current || generation !== operationGenerationRef.current) return
      const ambiguous = error instanceof TypeError || (error instanceof MeusAnunciosApiError
        && (error.status === 0 || error.status === 408 || error.status >= 500
          || error.code === 'MIDIAS_ESTADO_NAO_CONFIRMADO'))
      if (ambiguous) onUploadUnconfirmedChange?.(true)
      setRetryable(error instanceof TypeError || (error instanceof MeusAnunciosApiError
        && (error.status === 0 || error.status === 408 || error.status === 429 || error.status >= 500)))
      setErrors({
        lote: ambiguous
          ? 'Não foi possível confirmar o envio. Confira o estado do anúncio e tente novamente.'
          : meusAnunciosErrorMessage(error, 'Falha ao enviar os arquivos.'),
      })
    } finally {
      endInteraction()
      if (mountedRef.current) setBusy(false)
    }
  }

  const pendingPersistedPhotos = pendingEntries.filter((entry) => entry.kind === 'photo')
  const pendingPersistedVideos = pendingEntries.filter((entry) => entry.kind === 'video')

  function updatePendingFiles(files: EditPendingMedia[]) {
    selectionVersionRef.current += 1
    pendingFilesRef.current = files
    if (onPendingFilesChange) onPendingFilesChange(files)
    else setUncontrolledPendingFiles(files)
    onUploadUnconfirmedChange?.(false)
    setErrors({})
    setProgress({})
    setRetryable(false)
  }

  function selectPersistedFiles(files: File[], photos = true) {
    if (busy || uploadLockRef.current || disabled || terminalRef.current) return
    if (photos) {
      updatePendingFiles([...pendingFilesRef.current, ...files.map((file) => ({ file, kind: 'photo' as const }))])
    } else {
      updatePendingFiles([
        ...pendingFilesRef.current.filter((entry) => entry.kind === 'photo'),
        ...files.map((file) => ({ file, kind: 'video' as const })),
      ])
    }
  }

  function removePendingPersistedFile(entry: EditPendingMedia) {
    if (busy || uploadLockRef.current || disabled || terminalRef.current) return
    updatePendingFiles(pendingFilesRef.current.filter((item) => item !== entry))
  }

  const move = async (index: number, direction: -1 | 1) => {
    if (!slug || !persisted || busy || disabled || terminalRef.current) return
    const nextIndex = index + direction
    if (nextIndex < 0 || nextIndex >= persisted.midias.length) return
    const next = [...persisted.midias]
    ;[next[index], next[nextIndex]] = [next[nextIndex], next[index]]
    if (!beginInteraction()) return
    const generation = operationGenerationRef.current
    setBusy(true)
    try {
      const mediaIds = next.map((item) => item.id)
      if (acceptResponse(await reordenarMinhasMidias(slug, mediaIds), generation) && !terminalRef.current) {
        void notifyPublicMediaChange(`reorder:${mediaIds.join(',')}`)
      }
    } catch (error) {
      if (mountedRef.current && generation === operationGenerationRef.current) {
        handleUnconfirmedState(error)
        setErrors({ ordenar: error instanceof Error ? error.message : 'Não foi possível alterar a ordem.' })
      }
    } finally {
      endInteraction()
      if (mountedRef.current) setBusy(false)
    }
  }

  const requestRemoval = (midia: MinhaMidiaGestao) => {
    if (!slug || busy || !persisted?.midias.some((item) => item.id === midia.id) || !beginInteraction()) return
    removalIntentRef.current = midia
    setRemovalIntent(midia)
  }

  const cancelRemoval = () => {
    if (busy || !removalIntentRef.current) return
    removalIntentRef.current = null
    setRemovalIntent(null)
    endInteraction()
  }

  const confirmRemoval = async () => {
    const midia = removalIntentRef.current
    if (!slug || !midia || busy || disabled || terminalRef.current || !uploadLockRef.current) return
    // Consume the intent synchronously: two clicks cannot send two DELETEs.
    removalIntentRef.current = null
    const generation = operationGenerationRef.current
    setBusy(true)
    try {
      if (acceptResponse(await removerMinhaMidia(slug, midia.id), generation) && !terminalRef.current) {
        void notifyPublicMediaChange(`remove:${midia.id}`)
      }
    } catch (error) {
      if (mountedRef.current && generation === operationGenerationRef.current) {
        handleUnconfirmedState(error, true)
        setErrors({ remover: meusAnunciosErrorMessage(error, 'Não foi possível remover a mídia.') })
      }
    } finally {
      endInteraction()
      if (mountedRef.current) { setBusy(false); setRemovalIntent(null) }
    }
  }

  if (!slug) {
    return (
      <StepPanel>
        <section className="space-y-5 rounded-[28px] border border-zinc-200/80 bg-white p-5 shadow-[0_18px_60px_rgba(24,24,27,0.04)] sm:p-6">
          <header className="flex items-start gap-3">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border border-zinc-200 bg-zinc-50 text-zinc-700">
              <Camera className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-lg font-semibold text-zinc-950">Fotos do anúncio</h3>
            </div>
          </header>
          <p className="text-sm text-zinc-600">{PHOTO_UPLOAD_GUIDANCE}</p>
          <FilePicker
            ariaLabel="Selecionar fotos do anúncio"
            buttonLabel="Selecionar fotos"
            accept={PHOTO_UPLOAD_ACCEPT}
            files={initialFiles}
            multiple
            disabled={disabled}
            onSelect={(files) => onChange([...initialFiles, ...files])}
            onRemove={(index) => onChange(initialFiles.filter((_, fileIndex) => fileIndex !== index))}
          />
          {initialFiles.map((file, index) => {
            const result = photoValidation[index]
            return (
              <p key={`${fileKey(file)}:${index}`} role={!photoValidationPending && result && !result.valid ? 'alert' : 'status'} className="text-sm text-zinc-700">
                {file.name}: {photoValidationPending || !result ? 'Verificando foto…' : result.valid ? 'Foto pronta para envio.' : result.message}
              </p>
            )
          })}
          {initialFiles.length > 4 ? <p role="alert" className="text-sm text-red-700">Você pode adicionar até 4 fotos gratuitamente. Remova o excedente para continuar.</p> : null}
          <p className="text-sm text-zinc-600">
            Você pode adicionar até 4 fotos gratuitamente.
          </p>
          <p className="text-xs text-zinc-500">
            {fotoNomes.length ? `${fotoNomes.length} foto(s) selecionada(s).` : 'Galeria de fotos'}
          </p>
        </section>
        <section className="space-y-5 rounded-[28px] border border-zinc-200/80 bg-white p-5 shadow-[0_18px_60px_rgba(24,24,27,0.04)] sm:p-6">
          <header className="flex items-start gap-3">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border border-zinc-200 bg-zinc-50 text-zinc-700">
              <PlayCircle className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-lg font-semibold text-zinc-950">Vídeo do anúncio</h3>
              <p className="mt-1 text-sm leading-6 text-zinc-600">Vídeos são sempre protegidos e aguardam moderação.</p>
            </div>
          </header>
          <VideoUploader
            newVideos={videosNovos}
            onChangeNew={onChangeVideosNovos}
            canUpload={false}
            onAddBenefit={onAddBenefit}
          />
          {[...initialFiles, ...videosNovos].map((file) => {
            const key = fileKey(file)
            const value = createProgress[key]
            const error = createErrors[key]
            if (value === undefined && !error) return null
            return (
              <div key={key} className="space-y-1 text-xs">
                <div className="flex justify-between gap-3 text-zinc-600">
                  <span className="truncate">{file.name}</span>
                  {value !== undefined ? <span>{value}%</span> : null}
                </div>
                {value !== undefined ? (
                  <div className="h-1.5 overflow-hidden rounded-full bg-zinc-200">
                    <div className="h-full bg-pink-500" style={{ width: `${value}%` }} />
                  </div>
                ) : null}
                {error ? <p className="font-medium text-red-700">{error}</p> : null}
              </div>
            )
          })}
        </section>
      </StepPanel>
    )
  }

  if (stateUnconfirmed) {
    return <StepPanel><p role="alert">Não foi possível confirmar o estado do anúncio. Volte para Meus anúncios para conferir antes de continuar.</p></StepPanel>
  }

  if (persisted?.anuncio.status === 'REMOVIDO') {
    return (
      <StepPanel>
        <section role="alert" className="space-y-3 rounded-xl border border-amber-300 bg-amber-50 p-6">
          <h2 className="text-2xl font-semibold">Anúncio encerrado</h2>
          <p>Seu anúncio foi encerrado e não está mais disponível.</p>
        </section>
        <button type="button" onClick={() => window.location.assign('/meus-anuncios')} className="min-h-11 rounded-xl border px-4 py-2">
          Voltar para Meus anúncios
        </button>
      </StepPanel>
    )
  }

  return (
    <StepPanel>
      <Dialog open={removalIntent !== null} onOpenChange={(open) => { if (!open) cancelRemoval() }}>
        <DialogContent showCloseButton={!busy} role="alertdialog" onOpenAutoFocus={(event) => {
          event.preventDefault()
          cancelRemovalButtonRef.current?.focus()
        }}>
          <DialogHeader className="pr-6 text-left">
            <DialogTitle className="leading-6">{removalIntent?.tipo === 'FOTO'
              ? removingLastValidPhoto ? 'Excluir a última foto?' : 'Excluir foto?'
              : 'Excluir vídeo do anúncio?'}</DialogTitle>
            <DialogDescription>
              {removalIntent?.tipo === 'FOTO'
                ? removingLastValidPhoto
                  ? 'Ao excluir esta foto, seu anúncio será encerrado. Deseja continuar?'
                  : 'Ao excluir a última foto, seu anúncio será encerrado. Deseja continuar?'
                : 'O vídeo será removido do anúncio. Você pode cancelar antes de confirmar.'}
            </DialogDescription>
          </DialogHeader>
          {removalIntent?.tipo === 'FOTO' ? (
            <div className="space-y-2 text-sm text-zinc-600">
              <p>Para trocar a foto, envie a nova antes de excluir a atual.</p>
              <p>Se o anúncio já estiver aprovado, aguarde a aprovação da nova foto antes de excluir a última foto aprovada.</p>
            </div>
          ) : null}
          <DialogFooter className="flex-col sm:flex-row">
            <button ref={cancelRemovalButtonRef} type="button" disabled={busy} onClick={cancelRemoval} className="min-h-11 rounded-xl border px-4 py-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-zinc-950">Cancelar</button>
            <button type="button" disabled={busy} onClick={() => void confirmRemoval()} className="min-h-11 min-w-0 rounded-xl bg-red-700 px-4 py-2 text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-700 focus-visible:ring-offset-2">{busy ? 'Excluindo…' : removalIntent?.tipo === 'FOTO'
              ? removingLastValidPhoto ? 'Excluir foto e encerrar anúncio' : 'Excluir foto'
              : 'Excluir vídeo'}</button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <section className="space-y-5 rounded-[28px] border border-zinc-200/80 bg-white p-5 shadow-[0_18px_60px_rgba(24,24,27,0.04)] sm:p-6">
        <header className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex items-start gap-3">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border border-zinc-200 bg-zinc-50 text-zinc-700">
              <Camera className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-lg font-semibold text-zinc-950">Fotos e vídeo</h3>
              <p className="mt-1 max-w-2xl text-sm leading-6 text-zinc-600">
                Novas mídias ficam privadas e pendentes. A classificação é definida pela moderação.
              </p>
            </div>
          </div>
          {persisted ? (
            <div className="rounded-2xl border border-zinc-200 bg-zinc-50 px-4 py-3 text-xs text-zinc-600">
              <p>Fotos: {persisted.limites.fotosAtivas}/{persisted.limites.maxFotos}</p>
              <p>Vídeos: {persisted.limites.videosAtivos}/{persisted.limites.maxVideos}</p>
            </div>
          ) : null}
        </header>

        {pendingSaveNotice && pendingEntries.length ? (
          <p
            ref={pendingSaveNoticeRef}
            role="alert"
            tabIndex={-1}
            className="rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-700"
          >
            Você selecionou arquivos que ainda não foram enviados. Clique em Enviar arquivos para concluir.
          </p>
        ) : null}

        <p className="text-sm text-zinc-600">{PHOTO_UPLOAD_GUIDANCE}</p>
        <div className="grid gap-4 sm:grid-cols-2">
          <FilePicker
            ariaLabel="Selecionar fotos do anúncio"
            buttonLabel="Selecionar foto"
            accept={PHOTO_UPLOAD_ACCEPT}
            files={pendingPersistedPhotos.map((entry) => entry.file)}
            multiple
            disabled={disabled || busy || !persisted || (persisted.limites.fotosDisponiveis === 0 && !pendingPersistedPhotos.length)}
            helperText={persisted?.limites.fotosExtrasAtivo
              ? 'Seu anúncio permite até 10 fotos com o benefício de fotos extras.'
              : 'Você pode adicionar até 4 fotos gratuitamente.'}
            onSelect={(files) => selectPersistedFiles(files)}
            onRemove={(index) => removePendingPersistedFile(pendingPersistedPhotos[index])}
          />
          {persisted?.limites.videoAtivo ? (
            <FilePicker
              ariaLabel="Selecionar vídeo do anúncio"
              buttonLabel="Selecionar vídeo"
              accept="video/mp4,video/quicktime,.mp4,.mov"
              files={pendingPersistedVideos.map((entry) => entry.file)}
              disabled={disabled || busy || (persisted.limites.videosDisponiveis === 0 && !pendingPersistedVideos.length)}
              helperText="Você pode adicionar 1 vídeo em MP4 ou MOV."
              onSelect={(files) => selectPersistedFiles(files.slice(0, 1), false)}
              onRemove={(index) => removePendingPersistedFile(pendingPersistedVideos[index])}
            />
          ) : (
            <div className="rounded-xl border border-zinc-200 bg-zinc-50 p-4">
              <p className="font-semibold text-zinc-900">Vídeo do anúncio</p>
              <p className="mt-1 text-sm text-zinc-600">
                Adicione um vídeo ao seu anúncio com o benefício Vídeo.
              </p>
              <button type="button" disabled={disabled || busy || Boolean(removalIntent) || terminalRef.current} onClick={onAddBenefit} className="mt-3 rounded-xl border border-zinc-300 bg-white px-4 py-2 text-sm font-semibold">
                Adicionar benefício
              </button>
            </div>
          )}
        </div>

        {pendingEntries.map((entry, index) => {
          const file = entry.file
          const result = validationReady ? persistedValidation.results[index] : undefined
          return (
            <p key={`${fileKey(file)}:${index}`} role={result && !result.valid ? 'alert' : 'status'} className="text-sm text-zinc-700">
              {file.name}: {!result ? entry.kind === 'photo' ? 'Verificando foto…' : 'Verificando vídeo…' : result.valid ? 'Arquivo pronto para envio.' : result.message}
            </p>
          )
        })}

        {Object.keys(progress).length ? (
          <div className="space-y-2 text-xs text-zinc-600">
            {Object.entries(progress).map(([name, value]) => (
              <div key={name}>
                <div className="mb-1 flex justify-between gap-3"><span className="truncate">{name}</span><span>{value}%</span></div>
                <div className="h-1.5 overflow-hidden rounded-full bg-zinc-200"><div className="h-full bg-pink-500" style={{ width: `${value}%` }} /></div>
              </div>
            ))}
          </div>
        ) : null}

        {Object.entries(errors).map(([key, message]) => (
          <p key={key} className="rounded-xl bg-red-50 px-3 py-2 text-xs font-medium text-red-700">{message}</p>
        ))}

        {pendingEntries.length ? (
          <button
            type="button"
            disabled={disabled || busy || !persisted || validationPending || invalidSelection || Boolean(errors.lote && !retryable)}
            onClick={() => void uploadPersisted(pendingEntries)}
            className="min-h-11 rounded-xl border border-zinc-300 bg-white px-4 text-sm font-semibold text-zinc-900 disabled:opacity-50"
          >
            {busy ? 'Enviando...' : validationPending ? pendingPersistedVideos.length ? 'Verificando arquivos…' : 'Verificando foto…' : errors.lote && retryable ? 'Tentar enviar novamente' : 'Enviar arquivos'}
          </button>
        ) : null}

        {loading ? (
          <div className="flex min-h-40 items-center justify-center"><Loader2 className="h-6 w-6 animate-spin text-zinc-400" /></div>
        ) : persisted?.midias.length ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {persisted.midias.map((midia, index) => (
              <article key={midia.id} className="min-w-0 overflow-hidden rounded-2xl border border-zinc-200 bg-zinc-50">
                <div className="flex aspect-[4/3] items-center justify-center overflow-hidden bg-zinc-200">
                  {midia.previewUrl && midia.tipo === 'VIDEO' ? (
                    <video src={midia.previewUrl} controls className="h-full w-full bg-black object-contain" />
                  ) : midia.previewUrl ? (
                    <img src={midia.previewUrl} alt="Prévia da mídia" className="h-full w-full object-cover" />
                  ) : (
                    <span className="px-4 text-center text-xs text-zinc-500">Prévia protegida ou indisponível</span>
                  )}
                </div>
                <div className="space-y-3 p-4">
                  <div className="text-xs text-zinc-600">
                    <p className="font-semibold text-zinc-900">{midia.tipo === 'VIDEO' ? 'Vídeo' : 'Foto'} · posição {index + 1}</p>
                    <p>{mensagemStatus(midia)}</p>
                    {midia.ocultaPorLimite ? <p className="font-medium text-amber-700">Oculta enquanto exceder o limite vigente</p> : null}
                  </div>
                  <div className="flex items-center gap-2">
                    <button type="button" title="Mover para cima" disabled={disabled || busy || Boolean(removalIntent) || terminalRef.current || index === 0} onClick={() => void move(index, -1)} className="flex h-9 w-9 items-center justify-center rounded-full border border-zinc-200 bg-white disabled:opacity-40"><ArrowUp className="h-4 w-4" /></button>
                    <button type="button" title="Mover para baixo" disabled={disabled || busy || Boolean(removalIntent) || terminalRef.current || index === persisted.midias.length - 1} onClick={() => void move(index, 1)} className="flex h-9 w-9 items-center justify-center rounded-full border border-zinc-200 bg-white disabled:opacity-40"><ArrowDown className="h-4 w-4" /></button>
                    <button type="button" title="Remover mídia" disabled={disabled || busy || Boolean(removalIntent) || terminalRef.current} onClick={() => requestRemoval(midia)} className="ml-auto flex h-9 w-9 items-center justify-center rounded-full border border-red-200 bg-white text-red-600 disabled:opacity-40"><Trash2 className="h-4 w-4" /></button>
                  </div>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <p className="rounded-xl bg-zinc-50 px-4 py-3 text-sm text-zinc-600">Este anúncio ainda não possui mídias.</p>
        )}
      </section>
    </StepPanel>
  )
}
