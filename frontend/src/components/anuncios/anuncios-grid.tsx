"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { ContractState } from "@/components/feedback/contract-state"
import {
  listarAnunciosPublicos,
  type PublicCatalogCard,
  type PublicCategoryList,
} from "@/lib/public-catalog-api"
import { AnuncioCard } from "./anuncio-card"

interface AnunciosGridProps {
  categoria: string
  busca?: string
  currentPage?: number
  initialData?: PublicCategoryList | null
  initialRequest?: {
    categoria: string
    busca: string
    currentPage: number
  }
}

const ITENS_POR_PAGINA = 16
const LIMITE_PAGINAS_AUTOMATICAS = 3

export default function AnunciosGrid({
  categoria,
  busca = "",
  currentPage = 1,
  initialData = null,
  initialRequest,
}: AnunciosGridProps) {
  const initialMatches =
    initialData !== null &&
    initialRequest?.categoria === categoria &&
    initialRequest.busca === busca &&
    initialRequest.currentPage === currentPage
  const [anuncios, setAnuncios] = useState<PublicCatalogCard[]>(
    initialMatches ? initialData.itens : []
  )
  const [loading, setLoading] = useState(!initialMatches)
  const [error, setError] = useState<unknown>(null)
  const [reloadMarker, setReloadMarker] = useState(0)
  const [proximaPagina, setProximaPagina] = useState<number | null>(() => {
    if (!initialMatches) return null
    return initialData.paginacao.pagina + 1 < initialData.paginacao.totalPaginas
      ? initialData.paginacao.pagina + 1
      : null
  })
  const [paginasCarregadas, setPaginasCarregadas] = useState(initialMatches ? 1 : 0)
  const sentinelRef = useRef<HTMLDivElement | null>(null)
  const consultaAtualRef = useRef(0)
  const initialRequestConsumedRef = useRef<string | null>(null)

  useEffect(() => {
    let ativa = true
    const consulta = consultaAtualRef.current + 1
    consultaAtualRef.current = consulta

    const fetchAnuncios = async () => {
      const requestKey = `${categoria}\u0000${busca}\u0000${currentPage}`
      if (
        initialMatches &&
        initialRequestConsumedRef.current !== requestKey
      ) {
        setAnuncios(initialData.itens)
        setPaginasCarregadas(1)
        setProximaPagina(
          initialData.paginacao.pagina + 1 < initialData.paginacao.totalPaginas
            ? initialData.paginacao.pagina + 1
            : null,
        )
        initialRequestConsumedRef.current = requestKey
        setLoading(false)
        return
      }

      setLoading(true)
      setError(null)
      setAnuncios([])
      setProximaPagina(null)
      setPaginasCarregadas(0)

      try {
        const paginaInicial = Math.max(currentPage - 1, 0)
        const data = await listarAnunciosPublicos(
          categoria,
          busca,
          paginaInicial,
          ITENS_POR_PAGINA,
        )
        if (!ativa || consulta !== consultaAtualRef.current) return
        setAnuncios(data.itens)
        setPaginasCarregadas(1)
        setProximaPagina(
          data.paginacao.pagina + 1 < data.paginacao.totalPaginas
            ? data.paginacao.pagina + 1
            : null,
        )
      } catch (fetchError) {
        if (!ativa || consulta !== consultaAtualRef.current) return
        setError(fetchError)
      } finally {
        if (ativa && consulta === consultaAtualRef.current) setLoading(false)
      }
    }

    void fetchAnuncios()
    return () => {
      ativa = false
    }
  }, [
    busca,
    categoria,
    currentPage,
    initialData,
    initialMatches,
    reloadMarker,
  ])

  const carregarMais = useCallback(async () => {
    if (loading || proximaPagina == null) return
    const consulta = consultaAtualRef.current
    setLoading(true)
    setError(null)
    try {
      const data = await listarAnunciosPublicos(
        categoria,
        busca,
        proximaPagina,
        ITENS_POR_PAGINA,
      )
      if (consulta !== consultaAtualRef.current) return
      setAnuncios((atuais) => {
        const ids = new Set(atuais.map((anuncio) => anuncio.id))
        return [...atuais, ...data.itens.filter((anuncio) => !ids.has(anuncio.id))]
      })
      setPaginasCarregadas((quantidade) => quantidade + 1)
      setProximaPagina(
        data.paginacao.pagina + 1 < data.paginacao.totalPaginas
          ? data.paginacao.pagina + 1
          : null,
      )
    } catch (fetchError) {
      if (consulta === consultaAtualRef.current) {
        setError(fetchError)
      }
    } finally {
      if (consulta === consultaAtualRef.current) setLoading(false)
    }
  }, [busca, categoria, loading, proximaPagina])

  const podeCarregarAutomaticamente =
    !error && !loading && proximaPagina != null && paginasCarregadas < LIMITE_PAGINAS_AUTOMATICAS
  const existeMaisPagina = proximaPagina != null

  useEffect(() => {
    if (!podeCarregarAutomaticamente || !sentinelRef.current) return

    const observer = new IntersectionObserver(
      (entries) => {
        const [entry] = entries
        if (!entry?.isIntersecting) return

        void carregarMais()
      },
      { rootMargin: "240px 0px" },
    )

    observer.observe(sentinelRef.current)
    return () => observer.disconnect()
  }, [carregarMais, podeCarregarAutomaticamente])

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

  if (error && anuncios.length === 0) {
    return (
      <div className="py-6">
        <ContractState error={error} onRetry={() => setReloadMarker((value) => value + 1)} />
      </div>
    )
  }

  if (anuncios.length === 0) {
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
        {anuncios.map((anuncio, index) => (
          <AnuncioCard
            key={anuncio.id}
            id={anuncio.id}
            slug={anuncio.slug}
            nome={anuncio.titulo}
            estadoUf={anuncio.estadoUf}
            cidadeNome={anuncio.cidadeNome}
            bairroNome={anuncio.bairroNome}
            pontoReferenciaTexto={anuncio.enderecoResumido}
            valor={`A partir de R$ ${anuncio.preco?.toFixed(2) ?? "0,00"} / hora`}
            midias={anuncio.midias}
            descricao={anuncio.descricao}
            destaque={anuncio.destaqueAtivo}
            anunciaDesde={anuncio.anunciaDesde}
            carrosselDisponivel={anuncio.carrosselDisponivel}
            videoHabilitado={anuncio.videoHabilitado}
            whatsappCardEnabled={anuncio.whatsappCardEnabled}
            comLocal={anuncio.comLocal}
            fazAnal={anuncio.fazAnal}
            mediaPriority={index === 0}
            onAccessUpdated={() => setReloadMarker((prev) => prev + 1)}
          />
        ))}
      </div>

      {podeCarregarAutomaticamente && <div ref={sentinelRef} className="h-8 w-full" />}

      {error !== null && anuncios.length > 0 && (
        <ContractState error={error} onRetry={() => void carregarMais()} compact />
      )}

      {existeMaisPagina && !podeCarregarAutomaticamente && (
        <div className="flex justify-center pt-2">
          <Button
            variant="outline"
            className="rounded-full border-pink-200 px-6 text-pink-600 hover:bg-pink-50 hover:text-pink-700"
            disabled={loading}
            onClick={() => void carregarMais()}
          >
            {loading ? "Carregando..." : "Ver mais resultados"}
          </Button>
        </div>
      )}
    </section>
  )
}
