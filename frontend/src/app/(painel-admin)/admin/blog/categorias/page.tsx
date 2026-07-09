"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { toast } from "sonner"
import { ArrowLeftIcon } from "@heroicons/react/24/solid"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

type BlogCategoriaAdmin = {
  id: number
  nome: string
  slug: string
  sortOrder: number
  ativo: boolean
  postCountPublicados: number
}

function apiUrl(path: string) {
  const base = (process.env.NEXT_PUBLIC_API_URL || "").replace(/\/$/, "")
  return `${base}${path.startsWith("/") ? "" : "/"}${path}`
}

export default function BlogCategoriasAdminPage() {
  const router = useRouter()
  const [items, setItems] = useState<BlogCategoriaAdmin[]>([])
  const [loading, setLoading] = useState(true)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState({
    nome: "",
    slug: "",
    sortOrder: "100",
    ativo: true,
  })
  const [saving, setSaving] = useState(false)

  async function load() {
    setLoading(true)
    try {
      const res = await fetch(apiUrl("/admin/blog-categorias"), {
        credentials: "include",
        cache: "no-store",
      })
      if (!res.ok) throw new Error("Não foi possível carregar categorias.")
      const data = (await res.json()) as BlogCategoriaAdmin[]
      setItems(Array.isArray(data) ? data : [])
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "Erro ao carregar.")
      if (e instanceof Error && e.message.includes("401")) {
        router.push("/acesso-negado")
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps -- carga inicial
  }, [])

  function startEdit(row: BlogCategoriaAdmin) {
    setEditingId(row.id)
    setForm({
      nome: row.nome,
      slug: row.slug,
      sortOrder: String(row.sortOrder),
      ativo: row.ativo,
    })
  }

  function resetForm() {
    setEditingId(null)
    setForm({ nome: "", slug: "", sortOrder: "100", ativo: true })
  }

  async function handleSave() {
    if (!form.nome.trim()) {
      toast.warning("Informe o nome.")
      return
    }
    setSaving(true)
    try {
      const body = {
        nome: form.nome.trim(),
        slug: form.slug.trim() || undefined,
        sortOrder: Number(form.sortOrder || "0"),
        ativo: form.ativo,
      }
      const url =
        editingId == null
          ? apiUrl("/admin/blog-categorias")
          : apiUrl(`/admin/blog-categorias/${editingId}`)
      const res = await fetch(url, {
        method: editingId == null ? "POST" : "PUT",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      })
      if (!res.ok) {
        const t = (await res.text()).trim()
        throw new Error(t || "Falha ao salvar.")
      }
      toast.success(editingId == null ? "Categoria criada." : "Categoria atualizada.")
      resetForm()
      await load()
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : "Erro ao salvar.")
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="mx-auto max-w-4xl p-6">
      <div className="mb-8 flex flex-wrap items-center justify-between gap-4">
        <div>
          <Button variant="ghost" className="mb-2 gap-2 pl-0 text-gray-600" asChild>
            <Link href="/admin/blog">
              <ArrowLeftIcon className="h-4 w-4" />
              Voltar ao blog
            </Link>
          </Button>
          <h1 className="text-3xl font-extrabold text-gray-900">Categorias do blog</h1>
          <p className="text-sm text-gray-500">
            Categorias persistidas; posts referenciam por ID. Slug define a URL pública{" "}
            <code className="text-xs">/blog/categoria/[slug]</code>.
          </p>
        </div>
      </div>

      <Card className="mb-8 border border-gray-200 p-6 shadow-sm">
        <h2 className="mb-4 text-lg font-semibold">
          {editingId == null ? "Nova categoria" : `Editar #${editingId}`}
        </h2>
        <div className="grid gap-4 md:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="nome">Nome</Label>
            <Input
              id="nome"
              value={form.nome}
              onChange={(e) => setForm((f) => ({ ...f, nome: e.target.value }))}
              placeholder="Ex.: Guias e dicas"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="slug">Slug (opcional)</Label>
            <Input
              id="slug"
              value={form.slug}
              onChange={(e) => setForm((f) => ({ ...f, slug: e.target.value }))}
              placeholder="Derivado do nome se vazio"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="sort">Ordem</Label>
            <Input
              id="sort"
              type="number"
              value={form.sortOrder}
              onChange={(e) => setForm((f) => ({ ...f, sortOrder: e.target.value }))}
            />
          </div>
          <div className="flex items-center gap-2 pt-8">
            <input
              id="ativo"
              type="checkbox"
              checked={form.ativo}
              onChange={(e) => setForm((f) => ({ ...f, ativo: e.target.checked }))}
            />
            <Label htmlFor="ativo" className="font-normal">
              Ativa (aparece na listagem pública)
            </Label>
          </div>
        </div>
        <div className="mt-4 flex flex-wrap gap-2">
          <Button
            className="bg-[#FC1EAD] text-white hover:bg-[#d91992]"
            onClick={handleSave}
            disabled={saving}
          >
            {saving ? "Salvando…" : editingId == null ? "Criar" : "Salvar alterações"}
          </Button>
          {editingId != null && (
            <Button variant="outline" type="button" onClick={resetForm} disabled={saving}>
              Cancelar edição
            </Button>
          )}
        </div>
      </Card>

      {loading ? (
        <p className="text-center text-gray-500">Carregando…</p>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-gray-200">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-gray-50 text-gray-700">
              <tr>
                <th className="px-4 py-3">Nome</th>
                <th className="px-4 py-3">Slug</th>
                <th className="px-4 py-3">Ordem</th>
                <th className="px-4 py-3">Posts pub.</th>
                <th className="px-4 py-3">Ativa</th>
                <th className="px-4 py-3"></th>
              </tr>
            </thead>
            <tbody>
              {items.map((row) => (
                <tr key={row.id} className="border-t border-gray-100">
                  <td className="px-4 py-3 font-medium text-gray-900">{row.nome}</td>
                  <td className="px-4 py-3 text-gray-600">{row.slug}</td>
                  <td className="px-4 py-3">{row.sortOrder}</td>
                  <td className="px-4 py-3">{row.postCountPublicados}</td>
                  <td className="px-4 py-3">{row.ativo ? "sim" : "não"}</td>
                  <td className="px-4 py-3">
                    <Button variant="outline" size="sm" type="button" onClick={() => startEdit(row)}>
                      Editar
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {items.length === 0 && <p className="p-6 text-center text-gray-500">Nenhuma categoria.</p>}
        </div>
      )}
    </div>
  )
}
