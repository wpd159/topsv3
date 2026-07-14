"use client"

import { useEffect, useMemo, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { corrigirEstruturaTexto } from "@/lib/text/encoding"
import { type MidiaPublica } from "@/lib/media/public-media"
import { AnuncioCard } from "./anuncio-card"

type OrdenacaoDistancia = "MAIOR" | "MENOR"

interface Anuncio {
  id: number
  slug: string
  titulo: string
  descricao: string
  localizacao: string
  preco: number
  midias?: MidiaPublica[]
  nomeAnunciante?: string
  usernameAnunciante?: string
  cidadeAnunciante?: string
  favorito?: boolean
  impulsionado?: boolean
  destaqueAtivo?: boolean
  videoHabilitado?: boolean
  dataFimImpulsionamento?: string
  visualizacoes?: number
  latitude?: number
  longitude?: number
  estadoUf?: string | null
  cidadeNome?: string | null
  bairroNome?: string | null
  pontoReferenciaTexto?: string | null
  idade?: number | null
  carrosselDisponivel?: boolean
  whatsappCardEnabled?: boolean
  comLocal?: boolean
  fazAnal?: boolean
  anunciaDesde?: string | null
}

interface AnunciosGridProps {
  categoria: string
  busca?: string
  estadoId?: number
  cidadeId?: number
  bairroId?: number
  currentPage?: number
  ordenacaoDistancia?: {
    ordem: OrdenacaoDistancia
    coords: { latitude: number; longitude: number } | null
  } | null
}

const ITENS_POR_PAGINA = 16
const LIMITE_PAGINAS_AUTOMATICAS = 3

function distanciaKm(lat1: number, lon1: number, lat2: number, lon2: number) {
  const raio = 6371
  const dLat = (lat2 - lat1) * (Math.PI / 180)
  const dLon = (lon2 - lon1) * (Math.PI / 180)
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * (Math.PI / 180)) *
      Math.cos(lat2 * (Math.PI / 180)) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2)
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return raio * c
}

export default function AnunciosGrid({
  categoria,
  busca = "",
  estadoId,
  cidadeId,
  bairroId,
  currentPage = 1,
  ordenacaoDistancia,
}: AnunciosGridProps) {
  const [anuncios, setAnuncios] = useState<Anuncio[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadMarker, setReloadMarker] = useState(0)
  const [paginasVisiveis, setPaginasVisiveis] = useState(1)

  const sentinelRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    setPaginasVisiveis(1)
  }, [bairroId, busca, categoria, cidadeId, currentPage, estadoId])

  useEffect(() => {
    const fetchAnuncios = async () => {
      setLoading(true)
      setError(null)

      try {
        const params = new URLSearchParams()

        if (categoria && categoria !== "TODOS") {
          params.set("categoriaEnum", categoria)
        }

        const termoBusca = busca.trim()
        if (termoBusca) params.set("busca", termoBusca)
        if (estadoId) params.set("estadoId", String(estadoId))
        if (cidadeId) params.set("cidadeId", String(cidadeId))
        if (bairroId) params.set("bairroId", String(bairroId))

        const url = `${process.env.NEXT_PUBLIC_API_URL}/anuncios${params.toString() ? `?${params.toString()}` : ""}`
        const res = await fetch(url, { cache: "no-store", credentials: "include" })

        if (!res.ok) {
          throw new Error(`Erro ${res.status}`)
        }

        const data = corrigirEstruturaTexto((await res.json()) as Anuncio[])
        setAnuncios(Array.isArray(data) ? data : [])
      } catch {
        setError("Não foi possível carregar os anúncios.")
      } finally {
        setLoading(false)
      }
    }

    void fetchAnuncios()
  }, [bairroId, busca, categoria, cidadeId, estadoId, reloadMarker])

  const anunciosOrdenados = useMemo(() => {
    if (!ordenacaoDistancia || !ordenacaoDistancia.coords) return anuncios

    const { latitude: latUser, longitude: lonUser } = ordenacaoDistancia.coords

    return [...anuncios].sort((a, b) => {
      const temCoordA = a.latitude != null && a.longitude != null
      const temCoordB = b.latitude != null && b.longitude != null

      if (!temCoordA && !temCoordB) return 0
      if (!temCoordA) return 1
      if (!temCoordB) return -1

      const distA = distanciaKm(latUser, lonUser, a.latitude!, a.longitude!)
      const distB = distanciaKm(latUser, lonUser, b.latitude!, b.longitude!)

      return ordenacaoDistancia.ordem === "MENOR" ? distA - distB : distB - distA
    })
  }, [anuncios, ordenacaoDistancia])

  const totalPages = Math.max(1, Math.ceil(anunciosOrdenados.length / ITENS_POR_PAGINA))
  const paginaBase = Math.min(Math.max(currentPage, 1), totalPages)
  const ultimaPaginaVisivel = Math.min(totalPages, paginaBase + paginasVisiveis - 1)

  const itensVisiveis = useMemo(() => {
    const start = (paginaBase - 1) * ITENS_POR_PAGINA
    const end = ultimaPaginaVisivel * ITENS_POR_PAGINA
    return anunciosOrdenados.slice(start, end)
  }, [anunciosOrdenados, paginaBase, ultimaPaginaVisivel])

  const podeCarregarAutomaticamente =
    paginasVisiveis < LIMITE_PAGINAS_AUTOMATICAS && ultimaPaginaVisivel < totalPages
  const existeMaisPagina = ultimaPaginaVisivel < totalPages

  useEffect(() => {
    if (!podeCarregarAutomaticamente || !sentinelRef.current) return

    const observer = new IntersectionObserver(
      (entries) => {
        const [entry] = entries
        if (!entry?.isIntersecting) return

        setPaginasVisiveis((prev) => {
          if (prev >= LIMITE_PAGINAS_AUTOMATICAS) return prev
          return prev + 1
        })
      },
      { rootMargin: "240px 0px" }
    )

    observer.observe(sentinelRef.current)
    return () => observer.disconnect()
  }, [podeCarregarAutomaticamente])

  if (loading && anuncios.length === 0) {
    return (
      <section className="py-3">
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 8 }).map((_, index) => (
            <div key={index} className="h-[420px] animate-pulse rounded-xl bg-gray-100" />
          ))}
        </div>
      </section>
    )
  }

  if (error) {
    return <div className="py-10 text-center text-red-500">{error}</div>
  }

  if (anunciosOrdenados.length === 0) {
    return (
      <section className="py-3">
        <div className="py-10 text-center text-gray-400">Nenhum anúncio encontrado.</div>
      </section>
    )
  }

  return (
    <section className="space-y-6 py-3" aria-busy={loading}>
      <div
        className={`grid grid-cols-1 gap-6 transition-opacity sm:grid-cols-2 lg:grid-cols-4 ${
          loading ? "opacity-70" : "opacity-100"
        }`}
      >
        {itensVisiveis.map((anuncio) => (
          <AnuncioCard
            key={anuncio.id}
            id={anuncio.id}
            slug={anuncio.slug ?? anuncio.titulo.toLowerCase().replace(/\s+/g, "-")}
            nome={anuncio.titulo}
            nomeAnunciante={anuncio.nomeAnunciante}
            usernameAnunciante={anuncio.usernameAnunciante}
            estadoUf={anuncio.estadoUf ?? null}
            cidadeNome={anuncio.cidadeNome ?? null}
            bairroNome={anuncio.bairroNome ?? null}
            pontoReferenciaTexto={anuncio.pontoReferenciaTexto ?? null}
            idade={anuncio.idade ?? null}
            valor={`A partir de R$ ${anuncio.preco?.toFixed(2) ?? "0,00"} / hora`}
            midias={anuncio.midias ?? []}
            descricao={anuncio.descricao}
            destaque={anuncio.destaqueAtivo ?? false}
            anunciaDesde={anuncio.anunciaDesde ?? null}
            visualizacoes={anuncio.visualizacoes ?? 0}
            carrosselDisponivel={anuncio.carrosselDisponivel ?? false}
            videoHabilitado={anuncio.videoHabilitado ?? false}
            whatsappCardEnabled={anuncio.whatsappCardEnabled ?? false}
            comLocal={anuncio.comLocal ?? false}
            fazAnal={anuncio.fazAnal ?? false}
            onAccessUpdated={() => setReloadMarker((prev) => prev + 1)}
          />
        ))}
      </div>

      {podeCarregarAutomaticamente && <div ref={sentinelRef} className="h-8 w-full" />}

      {existeMaisPagina && !podeCarregarAutomaticamente && (
        <div className="flex justify-center pt-2">
          <Button
            variant="outline"
            className="rounded-full border-pink-200 px-6 text-pink-600 hover:bg-pink-50 hover:text-pink-700"
            onClick={() => setPaginasVisiveis((prev) => prev + 1)}
          >
            Ver mais resultados
          </Button>
        </div>
      )}
    </section>
  )
}
