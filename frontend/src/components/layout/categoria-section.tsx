'use client'

import Link from 'next/link'
import { JSX, useEffect, useState } from 'react'
import { CategoriaCard } from './categoria-card'
import {
  HeartIcon,
  UsersIcon,
  SparklesIcon,
  FireIcon,
  PhotoIcon,
} from '@heroicons/react/24/solid'

interface Categoria {
  id: number | null
  nome: string | null
  descricao: string | null
  imagemUrl: string | null
  categoriaEnum: string
  ativo: boolean
}

type CategoriaCardData = {
  titulo: string
  descricao: string
  imagem: string
  icon: JSX.Element
  categoriaEnum: string
}

const CATEGORY_META_BY_ENUM: Record<
  string,
  { titulo: string; descricao: string; imagem: string; icon: JSX.Element }
> = {
  ACOMPANHANTE_FEMININA: {
    titulo: 'Acompanhante feminina',
    descricao: 'Encontre as melhores acompanhantes femininas.',
    imagem: '/cards/acompanhante-feminina.jpg',
    icon: <HeartIcon className="w-6 h-6 text-pink-500" />,
  },
  TRANSEX_TRAVESTIS: {
    titulo: 'Transex e Travestis',
    descricao: 'As mais desejadas transex e travestis.',
    imagem: '/cards/acompanhante-trans.jpg',
    icon: <UsersIcon className="w-6 h-6 text-pink-500" />,
  },
  MASSAGENS: {
    titulo: 'Massagens',
    descricao: 'Massagistas sensuais e terapêuticas.',
    imagem: '/cards/massagem.jpg',
    icon: <SparklesIcon className="w-6 h-6 text-pink-500" />,
  },
  ACOMPANHANTE_MASCULINO: {
    titulo: 'Acompanhante masculino',
    descricao: 'Homens elegantes e discretos.',
    imagem: '/cards/acompanhante-masculino.jpg',
    icon: <FireIcon className="w-6 h-6 text-pink-500" />,
  },
  ENCONTROS_CASUAIS: {
    titulo: 'Casual e encontros',
    descricao: 'Encontros leves e espontâneos.',
    imagem: '/cards/casual.jpg',
    icon: <HeartIcon className="w-6 h-6 text-pink-500" />,
  },
  VENDA_DE_CONTEUDO: {
    titulo: 'Sexo Virtual',
    descricao: 'Videochamadas, conteúdo exclusivo e atendimento online.',
    imagem: '/cards/casual.jpg',
    icon: <PhotoIcon className="w-6 h-6 text-pink-500" />,
  },
}

const GENERIC_CATEGORY_META = {
  titulo: 'Categoria',
  descricao: 'Confira anúncios disponíveis nesta categoria.',
  imagem: '/cards/casual.jpg',
  icon: <SparklesIcon className="w-6 h-6 text-pink-500" />,
}

export default function CategoriasSection() {
  const [categorias, setCategorias] = useState<CategoriaCardData[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function load() {
      try {
        const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/categorias-home`)
        if (!res.ok) throw new Error('Erro ao carregar categorias da home')

        const data = await res.json()
        const mapped = Array.isArray(data)
          ? data
              .filter((c: Categoria) => c.ativo !== false && c.categoriaEnum)
              .map((c: Categoria) => {
                const meta = CATEGORY_META_BY_ENUM[c.categoriaEnum] ?? GENERIC_CATEGORY_META

                return {
                  titulo: c.categoriaEnum === 'VENDA_DE_CONTEUDO' ? meta.titulo : c.nome?.trim() || meta.titulo,
                  descricao: c.categoriaEnum === 'VENDA_DE_CONTEUDO' ? meta.descricao : c.descricao?.trim() || meta.descricao,
                  imagem: c.imagemUrl?.trim() || meta.imagem,
                  icon: meta.icon,
                  categoriaEnum: c.categoriaEnum,
                }
              })
          : []

        setCategorias(mapped)
      } catch {
        setCategorias([])
      } finally {
        setLoading(false)
      }
    }

    load()
  }, [])

  return (
    <section className="mx-auto max-w-7xl px-6 py-16">
      <h2 className="text-3xl font-bold mb-10 text-center">
        Categorias em destaque
      </h2>

      {(loading || categorias.length > 0) && (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 md:grid-cols-6 lg:grid-cols-5">
          {loading &&
            Array.from({ length: 5 }).map((_, index) => (
              <div
                key={index}
                className="min-h-[280px] animate-pulse rounded-2xl bg-gray-100 md:col-span-2 lg:col-span-1"
              />
            ))}

          {!loading &&
            categorias.map((c, index) => (
              <Link
                key={c.categoriaEnum}
                href={`/anuncios?categoria=${c.categoriaEnum}`}
                className={`block md:col-span-2 lg:col-span-1 lg:col-start-auto ${
                  categorias.length === 5 && index === 3 ? 'md:col-start-2' : ''
                }`}
              >
                <CategoriaCard {...c} />
              </Link>
            ))}
        </div>
      )}

      {!loading && categorias.length === 0 && (
        <p className="mt-6 text-center text-sm text-gray-500">
          Categorias indisponíveis no momento.
        </p>
      )}
    </section>
  )
}
