import { corrigirEstruturaTexto } from "@/lib/text/encoding"

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

function rewriteProgrammaticLinksInHtml(html: string): string {
  return html.replace(
    /href="(\/blog\/(?:acompanhantes|garotas-de-programa|anuncios-adultos)\/[^"?]+(?:\?[^"]*)?)"/g,
    (_, p1: string) => `href="${rewriteLegacyProgrammaticBlogPath(p1)}"`
  )
}

function normalizeProgrammaticPublicPayload(raw: ProgrammaticBlogPublic): ProgrammaticBlogPublic {
  return {
    ...raw,
    canonicalPath: rewriteLegacyProgrammaticBlogPath(raw.canonicalPath),
    cidadesRelacionadas: raw.cidadesRelacionadas.map((c) => ({
      ...c,
      hrefPath: rewriteLegacyProgrammaticBlogPath(c.hrefPath),
    })),
    temasMesmaCidade: raw.temasMesmaCidade.map((t) => ({
      ...t,
      hrefPath: rewriteLegacyProgrammaticBlogPath(t.hrefPath),
    })),
    contentHtml: rewriteProgrammaticLinksInHtml(raw.contentHtml),
  }
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

function apiUrl(path: string) {
  const base = (process.env.NEXT_PUBLIC_API_URL || "").replace(/\/$/, "")
  return `${base}${path.startsWith("/") ? "" : "/"}${path}`
}

export async function fetchProgrammaticBlogPage(
  tema: string,
  cidadeSlug: string,
  uf?: string | null
): Promise<ProgrammaticBlogPublic | null> {
  const q =
    uf && uf.length > 0
      ? `?uf=${encodeURIComponent(uf)}`
      : ""
  const res = await fetch(
    apiUrl(`/blog-programmatic/public/page/${encodeURIComponent(tema)}/${encodeURIComponent(cidadeSlug)}${q}`),
    {
      next: { revalidate: 3600 },
      credentials: "include",
    }
  )
  if (res.status === 404) return null
  if (res.status === 400) {
    const err = await res.json().catch(() => null)
    throw new ProgrammaticBlogAmbiguousError(err?.ufs as string[] | undefined)
  }
  if (!res.ok) return null
  const raw = corrigirEstruturaTexto(await res.json()) as ProgrammaticBlogPublic
  return normalizeProgrammaticPublicPayload(raw)
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
  const q = new URLSearchParams()
  q.set("limit", String(Math.min(120, Math.max(1, limit))))
  if (tema?.trim()) q.set("tema", tema.trim())
  const res = await fetch(apiUrl(`/blog-programmatic/public/home-entries?${q.toString()}`), {
    cache: "no-store",
    credentials: "include",
  })
  if (!res.ok) return []
  const data = await res.json().catch(() => [])
  const list = Array.isArray(data) ? data : []
  return corrigirEstruturaTexto(list) as ProgrammaticBlogHomeEntry[]
}

export async function fetchProgrammaticSitemapEntries(): Promise<
  Array<{ path: string; lastmod?: string | null }>
> {
  const res = await fetch(apiUrl("/blog-programmatic/public/sitemap-entries"), {
    next: { revalidate: 3600 },
    signal: AbortSignal.timeout(8000),
  })
  if (!res.ok) return []
  const data = await res.json().catch(() => [])
  const list = Array.isArray(data) ? data : []
  return list.map((row: { path?: string; lastmod?: string | null }) => {
    const p = row.path
    return {
      ...row,
      path: p ? rewriteLegacyProgrammaticBlogPath(p) : "",
    }
  })
}
