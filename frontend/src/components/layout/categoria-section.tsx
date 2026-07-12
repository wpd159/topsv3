import Link from 'next/link'
import { JSX } from 'react'
import {
  FireIcon,
  HeartIcon,
  PhotoIcon,
  SparklesIcon,
  UsersIcon,
} from '@heroicons/react/24/solid'
import { listarCategoriasHomePublicas } from '@/lib/public-catalog-api'
import { CategoriaCard } from './categoria-card'

type CategoriaCardData = {
  titulo: string
  descricao: string
  imagem: string
  icon: JSX.Element
  identificador: string
  destino: string
}

const CATEGORY_ICON_BY_ID: Record<string, JSX.Element> = {
  ACOMPANHANTE_FEMININA: <HeartIcon className="h-6 w-6 text-pink-500" />,
  TRANSEX_TRAVESTIS: <UsersIcon className="h-6 w-6 text-pink-500" />,
  MASSAGENS: <SparklesIcon className="h-6 w-6 text-pink-500" />,
  ACOMPANHANTE_MASCULINO: <FireIcon className="h-6 w-6 text-pink-500" />,
  ENCONTROS_CASUAIS: <HeartIcon className="h-6 w-6 text-pink-500" />,
  VENDA_DE_CONTEUDO: <PhotoIcon className="h-6 w-6 text-pink-500" />,
}

const GENERIC_CATEGORY_ICON = <SparklesIcon className="h-6 w-6 text-pink-500" />

export default async function CategoriasSection() {
  const categorias: CategoriaCardData[] = (await listarCategoriasHomePublicas()).map((categoria) => ({
    titulo: categoria.titulo,
    descricao: categoria.descricao,
    imagem: categoria.imagemPublicaUrl,
    icon: CATEGORY_ICON_BY_ID[categoria.identificador] ?? GENERIC_CATEGORY_ICON,
    identificador: categoria.identificador,
    destino: categoria.destino,
  }))

  return (
    <section className="mx-auto max-w-7xl px-6 py-16">
      <h2 className="mb-10 text-center text-3xl font-bold">Categorias em destaque</h2>

      {categorias.length > 0 && (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 md:grid-cols-6 lg:grid-cols-5">
          {categorias.map((categoria, index) => (
            <Link
              key={categoria.identificador}
              href={categoria.destino}
              className={`block md:col-span-2 lg:col-span-1 lg:col-start-auto ${
                categorias.length === 5 && index === 3 ? 'md:col-start-2' : ''
              }`}
            >
              <CategoriaCard {...categoria} />
            </Link>
          ))}
        </div>
      )}
    </section>
  )
}
