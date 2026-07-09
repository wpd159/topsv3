'use client'

import { useEffect, useMemo, useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { Button } from '@/components/ui/button'

const CATEGORIAS = [
  { id: 'TODOS', nome: 'Todos' },
  { id: 'ACOMPANHANTE_FEMININA', nome: 'Acompanhante feminina' },
  { id: 'ACOMPANHANTE_MASCULINO', nome: 'Acompanhante masculino' },
  { id: 'MASSAGENS', nome: 'Massagens' },
  { id: 'TRANSEX_TRAVESTIS', nome: 'Transex & Travestis' },
  { id: 'VENDA_DE_CONTEUDO', nome: 'Sexo Virtual' },
]

type CategoriaHome = {
  categoriaEnum?: string
  ativo?: boolean
}

const DEFAULT_DISABLED_CATEGORIES = new Set(['ENCONTROS_CASUAIS'])

export default function FiltrosAnuncios({
  categoriaAtual,
}: {
  categoriaAtual: string
}) {
  const router = useRouter()
  const searchParams = useSearchParams()
  const [categoriasAtivas, setCategoriasAtivas] = useState<Set<string> | null>(null)

  useEffect(() => {
    const apiBase = process.env.NEXT_PUBLIC_API_URL?.replace(/\/$/, '')
    if (!apiBase) return

    let cancelled = false

    fetch(`${apiBase}/categorias-home`, { cache: 'no-store' })
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (cancelled || !Array.isArray(data)) return

        setCategoriasAtivas(
          new Set(
            data
              .filter((c: CategoriaHome) => c?.ativo !== false && c?.categoriaEnum)
              .map((c: CategoriaHome) => c.categoriaEnum as string)
          )
        )
      })
      .catch(() => {
        setCategoriasAtivas(null)
      })

    return () => {
      cancelled = true
    }
  }, [])

  const categoriasVisiveis = useMemo(
    () =>
      CATEGORIAS.filter((c) => {
        if (c.id === 'TODOS') return true
        if (categoriasAtivas) return categoriasAtivas.has(c.id)
        return !DEFAULT_DISABLED_CATEGORIES.has(c.id)
      }),
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
            className="shrink-0 text-xs sm:text-sm"
          >
            {c.nome}
          </Button>
        ))}
      </div>
    </div>
  )
}
