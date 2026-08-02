'use client'

import Link from 'next/link'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  CircleCheck,
  CircleDollarSign,
  FileImage,
  Megaphone,
  RefreshCw,
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
  newPremiumPurchaseIdempotencyKey,
  PremiumApiError,
} from '@/features/monetizacao-wizard/api'
import {
  consultarLimitesMinhasMidias,
  consultarMeuAnuncioStoryOferta,
  MeusAnunciosApiError,
  publicarMeuAnuncioStory,
  type MeuAnuncio,
  type MeuAnuncioStory,
  type MeuAnuncioStoryOferta,
  type MeuAnuncioStoryOfertaOpcao,
  type MinhasMidiasLimites,
} from '@/lib/meus-anuncios-api'
import { cn } from '@/lib/utils'

type StoryMode = 'ANUNCIO' | 'MIDIA_UPLOAD'
type StoryStep = 'ESCOLHER_CONTEUDO' | 'CONFIGURAR_CONTEUDO' | 'REVISAR' | 'VERIFICAR_DIREITO_E_OFERTA'

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

function pluralizeCredits(value: number) {
  return `${value} ${value === 1 ? 'crédito' : 'créditos'}`
}

function storyErrorMessage(error: unknown) {
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

function requestIdSuffix(error: unknown) {
  const requestId = error instanceof MeusAnunciosApiError || error instanceof PremiumApiError
    ? error.requestId
    : null
  return requestId ? ` Código de atendimento: ${requestId}.` : ''
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
  const offerRequestSequenceRef = useRef(0)
  const offerRequestRef = useRef<{ sequence: number; slug: string } | null>(null)
  const [step, setStep] = useState<StoryStep>('ESCOLHER_CONTEUDO')
  const [mode, setMode] = useState<StoryMode | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [limits, setLimits] = useState<MinhasMidiasLimites | null>(null)
  const [offer, setOffer] = useState<MeuAnuncioStoryOferta | null>(null)
  const [selectedOptionId, setSelectedOptionId] = useState<string | null>(null)
  const [loadingOffer, setLoadingOffer] = useState(false)
  const [purchasing, setPurchasing] = useState(false)
  const [publishing, setPublishing] = useState(false)
  const [progress, setProgress] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<MeuAnuncioStory | null>(null)
  const [purchaseCompleted, setPurchaseCompleted] = useState(false)
  const [awaitingCredits, setAwaitingCredits] = useState(false)

  const entry = getStoryEntryState(anuncio)
  const activeStory = result ?? offer?.storyAtivo ?? anuncio.storyAtivo
  const selectedOption = offer?.opcoes.find((item) => item.opcaoId === selectedOptionId) ?? null
  const busy = loadingOffer || purchasing || publishing

  const loadOffer = useCallback(async () => {
    const slug = anuncio.slug
    if (offerRequestRef.current?.slug === slug) return null
    const sequence = ++offerRequestSequenceRef.current
    offerRequestRef.current = { sequence, slug }
    setLoadingOffer(true)
    setError(null)
    try {
      const next = await consultarMeuAnuncioStoryOferta(slug)
      if (offerRequestSequenceRef.current !== sequence) return null
      setOffer(next)
      setSelectedOptionId((current) => {
        if (next.opcoes.length === 1) return next.opcoes[0].opcaoId
        return next.opcoes.some((item) => item.opcaoId === current) ? current : null
      })
      return next
    } catch (cause) {
      if (offerRequestSequenceRef.current !== sequence) return null
      setOffer(null)
      setError(`Não foi possível carregar as opções de Stories.${requestIdSuffix(cause)}`)
      return null
    } finally {
      if (offerRequestSequenceRef.current === sequence) {
        offerRequestRef.current = null
        setLoadingOffer(false)
      }
    }
  }, [anuncio.slug])

  useEffect(() => {
    offerRequestSequenceRef.current += 1
    offerRequestRef.current = null
    if (!open) {
      setStep('ESCOLHER_CONTEUDO')
      setMode(null)
      setFile(null)
      setLimits(null)
      setOffer(null)
      setSelectedOptionId(null)
      setLoadingOffer(false)
      setPurchasing(false)
      setPublishing(false)
      setProgress(0)
      setError(null)
      setResult(null)
      setPurchaseCompleted(false)
      setAwaitingCredits(false)
      publishIdempotencyKeyRef.current = null
      purchaseIdempotencyKeyRef.current = null
      return
    }
    setStep('ESCOLHER_CONTEUDO')
    setMode(null)
    setFile(null)
    setOffer(null)
    setSelectedOptionId(null)
    setProgress(0)
    setError(null)
    setResult(null)
    setPurchaseCompleted(false)
    setAwaitingCredits(false)
    publishIdempotencyKeyRef.current = null
    purchaseIdempotencyKeyRef.current = null
  }, [anuncio.id, open])

  useEffect(() => {
    if (!open || activeStory || entry.kind === 'UNAVAILABLE') return
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
  }, [activeStory, anuncio.slug, entry.kind, open])

  useEffect(() => {
    if (!file) {
      setPreview(null)
      return
    }
    const url = URL.createObjectURL(file)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  useEffect(() => {
    if (!open || !awaitingCredits || step !== 'VERIFICAR_DIREITO_E_OFERTA') return
    const refreshOnReturn = () => {
      if (document.visibilityState === 'visible') {
        setAwaitingCredits(false)
        void loadOffer()
      }
    }
    window.addEventListener('focus', refreshOnReturn)
    document.addEventListener('visibilitychange', refreshOnReturn)
    return () => {
      window.removeEventListener('focus', refreshOnReturn)
      document.removeEventListener('visibilitychange', refreshOnReturn)
    }
  }, [awaitingCredits, loadOffer, open, step])

  function selectMode(next: StoryMode) {
    if (next === mode) return
    setMode(next)
    if (next === 'ANUNCIO') setFile(null)
    setOffer(null)
    setError(null)
    publishIdempotencyKeyRef.current = null
  }

  function selectFile(next: File | null) {
    setFile(next)
    setOffer(null)
    setError(null)
    publishIdempotencyKeyRef.current = null
  }

  async function publishStory(activatedNow = purchaseCompleted) {
    if (!mode || (mode === 'MIDIA_UPLOAD' && !file)) return
    const key = publishIdempotencyKeyRef.current ?? crypto.randomUUID()
    publishIdempotencyKeyRef.current = key
    setPublishing(true)
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
      onAnuncioChange({ ...anuncio, storyAtivo: story })
      onSuccess(story)
      toast.success('Seu Story foi publicado.')
    } catch (cause) {
      setError(activatedNow
        ? `Seu direito de Story foi ativado, mas a publicação não foi concluída. Tente novamente. Nenhuma nova cobrança será feita.${requestIdSuffix(cause)}`
        : `${storyErrorMessage(cause)}${requestIdSuffix(cause)}`)
    } finally {
      setPublishing(false)
    }
  }

  async function activateAndPublish(option: MeuAnuncioStoryOfertaOpcao) {
    if (option.creditosFaltantes > 0) return
    const key = purchaseIdempotencyKeyRef.current ?? newPremiumPurchaseIdempotencyKey()
    purchaseIdempotencyKeyRef.current = key
    setPurchasing(true)
    setError(null)
    try {
      const purchase = await comprarBeneficios(
        anuncio.slug,
        [{
          beneficioCodigo: 'STORIES',
          duracaoDias: option.duracaoDias,
          opcaoId: option.opcaoId,
          custoCreditosEsperado: option.custoCreditos,
        }],
        key
      )
      if (!purchase.ativacoes.some((activation) => activation.beneficioCodigo === 'STORIES')) {
        throw new Error('A ativação de Stories não foi confirmada pelo servidor.')
      }
      setPurchaseCompleted(true)
      setPurchasing(false)
      await publishStory(true)
    } catch (cause) {
      if (cause instanceof PremiumApiError && cause.code === 'PREMIUM_OFERTA_ATUALIZADA') {
        purchaseIdempotencyKeyRef.current = null
        await loadOffer()
        setError(`As condições desta opção foram atualizadas. Confira o novo valor antes de continuar.${requestIdSuffix(cause)}`)
      } else {
        setError(`${cause instanceof Error && cause.message.trim()
          ? cause.message
          : 'Não foi possível ativar Stories. Tente novamente.'}${requestIdSuffix(cause)}`)
      }
    } finally {
      setPurchasing(false)
    }
  }

  function advanceToReview() {
    if (!mode) return
    if (mode === 'MIDIA_UPLOAD' && !file) {
      setError('Selecione uma foto ou um vídeo para continuar.')
      return
    }
    setError(null)
    setStep('REVISAR')
  }

  const title = activeStory
    ? 'Gerenciar Story'
    : entry.kind === 'UNAVAILABLE'
      ? 'Stories indisponíveis para este anúncio'
      : step === 'ESCOLHER_CONTEUDO'
        ? 'Como você quer aparecer nos Stories?'
        : step === 'CONFIGURAR_CONTEUDO'
          ? 'Configure seu Story'
          : step === 'REVISAR'
            ? 'Revise seu Story'
            : 'Confirme a publicação'

  const description = activeStory
    ? 'Consulte o modo e a vigência informados pelo servidor.'
    : entry.kind === 'UNAVAILABLE'
      ? entry.reason
      : 'A ativação, quando necessária, será confirmada somente depois da revisão.'

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!busy) onOpenChange(next) }}>
      <DialogContent
        className="flex max-h-[100dvh] w-[calc(100vw-1rem)] max-w-lg flex-col overflow-hidden p-0 sm:max-h-[calc(100dvh-2rem)]"
        onEscapeKeyDown={(event) => { if (busy) event.preventDefault() }}
        onOpenAutoFocus={(event) => {
          event.preventDefault()
          const content = event.currentTarget as HTMLElement | null
          requestAnimationFrame(() => {
            content?.querySelector<HTMLElement>('input:not(:disabled), button:not(:disabled), a[href]')?.focus()
          })
        }}
        onCloseAutoFocus={(event) => {
          event.preventDefault()
          requestAnimationFrame(() => {
            if (returnFocusTo?.isConnected) returnFocusTo.focus()
          })
        }}
      >
        <div className="min-h-0 overflow-y-auto px-5 py-5 sm:px-6">
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
                    <div><dt className="inline font-medium">Mídia: </dt><dd className="inline">{activeStory.estadoMidia === 'INDISPONIVEL' ? 'Indisponível' : 'Disponível'}</dd></div>
                  ) : null}
                </dl>
              </div>
              <div className="grid gap-2 sm:grid-cols-2">
                <Button asChild variant="outline"><Link href={`/anuncios/${encodeURIComponent(anuncio.slug)}`}>Ver anúncio</Link></Button>
                <Button type="button" onClick={() => onOpenChange(false)}>Concluir</Button>
              </div>
            </div>
          ) : entry.kind === 'UNAVAILABLE' ? (
            <div className="mt-5 space-y-4">
              <p className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900" role="status">{entry.reason}</p>
              <Button type="button" variant="outline" className="w-full" onClick={() => onOpenChange(false)}>Fechar</Button>
            </div>
          ) : step === 'ESCOLHER_CONTEUDO' ? (
            <div className="mt-5 space-y-5">
              <fieldset className="grid gap-3">
                <legend className="sr-only">Modo do Story</legend>
                <label className={cn('flex cursor-pointer gap-3 rounded-lg border p-4 transition focus-within:ring-2 focus-within:ring-[#FC1EAD]', mode === 'ANUNCIO' ? 'border-[#FC1EAD] bg-pink-50' : 'border-slate-200')}>
                  <input type="radio" name="story-mode" value="ANUNCIO" checked={mode === 'ANUNCIO'} onChange={() => selectMode('ANUNCIO')} className="mt-1 accent-[#FC1EAD]" />
                  <Megaphone className="mt-0.5 h-5 w-5 shrink-0 text-[#FC1EAD]" aria-hidden="true" />
                  <span><span className="block font-semibold">Divulgar meu anúncio</span><span className="mt-1 block text-sm text-slate-600">Use os dados públicos e a capa do anúncio.</span></span>
                </label>
                <label className={cn('flex cursor-pointer gap-3 rounded-lg border p-4 transition focus-within:ring-2 focus-within:ring-[#FC1EAD]', mode === 'MIDIA_UPLOAD' ? 'border-[#FC1EAD] bg-pink-50' : 'border-slate-200')}>
                  <input type="radio" name="story-mode" value="MIDIA_UPLOAD" checked={mode === 'MIDIA_UPLOAD'} onChange={() => selectMode('MIDIA_UPLOAD')} className="mt-1 accent-[#FC1EAD]" />
                  <Upload className="mt-0.5 h-5 w-5 shrink-0 text-[#FC1EAD]" aria-hidden="true" />
                  <span><span className="block font-semibold">Enviar uma mídia</span><span className="mt-1 block text-sm text-slate-600">Escolha uma foto ou um vídeo exclusivo para este Story.</span></span>
                </label>
              </fieldset>
              <div className="grid gap-2 sm:grid-cols-2">
                <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Cancelar</Button>
                <Button type="button" disabled={!mode} onClick={() => setStep('CONFIGURAR_CONTEUDO')}>Continuar</Button>
              </div>
            </div>
          ) : step === 'CONFIGURAR_CONTEUDO' ? (
            <div className="mt-5 space-y-5">
              {mode === 'ANUNCIO' ? (
                <section className="rounded-lg border border-slate-200 bg-slate-50 p-4" aria-label="Resumo do anúncio">
                  <p className="font-semibold text-slate-950">{anuncio.titulo}</p>
                  <p className="mt-1 text-sm text-slate-600">O Story usará somente os dados públicos e a capa atual deste anúncio.</p>
                </section>
              ) : (
                <section className="space-y-3" aria-label="Mídia exclusiva do Story">
                  <input ref={inputRef} type="file" accept={ACCEPTED_MEDIA} className="sr-only" onChange={(event) => selectFile(event.target.files?.item(0) ?? null)} />
                  {!file ? (
                    <button type="button" onClick={() => inputRef.current?.click()} className="flex min-h-32 w-full flex-col items-center justify-center rounded-lg border border-dashed border-slate-300 bg-slate-50 px-4 text-center focus-visible:ring-2 focus-visible:ring-[#FC1EAD]">
                      <Upload className="h-6 w-6 text-slate-500" aria-hidden="true" />
                      <span className="mt-2 text-sm font-semibold">Selecionar foto ou vídeo</span>
                      <span className="mt-1 text-xs text-slate-500">JPG, PNG, WebP ou MP4</span>
                      {limits ? <span className="text-xs text-slate-500">Foto até {formatBytes(limits.maxFotoBytes)}; vídeo até {formatBytes(limits.maxVideoBytes)}</span> : null}
                    </button>
                  ) : (
                    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                      <div className="flex max-h-64 min-h-36 items-center justify-center overflow-hidden rounded-lg bg-slate-950">
                        {file.type.startsWith('video/')
                          ? <video src={preview ?? undefined} controls className="max-h-64 max-w-full object-contain" />
                          // eslint-disable-next-line @next/next/no-img-element
                          : <img src={preview ?? undefined} alt="Prévia da mídia do Story" className="max-h-64 max-w-full object-contain" />}
                      </div>
                      <div className="mt-3 flex min-w-0 items-center gap-2">
                        {file.type.startsWith('video/') ? <Video className="h-4 w-4 shrink-0" /> : <FileImage className="h-4 w-4 shrink-0" />}
                        <span className="min-w-0 flex-1 truncate text-sm" title={file.name}>{file.name}</span>
                        <Button type="button" size="icon" variant="outline" onClick={() => { selectFile(null); if (inputRef.current) inputRef.current.value = '' }} aria-label="Remover mídia selecionada" title="Remover mídia">
                          <Trash2 className="h-4 w-4 text-rose-600" aria-hidden="true" />
                        </Button>
                      </div>
                      <Button type="button" variant="outline" className="mt-2 w-full" onClick={() => inputRef.current?.click()}>Substituir mídia</Button>
                    </div>
                  )}
                </section>
              )}
              {error ? <p className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800" role="alert">{error}</p> : null}
              <div className="grid gap-2 sm:grid-cols-2">
                <Button type="button" variant="outline" onClick={() => { setError(null); setStep('ESCOLHER_CONTEUDO') }}>Voltar</Button>
                <Button type="button" disabled={!mode || (mode === 'MIDIA_UPLOAD' && !file)} onClick={advanceToReview}>Revisar Story</Button>
              </div>
            </div>
          ) : step === 'REVISAR' ? (
            <div className="mt-5 space-y-5">
              <section className="rounded-lg border border-slate-200 p-4" aria-label="Revisão do Story">
                <p className="text-xs font-semibold uppercase text-slate-500">Anúncio</p>
                <p className="mt-1 font-semibold">{anuncio.titulo}</p>
                <p className="mt-3 text-xs font-semibold uppercase text-slate-500">Modo</p>
                <p className="mt-1 text-sm">{mode === 'ANUNCIO' ? 'Divulgar meu anúncio' : 'Mídia enviada'}</p>
                {mode === 'MIDIA_UPLOAD' && file ? (
                  <div className="mt-3 flex max-h-64 min-h-36 items-center justify-center overflow-hidden rounded-lg bg-slate-950">
                    {file.type.startsWith('video/')
                      ? <video src={preview ?? undefined} controls className="max-h-64 max-w-full object-contain" />
                      // eslint-disable-next-line @next/next/no-img-element
                      : <img src={preview ?? undefined} alt="Revisão da mídia do Story" className="max-h-64 max-w-full object-contain" />}
                  </div>
                ) : null}
              </section>
              <p className="text-sm text-slate-600">Nenhum crédito será debitado e nenhum arquivo será enviado antes da confirmação final.</p>
              <div className="grid gap-2 sm:grid-cols-2">
                <Button type="button" variant="outline" onClick={() => setStep('CONFIGURAR_CONTEUDO')}>Voltar</Button>
                <Button type="button" onClick={() => { setStep('VERIFICAR_DIREITO_E_OFERTA'); void loadOffer() }}>Verificar condição para publicar</Button>
              </div>
            </div>
          ) : (
            <div className="mt-5 space-y-5">
              {loadingOffer ? <p className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm" role="status">Verificando direito, oferta e saldo...</p> : null}

              {!loadingOffer && (purchaseCompleted || offer?.estado === 'DIREITO_DISPONIVEL') ? (
                <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-950" role="status">
                  <p className="font-semibold">Você já possui um direito de Story disponível.</p>
                  <p className="mt-1">Nenhuma nova cobrança será feita.</p>
                  {offer?.direitoDisponivel?.status === 'ATIVA' && offer.direitoDisponivel.fimEm ? (
                    <p className="mt-1">Direito vigente até {formatStoryDate(offer.direitoDisponivel.fimEm)}. A publicação não prolonga essa data.</p>
                  ) : null}
                </div>
              ) : null}

              {!loadingOffer && !purchaseCompleted && offer?.estado === 'OPCOES_DISPONIVEIS' ? (
                <>
                  <div className="rounded-lg border border-pink-200 bg-pink-50 p-4">
                    <div className="flex items-center gap-2 font-semibold"><CircleDollarSign className="h-5 w-5 text-[#FC1EAD]" aria-hidden="true" />{offer.nome || 'Stories'}</div>
                    {offer.descricao ? <p className="mt-1 text-sm text-slate-600">{offer.descricao}</p> : null}
                  </div>
                  <fieldset className="space-y-2">
                    <legend className="text-sm font-semibold">Duração e créditos</legend>
                    {offer.opcoes.map((option) => (
                      <label key={option.opcaoId} className={cn('flex cursor-pointer items-center gap-3 rounded-lg border p-3 focus-within:ring-2 focus-within:ring-[#FC1EAD]', selectedOptionId === option.opcaoId ? 'border-[#FC1EAD] bg-pink-50' : 'border-slate-200')}>
                        <input type="radio" name="story-offer" checked={selectedOptionId === option.opcaoId} onChange={() => { setSelectedOptionId(option.opcaoId); setError(null); purchaseIdempotencyKeyRef.current = null }} disabled={busy} className="accent-[#FC1EAD]" />
                        <span className="min-w-0 flex-1 text-sm"><strong>{option.duracaoDias} dias</strong> · {pluralizeCredits(option.custoCreditos)}</span>
                      </label>
                    ))}
                  </fieldset>
                  {selectedOption ? (
                    <dl className="grid gap-2 rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm">
                      <div><dt className="inline font-medium">Custo: </dt><dd className="inline">{pluralizeCredits(selectedOption.custoCreditos)}</dd></div>
                      <div><dt className="inline font-medium">Seu saldo: </dt><dd className="inline">{pluralizeCredits(selectedOption.saldoAtual)}</dd></div>
                      {selectedOption.creditosFaltantes === 0
                        ? <div><dt className="inline font-medium">Saldo após a compra: </dt><dd className="inline">{pluralizeCredits(selectedOption.saldoAposCompra)}</dd></div>
                        : <div className="text-amber-900"><dt className="inline font-medium">Faltam: </dt><dd className="inline">{pluralizeCredits(selectedOption.creditosFaltantes)}</dd></div>}
                    </dl>
                  ) : <p className="text-sm text-amber-900">Escolha uma opção para continuar.</p>}
                  {selectedOption && selectedOption.creditosFaltantes > 0 ? (
                    <div className="space-y-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950">
                      <p>Você precisa de {pluralizeCredits(selectedOption.custoCreditos)}. Seu saldo atual é {pluralizeCredits(selectedOption.saldoAtual)}.</p>
                      {awaitingCredits ? <p>Após comprar os créditos, volte para esta página.</p> : null}
                      <div className="grid gap-2 sm:grid-cols-2">
                        <Button asChild><a href="/creditos" target="_blank" rel="noopener noreferrer" onClick={() => setAwaitingCredits(true)}>Comprar créditos</a></Button>
                        <Button type="button" variant="outline" onClick={() => void loadOffer()} disabled={loadingOffer}><RefreshCw className="mr-2 h-4 w-4" aria-hidden="true" />Atualizar saldo</Button>
                      </div>
                    </div>
                  ) : null}
                </>
              ) : null}

              {!loadingOffer && !purchaseCompleted && offer?.estado === 'NOVAS_ATIVACOES_INDISPONIVEIS' ? (
                <p className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950" role="status">Stories está temporariamente indisponível para novas ativações.</p>
              ) : null}

              {publishing ? (
                <div className="space-y-2" role="status" aria-live="polite">
                  <div className="flex justify-between text-sm"><span>{mode === 'ANUNCIO' ? 'Publicando Story...' : progress < 99 ? 'Enviando mídia...' : 'Processando mídia...'}</span><span>{progress}%</span></div>
                  <div className="h-2 overflow-hidden rounded-full bg-slate-200" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress} aria-label="Progresso da publicação do Story"><div className="h-full bg-[#FC1EAD] transition-[width]" style={{ width: `${progress}%` }} /></div>
                </div>
              ) : null}

              {error ? <p className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800" role="alert">{error}</p> : null}

              <div className="grid gap-2 sm:grid-cols-2">
                <Button type="button" variant="outline" onClick={() => { setError(null); setStep('REVISAR') }} disabled={busy}>Voltar</Button>
                {purchaseCompleted || offer?.estado === 'DIREITO_DISPONIVEL' ? (
                  <Button type="button" onClick={() => void publishStory(purchaseCompleted)} disabled={publishing}>Publicar Story</Button>
                ) : selectedOption && selectedOption.creditosFaltantes === 0 ? (
                  <Button type="button" onClick={() => void activateAndPublish(selectedOption)} disabled={busy}>{purchasing ? 'Ativando...' : publishing ? 'Publicando...' : selectedOption.custoCreditos === 0 ? 'Ativar gratuitamente e publicar' : `Ativar por ${pluralizeCredits(selectedOption.custoCreditos)} e publicar`}</Button>
                ) : !offer && !loadingOffer ? (
                  <Button type="button" onClick={() => void loadOffer()}>Tentar novamente</Button>
                ) : null}
              </div>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
