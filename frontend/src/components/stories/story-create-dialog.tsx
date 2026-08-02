'use client'

import Link from 'next/link'
import { useEffect, useRef, useState } from 'react'
import {
  CircleCheck,
  CircleDollarSign,
  FileImage,
  Megaphone,
  Trash2,
  Upload,
  Video,
} from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { formatStoryDate, getStoryEntryState } from '@/components/stories/story-entry-state'
import {
  comprarBeneficios,
  fetchMinhaMonetizacao,
  newPremiumPurchaseIdempotencyKey,
  type MinhaMonetizacaoBackend,
} from '@/features/monetizacao-wizard/api'
import {
  buscarMeuAnuncio,
  consultarLimitesMinhasMidias,
  MeusAnunciosApiError,
  publicarMeuAnuncioStory,
  type MeuAnuncio,
  type MeuAnuncioStory,
  type MinhasMidiasLimites,
} from '@/lib/meus-anuncios-api'
import { cn } from '@/lib/utils'

type StoryMode = 'ANUNCIO' | 'MIDIA_UPLOAD'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  anuncio: MeuAnuncio
  returnFocusTo?: HTMLElement | null
  onAnuncioChange: (anuncio: MeuAnuncio) => void
  onSuccess: (story: MeuAnuncioStory) => void
}

const ACCEPTED_MEDIA = '.jpg,.jpeg,.png,.webp,.mp4,image/jpeg,image/png,image/webp,video/mp4'

function formatBytes(bytes: number) {
  return new Intl.NumberFormat('pt-BR', {
    style: 'unit',
    unit: 'megabyte',
    maximumFractionDigits: 0,
  }).format(bytes / 1024 / 1024)
}

function errorMessage(error: unknown) {
  if (!(error instanceof MeusAnunciosApiError)) {
    return 'Não foi possível publicar o Story. Tente novamente.'
  }
  if (error.code === 'STORY_JA_ATIVO') {
    return 'Este anúncio já possui um Story ativo. Aguarde a expiração para publicar outro.'
  }
  if (error.status === 401) return 'Sua sessão expirou. Entre novamente para continuar.'
  if (error.status === 403) return 'Você não tem permissão para publicar este Story.'
  if (error.status === 409) return error.message || 'O estado do anúncio ou do benefício mudou.'
  if (error.status === 413) return 'O arquivo ultrapassa o limite permitido.'
  if (error.status === 415) return 'Use uma foto JPG, PNG ou WebP, ou um vídeo MP4 compatível.'
  if (error.status === 422) return error.message || 'A mídia não pôde ser processada.'
  return error.message || 'Não foi possível publicar o Story. Tente novamente.'
}

function purchaseErrorMessage(error: unknown) {
  if (error instanceof Error && error.message.trim()) return error.message
  return 'Não foi possível ativar Stories. Tente novamente.'
}

function pluralizeCredits(value: number) {
  return `${value} ${value === 1 ? 'crédito' : 'créditos'}`
}

export function StoryCreateDialog({
  open,
  onOpenChange,
  anuncio,
  returnFocusTo,
  onAnuncioChange,
  onSuccess,
}: Props) {
  const inputRef = useRef<HTMLInputElement | null>(null)
  const publishIdempotencyKeyRef = useRef<string | null>(null)
  const purchaseIdempotencyKeyRef = useRef<string | null>(null)
  const [mode, setMode] = useState<StoryMode | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [limits, setLimits] = useState<MinhasMidiasLimites | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [progress, setProgress] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<MeuAnuncioStory | null>(null)
  const [accessGranted, setAccessGranted] = useState(false)
  const [monetizacao, setMonetizacao] = useState<MinhaMonetizacaoBackend | null>(null)
  const [loadingAcquisition, setLoadingAcquisition] = useState(false)
  const [purchasing, setPurchasing] = useState(false)
  const [purchaseError, setPurchaseError] = useState<string | null>(null)
  const [selectedDuration, setSelectedDuration] = useState<number | null>(null)

  const entry = getStoryEntryState(anuncio)
  const activeStory = result ?? anuncio.storyAtivo
  const canCreate = accessGranted || entry.kind === 'READY'
  const storyCatalog = monetizacao?.catalogo.find(
    (item) => item.codigo === 'STORIES' && item.ativo
  ) ?? null
  const storyOptions = storyCatalog?.opcoes.filter((option) => option.ativo) ?? []
  const selectedOption = storyOptions.find((option) => option.duracaoDias === selectedDuration) ?? null
  const busy = submitting || purchasing

  useEffect(() => {
    if (!open) {
      setMode(null)
      setFile(null)
      setLimits(null)
      setSubmitting(false)
      setProgress(0)
      setError(null)
      setResult(null)
      setAccessGranted(false)
      setMonetizacao(null)
      setLoadingAcquisition(false)
      setPurchasing(false)
      setPurchaseError(null)
      setSelectedDuration(null)
      publishIdempotencyKeyRef.current = null
      purchaseIdempotencyKeyRef.current = null
      return
    }

    setMode(null)
    setFile(null)
    setLimits(null)
    setProgress(0)
    setError(null)
    setResult(null)
    setAccessGranted(entry.kind === 'READY')
    setMonetizacao(null)
    setPurchaseError(null)
    setSelectedDuration(null)
    publishIdempotencyKeyRef.current = null
    purchaseIdempotencyKeyRef.current = null
  }, [anuncio.id, entry.kind, open])

  useEffect(() => {
    if (!open || activeStory || canCreate || entry.kind !== 'NEEDS_ACTIVATION') return
    let cancelled = false
    setLoadingAcquisition(true)
    setPurchaseError(null)
    void fetchMinhaMonetizacao(anuncio.slug)
      .then((value) => {
        if (cancelled) return
        setMonetizacao(value)
        const catalog = value.catalogo.find((item) => item.codigo === 'STORIES' && item.ativo)
        const firstOption = catalog?.opcoes.find((option) => option.ativo) ?? null
        setSelectedDuration(firstOption?.duracaoDias ?? null)
      })
      .catch((cause) => {
        if (!cancelled) setPurchaseError(purchaseErrorMessage(cause))
      })
      .finally(() => {
        if (!cancelled) setLoadingAcquisition(false)
      })
    return () => {
      cancelled = true
    }
  }, [activeStory, anuncio.slug, canCreate, entry.kind, open])

  useEffect(() => {
    if (!open || activeStory || !canCreate) return
    let cancelled = false
    void consultarLimitesMinhasMidias(anuncio.slug)
      .then((value) => {
        if (!cancelled) setLimits(value)
      })
      .catch(() => {
        if (!cancelled) setLimits(null)
      })
    return () => {
      cancelled = true
    }
  }, [activeStory, anuncio.slug, canCreate, open])

  useEffect(() => {
    if (!file) {
      setPreview(null)
      return
    }
    const url = URL.createObjectURL(file)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  function resetPublishIntent() {
    publishIdempotencyKeyRef.current = null
    setError(null)
    setProgress(0)
  }

  function selectMode(next: StoryMode) {
    if (next === mode) return
    setMode(next)
    if (next === 'ANUNCIO') setFile(null)
    resetPublishIntent()
  }

  function selectFile(next: File | null) {
    setFile(next)
    resetPublishIntent()
  }

  async function purchaseStories() {
    if (!selectedOption || !storyCatalog || !monetizacao) return
    if (monetizacao.saldoCreditos < selectedOption.custoCreditos) {
      setPurchaseError('Seu saldo não é suficiente para esta opção de Stories.')
      return
    }

    const key = purchaseIdempotencyKeyRef.current ?? newPremiumPurchaseIdempotencyKey()
    purchaseIdempotencyKeyRef.current = key
    setPurchasing(true)
    setPurchaseError(null)
    try {
      const purchase = await comprarBeneficios(
        anuncio.slug,
        [{ beneficioCodigo: storyCatalog.codigo, duracaoDias: selectedOption.duracaoDias }],
        key
      )
      const storyActivation = purchase.ativacoes.find(
        (activation) => activation.beneficioCodigo === 'STORIES'
      )
      if (!storyActivation) {
        throw new Error('A ativação de Stories não foi confirmada pelo servidor.')
      }
      setAccessGranted(true)
      purchaseIdempotencyKeyRef.current = null
      toast.success('Stories ativado. Escolha agora como deseja publicar.')
      try {
        onAnuncioChange(await buscarMeuAnuncio(anuncio.slug))
      } catch {
        setPurchaseError('Stories foi ativado, mas a atualização do anúncio não pôde ser concluída. Você ainda pode publicar agora.')
      }
    } catch (cause) {
      setPurchaseError(purchaseErrorMessage(cause))
    } finally {
      setPurchasing(false)
    }
  }

  async function publish() {
    if (!mode) {
      setError('Escolha como você quer aparecer nos Stories.')
      return
    }
    if (mode === 'MIDIA_UPLOAD' && !file) {
      setError('Selecione uma foto ou um vídeo para publicar.')
      return
    }
    const key = publishIdempotencyKeyRef.current ?? crypto.randomUUID()
    publishIdempotencyKeyRef.current = key
    setSubmitting(true)
    setProgress(0)
    setError(null)
    try {
      const story = await publicarMeuAnuncioStory(
        anuncio.slug,
        mode,
        mode === 'MIDIA_UPLOAD' ? file : null,
        key,
        setProgress
      )
      setResult(story)
      onSuccess(story)
      toast.success('Seu Story foi publicado.')
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setSubmitting(false)
    }
  }

  const title = activeStory
    ? 'Gerenciar Story'
    : entry.kind === 'UNAVAILABLE'
      ? 'Stories indisponíveis para este anúncio'
      : canCreate
        ? 'Como você quer aparecer nos Stories?'
        : 'Ativar Stories'

  const description = activeStory
    ? 'Consulte o modo e a vigência informados pelo servidor.'
    : entry.kind === 'UNAVAILABLE'
      ? entry.reason
      : canCreate
        ? 'Escolha o anúncio ou envie uma mídia exclusiva para esta publicação.'
        : 'Escolha uma opção do catálogo de Stories para continuar.'

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!busy) onOpenChange(next)
      }}
    >
      <DialogContent
        className="flex max-h-[100dvh] w-[calc(100vw-1rem)] max-w-lg flex-col overflow-hidden p-0 sm:max-h-[calc(100dvh-2rem)]"
        onOpenAutoFocus={(event) => {
          event.preventDefault()
          const content = event.currentTarget as HTMLElement | null
          if (!content) return
          requestAnimationFrame(() => {
            content.querySelector<HTMLElement>('input:not(:disabled), button:not(:disabled), a[href]')?.focus()
          })
        }}
        onCloseAutoFocus={(event) => {
          event.preventDefault()
          requestAnimationFrame(() => {
            if (returnFocusTo?.isConnected) returnFocusTo.focus()
          })
        }}
      >
        <div className="overflow-y-auto px-5 py-5 sm:px-6">
          <DialogHeader className="pr-7 text-left">
            <DialogTitle>{title}</DialogTitle>
            <DialogDescription>{description}</DialogDescription>
          </DialogHeader>

          {activeStory ? (
            <div className="mt-5 space-y-4" role="status">
              <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-emerald-950">
                <div className="flex items-center gap-2 font-semibold">
                  <CircleCheck className="h-5 w-5" aria-hidden="true" />
                  {result ? 'Seu Story foi publicado.' : 'Story ativo'}
                </div>
                <dl className="mt-3 grid gap-2 text-sm">
                  <div><dt className="inline font-medium">Modo: </dt><dd className="inline">{activeStory.modoConteudo === 'ANUNCIO' ? 'Divulgar meu anúncio' : 'Mídia enviada'}</dd></div>
                  <div><dt className="inline font-medium">Status: </dt><dd className="inline">{activeStory.status}</dd></div>
                  <div><dt className="inline font-medium">Início: </dt><dd className="inline">{formatStoryDate(activeStory.inicioEm)}</dd></div>
                  <div><dt className="inline font-medium">Expira em: </dt><dd className="inline">{formatStoryDate(activeStory.fimEm)}</dd></div>
                  {activeStory.modoConteudo === 'MIDIA_UPLOAD' ? (
                    <div>
                      <dt className="inline font-medium">Mídia: </dt>
                      <dd className="inline">{activeStory.estadoMidia === 'INDISPONIVEL' ? 'Indisponível' : 'Disponível'}</dd>
                    </div>
                  ) : null}
                </dl>
              </div>
              <div className="grid gap-2 sm:grid-cols-2">
                <Button asChild variant="outline">
                  <Link href={`/anuncios/${encodeURIComponent(anuncio.slug)}`}>Ver anúncio</Link>
                </Button>
                <Button type="button" onClick={() => onOpenChange(false)}>Concluir</Button>
              </div>
            </div>
          ) : entry.kind === 'UNAVAILABLE' ? (
            <div className="mt-5 space-y-4">
              <p className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900" role="status">
                {entry.reason}
              </p>
              <Button type="button" variant="outline" className="w-full" onClick={() => onOpenChange(false)}>
                Fechar
              </Button>
            </div>
          ) : !canCreate ? (
            <div className="mt-5 space-y-5">
              {loadingAcquisition ? (
                <p className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700" role="status">
                  Carregando opções de Stories...
                </p>
              ) : storyCatalog && storyOptions.length > 0 && monetizacao ? (
                <>
                  <div className="rounded-lg border border-pink-200 bg-pink-50 p-4">
                    <div className="flex items-center gap-2 font-semibold text-slate-950">
                      <CircleDollarSign className="h-5 w-5 text-[#FC1EAD]" aria-hidden="true" />
                      {storyCatalog.nome}
                    </div>
                    <p className="mt-1 text-sm leading-6 text-slate-600">{storyCatalog.descricao}</p>
                    <p className="mt-3 text-sm font-medium text-slate-800">
                      Saldo disponível: {pluralizeCredits(monetizacao.saldoCreditos)}
                    </p>
                  </div>

                  <fieldset className="space-y-2">
                    <legend className="text-sm font-semibold text-slate-900">Duração e créditos</legend>
                    {storyOptions.map((option) => {
                      const checked = selectedDuration === option.duracaoDias
                      return (
                        <label
                          key={`${option.duracaoDias}-${option.custoCreditos}`}
                          className={cn(
                            'flex cursor-pointer items-center gap-3 rounded-lg border p-3 transition focus-within:ring-2 focus-within:ring-[#FC1EAD]',
                            checked ? 'border-[#FC1EAD] bg-pink-50' : 'border-slate-200 hover:border-pink-300'
                          )}
                        >
                          <input
                            type="radio"
                            name="story-duration"
                            checked={checked}
                            onChange={() => {
                              setSelectedDuration(option.duracaoDias)
                              setPurchaseError(null)
                              purchaseIdempotencyKeyRef.current = null
                            }}
                            disabled={purchasing}
                            className="shrink-0 accent-[#FC1EAD]"
                          />
                          <span className="min-w-0 flex-1 text-sm text-slate-800">
                            <span className="font-semibold">{option.duracaoDias} dias</span>
                            <span className="mx-1.5 text-slate-400">•</span>
                            <span>{pluralizeCredits(option.custoCreditos)}</span>
                          </span>
                        </label>
                      )
                    })}
                  </fieldset>

                  {selectedOption && monetizacao.saldoCreditos < selectedOption.custoCreditos ? (
                    <p className="text-sm leading-6 text-amber-800">
                      Faltam {pluralizeCredits(selectedOption.custoCreditos - monetizacao.saldoCreditos)} para esta opção.{' '}
                      <Link href="/creditos" className="font-semibold underline underline-offset-2">Consultar créditos e planos</Link>
                    </p>
                  ) : null}
                </>
              ) : purchaseError ? null : (
                <p className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900" role="status">
                  Stories não está disponível no catálogo retornado pelo servidor para esta conta.
                </p>
              )}

              {purchaseError ? (
                <p className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800" role="alert">
                  {purchaseError}
                </p>
              ) : null}

              <div className="grid gap-2 sm:grid-cols-2">
                <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={purchasing}>
                  Cancelar
                </Button>
                <Button
                  type="button"
                  onClick={() => void purchaseStories()}
                  disabled={
                    purchasing
                    || loadingAcquisition
                    || !selectedOption
                    || !monetizacao
                    || monetizacao.saldoCreditos < selectedOption.custoCreditos
                  }
                >
                  {purchasing ? 'Ativando Stories...' : 'Ativar e continuar'}
                </Button>
              </div>
            </div>
          ) : (
            <div className="mt-5 space-y-5">
              <fieldset className="grid gap-3">
                <legend className="sr-only">Modo do Story</legend>
                <label className={cn(
                  'flex cursor-pointer gap-3 rounded-lg border p-4 transition focus-within:ring-2 focus-within:ring-[#FC1EAD]',
                  mode === 'ANUNCIO' ? 'border-[#FC1EAD] bg-pink-50' : 'border-slate-200 hover:border-slate-300'
                )}>
                  <input
                    type="radio"
                    name="story-mode"
                    value="ANUNCIO"
                    checked={mode === 'ANUNCIO'}
                    onChange={() => selectMode('ANUNCIO')}
                    disabled={submitting}
                    className="mt-1 accent-[#FC1EAD]"
                  />
                  <Megaphone className="mt-0.5 h-5 w-5 shrink-0 text-[#FC1EAD]" aria-hidden="true" />
                  <span className="min-w-0">
                    <span className="block font-semibold text-slate-950">Divulgar meu anúncio</span>
                    <span className="mt-1 block text-sm leading-5 text-slate-600">Exibe seu anúncio nos Stories com suas informações públicas e o botão Ver anúncio.</span>
                  </span>
                </label>

                <label className={cn(
                  'flex cursor-pointer gap-3 rounded-lg border p-4 transition focus-within:ring-2 focus-within:ring-[#FC1EAD]',
                  mode === 'MIDIA_UPLOAD' ? 'border-[#FC1EAD] bg-pink-50' : 'border-slate-200 hover:border-slate-300'
                )}>
                  <input
                    type="radio"
                    name="story-mode"
                    value="MIDIA_UPLOAD"
                    checked={mode === 'MIDIA_UPLOAD'}
                    onChange={() => selectMode('MIDIA_UPLOAD')}
                    disabled={submitting}
                    className="mt-1 accent-[#FC1EAD]"
                  />
                  <Upload className="mt-0.5 h-5 w-5 shrink-0 text-[#FC1EAD]" aria-hidden="true" />
                  <span className="min-w-0">
                    <span className="block font-semibold text-slate-950">Enviar uma mídia</span>
                    <span className="mt-1 block text-sm leading-5 text-slate-600">Envie uma foto ou um vídeo exclusivo para este Story.</span>
                  </span>
                </label>
              </fieldset>

              {mode === 'MIDIA_UPLOAD' ? (
                <section className="space-y-3" aria-label="Mídia exclusiva do Story">
                  <input
                    ref={inputRef}
                    type="file"
                    accept={ACCEPTED_MEDIA}
                    className="sr-only"
                    onChange={(event) => selectFile(event.target.files?.item(0) ?? null)}
                    disabled={submitting}
                  />
                  {!file ? (
                    <button
                      type="button"
                      onClick={() => inputRef.current?.click()}
                      disabled={submitting}
                      className="flex min-h-32 w-full flex-col items-center justify-center rounded-lg border border-dashed border-slate-300 bg-slate-50 px-4 text-center transition hover:border-[#FC1EAD] hover:bg-pink-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#FC1EAD] disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      <Upload className="h-6 w-6 text-slate-500" aria-hidden="true" />
                      <span className="mt-2 text-sm font-semibold text-slate-800">Selecionar foto ou vídeo</span>
                      <span className="mt-1 text-xs leading-5 text-slate-500">JPG, PNG, WebP ou MP4</span>
                      {limits ? (
                        <span className="text-xs leading-5 text-slate-500">Foto até {formatBytes(limits.maxFotoBytes)}; vídeo até {formatBytes(limits.maxVideoBytes)}</span>
                      ) : null}
                    </button>
                  ) : (
                    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                      <div className="flex max-h-64 min-h-36 items-center justify-center overflow-hidden rounded-lg bg-slate-950">
                        {file.type.startsWith('video/') ? (
                          <video src={preview ?? undefined} controls className="max-h-64 max-w-full object-contain" />
                        ) : (
                          // eslint-disable-next-line @next/next/no-img-element
                          <img src={preview ?? undefined} alt="Prévia da mídia do Story" className="max-h-64 max-w-full object-contain" />
                        )}
                      </div>
                      <div className="mt-3 flex min-w-0 items-center gap-2">
                        {file.type.startsWith('video/') ? <Video className="h-4 w-4 shrink-0" /> : <FileImage className="h-4 w-4 shrink-0" />}
                        <span className="min-w-0 flex-1 truncate text-sm text-slate-700" title={file.name}>{file.name}</span>
                        <Button
                          type="button"
                          size="icon"
                          variant="outline"
                          onClick={() => {
                            selectFile(null)
                            if (inputRef.current) inputRef.current.value = ''
                          }}
                          disabled={submitting}
                          aria-label="Remover mídia selecionada"
                          title="Remover mídia"
                        >
                          <Trash2 className="h-4 w-4 text-rose-600" aria-hidden="true" />
                        </Button>
                      </div>
                      <Button type="button" variant="outline" className="mt-2 w-full" onClick={() => inputRef.current?.click()} disabled={submitting}>
                        Substituir mídia
                      </Button>
                    </div>
                  )}
                </section>
              ) : null}

              {submitting ? (
                <div className="space-y-2" role="status" aria-live="polite">
                  <div className="flex justify-between text-sm text-slate-700">
                    <span>{mode === 'ANUNCIO'
                      ? 'Publicando Story...'
                      : progress < 99 ? 'Enviando mídia...' : 'Processando mídia...'}</span>
                    <span>{progress}%</span>
                  </div>
                  <div
                    className="h-2 overflow-hidden rounded-full bg-slate-200"
                    role="progressbar"
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-valuenow={progress}
                    aria-label="Progresso da publicação do Story"
                  >
                    <div className="h-full bg-[#FC1EAD] transition-[width]" style={{ width: `${progress}%` }} />
                  </div>
                </div>
              ) : null}

              {error ? <p className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800" role="alert">{error}</p> : null}

              <div className="grid gap-2 sm:grid-cols-2">
                <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={submitting}>Cancelar</Button>
                <Button type="button" onClick={() => void publish()} disabled={submitting || !mode || (mode === 'MIDIA_UPLOAD' && !file)}>
                  {submitting ? 'Publicando Story...' : 'Publicar nos Stories'}
                </Button>
              </div>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
