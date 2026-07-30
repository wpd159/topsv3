'use client'

import { ArrowDown, ArrowUp, Camera, Loader2, PlayCircle, Trash2 } from 'lucide-react'
import { GaleriaFotos } from '@/components/anuncios/galeria-fotos'
import { VideoUploader } from '@/components/anuncios/editar/video-uploader'
import { FilePicker } from '@/components/forms/file-picker'
import { useCallback, useEffect, useState } from 'react'
import {
  enviarMinhasMidiasEmLote,
  listarMinhasMidias,
  removerMinhaMidia,
  reordenarMinhasMidias,
  type MinhaMidiaGestao,
  type MinhasMidiasResponse,
} from '@/lib/meus-anuncios-api'
import { StepPanel } from './wizard-ui'

type WizardStepFotosProps = {
  slug?: string
  initialFiles: File[]
  fotoNomes: string[]
  onChange: (payload: File[]) => void
  videosNovos: File[]
  onChangeVideosNovos: (payload: File[]) => void
  createProgress?: Record<string, number>
  createErrors?: Record<string, string>
  onAddBenefit?: () => void
}

function fileKey(file: File) {
  return `${file.name}:${file.size}:${file.lastModified}`
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
  onAddBenefit,
}: WizardStepFotosProps) {
  const [persisted, setPersisted] = useState<MinhasMidiasResponse | null>(null)
  const [loading, setLoading] = useState(Boolean(slug))
  const [busy, setBusy] = useState(false)
  const [progress, setProgress] = useState<Record<string, number>>({})
  const [errors, setErrors] = useState<Record<string, string>>({})

  const refresh = useCallback(async () => {
    if (!slug) return
    setLoading(true)
    try {
      setPersisted(await listarMinhasMidias(slug))
    } finally {
      setLoading(false)
    }
  }, [slug])

  useEffect(() => {
    void refresh().catch((error) => {
      setErrors({ carregar: error instanceof Error ? error.message : 'Não foi possível carregar as mídias.' })
    })
  }, [refresh])

  const uploadPersisted = async (files: File[]) => {
    if (!slug || !files.length || busy) return
    const fotosNovas = files.filter((file) => file.type.startsWith('image/')).length
    const videosNovos = files.filter((file) => file.type.startsWith('video/')).length
    if (persisted && fotosNovas > persisted.limites.fotosDisponiveis) {
      setErrors({ lote: 'Você atingiu o limite de fotos deste anúncio.' })
      return
    }
    if (persisted && videosNovos > persisted.limites.videosDisponiveis) {
      setErrors({
        lote: persisted.limites.videoAtivo
          ? 'Este anúncio já atingiu o limite de vídeos.'
          : 'Adicione um vídeo ao seu anúncio com o benefício Vídeo.',
      })
      return
    }
    setBusy(true)
    setErrors({})
    try {
      const latest = await enviarMinhasMidiasEmLote(slug, files, (value) => {
        setProgress((current) => Object.fromEntries([
          ...Object.entries(current),
          ...files.map((file) => [file.name, value] as const),
        ]))
      })
      setPersisted(latest)
    } catch (error) {
      setErrors({
        lote: error instanceof Error ? error.message : 'Falha ao enviar os arquivos.',
      })
    } finally {
      setBusy(false)
    }
  }

  const move = async (index: number, direction: -1 | 1) => {
    if (!slug || !persisted || busy) return
    const nextIndex = index + direction
    if (nextIndex < 0 || nextIndex >= persisted.midias.length) return
    const next = [...persisted.midias]
    ;[next[index], next[nextIndex]] = [next[nextIndex], next[index]]
    setBusy(true)
    try {
      setPersisted(await reordenarMinhasMidias(slug, next.map((item) => item.id)))
    } catch (error) {
      setErrors({ ordenar: error instanceof Error ? error.message : 'Não foi possível alterar a ordem.' })
    } finally {
      setBusy(false)
    }
  }

  const remove = async (midia: MinhaMidiaGestao) => {
    if (!slug || busy) return
    setBusy(true)
    try {
      setPersisted(await removerMinhaMidia(slug, midia.id))
    } catch (error) {
      setErrors({ remover: error instanceof Error ? error.message : 'Não foi possível remover a mídia.' })
    } finally {
      setBusy(false)
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
          <GaleriaFotos
            initialFiles={initialFiles}
            onChange={onChange}
            maxCount={4}
            variant="wizard"
            showLimit
            enforceLimit
          />
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

  return (
    <StepPanel>
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

        <div className="grid gap-4 sm:grid-cols-2">
          <FilePicker
            ariaLabel="Selecionar fotos do anúncio"
            buttonLabel="Selecionar foto"
            accept="image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp"
            files={[]}
            multiple
            disabled={busy || !persisted || persisted.limites.fotosDisponiveis === 0}
            helperText={persisted?.limites.fotosExtrasAtivo
              ? 'Seu anúncio permite até 10 fotos com o benefício de fotos extras.'
              : 'Você pode adicionar até 4 fotos gratuitamente.'}
            onSelect={(files) => void uploadPersisted(files)}
            onRemove={() => undefined}
          />
          {persisted?.limites.videoAtivo ? (
            <FilePicker
              ariaLabel="Selecionar vídeo do anúncio"
              buttonLabel="Selecionar vídeo"
              accept="video/mp4,video/quicktime,.mp4,.mov"
              files={[]}
              disabled={busy || persisted.limites.videosDisponiveis === 0}
              helperText="Você pode adicionar 1 vídeo em MP4 ou MOV."
              onSelect={(files) => void uploadPersisted(files.slice(0, 1))}
              onRemove={() => undefined}
            />
          ) : (
            <div className="rounded-xl border border-zinc-200 bg-zinc-50 p-4">
              <p className="font-semibold text-zinc-900">Vídeo do anúncio</p>
              <p className="mt-1 text-sm text-zinc-600">
                Adicione um vídeo ao seu anúncio com o benefício Vídeo.
              </p>
              <button type="button" onClick={onAddBenefit} className="mt-3 rounded-xl border border-zinc-300 bg-white px-4 py-2 text-sm font-semibold">
                Adicionar benefício
              </button>
            </div>
          )}
        </div>

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
                    <button type="button" title="Mover para cima" disabled={busy || index === 0} onClick={() => void move(index, -1)} className="flex h-9 w-9 items-center justify-center rounded-full border border-zinc-200 bg-white disabled:opacity-40"><ArrowUp className="h-4 w-4" /></button>
                    <button type="button" title="Mover para baixo" disabled={busy || index === persisted.midias.length - 1} onClick={() => void move(index, 1)} className="flex h-9 w-9 items-center justify-center rounded-full border border-zinc-200 bg-white disabled:opacity-40"><ArrowDown className="h-4 w-4" /></button>
                    <button type="button" title="Remover mídia" disabled={busy} onClick={() => void remove(midia)} className="ml-auto flex h-9 w-9 items-center justify-center rounded-full border border-red-200 bg-white text-red-600 disabled:opacity-40"><Trash2 className="h-4 w-4" /></button>
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
