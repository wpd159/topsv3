import { BackendContractPendingError, PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export type BlogPostSummary = {
  id: number
  titulo: string
  slug: string
  resumo?: string | null
  categoria: string
  categoriaId?: number | null
  categoriaSlug?: string | null
  imagemUrl?: string | null
  autorNome: string
  status?: string
  seoTitle?: string | null
  seoDescription?: string | null
  ogImageUrl?: string | null
  publishedAt?: string | null
  updatedAt?: string | null
}

export type BlogCategoriaPublic = {
  id: number
  nome: string
  slug: string
  postCountPublicados: number
}

export type BlogPostDetail = BlogPostSummary & {
  conteudo: string
  sitemapPriority?: number | null
  changeFrequency?: string | null
  createdAt?: string | null
}

async function pendingBlogContract<T>(): Promise<T> {
  throw new BackendContractPendingError(PENDING_BACKEND_CONTRACTS.blog)
}

export async function fetchPublicBlogPosts(): Promise<BlogPostSummary[]> {
  return pendingBlogContract<BlogPostSummary[]>()
}

export async function fetchPublicBlogPost(slug: string): Promise<BlogPostDetail> {
  void slug
  return pendingBlogContract<BlogPostDetail>()
}

export async function fetchPublicBlogSitemap() {
  return pendingBlogContract<Array<Record<string, unknown>>>()
}

export async function fetchPublicBlogCategorias(): Promise<BlogCategoriaPublic[]> {
  return pendingBlogContract<BlogCategoriaPublic[]>()
}

export async function fetchPublicBlogPostsByCategoria(slug: string): Promise<BlogPostSummary[]> {
  void slug
  return pendingBlogContract<BlogPostSummary[]>()
}
