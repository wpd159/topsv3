'use client'

import * as React from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

export function VideoUploader({
  existing = [],
  onChangeExisting,
  newVideos = [],
  onChangeNew,
  canUpload = true,
  onMediaTouched,
}: {
  existing?: string[]
  onChangeExisting?: (v: string[]) => void
  newVideos?: File[]
  onChangeNew?: (v: File[]) => void
  canUpload?: boolean
  onMediaTouched?: () => void
}) {
  const previewUrls = React.useMemo(
    () => newVideos.map((file) => URL.createObjectURL(file)),
    [newVideos]
  )

  React.useEffect(() => {
    return () => previewUrls.forEach((url) => URL.revokeObjectURL(url))
  }, [previewUrls])

  const upload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files
    if (!files?.length) return
    const updated = [...newVideos, ...Array.from(files)]
    onMediaTouched?.()
    onChangeNew?.(updated)
    e.currentTarget.value = ''
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
        <div className="flex flex-col gap-1">
          <Input type="file" accept="video/*" multiple onChange={upload} />
          <p className="text-xs text-gray-500">Formatos: mp4, mov, webm.</p>
        </div>
      ) : (
        <p className="text-xs text-gray-500">
          Para enviar vídeos, ative a funcionalidade de vídeo.
        </p>
      )}
    </div>
  )
}
