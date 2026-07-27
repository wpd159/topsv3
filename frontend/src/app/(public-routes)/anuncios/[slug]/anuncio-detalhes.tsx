"use client"

import { useParams } from "next/navigation"
import { useEffect, useRef, useState } from "react"
import HeaderTabs from "./componentes/header-tabs"
import MainContent from "./componentes/main-content"
import Sidebar from "./componentes/sidebar"
import { AnunciosRelacionados } from "./componentes/anuncios-relacionados"
import { AvisosAdministracao } from "./componentes/avisos-administracao"
import type { MidiaPublica } from "@/lib/media/public-media"
import {
  obterAnuncioPublicoPorSlug,
  type PublicCatalogCard,
  type PublicCatalogDetail,
} from "@/lib/public-catalog-api"
import {
  novaChaveMetricaPublica,
  registrarVisualizacaoPublica,
} from "@/lib/public-metrics-api"
import type { VisualizacoesCanonicas } from "@/lib/visualizacoes-canonicas"

type AnuncioUI = {
  id: string
  slug: string
  nome: string
  username?: string | null
  idade?: number | null
  estadoUf?: string | null
  cidadeNome?: string | null
  bairroNome?: string | null
  pontoReferenciaTexto?: string | null
  cidade?: string | null
  localizacao?: string | null
  impulsionado?: boolean
  destaqueAtivo?: boolean
  carrosselDisponivel?: boolean
  videoHabilitado?: boolean
  whatsappCardEnabled?: boolean
  valor: string
  tipo: string
  descricaoAnunciante?: string | null
  descricaoAnuncio?: string | null
  midias: MidiaPublica[]
  categoria?: string | null
  servicos?: string[]
  locaisAtendimento?: string[]
  linkConteudo?: string | null
  horario?: string | null
  anunciaDesde?: string | null
  visualizacoes: VisualizacoesCanonicas
}

function mapAnuncioPayload(slug: string, data: PublicCatalogDetail): AnuncioUI {
  return {
    id: data.id,
    slug,
    nome: data.titulo ?? "Anúncio",
    username: data.username ?? null,
    idade: data.idade ?? null,
    estadoUf: data.estadoUf ?? null,
    cidadeNome: data.cidadeNome ?? null,
    bairroNome: data.bairroNome ?? null,
    pontoReferenciaTexto: data.enderecoResumido ?? null,
    impulsionado: Boolean(data.topoAtivo),
    destaqueAtivo: Boolean(data.destaqueAtivo),
    carrosselDisponivel: Boolean(data.carrosselDisponivel),
    videoHabilitado: Boolean(data.videoHabilitado),
    whatsappCardEnabled: Boolean(data.whatsappCardEnabled),
    cidade: data.cidadeNome ?? null,
    localizacao: data.enderecoResumido ?? null,
    valor:
      data.preco != null
        ? `R$ ${Number(data.preco).toFixed(2)}`
        : "Valor não informado",
    tipo: data.categoria ?? "Não informado",
    descricaoAnunciante: data.descricao ?? null,
    descricaoAnuncio: data.descricao ?? null,
    midias: Array.isArray(data.midias) ? data.midias : [],
    categoria: data.categoria ?? null,
    servicos: data.servicos,
    locaisAtendimento: data.locaisAtendimento,
    linkConteudo: null,
    horario: null,
    anunciaDesde: data.anunciaDesde ?? null,
    visualizacoes: data.visualizacoes,
  }
}

export default function AnuncioDetalhesPageClient({
  initialData,
  initialRelatedData,
}: {
  initialData?: PublicCatalogDetail | null
  initialRelatedData?: PublicCatalogCard[]
}) {
  const params = useParams<{ slug: string }>()
  const slug =
    typeof params.slug === "string"
      ? params.slug
      : Array.isArray(params.slug)
        ? params.slug[0]
        : ""

  const [anuncio, setAnuncio] = useState<AnuncioUI | null>(() =>
    initialData && slug ? mapAnuncioPayload(slug, initialData) : null
  )
  const [imagemAtiva, setImagemAtiva] = useState(0)
  const [reloadMarker, setReloadMarker] = useState(0)
  const [loadError, setLoadError] = useState(false)
  const visualizacaoRegistradaParaId = useRef<string | null>(null)
  const visualizacaoFetchParaId = useRef<string | null>(null)
  const visualizacaoChaveParaId = useRef<{ anuncioId: string; chave: string } | null>(null)
  useEffect(() => {
    visualizacaoRegistradaParaId.current = null
    visualizacaoFetchParaId.current = null
    visualizacaoChaveParaId.current = null
  }, [slug])

  useEffect(() => {
    if (!slug) return
    if (
      reloadMarker === 0 &&
      initialData &&
      anuncio?.slug === slug
    ) {
      return
    }

    ;(async () => {
      try {
        const data = await obterAnuncioPublicoPorSlug(slug)
        setLoadError(false)
        setAnuncio(mapAnuncioPayload(slug, data))
      } catch {
        setLoadError(true)
        setAnuncio(null)
      }
    })()
  }, [slug, reloadMarker, initialData, anuncio?.slug])

  useEffect(() => {
    if (!anuncio?.id || !slug) return
    if (anuncio.slug !== slug) return
    if (visualizacaoRegistradaParaId.current === anuncio.id) return

    if (visualizacaoFetchParaId.current === anuncio.id) return
    visualizacaoFetchParaId.current = anuncio.id

    const chaveRegistro = visualizacaoChaveParaId.current?.anuncioId === anuncio.id
      ? visualizacaoChaveParaId.current
      : {
          anuncioId: anuncio.id,
          chave: novaChaveMetricaPublica("visualizacao"),
        }
    visualizacaoChaveParaId.current = chaveRegistro

    void registrarVisualizacaoPublica(slug, chaveRegistro.chave)
      .then((response) => {
        if (response.registrado) {
          visualizacaoRegistradaParaId.current = anuncio.id
        }
      })
      .catch((err) => {
        if (process.env.NODE_ENV === "development") {
          console.warn("[AnuncioDetalhes] visualizar falhou", err)
        }
      })
      .finally(() => {
        if (visualizacaoFetchParaId.current === anuncio.id) {
          visualizacaoFetchParaId.current = null
        }
      })
  }, [anuncio?.id, anuncio?.slug, slug])

  if (!anuncio) {
    return (
      <div className="mx-auto py-16 text-center text-gray-500">
        {loadError ? "Não foi possível carregar o anúncio." : "Carregando anúncio..."}
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-[1240px] space-y-8 px-4 py-4 sm:py-6">
      <HeaderTabs
        anuncio={anuncio}
        imagemAtiva={imagemAtiva}
        setImagemAtiva={setImagemAtiva}
        onAccessUpdated={() => setReloadMarker((prev) => prev + 1)}
      />

      <section className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="order-1 flex flex-col gap-6 lg:order-2">
          <Sidebar anuncio={anuncio} />
          <AvisosAdministracao />
        </div>

        <div className="order-2 lg:order-1 lg:col-span-2">
          <MainContent anuncio={anuncio} />
        </div>
      </section>

      <AnunciosRelacionados
        anuncioIdAtual={anuncio.id}
        anuncios={initialRelatedData ?? []}
        cidadeNome={anuncio.cidadeNome}
        bairroNome={anuncio.bairroNome}
        categoria={anuncio.categoria}
      />

    </div>
  )
}
