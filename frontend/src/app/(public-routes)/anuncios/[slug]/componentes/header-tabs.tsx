'use client'

import { createPortal } from 'react-dom'
import Image from 'next/image'
import { useEffect, useMemo, useRef, useState, type TouchEvent } from 'react'
import { ChevronLeftIcon, ChevronRightIcon, PlayIcon, XMarkIcon } from '@heroicons/react/24/solid'
import { SensitiveImage } from '@/components/compliance/sensitive-image'
import { fontePublicaSegura, type MidiaPublica } from '@/lib/media/public-media'
import { corrigirTextoCorrompido } from '@/lib/text/encoding'
import { cn } from '@/lib/utils'

type HeaderTabsProps = {
  anuncio: {
    id?: number
    slug?: string
    nome: string
    cidade?: string | null
    cidadeNome?: string | null
    midias?: MidiaPublica[]
  }
  imagemAtiva: number
  setImagemAtiva: (index: number) => void
  onAccessUpdated?: () => void
}

export default function HeaderTabs({
  anuncio,
  imagemAtiva,
  setImagemAtiva,
  onAccessUpdated,
}: HeaderTabsProps) {
  const [lightboxOpen, setLightboxOpen] = useState(false)
  const [mediaAtiva, setMediaAtiva] = useState(0)
  const touchStartX = useRef<number | null>(null)
  const nome = corrigirTextoCorrompido(anuncio.nome)
  const cidade = corrigirTextoCorrompido(anuncio.cidadeNome ?? anuncio.cidade ?? '')

  const midias = useMemo(
    () => [...(anuncio.midias ?? [])]
      .filter((item) => item && (item.tipo === 'FOTO' || item.tipo === 'VIDEO'))
      .sort((a, b) => (a.ordem ?? Number.MAX_SAFE_INTEGER) - (b.ordem ?? Number.MAX_SAFE_INTEGER)),
    [anuncio.midias]
  )
  const fotos = useMemo(() => midias.filter((item) => item.tipo === 'FOTO'), [midias])
  const mediaHero = midias[mediaAtiva]

  useEffect(() => {
    setMediaAtiva(0)
    setImagemAtiva(0)
  }, [anuncio.id, anuncio.slug, setImagemAtiva])

  useEffect(() => {
    if (!midias.length) setMediaAtiva(0)
    else if (mediaAtiva >= midias.length) setMediaAtiva(0)
  }, [mediaAtiva, midias.length])

  useEffect(() => {
    if (!mediaHero || mediaHero.tipo !== 'FOTO') return
    const photoIndex = fotos.findIndex((item) => item.id === mediaHero.id)
    if (photoIndex >= 0 && photoIndex !== imagemAtiva) setImagemAtiva(photoIndex)
  }, [fotos, imagemAtiva, mediaHero, setImagemAtiva])

  const selecionarMedia = (index: number) => {
    if (!midias.length) return
    setMediaAtiva(((index % midias.length) + midias.length) % midias.length)
  }

  const selecionarFoto = (index: number) => {
    if (!fotos.length) return
    const normalized = ((index % fotos.length) + fotos.length) % fotos.length
    setImagemAtiva(normalized)
    const mediaIndex = midias.findIndex((item) => item.id === fotos[normalized].id)
    if (mediaIndex >= 0) setMediaAtiva(mediaIndex)
  }

  const handleTouchStart = (event: TouchEvent<HTMLElement | HTMLDivElement>) => {
    touchStartX.current = event.touches[0]?.clientX ?? null
  }

  const handleTouchEnd = (event: TouchEvent<HTMLElement | HTMLDivElement>, total: number, current: number, select: (index: number) => void) => {
    if (touchStartX.current == null || total <= 1) return
    const delta = (event.changedTouches[0]?.clientX ?? 0) - touchStartX.current
    touchStartX.current = null
    if (Math.abs(delta) < 48) return
    select(delta > 0 ? current - 1 : current + 1)
  }

  const renderMidia = (midia: MidiaPublica, className: string, priority = false, onClick?: () => void) => {
    const source = fontePublicaSegura(midia)
    if (midia.tipo === 'VIDEO' && midia.autorizada && source) {
      return (
        <video
          key={String(midia.id)}
          src={source}
          controls
          muted
          playsInline
          preload="metadata"
          className={className}
        />
      )
    }

    return (
      <SensitiveImage
        midia={midia}
        anuncioId={anuncio.id}
        anuncioSlug={anuncio.slug}
        alt={cidade ? `${nome} em ${cidade}` : nome}
        fill
        priority={priority}
        className={className}
        onImageClick={onClick}
        onVerificationSuccess={onAccessUpdated}
      />
    )
  }

  const fotoLightbox = fotos[imagemAtiva]

  return (
    <div className="public-anuncio-gallery min-w-0 space-y-6">
      <section
        className="relative overflow-hidden rounded-xl border border-gray-200 bg-zinc-950"
        onTouchStart={handleTouchStart}
        onTouchEnd={(event) => handleTouchEnd(event, midias.length, mediaAtiva, selecionarMedia)}
      >
        <div className="relative h-[60svh] w-full sm:h-[64vh]">
          {mediaHero ? (
            renderMidia(
              mediaHero,
              'cursor-pointer bg-zinc-950 object-contain object-center',
              mediaHero.visibilidadeMidia === 'LIVRE',
              mediaHero.tipo === 'FOTO'
                ? () => {
                    const index = fotos.findIndex((item) => item.id === mediaHero.id)
                    if (index >= 0) {
                      setImagemAtiva(index)
                      setLightboxOpen(true)
                    }
                  }
                : undefined
            )
          ) : (
            <div className="relative h-full min-h-[60svh] w-full bg-gray-100 sm:min-h-[64vh]">
              <Image src="/icone-sem-foto.png" alt={`${nome} sem foto`} fill className="object-cover object-center" priority />
            </div>
          )}
        </div>

        {midias.length > 1 ? (
          <>
            <button type="button" onClick={() => selecionarMedia(mediaAtiva - 1)} className="absolute left-3 top-1/2 z-30 flex -translate-y-1/2 items-center justify-center rounded-full bg-black/65 p-2 text-white shadow-lg ring-1 ring-white/20 transition hover:bg-black/80" aria-label="Ver mídia anterior">
              <ChevronLeftIcon className="h-5 w-5" />
            </button>
            <button type="button" onClick={() => selecionarMedia(mediaAtiva + 1)} className="absolute right-3 top-1/2 z-30 flex -translate-y-1/2 items-center justify-center rounded-full bg-black/65 p-2 text-white shadow-lg ring-1 ring-white/20 transition hover:bg-black/80" aria-label="Ver próxima mídia">
              <ChevronRightIcon className="h-5 w-5" />
            </button>
          </>
        ) : null}
      </section>

      {midias.length ? (
        <section className="rounded-xl border border-gray-200 bg-white p-3" aria-label="Mini galeria">
          <div className="overflow-x-auto pb-1 no-scrollbar">
            <div className="flex min-w-full justify-center gap-2 sm:gap-3">
              {midias.map((item, index) => (
                <div
                  key={String(item.id)}
                  className={cn(
                    'relative h-24 w-20 flex-shrink-0 overflow-hidden rounded-lg border bg-black/5 transition sm:h-28 sm:w-24',
                    index === mediaAtiva ? 'border-pink-500 ring-2 ring-pink-200' : 'border-gray-200 hover:border-pink-300'
                  )}
                >
                  {renderMidia(item, 'object-cover object-center')}
                  {item.tipo === 'VIDEO' ? (
                    <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-black/25"><span className="flex h-8 w-8 items-center justify-center rounded-full bg-black/70 text-white"><PlayIcon className="h-4 w-4" /></span></div>
                  ) : null}
                  <button type="button" className="absolute inset-0 z-30 rounded-lg focus:outline-none focus:ring-2 focus:ring-pink-500 focus:ring-offset-2" onClick={() => selecionarMedia(index)} aria-label={`Selecionar mídia ${index + 1}`} />
                </div>
              ))}
            </div>
          </div>
        </section>
      ) : null}

      <h1 className="text-3xl font-bold leading-tight text-gray-950 sm:text-4xl">{nome}</h1>

      {lightboxOpen && fotoLightbox && typeof document !== 'undefined'
        ? createPortal(
            <div className="fixed inset-0 z-[999] flex items-center justify-center bg-black/90 px-4" onTouchStart={handleTouchStart} onTouchEnd={(event) => handleTouchEnd(event, fotos.length, imagemAtiva, selecionarFoto)}>
              <div className="relative flex max-h-[90vh] w-full max-w-5xl items-center justify-center">
                <button type="button" onClick={() => setLightboxOpen(false)} className="absolute right-2 top-2 z-20 rounded-full bg-white/10 p-2 text-white hover:bg-white/20" aria-label="Fechar galeria"><XMarkIcon className="h-6 w-6" /></button>
                {fotos.length > 1 ? <button type="button" onClick={() => selecionarFoto(imagemAtiva - 1)} className="absolute left-2 top-1/2 z-20 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-full bg-black/65 text-white" aria-label="Foto anterior"><ChevronLeftIcon className="h-6 w-6" /></button> : null}
                <div className="relative aspect-[3/4] max-h-[90vh] w-[90vw] md:w-[60vw] xl:w-[45vw]">
                  {renderMidia(fotoLightbox, 'object-contain', false)}
                  <div className="absolute bottom-3 left-1/2 -translate-x-1/2 rounded-full bg-black/65 px-3 py-1 text-xs font-medium text-white">{imagemAtiva + 1} / {fotos.length}</div>
                </div>
                {fotos.length > 1 ? <button type="button" onClick={() => selecionarFoto(imagemAtiva + 1)} className="absolute right-2 top-1/2 z-20 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-full bg-black/65 text-white" aria-label="Próxima foto"><ChevronRightIcon className="h-6 w-6" /></button> : null}
              </div>
            </div>,
            document.body
          )
        : null}
    </div>
  )
}
