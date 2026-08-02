'use client'

import Link from 'next/link'
import { MegaphoneIcon, PlusIcon } from '@heroicons/react/24/solid'
import { useCallback, useEffect, useRef, useState } from 'react'
import { MeuAnuncioCard } from '@/components/anuncios/meu-anuncio-card'
import type { CicloVidaAcao } from '@/components/anuncios/meu-anuncio-acoes-ciclo-vida'
import { PainelShell } from '@/components/painel-anunciante/painel-shell'
import { StoryAnuncioSelectorDialog } from '@/components/stories/story-anuncio-selector-dialog'
import { StoryCreateDialog } from '@/components/stories/story-create-dialog'
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
  const [storySelectorOpen, setStorySelectorOpen] = useState(false)
  const [storyDialogOpen, setStoryDialogOpen] = useState(false)
  const [storyTargetId, setStoryTargetId] = useState<string | null>(null)
  const storyReturnFocusRef = useRef<HTMLElement | null>(null)

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

  const aplicarAnuncio = useCallback((resultado: MeuAnuncio) => {
    setAnuncios((atuais) => atuais.map((anuncio) => anuncio.id === resultado.id ? resultado : anuncio))
  }, [])

  const abrirStory = useCallback((anuncioId: string, trigger?: HTMLElement | null) => {
    if (trigger) storyReturnFocusRef.current = trigger
    setStoryTargetId(anuncioId)
    setStoryDialogOpen(true)
  }, [])

  const abrirStoryGlobal = useCallback((trigger: HTMLElement) => {
    if (loading || erro || anuncios.length === 0) return
    storyReturnFocusRef.current = trigger
    if (anuncios.length === 1) {
      abrirStory(anuncios[0].id, trigger)
      return
    }
    setStorySelectorOpen(true)
  }, [anuncios, abrirStory, erro, loading])

  const storyTarget = anuncios.find((anuncio) => anuncio.id === storyTargetId) ?? null
  const storyGlobalDisabled = loading || Boolean(erro) || anuncios.length === 0
  const storyGlobalReason = loading
    ? 'Aguarde enquanto seus anúncios são carregados.'
    : erro
      ? 'Recarregue seus anúncios antes de usar os Stories.'
      : 'Publique um anúncio antes de usar os Stories.'

  return (
    <PainelShell
      title="Meus anúncios"
      description="Consulte o status, a localização e a capa pública dos seus anúncios."
    >
      <div className="space-y-6">
        <div className="flex flex-col justify-end gap-2 sm:flex-row sm:items-center">
          <button
            type="button"
            onClick={(event) => abrirStoryGlobal(event.currentTarget)}
            disabled={storyGlobalDisabled}
            aria-describedby={storyGlobalDisabled ? 'story-global-indisponivel' : undefined}
            title={storyGlobalDisabled ? storyGlobalReason : undefined}
            className="inline-flex min-h-11 w-full items-center justify-center rounded-lg border border-[#FC1EAD]/50 bg-white px-5 py-2.5 text-sm font-semibold text-[#b5127a] transition hover:-translate-y-0.5 hover:border-[#FC1EAD] hover:bg-pink-50 hover:shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#FC1EAD] focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:border-slate-200 disabled:bg-slate-100 disabled:text-slate-500 disabled:hover:translate-y-0 disabled:hover:shadow-none sm:w-auto"
          >
            <MegaphoneIcon className="mr-2 h-5 w-5" aria-hidden="true" />
            Publicar nos Stories
          </button>
          {storyGlobalDisabled ? (
            <span id="story-global-indisponivel" className="sr-only">{storyGlobalReason}</span>
          ) : null}
          <Link
            href="/anunciar/wizard"
            className="inline-flex min-h-11 w-full items-center justify-center rounded-lg bg-[#FC1EAD] px-5 py-2.5 text-sm font-semibold text-white transition hover:-translate-y-0.5 hover:bg-[#e01a9a] hover:shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#FC1EAD] focus-visible:ring-offset-2 sm:w-auto"
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
                onStoryOpen={abrirStory}
              />
            ))}
          </div>
        )}

        <StoryAnuncioSelectorDialog
          open={storySelectorOpen}
          onOpenChange={setStorySelectorOpen}
          anuncios={anuncios}
          returnFocusTo={storyReturnFocusRef.current}
          onSelect={(anuncio) => {
            setStorySelectorOpen(false)
            setStoryTargetId(anuncio.id)
            window.setTimeout(() => setStoryDialogOpen(true), 0)
          }}
        />
        {storyTarget ? (
          <StoryCreateDialog
            open={storyDialogOpen}
            onOpenChange={setStoryDialogOpen}
            anuncio={storyTarget}
            returnFocusTo={storyReturnFocusRef.current}
            onAnuncioChange={aplicarAnuncio}
            onSuccess={(story) => aplicarStory(storyTarget.id, story)}
          />
        ) : null}
      </div>
    </PainelShell>
  )
}
