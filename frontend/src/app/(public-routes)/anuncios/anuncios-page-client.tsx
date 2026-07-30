"use client"

import { Suspense } from "react"
import { useSearchParams } from "next/navigation"
import { BarraLocalizacao } from "@/components/anuncios/barra-localizacao"
import AnunciosGrid from "@/components/anuncios/anuncios-grid"
import { StoriesBar } from "@/components/stories/stories-bar"
import type { PublicCategoryList } from "@/lib/public-catalog-api"

type InitialRequest = {
  categoria: string
  busca: string
  currentPage: number
}

type AnunciosPageClientProps = {
  initialData: PublicCategoryList | null
  initialRequest: InitialRequest
}

function parsePageParam(value: string | null) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 1) return 1
  return Math.floor(parsed)
}

function AnunciosPageContent({ initialData, initialRequest }: AnunciosPageClientProps) {
  const searchParams = useSearchParams()

  const categoriaParam = searchParams.get("categoria") || "TODOS"
  const buscaParam = searchParams.get("busca") || ""
  const paginaAtual = parsePageParam(searchParams.get("page"))

  return (
    <section className="space-y-4 px-4 py-4 md:space-y-6 md:py-8">
      <header className="mx-auto w-full max-w-7xl space-y-2">
        <h1 className="text-2xl font-bold text-gray-900 md:text-3xl">
          Anúncios de acompanhantes
        </h1>
        <p className="max-w-3xl text-sm leading-6 text-gray-600">
          Explore perfis publicados e refine a busca por categoria e localização.
        </p>
      </header>
      <BarraLocalizacao />
      <StoriesBar />
      <AnunciosGrid
        categoria={categoriaParam}
        busca={buscaParam}
        currentPage={paginaAtual}
        initialData={initialData}
        initialRequest={initialRequest}
      />
    </section>
  )
}

export default function AnunciosPageClient(props: AnunciosPageClientProps) {
  return (
    <Suspense fallback={<div className="py-16 text-center text-gray-500">Carregando anúncios...</div>}>
      <AnunciosPageContent {...props} />
    </Suspense>
  )
}
