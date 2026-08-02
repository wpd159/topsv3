'use client'

import Link from 'next/link'
import { useEffect, useId, useRef, useState } from 'react'
import {
  CircleCheck,
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
import {
  consultarLimitesMinhasMidias,
  MeusAnunciosApiError,
  publicarMeuAnuncioStory,
  type MeuAnuncioStory,
  type MinhasMidiasLimites,
} from '@/lib/meus-anuncios-api'
import { cn } from '@/lib/utils'

type StoryMode = 'ANUNCIO' | 'MIDIA_UPLOAD'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  slug: string
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

function formatDate(value: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'America/Sao_Paulo',
  }).format(new Date(value))
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

export function StoryCreateDialog({ open, onOpenChange, slug, onSuccess }: Props) {
  const titleId = useId()
  const inputRef = useRef<HTMLInputElement | null>(null)
  const idempotencyKeyRef = useRef<string | null>(null)
  const [mode, setMode] = useState<StoryMode | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [limits, setLimits] = useState<MinhasMidiasLimites | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [progress, setProgress] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<MeuAnuncioStory | null>(null)

  useEffect(() => {
    if (!open) {
      setMode(null)
      setFile(null)
      setLimits(null)
      setSubmitting(false)
      setProgress(0)
      setError(null)
      setResult(null)
      idempotencyKeyRef.current = null
      return
    }
    let cancelled = false
    void consultarLimitesMinhasMidias(slug)
      .then((value) => {
        if (!cancelled) setLimits(value)
      })
      .catch(() => {
        if (!cancelled) setLimits(null)
      })
    return () => {
      cancelled = true
    }
  }, [open, slug])

  useEffect(() => {
    if (!file) {
      setPreview(null)
      return
    }
    const url = URL.createObjectURL(file)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  function resetIntent() {
    idempotencyKeyRef.current = null
    setError(null)
    setProgress(0)
  }

  function selectMode(next: StoryMode) {
    if (next === mode) return
    setMode(next)
    if (next === 'ANUNCIO') setFile(null)
    resetIntent()
  }

  function selectFile(next: File | null) {
    setFile(next)
    resetIntent()
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
    const key = idempotencyKeyRef.current ?? crypto.randomUUID()
    idempotencyKeyRef.current = key
    setSubmitting(true)
    setProgress(0)
    setError(null)
    try {
      const story = await publicarMeuAnuncioStory(
        slug,
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

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!submitting) onOpenChange(next)
      }}
    >
      <DialogContent className="flex max-h-[100dvh] w-[calc(100vw-1rem)] max-w-lg flex-col overflow-hidden p-0 sm:max-h-[calc(100dvh-2rem)]">
        <div className="overflow-y-auto px-5 py-5 sm:px-6" aria-labelledby={titleId}>
          <DialogHeader className="pr-7 text-left">
            <DialogTitle id={titleId}>Como você quer aparecer nos Stories?</DialogTitle>
            <DialogDescription>
              Escolha o anúncio ou envie uma mídia exclusiva para esta publicação.
            </DialogDescription>
          </DialogHeader>

          {result ? (
            <div className="mt-5 space-y-4" role="status">
              <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-emerald-950">
                <div className="flex items-center gap-2 font-semibold">
                  <CircleCheck className="h-5 w-5" aria-hidden="true" />
                  Seu Story foi publicado.
                </div>
                <dl className="mt-3 grid gap-2 text-sm">
                  <div><dt className="inline font-medium">Modo: </dt><dd className="inline">{result.modoConteudo === 'ANUNCIO' ? 'Divulgar meu anúncio' : 'Mídia enviada'}</dd></div>
                  <div><dt className="inline font-medium">Status: </dt><dd className="inline">Ativo</dd></div>
                  <div><dt className="inline font-medium">Início: </dt><dd className="inline">{formatDate(result.inicioEm)}</dd></div>
                  <div><dt className="inline font-medium">Expira em: </dt><dd className="inline">{formatDate(result.fimEm)}</dd></div>
                </dl>
              </div>
              <div className="grid gap-2 sm:grid-cols-2">
                <Button asChild variant="outline">
                  <Link href={`/anuncios/${encodeURIComponent(slug)}`}>Ver anúncio</Link>
                </Button>
                <Button type="button" onClick={() => onOpenChange(false)}>Concluir</Button>
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
