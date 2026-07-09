"use client"

import { useEffect, useMemo, useState } from "react"
import Image from "next/image"
import { PencilSquareIcon } from "@heroicons/react/24/solid"
import { toast } from "sonner"

import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"

type CategoriaHome = {
  id: number | null
  nome: string
  descricao: string
  imagemUrl: string | null
  categoriaEnum: string
  ativo: boolean
}

const CATEGORY_IMAGE_FALLBACK: Record<string, string> = {
  ACOMPANHANTE_FEMININA: "/cards/acompanhante-feminina.jpg",
  ACOMPANHANTE_MASCULINO: "/cards/acompanhante-masculino.jpg",
  TRANSEX_TRAVESTIS: "/cards/acompanhante-trans.jpg",
  MASSAGENS: "/cards/massagem.jpg",
  ENCONTROS_CASUAIS: "/cards/casual.jpg",
  VENDA_DE_CONTEUDO: "/cards/casual.jpg",
}

function apiUrl(path: string) {
  const base = (process.env.NEXT_PUBLIC_API_URL || "").replace(/\/$/, "")
  return `${base}${path}`
}

function CategoriaSwitch({
  checked,
  disabled,
  onChange,
}: {
  checked: boolean
  disabled?: boolean
  onChange: (value: boolean) => void
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
        checked ? "bg-[#FC1EAD]" : "bg-gray-300"
      } ${disabled ? "cursor-not-allowed opacity-60" : ""}`}
    >
      <span
        className={`inline-block h-4 w-4 rounded-full bg-white transition-transform ${
          checked ? "translate-x-4" : "translate-x-0.5"
        }`}
      />
    </button>
  )
}

export default function AdminCategoriasHomePage() {
  const [categorias, setCategorias] = useState<CategoriaHome[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<CategoriaHome | null>(null)
  const [novaImagem, setNovaImagem] = useState<File | null>(null)

  const orderedCategorias = useMemo(
    () => [...categorias].sort((a, b) => a.categoriaEnum.localeCompare(b.categoriaEnum)),
    [categorias],
  )

  async function fetchCategorias() {
    try {
      setLoading(true)
      const res = await fetch(apiUrl("/categorias-home"), { credentials: "include" })
      if (!res.ok) throw new Error(await res.text())
      const data = await res.json()
      setCategorias(Array.isArray(data) ? data : [])
    } catch {
      toast.error("Erro ao carregar categorias da home.")
      setCategorias([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchCategorias()
  }, [])

  async function salvarCategoria(categoria: CategoriaHome) {
    const method = categoria.id ? "PUT" : "POST"
    const path = categoria.id ? `/categorias-home/${categoria.id}` : "/categorias-home"

    const res = await fetch(apiUrl(path), {
      method,
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify(categoria),
    })

    if (!res.ok) throw new Error(await res.text())
  }

  async function salvarEdicao() {
    if (!editing) return

    try {
      setSaving(true)
      await salvarCategoria(editing)
      toast.success("Categoria atualizada.")
      setEditing(null)
      await fetchCategorias()
    } catch {
      toast.error("Erro ao salvar categoria.")
    } finally {
      setSaving(false)
    }
  }

  async function atualizarImagem() {
    if (!editing || !novaImagem) return

    try {
      setSaving(true)
      const formData = new FormData()
      formData.append("imagem", novaImagem)

      const path = editing.id
        ? `/categorias-home/${editing.id}/imagem`
        : `/categorias-home/upload-default?categoriaEnum=${encodeURIComponent(editing.categoriaEnum)}`

      const res = await fetch(apiUrl(path), {
        method: editing.id ? "PUT" : "POST",
        credentials: "include",
        body: formData,
      })

      if (!res.ok) throw new Error(await res.text())

      toast.success("Imagem atualizada.")
      setNovaImagem(null)
      setEditing(null)
      await fetchCategorias()
    } catch {
      toast.error("Erro ao atualizar imagem.")
    } finally {
      setSaving(false)
    }
  }

  async function toggleAtivo(categoria: CategoriaHome, ativo: boolean) {
    try {
      await salvarCategoria({ ...categoria, ativo })
      await fetchCategorias()
    } catch {
      toast.error("Erro ao atualizar status da categoria.")
    }
  }

  return (
    <section className="mx-auto max-w-6xl space-y-8 px-6 py-10">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Categorias da home</h1>
          <p className="mt-2 text-sm text-gray-600">
            Controle os cards públicos exibidos na home. A lista vem do backend e respeita o status ativo.
          </p>
        </div>

        <Button variant="outline" onClick={fetchCategorias} disabled={loading}>
          Recarregar
        </Button>
      </div>

      {loading ? (
        <div className="rounded-2xl border bg-white p-8 text-center text-sm text-gray-500">
          Carregando categorias...
        </div>
      ) : orderedCategorias.length === 0 ? (
        <div className="rounded-2xl border bg-white p-8 text-center text-sm text-gray-500">
          Nenhuma categoria retornada pelo backend.
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
          {orderedCategorias.map((categoria) => {
            const imageSrc =
              categoria.imagemUrl?.trim() ||
              CATEGORY_IMAGE_FALLBACK[categoria.categoriaEnum] ||
              "/cards/casual.jpg"

            return (
              <article
                key={categoria.categoriaEnum}
                className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm"
              >
                <div className="relative h-40 bg-gray-100">
                  <Image
                    src={imageSrc}
                    alt={categoria.nome}
                    fill
                    className="object-cover"
                    sizes="(min-width: 1280px) 33vw, (min-width: 768px) 50vw, 100vw"
                  />
                </div>

                <div className="space-y-4 p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <h2 className="text-lg font-semibold text-gray-900">{categoria.nome}</h2>
                      <p className="mt-1 text-xs font-medium uppercase tracking-wide text-gray-400">
                        {categoria.categoriaEnum}
                      </p>
                    </div>

                    <Button size="icon" variant="ghost" onClick={() => setEditing(categoria)}>
                      <PencilSquareIcon className="h-5 w-5 text-gray-600" />
                    </Button>
                  </div>

                  <p className="min-h-[42px] text-sm leading-5 text-gray-600">{categoria.descricao}</p>

                  <div className="flex items-center justify-between border-t pt-3">
                    <span className="text-sm text-gray-500">Exibir na home</span>
                    <CategoriaSwitch
                      checked={categoria.ativo !== false}
                      onChange={(checked) => toggleAtivo(categoria, checked)}
                    />
                  </div>
                </div>
              </article>
            )
          })}
        </div>
      )}

      <Dialog open={!!editing} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Editar categoria da home</DialogTitle>
          </DialogHeader>

          {editing && (
            <div className="space-y-4">
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700">Nome</label>
                <Input
                  value={editing.nome}
                  onChange={(event) => setEditing({ ...editing, nome: event.target.value })}
                />
              </div>

              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700">Descrição</label>
                <Input
                  value={editing.descricao}
                  onChange={(event) => setEditing({ ...editing, descricao: event.target.value })}
                />
              </div>

              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700">Imagem</label>
                <Input
                  type="file"
                  accept="image/*"
                  onChange={(event) => setNovaImagem(event.target.files?.[0] ?? null)}
                />
              </div>

              <div className="flex items-center justify-between rounded-lg bg-gray-50 px-3 py-2">
                <span className="text-sm text-gray-600">Categoria ativa</span>
                <CategoriaSwitch
                  checked={editing.ativo !== false}
                  onChange={(checked) => setEditing({ ...editing, ativo: checked })}
                />
              </div>

              <div className="flex justify-end gap-3 pt-2">
                <Button variant="outline" onClick={() => setEditing(null)} disabled={saving}>
                  Cancelar
                </Button>
                {novaImagem && (
                  <Button variant="outline" onClick={atualizarImagem} disabled={saving}>
                    Trocar imagem
                  </Button>
                )}
                <Button onClick={salvarEdicao} disabled={saving}>
                  Salvar
                </Button>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </section>
  )
}
