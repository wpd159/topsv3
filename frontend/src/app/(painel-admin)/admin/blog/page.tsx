'use client'

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import { toast } from 'sonner'

import { ContractState } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  arquivarBlogPostAdmin,
  listarBlogPostsAdmin,
  publicarBlogPostAdmin,
  retirarBlogPostAdmin,
} from '@/lib/admin-blog-api'
import type { BlogPostDetail } from '@/lib/blog-api'
import { revalidarBlogPublico } from './actions'

export default function AdminBlogPage() {
  const [posts, setPosts] = useState<BlogPostDetail[]>([])
  const [termo, setTermo] = useState('')
  const [status, setStatus] = useState('TODOS')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [busyId, setBusyId] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setPosts(await listarBlogPostsAdmin({ termo, status }))
    } catch (loadError) {
      setError(loadError)
    } finally {
      setLoading(false)
    }
  }, [status, termo])

  useEffect(() => {
    void load()
  }, [load])

  async function changeStatus(
    post: BlogPostDetail,
    action: 'publicar' | 'retirar' | 'arquivar',
  ) {
    if (busyId) return
    setBusyId(post.id)
    setError(null)
    try {
      if (action === 'publicar') await publicarBlogPostAdmin(post.id)
      if (action === 'retirar') await retirarBlogPostAdmin(post.id)
      if (action === 'arquivar') await arquivarBlogPostAdmin(post.id)
      await revalidarBlogPublico(post.slug)
      await load()
      toast.success(
        action === 'publicar'
          ? 'Post publicado.'
          : action === 'retirar'
            ? 'Post retirado da publicação.'
            : 'Post arquivado.',
      )
    } catch (actionError) {
      setError(actionError)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section className="space-y-6">
      <div className="flex flex-col justify-between gap-3 lg:flex-row lg:items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Blog</h1>
          <p className="text-sm text-gray-600">
            Posts editoriais, categorias e publicação pública.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button asChild variant="outline">
            <Link href="/admin/blog/categorias">Categorias do blog</Link>
          </Button>
          <Button asChild variant="outline">
            <Link href="/admin/blog/programatico">Páginas programáticas (SEO)</Link>
          </Button>
          <Button asChild>
            <Link href="/admin/blog/novo">Novo post</Link>
          </Button>
        </div>
      </div>

      {error ? <ContractState error={error} onRetry={load} /> : null}

      <form
        className="grid gap-3 rounded-lg border border-gray-200 bg-white p-4 md:grid-cols-[1fr_220px_auto]"
        onSubmit={(event) => {
          event.preventDefault()
          void load()
        }}
      >
        <Input
          value={termo}
          onChange={(event) => setTermo(event.target.value)}
          placeholder="Buscar por título, autor ou slug"
        />
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="TODOS">Todos os status</SelectItem>
            <SelectItem value="RASCUNHO">Rascunho</SelectItem>
            <SelectItem value="PUBLICADO">Publicado</SelectItem>
            <SelectItem value="ARQUIVADO">Arquivado</SelectItem>
          </SelectContent>
        </Select>
        <Button type="submit" variant="outline" disabled={loading}>
          {loading ? 'Carregando...' : 'Aplicar filtros'}
        </Button>
      </form>

      <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Título</TableHead>
              <TableHead>Categoria</TableHead>
              <TableHead>Autor</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Atualização</TableHead>
              <TableHead>Ações</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {!loading && posts.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="py-10 text-center text-gray-500">
                  Nenhum post corresponde aos filtros.
                </TableCell>
              </TableRow>
            ) : null}
            {posts.map((post) => (
              <TableRow key={post.id}>
                <TableCell>
                  <p className="font-medium text-gray-900">{post.titulo}</p>
                  <p className="text-xs text-gray-500">{post.slug}</p>
                </TableCell>
                <TableCell>{post.categoria}</TableCell>
                <TableCell>{post.autorNome}</TableCell>
                <TableCell>{post.status}</TableCell>
                <TableCell>
                  {post.updatedAt
                    ? new Date(post.updatedAt).toLocaleString('pt-BR')
                    : '—'}
                </TableCell>
                <TableCell>
                  <div className="flex flex-wrap gap-2">
                    {post.status === 'PUBLICADO' ? (
                      <Button asChild size="sm" variant="outline">
                        <Link href={`/blog/${post.slug}`} target="_blank">Ver</Link>
                      </Button>
                    ) : null}
                    <Button asChild size="sm" variant="outline">
                      <Link href={`/admin/blog/${post.id}/editar`}>Editar</Link>
                    </Button>
                    {post.status === 'RASCUNHO' ? (
                      <Button
                        size="sm"
                        disabled={busyId === post.id}
                        onClick={() => void changeStatus(post, 'publicar')}
                      >
                        Publicar
                      </Button>
                    ) : null}
                    {post.status === 'PUBLICADO' ? (
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={busyId === post.id}
                        onClick={() => void changeStatus(post, 'retirar')}
                      >
                        Retirar
                      </Button>
                    ) : null}
                    {post.status !== 'ARQUIVADO' ? (
                      <Button
                        size="sm"
                        variant="destructive"
                        disabled={busyId === post.id}
                        onClick={() => void changeStatus(post, 'arquivar')}
                      >
                        Arquivar
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
