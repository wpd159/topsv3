'use client'

import * as React from 'react'
import { GaleriaFotos } from '@/components/anuncios/galeria-fotos'
import { VideoUploader } from './video-uploader'

export function AnuncioEditMedia(p: {
  maxFotos: number
  maxMB: number
  fotosExistentes: string[]
  setFotosExistentes: (v: string[]) => void
  onFotosChange: (files: File[]) => Promise<void>
  fotosMsg: string
  fotosErro: string

  videosExistentes: string[]
  setVideosExistentes: (v: string[]) => void
  videosNovos: File[]
  setVideosNovos: (v: File[]) => void
  canUploadVideos: boolean
  onMediaTouched?: () => void
}) {
  return (
    <>
      <GaleriaFotos
        initialUrls={p.fotosExistentes}
        onChange={p.onFotosChange}
        onChangeExistentes={p.setFotosExistentes}
        onMediaTouched={p.onMediaTouched}
        maxCount={p.maxFotos}
        pro
      />

      <p className="rounded-md border border-amber-200/80 bg-amber-50/90 px-3 py-2 text-xs leading-relaxed text-amber-950">
        Fotos com nudez ou conteúdo adulto poderão ser borradas, classificadas ou restringidas pela moderação, em
        conformidade com a Lei nº 15.211/2025 (ECA Digital).
      </p>

      {p.fotosErro ? (
        <p className="text-xs text-red-500 -mt-2">{p.fotosErro}</p>
      ) : (
        <p className="text-xs text-gray-500 -mt-2">
          📎 {p.fotosMsg || `Máximo de ${p.maxFotos} fotos. Até ${p.maxMB}MB por imagem.`}
        </p>
      )}

      <VideoUploader
        existing={p.videosExistentes}
        onChangeExisting={p.setVideosExistentes}
        newVideos={p.videosNovos}
        onChangeNew={p.setVideosNovos}
        onMediaTouched={p.onMediaTouched}
        canUpload={p.canUploadVideos}
      />
    </>
  )
}
