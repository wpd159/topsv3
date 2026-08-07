'use client'

import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'
import { AlertTriangle, ChevronDown, ChevronUp, Megaphone, RefreshCw, Trash2, Upload } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  encerrarMeuStory,
  listarMeusStories,
  type MeuStoryGerenciado,
  type MeusStoriesPagina,
} from '@/lib/minha-conta-stories-api'
import { MeusAnunciosApiError } from '@/lib/meus-anuncios-api'

type Props = {
  refreshVersion: number
  onChanged?: () => void
}

function formatDate(value: string | null) {
  if (!value) return 'Não informado'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime())
    ? 'Não informado'
    : parsed.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
}

function statusLabel(story: MeuStoryGerenciado) {
  switch (story.estadoGerenciamento) {
    case 'ENCERRADO_USUARIO':
      return 'Excluído por você'
    case 'REMOVIDO_ADMIN':
      return 'Removido'
    case 'DESCARTADO_FALHA_TECNICA':
      return 'Encerrado após falha técnica'
    case 'ENCERRADO':
      return 'Encerrado'
    case 'EXPIRADO':
      return 'Expirado'
    case 'FALHA_TECNICA':
      return 'Falha na publicação da mídia'
    case 'ATIVO':
      return 'Ativo'
    default:
      return story.status.replaceAll('_', ' ').toLocaleLowerCase('pt-BR')
  }
}

function mediaLabel(story: MeuStoryGerenciado) {
  switch (story.estadoMidia) {
    case 'REMOVIDA':
      return 'Removida'
    case 'FALHA_PUBLICACAO':
      return 'Falha na publicação'
    case 'INDISPONIVEL':
      return 'Indisponível'
    case 'DISPONIVEL':
      return 'Disponível'
    default:
      return 'Não aplicável'
  }
}

function encerramentoConfirmado(story: MeuStoryGerenciado) {
  return [
    'ENCERRADO_USUARIO',
    'REMOVIDO_ADMIN',
    'DESCARTADO_FALHA_TECNICA',
    'ENCERRADO',
    'EXPIRADO',
  ].includes(story.estadoGerenciamento)
}

function resumoCompacto(data: MeusStoriesPagina | null, loading: boolean, error: string | null) {
  if (loading && !data) return 'Carregando resumo...'
  if (error && !data) return 'Resumo temporariamente indisponível'
  if (!data || data.totalElementos === 0) return 'Nenhum Story recente'

  const ativos = data.itens.filter((story) => story.estadoGerenciamento === 'ATIVO').length
  const encerrados = data.itens.filter(encerramentoConfirmado).length
  const outros = Math.max(0, data.itens.length - ativos - encerrados)
  const partes = [
    ativos > 0 ? `${ativos} ${ativos === 1 ? 'ativo' : 'ativos'}` : null,
    encerrados > 0 ? `${encerrados} ${encerrados === 1 ? 'encerrado' : 'encerrados'}` : null,
    outros > 0 ? `${outros} ${outros === 1 ? 'outro' : 'outros'}` : null,
  ].filter((parte): parte is string => Boolean(parte))

  if (data.totalPaginas > 1) partes.push(`${data.totalElementos} no total`)
  return partes.join(' · ') || `${data.totalElementos} Stories recentes`
}

function errorMessage(error: unknown) {
  if (error instanceof MeusAnunciosApiError) {
    const suffix = error.requestId ? ` Código de atendimento: ${error.requestId}.` : ''
    return `${error.message}${suffix}`
  }
  return error instanceof Error ? error.message : 'Não foi possível concluir a operação.'
}

export function MeusStoriesPanel({ refreshVersion, onChanged }: Props) {
  const [expanded, setExpanded] = useState(false)
  const [page, setPage] = useState(0)
  const [data, setData] = useState<MeusStoriesPagina | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<MeuStoryGerenciado | null>(null)
  const [ending, setEnding] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const load = useCallback(async (targetPage = page) => {
    setLoading(true)
    setError(null)
    try {
      const next = await listarMeusStories(targetPage, 12)
      setData(next)
      setPage(next.pagina)
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setLoading(false)
    }
  }, [page])

  useEffect(() => {
    void load(page)
    // refreshVersion representa uma publicação concluída fora deste componente.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refreshVersion])

  async function confirmEnd() {
    if (!selected || ending) return
    const storyId = selected.id
    const descartando = selected.podeDescartar
    setEnding(true)
    setActionError(null)
    try {
      const result = await encerrarMeuStory(storyId)
      toast.success(descartando
        ? 'Story com falha descartado. O direito permanece disponível para uma nova tentativa.'
        : result.repetido ? 'O Story já estava encerrado.' : 'Story excluído.')
      setSelected(null)
      const targetPage = data && data.itens.length === 1 && page > 0 ? page - 1 : page
      await load(targetPage)
      onChanged?.()
    } catch (cause) {
      const message = errorMessage(cause)
      let reconciliado: MeuStoryGerenciado | null = null
      try {
        const next = await listarMeusStories(page, 12)
        setData(next)
        setPage(next.pagina)
        reconciliado = next.itens.find((item) => item.id === storyId) ?? null
      } catch {
        // A falha original e seu requestId permanecem como fonte do erro.
      }
      if (reconciliado && encerramentoConfirmado(reconciliado)) {
        toast.success(
          reconciliado.estadoGerenciamento === 'DESCARTADO_FALHA_TECNICA'
            ? 'Story encerrado após falha técnica.'
            : 'Story encerrado.',
        )
        setSelected(null)
        onChanged?.()
      } else {
        setActionError(message)
        toast.error(message)
      }
    } finally {
      setEnding(false)
    }
  }
  return (
    <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm" aria-labelledby="meus-stories-title">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <h2 id="meus-stories-title" className="text-lg font-bold text-slate-950">Meus Stories</h2>
          <p className="mt-1 break-words text-sm text-slate-600" aria-live="polite">
            {expanded
              ? 'Acompanhe Stories de anúncios e mídias publicadas diretamente pela sua conta.'
              : resumoCompacto(data, loading, error)}
          </p>
        </div>
        <div className="flex w-full flex-wrap gap-2 sm:w-auto sm:justify-end">
          {expanded ? (
            <Button type="button" variant="outline" size="sm" onClick={() => void load(page)} disabled={loading}>
              <RefreshCw className="mr-2 h-4 w-4" aria-hidden="true" />
              Atualizar
            </Button>
          ) : null}
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="min-w-0 flex-1 sm:flex-none"
            aria-expanded={expanded}
            aria-controls="meus-stories-conteudo"
            onClick={() => setExpanded((current) => !current)}
          >
            {expanded ? <ChevronUp className="mr-2 h-4 w-4" aria-hidden="true" /> : <ChevronDown className="mr-2 h-4 w-4" aria-hidden="true" />}
            {expanded ? 'Ocultar meus Stories' : 'Ver meus Stories'}
          </Button>
        </div>
      </div>

      <div id="meus-stories-conteudo" hidden={!expanded}>
        {expanded ? (loading && !data ? (
        <p className="mt-5 rounded-lg bg-slate-50 p-4 text-sm text-slate-600" role="status">Carregando seus Stories...</p>
      ) : error ? (
        <div className="mt-5 rounded-lg border border-rose-200 bg-rose-50 p-4" role="alert">
          <p className="text-sm text-rose-800">{error}</p>
          <Button type="button" variant="outline" size="sm" className="mt-3" onClick={() => void load(page)}>Tentar novamente</Button>
        </div>
      ) : !data || data.itens.length === 0 ? (
        <p className="mt-5 rounded-lg border border-dashed border-slate-300 p-6 text-center text-sm text-slate-500">Nenhum Story recente nesta conta.</p>
      ) : (
        <>
          <div className="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {data.itens.map((story) => (
              <article key={story.id} className="flex min-w-0 flex-col rounded-lg border border-slate-200 p-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="flex min-w-0 items-start gap-2">
                    {story.modoConteudo === 'ANUNCIO'
                      ? <Megaphone className="mt-0.5 h-5 w-5 shrink-0 text-[#FC1EAD]" aria-hidden="true" />
                      : <Upload className="mt-0.5 h-5 w-5 shrink-0 text-[#FC1EAD]" aria-hidden="true" />}
                    <div className="min-w-0">
                      <p className="text-xs font-semibold uppercase text-slate-500">{story.modoConteudo === 'ANUNCIO' ? 'Anúncio' : 'Mídia enviada'}</p>
                      <p className="mt-1 break-words font-semibold text-slate-950">{story.anuncioTitulo ?? 'Story independente'}</p>
                    </div>
                  </div>
                  <span className={`shrink-0 rounded-full px-2 py-1 text-xs font-semibold ${story.estadoGerenciamento === 'FALHA_TECNICA' ? 'bg-amber-100 text-amber-900' : encerramentoConfirmado(story) ? 'bg-slate-100 text-slate-700' : 'bg-emerald-100 text-emerald-800'}`}>
                    {statusLabel(story)}
                  </span>
                </div>

                <dl className="mt-4 grid gap-1.5 text-sm text-slate-600">
                  <div><dt className="inline font-medium text-slate-800">Início: </dt><dd className="inline">{formatDate(story.publicadoEm)}</dd></div>
                  <div><dt className="inline font-medium text-slate-800">Expira em: </dt><dd className="inline">{formatDate(story.expiraEm)}</dd></div>
                  <div><dt className="inline font-medium text-slate-800">Mídia: </dt><dd className="inline">{mediaLabel(story)}</dd></div>
                </dl>

                {story.falhaTecnica ? (
                  <p className="mt-3 flex items-start gap-2 rounded-lg bg-amber-50 p-3 text-xs leading-5 text-amber-900">
                    <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                    A publicação não foi concluída. O backend confirmou a falha técnica.
                  </p>
                ) : null}

                <div className="mt-auto grid gap-2 pt-4">
                  {story.modoConteudo === 'ANUNCIO' && story.anuncioSlug ? (
                    <Button asChild variant="outline" size="sm"><Link href={`/meus-anuncios/${encodeURIComponent(story.anuncioSlug)}`}>Gerenciar anúncio</Link></Button>
                  ) : null}
                  {story.podeExcluir || story.podeDescartar ? (
                    <Button type="button" variant="outline" size="sm" className="border-rose-200 text-rose-700 hover:bg-rose-50" onClick={() => { setActionError(null); setSelected(story) }}>
                      <Trash2 className="mr-2 h-4 w-4" aria-hidden="true" />
                      {story.podeDescartar ? 'Descartar Story com falha' : 'Excluir Story'}
                    </Button>
                  ) : null}
                </div>
              </article>
            ))}
          </div>

          {data.totalPaginas > 1 ? (
            <nav className="mt-5 flex items-center justify-between gap-3" aria-label="Paginação de Meus Stories">
              <Button type="button" variant="outline" size="sm" disabled={loading || page === 0} onClick={() => void load(page - 1)}>Anterior</Button>
              <span className="text-sm text-slate-600">Página {page + 1} de {data.totalPaginas}</span>
              <Button type="button" variant="outline" size="sm" disabled={loading || page + 1 >= data.totalPaginas} onClick={() => void load(page + 1)}>Próxima</Button>
            </nav>
          ) : null}
        </>
        )) : null}
      </div>

      <Dialog open={Boolean(selected)} onOpenChange={(next) => {
        if (!next && !ending) {
          setActionError(null)
          setSelected(null)
        }
      }}>
        <DialogContent className="w-[calc(100vw-1rem)] sm:max-w-md">
          <DialogHeader className="pr-8 text-left">
            <DialogTitle>{selected?.podeDescartar ? 'Descartar Story com falha?' : 'Excluir este Story?'}</DialogTitle>
            <DialogDescription>
              {selected?.podeDescartar
                ? 'A publicação não foi concluída. Este Story será encerrado e o direito permanecerá disponível para uma nova tentativa.'
                : 'Ele será retirado imediatamente do feed. O tempo restante e os créditos utilizados não serão devolvidos automaticamente.'}
            </DialogDescription>
          </DialogHeader>
          {actionError ? (
            <p className="mt-4 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800" role="alert">
              {actionError}
            </p>
          ) : null}
          {ending ? <p className="sr-only" role="status">Processando encerramento do Story.</p> : null}
          <div className="mt-4 grid gap-2 sm:grid-cols-2">
            <Button type="button" variant="outline" disabled={ending} onClick={() => setSelected(null)}>Cancelar</Button>
            <Button type="button" disabled={ending} className="bg-rose-700 text-white hover:bg-rose-800" onClick={() => void confirmEnd()}>
              {ending ? 'Processando...' : selected?.podeDescartar ? 'Descartar Story com falha' : 'Excluir Story'}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </section>
  )
}
