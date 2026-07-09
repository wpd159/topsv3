'use client'

import { useEffect, useState } from 'react'
import { HeartIcon as HeartOutline } from '@heroicons/react/24/outline'
import { AnuncioCard } from '@/components/anuncios/anuncio-card'
import { useAuth } from '@/context/AuthContext'

type Anuncio = {
  id: number
  slug: string
  titulo: string
  descricao: string
  localizacao: string
  preco: number
  fotosUrl: string[]
  videosAnuncio?: string[]
  destaqueAtivo?: boolean
  carrosselDisponivel?: boolean
  videoHabilitado?: boolean
  nomeAnunciante?: string
  usernameAnunciante?: string
  telefoneAnunciante?: string
  favorito?: boolean
  whatsappCardEnabled?: boolean
}

export default function FavoritosPage() {
  const { usuario } = useAuth()
  const [favoritos, setFavoritos] = useState<Anuncio[]>([])
  const [loading, setLoading] = useState(true)
  const API = process.env.NEXT_PUBLIC_API_URL

  const carregarFavoritos = async () => {
    try {
      const res = await fetch(`${API}/anuncios/favoritos`, {
        credentials: 'include',
      })
      if (!res.ok) throw new Error('Erro ao carregar favoritos')
      const data: Anuncio[] = await res.json()
      setFavoritos(data)
    } catch (err) {
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    carregarFavoritos()
  }, [])

  const handleRemoverFavorito = (id: number) => {
    setFavoritos((prev) => prev.filter((f) => f.id !== id))
  }

  if (loading)
    return (
      <section className="max-w-6xl mx-auto px-4 py-8 text-center text-gray-500">
        Carregando anúncios favoritados...
      </section>
    )

  if (favoritos.length === 0)
    return (
      <section className="max-w-6xl mx-auto px-4 py-8 text-center text-gray-500">
        <div className="py-20 border rounded-xl bg-gray-50">
          <HeartOutline className="w-10 h-10 text-gray-400 mx-auto mb-3" />
          Nenhum anúncio favoritado ainda.
        </div>
      </section>
    )

  return (
    <section className="max-w-[1300px] mx-auto py-8 space-y-6">
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-8">
        {favoritos.map((a) => (
          <AnuncioCard
            key={a.id}
            id={a.id}
            slug={a.slug}
            usuarioId={usuario?.id}
            nome={a.titulo}
            nomeAnunciante={a.nomeAnunciante}
            usernameAnunciante={a.usernameAnunciante}
            cidadeNome={a.localizacao ?? 'Não informado'}
            valor={`A partir de R$ ${a.preco?.toFixed(2).replace('.', ',')} / hora`}
            imagens={a.fotosUrl?.length > 0 ? a.fotosUrl : []}
            videos={Array.isArray(a.videosAnuncio) ? a.videosAnuncio : []}
            descricao={a.descricao}
            telefone={a.telefoneAnunciante}
            favoritoInicial={true}
            destaque={a.destaqueAtivo ?? false}
            carrosselDisponivel={a.carrosselDisponivel ?? false}
            videoHabilitado={a.videoHabilitado ?? false}
            whatsappCardEnabled={a.whatsappCardEnabled ?? false}
            onDesfavoritar={() => handleRemoverFavorito(a.id)}
          />
        ))}
      </div>
    </section>
  )
}
