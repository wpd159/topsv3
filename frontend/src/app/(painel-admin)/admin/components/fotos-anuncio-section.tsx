'use client'

import { useMemo, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { TrashIcon, PlusIcon } from '@heroicons/react/24/solid'
import { PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'

type Props = {
  anuncioId: string | number
  fotos: string[]
  onUpdated?: (novasFotos: string[]) => void
}

export default function FotosAnuncioSection({ anuncioId, fotos }: Props) {
  const inputRef = useRef<HTMLInputElement | null>(null)
  const pendingAction = usePendingContractActions(`Gestao administrativa de fotos do anuncio ${anuncioId}`)

  const [selected, setSelected] = useState<Record<string, boolean>>({})

  const fotosUnicas = useMemo(() => {
    return Array.from(new Set(fotos))
  }, [fotos])

  const selectedUrls = Object.entries(selected)
    .filter(([, ativo]) => ativo)
    .map(([url]) => url)

  const toggle = (url: string) => {
    setSelected((prev) => ({ ...prev, [url]: !prev[url] }))
  }

  function removerSelecionadas() {
    if (!selectedUrls.length) return
    pendingAction.runPendingAction('Remover fotos selecionadas')
  }

  function adicionarFotos(files: FileList | null) {
    if (!files || !files.length) return
    pendingAction.runPendingAction('Adicionar fotos')
    if (inputRef.current) inputRef.current.value = ''
  }

  return (
    <div className="mb-10 rounded-xl border border-gray-100 bg-white shadow-sm">
      <div className="flex items-center justify-between border-b bg-gradient-to-r from-[#FC1EAD]/10 to-transparent px-5 py-4">
        <h3 className="text-base font-semibold text-gray-800">Fotos do anúncio</h3>

        <div className="flex items-center gap-2">
          <input
            ref={inputRef}
            type="file"
            accept="image/*"
            multiple
            className="hidden"
            onChange={(e) => adicionarFotos(e.target.files)}
          />

          <Button
            variant="outline"
            onClick={() => inputRef.current?.click()}
            className="flex items-center gap-1 border-[#C41E73]/40 text-[#C41E73] hover:bg-[#FC1EAD]/10"
          >
            <PlusIcon className="h-4 w-4" />
            Adicionar
          </Button>

          <Button
            variant="outline"
            onClick={removerSelecionadas}
            disabled={selectedUrls.length === 0}
            className="flex items-center gap-1 border-red-300 text-red-600 hover:bg-red-50"
          >
            <TrashIcon className="h-4 w-4" />
            Remover ({selectedUrls.length})
          </Button>
        </div>
      </div>

      {pendingAction.attemptedAction ? (
        <div className="border-b border-amber-200 bg-amber-50 px-5 py-3">
          <PendingActionFeedback attemptedAction={pendingAction.attemptedAction} />
        </div>
      ) : null}

      <div className="p-6">
        {fotosUnicas.length ? (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4">
            {fotosUnicas.map((url, index) => {
              const active = !!selected[url]

              return (
                <button
                  key={`foto-${url}`}
                  type="button"
                  onClick={() => toggle(url)}
                  className={`relative overflow-hidden rounded-lg border transition ${
                    active
                      ? 'border-[#C41E73] ring-2 ring-[#FC1EAD]/30'
                      : 'border-gray-200 hover:shadow-md'
                  }`}
                  title="Clique para selecionar"
                >
                  <img
                    src={url}
                    alt={`Foto ${index + 1}`}
                    className="h-40 w-full object-cover"
                    loading="lazy"
                  />

                  <div className="absolute right-2 top-2">
                    <div
                      className={`flex h-5 w-5 items-center justify-center rounded-full border text-[10px] font-bold ${
                        active
                          ? 'border-[#C41E73] bg-[#C41E73] text-white'
                          : 'border-gray-300 bg-white/90 text-gray-600'
                      }`}
                    >
                      ✓
                    </div>
                  </div>
                </button>
              )
            })}
          </div>
        ) : (
          <p className="py-6 text-center text-gray-500">Nenhuma foto.</p>
        )}
      </div>
    </div>
  )
}
