'use client'

import Image from 'next/image'
import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'
import { anuncioLocalizacao, anuncioModeracao, anuncioStatus } from '@/components/anuncios/meu-anuncio-card'
import { PainelShell } from '@/components/painel-anunciante/painel-shell'
import { buscarMeuAnuncio, MeusAnunciosApiError, type MeuAnuncio } from '@/lib/meus-anuncios-api'
import { cn } from '@/lib/utils'

export function MeuAnuncioDetalheView({ slug }: { slug: string }) {
  const [anuncio, setAnuncio] = useState<MeuAnuncio | null>(null)
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<string | null>(null)

  const carregar = useCallback(async () => {
    setLoading(true)
    setErro(null)
    try {
      setAnuncio(await buscarMeuAnuncio(slug))
    } catch (error) {
      if (error instanceof MeusAnunciosApiError && error.status === 403) {
        setErro('Você não tem permissão para acessar este anúncio.')
      } else if (error instanceof MeusAnunciosApiError && error.status === 404) {
        setErro('Anúncio não encontrado.')
      } else {
        setErro(error instanceof Error ? error.message : 'Não foi possível carregar o anúncio.')
      }
    } finally {
      setLoading(false)
    }
  }, [slug])

  useEffect(() => {
    void carregar()
  }, [carregar])

  const status = anuncio ? anuncioStatus(anuncio.status) : null

  return (
    <PainelShell
      title="Detalhes do anúncio"
      description="Consulte os dados atuais vinculados à sua conta."
    >
      {loading ? (
        <div className="flex min-h-[280px] items-center justify-center rounded-[28px] border border-slate-200 bg-white text-sm text-slate-500 shadow-sm">
          Carregando anúncio...
        </div>
      ) : erro || !anuncio || !status ? (
        <div className="rounded-[28px] border border-rose-200 bg-rose-50 p-6 text-center shadow-sm">
          <p className="font-semibold text-rose-900">{erro || 'Não foi possível carregar o anúncio.'}</p>
          <Link href="/meus-anuncios" className="mt-4 inline-flex text-sm font-semibold text-rose-800 underline underline-offset-4">
            Voltar para Meus anúncios
          </Link>
        </div>
      ) : (
        <article className="overflow-hidden rounded-[28px] border border-slate-200 bg-white shadow-sm">
          <div className="grid md:grid-cols-[minmax(260px,380px)_1fr]">
            <div className="relative aspect-[3/4] min-h-[320px] bg-slate-100">
              <Image
                src={anuncio.capa?.urlPublica || '/icone-sem-foto.png'}
                alt={`Capa de ${anuncio.titulo}`}
                fill
                className="object-cover"
                sizes="(max-width: 768px) 100vw, 380px"
              />
            </div>
            <div className="flex flex-col p-6 md:p-8">
              <span className={cn('w-fit rounded-md border px-3 py-1 text-xs font-semibold', status.className)}>
                {status.label}
              </span>
              <h2 className="mt-4 text-2xl font-extrabold tracking-tight text-slate-900">{anuncio.titulo}</h2>
              <p className="mt-3 text-sm leading-6 text-slate-600">{anuncioLocalizacao(anuncio)}</p>
              <p className="mt-2 text-sm text-slate-500">{anuncioModeracao(anuncio.statusModeracao)}</p>
              {anuncio.capa?.restrita ? (
                <p className="mt-4 rounded-xl bg-slate-100 px-4 py-3 text-sm text-slate-700">
                  A capa deste anúncio está protegida e não possui URL pública disponível.
                </p>
              ) : null}

              <div className="mt-auto flex flex-col gap-3 pt-8 sm:flex-row">
                <Link
                  href="/meus-anuncios"
                  className="inline-flex items-center justify-center rounded-lg border border-slate-200 px-5 py-2.5 text-sm font-semibold text-slate-700 transition hover:-translate-y-0.5 hover:shadow-sm"
                >
                  Voltar
                </Link>
                <Link
                  href={`/meus-anuncios/${encodeURIComponent(anuncio.slug)}/editar`}
                  className="inline-flex items-center justify-center rounded-lg bg-[#FC1EAD] px-5 py-2.5 text-sm font-semibold text-white transition hover:-translate-y-0.5 hover:bg-[#e01a9a] hover:shadow-sm"
                >
                  Editar anúncio
                </Link>
              </div>
            </div>
          </div>
        </article>
      )}
    </PainelShell>
  )
}
