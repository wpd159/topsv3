"use client"

import Image from "next/image"
import { useCallback, useEffect, useRef, useState } from "react"
import { PlayCircleIcon, VideoCameraIcon } from "@heroicons/react/24/solid"
import { SensitiveVideo } from "@/components/compliance/sensitive-video"
import {
  midiaExigeConfirmacaoIdade,
  type CapaVideoCard,
  type MidiaPublica,
} from "@/lib/media/public-media"

let activeCardVideo: HTMLVideoElement | null = null

type AnuncioCardVideoProps = {
  midia: MidiaPublica
  anuncioId: string | number
  anuncioSlug: string
  capa?: CapaVideoCard | null
  priority?: boolean
  onVerificationSuccess?: () => void
}

export function AnuncioCardVideo({
  midia,
  anuncioId,
  anuncioSlug,
  capa,
  priority = false,
  onVerificationSuccess,
}: AnuncioCardVideoProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const [playing, setPlaying] = useState(false)
  const [sessionAuthorized, setSessionAuthorized] = useState(midia.autorizada)
  const [mediaFailed, setMediaFailed] = useState(false)
  const [posterFailed, setPosterFailed] = useState(false)
  const blocked = midiaExigeConfirmacaoIdade(midia) && !sessionAuthorized
  const showPoster = !playing && !mediaFailed
  const posterUrl = capa?.podeExibir && !posterFailed ? capa.url : null

  const setVideoElement = useCallback((video: HTMLVideoElement | null) => {
    const previous = videoRef.current
    if (!video && previous) {
      previous.pause()
      if (activeCardVideo === previous) activeCardVideo = null
    }
    videoRef.current = video
  }, [])

  useEffect(() => {
    if (midia.autorizada) setSessionAuthorized(true)
  }, [midia.autorizada])

  useEffect(() => {
    setPosterFailed(false)
  }, [capa?.url])

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
      className="absolute inset-0 z-10 bg-zinc-100 focus-within:ring-2 focus-within:ring-inset focus-within:ring-pink-500"
      onClick={(event) => event.stopPropagation()}
      data-anuncio-card-video
      data-video-poster-origin={capa?.origem ?? "PLACEHOLDER_NEUTRO"}
    >
      <SensitiveVideo
        midia={midia}
        anuncioId={anuncioId}
        anuncioSlug={anuncioSlug}
        preload="none"
        className="object-cover transition-transform duration-500 group-hover:scale-[1.02]"
        onVideoElementChange={setVideoElement}
        onVerificationSuccess={() => {
          setSessionAuthorized(true)
          setMediaFailed(false)
          onVerificationSuccess?.()
        }}
        onPlay={(video) => {
          if (activeCardVideo && activeCardVideo !== video) {
            activeCardVideo.pause()
          }
          activeCardVideo = video
          setPlaying(true)
          setMediaFailed(false)
        }}
        onPause={(video) => {
          if (activeCardVideo === video) activeCardVideo = null
          setPlaying(false)
        }}
        onEnded={(video) => {
          if (activeCardVideo === video) activeCardVideo = null
          setPlaying(false)
        }}
        onError={() => {
          setPlaying(false)
          setMediaFailed(true)
        }}
        onRetry={() => setMediaFailed(false)}
      />

      {showPoster ? (
        <div className="pointer-events-none absolute inset-0 z-10 overflow-hidden bg-zinc-100">
          {posterUrl ? (
            <Image
              src={posterUrl}
              alt={capa?.altText ?? "Vídeo"}
              fill
              priority={priority}
              sizes="(max-width: 768px) 100vw, 360px"
              className="object-cover object-center"
              onError={() => setPosterFailed(true)}
            />
          ) : (
            <div
              className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-zinc-100 text-zinc-700"
              role="img"
              aria-label="Vídeo"
            >
              <VideoCameraIcon className="h-10 w-10 text-zinc-500" aria-hidden="true" />
              <span className="text-sm font-semibold">Vídeo</span>
            </div>
          )}

          {posterUrl ? <div className="absolute inset-0 bg-black/25" /> : null}
          <div className="absolute inset-0 flex items-center justify-center">
            <span className="flex flex-col items-center gap-2 rounded-md bg-black/70 px-3 py-2 text-xs font-semibold text-white ring-1 ring-white/30">
              <PlayCircleIcon className="h-10 w-10" aria-hidden="true" />
              {blocked ? "Confirmar maioridade" : "Reproduzir vídeo"}
            </span>
          </div>
        </div>
      ) : null}
    </div>
  )
}
