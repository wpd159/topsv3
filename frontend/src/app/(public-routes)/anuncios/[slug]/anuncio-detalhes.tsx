"use client"

import { useParams } from "next/navigation"
import { useEffect, useRef, useState } from "react"
import HeaderTabs from "./componentes/header-tabs"
import MainContent from "./componentes/main-content"
import Sidebar from "./componentes/sidebar"
import { AnunciosRelacionados } from "./componentes/anuncios-relacionados"
import { AvisosAdministracao } from "./componentes/avisos-administracao"
import type { MidiaPublica } from "@/lib/media/public-media"

type AnuncioUI = {
  id: number
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
  usuarioId: number
  descricaoAnuncio?: string | null
  midias: MidiaPublica[]
  categoria?: string | null
  servicos?: string[]
  locaisAtendimento?: string[]
  linkConteudo?: string | null
  horario?: string | null
}

type AnuncioApiPayload = Record<string, any>

function dedupeStrings(values: unknown): string[] {
  if (!Array.isArray(values)) return []

  const seen = new Set<string>()
  const result: string[] = []

  for (const value of values) {
    if (typeof value !== "string") continue
    const normalized = value.trim()
    if (!normalized || seen.has(normalized)) continue
    seen.add(normalized)
    result.push(normalized)
  }

  return result
}

function mapAnuncioPayload(slug: string, data: AnuncioApiPayload): AnuncioUI {
  return {
    id: data.id,
    slug,
    nome: data.titulo ?? "Anúncio",
    username: data.username ?? data.usernameAnunciante ?? null,
    idade: data.idade ?? null,
    estadoUf: data.estadoUf ?? null,
    cidadeNome: data.cidadeNome ?? null,
    bairroNome: data.bairroNome ?? null,
    pontoReferenciaTexto: data.pontoReferenciaTexto ?? null,
    impulsionado: Boolean(data.impulsionado),
    destaqueAtivo: Boolean(data.destaqueAtivo),
    carrosselDisponivel: Boolean(data.carrosselDisponivel),
    videoHabilitado: Boolean(data.videoHabilitado),
    whatsappCardEnabled: Boolean(data.whatsappCardEnabled),
    usuarioId: data.usuarioId,
    cidade: data.cidade ?? null,
    localizacao: data.localizacao ?? null,
    valor:
      data.preco !== undefined
        ? `R$ ${Number(data.preco).toFixed(2)}`
        : `R$ ${Number(data.valor ?? 0).toFixed(2)}`,
    tipo: data.categoria ?? "Não informado",
    descricaoAnunciante: data.descricaoAnunciante ?? data.descricao ?? null,
    descricaoAnuncio: data.descricaoAnuncio ?? data.descricao ?? null,
    midias: Array.isArray(data.midias) ? data.midias : [],
    categoria: data.categoria ?? null,
    servicos: dedupeStrings(data.servicos),
    locaisAtendimento: dedupeStrings(data.locaisAtendimento),
    linkConteudo: data.linkConteudo ?? null,
    horario: data.horario ?? null,
  }
}

export default function AnuncioDetalhesPageClient({
  initialData,
}: {
  initialData?: AnuncioApiPayload | null
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
  const visualizacaoRegistradaParaId = useRef<number | null>(null)
  const visualizacaoFetchParaId = useRef<number | null>(null)
  useEffect(() => {
    visualizacaoRegistradaParaId.current = null
    visualizacaoFetchParaId.current = null
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
        const res = await fetch(
          `${process.env.NEXT_PUBLIC_API_URL}/anuncios/publico/slug/${encodeURIComponent(slug)}`,
          { cache: "no-store", credentials: "include" }
        )

        if (!res.ok) throw new Error(await res.text())
        const data = await res.json()
        setAnuncio(mapAnuncioPayload(slug, data))
      } catch {
        setAnuncio(null)
      }
    })()
  }, [slug, reloadMarker, initialData, anuncio?.slug])

  useEffect(() => {
    if (!anuncio?.id || !slug) return
    if (anuncio.slug !== slug) return
    if (visualizacaoRegistradaParaId.current === anuncio.id) return

    const base = (process.env.NEXT_PUBLIC_API_URL || "").replace(/\/$/, "")
    if (!base) return

    if (visualizacaoFetchParaId.current === anuncio.id) return
    visualizacaoFetchParaId.current = anuncio.id

    const url = `${base}/api/public/anuncios/${encodeURIComponent(slug)}/visualizacao`
    if (process.env.NODE_ENV === "development") {
      console.debug("[AnuncioDetalhes] registrando visualização", { method: "POST", url })
    }

    void fetch(url, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: "{}",
    })
      .then((res) => {
        if (process.env.NODE_ENV === "development") {
          console.debug("[AnuncioDetalhes] visualizar resposta", res.status, res.ok)
        }
        if (res.ok) {
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
        Carregando anúncio...
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
        estadoUf={anuncio.estadoUf}
        cidadeSlug={slugifyCidade(anuncio.cidadeNome)}
        cidadeNome={anuncio.cidadeNome}
        bairroNome={anuncio.bairroNome}
        categoria={anuncio.categoria}
      />

    </div>
  )
}

function slugifyCidade(value?: string | null) {
  if (!value) return null
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9\s-]/g, "")
    .replace(/\s+/g, "-")
    .replace(/-+/g, "-")
}
