'use client'

import { useEffect, useMemo, useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { ContractState } from '@/components/feedback/contract-state'
import { listarCategoriasHomePublicas } from '@/lib/public-catalog-api'

const TODAS = { id: 'TODOS', nome: 'Todos' }

export default function FiltrosAnuncios({
  categoriaAtual,
}: {
  categoriaAtual: string
}) {
  const router = useRouter()
  const searchParams = useSearchParams()
  const [categoriasAtivas, setCategoriasAtivas] = useState<Array<{ id: string; nome: string }>>([])
  const [erroCategorias, setErroCategorias] = useState<unknown>(null)
  const [reloadMarker, setReloadMarker] = useState(0)

  useEffect(() => {
    let cancelled = false

    listarCategoriasHomePublicas()
      .then((categorias) => {
        if (cancelled) return
        setErroCategorias(null)
        setCategoriasAtivas(categorias.map((categoria) => ({
          id: categoria.identificador,
          nome: categoria.titulo,
        })))
      })
      .catch((error) => {
        if (cancelled) return
        setErroCategorias(error)
      })

    return () => {
      cancelled = true
    }
  }, [reloadMarker])

  const categoriasVisiveis = useMemo(
    () => [TODAS, ...categoriasAtivas],
    [categoriasAtivas]
  )

  const handleCategoriaClick = (catId: string) => {
    const params = new URLSearchParams(searchParams)

    if (catId === 'TODOS') {
      params.delete('categoria')
    } else {
      params.set('categoria', catId)
    }

    params.delete('page')
    const nextQuery = params.toString()
    router.push(nextQuery ? `/anuncios?${nextQuery}` : '/anuncios', { scroll: false })
  }

  return (
    <div className="mb-3 w-full border-b border-gray-200 pb-2 md:mb-6 md:pb-3">
      <div className="flex gap-2 overflow-x-auto pb-1 [-ms-overflow-style:none] [scrollbar-width:none] sm:flex-wrap sm:overflow-visible [&::-webkit-scrollbar]:hidden">
        {categoriasVisiveis.map((c) => (
          <Button
            key={c.id}
            variant={c.id === categoriaAtual ? 'default' : 'outline'}
            onClick={() => handleCategoriaClick(c.id)}
            className="shrink-0 border-pink-100 text-xs shadow-[0_0_10px_rgba(252,30,173,0.06)] transition hover:-translate-y-0.5 hover:border-pink-200 hover:shadow-[0_0_14px_rgba(252,30,173,0.12)] sm:text-sm"
          >
            {c.nome}
          </Button>
        ))}
      </div>
      {erroCategorias ? (
        <div className="mt-3">
          <ContractState
            error={erroCategorias}
            onRetry={() => setReloadMarker((value) => value + 1)}
            compact
          />
        </div>
      ) : null}
    </div>
  )
}
