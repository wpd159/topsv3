'use client'

import { useCallback, useEffect, useMemo, useRef, useState, type TouchEvent } from 'react'
import { createPortal } from 'react-dom'
import Image from 'next/image'
import { cn } from '@/lib/utils'
import { SensitiveImage, resolverUrlsSensitiveImage } from '@/components/compliance/sensitive-image'
import { corrigirTextoCorrompido } from '@/lib/text/encoding'
import { classificacaoIniciaComProtecao } from '@/lib/compliance/content-classification'
import {
  ChevronLeftIcon,
  ChevronRightIcon,
  PlayIcon,
  XMarkIcon,
} from '@heroicons/react/24/solid'

type HeaderTabsProps = {
  anuncio: {
    id?: number
    slug?: string
    nome: string
    cidade?: string | null
    imagens?: string[]
    fotos?: string[]
    videos?: string[]
    videosAnuncio?: string[]
    impulsionado?: boolean
    destaqueAtivo?: boolean
    carrosselDisponivel?: boolean
    videoHabilitado?: boolean
    whatsappCardEnabled?: boolean
    contentClassification?: string | null
    restrictedPreview?: boolean
    requiresVisitorVerification?: boolean
    requiresStrongVerification?: boolean
    viewerAuthorized?: boolean
  }
  imagemAtiva: number
  setImagemAtiva: (index: number) => void
  onAccessUpdated?: () => void
}

type MediaItem = {
  type: 'image' | 'video'
  src: string
}

function isKnownGenericFallbackMedia(src?: string | null) {
  if (!src) return false

  try {
    const parsed = new URL(src, 'https://topsdojob.com')
    const path = parsed.pathname.toLowerCase()
    return (
      path.endsWith('/2151117281.jpg') ||
      path.endsWith('/icone-sem-foto.png') ||
      path.endsWith('/logo-finallllll.webp')
    )
  } catch {
    const normalized = src.toLowerCase()
    return (
      normalized.includes('/2151117281.jpg') ||
      normalized.includes('/icone-sem-foto.png') ||
      normalized.includes('/logo-finallllll.webp')
    )
  }
}

export default function HeaderTabs({
  anuncio,
  imagemAtiva,
  setImagemAtiva,
  onAccessUpdated,
}: HeaderTabsProps) {
  const [lightboxOpen, setLightboxOpen] = useState(false)
  const [mediaAtiva, setMediaAtiva] = useState(0)
  const [imagensComErro, setImagensComErro] = useState<Set<string>>(() => new Set())
  const touchStartX = useRef<number | null>(null)

  const imagemFonteKey = useMemo(() => {
    return [
      ...(Array.isArray(anuncio.imagens) ? anuncio.imagens : []),
      ...(Array.isArray(anuncio.fotos) ? anuncio.fotos : []),
    ]
      .filter(Boolean)
      .join('|')
  }, [anuncio.imagens, anuncio.fotos])

  useEffect(() => {
    setImagensComErro(new Set())
  }, [imagemFonteKey])

  const marcarImagemIndisponivel = useCallback((src?: string | null) => {
    if (!src) return
    setImagensComErro((prev) => {
      if (prev.has(src)) return prev
      const next = new Set(prev)
      next.add(src)
      return next
    })
  }, [])

  const imagens = useMemo(() => {
    const lista = [
      ...(Array.isArray(anuncio.imagens) ? anuncio.imagens : []),
      ...(Array.isArray(anuncio.fotos) ? anuncio.fotos : []),
    ].filter(Boolean)

    return Array.from(new Set(lista)).filter(
      (src) => !isKnownGenericFallbackMedia(src) && !imagensComErro.has(src)
    )
  }, [anuncio.imagens, anuncio.fotos, imagensComErro])

  const videos = useMemo(() => {
    if (!anuncio.videoHabilitado) return []

    const lista = [
      ...(Array.isArray(anuncio.videos) ? anuncio.videos : []),
      ...(Array.isArray(anuncio.videosAnuncio) ? anuncio.videosAnuncio : []),
    ].filter(Boolean)

    return Array.from(new Set(lista))
  }, [anuncio.videoHabilitado, anuncio.videos, anuncio.videosAnuncio])

  const media: MediaItem[] = useMemo(() => {
    return [
      ...videos.map((src) => ({ type: 'video' as const, src })),
      ...imagens.map((src) => ({ type: 'image' as const, src })),
    ]
  }, [imagens, videos])

  const mediaInicialKey = useMemo(
    () => `${anuncio.id ?? anuncio.slug ?? 'anuncio'}:${media.map((item) => `${item.type}:${item.src}`).join('|')}`,
    [anuncio.id, anuncio.slug, media]
  )

  const imagensOriginais = useMemo(
    () => imagens.map((item) => resolverUrlsSensitiveImage(item).originalSrc),
    [imagens]
  )

  const nomeExibido = corrigirTextoCorrompido(anuncio.nome)
  const cidadeExibida = corrigirTextoCorrompido(anuncio.cidade ?? '')
  const deveIniciarProtegido =
    classificacaoIniciaComProtecao(anuncio.contentClassification) ||
    Boolean(anuncio.restrictedPreview) ||
    Boolean(anuncio.requiresVisitorVerification)
  const altSensivel = cidadeExibida
    ? `Perfil com fotos verificadas em ${cidadeExibida}`
    : 'Perfil com fotos verificadas'

  const mediaIndexImagemAtiva = useMemo(() => {
    const srcAtual = imagens[imagemAtiva]
    if (!srcAtual) return 0
    const index = media.findIndex((item) => item.type === 'image' && item.src === srcAtual)
    return index >= 0 ? index : 0
  }, [imagemAtiva, imagens, media])

  useEffect(() => {
    setMediaAtiva(0)
    setImagemAtiva(0)
  }, [mediaInicialKey, setImagemAtiva])

  useEffect(() => {
    setMediaAtiva((prev) => {
      if (media.length === 0) return 0
      if (prev >= media.length) return mediaIndexImagemAtiva
      return prev
    })
  }, [media.length, mediaIndexImagemAtiva])

  useEffect(() => {
    if (imagens.length > 0 && imagemAtiva >= imagens.length) {
      setImagemAtiva(0)
    }
  }, [imagemAtiva, imagens.length, setImagemAtiva])

  useEffect(() => {
    if (media.length === 0) return

    const mediaAtual = media[mediaAtiva]
    if (!mediaAtual || mediaAtual.type !== 'image') return

    const imageIndex = imagens.findIndex((img) => img === mediaAtual.src)
    if (imageIndex >= 0 && imageIndex !== imagemAtiva) {
      setImagemAtiva(imageIndex)
    }
  }, [imagemAtiva, imagens, media, mediaAtiva, setImagemAtiva])

  const selecionarMedia = (index: number) => {
    if (media.length === 0) return
    const next = ((index % media.length) + media.length) % media.length
    setMediaAtiva(next)
  }

  const openImage = (imageIndex: number) => {
    if (imageIndex < 0) return
    setImagemAtiva(imageIndex)
    const indexNaGaleria = media.findIndex(
      (item) => item.type === 'image' && item.src === imagens[imageIndex]
    )
    if (indexNaGaleria >= 0) {
      setMediaAtiva(indexNaGaleria)
    }
    setLightboxOpen(true)
  }

  const atualizarImagemAtiva = (nextImageIndex: number) => {
    if (imagens.length === 0) return

    const normalizedIndex = ((nextImageIndex % imagens.length) + imagens.length) % imagens.length
    setImagemAtiva(normalizedIndex)

    const mediaIndex = media.findIndex(
      (item) => item.type === 'image' && item.src === imagens[normalizedIndex]
    )

    if (mediaIndex >= 0) {
      setMediaAtiva(mediaIndex)
    }
  }

  const goPrev = () => {
    if (imagens.length === 0) return
    atualizarImagemAtiva(imagemAtiva - 1)
  }

  const goNext = () => {
    if (imagens.length === 0) return
    atualizarImagemAtiva(imagemAtiva + 1)
  }

  const handleLightboxTouchStart = (event: TouchEvent<HTMLDivElement>) => {
    touchStartX.current = event.touches[0]?.clientX ?? null
  }

  const handleLightboxTouchEnd = (event: TouchEvent<HTMLDivElement>) => {
    if (touchStartX.current == null) return
    const delta = (event.changedTouches[0]?.clientX ?? 0) - touchStartX.current
    touchStartX.current = null
    if (Math.abs(delta) < 48) return
    if (delta > 0) goPrev()
    else goNext()
  }

  const handleHeroTouchStart = (event: TouchEvent<HTMLElement>) => {
    touchStartX.current = event.touches[0]?.clientX ?? null
  }

  const handleHeroTouchEnd = (event: TouchEvent<HTMLElement>) => {
    if (touchStartX.current == null || media.length <= 1) return
    const delta = (event.changedTouches[0]?.clientX ?? 0) - touchStartX.current
    touchStartX.current = null
    if (Math.abs(delta) < 48) return
    selecionarMedia(delta > 0 ? mediaAtiva - 1 : mediaAtiva + 1)
  }

  const mediaHero = media[mediaAtiva]

  return (
    <div className="space-y-6">
      <section
        className="relative overflow-hidden rounded-xl border border-gray-200 bg-zinc-950"
        onTouchStart={handleHeroTouchStart}
        onTouchEnd={handleHeroTouchEnd}
      >
        <div className="relative h-[60svh] w-full sm:h-[64vh]">
          {mediaHero ? (
            mediaHero.type === 'image' ? (
              <SensitiveImage
                anuncioId={anuncio.id}
                anuncioSlug={anuncio.slug}
                anuncioNome={nomeExibido}
                cidade={cidadeExibida || null}
                contentClassification={anuncio.contentClassification}
                src={mediaHero.src}
                alt={deveIniciarProtegido ? altSensivel : `${nomeExibido} - mídia principal`}
                fill
                className="cursor-pointer bg-zinc-950 object-contain object-center"
                priority
                requiresVisitorVerification={Boolean(anuncio.requiresVisitorVerification)}
                requiresStrongVerification={Boolean(anuncio.requiresStrongVerification)}
                viewerAuthorized={Boolean(anuncio.viewerAuthorized)}
                deferCompliancePreview
                onImageClick={() => {
                  const imageIndex = imagens.findIndex((img) => img === mediaHero.src)
                  openImage(imageIndex)
                }}
                onError={() => marcarImagemIndisponivel(mediaHero.src)}
                onVerificationSuccess={onAccessUpdated}
              />
            ) : (
              <video
                key={mediaHero.src}
                src={mediaHero.src}
                autoPlay
                controls
                loop
                muted
                playsInline
                preload="auto"
                className="h-full w-full bg-zinc-950 object-contain"
              />
            )
          ) : (
            <div className="relative h-full min-h-[60svh] w-full bg-gray-100 sm:min-h-[64vh]">
              <Image
                src="/icone-sem-foto.png"
                alt={`${nomeExibido} sem foto`}
                fill
                className="object-cover object-center"
                priority
              />
            </div>
          )}

        </div>

        {media.length > 1 && (
          <>
            <button
              type="button"
              onClick={() => selecionarMedia(mediaAtiva - 1)}
              className="absolute left-3 top-1/2 z-30 flex -translate-y-1/2 items-center justify-center rounded-full bg-black/65 p-2 text-white shadow-lg ring-1 ring-white/20 transition hover:bg-black/80"
              aria-label="Ver mídia anterior"
            >
              <ChevronLeftIcon className="h-5 w-5" />
            </button>

            <button
              type="button"
              onClick={() => selecionarMedia(mediaAtiva + 1)}
              className="absolute right-3 top-1/2 z-30 flex -translate-y-1/2 items-center justify-center rounded-full bg-black/65 p-2 text-white shadow-lg ring-1 ring-white/20 transition hover:bg-black/80"
              aria-label="Ver próxima mídia"
            >
              <ChevronRightIcon className="h-5 w-5" />
            </button>
          </>
        )}
      </section>

      {media.length > 0 && (
        <section className="rounded-xl border border-gray-200 bg-white p-3" aria-label="Mini galeria">
          <div className="overflow-x-auto pb-1 no-scrollbar">
            <div className="flex min-w-full justify-center gap-2 sm:gap-3">
            {media.map((item, index) => {
              const isImage = item.type === 'image'
              const imageIndex = isImage ? imagens.findIndex((img) => img === item.src) : -1

              return (
                <div
                  key={`${item.type}-${item.src}-${index}`}
                  className={cn(
                    'relative h-24 w-20 flex-shrink-0 overflow-hidden rounded-lg border bg-black/5 transition sm:h-28 sm:w-24',
                    index === mediaAtiva
                      ? 'border-pink-500 ring-2 ring-pink-200'
                      : 'border-gray-200 hover:border-pink-300'
                  )}
                >
                  {isImage ? (
                    <SensitiveImage
                      anuncioId={anuncio.id}
                      anuncioSlug={anuncio.slug}
                      anuncioNome={nomeExibido}
                      cidade={cidadeExibida || null}
                      contentClassification={anuncio.contentClassification}
                      src={item.src}
                      alt={deveIniciarProtegido ? altSensivel : `${nomeExibido} - foto ${imageIndex + 1}`}
                      fill
                      className="cursor-pointer object-cover object-center"
                      requiresVisitorVerification={Boolean(anuncio.requiresVisitorVerification)}
                      requiresStrongVerification={Boolean(anuncio.requiresStrongVerification)}
                      viewerAuthorized={Boolean(anuncio.viewerAuthorized)}
                      deferCompliancePreview
                      onImageClick={() => selecionarMedia(index)}
                      onError={() => marcarImagemIndisponivel(item.src)}
                      onVerificationSuccess={onAccessUpdated}
                    />
                  ) : (
                    <>
                      <video src={item.src} playsInline muted preload="metadata" className="h-full w-full object-cover" />
                      <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-black/35">
                        <span className="flex h-8 w-8 items-center justify-center rounded-full bg-black/70 text-white">
                          <PlayIcon className="h-4 w-4" />
                        </span>
                      </div>
                    </>
                  )}
                  <button
                    type="button"
                    className="absolute inset-0 z-30 rounded-lg focus:outline-none focus:ring-2 focus:ring-pink-500 focus:ring-offset-2"
                    onClick={(event) => {
                      event.preventDefault()
                      event.stopPropagation()
                      selecionarMedia(index)
                    }}
                    aria-label={isImage ? `Selecionar foto ${imageIndex + 1}` : `Selecionar vídeo ${index + 1}`}
                  />
                </div>
              )
            })}
            </div>
          </div>
        </section>
      )}

      <h1 className="text-3xl font-bold leading-tight text-gray-950 sm:text-4xl">{nomeExibido}</h1>

      {lightboxOpen && imagensOriginais[imagemAtiva] && typeof document !== 'undefined'
        ? createPortal(
            <div
              className="fixed inset-0 z-[999] flex items-center justify-center bg-black/90 px-4"
              onTouchStart={handleLightboxTouchStart}
              onTouchEnd={handleLightboxTouchEnd}
            >
              <div className="relative flex max-h-[90vh] w-full max-w-5xl items-center justify-center">
                <button
                  type="button"
                  onClick={() => setLightboxOpen(false)}
                  className="absolute right-2 top-2 z-20 rounded-full bg-white/10 p-2 text-white hover:bg-white/20"
                  aria-label="Fechar galeria"
                >
                  <XMarkIcon className="h-6 w-6" />
                </button>

                {imagens.length > 1 && (
                  <button
                    type="button"
                    onClick={goPrev}
                    className="absolute left-2 top-1/2 z-20 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-full bg-black/65 text-white shadow-lg ring-1 ring-white/20 transition hover:bg-black/80"
                    aria-label="Foto anterior"
                  >
                    <ChevronLeftIcon className="h-6 w-6" />
                  </button>
                )}

                <div className="relative aspect-[3/4] max-h-[90vh] w-[90vw] md:w-[60vw] xl:w-[45vw]">
                  <SensitiveImage
                    anuncioId={anuncio.id}
                    anuncioSlug={anuncio.slug}
                    anuncioNome={nomeExibido}
                    cidade={cidadeExibida || null}
                    contentClassification={anuncio.contentClassification}
                    src={imagensOriginais[imagemAtiva]}
                    alt={deveIniciarProtegido ? altSensivel : `Visualização ampliada de ${nomeExibido}`}
                    fill
                    className="object-contain"
                    requiresVisitorVerification={Boolean(anuncio.requiresVisitorVerification)}
                    requiresStrongVerification={Boolean(anuncio.requiresStrongVerification)}
                    viewerAuthorized={Boolean(anuncio.viewerAuthorized)}
                    deferCompliancePreview
                    onError={() => marcarImagemIndisponivel(imagens[imagemAtiva])}
                    onVerificationSuccess={onAccessUpdated}
                  />
                  <div className="absolute bottom-3 left-1/2 -translate-x-1/2 rounded-full bg-black/65 px-3 py-1 text-xs font-medium text-white">
                    {imagemAtiva + 1} / {imagens.length}
                  </div>
                </div>

                {imagens.length > 1 && (
                  <button
                    type="button"
                    onClick={goNext}
                    className="absolute right-2 top-1/2 z-20 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-full bg-black/65 text-white shadow-lg ring-1 ring-white/20 transition hover:bg-black/80"
                    aria-label="Próxima foto"
                  >
                    <ChevronRightIcon className="h-6 w-6" />
                  </button>
                )}
              </div>
            </div>,
            document.body
          )
        : null}
    </div>
  )
}
