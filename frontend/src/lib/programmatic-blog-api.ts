import { BackendContractPendingError, PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

/** Temas da landing programática (deve coincidir com o backend). */
export const PROGRAMMATIC_BLOG_TEMAS = [
  "acompanhantes",
  "garotas-de-programa",
  "anuncios-adultos",
] as const

const TEMA_SET = new Set<string>(PROGRAMMATIC_BLOG_TEMAS)

/**
 * Converte path legado do backend (/blog/{tema}/{cidade}?uf=) para rota Next sem conflito com /blog/[slug].
 */
export function rewriteLegacyProgrammaticBlogPath(pathOrUrl: string): string {
  if (!pathOrUrl || pathOrUrl.startsWith("/blog/cidade/")) {
    return pathOrUrl
  }
  try {
    const withOrigin = pathOrUrl.startsWith("/") ? `https://internal.local${pathOrUrl}` : pathOrUrl
    const u = new URL(withOrigin)
    const parts = u.pathname.split("/").filter(Boolean)
    if (
      parts.length === 3 &&
      parts[0] === "blog" &&
      TEMA_SET.has(parts[1]) &&
      parts[2] &&
      parts[2] !== "cidade"
    ) {
      u.pathname = `/blog/cidade/${parts[1]}/${parts[2]}`
      const out = u.pathname + u.search + u.hash
      return pathOrUrl.startsWith("/") ? out : u.toString().replace("https://internal.local", "")
    }
  } catch {
    /* ignore */
  }
  return pathOrUrl
}

/** URL pública Next.js (padrão novo). Backend não precisa mudar. */
export function programmaticBlogPageUrl(tema: string, cidadeSlug: string, estadoUf: string): string {
  const uf = estadoUf.trim().toLowerCase()
  return `/blog/cidade/${encodeURIComponent(tema)}/${encodeURIComponent(cidadeSlug)}?uf=${encodeURIComponent(uf)}`
}

export type ProgrammaticBlogFaqItem = {
  pergunta: string
  resposta: string
}

export type ProgrammaticBlogPublic = {
  tema: string
  estadoUf: string
  cidadeSlug: string
  cidadeNome: string
  estadoNome: string
  title: string
  metaDescription: string | null
  h1: string
  contentHtml: string
  faq: ProgrammaticBlogFaqItem[]
  published: boolean
  indexed: boolean
  contentQualityOk: boolean
  canonicalPath: string
  cidadesRelacionadas: Array<{
    uf: string
    cidadeSlug: string
    cidadeNome: string
    hrefPath: string
  }>
  postsRelacionados: Array<{
    slug: string
    titulo: string
    categoria: string
  }>
  temasMesmaCidade: Array<{
    tema: string
    label: string
    hrefPath: string
  }>
  updatedAtIso: string | null
}

async function pendingBlogContract<T>(): Promise<T> {
  throw new BackendContractPendingError(PENDING_BACKEND_CONTRACTS.blog)
}

export async function fetchProgrammaticBlogPage(
  tema: string,
  cidadeSlug: string,
  uf?: string | null
): Promise<ProgrammaticBlogPublic | null> {
  void tema
  void cidadeSlug
  void uf
  return pendingBlogContract<ProgrammaticBlogPublic | null>()
}

export class ProgrammaticBlogAmbiguousError extends Error {
  constructor(public readonly ufs: string[] | undefined) {
    super("AMBIGUOUS_CIDADE")
    this.name = "ProgrammaticBlogAmbiguousError"
  }
}

export type ProgrammaticBlogHomeEntry = {
  tema: string
  temaLabel: string
  cidadeSlug: string
  estadoUf: string
  cidadeNome: string
  title: string
  h1: string
}

export async function fetchProgrammaticHomeEntries(
  limit = 48,
  tema?: string | null
): Promise<ProgrammaticBlogHomeEntry[]> {
  void limit
  void tema
  return pendingBlogContract<ProgrammaticBlogHomeEntry[]>()
}

export async function fetchProgrammaticSitemapEntries(): Promise<
  Array<{ path: string; lastmod?: string | null }>
> {
  return pendingBlogContract<Array<{ path: string; lastmod?: string | null }>>()
}
