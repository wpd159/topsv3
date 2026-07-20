'use client'

import { useState } from 'react'
import Link from 'next/link'

import {
  ContractState,
  PendingActionFeedback,
  usePendingContractActions,
} from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'
import type { BlogPostDetail } from '@/lib/blog-api'

type Props = {
  mode: 'create' | 'edit'
  initialPost?: BlogPostDetail | null
  postId?: string
}

type FormState = {
  titulo: string
  categoria: string
  autorNome: string
  slug: string
  status: string
  imagemUrl: string
  ogImageUrl: string
  resumo: string
  conteudo: string
  seoTitle: string
  seoDescription: string
  sitemapPriority: string
  changeFrequency: string
}

function initialState(post?: BlogPostDetail | null): FormState {
  const item = post as (BlogPostDetail & Record<string, unknown>) | null | undefined
  return {
    titulo: String(item?.titulo || ''),
    categoria: String(item?.categoria || ''),
    autorNome: String(item?.autorNome || ''),
    slug: String(item?.slug || ''),
    status: String(item?.status || 'RASCUNHO'),
    imagemUrl: String(item?.imagemUrl || ''),
    ogImageUrl: String(item?.ogImageUrl || ''),
    resumo: String(item?.resumo || ''),
    conteudo: String(item?.conteudo || ''),
    seoTitle: String(item?.seoTitle || ''),
    seoDescription: String(item?.seoDescription || ''),
    sitemapPriority: String(item?.sitemapPriority || '0.5'),
    changeFrequency: String(item?.changeFrequency || 'weekly'),
  }
}

export function BlogPostForm({ mode, initialPost, postId }: Props) {
  const [form, setForm] = useState<FormState>(() => initialState(initialPost))
  const [previewOpen, setPreviewOpen] = useState(false)
  const [coverFile, setCoverFile] = useState('')
  const [ogFile, setOgFile] = useState('')
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(
    PENDING_BACKEND_CONTRACTS.blog
  )

  function update<K extends keyof FormState>(field: K, value: FormState[K]) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  return (
    <section className="space-y-6">
      <div className="flex flex-col justify-between gap-3 md:flex-row md:items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{mode === 'create' ? 'Novo post' : 'Editar post'}</h1>
          <p className="mt-1 text-sm text-gray-600">Editor, imagens, metadados e publicação editorial.</p>
          {postId ? <p className="text-xs text-gray-500">ID técnico: {postId}</p> : null}
        </div>
        <Button asChild variant="outline"><Link href="/admin/blog">Voltar aos posts</Link></Button>
      </div>

      <ContractState error={error} />
      <PendingActionFeedback attemptedAction={attemptedAction} />

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="space-y-5 rounded-lg border border-gray-200 bg-white p-5">
          <div className="space-y-2"><Label htmlFor="post-title">Título</Label><Input id="post-title" value={form.titulo} onChange={(event) => update('titulo', event.target.value)} placeholder="Digite o título do post" /></div>
          <div className="space-y-2"><Label htmlFor="post-summary">Resumo</Label><Textarea id="post-summary" rows={4} value={form.resumo} onChange={(event) => update('resumo', event.target.value)} /></div>
          <div className="space-y-2"><Label htmlFor="post-content">Conteúdo</Label><Textarea id="post-content" rows={18} value={form.conteudo} onChange={(event) => update('conteudo', event.target.value)} /></div>
        </div>

        <aside className="space-y-5 rounded-lg border border-gray-200 bg-white p-5">
          <div className="space-y-2">
            <Label htmlFor="post-category">Categoria</Label>
            <Input id="post-category" value={form.categoria} onChange={(event) => update('categoria', event.target.value)} placeholder="Categoria editorial" />
            <Link href="/admin/blog/categorias" className="text-sm text-pink-700 underline">Gerenciar categorias</Link>
          </div>
          <div className="space-y-2"><Label htmlFor="post-author">Autor</Label><Input id="post-author" value={form.autorNome} onChange={(event) => update('autorNome', event.target.value)} /></div>
          <div className="space-y-2"><Label htmlFor="post-slug">Slug</Label><Input id="post-slug" value={form.slug} onChange={(event) => update('slug', event.target.value)} /></div>
          <div className="space-y-2"><Label>Status</Label><Select value={form.status} onValueChange={(value) => update('status', value)}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="RASCUNHO">Rascunho</SelectItem><SelectItem value="PUBLICADO">Publicado</SelectItem><SelectItem value="ARQUIVADO">Arquivado</SelectItem></SelectContent></Select></div>

          <div className="space-y-2">
            <Label htmlFor="cover-url">Imagem destacada</Label>
            <Input id="cover-url" value={form.imagemUrl} onChange={(event) => update('imagemUrl', event.target.value)} />
            <Input type="file" accept="image/png,image/jpeg,image/webp" aria-label="Selecionar imagem destacada" onChange={(event) => setCoverFile(event.target.files?.[0]?.name || '')} />
            {coverFile ? <p className="text-xs text-gray-600">Selecionada: {coverFile}</p> : null}
            <div className="flex gap-2">
              <Button type="button" variant="outline" onClick={() => runPendingAction('Enviar imagem destacada')}>Enviar imagem</Button>
              <Button type="button" variant="ghost" onClick={() => { update('imagemUrl', ''); setCoverFile('') }}>Remover imagem</Button>
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="og-url">Imagem OG</Label>
            <Input id="og-url" value={form.ogImageUrl} onChange={(event) => update('ogImageUrl', event.target.value)} />
            <Input type="file" accept="image/png,image/jpeg,image/webp" aria-label="Selecionar imagem OG" onChange={(event) => setOgFile(event.target.files?.[0]?.name || '')} />
            {ogFile ? <p className="text-xs text-gray-600">Selecionada: {ogFile}</p> : null}
            <div className="flex gap-2">
              <Button type="button" variant="outline" onClick={() => runPendingAction('Enviar imagem OG')}>Enviar imagem OG</Button>
              <Button type="button" variant="ghost" onClick={() => { update('ogImageUrl', ''); setOgFile('') }}>Remover imagem OG</Button>
            </div>
          </div>
        </aside>
      </div>

      <div className="rounded-lg border border-gray-200 bg-white p-5">
        <h2 className="mb-4 text-lg font-semibold">Metadados SEO existentes</h2>
        <div className="grid gap-4 md:grid-cols-2">
          <div className="space-y-2"><Label htmlFor="seo-title">SEO title</Label><Input id="seo-title" value={form.seoTitle} onChange={(event) => update('seoTitle', event.target.value)} /></div>
          <div className="space-y-2"><Label htmlFor="seo-description">SEO description</Label><Textarea id="seo-description" rows={3} value={form.seoDescription} onChange={(event) => update('seoDescription', event.target.value)} /></div>
          <div className="space-y-2"><Label htmlFor="sitemap-priority">Sitemap priority</Label><Input id="sitemap-priority" value={form.sitemapPriority} onChange={(event) => update('sitemapPriority', event.target.value)} /></div>
          <div className="space-y-2"><Label>Change frequency</Label><Select value={form.changeFrequency} onValueChange={(value) => update('changeFrequency', value)}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="daily">daily</SelectItem><SelectItem value="weekly">weekly</SelectItem><SelectItem value="monthly">monthly</SelectItem></SelectContent></Select></div>
        </div>
      </div>

      <div className="flex flex-wrap justify-end gap-2">
        <Button type="button" variant="outline" onClick={() => runPendingAction('Salvar como rascunho')}>Salvar como rascunho</Button>
        <Button type="button" variant="outline" onClick={() => setPreviewOpen(true)}>Visualizar</Button>
        <Button type="button" onClick={() => runPendingAction(mode === 'create' ? 'Criar post' : 'Salvar alterações')}>{mode === 'create' ? 'Criar post' : 'Salvar alterações'}</Button>
      </div>

      <Dialog open={previewOpen} onOpenChange={setPreviewOpen}>
        <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-3xl">
          <DialogHeader><DialogTitle>{form.titulo || 'Prévia do post'}</DialogTitle></DialogHeader>
          {form.imagemUrl ? <p className="text-sm text-gray-600">Imagem destacada: {form.imagemUrl}</p> : null}
          <p className="font-medium text-gray-700">{form.resumo || 'Sem resumo preenchido.'}</p>
          <div className="whitespace-pre-wrap text-sm text-gray-700">{form.conteudo || 'Sem conteúdo preenchido.'}</div>
        </DialogContent>
      </Dialog>
    </section>
  )
}
