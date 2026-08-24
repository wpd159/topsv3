import type { Metadata } from "next"
import { notFound } from "next/navigation"
import AnunciosPageClient from "./anuncios-page-client"
import { buildPublicUrl } from "@/lib/seo/public-url"
import {
  buildPublicListingIndexingDecision,
  buildPublicRobotsMetadata,
  type PublicListingSearchParams,
} from "@/lib/seo/search-indexing-policy"
import {
  isPublicCatalogNotFound,
  listarAnunciosPublicos,
  type PublicCategoryList,
} from "@/lib/public-catalog-server-api"


type AnunciosSearchParams = PublicListingSearchParams & {
  page?: string
  busca?: string
  categoria?: string
  anunciante?: string
  estadoId?: string
  cidadeId?: string
  bairroId?: string
}

function searchValue(value: string | string[] | undefined) {
  return typeof value === "string" ? value : ""
}

function parsePositivePage(value?: string | string[]) {
  if (Array.isArray(value)) return null
  if (value === undefined) return 1
  if (value === "") return null
  if (!/^[1-9]\d*$/.test(value)) return null
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) ? parsed : null
}

export async function generateMetadata({
  searchParams: searchParamsPromise,
}: {
  searchParams: Promise<AnunciosSearchParams>
}): Promise<Metadata> {
  const searchParams = await searchParamsPromise
  const page = parsePositivePage(searchParams.page)
  if (page === null) {
    return {
      title: "Página inválida | Tops do Job",
      robots: buildPublicRobotsMetadata(false),
    }
  }
  const busca = searchValue(searchParams.busca).trim()
  const categoria = searchValue(searchParams.categoria).trim()
  const anunciante = searchValue(searchParams.anunciante).trim()
  const possuiFiltrosLocais = Boolean(
    searchParams.estadoId || searchParams.cidadeId || searchParams.bairroId
  )
  const url = new URL(buildPublicUrl("/anuncios"))
  const indexingDecision = buildPublicListingIndexingDecision(searchParams, page)
  url.search = indexingDecision.canonicalQuery

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
    robots: buildPublicRobotsMetadata(indexingDecision.indexable, true),
  }
}

export default async function AnunciosPage({
  searchParams: searchParamsPromise,
}: {
  searchParams: Promise<AnunciosSearchParams>
}) {
  const searchParams = await searchParamsPromise
  const currentPage = parsePositivePage(searchParams.page)
  if (currentPage === null) notFound()
  const categoria = searchValue(searchParams.categoria).trim() || "TODOS"
  const busca = searchValue(searchParams.busca).trim()
  const anunciante = searchValue(searchParams.anunciante).trim()
  let initialData: PublicCategoryList

  try {
    initialData = await listarAnunciosPublicos(
      categoria,
      busca,
      currentPage - 1,
      16,
      undefined,
      anunciante,
    )
  } catch (error) {
    if (categoria.toUpperCase() !== "TODOS" && isPublicCatalogNotFound(error)) {
      notFound()
    }
    throw error
  }

  if (currentPage > Math.max(1, initialData.paginacao.totalPaginas)) {
    notFound()
  }

  return (
    <AnunciosPageClient
      initialData={initialData}
      initialRequest={{ categoria, busca, anunciante, currentPage }}
    />
  )
}
