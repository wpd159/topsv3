'use client'

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import { toast } from 'sonner'

import { ContractState } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  atualizarBlogCategoriaAdmin,
  criarBlogCategoriaAdmin,
  desativarBlogCategoriaAdmin,
  listarBlogCategoriasAdmin,
} from '@/lib/admin-blog-api'
import type { BlogCategoriaPublic } from '@/lib/blog-api'
import { revalidarBlogPublico } from '../actions'

const EMPTY = { nome: '', slug: '', ordem: '0', ativa: true }

export default function BlogCategoriesPage() {
  const [categories, setCategories] = useState<BlogCategoriaPublic[]>([])
  const [editing, setEditing] = useState<BlogCategoriaPublic | null>(null)
  const [form, setForm] = useState(EMPTY)
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    setError(null)
    try {
      setCategories(await listarBlogCategoriasAdmin())
    } catch (loadError) {
      setError(loadError)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  function startEdit(category: BlogCategoriaPublic) {
    setEditing(category)
    setForm({
      nome: category.nome,
      slug: category.slug,
      ordem: String(category.ordem),
      ativa: category.ativa,
    })
  }

  function cancelEdit() {
    setEditing(null)
    setForm(EMPTY)
  }

  async function submit() {
    if (busy) return
    setBusy(true)
    setError(null)
    const input = {
      nome: form.nome,
      slug: form.slug,
      ordem: Number(form.ordem),
      ativa: form.ativa,
      versao: editing?.versao ?? null,
    }
    try {
      if (editing) {
        await atualizarBlogCategoriaAdmin(editing.id, input)
      } else {
        await criarBlogCategoriaAdmin(input)
      }
      await revalidarBlogPublico()
      await load()
      cancelEdit()
      toast.success(editing ? 'Categoria atualizada.' : 'Categoria criada.')
    } catch (submitError) {
      setError(submitError)
    } finally {
      setBusy(false)
    }
  }

  async function deactivate(category: BlogCategoriaPublic) {
    if (busy || !category.ativa) return
    setBusy(true)
    setError(null)
    try {
      await desativarBlogCategoriaAdmin(category.id)
      await revalidarBlogPublico()
      await load()
      toast.success('Categoria desativada.')
    } catch (deactivateError) {
      setError(deactivateError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="space-y-6">
      <div>
        <Button asChild variant="ghost" className="px-0">
          <Link href="/admin/blog">Voltar ao blog</Link>
        </Button>
        <h1 className="text-2xl font-bold text-gray-900">Categorias do blog</h1>
      </div>
      {error ? <ContractState error={error} onRetry={load} /> : null}

      <form
        className="grid gap-4 rounded-lg border border-gray-200 bg-white p-5 md:grid-cols-4"
        onSubmit={(event) => {
          event.preventDefault()
          void submit()
        }}
      >
        <div className="space-y-2">
          <Label htmlFor="category-name">Nome</Label>
          <Input
            id="category-name"
            value={form.nome}
            onChange={(event) => setForm((value) => ({ ...value, nome: event.target.value }))}
            placeholder="Ex.: Guias e dicas"
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="category-slug">Slug (opcional)</Label>
          <Input
            id="category-slug"
            value={form.slug}
            onChange={(event) => setForm((value) => ({ ...value, slug: event.target.value }))}
            placeholder="Derivado do nome se vazio"
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="category-order">Ordem</Label>
          <Input
            id="category-order"
            type="number"
            min={0}
            value={form.ordem}
            onChange={(event) => setForm((value) => ({ ...value, ordem: event.target.value }))}
          />
        </div>
        <label className="flex items-center gap-2 self-end pb-3 text-sm">
          <input
            type="checkbox"
            checked={form.ativa}
            onChange={(event) => setForm((value) => ({ ...value, ativa: event.target.checked }))}
          />
          Ativa
        </label>
        <div className="flex flex-wrap gap-2 md:col-span-4">
          <Button type="submit" disabled={busy}>
            {busy ? 'Salvando...' : editing ? 'Salvar alterações' : 'Nova categoria'}
          </Button>
          {editing ? (
            <Button type="button" variant="outline" onClick={cancelEdit}>
              Cancelar edição
            </Button>
          ) : null}
        </div>
      </form>

      <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Nome</TableHead>
              <TableHead>Slug</TableHead>
              <TableHead>Ordem</TableHead>
              <TableHead>Posts publicados</TableHead>
              <TableHead>Ativa</TableHead>
              <TableHead>Ações</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {categories.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="py-8 text-center text-gray-500">
                  Nenhuma categoria cadastrada.
                </TableCell>
              </TableRow>
            ) : null}
            {categories.map((category) => (
              <TableRow key={category.id}>
                <TableCell>{category.nome}</TableCell>
                <TableCell>{category.slug}</TableCell>
                <TableCell>{category.ordem}</TableCell>
                <TableCell>{category.postCountPublicados}</TableCell>
                <TableCell>{category.ativa ? 'Sim' : 'Não'}</TableCell>
                <TableCell>
                  <div className="flex gap-2">
                    <Button type="button" variant="outline" onClick={() => startEdit(category)}>
                      Editar
                    </Button>
                    {category.ativa ? (
                      <Button
                        type="button"
                        variant="destructive"
                        disabled={busy}
                        onClick={() => void deactivate(category)}
                      >
                        Desativar
                      </Button>
                    ) : null}
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </section>
  )
}
