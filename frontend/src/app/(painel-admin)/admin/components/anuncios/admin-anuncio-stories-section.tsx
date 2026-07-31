'use client'

import { useCallback, useEffect, useState } from 'react'
import { SparklesIcon } from '@heroicons/react/24/solid'
import { toast } from 'sonner'

import { ContractState } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import {
  deactivateAdminStorySelection,
  fetchAdminStorySelection,
  type AdminStorySelection,
} from '@/lib/admin-stories-api'

export default function AdminAnuncioStoriesSection({
  anuncioId,
}: {
  anuncioId: string | number
  apiScope?: 'admin' | 'staff'
}) {
  const [selections, setSelections] = useState<AdminStorySelection[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [removing, setRemoving] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setSelections(await fetchAdminStorySelection())
    } catch (loadError) {
      setError(loadError)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const selection = selections.find((item) => item.ativa && String(item.anuncioId) === String(anuncioId))
  const selectedHere = Boolean(selection)

  async function remove() {
    if (!selectedHere || removing) return
    if (!window.confirm('Desativar o Story administrativo deste anuncio?')) return
    setRemoving(true)
    try {
      await deactivateAdminStorySelection(String(anuncioId))
      setSelections((current) => current.filter((item) => String(item.anuncioId) !== String(anuncioId)))
      toast.success('Story administrativo desativado.')
    } catch (removeError) {
      setError(removeError)
    } finally {
      setRemoving(false)
    }
  }

  return (
    <div className="mb-10 rounded-xl border border-gray-100 bg-white p-6 shadow-sm">
      <div className="mb-4 flex items-center gap-2 text-lg font-semibold text-gray-800">
        <SparklesIcon className="h-5 w-5 text-[#C41E73]" />
        Story administrativo
      </div>
      {loading ? <p className="text-sm text-gray-500">Carregando Story...</p> : null}
      {error ? <ContractState error={error} onRetry={() => void load()} compact /> : null}
      {!loading && !error && !selectedHere ? (
        <p className="text-sm text-gray-500">Este anuncio nao e o Story administrativo ativo.</p>
      ) : null}
      {!loading && !error && selectedHere ? (
        <div className="flex items-center justify-between gap-4">
          <div>
            <p className="font-medium text-gray-900">{selection?.anuncioTitulo}</p>
            <p className="text-sm text-gray-500">Selecao administrativa ativa</p>
          </div>
          <Button type="button" variant="outline" disabled={removing} onClick={() => void remove()}>
            Desativar
          </Button>
        </div>
      ) : null}
    </div>
  )
}
