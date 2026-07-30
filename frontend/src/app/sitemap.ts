import type { MetadataRoute } from "next"
import { PUBLIC_BLOG_CACHE_TAG } from "@/lib/blog-api"
import { rewriteLegacyProgrammaticBlogPath } from "@/lib/programmatic-blog-api"
import {
  descobrirAnunciosIndexaveisSitemap,
  descobrirLocalidadesPublicas,
} from "@/lib/public-catalog-api"
import { isBairroIndexavelLocal, isCidadeIndexavelLocal } from "@/lib/seo/local-indexing"
import { buildPublicPath, buildPublicUrl, getPublicSiteBaseUrl } from "@/lib/seo/public-url"
import {
  isSafeSitemapUrl,
  resolveSearchIndexingPolicy,
} from "@/lib/seo/search-indexing-policy"

export const dynamic = "force-dynamic"

function parseDate(value: unknown): Date | undefined {
  if (typeof value !== "string" || !value) return undefined
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? undefined : parsed
}

function maxDate(a?: Date, b?: Date) {
  if (!a) return b
  if (!b) return a
  return a > b ? a : b
}

function textField(row: Record<string, unknown>, field: string) {
  const value = row[field]
  return typeof value === "string" ? value : undefined
}

async function fetchList(url: string, required = false) {
  const response = await fetch(url, {
    next: { revalidate: 3600, tags: [PUBLIC_BLOG_CACHE_TAG] },
    signal: AbortSignal.timeout(8000),
  })
  if (!response.ok) {
    if (required) {
      throw new Error(`Falha no contrato editorial do sitemap: ${response.status}`)
    }
    return [] as Record<string, unknown>[]
  }
  const payload: unknown = await response.json()
  if (!Array.isArray(payload)) {
    if (required) throw new Error("Resposta editorial invalida no sitemap")
    return [] as Record<string, unknown>[]
  }
  return payload as Record<string, unknown>[]
}

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const indexingPolicy = resolveSearchIndexingPolicy()
  if (!indexingPolicy.sitemapEnabled) return []

  const baseUrl = getPublicSiteBaseUrl()
  const apiBase = (process.env.NEXT_PUBLIC_API_URL || "").replace(/\/$/, "")
  const staticRoutes: MetadataRoute.Sitemap = [
    { url: `${baseUrl}/`, priority: 1.0 },
    { url: `${baseUrl}/anuncios`, priority: 0.9 },
    { url: `${baseUrl}/acompanhantes`, priority: 0.95 },
    { url: `${baseUrl}/blog`, priority: 0.7 },
    { url: `${baseUrl}/faq`, priority: 0.6 },
    { url: `${baseUrl}/sobre`, priority: 0.5 },
    { url: `${baseUrl}/termos-de-uso`, priority: 0.4 },
    { url: `${baseUrl}/politica-de-privacidade`, priority: 0.4 },
    { url: `${baseUrl}/cookies`, priority: 0.4 },
    { url: `${baseUrl}/politicas/verificacao-etaria`, priority: 0.4 },
    { url: `${baseUrl}/consentimento-promocional`, priority: 0.4 },
    { url: `${baseUrl}/aviso-seguranca-whatsapp`, priority: 0.4 },
  ]

  const urlSet = new Set(staticRoutes.map((route) => route.url))
  const dynamicAnuncioRoutes: MetadataRoute.Sitemap = []
  const dynamicEstadoRoutes: MetadataRoute.Sitemap = []
  const dynamicCidadeRoutes: MetadataRoute.Sitemap = []
  const dynamicBairroRoutes: MetadataRoute.Sitemap = []
  const dynamicBlogRoutes: MetadataRoute.Sitemap = []
  const dynamicBlogCategoryRoutes: MetadataRoute.Sitemap = []
  const dynamicProgBlogRoutes: MetadataRoute.Sitemap = []
  let lastModAnunciosIndex: Date | undefined
  let lastModAcompanhantesIndex: Date | undefined
  let lastModBlogIndex: Date | undefined

  const descoberta = await descobrirLocalidadesPublicas()

  for (const estado of descoberta.estados) {
    const cidadesIndexaveis = estado.cidades.filter((cidade) =>
      isCidadeIndexavelLocal({ totalAnunciosAtivos: cidade.totalAnunciosAtivos })
    )
    if (cidadesIndexaveis.length === 0) continue

    const estadoLastMod = parseDate(estado.ultimaAtualizacao)
    const estadoUrl = buildPublicUrl(buildPublicPath("acompanhantes", estado.uf))
    urlSet.add(estadoUrl)
    dynamicEstadoRoutes.push({ url: estadoUrl, lastModified: estadoLastMod, priority: 0.9 })
    lastModAcompanhantesIndex = maxDate(lastModAcompanhantesIndex, estadoLastMod)

    for (const cidade of cidadesIndexaveis) {
      const cidadeLastMod = parseDate(cidade.ultimaAtualizacao)
      const cidadeUrl = buildPublicUrl(buildPublicPath("acompanhantes", estado.uf, cidade.slug))
      if (!urlSet.has(cidadeUrl)) {
        urlSet.add(cidadeUrl)
        dynamicCidadeRoutes.push({
          url: cidadeUrl,
          lastModified: cidadeLastMod,
          priority: cidade.totalAnunciosAtivos >= 20 ? 0.88 : 0.82,
        })
      }
      lastModAcompanhantesIndex = maxDate(lastModAcompanhantesIndex, cidadeLastMod)

      for (const bairro of cidade.bairros) {
        if (!isBairroIndexavelLocal({ totalAnunciosAtivos: bairro.totalAnunciosAtivos })) continue

        const bairroLastMod = parseDate(bairro.ultimaAtualizacao)
        const bairroUrl = buildPublicUrl(
          buildPublicPath("acompanhantes", estado.uf, cidade.slug, bairro.slug)
        )
        if (!urlSet.has(bairroUrl)) {
          urlSet.add(bairroUrl)
          dynamicBairroRoutes.push({ url: bairroUrl, lastModified: bairroLastMod, priority: 0.8 })
        }
        lastModAcompanhantesIndex = maxDate(lastModAcompanhantesIndex, bairroLastMod)
      }
    }

  }

  for (const anuncio of await descobrirAnunciosIndexaveisSitemap()) {
    const anuncioUrl = buildPublicUrl(buildPublicPath("anuncios", anuncio.slug))
    if (urlSet.has(anuncioUrl)) continue
    urlSet.add(anuncioUrl)
    const anuncioLastMod = parseDate(anuncio.atualizadoEm)
    lastModAnunciosIndex = maxDate(lastModAnunciosIndex, anuncioLastMod)
    dynamicAnuncioRoutes.push({ url: anuncioUrl, lastModified: anuncioLastMod, priority: 0.8 })
  }

  if (apiBase) {
    try {
      for (const post of await fetchList(`${apiBase}/blog-posts/public/sitemap`, true)) {
        const slug = textField(post, "slug")
        if (!slug) continue
        const url = `${baseUrl}/blog/${encodeURIComponent(slug)}`
        if (urlSet.has(url)) continue
        urlSet.add(url)
        const lastModified = parseDate(textField(post, "updatedAt") || textField(post, "publishedAt"))
        lastModBlogIndex = maxDate(lastModBlogIndex, lastModified)
        dynamicBlogRoutes.push({ url, lastModified, priority: Number(post.priority ?? 0.7) })
      }

      for (const category of await fetchList(`${apiBase}/blog-categorias/public`, true)) {
        const slug = textField(category, "slug")
        if (!slug) continue
        const url = `${baseUrl}/blog/categoria/${encodeURIComponent(slug)}`
        if (urlSet.has(url)) continue
        urlSet.add(url)
        const lastModified = parseDate(
          textField(category, "updatedAt") || textField(category, "atualizadoEm")
        )
        lastModBlogIndex = maxDate(lastModBlogIndex, lastModified)
        dynamicBlogCategoryRoutes.push({ url, lastModified, priority: 0.65 })
      }

      for (const entry of await fetchList(`${apiBase}/blog-programmatic/public/sitemap-entries`)) {
        const rawPath = textField(entry, "path")
        if (!rawPath) continue
        const path = rewriteLegacyProgrammaticBlogPath(
          rawPath.startsWith("/") ? rawPath : `/${rawPath}`
        )
        const url = `${baseUrl}${path.startsWith("/") ? path : `/${path}`}`
        if (urlSet.has(url)) continue
        urlSet.add(url)
        const lastModified = parseDate(textField(entry, "lastmod"))
        lastModBlogIndex = maxDate(lastModBlogIndex, lastModified)
        dynamicProgBlogRoutes.push({ url, lastModified, priority: 0.65 })
      }
    } catch (error) {
      console.error("Falha ao montar as rotas de blog no sitemap.", error)
      throw error
    }
  }

  const finalStaticRoutes = staticRoutes.map((route) => {
    if (route.url === `${baseUrl}/anuncios`) return { ...route, lastModified: lastModAnunciosIndex }
    if (route.url === `${baseUrl}/acompanhantes`) {
      return { ...route, lastModified: lastModAcompanhantesIndex }
    }
    if (route.url === `${baseUrl}/blog`) return { ...route, lastModified: lastModBlogIndex }
    if (route.url === `${baseUrl}/`) {
      return {
        ...route,
        lastModified: maxDate(
          maxDate(lastModAnunciosIndex, lastModAcompanhantesIndex),
          lastModBlogIndex
        ),
      }
    }
    return route
  })

  return [
    ...finalStaticRoutes,
    ...dynamicEstadoRoutes,
    ...dynamicCidadeRoutes,
    ...dynamicBairroRoutes,
    ...dynamicAnuncioRoutes,
    ...dynamicBlogRoutes,
    ...dynamicBlogCategoryRoutes,
    ...dynamicProgBlogRoutes,
  ].filter((route) => isSafeSitemapUrl(route.url, indexingPolicy))
}
