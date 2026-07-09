'use client'

import { useMemo, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { toast } from 'sonner'
import { TrashIcon, PlusIcon } from '@heroicons/react/24/solid'

type Props = {
  anuncioId: number
  fotos: string[]
  onUpdated?: (novasFotos: string[]) => void
}

export default function FotosAnuncioSection({ anuncioId, fotos, onUpdated }: Props) {
  const inputRef = useRef<HTMLInputElement | null>(null)

  const [busy, setBusy] = useState(false)
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

  const clearSelected = () => setSelected({})

  async function removerSelecionadas() {
    if (!selectedUrls.length) return

    try {
      setBusy(true)

      const res = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/anuncios/staff/${anuncioId}/fotos/remover`,
        {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({ fotosParaRemover: selectedUrls }),
        }
      )

      if (!res.ok) throw new Error('Falha ao remover fotos')

      const data = await res.json()
      const novas = (data?.fotosUrl ?? []) as string[]

      toast.success('Foto(s) removida(s).')
      clearSelected()
      onUpdated?.(novas)
    } catch {
      toast.error('Erro ao remover fotos.')
    } finally {
      setBusy(false)
    }
  }

  async function adicionarFotos(files: FileList | null) {
    if (!files || !files.length) return

    try {
      setBusy(true)

      const fd = new FormData()
      fotosUnicas.forEach((url) => fd.append('fotosExistentes', url))
      Array.from(files).forEach((file) => fd.append('novasFotos', file))

      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/anuncios/staff/${anuncioId}/fotos`, {
        method: 'PUT',
        credentials: 'include',
        body: fd,
      })

      if (!res.ok) throw new Error('Falha ao enviar novas fotos')

      const data = await res.json()
      const novas = (data?.fotosUrl ?? []) as string[]

      toast.success('Foto(s) adicionada(s).')
      onUpdated?.(novas)

      if (inputRef.current) inputRef.current.value = ''
    } catch {
      toast.error('Erro ao adicionar fotos.')
    } finally {
      setBusy(false)
    }
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
            disabled={busy}
            className="flex items-center gap-1 border-[#C41E73]/40 text-[#C41E73] hover:bg-[#FC1EAD]/10"
          >
            <PlusIcon className="h-4 w-4" />
            Adicionar
          </Button>

          <Button
            variant="outline"
            onClick={removerSelecionadas}
            disabled={busy || selectedUrls.length === 0}
            className="flex items-center gap-1 border-red-300 text-red-600 hover:bg-red-50"
          >
            <TrashIcon className="h-4 w-4" />
            Remover ({selectedUrls.length})
          </Button>
        </div>
      </div>

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
