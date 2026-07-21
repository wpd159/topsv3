"use client"

import type { MouseEvent, ReactNode } from "react"
import { useRef, useState } from "react"
import { ContractState } from "@/components/feedback/contract-state"
import {
  listarPublicosPorBairro,
  listarPublicosPorCidade,
  listarPublicosPorEstado,
  type PublicCatalogList,
} from "@/lib/public-catalog-api"
import AnuncioCard from "./anuncio-card"

type EscopoListagem =
  | { tipo: "estado"; uf: string }
  | { tipo: "cidade"; uf: string; cidade: string }
  | { tipo: "bairro"; uf: string; cidade: string; bairro: string }

interface ListagemPublicaPaginadaProps {
  caminhoBase: string
  escopo: EscopoListagem
  initialData: PublicCatalogList
  children?: ReactNode
}

function hrefPagina(caminhoBase: string, pagina: number) {
  return pagina <= 0 ? caminhoBase : `${caminhoBase}?page=${pagina}`
}

async function carregarPagina(
  escopo: EscopoListagem,
  pagina: number,
  tamanho: number,
  ordemSeed: string,
) {
  if (escopo.tipo === "estado") {
    return listarPublicosPorEstado(escopo.uf, pagina, tamanho, ordemSeed)
  }
  if (escopo.tipo === "cidade") {
    return listarPublicosPorCidade(escopo.uf, escopo.cidade, pagina, tamanho, ordemSeed)
  }
  return listarPublicosPorBairro(
    escopo.uf,
    escopo.cidade,
    escopo.bairro,
    pagina,
    tamanho,
    ordemSeed,
  )
}

export function ListagemPublicaPaginada({
  caminhoBase,
  escopo,
  initialData,
  children,
}: ListagemPublicaPaginadaProps) {
  const [data, setData] = useState(initialData)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const pagina = data.paginacao.pagina
  const ordemSeed = initialData.paginacao.ordemSeed
  const consultaRef = useRef(0)
  const ultimaPaginaSolicitadaRef = useRef(pagina)
  const idsPorPaginaRef = useRef(
    new Map([[pagina, new Set(initialData.itens.map((anuncio) => anuncio.id))]]),
  )
  const gridRef = useRef<HTMLDivElement | null>(null)

  const navegar = async (paginaDestino: number, forcar = false) => {
    if (loading || (!forcar && paginaDestino === pagina)) return
    ultimaPaginaSolicitadaRef.current = paginaDestino
    const consulta = consultaRef.current + 1
    consultaRef.current = consulta
    setLoading(true)
    setError(null)

    try {
      const resposta = await carregarPagina(
        escopo,
        paginaDestino,
        data.paginacao.tamanho,
        ordemSeed,
      )
      if (consulta !== consultaRef.current) return
      if (resposta.paginacao.ordemSeed !== ordemSeed) {
        throw new Error("A ordenacao da listagem mudou durante a paginacao.")
      }

      const outrosIds = new Set(
        [...idsPorPaginaRef.current.entries()]
          .filter(([paginaVisitada]) => paginaVisitada !== paginaDestino)
          .flatMap(([, ids]) => [...ids]),
      )
      if (resposta.itens.some((anuncio) => outrosIds.has(anuncio.id))) {
        throw new Error("A listagem repetiu anuncios entre paginas.")
      }

      idsPorPaginaRef.current.set(
        paginaDestino,
        new Set(resposta.itens.map((anuncio) => anuncio.id)),
      )
      setData(resposta)
      gridRef.current?.scrollIntoView({ block: "start" })
    } catch (fetchError) {
      if (consulta === consultaRef.current) setError(fetchError)
    } finally {
      if (consulta === consultaRef.current) setLoading(false)
    }
  }

  const interceptar = (event: MouseEvent<HTMLAnchorElement>, paginaDestino: number) => {
    if (
      event.button !== 0 ||
      event.metaKey ||
      event.ctrlKey ||
      event.shiftKey ||
      event.altKey
    ) {
      return
    }
    event.preventDefault()
    void navegar(paginaDestino)
  }

  return (
    <>
      <div
        ref={gridRef}
        aria-busy={loading}
        className={`grid scroll-mt-24 grid-cols-1 gap-4 transition-opacity md:grid-cols-2 lg:grid-cols-4 ${
          loading ? "opacity-70" : "opacity-100"
        }`}
      >
        {data.itens.map((anuncio, index) => (
          <AnuncioCard
            key={anuncio.id}
            id={anuncio.id}
            slug={anuncio.slug}
            nome={anuncio.titulo}
            estadoUf={anuncio.estadoUf ?? null}
            cidadeNome={anuncio.cidadeNome ?? null}
            bairroNome={anuncio.bairroNome ?? null}
            valor={`A partir de R$ ${Number(anuncio.preco ?? 0).toFixed(2)} / hora`}
            midias={anuncio.midias ?? []}
            descricao={anuncio.descricao}
            destaque={anuncio.destaqueAtivo ?? false}
            visualizacoes={anuncio.visualizacoes}
            anunciaDesde={anuncio.anunciaDesde ?? null}
            carrosselDisponivel={anuncio.carrosselDisponivel ?? false}
            videoHabilitado={anuncio.videoHabilitado ?? false}
            whatsappCardEnabled={anuncio.whatsappCardEnabled ?? false}
            comLocal={anuncio.comLocal}
            fazAnal={anuncio.fazAnal}
            mediaPriority={index === 0}
          />
        ))}
      </div>

      {error !== null && (
        <ContractState
          error={error}
          compact
          onRetry={() => void navegar(ultimaPaginaSolicitadaRef.current, true)}
        />
      )}

      {children}

      {data.paginacao.totalPaginas > 1 && (
        <nav className="flex items-center justify-center gap-2 border-t py-8" aria-label="Paginacao">
          {pagina > 0 && (
            <a
              href={hrefPagina(caminhoBase, pagina - 1)}
              onClick={(event) => interceptar(event, pagina - 1)}
              aria-disabled={loading}
              className="rounded-lg border border-gray-300 px-4 py-2 hover:bg-gray-100"
            >
              Anterior
            </a>
          )}

          <div className="flex gap-1">
            {Array.from({ length: Math.min(data.paginacao.totalPaginas, 5) }).map((_, index) => (
              <a
                key={index}
                href={hrefPagina(caminhoBase, index)}
                onClick={(event) => interceptar(event, index)}
                aria-current={pagina === index ? "page" : undefined}
                aria-disabled={loading}
                className={`rounded-lg px-3 py-2 ${
                  pagina === index
                    ? "bg-pink-600 text-white"
                    : "border border-gray-300 hover:bg-gray-100"
                }`}
              >
                {index + 1}
              </a>
            ))}
          </div>

          {pagina < data.paginacao.totalPaginas - 1 && (
            <a
              href={hrefPagina(caminhoBase, pagina + 1)}
              onClick={(event) => interceptar(event, pagina + 1)}
              aria-disabled={loading}
              className="rounded-lg border border-gray-300 px-4 py-2 hover:bg-gray-100"
            >
              Proxima
            </a>
          )}
        </nav>
      )}
    </>
  )
}
