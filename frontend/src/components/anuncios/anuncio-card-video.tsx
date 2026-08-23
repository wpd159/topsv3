"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import { PlayCircleIcon } from "@heroicons/react/24/solid"
import { SensitiveVideo } from "@/components/compliance/sensitive-video"
import {
  midiaExigeConfirmacaoIdade,
  type MidiaPublica,
} from "@/lib/media/public-media"

let activeCardVideo: HTMLVideoElement | null = null

type AnuncioCardVideoProps = {
  midia: MidiaPublica
  anuncioId: string | number
  anuncioSlug: string
  onVerificationSuccess?: () => void
}

export function AnuncioCardVideo({
  midia,
  anuncioId,
  anuncioSlug,
  onVerificationSuccess,
}: AnuncioCardVideoProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const [playing, setPlaying] = useState(false)
  const blocked = midiaExigeConfirmacaoIdade(midia)

  const setVideoElement = useCallback((video: HTMLVideoElement | null) => {
    const previous = videoRef.current
    if (!video && previous) {
      previous.pause()
      if (activeCardVideo === previous) activeCardVideo = null
    }
    videoRef.current = video
  }, [])

  useEffect(() => {
    return () => {
      const video = videoRef.current
      if (!video) return
      video.pause()
      if (activeCardVideo === video) activeCardVideo = null
    }
  }, [])

  return (
    <div
      className="absolute inset-0 z-10 bg-zinc-950"
      onClick={(event) => event.stopPropagation()}
      data-anuncio-card-video
    >
      <SensitiveVideo
        midia={midia}
        anuncioId={anuncioId}
        anuncioSlug={anuncioSlug}
        preload="none"
        className="object-cover transition-transform duration-500 group-hover:scale-[1.02]"
        onVideoElementChange={setVideoElement}
        onVerificationSuccess={onVerificationSuccess}
        onPlay={(video) => {
          if (activeCardVideo && activeCardVideo !== video) {
            activeCardVideo.pause()
          }
          activeCardVideo = video
          setPlaying(true)
        }}
        onPause={(video) => {
          if (activeCardVideo === video) activeCardVideo = null
          setPlaying(false)
        }}
        onEnded={(video) => {
          if (activeCardVideo === video) activeCardVideo = null
          setPlaying(false)
        }}
      />

      {!blocked && !playing ? (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
          <span className="flex flex-col items-center gap-2 rounded-md bg-black/65 px-3 py-2 text-xs font-semibold text-white ring-1 ring-white/25">
            <PlayCircleIcon className="h-9 w-9" aria-hidden="true" />
            Reproduzir vídeo
          </span>
        </div>
      ) : null}
    </div>
  )
}
