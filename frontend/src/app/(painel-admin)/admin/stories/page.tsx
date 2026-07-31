'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { FilmIcon, MagnifyingGlassIcon, PhotoIcon, SparklesIcon, XMarkIcon } from '@heroicons/react/24/solid'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  activateAdminStorySelection,
  deactivateAdminStorySelection,
  fetchAdminStoryCandidates,
  fetchAdminStorySelection,
  type AdminStoryCandidate,
  type AdminStorySelection,
} from '@/lib/admin-stories-api'

function formatDate(value: string | null) {
  if (!value) return '-'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? '-' : parsed.toLocaleString('pt-BR')
}

export default function AdminStoriesPage() {
  const [selections, setSelections] = useState<AdminStorySelection[]>([])
  const [candidates, setCandidates] = useState<AdminStoryCandidate[]>([])
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(true)
  const [pendingId, setPendingId] = useState<string | null>(null)
  const activationKeys = useRef(new Map<string, string>())

  const load = useCallback(async (term = '') => {
    try {
      setLoading(true)
      const [current, page] = await Promise.all([
        fetchAdminStorySelection(),
        fetchAdminStoryCandidates(term),
      ])
      setSelections(current)
      setCandidates(page.itens)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Não foi possível carregar os Stories.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  async function activate(candidate: AdminStoryCandidate) {
    const key = activationKeys.current.get(candidate.anuncioId) || crypto.randomUUID()
    activationKeys.current.set(candidate.anuncioId, key)
    try {
      setPendingId(candidate.anuncioId)
      const updated = await activateAdminStorySelection(candidate.anuncioId, key)
      activationKeys.current.delete(candidate.anuncioId)
      setSelections((current) => [
        ...current.filter((item) => item.anuncioId !== updated.anuncioId),
        updated,
      ])
      await load(search)
      toast.success('Anúncio exibido nos Stories.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Não foi possível exibir o anúncio nos Stories.')
    } finally {
      setPendingId(null)
    }
  }

  async function deactivate(selection: AdminStorySelection) {
    try {
      setPendingId(selection?.anuncioId || 'desativar')
      if (!selection.anuncioId) return
      await deactivateAdminStorySelection(selection.anuncioId)
      setSelections((current) => current.filter((item) => item.anuncioId !== selection.anuncioId))
      await load(search)
      toast.success('Anúncio removido dos Stories.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Não foi possível remover o anúncio dos Stories.')
    } finally {
      setPendingId(null)
    }
  }

  return (
    <section className="mx-auto w-full max-w-6xl space-y-6">
      <header>
        <h1 className="text-2xl font-bold text-gray-900">Stories administrativos</h1>
        <p className="mt-1 text-sm text-gray-600">
          Escolha um anúncio público para exibir suas fotos e vídeos aprovados antes dos Stories dos anunciantes.
        </p>
      </header>

      <div className="rounded-lg border border-gray-200 bg-white p-5 shadow-sm">
        <div className="flex items-center gap-2">
          <SparklesIcon className="h-5 w-5 text-[#C41E73]" />
          <h2 className="text-base font-semibold text-gray-900">Anúncios exibidos agora</h2>
        </div>

        {selections.length > 0 ? (
          <div className="mt-4 space-y-4">
          {selections.map((selection) => (
          <div key={selection.id} className="flex flex-col gap-4 border-t border-gray-100 pt-4 first:border-t-0 first:pt-0 md:flex-row md:items-end md:justify-between">
            <div className="min-w-0 space-y-2">
              <p className="break-words text-lg font-semibold text-gray-900">{selection.anuncioTitulo}</p>
              <p className="break-all text-sm text-gray-500">/anuncios/{selection.anuncioSlug}</p>
              <div className="flex flex-wrap gap-3 text-sm text-gray-700">
                <span className="inline-flex items-center gap-1"><PhotoIcon className="h-4 w-4" /> {selection.fotosAprovadas} fotos</span>
                <span className="inline-flex items-center gap-1"><FilmIcon className="h-4 w-4" /> {selection.videosAprovados} vídeos</span>
              </div>
              <p className="text-xs text-gray-500">
                Ativado em {formatDate(selection.ativadoEm)} por {selection.ativadoPorEmail || 'Administrador'}
              </p>
              <p className="text-xs text-gray-500">Expira em {formatDate(selection.expiraEm)}</p>
            </div>
            <Button
              type="button"
              variant="outline"
              className="w-full border-red-200 text-red-700 hover:bg-red-50 md:w-auto"
              disabled={pendingId !== null}
              onClick={() => void deactivate(selection)}
            >
              <XMarkIcon className="mr-1 h-4 w-4" />
              Remover dos Stories
            </Button>
          </div>
          ))}
          </div>
        ) : (
          <p className="mt-4 text-sm text-gray-500">Nenhum anúncio está sendo exibido nos Stories.</p>
        )}
      </div>

      <div className="rounded-lg border border-gray-200 bg-white p-5 shadow-sm">
        <form
          className="flex flex-col gap-3 sm:flex-row"
          onSubmit={(event) => {
            event.preventDefault()
            void load(search)
          }}
        >
          <div className="relative min-w-0 flex-1">
            <MagnifyingGlassIcon className="absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400" />
            <Input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Buscar anúncio por título ou endereço"
              className="w-full pl-10"
            />
          </div>
          <Button type="submit" variant="outline" disabled={loading}>Buscar</Button>
        </form>

        <div className="mt-5 space-y-3">
          {loading ? <p className="text-sm text-gray-500">Carregando anúncios...</p> : null}
          {!loading && candidates.length === 0 ? (
            <p className="text-sm text-gray-500">Nenhum anúncio público encontrado.</p>
          ) : null}
          {candidates.map((candidate) => {
            const mediaCount = candidate.fotosAprovadas + candidate.videosAprovados
            return (
              <article
                key={candidate.anuncioId}
                className="flex flex-col gap-4 rounded-lg border border-gray-200 p-4 md:flex-row md:items-center md:justify-between"
              >
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="break-words font-semibold text-gray-900">{candidate.titulo}</h3>
                    {candidate.selecionado ? (
                      <span className="rounded-full bg-pink-50 px-2 py-1 text-xs font-semibold text-[#C41E73]">Em exibição</span>
                    ) : null}
                  </div>
                  <p className="mt-1 break-all text-xs text-gray-500">/anuncios/{candidate.slug}</p>
                  <p className="mt-2 text-sm text-gray-600">
                    {candidate.fotosAprovadas} fotos e {candidate.videosAprovados} vídeos aprovados
                  </p>
                </div>
                <Button
                  type="button"
                  className="w-full bg-[#C41E73] text-white hover:bg-[#a91961] md:w-auto"
                  disabled={mediaCount === 0 || candidate.selecionado || pendingId !== null}
                  onClick={() => void activate(candidate)}
                >
                  {pendingId === candidate.anuncioId ? 'Atualizando...' : 'Exibir anúncio nos Stories'}
                </Button>
              </article>
            )
          })}
        </div>
      </div>
    </section>
  )
}
