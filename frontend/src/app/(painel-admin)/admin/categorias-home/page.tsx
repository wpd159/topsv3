"use client"

import { useEffect, useMemo, useState } from "react"
import Image from "next/image"
import { PencilSquareIcon, PlusIcon } from "@heroicons/react/24/solid"
import { toast } from "sonner"

import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import {
  listarCategoriasCanonicasAdmin,
  listarCategoriasHomeAdmin,
  salvarCategoriaHomeAdmin,
  type AdminCanonicalCategory,
  type AdminHomeCategory,
  type AdminHomeCategoryInput,
} from "@/lib/admin-categorias-home-api"
import { revalidarCacheCategoriasHome } from "./actions"

type CategoryForm = AdminHomeCategoryInput & {
  id: string | null
  imagemUrl: string | null
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
      role="switch"
      aria-checked={checked}
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
  const [categorias, setCategorias] = useState<AdminHomeCategory[]>([])
  const [categoriasCanonicas, setCategoriasCanonicas] = useState<AdminCanonicalCategory[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<CategoryForm | null>(null)
  const [novaImagem, setNovaImagem] = useState<File | null>(null)

  const orderedCategorias = useMemo(
    () => [...categorias].sort((a, b) => a.ordem - b.ordem || a.id.localeCompare(b.id)),
    [categorias],
  )

  const codigosVinculados = useMemo(
    () => new Set(categorias.map((categoria) => categoria.categoriaCodigo)),
    [categorias],
  )

  async function fetchCategorias() {
    try {
      setLoading(true)
      const [cards, taxonomia] = await Promise.all([
        listarCategoriasHomeAdmin(),
        listarCategoriasCanonicasAdmin(),
      ])
      setCategorias(cards)
      setCategoriasCanonicas(taxonomia)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Erro ao carregar categorias da home.")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void fetchCategorias()
  }, [])

  function abrirCriacao() {
    const categoriaDisponivel = categoriasCanonicas.find(
      (categoria) => !codigosVinculados.has(categoria.codigo),
    )
    if (!categoriaDisponivel) {
      toast.error("Todas as categorias canônicas já possuem um card.")
      return
    }
    const proximaOrdem = categorias.reduce((maior, categoria) => Math.max(maior, categoria.ordem), 0) + 1
    setNovaImagem(null)
    setEditing({
      id: null,
      categoriaCodigo: categoriaDisponivel.codigo,
      nome: categoriaDisponivel.nome,
      descricao: "",
      ordem: proximaOrdem,
      ativo: true,
      imagemUrl: null,
    })
  }

  function abrirEdicao(categoria: AdminHomeCategory) {
    setNovaImagem(null)
    setEditing({
      id: categoria.id,
      categoriaCodigo: categoria.categoriaCodigo,
      nome: categoria.nome,
      descricao: categoria.descricao,
      ordem: categoria.ordem,
      ativo: categoria.ativo,
      imagemUrl: categoria.imagemUrl,
    })
  }

  async function salvarEdicao() {
    if (!editing) return
    if (!editing.id && !novaImagem) {
      toast.error("Escolha a imagem da categoria.")
      return
    }

    try {
      setSaving(true)
      await salvarCategoriaHomeAdmin(
        editing.id,
        {
          categoriaCodigo: editing.categoriaCodigo,
          nome: editing.nome,
          descricao: editing.descricao,
          ordem: editing.ordem,
          ativo: editing.ativo,
        },
        novaImagem,
      )
      await revalidarCacheCategoriasHome()
      toast.success(editing.id ? "Categoria atualizada." : "Categoria criada.")
      setEditing(null)
      setNovaImagem(null)
      await fetchCategorias()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Erro ao salvar categoria.")
    } finally {
      setSaving(false)
    }
  }

  async function toggleAtivo(categoria: AdminHomeCategory, ativo: boolean) {
    try {
      await salvarCategoriaHomeAdmin(
        categoria.id,
        {
          categoriaCodigo: categoria.categoriaCodigo,
          nome: categoria.nome,
          descricao: categoria.descricao,
          ordem: categoria.ordem,
          ativo,
        },
        null,
      )
      await revalidarCacheCategoriasHome()
      await fetchCategorias()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Erro ao atualizar a categoria.")
    }
  }

  return (
    <section className="mx-auto max-w-6xl space-y-8 px-6 py-10">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Categorias da home</h1>
          <p className="mt-2 text-sm text-gray-600">
            Configure os cards públicos e vincule cada um à categoria escolhida pelos anunciantes.
          </p>
        </div>

        <div className="flex gap-2">
          <Button variant="outline" onClick={() => void fetchCategorias()} disabled={loading}>
            Recarregar
          </Button>
          <Button onClick={abrirCriacao} disabled={loading || categoriasCanonicas.length === 0}>
            <PlusIcon className="mr-2 h-4 w-4" />
            Nova categoria
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="rounded-2xl border bg-white p-8 text-center text-sm text-gray-500">
          Carregando categorias...
        </div>
      ) : orderedCategorias.length === 0 ? (
        <div className="rounded-2xl border bg-white p-8 text-center text-sm text-gray-500">
          Nenhuma categoria configurada.
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
          {orderedCategorias.map((categoria) => (
            <article
              key={categoria.id}
              className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm"
            >
              <div className="relative h-40 bg-gray-100">
                <Image
                  src={categoria.imagemUrl}
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
                    <p className="mt-1 text-xs font-medium uppercase text-gray-400">
                      {categoria.categoriaNome} · ordem {categoria.ordem}
                    </p>
                  </div>

                  <Button
                    size="icon"
                    variant="ghost"
                    title="Editar categoria"
                    onClick={() => abrirEdicao(categoria)}
                  >
                    <PencilSquareIcon className="h-5 w-5 text-gray-600" />
                  </Button>
                </div>

                <p className="min-h-[42px] text-sm leading-5 text-gray-600">{categoria.descricao}</p>

                <div className="flex items-center justify-between border-t pt-3">
                  <span className="text-sm text-gray-500">Exibir na home</span>
                  <CategoriaSwitch
                    checked={categoria.ativo}
                    onChange={(checked) => void toggleAtivo(categoria, checked)}
                  />
                </div>
              </div>
            </article>
          ))}
        </div>
      )}

      <Dialog
        open={Boolean(editing)}
        onOpenChange={(open) => {
          if (!open && !saving) {
            setEditing(null)
            setNovaImagem(null)
          }
        }}
      >
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{editing?.id ? "Editar categoria da home" : "Nova categoria da home"}</DialogTitle>
          </DialogHeader>

          {editing && (
            <div className="space-y-4">
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700">Categoria do anúncio</label>
                <Select
                  value={editing.categoriaCodigo}
                  onValueChange={(value) => setEditing({ ...editing, categoriaCodigo: value })}
                >
                  <SelectTrigger className="w-full rounded-lg bg-white">
                    <SelectValue placeholder="Selecione a categoria" />
                  </SelectTrigger>
                  <SelectContent>
                    {categoriasCanonicas.map((categoria) => {
                      const usadaPorOutro = codigosVinculados.has(categoria.codigo)
                        && categoria.codigo !== editing.categoriaCodigo
                      return (
                        <SelectItem key={categoria.codigo} value={categoria.codigo} disabled={usadaPorOutro}>
                          {categoria.nome}
                        </SelectItem>
                      )
                    })}
                  </SelectContent>
                </Select>
              </div>

              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700">Nome público</label>
                <Input
                  value={editing.nome}
                  maxLength={120}
                  onChange={(event) => setEditing({ ...editing, nome: event.target.value })}
                />
              </div>

              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700">Descrição</label>
                <Textarea
                  value={editing.descricao}
                  maxLength={280}
                  onChange={(event) => setEditing({ ...editing, descricao: event.target.value })}
                />
              </div>

              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700">Ordem</label>
                <Input
                  type="number"
                  min={0}
                  value={editing.ordem}
                  onChange={(event) => setEditing({ ...editing, ordem: Number(event.target.value) })}
                />
              </div>

              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700">
                  {editing.id ? "Substituir imagem" : "Imagem"}
                </label>
                <Input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  onChange={(event) => setNovaImagem(event.target.files?.[0] ?? null)}
                />
                {novaImagem && <p className="mt-1 text-xs text-gray-500">{novaImagem.name}</p>}
              </div>

              <div className="flex items-center justify-between rounded-lg bg-gray-50 px-3 py-2">
                <span className="text-sm text-gray-600">Categoria ativa</span>
                <CategoriaSwitch
                  checked={editing.ativo}
                  onChange={(checked) => setEditing({ ...editing, ativo: checked })}
                />
              </div>

              <div className="flex justify-end gap-3 pt-2">
                <Button variant="outline" onClick={() => setEditing(null)} disabled={saving}>
                  Cancelar
                </Button>
                <Button onClick={() => void salvarEdicao()} disabled={saving}>
                  {saving ? "Salvando..." : "Salvar"}
                </Button>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </section>
  )
}
