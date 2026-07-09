'use client'

import { ImagePlus, UploadCloud } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { cn } from '@/lib/utils'
import {
  ALLOWED_IMAGE_ACCEPT,
  INCOMPATIBLE_IMAGE_FORMAT_MESSAGE,
  UNSUPPORTED_IMAGE_MESSAGE,
  isHeic,
  isImagemValida,
} from '@/utils/image-upload'

type Props = {
  initialUrls?: string[]
  initialFiles?: File[]
  onChange?: (files: File[]) => void
  onChangeExistentes?: (urls: string[]) => void
  onMediaTouched?: () => void
  maxCount?: number
  pro?: boolean
  accept?: string
  variant?: 'default' | 'wizard'
}

export function GaleriaFotos({
  initialUrls,
  initialFiles,
  onChange,
  onChangeExistentes,
  onMediaTouched,
  maxCount = 15,
  pro = false,
  accept = ALLOWED_IMAGE_ACCEPT,
  variant = 'default',
}: Props) {
  const [existentes, setExistentes] = useState<string[]>([])
  const [novas, setNovas] = useState<File[]>(() => initialFiles ?? [])
  const [erroArquivo, setErroArquivo] = useState<string | null>(null)
  const previewsNovas = useMemo(() => novas.map((f) => URL.createObjectURL(f)), [novas])
  const scrollRef = useRef<HTMLDivElement>(null)
  const isWizard = variant === 'wizard'

  useEffect(() => {
    if (!Array.isArray(initialUrls)) return
    const next = initialUrls.filter(Boolean)
    setExistentes((prev) => {
      if (prev.length === next.length && prev.every((v, i) => v === next[i])) return prev
      return next
    })
  }, [initialUrls])

  useEffect(() => {
    if (!Array.isArray(initialFiles)) return
    setNovas((prev) => {
      if (
        prev.length === initialFiles.length &&
        prev.every((file, index) => file === initialFiles[index])
      ) {
        return prev
      }
      return initialFiles
    })
  }, [initialFiles])

  useEffect(() => {
    onChange?.(novas)
    return () => previewsNovas.forEach((u) => URL.revokeObjectURL(u))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [novas])

  const total = existentes.length + novas.length
  const limiteAtingido = total >= maxCount

  const pick = (files: FileList | null) => {
    if (!files?.length) return

    const disponivel = Math.max(0, maxCount - total)
    if (disponivel <= 0) return

    const selecionadas = Array.from(files)
    if (selecionadas.some(isHeic)) {
      setErroArquivo(INCOMPATIBLE_IMAGE_FORMAT_MESSAGE)
      return
    }

    if (selecionadas.some((file) => !isImagemValida(file))) {
      setErroArquivo(UNSUPPORTED_IMAGE_MESSAGE)
      return
    }

    const lote = selecionadas.slice(0, disponivel)
    const merged = [...novas, ...lote]
    onMediaTouched?.()
    setErroArquivo(null)
    setNovas(merged)

    setTimeout(() => {
      scrollRef.current?.scrollTo({ left: scrollRef.current.scrollWidth, behavior: 'smooth' })
    }, 80)
  }

  const removeExistente = (url: string) => {
    const updated = existentes.filter((u) => u !== url)
    onMediaTouched?.()
    setErroArquivo(null)
    setExistentes(updated)
    onChangeExistentes?.(updated)
  }

  const removeNova = (idx: number) => {
    const url = previewsNovas[idx]
    URL.revokeObjectURL(url)
    onMediaTouched?.()
    setErroArquivo(null)
    setNovas((prev) => prev.filter((_, i) => i !== idx))
  }

  return (
    <div className="flex flex-col gap-3">
      <label className="font-semibold text-gray-800">
        Galeria de fotos {pro ? '(PRO)' : ''}
      </label>

      {!pro && !isWizard && (
        <p className="-mt-1 text-xs text-gray-600">
          No seu plano atual é permitido <strong>até {maxCount} fotos</strong>.
        </p>
      )}

      <label
        className={cn(
          'cursor-pointer rounded-lg border border-dashed transition',
          isWizard
            ? 'flex min-h-[228px] flex-col items-center justify-center border-zinc-200 bg-white px-5 py-6 text-center shadow-sm hover:border-zinc-300 hover:bg-zinc-50/80'
            : 'flex flex-col items-center justify-center space-y-3 border-gray-300 p-6 text-center hover:bg-gray-50'
        )}
      >
        {isWizard ? (
          <>
            <div className="flex h-14 w-14 items-center justify-center rounded-2xl border border-zinc-200 bg-zinc-50 text-zinc-500">
              <UploadCloud className="h-6 w-6" />
            </div>

            <div className="mt-4 space-y-2">
              <p className="text-sm font-semibold text-zinc-900">Adicione fotos ao seu anúncio</p>
              <p className="text-sm leading-6 text-zinc-500">
                Escolha imagens que representem bem seu perfil.
              </p>
            </div>

            <span className="mt-5 inline-flex min-h-11 items-center justify-center rounded-full bg-zinc-950 px-5 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-zinc-800">
              Selecionar fotos
            </span>

            <p className="mt-3 text-xs text-zinc-500">ou arraste arquivos aqui</p>
            <p className="mt-2 text-xs font-medium text-zinc-400">
              {total}/{maxCount} {total === 1 ? 'foto' : 'fotos'}
            </p>
          </>
        ) : (
          <>
            <svg width="36" height="36" viewBox="0 0 24 24" className="text-gray-400" fill="none">
              <path
                d="M21 15l-5-5-4 4-2-2-5 5"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
              <circle cx="8.5" cy="8.5" r="1.5" stroke="currentColor" strokeWidth="2" />
            </svg>

            <p className="text-sm text-gray-500">Arraste e solte ou clique para selecionar</p>
            <p className="text-xs text-gray-400">
              {total}/{maxCount} {total === 1 ? 'foto' : 'fotos'}
            </p>
          </>
        )}

        <input
          type="file"
          accept={accept}
          multiple
          className="hidden"
          onChange={(e) => {
            pick(e.target.files)
            e.currentTarget.value = ''
          }}
          disabled={limiteAtingido}
        />
      </label>

      {erroArquivo ? (
        <p className="text-xs font-medium text-red-600">{erroArquivo}</p>
      ) : null}

      {isWizard ? (
        <div className="rounded-[22px] border border-zinc-200 bg-zinc-50/80 p-3 shadow-sm">
          <div className="mb-3 flex min-h-5 items-center justify-between gap-3 px-1 text-xs text-zinc-500">
            <span>
              {total} {total === 1 ? 'foto adicionada' : 'fotos adicionadas'}
            </span>
            <span className="shrink-0">arraste para o lado →</span>
          </div>

          <div ref={scrollRef} className="flex min-h-[148px] gap-3 overflow-x-auto pb-1">
            {total === 0 ? (
              <div className="flex min-h-[148px] w-full min-w-0 items-center justify-center rounded-[20px] border border-dashed border-zinc-200 bg-white text-center">
                <div className="space-y-2 px-6">
                  <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-full bg-zinc-100 text-zinc-400">
                    <ImagePlus className="h-5 w-5" />
                  </div>
                  <p className="text-sm font-medium text-zinc-700">
                    Suas fotos aparecerão aqui conforme forem adicionadas
                  </p>
                  <p className="text-xs leading-5 text-zinc-500">
                    A primeira imagem costuma ser a que mais chama atenção no anúncio.
                  </p>
                </div>
              </div>
            ) : (
              <>
                {existentes.map((url) => (
                  <div
                    key={url}
                    className="relative h-[148px] w-[132px] shrink-0 overflow-hidden rounded-[20px] border border-zinc-200 bg-white shadow-sm"
                  >
                    <img
                      src={url}
                      alt="foto existente"
                      className="h-full w-full object-cover"
                      loading="lazy"
                    />
                    <button
                      type="button"
                      onClick={() => removeExistente(url)}
                      className="absolute right-2 top-2 flex h-8 w-8 items-center justify-center rounded-full bg-white/90 text-sm font-medium text-zinc-700 shadow-sm transition hover:bg-white"
                      title="Remover foto"
                    >
                      ×
                    </button>
                  </div>
                ))}

                {previewsNovas.map((u, i) => (
                  <div
                    key={u}
                    className="relative h-[148px] w-[132px] shrink-0 overflow-hidden rounded-[20px] border border-zinc-200 bg-white shadow-sm"
                  >
                    <img src={u} alt={`nova-${i}`} className="h-full w-full object-cover" />
                    <span className="absolute bottom-2 left-2 rounded-full bg-zinc-950/80 px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.12em] text-white">
                      Nova
                    </span>
                    <button
                      type="button"
                      onClick={() => removeNova(i)}
                      className="absolute right-2 top-2 flex h-8 w-8 items-center justify-center rounded-full bg-white/90 text-sm font-medium text-zinc-700 shadow-sm transition hover:bg-white"
                      title="Remover foto"
                    >
                      ×
                    </button>
                  </div>
                ))}
              </>
            )}
          </div>
        </div>
      ) : (
        (existentes.length > 0 || novas.length > 0) && (
          <div className="mt-2 w-full">
            <div className="mb-2 flex items-center justify-between px-1 text-xs text-gray-600">
              <span>
                {total} {total === 1 ? 'foto' : 'fotos'}
              </span>
              <span className="text-gray-400">(arraste para o lado →)</span>
            </div>

            <div ref={scrollRef} className="flex max-w-full gap-3 overflow-x-auto pb-3">
              {existentes.map((url) => (
                <div
                  key={url}
                  className="relative h-32 w-32 shrink-0 overflow-hidden rounded-lg border"
                >
                  <img
                    src={url}
                    alt="foto existente"
                    className="h-full w-full object-cover"
                    loading="lazy"
                  />
                  <button
                    type="button"
                    onClick={() => removeExistente(url)}
                    className="absolute right-1 top-1 rounded-full bg-white/85 px-2 py-1 text-xs text-gray-700 shadow hover:bg-white"
                    title="Remover foto"
                  >
                    ×
                  </button>
                </div>
              ))}

              {previewsNovas.map((u, i) => (
                <div
                  key={u}
                  className="relative h-32 w-32 shrink-0 overflow-hidden rounded-lg border"
                >
                  <img src={u} alt={`nova-${i}`} className="h-full w-full object-cover" />
                  <span className="absolute bottom-1 left-1 rounded bg-black/55 px-1.5 py-0.5 text-[10px] text-white">
                    nova
                  </span>
                  <button
                    type="button"
                    onClick={() => removeNova(i)}
                    className="absolute right-1 top-1 rounded-full bg-white/85 px-2 py-1 text-xs text-gray-700 shadow hover:bg-white"
                    title="Remover foto"
                  >
                    ×
                  </button>
                </div>
              ))}
            </div>
          </div>
        )
      )}
    </div>
  )
}
