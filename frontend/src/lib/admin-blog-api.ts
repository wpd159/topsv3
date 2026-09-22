import {
  adminApiUrl,
  apiErrorFromResponse,
  requireArrayPayload,
} from '@/lib/api-contract'
import type {
  BlogCategoriaPublic,
  BlogPostDetail,
} from '@/lib/blog-api'

export type BlogPostInput = {
  categoriaId: string
  titulo: string
  slug: string
  resumo: string
  conteudo: string
  autorNome: string
  seoTitle: string
  seoDescription: string
  sitemapPriority: number
  changeFrequency: string
  imagemCapaId: string | null
  imagemOgId: string | null
  versao: number | null
}

export type BlogImagemUpload = {
  id: string
  tipo: 'CAPA' | 'OG'
  previewUrl: string
  mimeType: string
  sha256: string
  tamanhoBytes: number
  largura: number
  altura: number
}

export type BlogCategoriaInput = {
  nome: string
  slug: string
  ordem: number
  ativa: boolean
  versao: number | null
}

function antiForgeryCookieName() {
  return ['XSRF', 'TOKEN'].join('-')
}

function antiForgeryHeaderName() {
  return ['X', 'XSRF', 'TOKEN'].join('-')
}

function readAntiForgeryValue() {
  if (typeof document === 'undefined') return null
  const name = antiForgeryCookieName()
  const entry = document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith(`${name}=`))
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : null
}

async function antiForgeryValue() {
  let value = readAntiForgeryValue()
  if (value) return value
  await fetch(adminApiUrl('/auth/me'), {
    credentials: 'include',
    cache: 'no-store',
  })
  value = readAntiForgeryValue()
  return value
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  multipart = false,
): Promise<T> {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('accept', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    if (!multipart) headers.set('content-type', 'application/json')
    const csrf = await antiForgeryValue()
    if (csrf) headers.set(antiForgeryHeaderName(), csrf)
  }
  const response = await fetch(adminApiUrl(path), {
    ...init,
    headers,
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) {
    const error = await apiErrorFromResponse(response)
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && error.kind === 'TECHNICAL_FAILURE') {
      error.message = 'O serviço não confirmou a operação. Verifique o estado antes de tentar novamente.'
    }
    throw error
  }
  return (await response.json()) as T
}

export async function listarBlogPostsAdmin(filters?: {
  termo?: string
  status?: string
}) {
  const query = new URLSearchParams()
  if (filters?.termo?.trim()) query.set('termo', filters.termo.trim())
  if (filters?.status && filters.status !== 'TODOS') {
    query.set('status', filters.status)
  }
  const suffix = query.size ? `?${query.toString()}` : ''
  return requireArrayPayload<BlogPostDetail>(
    await request<unknown>(`/blog-posts${suffix}`),
  )
}

export function buscarBlogPostAdmin(id: string) {
  return request<BlogPostDetail>(`/blog-posts/${encodeURIComponent(id)}`)
}

export function criarBlogPostAdmin(input: BlogPostInput) {
  return request<BlogPostDetail>('/blog-posts', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function atualizarBlogPostAdmin(id: string, input: BlogPostInput) {
  return request<BlogPostDetail>(`/blog-posts/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function publicarBlogPostAdmin(id: string) {
  return request<BlogPostDetail>(
    `/blog-posts/${encodeURIComponent(id)}/publicar`,
    { method: 'POST' },
  )
}

export function retirarBlogPostAdmin(id: string) {
  return request<BlogPostDetail>(
    `/blog-posts/${encodeURIComponent(id)}/retirar`,
    { method: 'POST' },
  )
}

export function arquivarBlogPostAdmin(id: string) {
  return request<BlogPostDetail>(
    `/blog-posts/${encodeURIComponent(id)}/arquivar`,
    { method: 'POST' },
  )
}

export async function enviarImagemBlogAdmin(
  file: File,
  tipo: 'CAPA' | 'OG',
) {
  const body = new FormData()
  body.set('imagem', file)
  body.set('tipo', tipo)
  return request<BlogImagemUpload>(
    '/blog-posts/upload-image',
    { method: 'POST', body },
    true,
  )
}

export async function listarBlogCategoriasAdmin() {
  return requireArrayPayload<BlogCategoriaPublic>(
    await request<unknown>('/blog-categorias'),
  )
}

export function criarBlogCategoriaAdmin(input: BlogCategoriaInput) {
  return request<BlogCategoriaPublic>('/blog-categorias', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function atualizarBlogCategoriaAdmin(
  id: string,
  input: BlogCategoriaInput,
) {
  return request<BlogCategoriaPublic>(
    `/blog-categorias/${encodeURIComponent(id)}`,
    { method: 'PUT', body: JSON.stringify(input) },
  )
}

export function desativarBlogCategoriaAdmin(id: string) {
  return request<BlogCategoriaPublic>(
    `/blog-categorias/${encodeURIComponent(id)}`,
    { method: 'DELETE' },
  )
}
