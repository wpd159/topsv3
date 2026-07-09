"use client"

import { Suspense } from "react"
import { useSearchParams } from "next/navigation"
import { BarraLocalizacao } from "@/components/anuncios/barra-localizacao"
import AnunciosGrid from "@/components/anuncios/anuncios-grid"
import { StoriesBar } from "@/components/stories/stories-bar"

function parsePageParam(value: string | null) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 1) return 1
  return Math.floor(parsed)
}

function AnunciosPageContent() {
  const searchParams = useSearchParams()

  const categoriaParam = searchParams.get("categoria") || "TODOS"
  const buscaParam = searchParams.get("busca") || ""
  const paginaAtual = parsePageParam(searchParams.get("page"))
  const estadoId = searchParams.get("estadoId")
  const cidadeId = searchParams.get("cidadeId")
  const bairroId = searchParams.get("bairroId")

  return (
    <section className="space-y-4 px-4 py-4 md:space-y-6 md:py-8">
      <BarraLocalizacao />
      <StoriesBar />
      <AnunciosGrid
        categoria={categoriaParam}
        busca={buscaParam}
        estadoId={estadoId ? Number(estadoId) : undefined}
        cidadeId={cidadeId ? Number(cidadeId) : undefined}
        bairroId={bairroId ? Number(bairroId) : undefined}
        currentPage={paginaAtual}
      />
    </section>
  )
}

export default function AnunciosPageClient() {
  return (
    <Suspense fallback={<div className="py-16 text-center text-gray-500">Carregando anúncios...</div>}>
      <AnunciosPageContent />
    </Suspense>
  )
}
