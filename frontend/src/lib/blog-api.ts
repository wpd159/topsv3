import { corrigirEstruturaTexto } from "@/lib/text/encoding"

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

function apiUrl(path: string) {
  const base = (process.env.NEXT_PUBLIC_API_URL || "").replace(/\/$/, "")
  return `${base}${path.startsWith("/") ? "" : "/"}${path}`
}

export async function fetchPublicBlogPosts(): Promise<BlogPostSummary[]> {
  const res = await fetch(apiUrl("/blog-posts/public"), {
    cache: "no-store",
    credentials: "include",
  })
  if (!res.ok) {
    throw new Error("Falha ao carregar posts do blog.")
  }
  const data = await res.json()
  return corrigirEstruturaTexto(Array.isArray(data) ? data : [])
}

export async function fetchPublicBlogPost(slug: string): Promise<BlogPostDetail> {
  const res = await fetch(apiUrl(`/blog-posts/public/${slug}`), {
    cache: "no-store",
    credentials: "include",
  })
  if (!res.ok) {
    throw new Error("Falha ao carregar o post.")
  }
  return corrigirEstruturaTexto(await res.json())
}

export async function fetchPublicBlogSitemap() {
  const res = await fetch(apiUrl("/blog-posts/public/sitemap"), {
    cache: "no-store",
    credentials: "include",
  })
  if (!res.ok) return []
  const data = await res.json()
  return corrigirEstruturaTexto(Array.isArray(data) ? data : [])
}

export async function fetchPublicBlogCategorias(): Promise<BlogCategoriaPublic[]> {
  const res = await fetch(apiUrl("/blog-categorias/public"), {
    cache: "no-store",
    credentials: "include",
  })
  if (!res.ok) return []
  const data = await res.json().catch(() => [])
  return corrigirEstruturaTexto(Array.isArray(data) ? data : [])
}

export async function fetchPublicBlogPostsByCategoria(slug: string): Promise<BlogPostSummary[]> {
  const s = (slug || "").trim()
  if (!s) return []
  const res = await fetch(apiUrl(`/blog-posts/public/categoria/${encodeURIComponent(s)}`), {
    cache: "no-store",
    credentials: "include",
  })
  if (!res.ok) {
    throw new Error("Falha ao carregar posts da categoria.")
  }
  const data = await res.json()
  return corrigirEstruturaTexto(Array.isArray(data) ? data : [])
}
