import type { Metadata } from "next"
import AnunciosPageClient from "./anuncios-page-client"
import { buildPublicUrl } from "@/lib/seo/public-url"

function parsePositivePage(value?: string) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 1) return 1
  return Math.floor(parsed)
}

export async function generateMetadata({
  searchParams: searchParamsPromise,
}: {
  searchParams: Promise<{
    page?: string
    busca?: string
    categoria?: string
    estadoId?: string
    cidadeId?: string
    bairroId?: string
  }>
}): Promise<Metadata> {
  const searchParams = await searchParamsPromise
  const page = parsePositivePage(searchParams.page)
  const busca = (searchParams.busca || "").trim()
  const categoria = (searchParams.categoria || "").trim()
  const possuiFiltrosLocais = Boolean(
    searchParams.estadoId || searchParams.cidadeId || searchParams.bairroId
  )
  const url = new URL(buildPublicUrl("/anuncios"))

  if (page > 1) url.searchParams.set("page", String(page))
  if (busca) url.searchParams.set("busca", busca)
  if (categoria && categoria !== "TODOS") url.searchParams.set("categoria", categoria)
  if (searchParams.estadoId) url.searchParams.set("estadoId", searchParams.estadoId)
  if (searchParams.cidadeId) url.searchParams.set("cidadeId", searchParams.cidadeId)
  if (searchParams.bairroId) url.searchParams.set("bairroId", searchParams.bairroId)

  const contextoBusca = busca
    ? `Resultados para ${busca}`
    : categoria && categoria !== "TODOS"
      ? `Anúncios em ${categoria.toLowerCase().replace(/_/g, " ")}`
      : "Anúncios de acompanhantes por cidade"

  const titleBase = busca
    ? `${contextoBusca} | Tops do Job`
    : "Anúncios de acompanhantes por cidade | Tops do Job"

  const title = page > 1 ? `${titleBase} | Página ${page}` : titleBase

  const descriptionBase = busca
    ? `Veja resultados para ${busca} e acesse os perfis publicados no Tops do Job.`
    : possuiFiltrosLocais
      ? "Explore anúncios filtrados por localização e acesse as informações publicadas em cada perfil."
      : "Explore anúncios de acompanhantes e encontre perfis publicados em diferentes cidades do Brasil."

  const description = page > 1 ? `${descriptionBase} Página ${page}.` : descriptionBase
  const indexavel = !busca && !possuiFiltrosLocais && (!categoria || categoria === "TODOS")

  return {
    title,
    description,
    alternates: {
      canonical: url.toString(),
    },
    openGraph: {
      title,
      description,
      url: url.toString(),
      type: "website",
      siteName: "Tops do Job",
      locale: "pt_BR",
    },
    robots: {
      index: indexavel,
      follow: true,
      "max-image-preview": "large",
      "max-snippet": -1,
      "max-video-preview": -1,
    },
  }
}

export default function AnunciosPage() {
  return <AnunciosPageClient />
}
