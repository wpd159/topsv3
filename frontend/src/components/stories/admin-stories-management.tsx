'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { Search, ShieldAlert, Trash2 } from 'lucide-react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import {
  AdminStoryManagementError,
  fetchAdminStories,
  removeAdminStory,
  type AdminStoriesPagina,
  type AdminStoryGerenciado,
  type AdminStoryRemocaoMotivo,
} from '@/lib/admin-story-management-api'
import { listAdminUsers } from '@/features/admin-usuarios/api'
import type { AdminUserSummary } from '@/features/admin-usuarios/types'

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
  if (story.statusAdministrativo === 'ATIVO') return 'Ativo'
  if (story.statusAdministrativo === 'FALHA_TECNICA') return 'Falha técnica'
  return story.statusAdministrativo.replaceAll('_', ' ')
}
const USER_LOOKUP_FILTERS = {
  status: 'TODOS',
  kyc: 'TODOS',
  grupo: 'TODOS',
  ordenacao: 'RECENTES',
  page: 0,
  size: 10,
} as const

function userLabel(user: AdminUserSummary) {
  return user.nome?.trim() || user.email?.trim() || user.id
}

function userDescription(user: AdminUserSummary) {
  return [user.email, user.id].filter(Boolean).join(' | ')
}


export function AdminStoriesManagement() {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const filters = useMemo(() => ({
    usuarioId: searchParams.get('usuarioId')?.trim() || '',
    busca: searchParams.get('busca')?.trim() || '',
    page: Math.max(Number(searchParams.get('pagina') || '0') || 0, 0),
    size: 20,
  }), [searchParams])
  const [data, setData] = useState<AdminStoriesPagina | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<AdminStoryGerenciado | null>(null)
  const [reason, setReason] = useState<AdminStoryRemocaoMotivo>('VIOLACAO_REGRAS')
  const [description, setDescription] = useState('')
  const [removing, setRemoving] = useState(false)
  const [reload, setReload] = useState(0)
  const [searchDraft, setSearchDraft] = useState(filters.busca)
  const [selectedUserId, setSelectedUserId] = useState(filters.usuarioId)
  const [selectedUser, setSelectedUser] = useState<AdminUserSummary | null>(null)
  const [userDraft, setUserDraft] = useState('')
  const [userSuggestions, setUserSuggestions] = useState<AdminUserSummary[]>([])
  const [userLoading, setUserLoading] = useState(false)
  const [userError, setUserError] = useState<string | null>(null)

  const updateQuery = useCallback((updates: Record<string, string | number | null>) => {
    const next = new URLSearchParams(searchParams.toString())
    Object.entries(updates).forEach(([key, value]) => {
      if (value === null || value === '') next.delete(key)
      else next.set(key, String(value))
    })
    router.push(next.size ? `${pathname}?${next.toString()}` : pathname)
  }, [pathname, router, searchParams])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await fetchAdminStories(filters))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível carregar a gestão de Stories.')
    } finally {
      setLoading(false)
    }
  }, [filters])

  useEffect(() => {
    void load()
  }, [load, reload])

  useEffect(() => {
    setSearchDraft(filters.busca)
    setSelectedUserId(filters.usuarioId)
    if (!filters.usuarioId) {
      setSelectedUser(null)
      setUserDraft('')
    }
  }, [filters.busca, filters.usuarioId])

  useEffect(() => {
    let active = true
    if (!filters.usuarioId) return
    listAdminUsers({ ...USER_LOOKUP_FILTERS, termo: filters.usuarioId })
      .then((result) => {
        if (!active) return
        const user = result.itens.find((item) => item.id === filters.usuarioId) ?? result.itens[0] ?? null
        setSelectedUser(user)
        setUserDraft(user ? userLabel(user) : filters.usuarioId)
      })
      .catch(() => {
        if (active) setUserDraft(filters.usuarioId)
      })
    return () => {
      active = false
    }
  }, [filters.usuarioId])

  useEffect(() => {
    const term = userDraft.trim()
    if (selectedUser && term === userLabel(selectedUser)) {
      setUserSuggestions([])
      setUserError(null)
      return
    }
    if (term.length < 2) {
      setUserSuggestions([])
      setUserError(null)
      return
    }
    let active = true
    const timer = globalThis.setTimeout(() => {
      setUserLoading(true)
      setUserError(null)
      listAdminUsers({ ...USER_LOOKUP_FILTERS, termo: term })
        .then((result) => {
          if (active) setUserSuggestions(result.itens)
        })
        .catch(() => {
          if (active) {
            setUserSuggestions([])
            setUserError('Não foi possível localizar usuários.')
          }
        })
        .finally(() => {
          if (active) setUserLoading(false)
        })
    }, 250)
    return () => {
      active = false
      globalThis.clearTimeout(timer)
    }
  }, [selectedUser, userDraft])
  function applyFilters(event: React.FormEvent) {
    event.preventDefault()
    updateQuery({
      usuarioId: selectedUserId || null,
      busca: searchDraft.trim() || null,
      pagina: 0,
    })
  }

  function clearFilters() {
    setSearchDraft('')
    setSelectedUserId('')
    setSelectedUser(null)
    setUserDraft('')
    setUserSuggestions([])
    router.push(pathname)
  }

  function selectUser(user: AdminUserSummary) {
    setSelectedUser(user)
    setSelectedUserId(user.id)
    setUserDraft(userLabel(user))
    setUserSuggestions([])
    setUserError(null)
  }

  function changeUserDraft(value: string) {
    setUserDraft(value)
    setSelectedUser(null)
    setSelectedUserId('')
  }


  function openRemoval(story: AdminStoryGerenciado) {
    if (!story.removivel) return
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
      const targetPage = data && data.itens.length === 1 && filters.page > 0
        ? filters.page - 1
        : filters.page
      if (targetPage !== filters.page) updateQuery({ pagina: targetPage })
      else setReload((value) => value + 1)
    } catch (cause) {
      if (cause instanceof AdminStoryManagementError && cause.status === 409) {
        try {
          const refreshed = await fetchAdminStories(filters)
          const currentStory = refreshed.itens.find((item) => item.id === selected.id)
          if (currentStory) {
            setData((current) => current ? {
              ...current,
              itens: current.itens.map((item) => item.id === currentStory.id ? currentStory : item),
            } : current)
            setSelected(null)
            toast.info('O Story não está mais ativo. O histórico foi atualizado.')
            return
          }
        } catch {
          // Mantem a resposta controlada original quando a reconciliacao nao puder ser concluida.
        }
      }
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
        </div>

        <form onSubmit={applyFilters} className="mt-4 grid min-w-0 gap-3 border-t border-gray-200 pt-4 xl:grid-cols-[minmax(220px,1fr)_minmax(260px,1.2fr)_auto] xl:items-end">
          <label className="min-w-0">
            <span className="mb-1 block text-xs font-semibold text-gray-700">Buscar</span>
            <span className="relative block">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" aria-hidden="true" />
              <Input value={searchDraft} onChange={(event) => setSearchDraft(event.target.value)} placeholder="Story, anúncio ou ID" className="w-full pl-9" />
            </span>
          </label>
          <div className="relative min-w-0">
            <label htmlFor="admin-story-user-filter" className="mb-1 block text-xs font-semibold text-gray-700">Usuário</label>
            <Input
              id="admin-story-user-filter"
              value={userDraft}
              onChange={(event) => changeUserDraft(event.target.value)}
              placeholder="Username, nome, e-mail ou ID"
              role="combobox"
              aria-autocomplete="list"
              aria-controls="admin-story-user-options"
              aria-expanded={userSuggestions.length > 0}
              className="w-full"
            />
            {userLoading ? <p className="mt-1 text-xs text-gray-500" role="status">Localizando usuários...</p> : null}
            {userError ? <p className="mt-1 text-xs text-red-700" role="alert">{userError}</p> : null}
            {userSuggestions.length > 0 ? (
              <div id="admin-story-user-options" role="listbox" className="absolute z-20 mt-1 max-h-64 w-full overflow-y-auto rounded-md border border-gray-200 bg-white p-1 shadow-lg">
                {userSuggestions.map((user) => (
                  <button
                    key={user.id}
                    type="button"
                    role="option"
                    aria-selected={selectedUserId === user.id}
                    className="block w-full rounded px-3 py-2 text-left hover:bg-pink-50 focus:bg-pink-50 focus:outline-none"
                    onMouseDown={(event) => event.preventDefault()}
                    onClick={() => selectUser(user)}
                  >
                    <span className="block truncate text-sm font-medium text-gray-900">{userLabel(user)}</span>
                    <span className="block truncate text-xs text-gray-500">{userDescription(user)}</span>
                  </button>
                ))}
              </div>
            ) : null}
          </div>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 xl:flex">
            <Button type="submit" disabled={loading}>Aplicar</Button>
            <Button type="button" variant="outline" disabled={loading} onClick={clearFilters}>Limpar</Button>
            <Button type="button" variant="outline" className="col-span-2 sm:col-span-1" disabled={loading} onClick={() => setReload((value) => value + 1)}>Atualizar</Button>
          </div>
        </form>

        {loading && !data ? <p className="mt-5 text-sm text-gray-500" role="status">Carregando Stories...</p> : null}
        {error && !selected ? <p className="mt-5 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800" role="alert">{error}</p> : null}
        {!loading && !error && (!data || data.itens.length === 0) ? <p className="mt-5 text-sm text-gray-500">Nenhum Story encontrado para os filtros informados.</p> : null}

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
                    <td className="px-2 py-3"><span className={`rounded-full px-2 py-1 text-xs font-semibold ${story.falhaTecnica ? 'bg-amber-100 text-amber-900' : story.ativo ? 'bg-emerald-100 text-emerald-800' : 'bg-gray-100 text-gray-700'}`}>{storyStatus(story)}</span></td>
                    <td className="px-2 py-3 text-gray-600">{formatDate(story.publicadoEm)}</td>
                    <td className="px-2 py-3 text-gray-600">{formatDate(story.expiraEm)}</td>
                    <td className="px-2 py-3 text-right">
                      {story.removivel ? (
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
            <Button type="button" variant="outline" size="sm" disabled={loading || filters.page === 0} onClick={() => updateQuery({ pagina: filters.page - 1 })}>Anterior</Button>
            <span className="text-sm text-gray-600">Página {filters.page + 1} de {data.totalPaginas}</span>
            <Button type="button" variant="outline" size="sm" disabled={loading || filters.page + 1 >= data.totalPaginas} onClick={() => updateQuery({ pagina: filters.page + 1 })}>Próxima</Button>
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
