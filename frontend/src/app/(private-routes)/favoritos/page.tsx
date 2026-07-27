'use client'

import { HeartIcon as HeartOutline } from '@heroicons/react/24/outline'
import { AnuncioCard } from '@/components/anuncios/anuncio-card'
import { Button } from '@/components/ui/button'
import { useFavoritos } from '@/context/FavoritosContext'

function valor(preco?: number | null) {
  if (preco == null) return 'Valor não informado'
  return `A partir de ${new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(preco)} / hora`
}

export default function FavoritosPage() {
  const { itens, carregando, erro, recarregar } = useFavoritos()

  if (carregando && itens.length === 0) {
    return (
      <section className="mx-auto max-w-6xl px-4 py-8 text-center text-gray-500">
        Carregando anúncios favoritados...
      </section>
    )
  }

  if (erro && itens.length === 0) {
    return (
      <section className="mx-auto max-w-6xl px-4 py-8 text-center">
        <div className="rounded-xl border border-red-100 bg-red-50 px-5 py-12 text-red-700">
          <p>{erro}</p>
          <Button type="button" variant="outline" className="mt-4" onClick={() => void recarregar()}>
            Tentar novamente
          </Button>
        </div>
      </section>
    )
  }

  if (itens.length === 0) {
    return (
      <section className="mx-auto max-w-6xl px-4 py-8 text-center text-gray-500">
        <div className="rounded-xl border bg-gray-50 py-20">
          <HeartOutline className="mx-auto mb-3 h-10 w-10 text-gray-400" />
          Nenhum anúncio favoritado ainda.
        </div>
      </section>
    )
  }

  return (
    <section className="mx-auto max-w-[1300px] space-y-6 px-4 py-8">
      {erro ? <p className="text-sm text-red-600">{erro}</p> : null}
      <div className="grid grid-cols-1 gap-8 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
        {itens.map((anuncio) => (
          <AnuncioCard
            key={anuncio.id}
            id={anuncio.id}
            slug={anuncio.slug}
            nome={anuncio.titulo}
            estadoUf={anuncio.localizacao?.uf ?? null}
            cidadeNome={anuncio.localizacao?.cidade ?? null}
            bairroNome={anuncio.localizacao?.bairro ?? null}
            pontoReferenciaTexto={anuncio.localizacao?.enderecoResumido ?? null}
            valor={valor(anuncio.preco)}
            midias={anuncio.midias ?? []}
            descricao={anuncio.descricaoResumo}
            whatsappCardEnabled={anuncio.contatoDisponivel}
            comLocal={anuncio.comLocal}
            fazAnal={anuncio.fazAnal}
            anunciaDesde={anuncio.anunciaDesde ?? null}
          />
        ))}
      </div>
    </section>
  )
}
