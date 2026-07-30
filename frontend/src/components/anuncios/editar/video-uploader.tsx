'use client'

import * as React from 'react'
import { FilePicker } from '@/components/forms/file-picker'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'

export function VideoUploader({
  existing = [],
  onChangeExisting,
  newVideos = [],
  onChangeNew,
  canUpload = true,
  onMediaTouched,
  onAddBenefit,
}: {
  existing?: string[]
  onChangeExisting?: (v: string[]) => void
  newVideos?: File[]
  onChangeNew?: (v: File[]) => void
  canUpload?: boolean
  onMediaTouched?: () => void
  onAddBenefit?: () => void
}) {
  const previewUrls = React.useMemo(
    () => newVideos.map((file) => URL.createObjectURL(file)),
    [newVideos]
  )

  React.useEffect(() => {
    return () => previewUrls.forEach((url) => URL.revokeObjectURL(url))
  }, [previewUrls])

  const upload = (files: File[]) => {
    if (!files.length) return
    const updated = [...newVideos, ...files].slice(0, 1)
    onMediaTouched?.()
    onChangeNew?.(updated)
  }

  const removeExisting = (url: string) => {
    const filtered = existing.filter((item) => item !== url)
    onMediaTouched?.()
    onChangeExisting?.(filtered)
  }

  const removeNew = (idx: number) => {
    const filtered = newVideos.filter((_, index) => index !== idx)
    onMediaTouched?.()
    onChangeNew?.(filtered)
  }

  return (
    <div className="flex flex-col gap-3">
      <Label>Vídeos do anúncio</Label>

      {existing.length > 0 ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          {existing.map((url) => (
            <div key={url} className="relative overflow-hidden rounded-lg border bg-black/5">
              <video src={url} controls className="h-40 w-full bg-black object-cover" />
              <button
                type="button"
                onClick={() => removeExisting(url)}
                className="absolute right-2 top-2 rounded bg-white/90 px-2 py-1 text-xs text-red-500 shadow"
              >
                remover
              </button>
            </div>
          ))}
        </div>
      ) : (
        <p className="text-xs text-gray-400">Nenhum vídeo salvo ainda.</p>
      )}

      {newVideos.length > 0 ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          {newVideos.map((_, idx) => (
            <div key={`${previewUrls[idx]}-${idx}`} className="relative overflow-hidden rounded-lg border bg-black/5">
              <video src={previewUrls[idx]} controls className="h-40 w-full bg-black object-cover" />
              <button
                type="button"
                onClick={() => removeNew(idx)}
                className="absolute right-2 top-2 rounded bg-white/90 px-2 py-1 text-xs text-red-500 shadow"
              >
                remover
              </button>
            </div>
          ))}
        </div>
      ) : null}

      {canUpload ? (
        <FilePicker
          ariaLabel="Selecionar vídeo do anúncio"
          buttonLabel="Selecionar vídeo"
          accept="video/mp4,video/quicktime,.mp4,.mov"
          files={newVideos}
          disabled={existing.length + newVideos.length >= 1}
          helperText="Você pode adicionar 1 vídeo em MP4 ou MOV."
          onSelect={upload}
          onRemove={removeNew}
        />
      ) : (
        <div className="rounded-xl border border-zinc-200 bg-zinc-50 p-4">
          <p className="font-semibold text-zinc-900">Vídeo do anúncio</p>
          <p className="mt-1 text-sm text-zinc-600">
            Adicione um vídeo ao seu anúncio com o benefício Vídeo.
          </p>
          <Button type="button" variant="outline" className="mt-3" onClick={onAddBenefit}>
            Adicionar benefício
          </Button>
        </div>
      )}
    </div>
  )
}
