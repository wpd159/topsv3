'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useRef, useState } from 'react'
import { ChevronLeftIcon, ChevronRightIcon, MapPinIcon } from '@heroicons/react/24/solid'
import { SensitiveImage } from '@/components/compliance/sensitive-image'
import { buildLocalizacaoLabel } from '@/lib/location'

interface AnuncioRelacionado {
  id: number
  titulo: string
  preco: number
  estadoUf?: string | null
  cidadeNome?: string | null
  bairroNome?: string | null
  localizacao?: string | null
  fotosUrl?: string[]
  slug?: string
  impulsionado?: boolean
  destaqueAtivo?: boolean
  categoria?: string | null
  contentClassification?: string | null
  requiresVisitorVerification?: boolean
  requiresStrongVerification?: boolean
  viewerAuthorized?: boolean
}

interface AnunciosRelacionadosProps {
  anuncioIdAtual?: number
  estadoUf?: string | null
  cidadeSlug?: string | null
  cidadeNome?: string | null
  bairroNome?: string | null
  categoria?: string | null
}

function slugify(value?: string | null) {
  if (!value) return ''
  return value
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9\s-]/g, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
}

function ordenarAnuncios(
  itens: AnuncioRelacionado[],
  bairroNome?: string | null,
  categoria?: string | null
) {
  const bairroNormalizado = (bairroNome || '').toLowerCase()
  const categoriaNormalizada = (categoria || '').toLowerCase()

  return [...itens].sort((a, b) => {
    const pontuacaoA =
      (a.impulsionado ? 100 : 0) +
      ((a.bairroNome || '').toLowerCase() === bairroNormalizado ? 10 : 0) +
      ((a.categoria || '').toLowerCase() === categoriaNormalizada ? 6 : 0)
    const pontuacaoB =
      (b.impulsionado ? 100 : 0) +
      ((b.bairroNome || '').toLowerCase() === bairroNormalizado ? 10 : 0) +
      ((b.categoria || '').toLowerCase() === categoriaNormalizada ? 6 : 0)

    if (pontuacaoA !== pontuacaoB) return pontuacaoB - pontuacaoA
    return a.titulo.localeCompare(b.titulo)
  })
}

export function AnunciosRelacionados({
  anuncioIdAtual,
  estadoUf,
  cidadeSlug,
  cidadeNome,
  bairroNome,
  categoria,
}: AnunciosRelacionadosProps) {
  const router = useRouter()
  const [anuncios, setAnuncios] = useState<AnuncioRelacionado[]>([])
  const [autoRotacaoAtiva, setAutoRotacaoAtiva] = useState(true)
  const scrollRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (!cidadeSlug || !estadoUf) {
      setAnuncios([])
      return
    }

    ;(async () => {
      try {
        const res = await fetch(
          `${process.env.NEXT_PUBLIC_API_URL}/anuncios/por-cidade/${encodeURIComponent(estadoUf)}/${encodeURIComponent(cidadeSlug)}?page=0&size=16`,
          { cache: 'no-store', credentials: 'include' }
        )
        if (!res.ok) throw new Error('Erro ao carregar anúncios recomendados')
        const data = await res.json()
        const lista = Array.isArray(data)
          ? data
          : Array.isArray(data?.content)
            ? data.content
            : []

        const filtrados = lista.filter((item: AnuncioRelacionado) => item?.id !== anuncioIdAtual)
        setAnuncios(ordenarAnuncios(filtrados, bairroNome, categoria).slice(0, 8))
      } catch {
        setAnuncios([])
      }
    })()
  }, [anuncioIdAtual, bairroNome, categoria, cidadeSlug, estadoUf])

  useEffect(() => {
    if (anuncios.length <= 1 || !autoRotacaoAtiva) return

    const interval = window.setInterval(() => {
      const container = scrollRef.current
      if (!container || window.innerWidth >= 768) return

      const card = container.querySelector<HTMLElement>('[data-card-relacionado]')
      const scrollStep = card?.offsetWidth ? card.offsetWidth + 16 : 201
      const maxScrollLeft = container.scrollWidth - container.clientWidth
      const nextLeft = container.scrollLeft + scrollStep

      container.scrollTo({
        left: nextLeft >= maxScrollLeft - 8 ? 0 : nextLeft,
        behavior: 'smooth',
      })
    }, 5000)

    return () => window.clearInterval(interval)
  }, [anuncios.length, autoRotacaoAtiva])

  if (anuncios.length === 0) return null

  const tituloSecao = cidadeNome ? `Mais perfis em ${cidadeNome}` : 'Você também pode gostar'

  const pararAutoRotacao = () => setAutoRotacaoAtiva(false)

  const navegar = (direcao: 'anterior' | 'proximo') => {
    pararAutoRotacao()
    const container = scrollRef.current
    if (!container) return

    const card = container.querySelector<HTMLElement>('[data-card-relacionado]')
    const scrollStep = card?.offsetWidth ? card.offsetWidth + 16 : 201

    container.scrollBy({
      left: direcao === 'anterior' ? -scrollStep : scrollStep,
      behavior: 'smooth',
    })
  }

  return (
    <section className="space-y-4 border-t pt-8">
      <div className="space-y-1">
        <h2 className="text-2xl font-bold text-gray-900">{tituloSecao}</h2>
        <p className="text-sm leading-relaxed text-gray-600">
          Continue navegando por anúncios da mesma cidade e descubra outros perfis com localização
          semelhante e mais opções de contato direto.
        </p>
      </div>

      <div className="relative -mx-4 px-4 md:mx-0 md:px-0">
        <button
          type="button"
          onClick={() => navegar('anterior')}
          aria-label="Ver perfis anteriores"
          className="absolute left-5 top-1/2 z-20 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-full bg-black/65 text-white shadow-lg ring-1 ring-white/20 transition hover:bg-black/80 md:hidden"
        >
          <ChevronLeftIcon className="h-5 w-5" />
        </button>

        <button
          type="button"
          onClick={() => navegar('proximo')}
          aria-label="Ver mais perfis"
          className="absolute right-5 top-1/2 z-20 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-full bg-black/65 text-white shadow-lg ring-1 ring-white/20 transition hover:bg-black/80 md:hidden"
        >
          <ChevronRightIcon className="h-5 w-5" />
        </button>

        <div
          ref={scrollRef}
          className="-mx-4 overflow-x-auto px-4 md:mx-0 md:px-0"
          onTouchStart={pararAutoRotacao}
          onWheel={pararAutoRotacao}
          onMouseEnter={pararAutoRotacao}
        >
        <div className="flex gap-4 pb-2 md:grid md:grid-cols-4">
          {anuncios.map((anuncio) => {
            const slugRota = (anuncio.slug ?? '').trim() || slugify(anuncio.titulo) || 'anuncio'
            const hrefAnuncio = `/anuncios/${encodeURIComponent(slugRota)}`

            const localizacaoLabel = buildLocalizacaoLabel(anuncio, 'compact')
            const srcCapa =
              Array.isArray(anuncio.fotosUrl) && anuncio.fotosUrl[0]?.trim()
                ? anuncio.fotosUrl[0]
                : '/icone-sem-foto.png'

            return (
              <Link
                href={hrefAnuncio}
                key={anuncio.id}
                data-card-relacionado
                onClick={pararAutoRotacao}
                className="w-[185px] shrink-0 overflow-hidden rounded-2xl border border-gray-200 bg-white transition hover:-translate-y-1 hover:border-pink-200 md:w-full"
              >
                <div className="relative h-[150px] w-full bg-gray-100">
                  <SensitiveImage
                    anuncioId={anuncio.id}
                    anuncioSlug={slugRota}
                    anuncioNome={anuncio.titulo}
                    cidade={anuncio.cidadeNome ?? null}
                    contentClassification={anuncio.contentClassification ?? null}
                    src={srcCapa}
                    alt={anuncio.titulo}
                    fill
                    sizes="(max-width: 768px) 185px, 25vw"
                    className="object-cover"
                    requiresVisitorVerification={Boolean(anuncio.requiresVisitorVerification)}
                    requiresStrongVerification={Boolean(anuncio.requiresStrongVerification)}
                    viewerAuthorized={Boolean(anuncio.viewerAuthorized)}
                    deferCompliancePreview
                    onAbrirPaginaDoAnuncio={() => router.push(hrefAnuncio)}
                  />
                  {anuncio.destaqueAtivo && (
                    <span className="absolute left-3 top-3 z-[25] rounded-full bg-pink-600 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.12em] text-white">
                      Em destaque
                    </span>
                  )}
                </div>

                <div className="space-y-2 p-3">
                  <p className="line-clamp-1 text-sm font-semibold text-gray-900">{anuncio.titulo}</p>

                  <p className="flex items-start gap-1 text-xs leading-tight text-gray-600">
                    <MapPinIcon className="mt-[2px] h-4 w-4 shrink-0 text-pink-500" />
                    <span className="line-clamp-2">{localizacaoLabel}</span>
                  </p>

                  <p className="text-xs font-semibold text-pink-600">
                    R$ {Number(anuncio.preco ?? 0).toFixed(2)}
                  </p>
                </div>
              </Link>
            )
          })}
        </div>
        </div>
      </div>
    </section>
  )
}
