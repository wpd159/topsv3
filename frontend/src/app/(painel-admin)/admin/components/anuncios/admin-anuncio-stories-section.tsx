'use client'

import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { TrashIcon, SparklesIcon } from '@heroicons/react/24/solid'

type AdminStoryItem = {
  storyId: number
  anuncioId: number | null
  usuarioUsername?: string | null
  displayUsername?: string | null
  profileNavigable?: boolean | null
  tipo: 'IMAGE' | 'VIDEO'
  status: 'ATIVO' | 'EXPIRADO' | 'REMOVIDO' | string
  midiaUrl?: string | null
  criadoEm?: string | null
  expiraEm?: string | null
  removidoEm?: string | null
}

function formatDate(value?: string | null) {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return d.toLocaleString('pt-BR')
}

function statusClasses(status: string) {
  switch (status) {
    case 'ATIVO':
      return 'border-green-200 bg-green-50 text-green-700'
    case 'EXPIRADO':
      return 'border-amber-200 bg-amber-50 text-amber-700'
    case 'REMOVIDO':
      return 'border-red-200 bg-red-50 text-red-700'
    default:
      return 'border-slate-200 bg-slate-50 text-slate-700'
  }
}

export default function AdminAnuncioStoriesSection({
  anuncioId,
  apiScope = 'admin',
}: {
  anuncioId: number
  apiScope?: 'admin' | 'staff'
}) {
  const API = process.env.NEXT_PUBLIC_API_URL
  const listUrl = `${API}/${apiScope === 'staff' ? 'anuncios/staff' : 'admin/anuncios'}/${anuncioId}/stories`
  const deleteUrl = (storyId: number) =>
    `${API}/${apiScope === 'staff' ? 'anuncios/staff/stories' : 'admin/stories'}/${storyId}`
  const [items, setItems] = useState<AdminStoryItem[]>([])
  const [loading, setLoading] = useState(true)
  const [deletingId, setDeletingId] = useState<number | null>(null)

  async function load() {
    if (!API) return
    try {
      setLoading(true)
      const res = await fetch(listUrl, {
        credentials: 'include',
        cache: 'no-store',
      })
      if (!res.ok) throw new Error('Falha ao carregar stories do anúncio.')
      const data = (await res.json()) as AdminStoryItem[]
      setItems(Array.isArray(data) ? data : [])
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Erro ao carregar stories do anúncio.')
      setItems([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [anuncioId, API, apiScope])

  async function remover(storyId: number) {
    if (!API) return
    if (!window.confirm('Remover este story do anúncio? O story ficará desativado, sem apagar a mídia.')) {
      return
    }

    try {
      setDeletingId(storyId)
      const res = await fetch(deleteUrl(storyId), {
        method: 'DELETE',
        credentials: 'include',
      })
      if (!res.ok) throw new Error('Falha ao remover story.')
      toast.success('Story removido com sucesso.')
      await load()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Erro ao remover story.')
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <div className="mb-10 rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
      <div className="mb-4 flex items-center gap-2 text-lg font-semibold text-gray-800">
        <SparklesIcon className="h-5 w-5 text-[#C41E73]" />
        Stories vinculados
      </div>

      {loading ? (
        <p className="text-sm text-gray-500">Carregando stories...</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-gray-500">Nenhum story vinculado a este anúncio.</p>
      ) : (
        <div className="space-y-4">
          {items.map((item) => (
            <div
              key={item.storyId}
              className="flex flex-col gap-3 rounded-xl border border-gray-200 bg-gray-50 p-4 md:flex-row md:items-start"
            >
              <div className="flex h-36 w-full shrink-0 items-center justify-center overflow-hidden rounded-lg border border-gray-200 bg-black md:w-28">
                {item.status === 'REMOVIDO' || !item.midiaUrl ? (
                  <span className="px-3 text-center text-xs font-semibold uppercase tracking-wide text-white/70">
                    Story indisponível
                  </span>
                ) : item.tipo === 'VIDEO' ? (
                  <video
                    src={item.midiaUrl}
                    muted
                    playsInline
                    preload="metadata"
                    className="h-full w-full object-cover"
                  />
                ) : (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={item.midiaUrl} alt="" className="h-full w-full object-cover" />
                )}
              </div>

              <div className="min-w-0 flex-1 space-y-2">
                <div className="flex flex-wrap items-center gap-2">
                  <span className={`rounded-full border px-2 py-1 text-[11px] font-semibold ${statusClasses(item.status)}`}>
                    {item.status}
                  </span>
                  <span className="rounded-full border border-slate-200 bg-white px-2 py-1 text-[11px] font-medium text-slate-700">
                    {item.tipo}
                  </span>
                </div>

                <div className="space-y-1 text-sm text-gray-700">
                  <p>
                    <span className="font-semibold">Story:</span> #{item.storyId}
                  </p>
                  <p>
                    <span className="font-semibold">Perfil:</span>{' '}
                    {item.profileNavigable && item.usuarioUsername
                      ? `@${item.usuarioUsername}`
                      : item.displayUsername || 'Perfil'}
                  </p>
                  <p>
                    <span className="font-semibold">Criado em:</span> {formatDate(item.criadoEm)}
                  </p>
                  <p>
                    <span className="font-semibold">Expira em:</span> {formatDate(item.expiraEm)}
                  </p>
                  {item.removidoEm ? (
                    <p>
                      <span className="font-semibold">Removido em:</span> {formatDate(item.removidoEm)}
                    </p>
                  ) : null}
                </div>
              </div>

              <div className="flex shrink-0 items-start">
                <Button
                  type="button"
                  variant="outline"
                  className="text-red-700 hover:bg-red-50"
                  disabled={item.status === 'REMOVIDO' || deletingId === item.storyId}
                  onClick={() => void remover(item.storyId)}
                >
                  <TrashIcon className="mr-1 h-4 w-4" />
                  {deletingId === item.storyId ? 'Removendo...' : 'Remover'}
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
