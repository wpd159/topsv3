import Image from 'next/image'
import Link from 'next/link'
import { MapPinIcon } from '@heroicons/react/24/solid'
import {
  fontePublicaSegura,
  selecionarCapaPublicaSegura,
} from '@/lib/media/public-media'
import type { PublicRelatedAd } from '@/lib/public-catalog-api'

interface AnunciosRelacionadosProps {
  anuncios: PublicRelatedAd[]
}

const moeda = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
})

export function AnunciosRelacionados({ anuncios }: AnunciosRelacionadosProps) {
  if (anuncios.length === 0) return null

  return (
    <section data-related-ads className="space-y-4 border-t border-gray-200 pt-7">
      <h2 className="text-xl font-bold text-gray-900">Você também pode gostar:</h2>

      <div className="-mx-4 overflow-x-auto px-4 pb-2 sm:mx-0 sm:px-0">
        <div className="flex min-w-max gap-3 sm:grid sm:min-w-0 sm:grid-cols-2 xl:grid-cols-3">
          {anuncios.map((anuncio) => {
            const href = `/anuncios/${encodeURIComponent(anuncio.slug)}`
            const capa = selecionarCapaPublicaSegura(anuncio.midias)
            const fonte = capa ? fontePublicaSegura(capa) : null

            return (
              <article
                key={anuncio.id}
                data-related-ad-card
                className="w-[190px] overflow-hidden rounded-lg border border-gray-200 bg-white sm:w-auto"
              >
                <Link href={href} className="group flex h-full flex-col" aria-label={`Ver perfil de ${anuncio.titulo}`}>
                  <div className="relative aspect-[4/5] w-full overflow-hidden bg-gray-100">
                    {fonte ? (
                      <Image
                        src={fonte}
                        alt={`Foto de ${anuncio.titulo}`}
                        fill
                        sizes="(max-width: 639px) 190px, (max-width: 1279px) 33vw, 260px"
                        className="object-cover transition-transform duration-300 group-hover:scale-[1.02]"
                      />
                    ) : (
                      <div className="flex h-full items-center justify-center px-4 text-center text-xs text-gray-500">
                        Mídia indisponível
                      </div>
                    )}
                  </div>

                  <div className="flex min-h-[142px] flex-1 flex-col gap-2 p-3">
                    <h3 className="line-clamp-2 text-sm font-semibold leading-5 text-gray-900">
                      {anuncio.idade == null
                        ? anuncio.titulo
                        : `${anuncio.titulo}, ${anuncio.idade} anos`}
                    </h3>

                    <p className="flex items-start gap-1 text-xs leading-4 text-gray-600">
                      <MapPinIcon className="mt-px h-4 w-4 shrink-0 text-pink-500" aria-hidden="true" />
                      <span>{anuncio.cidadeNome}, {anuncio.estadoUf}</span>
                    </p>

                    {anuncio.preco != null ? (
                      <p className="text-xs font-semibold text-gray-800">
                        A partir de {moeda.format(anuncio.preco)}
                      </p>
                    ) : null}

                    <span className="mt-auto inline-flex min-h-9 items-center justify-center rounded-md bg-pink-600 px-3 py-2 text-xs font-semibold text-white transition group-hover:bg-pink-700">
                      Ver perfil
                    </span>
                  </div>
                </Link>
              </article>
            )
          })}
        </div>
      </div>
    </section>
  )
}
