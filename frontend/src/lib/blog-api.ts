import {
  apiErrorFromResponse,
  publicApiUrl,
  requireArrayPayload,
} from '@/lib/api-contract'

export const PUBLIC_BLOG_CACHE_TAG = 'public-blog'
export const PUBLIC_BLOG_REVALIDATE_SECONDS = 3600

export type BlogPostSummary = {
  id: string
  titulo: string
  slug: string
  resumo: string
  categoria: string
  categoriaId: string
  categoriaSlug: string
  imagemUrl?: string | null
  imagemCapaId?: string | null
  autorNome: string
  status: 'RASCUNHO' | 'PUBLICADO' | 'ARQUIVADO'
  seoTitle: string
  seoDescription: string
  ogImageUrl?: string | null
  imagemOgId?: string | null
  publishedAt?: string | null
  updatedAt?: string | null
  createdAt?: string | null
  versao: number
}

export type BlogCategoriaPublic = {
  id: string
  nome: string
  slug: string
  ordem: number
  ativa: boolean
  postCountPublicados: number
  versao: number
  updatedAt?: string | null
}

export type BlogPostDetail = BlogPostSummary & {
  conteudo: string
  sitemapPriority: number
  changeFrequency: 'daily' | 'weekly' | 'monthly'
}

async function request<T>(path: string): Promise<T> {
  const response = await fetch(publicApiUrl(path), {
    next: {
      revalidate: PUBLIC_BLOG_REVALIDATE_SECONDS,
      tags: [PUBLIC_BLOG_CACHE_TAG],
    },
    signal: AbortSignal.timeout(8000),
  })
  if (!response.ok) {
    throw await apiErrorFromResponse(response)
  }
  return (await response.json()) as T
}

export async function fetchPublicBlogPosts(): Promise<BlogPostSummary[]> {
  return requireArrayPayload<BlogPostSummary>(
    await request<unknown>('/blog-posts/public'),
  )
}

export async function fetchPublicBlogPost(slug: string): Promise<BlogPostDetail> {
  return request<BlogPostDetail>(`/blog-posts/public/${encodeURIComponent(slug)}`)
}

export async function fetchPublicBlogSitemap() {
  return requireArrayPayload<Record<string, unknown>>(
    await request<unknown>('/blog-posts/public/sitemap'),
  )
}

export async function fetchPublicBlogCategorias(): Promise<BlogCategoriaPublic[]> {
  return requireArrayPayload<BlogCategoriaPublic>(
    await request<unknown>('/blog-categorias/public'),
  )
}

export async function fetchPublicBlogPostsByCategoria(
  slug: string,
): Promise<BlogPostSummary[]> {
  return requireArrayPayload<BlogPostSummary>(
    await request<unknown>(
      `/blog-posts/public/categoria/${encodeURIComponent(slug)}`,
    ),
  )
}
