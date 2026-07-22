'use client'

import { useCallback, useEffect, useState } from 'react'
import { Loader2, Sparkles } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'

import { activateAdminStory, deactivateAdminStory, getAdminStorySelection } from './api'
import type { AdminStorySelection } from './types'

function formatDate(value?: string | null) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

export function AdminAnuncioStory({ anuncioId, canManage }: { anuncioId: string; canManage: boolean }) {
  const [selection, setSelection] = useState<AdminStorySelection | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)

  const load = useCallback(async () => {
    if (!canManage) { setLoading(false); return }
    setLoading(true)
    setError(null)
    try { setSelection(await getAdminStorySelection()) } catch (reason) { setError(reason) } finally { setLoading(false) }
  }, [canManage])

  useEffect(() => { void load() }, [load])

  async function activate() {
    if (busy) return
    setBusy(true)
    setError(null)
    try { setSelection(await activateAdminStory(anuncioId)) } catch (reason) { setError(reason) } finally { setBusy(false) }
  }

  async function deactivate() {
    if (busy || !selection?.ativa || selection.anuncioId !== anuncioId) return
    setBusy(true)
    setError(null)
    try { setSelection(await deactivateAdminStory()) } catch (reason) { setError(reason) } finally { setBusy(false) }
  }

  if (!canManage) return null
  const selectedHere = selection?.ativa && selection.anuncioId === anuncioId

  return (
    <section className="rounded-md border border-zinc-200 bg-white p-4">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2"><Sparkles className="h-4 w-4 text-pink-700" /><h3 className="text-sm font-semibold text-zinc-950">Story administrativo</h3>{selectedHere ? <Badge variant="outline">ATIVO</Badge> : null}</div>
          {selectedHere ? <p className="mt-2 text-xs text-zinc-600">Restrita 18+ · início {formatDate(selection?.ativadoEm)} · expira {formatDate(selection?.expiraEm)}</p> : <p className="mt-2 text-xs text-zinc-600">Usa as mídias já vinculadas e expira automaticamente em 24 horas.</p>}
          {selection?.ativa && !selectedHere ? <p className="mt-2 text-xs font-medium text-amber-700">Outro anúncio está selecionado; a ativação substituirá a seleção atual.</p> : null}
        </div>
        <div className="shrink-0">
          {loading ? <Loader2 className="h-5 w-5 animate-spin text-zinc-500" /> : selectedHere ? <Button type="button" variant="outline" disabled={busy} onClick={() => void deactivate()}>Remover dos Stories</Button> : <Button type="button" disabled={busy} onClick={() => void activate()}>Colocar nos Stories</Button>}
        </div>
      </div>
      {error ? <div className="mt-3"><ContractState error={error} onRetry={() => void load()} compact /></div> : null}
    </section>
  )
}
