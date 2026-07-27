'use client'

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'

import { ContractState } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { SafeBlogPostBody } from '@/lib/blog/safe-blog-body'
import {
  arquivarBlogPostAdmin,
  atualizarBlogPostAdmin,
  buscarBlogPostAdmin,
  criarBlogPostAdmin,
  enviarImagemBlogAdmin,
  listarBlogCategoriasAdmin,
  publicarBlogPostAdmin,
  retirarBlogPostAdmin,
  type BlogPostInput,
} from '@/lib/admin-blog-api'
import type {
  BlogCategoriaPublic,
  BlogPostDetail,
} from '@/lib/blog-api'
import { revalidarBlogPublico } from '../actions'

type Props = {
  mode: 'create' | 'edit'
  initialPost?: BlogPostDetail | null
  postId?: string
}

type FormState = {
  titulo: string
  categoriaId: string
  autorNome: string
  slug: string
  imagemUrl: string
  imagemCapaId: string | null
  ogImageUrl: string
  imagemOgId: string | null
  resumo: string
  conteudo: string
  seoTitle: string
  seoDescription: string
  sitemapPriority: string
  changeFrequency: string
}

const EMPTY: FormState = {
  titulo: '',
  categoriaId: '',
  autorNome: 'Equipe Tops do Job',
  slug: '',
  imagemUrl: '',
  imagemCapaId: null,
  ogImageUrl: '',
  imagemOgId: null,
  resumo: '',
  conteudo: '',
  seoTitle: '',
  seoDescription: '',
  sitemapPriority: '0.7',
  changeFrequency: 'weekly',
}

function fromPost(post: BlogPostDetail): FormState {
  return {
    titulo: post.titulo,
    categoriaId: post.categoriaId,
    autorNome: post.autorNome,
    slug: post.slug,
    imagemUrl: post.imagemUrl || '',
    imagemCapaId: post.imagemCapaId || null,
    ogImageUrl: post.ogImageUrl || '',
    imagemOgId: post.imagemOgId || null,
    resumo: post.resumo,
    conteudo: post.conteudo,
    seoTitle: post.seoTitle,
    seoDescription: post.seoDescription,
    sitemapPriority: String(post.sitemapPriority),
    changeFrequency: post.changeFrequency,
  }
}

export function BlogPostForm({ mode, initialPost, postId }: Props) {
  const router = useRouter()
  const [post, setPost] = useState<BlogPostDetail | null>(initialPost || null)
  const [form, setForm] = useState<FormState>(
    initialPost ? fromPost(initialPost) : EMPTY,
  )
  const [categories, setCategories] = useState<BlogCategoriaPublic[]>([])
  const [previewOpen, setPreviewOpen] = useState(false)
  const [coverFile, setCoverFile] = useState<File | null>(null)
  const [ogFile, setOgFile] = useState<File | null>(null)
  const [loading, setLoading] = useState(mode === 'edit' && !initialPost)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [loadedCategories, loadedPost] = await Promise.all([
        listarBlogCategoriasAdmin(),
        mode === 'edit' && postId
          ? buscarBlogPostAdmin(postId)
          : Promise.resolve(null),
      ])
      setCategories(loadedCategories)
      if (loadedPost) {
        setPost(loadedPost)
        setForm(fromPost(loadedPost))
      }
    } catch (loadError) {
      setError(loadError)
    } finally {
      setLoading(false)
    }
  }, [mode, postId])

  useEffect(() => {
    void load()
  }, [load])

  function update<K extends keyof FormState>(field: K, value: FormState[K]) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  function input(): BlogPostInput {
    return {
      categoriaId: form.categoriaId,
      titulo: form.titulo,
      slug: form.slug,
      resumo: form.resumo,
      conteudo: form.conteudo,
      autorNome: form.autorNome,
      seoTitle: form.seoTitle,
      seoDescription: form.seoDescription,
      sitemapPriority: Number(form.sitemapPriority),
      changeFrequency: form.changeFrequency,
      imagemCapaId: form.imagemCapaId,
      imagemOgId: form.imagemOgId,
      versao: post?.versao ?? null,
    }
  }

  async function save(publishAfter = false) {
    if (busy) return
    setBusy(true)
    setError(null)
    try {
      let saved = post
        ? await atualizarBlogPostAdmin(post.id, input())
        : await criarBlogPostAdmin(input())
      if (publishAfter && saved.status !== 'PUBLICADO') {
        saved = await publicarBlogPostAdmin(saved.id)
      }
      setPost(saved)
      setForm(fromPost(saved))
      await revalidarBlogPublico(saved.slug)
      if (!post) {
        router.replace(`/admin/blog/${saved.id}/editar`)
      }
      toast.success(
        publishAfter
          ? 'Post publicado com sucesso.'
          : saved.status === 'PUBLICADO'
            ? 'Alterações publicadas.'
            : 'Rascunho salvo.',
      )
    } catch (saveError) {
      setError(saveError)
    } finally {
      setBusy(false)
    }
  }

  async function changeStatus(action: 'retirar' | 'arquivar') {
    if (!post || busy) return
    setBusy(true)
    setError(null)
    try {
      const updated = action === 'retirar'
        ? await retirarBlogPostAdmin(post.id)
        : await arquivarBlogPostAdmin(post.id)
      setPost(updated)
      setForm(fromPost(updated))
      await revalidarBlogPublico(updated.slug)
      toast.success(
        action === 'retirar'
          ? 'Post retirado da publicação.'
          : 'Post arquivado.',
      )
    } catch (changeError) {
      setError(changeError)
    } finally {
      setBusy(false)
    }
  }

  async function upload(tipo: 'CAPA' | 'OG') {
    const file = tipo === 'CAPA' ? coverFile : ogFile
    if (!file || busy) return
    setBusy(true)
    setError(null)
    try {
      const uploaded = await enviarImagemBlogAdmin(file, tipo)
      if (tipo === 'CAPA') {
        update('imagemCapaId', uploaded.id)
        update('imagemUrl', uploaded.previewUrl)
        setCoverFile(null)
      } else {
        update('imagemOgId', uploaded.id)
        update('ogImageUrl', uploaded.previewUrl)
        setOgFile(null)
      }
      toast.success('Imagem editorial processada e armazenada com segurança.')
    } catch (uploadError) {
      setError(uploadError)
    } finally {
      setBusy(false)
    }
  }

  if (loading) {
    return <p className="py-10 text-center text-gray-500">Carregando post...</p>
  }

  return (
    <section className="space-y-6">
      <div className="flex flex-col justify-between gap-3 md:flex-row md:items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">
            {mode === 'create' ? 'Novo post' : 'Editar post'}
          </h1>
          <p className="mt-1 text-sm text-gray-600">
            Editor seguro, imagens, metadados e publicação editorial.
          </p>
          {post ? (
            <p className="mt-1 text-xs text-gray-500">
              Status: <strong>{post.status}</strong>
            </p>
          ) : null}
        </div>
        <Button asChild variant="outline">
          <Link href="/admin/blog">Voltar aos posts</Link>
        </Button>
      </div>

      {error ? <ContractState error={error} onRetry={load} /> : null}

      {categories.length === 0 ? (
        <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
          Cadastre uma categoria editorial antes de salvar o post.{' '}
          <Link href="/admin/blog/categorias" className="font-semibold underline">
            Gerenciar categorias
          </Link>
        </div>
      ) : null}

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="space-y-5 rounded-lg border border-gray-200 bg-white p-5">
          <div className="space-y-2">
            <Label htmlFor="post-title">Título</Label>
            <Input
              id="post-title"
              value={form.titulo}
              onChange={(event) => update('titulo', event.target.value)}
              placeholder="Digite o título do post"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="post-summary">Resumo</Label>
            <Textarea
              id="post-summary"
              rows={4}
              maxLength={320}
              value={form.resumo}
              onChange={(event) => update('resumo', event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="post-content">Conteúdo</Label>
            <Textarea
              id="post-content"
              rows={18}
              value={form.conteudo}
              onChange={(event) => update('conteudo', event.target.value)}
            />
            <p className="text-xs text-gray-500">
              Aceita parágrafos, títulos H2/H3, listas, ênfase e links HTTP(S) ou internos.
            </p>
          </div>
        </div>

        <aside className="space-y-5 rounded-lg border border-gray-200 bg-white p-5">
          <div className="space-y-2">
            <Label>Categoria</Label>
            <Select
              value={form.categoriaId}
              onValueChange={(value) => update('categoriaId', value)}
            >
              <SelectTrigger><SelectValue placeholder="Selecione" /></SelectTrigger>
              <SelectContent>
                {categories.filter((category) => category.ativa).map((category) => (
                  <SelectItem key={category.id} value={category.id}>
                    {category.nome}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Link href="/admin/blog/categorias" className="text-sm text-pink-700 underline">
              Gerenciar categorias
            </Link>
          </div>
          <div className="space-y-2">
            <Label htmlFor="post-author">Autor</Label>
            <Input
              id="post-author"
              value={form.autorNome}
              onChange={(event) => update('autorNome', event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="post-slug">Slug</Label>
            <Input
              id="post-slug"
              value={form.slug}
              onChange={(event) => update('slug', event.target.value)}
              placeholder="derivado-do-titulo"
            />
          </div>

          <ImageField
            label="Imagem destacada"
            previewUrl={form.imagemUrl}
            file={coverFile}
            setFile={setCoverFile}
            busy={busy}
            upload={() => void upload('CAPA')}
            remove={() => {
              update('imagemUrl', '')
              update('imagemCapaId', null)
              setCoverFile(null)
            }}
          />
          <ImageField
            label="Imagem OG"
            previewUrl={form.ogImageUrl}
            file={ogFile}
            setFile={setOgFile}
            busy={busy}
            upload={() => void upload('OG')}
            remove={() => {
              update('ogImageUrl', '')
              update('imagemOgId', null)
              setOgFile(null)
            }}
          />
        </aside>
      </div>

      <div className="rounded-lg border border-gray-200 bg-white p-5">
        <h2 className="mb-4 text-lg font-semibold">Metadados SEO</h2>
        <div className="grid gap-4 md:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="seo-title">SEO title</Label>
            <Input
              id="seo-title"
              maxLength={180}
              value={form.seoTitle}
              onChange={(event) => update('seoTitle', event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="seo-description">SEO description</Label>
            <Textarea
              id="seo-description"
              rows={3}
              maxLength={320}
              value={form.seoDescription}
              onChange={(event) => update('seoDescription', event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="sitemap-priority">Sitemap priority</Label>
            <Input
              id="sitemap-priority"
              type="number"
              min="0.1"
              max="1"
              step="0.1"
              value={form.sitemapPriority}
              onChange={(event) => update('sitemapPriority', event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label>Change frequency</Label>
            <Select
              value={form.changeFrequency}
              onValueChange={(value) => update('changeFrequency', value)}
            >
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="daily">daily</SelectItem>
                <SelectItem value="weekly">weekly</SelectItem>
                <SelectItem value="monthly">monthly</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
      </div>

      <div className="flex flex-wrap justify-end gap-2">
        <Button type="button" variant="outline" onClick={() => setPreviewOpen(true)}>
          Visualizar
        </Button>
        <Button
          type="button"
          variant="outline"
          disabled={busy || !form.categoriaId}
          onClick={() => void save(false)}
        >
          {busy ? 'Processando...' : post?.status === 'PUBLICADO' ? 'Salvar alterações' : 'Salvar rascunho'}
        </Button>
        {post?.status === 'PUBLICADO' ? (
          <Button
            type="button"
            variant="outline"
            disabled={busy}
            onClick={() => void changeStatus('retirar')}
          >
            Retirar de publicação
          </Button>
        ) : (
          <Button
            type="button"
            disabled={busy || !form.categoriaId}
            onClick={() => void save(true)}
          >
            Publicar
          </Button>
        )}
        {post && post.status !== 'ARQUIVADO' ? (
          <Button
            type="button"
            variant="destructive"
            disabled={busy}
            onClick={() => void changeStatus('arquivar')}
          >
            Arquivar
          </Button>
        ) : null}
      </div>

      <Dialog open={previewOpen} onOpenChange={setPreviewOpen}>
        <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>{form.titulo || 'Prévia do post'}</DialogTitle>
          </DialogHeader>
          {form.imagemUrl ? (
            <img
              src={form.imagemUrl}
              alt=""
              className="aspect-video w-full rounded-lg object-cover"
            />
          ) : null}
          <p className="font-medium text-gray-700">
            {form.resumo || 'Sem resumo preenchido.'}
          </p>
          <SafeBlogPostBody
            conteudo={form.conteudo || 'Sem conteúdo preenchido.'}
            className="max-w-none"
          />
        </DialogContent>
      </Dialog>
    </section>
  )
}

function ImageField({
  label,
  previewUrl,
  file,
  setFile,
  busy,
  upload,
  remove,
}: {
  label: string
  previewUrl: string
  file: File | null
  setFile: (file: File | null) => void
  busy: boolean
  upload: () => void
  remove: () => void
}) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      {previewUrl ? (
        <img src={previewUrl} alt="" className="aspect-video w-full rounded-lg object-cover" />
      ) : null}
      <Input
        type="file"
        accept="image/png,image/jpeg,image/webp"
        aria-label={`Selecionar ${label.toLowerCase()}`}
        onChange={(event) => setFile(event.target.files?.[0] || null)}
      />
      {file ? <p className="truncate text-xs text-gray-600">{file.name}</p> : null}
      <div className="flex gap-2">
        <Button type="button" variant="outline" disabled={!file || busy} onClick={upload}>
          Enviar imagem
        </Button>
        {previewUrl || file ? (
          <Button type="button" variant="ghost" disabled={busy} onClick={remove}>
            Remover
          </Button>
        ) : null}
      </div>
    </div>
  )
}
