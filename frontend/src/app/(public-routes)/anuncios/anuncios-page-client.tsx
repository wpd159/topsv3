"use client"

import { useRouter, useSearchParams } from "next/navigation"
import { XMarkIcon } from "@heroicons/react/24/outline"
import { BarraLocalizacao } from "@/components/anuncios/barra-localizacao"
import AnunciosGrid from "@/components/anuncios/anuncios-grid"
import { StoriesBar } from "@/components/stories/stories-bar"
import { Button } from "@/components/ui/button"
import type { PublicCategoryList } from "@/lib/public-catalog-api"
import { parsePublicOrderSeed } from "@/lib/seo/public-url"

type InitialRequest = {
  categoria: string
  busca: string
  anunciante: string
  currentPage: number
  requestedSeed?: string
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

export default function AnunciosPageClient({ initialData, initialRequest }: AnunciosPageClientProps) {
  const router = useRouter()
  const searchParams = useSearchParams()

  const categoriaParam = searchParams.get("categoria")?.trim() || "TODOS"
  const buscaParam = searchParams.get("busca")?.trim() || ""
  const anuncianteParam = (searchParams.get("anunciante") || "").trim()
  const paginaAtual = parsePageParam(searchParams.get("page"))
  const requestedSeed = parsePublicOrderSeed(searchParams.get("ordemSeed") ?? undefined) ?? undefined

  const removerFiltroAnunciante = () => {
    const params = new URLSearchParams(searchParams)
    params.delete("anunciante")
    params.delete("page")
    params.delete("ordemSeed")
    const query = params.toString()
    router.push(query ? `/anuncios?${query}` : "/anuncios", { scroll: false })
  }

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
      {anuncianteParam ? (
        <div className="mx-auto flex w-full max-w-7xl flex-wrap items-center gap-2 text-sm text-gray-700">
          <span>
            Anuncios de <strong>@{anuncianteParam}</strong>
          </span>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="h-8 gap-1.5 rounded-full"
            onClick={removerFiltroAnunciante}
          >
            <XMarkIcon className="h-4 w-4" aria-hidden />
            Remover filtro
          </Button>
        </div>
      ) : null}
      <BarraLocalizacao />
      <StoriesBar />
      <AnunciosGrid
        categoria={categoriaParam}
        busca={buscaParam}
        anunciante={anuncianteParam}
        currentPage={paginaAtual}
        requestedSeed={requestedSeed}
        paginationQuery={Object.fromEntries([...searchParams.keys()].map((key) => [key, searchParams.getAll(key)]))}
        initialData={initialData}
        initialRequest={initialRequest}
      />
    </section>
  )
}
