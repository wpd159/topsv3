import 'server-only'

import { ApiContractError, requireArrayPayload } from '@/lib/api-contract'
import { publicServerApiJson } from '@/lib/public-server-api'

export const PUBLIC_BLOG_CACHE_TAG = 'public-blog'
export const PUBLIC_BLOG_REVALIDATE_SECONDS = 300

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

const BLOG_CACHE = {
  mode: 'revalidate',
  seconds: PUBLIC_BLOG_REVALIDATE_SECONDS,
  tags: [PUBLIC_BLOG_CACHE_TAG],
} as const

function requireObjectPayload<T>(payload: unknown): T {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
    throw new ApiContractError(
      'O servico editorial retornou um contrato incompativel.',
      'TECHNICAL_FAILURE',
      502,
      false,
    )
  }
  return payload as T
}

function request<T>(
  path: string,
  endpointFamily: string,
  validate: (payload: unknown) => T,
) {
  return publicServerApiJson(path, {
    endpointFamily,
    cache: BLOG_CACHE,
    validate,
  })
}

export async function fetchPublicBlogPosts(): Promise<BlogPostSummary[]> {
  return request(
    '/blog-posts/public',
    'blog.posts',
    (payload) => requireArrayPayload<BlogPostSummary>(payload),
  )
}

export async function fetchPublicBlogPost(slug: string): Promise<BlogPostDetail> {
  return request(
    `/blog-posts/public/${encodeURIComponent(slug)}`,
    'blog.post-detail',
    requireObjectPayload<BlogPostDetail>,
  )
}

export async function fetchPublicBlogSitemap() {
  return request(
    '/blog-posts/public/sitemap',
    'blog.sitemap',
    (payload) => requireArrayPayload<Record<string, unknown>>(payload),
  )
}

export async function fetchPublicBlogCategorias(): Promise<BlogCategoriaPublic[]> {
  return request(
    '/blog-categorias/public',
    'blog.categories',
    (payload) => requireArrayPayload<BlogCategoriaPublic>(payload),
  )
}

export async function fetchPublicBlogPostsByCategoria(
  slug: string,
): Promise<BlogPostSummary[]> {
  return request(
    `/blog-posts/public/categoria/${encodeURIComponent(slug)}`,
    'blog.posts-by-category',
    (payload) => requireArrayPayload<BlogPostSummary>(payload),
  )
}
