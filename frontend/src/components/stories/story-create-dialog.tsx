'use client'

import Link from 'next/link'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { CircleCheck, CircleDollarSign, FileImage, ImagePlus, Megaphone, RefreshCw, Trash2, Upload, Video } from 'lucide-react'
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
  ativarMinhaContaStory,
  consultarMinhaContaStoryOferta,
  publicarMinhaContaStory,
  type MinhaContaStory,
  type MinhaContaStoryOferta,
  type StoryMode,
} from '@/lib/minha-conta-stories-api'
import { MeusAnunciosApiError, type MeuAnuncio } from '@/lib/meus-anuncios-api'

type Step = 'ESCOLHER_MODO' | 'SELECIONAR_ANUNCIO' | 'CONFIGURAR_MIDIA' | 'REVISAR' | 'OFERTA'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  anuncios: MeuAnuncio[]
  initialAnuncioId?: string | null
  returnFocusTo?: HTMLElement | null
  onSuccess: (story: MinhaContaStory) => void
}

const ACCEPTED_MEDIA = '.jpg,.jpeg,.png,.webp,.mp4,image/jpeg,image/png,image/webp,video/mp4'

function selectedStoryFileError(file: File) {
  const extension = file.name.trim().toLowerCase().split('.').pop() ?? ''
  const isVideo = file.type.startsWith('video/') || extension === 'mp4' || extension === 'mov'
  if (!isVideo) return null
  if (extension !== 'mp4' || file.type.toLowerCase() === 'video/quicktime') {
    return 'Vídeos MOV não são compatíveis com Stories. Converta o arquivo para MP4 com vídeo H.264 e áudio AAC-LC.'
  }
  return null
}

function pluralizeCredits(value: number) {
  return `${value} ${value === 1 ? 'crédito' : 'créditos'}`
}

function requestIdSuffix(error: unknown) {
  const requestId = error instanceof MeusAnunciosApiError ? error.requestId : null
  return requestId ? ` Código de atendimento: ${requestId}.` : ''
}

function storyErrorMessage(error: unknown) {
  if (!(error instanceof MeusAnunciosApiError)) return 'Não foi possível publicar o Story. Tente novamente.'
  if (error.code === 'STORY_JA_ATIVO') return 'Este anúncio já possui um Story ativo.'
  if (error.status === 401) return 'Sua sessão expirou. Entre novamente para continuar.'
  if (error.status === 403) return 'Você não tem permissão para publicar este Story.'
  if (error.status === 409) return error.message || 'O estado do anúncio ou do direito mudou.'
  if (error.status === 413) return 'O arquivo ultrapassa o limite permitido.'
  if (error.status === 415) return error.message || 'Use uma foto JPG, PNG ou WebP, ou um vídeo MP4 compatível.'
  if (error.status === 422) return error.message || 'A mídia não pôde ser processada.'
  return error.message || 'Não foi possível publicar o Story. Tente novamente.'
}

export function StoryCreateDialog({
  open,
  onOpenChange,
  anuncios,
  initialAnuncioId = null,
  returnFocusTo,
  onSuccess,
}: Props) {
  const inputRef = useRef<HTMLInputElement | null>(null)
  const publishKeyRef = useRef<string | null>(null)
  const activationKeyRef = useRef<string | null>(null)
  const initialTargetId = initialAnuncioId && anuncios.some((item) => item.id === initialAnuncioId)
    ? initialAnuncioId
    : null
  const [step, setStep] = useState<Step>('ESCOLHER_MODO')
  const [mode, setMode] = useState<StoryMode | null>(null)
  const [anuncioId, setAnuncioId] = useState<string | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [offer, setOffer] = useState<MinhaContaStoryOferta | null>(null)
  const [loadingOffer, setLoadingOffer] = useState(false)
  const [activating, setActivating] = useState(false)
  const [publishing, setPublishing] = useState(false)
  const [progress, setProgress] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<MinhaContaStory | null>(null)
  const [activationCompleted, setActivationCompleted] = useState(false)
  const [awaitingCredits, setAwaitingCredits] = useState(false)

  const selectedAnuncio = useMemo(
    () => anuncios.find((item) => item.id === anuncioId) ?? null,
    [anuncioId, anuncios]
  )
  const eligibleAnuncios = useMemo(
    () => anuncios.filter((item) => getStoryEntryState(item).kind !== 'UNAVAILABLE'),
    [anuncios]
  )
  const activeStory = result ?? selectedAnuncio?.storyAtivo ?? null
  const busy = loadingOffer || activating || publishing

  useEffect(() => {
    if (!open) return
    setMode(initialTargetId ? 'ANUNCIO' : null)
    setAnuncioId(initialTargetId)
    setStep(initialTargetId ? 'REVISAR' : 'ESCOLHER_MODO')
    setFile(null)
    setOffer(null)
    setError(null)
    setResult(null)
    setProgress(0)
    setActivationCompleted(false)
    setAwaitingCredits(false)
    publishKeyRef.current = null
    activationKeyRef.current = null
  }, [initialTargetId, open])

  useEffect(() => {
    if (!file) {
      setPreview(null)
      return
    }
    const url = URL.createObjectURL(file)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  const loadOffer = useCallback(async () => {
    if (!mode) return null
    setLoadingOffer(true)
    setError(null)
    try {
      const next = await consultarMinhaContaStoryOferta(mode, mode === 'ANUNCIO' ? anuncioId : null)
      setOffer(next)
      return next
    } catch (cause) {
      setOffer(null)
      setError(`Não foi possível carregar a oferta de Stories.${requestIdSuffix(cause)}`)
      return null
    } finally {
      setLoadingOffer(false)
    }
  }, [anuncioId, mode])

  useEffect(() => {
    if (!open || !awaitingCredits || step !== 'OFERTA') return
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

  function resetIntent() {
    setOffer(null)
    setError(null)
    setResult(null)
    setActivationCompleted(false)
    publishKeyRef.current = null
    activationKeyRef.current = null
  }

  function chooseMode(nextMode: StoryMode) {
    resetIntent()
    setMode(nextMode)
    setFile(null)
    if (nextMode === 'MIDIA_UPLOAD') {
      setAnuncioId(null)
      setStep('CONFIGURAR_MIDIA')
      return
    }
    if (eligibleAnuncios.length === 0) return
    if (eligibleAnuncios.length === 1) {
      setAnuncioId(eligibleAnuncios[0].id)
      setStep('REVISAR')
      return
    }
    setAnuncioId(null)
    setStep('SELECIONAR_ANUNCIO')
  }

  function selectFile(next: File | null) {
    setFile(next)
    resetIntent()
    setError(next ? selectedStoryFileError(next) : null)
  }

  async function publishStory(activatedNow = activationCompleted) {
    if (!mode || publishing) return
    if (mode === 'ANUNCIO' && !anuncioId) return
    if (mode === 'MIDIA_UPLOAD' && !file) return
    const key = publishKeyRef.current ?? crypto.randomUUID()
    publishKeyRef.current = key
    setPublishing(true)
    setProgress(0)
    setError(null)
    try {
      const story = await publicarMinhaContaStory({
        modoConteudo: mode,
        anuncioId: mode === 'ANUNCIO' ? anuncioId : null,
        arquivo: mode === 'MIDIA_UPLOAD' ? file : null,
        idempotencyKey: key,
        onProgress: mode === 'MIDIA_UPLOAD' ? setProgress : undefined,
      })
      setResult(story)
      onSuccess(story)
      toast.success(mode === 'ANUNCIO' ? 'Seu anúncio foi publicado nos Stories.' : 'Seu Story foi publicado.')
    } catch (cause) {
      setError(activatedNow
        ? `Seu direito de Story foi ativado, mas a publicação não foi concluída. ${storyErrorMessage(cause)} Nenhuma nova cobrança será feita.${requestIdSuffix(cause)}`
        : `${storyErrorMessage(cause)}${requestIdSuffix(cause)}`)
    } finally {
      setPublishing(false)
    }
  }

  async function activateAndPublish() {
    if (!mode || activating || offer?.estado !== 'OFERTA_DISPONIVEL'
        || offer.deficit !== 0 || offer.custoCreditos == null || offer.versaoConfiguracao == null) return
    const key = activationKeyRef.current ?? crypto.randomUUID()
    activationKeyRef.current = key
    setActivating(true)
    setError(null)
    try {
      await ativarMinhaContaStory(
        mode,
        mode === 'ANUNCIO' ? anuncioId : null,
        offer.custoCreditos,
        offer.versaoConfiguracao,
        key
      )
      setActivationCompleted(true)
      setActivating(false)
      await publishStory(true)
    } catch (cause) {
      if (cause instanceof MeusAnunciosApiError && cause.status === 409) {
        activationKeyRef.current = null
        await loadOffer()
        setError(`A condição de Stories mudou. Confira disponibilidade, custo e saldo antes de continuar.${requestIdSuffix(cause)}`)
      } else {
        setError(`${storyErrorMessage(cause)}${requestIdSuffix(cause)}`)
      }
    } finally {
      setActivating(false)
    }
  }

  const title = activeStory
    ? 'Gerenciar Story'
    : step === 'ESCOLHER_MODO'
      ? 'Como você quer publicar?'
      : mode === 'ANUNCIO'
        ? 'Promover anúncio nos Stories'
        : 'Enviar uma mídia aos Stories'
  const description = activeStory
    ? 'Consulte o modo e a vigência informados pelo servidor.'
    : mode === 'MIDIA_UPLOAD'
      ? 'A foto ou o vídeo será exclusivo do Story, restrito a maiores de 18 anos e ficará ativo por 24 horas.'
      : 'Promova um anúncio público da sua conta por 24 horas.'

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!busy) onOpenChange(next) }}>
      <DialogContent
        className="flex max-h-[calc(100dvh-1rem)] w-[calc(100vw-1rem)] flex-col overflow-hidden p-0 sm:max-h-[calc(100dvh-2rem)] sm:max-w-xl"
        onCloseAutoFocus={(event) => {
          event.preventDefault()
          requestAnimationFrame(() => {
            if (returnFocusTo?.isConnected) returnFocusTo.focus()
          })
        }}
      >
        <div className="min-h-0 flex-1 overflow-y-auto px-4 py-5 sm:px-6">
          <DialogHeader className="pr-8 text-left">
            <DialogTitle>{title}</DialogTitle>
            <DialogDescription>{description}</DialogDescription>
          </DialogHeader>

          {activeStory ? (
            <div className="mt-5 space-y-4">
              <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-emerald-950" role="status">
                <div className="flex items-center gap-2 font-semibold"><CircleCheck className="h-5 w-5" aria-hidden="true" />Story ativo</div>
                <dl className="mt-3 grid gap-1.5 text-sm">
                  <div><dt className="inline font-medium">Modo: </dt><dd className="inline">{activeStory.modoConteudo === 'ANUNCIO' ? 'Anúncio' : 'Mídia enviada'}</dd></div>
                  <div><dt className="inline font-medium">Início: </dt><dd className="inline">{formatStoryDate(activeStory.inicioEm)}</dd></div>
                  <div><dt className="inline font-medium">Expira em: </dt><dd className="inline">{formatStoryDate(activeStory.fimEm)}</dd></div>
                </dl>
              </div>
              <p className="text-sm text-slate-600">Use a área Meus Stories para excluir ou descartar uma publicação com falha.</p>
              <Button type="button" variant="outline" className="w-full" onClick={() => onOpenChange(false)}>Fechar</Button>
            </div>
          ) : step === 'ESCOLHER_MODO' ? (
            <div className="mt-5 grid gap-3">
              <button type="button" className="flex min-h-24 w-full items-start gap-4 rounded-lg border border-slate-200 p-4 text-left transition hover:border-pink-300 hover:bg-pink-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#FC1EAD]" onClick={() => chooseMode('MIDIA_UPLOAD')}>
                <ImagePlus className="mt-0.5 h-6 w-6 shrink-0 text-[#FC1EAD]" aria-hidden="true" />
                <span><span className="block font-semibold text-slate-950">Enviar uma mídia</span><span className="mt-1 block text-sm leading-5 text-slate-600">Publique uma foto ou vídeo exclusivo, sem precisar de anúncio.</span></span>
              </button>
              <button type="button" disabled={eligibleAnuncios.length === 0} className="flex min-h-24 w-full items-start gap-4 rounded-lg border border-slate-200 p-4 text-left transition hover:border-pink-300 hover:bg-pink-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#FC1EAD] disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-500" onClick={() => chooseMode('ANUNCIO')}>
                <Megaphone className="mt-0.5 h-6 w-6 shrink-0 text-[#FC1EAD]" aria-hidden="true" />
                <span><span className="block font-semibold">Promover anúncio</span><span className="mt-1 block text-sm leading-5">{eligibleAnuncios.length > 0 ? 'Use um anúncio público da sua conta.' : 'Publique e aprove um anúncio para habilitar esta opção.'}</span></span>
              </button>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Cancelar</Button>
            </div>
          ) : step === 'SELECIONAR_ANUNCIO' ? (
            <div className="mt-5 space-y-3">
              <p className="text-sm text-slate-600">Selecione o anúncio que receberá o Story.</p>
              <div className="max-h-[50dvh] space-y-2 overflow-y-auto pr-1">
                {eligibleAnuncios.map((item) => (
                  <button key={item.id} type="button" className="w-full rounded-lg border border-slate-200 p-3 text-left transition hover:border-pink-300 hover:bg-pink-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#FC1EAD]" onClick={() => { setAnuncioId(item.id); setStep('REVISAR') }}>
                    <span className="block break-words font-semibold text-slate-950">{item.titulo}</span>
                    <span className="mt-1 block break-all text-xs text-slate-500">/anuncios/{item.slug}</span>
                  </button>
                ))}
              </div>
              <Button type="button" variant="outline" className="w-full" onClick={() => { setMode(null); setStep('ESCOLHER_MODO') }}>Voltar</Button>
            </div>
          ) : step === 'CONFIGURAR_MIDIA' ? (
            <div className="mt-5 space-y-4">
              <input ref={inputRef} type="file" accept={ACCEPTED_MEDIA} className="sr-only" onChange={(event) => selectFile(event.target.files?.item(0) ?? null)} />
              {!file ? (
                <button type="button" onClick={() => inputRef.current?.click()} className="flex min-h-36 w-full flex-col items-center justify-center rounded-lg border border-dashed border-slate-300 bg-slate-50 px-4 text-center focus-visible:ring-2 focus-visible:ring-[#FC1EAD]">
                  <Upload className="h-7 w-7 text-slate-500" aria-hidden="true" />
                  <span className="mt-2 text-sm font-semibold">Selecionar foto ou vídeo</span>
                  <span className="mt-1 text-xs text-slate-500">JPG, PNG, WebP ou MP4. Envie exatamente um arquivo.</span>
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
                    <Button type="button" size="icon" variant="outline" onClick={() => { selectFile(null); if (inputRef.current) inputRef.current.value = '' }} aria-label="Remover mídia selecionada" title="Remover mídia"><Trash2 className="h-4 w-4 text-rose-600" aria-hidden="true" /></Button>
                  </div>
                  <Button type="button" variant="outline" className="mt-2 w-full" onClick={() => inputRef.current?.click()}>Substituir mídia</Button>
                </div>
              )}
              {error ? <p className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800" role="alert">{error}</p> : null}
              <div className="grid gap-2 sm:grid-cols-2">
                <Button type="button" variant="outline" onClick={() => { setMode(null); setFile(null); setStep('ESCOLHER_MODO') }}>Voltar</Button>
                <Button type="button" disabled={!file || Boolean(error)} onClick={() => { setError(null); setStep('REVISAR') }}>Revisar Story</Button>
              </div>
            </div>
          ) : step === 'REVISAR' ? (
            <div className="mt-5 space-y-4">
              <section className="rounded-lg border border-slate-200 bg-slate-50 p-4" aria-label="Revisão do Story">
                <div className="flex items-start gap-3">
                  {mode === 'ANUNCIO' ? <Megaphone className="mt-0.5 h-5 w-5 shrink-0 text-[#FC1EAD]" aria-hidden="true" /> : <ImagePlus className="mt-0.5 h-5 w-5 shrink-0 text-[#FC1EAD]" aria-hidden="true" />}
                  <div className="min-w-0">
                    <p className="break-words font-semibold text-slate-950">{mode === 'ANUNCIO' ? selectedAnuncio?.titulo : 'Mídia enviada'}</p>
                    <p className="mt-1 text-sm text-slate-600">{mode === 'ANUNCIO' ? 'CTA público: Ver anúncio.' : 'Sem vínculo com anúncio. CTA público: Ver anunciante.'}</p>
                  </div>
                </div>
                {mode === 'MIDIA_UPLOAD' && file ? (
                  <div className="mt-3 flex max-h-64 min-h-36 items-center justify-center overflow-hidden rounded-lg bg-slate-950">
                    {file.type.startsWith('video/')
                      ? <video src={preview ?? undefined} controls className="max-h-64 max-w-full object-contain" />
                      // eslint-disable-next-line @next/next/no-img-element
                      : <img src={preview ?? undefined} alt="Revisão da mídia do Story" className="max-h-64 max-w-full object-contain" />}
                  </div>
                ) : null}
              </section>
              <p className="text-sm text-slate-600">Nenhum crédito será debitado antes da confirmação final.</p>
              <div className="grid gap-2 sm:grid-cols-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    if (initialTargetId) onOpenChange(false)
                    else setStep(mode === 'ANUNCIO' && eligibleAnuncios.length > 1 ? 'SELECIONAR_ANUNCIO' : mode === 'ANUNCIO' ? 'ESCOLHER_MODO' : 'CONFIGURAR_MIDIA')
                  }}
                >
                  {initialTargetId ? 'Cancelar' : 'Voltar'}
                </Button>
                <Button type="button" onClick={() => { setStep('OFERTA'); void loadOffer() }}>Verificar condição para publicar</Button>
              </div>
            </div>
          ) : (
            <div className="mt-5 space-y-4">
              {loadingOffer ? <p className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm" role="status">Verificando direito, oferta e saldo...</p> : null}
              {!loadingOffer && (activationCompleted || offer?.estado === 'DIREITO_DISPONIVEL') ? (
                <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-950" role="status"><p className="font-semibold">Você já possui um direito de Story disponível.</p><p className="mt-1">Nenhuma nova cobrança será feita.</p></div>
              ) : null}
              {!loadingOffer && !activationCompleted && offer?.estado === 'OFERTA_DISPONIVEL' ? (
                <>
                  <div className="rounded-lg border border-pink-200 bg-pink-50 p-4"><div className="flex items-center gap-2 font-semibold"><CircleDollarSign className="h-5 w-5 text-[#FC1EAD]" aria-hidden="true" />Story por 24 horas</div><p className="mt-1 text-sm text-slate-600">A vigência começa após a publicação bem-sucedida.</p></div>
                  <dl className="grid gap-2 rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm">
                    <div><dt className="inline font-medium">Custo: </dt><dd className="inline">{pluralizeCredits(offer.custoCreditos ?? 0)}</dd></div>
                    <div><dt className="inline font-medium">Seu saldo: </dt><dd className="inline">{pluralizeCredits(offer.saldoAtual ?? 0)}</dd></div>
                    {(offer.deficit ?? 0) > 0 ? <div className="text-amber-900"><dt className="inline font-medium">Faltam: </dt><dd className="inline">{pluralizeCredits(offer.deficit ?? 0)}</dd></div> : null}
                  </dl>
                  {(offer.deficit ?? 0) > 0 ? <div className="space-y-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950"><p>Seu saldo não é suficiente para esta ativação.</p><div className="grid gap-2 sm:grid-cols-2"><Button asChild><Link href="/creditos" target="_blank" onClick={() => setAwaitingCredits(true)}>Comprar créditos</Link></Button><Button type="button" variant="outline" onClick={() => void loadOffer()} disabled={loadingOffer}><RefreshCw className="mr-2 h-4 w-4" aria-hidden="true" />Atualizar saldo</Button></div></div> : null}
                </>
              ) : null}
              {!loadingOffer && !activationCompleted && offer?.estado === 'NOVAS_ATIVACOES_INDISPONIVEIS' ? <p className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950" role="status">Stories está temporariamente indisponível para novas ativações.</p> : null}
              {publishing && mode === 'MIDIA_UPLOAD' ? <div className="space-y-2" role="status" aria-live="polite"><div className="flex justify-between text-sm"><span>{progress < 99 ? 'Enviando mídia...' : 'Processando mídia...'}</span><span>{progress}%</span></div><div className="h-2 overflow-hidden rounded-full bg-slate-200" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress} aria-label="Progresso da publicação do Story"><div className="h-full bg-[#FC1EAD] transition-[width]" style={{ width: `${progress}%` }} /></div></div> : null}
              {error ? <p className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800" role="alert">{error}</p> : null}
              <div className="grid gap-2 sm:grid-cols-2">
                <Button type="button" variant="outline" onClick={() => { setError(null); setStep('REVISAR') }} disabled={busy}>Voltar</Button>
                {activationCompleted || offer?.estado === 'DIREITO_DISPONIVEL' ? <Button type="button" onClick={() => void publishStory(activationCompleted)} disabled={publishing}>{publishing ? 'Publicando...' : 'Publicar Story'}</Button> : offer?.estado === 'OFERTA_DISPONIVEL' && offer.deficit === 0 ? <Button type="button" onClick={() => void activateAndPublish()} disabled={busy}>{activating ? 'Ativando...' : publishing ? 'Publicando...' : offer.custoCreditos === 0 ? 'Ativar gratuitamente e publicar' : `Ativar por ${pluralizeCredits(offer.custoCreditos ?? 0)} e publicar`}</Button> : !offer && !loadingOffer ? <Button type="button" onClick={() => void loadOffer()}>Tentar novamente</Button> : null}
              </div>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
