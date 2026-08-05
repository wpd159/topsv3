'use client'

import { useCallback, useEffect, useState } from 'react'
import { ShieldAlert, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import {
  fetchAdminStories,
  removeAdminStory,
  type AdminStoriesPagina,
  type AdminStoryGerenciado,
  type AdminStoryRemocaoMotivo,
} from '@/lib/admin-story-management-api'

const MOTIVOS: Array<{ value: AdminStoryRemocaoMotivo; label: string }> = [
  { value: 'VIOLACAO_REGRAS', label: 'Violação das regras' },
  { value: 'DENUNCIA_PROCEDENTE', label: 'Denúncia procedente' },
  { value: 'DETERMINACAO_JURIDICA', label: 'Determinação jurídica' },
  { value: 'ERRO_TECNICO', label: 'Erro técnico comprovado' },
  { value: 'OUTRO', label: 'Outro' },
]

function formatDate(value: string | null) {
  if (!value) return '-'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? '-' : parsed.toLocaleString('pt-BR')
}

function storyStatus(story: AdminStoryGerenciado) {
  if (story.falhaTecnica) return 'Falha técnica'
  if (story.encerradoEm) return 'Encerrado'
  return story.status === 'PUBLICADO' ? 'Ativo' : story.status.replaceAll('_', ' ')
}

export function AdminStoriesManagement() {
  const [data, setData] = useState<AdminStoriesPagina | null>(null)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<AdminStoryGerenciado | null>(null)
  const [reason, setReason] = useState<AdminStoryRemocaoMotivo>('VIOLACAO_REGRAS')
  const [description, setDescription] = useState('')
  const [removing, setRemoving] = useState(false)

  const load = useCallback(async (targetPage = page) => {
    setLoading(true)
    setError(null)
    try {
      const next = await fetchAdminStories(targetPage, 20)
      setData(next)
      setPage(next.pagina)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível carregar a gestão de Stories.')
    } finally {
      setLoading(false)
    }
  }, [page])

  useEffect(() => {
    void load(0)
    // A carga inicial usa a primeira página de forma intencional.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function openRemoval(story: AdminStoryGerenciado) {
    setSelected(story)
    setReason(story.falhaTecnica ? 'ERRO_TECNICO' : 'VIOLACAO_REGRAS')
    setDescription('')
  }

  async function confirmRemoval() {
    if (!selected || removing) return
    if (reason === 'OUTRO' && !description.trim()) {
      setError('Informe a descrição quando o motivo for Outro.')
      return
    }
    setRemoving(true)
    setError(null)
    try {
      const result = await removeAdminStory(selected.id, reason, description.trim() || null)
      toast.success(result.repetido ? 'O Story já estava removido.' : 'Story removido do feed.')
      setSelected(null)
      const targetPage = data && data.itens.length === 1 && page > 0 ? page - 1 : page
      await load(targetPage)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível remover o Story.')
    } finally {
      setRemoving(false)
    }
  }

  return (
    <>
      <section className="rounded-lg border border-gray-200 bg-white p-5 shadow-sm" aria-labelledby="admin-stories-management-title">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <div className="flex items-center gap-2">
              <ShieldAlert className="h-5 w-5 text-[#C41E73]" aria-hidden="true" />
              <h2 id="admin-stories-management-title" className="text-base font-semibold text-gray-900">Gestão dos Stories publicados</h2>
            </div>
            <p className="mt-1 text-sm text-gray-600">Consulte publicações da conta e remova somente com motivo administrativo.</p>
          </div>
          <Button type="button" variant="outline" size="sm" disabled={loading} onClick={() => void load(page)}>Atualizar</Button>
        </div>

        {loading && !data ? <p className="mt-5 text-sm text-gray-500" role="status">Carregando Stories...</p> : null}
        {error && !selected ? <p className="mt-5 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800" role="alert">{error}</p> : null}
        {!loading && !error && (!data || data.itens.length === 0) ? <p className="mt-5 text-sm text-gray-500">Nenhum Story recente encontrado.</p> : null}

        {data && data.itens.length > 0 ? (
          <div className="mt-5 overflow-x-auto">
            <table className="min-w-[760px] w-full text-left text-sm">
              <thead className="border-b border-gray-200 text-xs uppercase text-gray-500">
                <tr>
                  <th className="px-2 py-3">Story</th>
                  <th className="px-2 py-3">Anunciante</th>
                  <th className="px-2 py-3">Status</th>
                  <th className="px-2 py-3">Publicação</th>
                  <th className="px-2 py-3">Expiração</th>
                  <th className="px-2 py-3 text-right">Ação</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {data.itens.map((story) => (
                  <tr key={story.id}>
                    <td className="px-2 py-3">
                      <p className="font-medium text-gray-900">{story.modoConteudo === 'ANUNCIO' ? 'Anúncio' : 'Mídia enviada'}</p>
                      <p className="max-w-52 truncate text-xs text-gray-500">{story.anuncioTitulo ?? 'Story independente'}</p>
                    </td>
                    <td className="px-2 py-3 text-gray-700">{story.usuarioUsername ? `@${story.usuarioUsername}` : 'Identidade pública indisponível'}</td>
                    <td className="px-2 py-3"><span className={`rounded-full px-2 py-1 text-xs font-semibold ${story.falhaTecnica ? 'bg-amber-100 text-amber-900' : story.encerradoEm ? 'bg-gray-100 text-gray-700' : 'bg-emerald-100 text-emerald-800'}`}>{storyStatus(story)}</span></td>
                    <td className="px-2 py-3 text-gray-600">{formatDate(story.publicadoEm)}</td>
                    <td className="px-2 py-3 text-gray-600">{formatDate(story.expiraEm)}</td>
                    <td className="px-2 py-3 text-right">
                      {!story.encerradoEm ? (
                        <Button type="button" size="sm" variant="outline" className="border-red-200 text-red-700 hover:bg-red-50" disabled={removing} onClick={() => openRemoval(story)}>
                          <Trash2 className="mr-2 h-4 w-4" aria-hidden="true" />Remover Story
                        </Button>
                      ) : <span className="text-xs text-gray-500">Histórico preservado</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {data && data.totalPaginas > 1 ? (
          <nav className="mt-5 flex items-center justify-between gap-3" aria-label="Paginação administrativa de Stories">
            <Button type="button" variant="outline" size="sm" disabled={loading || page === 0} onClick={() => void load(page - 1)}>Anterior</Button>
            <span className="text-sm text-gray-600">Página {page + 1} de {data.totalPaginas}</span>
            <Button type="button" variant="outline" size="sm" disabled={loading || page + 1 >= data.totalPaginas} onClick={() => void load(page + 1)}>Próxima</Button>
          </nav>
        ) : null}
      </section>

      <Dialog open={Boolean(selected)} onOpenChange={(next) => { if (!next && !removing) setSelected(null) }}>
        <DialogContent className="w-[calc(100vw-1rem)] sm:max-w-md">
          <DialogHeader className="pr-8 text-left">
            <DialogTitle>Remover Story</DialogTitle>
            <DialogDescription>O Story será retirado imediatamente do feed. O histórico administrativo será preservado.</DialogDescription>
          </DialogHeader>
          <div className="mt-4 space-y-4">
            <label className="block text-sm font-medium text-gray-800">
              Motivo obrigatório
              <select value={reason} onChange={(event) => setReason(event.target.value as AdminStoryRemocaoMotivo)} className="mt-1 min-h-11 w-full rounded-md border border-gray-300 bg-white px-3 text-sm focus:outline-none focus:ring-2 focus:ring-[#C41E73]">
                {MOTIVOS.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
              </select>
            </label>
            <label className="block text-sm font-medium text-gray-800">
              Descrição {reason === 'OUTRO' ? '(obrigatória)' : '(opcional)'}
              <Textarea value={description} onChange={(event) => setDescription(event.target.value)} maxLength={500} rows={4} className="mt-1" />
            </label>
            {error ? <p className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800" role="alert">{error}</p> : null}
            <div className="grid gap-2 sm:grid-cols-2">
              <Button type="button" variant="outline" disabled={removing} onClick={() => setSelected(null)}>Cancelar</Button>
              <Button type="button" disabled={removing || (reason === 'OUTRO' && !description.trim())} className="bg-red-700 text-white hover:bg-red-800" onClick={() => void confirmRemoval()}>{removing ? 'Removendo...' : 'Remover Story'}</Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}
