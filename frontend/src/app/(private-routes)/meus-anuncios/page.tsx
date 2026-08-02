'use client'

import Link from 'next/link'
import { PlusIcon } from '@heroicons/react/24/solid'
import { useCallback, useEffect, useState } from 'react'
import { MeuAnuncioCard } from '@/components/anuncios/meu-anuncio-card'
import type { CicloVidaAcao } from '@/components/anuncios/meu-anuncio-acoes-ciclo-vida'
import { PainelShell } from '@/components/painel-anunciante/painel-shell'
import {
  listarMeusAnuncios,
  MeusAnunciosApiError,
  type MeuAnuncio,
  type MeuAnuncioCicloVida,
  type MeuAnuncioStory,
} from '@/lib/meus-anuncios-api'

type ListaErro = {
  titulo: string
  mensagem: string
}

export default function MeusAnunciosPage() {
  const [anuncios, setAnuncios] = useState<MeuAnuncio[]>([])
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<ListaErro | null>(null)

  const carregar = useCallback(async () => {
    setLoading(true)
    setErro(null)
    try {
      setAnuncios(await listarMeusAnuncios())
    } catch (error) {
      if (error instanceof MeusAnunciosApiError && error.status === 401) {
        setErro({
          titulo: 'Sessão necessária',
          mensagem: 'Entre novamente para consultar seus anúncios.',
        })
      } else if (error instanceof MeusAnunciosApiError && error.status === 403) {
        setErro({
          titulo: 'Acesso negado',
          mensagem: 'Sua conta não possui acesso a esta listagem.',
        })
      } else if (error instanceof MeusAnunciosApiError && error.status === 404) {
        setErro({
          titulo: 'Integração indisponível',
          mensagem: 'A consulta de anúncios não está disponível neste ambiente.',
        })
      } else {
        setErro({
          titulo: 'Não foi possível carregar seus anúncios.',
          mensagem: 'Não foi possível concluir a consulta. Tente novamente em instantes.',
        })
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void carregar()
  }, [carregar])

  const aplicarCicloVida = useCallback((
    resultado: MeuAnuncioCicloVida,
    acao: CicloVidaAcao
  ) => {
    setAnuncios((atuais) => {
      if (acao === 'REMOVER' || resultado.status === 'REMOVIDO') {
        return atuais.filter((anuncio) => anuncio.id !== resultado.id)
      }
      return atuais.map((anuncio) => anuncio.id === resultado.id
        ? {
            ...anuncio,
            status: resultado.status,
            statusModeracao: resultado.statusModeracao,
            atualizadoEm: resultado.atualizadoEm,
            acoesPermitidas: resultado.acoesPermitidas,
          }
        : anuncio)
    })
  }, [])

  const aplicarStory = useCallback((anuncioId: string, story: MeuAnuncioStory) => {
    setAnuncios((atuais) => atuais.map((anuncio) => anuncio.id === anuncioId
      ? { ...anuncio, storyAtivo: story }
      : anuncio))
  }, [])

  return (
    <PainelShell
      title="Meus anúncios"
      description="Consulte o status, a localização e a capa pública dos seus anúncios."
    >
      <div className="space-y-6">
        <div className="flex justify-end">
          <Link
            href="/anunciar/wizard"
            className="inline-flex min-h-11 items-center justify-center rounded-lg bg-[#FC1EAD] px-5 py-2.5 text-sm font-semibold text-white transition hover:-translate-y-0.5 hover:bg-[#e01a9a] hover:shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#FC1EAD] focus-visible:ring-offset-2"
          >
            <PlusIcon className="mr-2 h-5 w-5" aria-hidden="true" />
            Publicar novo anúncio
          </Link>
        </div>

        {!loading && !erro && anuncios.length > 0 ? (
          <section className="rounded-[28px] border border-slate-200 bg-white px-5 py-4 shadow-sm">
            <p className="text-sm font-medium text-slate-600">
              {anuncios.length} {anuncios.length === 1 ? 'anúncio encontrado' : 'anúncios encontrados'}
            </p>
          </section>
        ) : null}

        {loading ? (
          <div className="flex min-h-[240px] items-center justify-center rounded-[28px] border border-slate-200 bg-white text-sm text-slate-500 shadow-sm">
            Carregando seus anúncios...
          </div>
        ) : erro ? (
          <div className="flex min-h-[240px] flex-col items-center justify-center rounded-[28px] border border-rose-200 bg-rose-50 px-6 text-center shadow-sm">
            <p className="font-semibold text-rose-900">{erro.titulo}</p>
            <p className="mt-2 max-w-lg text-sm leading-6 text-rose-800">{erro.mensagem}</p>
            <button
              type="button"
              onClick={() => void carregar()}
              className="mt-5 rounded-lg bg-[#FC1EAD] px-5 py-2.5 text-sm font-semibold text-white transition hover:-translate-y-0.5 hover:bg-[#e01a9a] hover:shadow-sm"
            >
              Tentar novamente
            </button>
          </div>
        ) : anuncios.length === 0 ? (
          <div className="flex min-h-[260px] flex-col items-center justify-center rounded-[28px] border border-dashed border-slate-300 bg-white px-6 text-center shadow-sm">
            <p className="text-lg font-semibold text-slate-800">Você ainda não possui anúncios.</p>
            <p className="mt-2 max-w-md text-sm leading-6 text-slate-500">
              Quando houver anúncios vinculados à sua conta, eles aparecerão aqui.
            </p>
          </div>
        ) : (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
            {anuncios.map((anuncio) => (
              <MeuAnuncioCard
                key={anuncio.id}
                anuncio={anuncio}
                onCicloVida={aplicarCicloVida}
                onStoryChange={aplicarStory}
              />
            ))}
          </div>
        )}
      </div>
    </PainelShell>
  )
}
