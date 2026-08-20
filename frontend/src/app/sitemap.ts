import type { MetadataRoute } from "next"
import {
  fetchPublicBlogCategorias,
  fetchPublicBlogSitemap,
} from "@/lib/blog-api"
import {
  descobrirAnunciosIndexaveisSitemap,
  descobrirLocalidadesPublicas,
} from "@/lib/public-catalog-server-api"
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

function numberField(row: Record<string, unknown>, field: string) {
  const value = row[field]
  return typeof value === "number" && Number.isFinite(value) ? value : 0
}

async function buildEditorialSitemap(
  baseUrl: string,
): Promise<{
  index: MetadataRoute.Sitemap
  posts: MetadataRoute.Sitemap
  categories: MetadataRoute.Sitemap
  lastModified?: Date
}> {
  const [posts, categories] = await Promise.all([
    fetchPublicBlogSitemap(),
    fetchPublicBlogCategorias(),
  ])

  const postRoutes: MetadataRoute.Sitemap = []
  const categoryRoutes: MetadataRoute.Sitemap = []
  const postUrls = new Set<string>()
  const categoryUrls = new Set<string>()
  let lastModified: Date | undefined

  for (const post of posts) {
    const slug = textField(post, "slug")
    if (!slug) continue
    const url = `${baseUrl}/blog/${encodeURIComponent(slug)}`
    if (postUrls.has(url)) continue
    postUrls.add(url)
    const routeLastModified = parseDate(
      textField(post, "updatedAt") || textField(post, "publishedAt"),
    )
    lastModified = maxDate(lastModified, routeLastModified)
    const configuredPriority = numberField(post, "priority")
    postRoutes.push({
      url,
      lastModified: routeLastModified,
      priority: configuredPriority > 0 ? configuredPriority : 0.7,
    })
  }

  for (const category of categories) {
    const slug = textField(category, "slug")
    if (
      !slug ||
      category.ativa !== true ||
      numberField(category, "postCountPublicados") <= 0
    ) {
      continue
    }
    const url = `${baseUrl}/blog/categoria/${encodeURIComponent(slug)}`
    if (categoryUrls.has(url)) continue
    categoryUrls.add(url)
    const routeLastModified = parseDate(
      textField(category, "updatedAt") || textField(category, "atualizadoEm"),
    )
    lastModified = maxDate(lastModified, routeLastModified)
    categoryRoutes.push({ url, lastModified: routeLastModified, priority: 0.65 })
  }

  return {
    index: postRoutes.length > 0
      ? [{ url: `${baseUrl}/blog`, lastModified, priority: 0.7 }]
      : [],
    posts: postRoutes,
    categories: categoryRoutes,
    lastModified,
  }
}

function logEditorialSitemapFailure(error: unknown) {
  const status =
    typeof error === "object" && error && "status" in error
      ? (error as { status?: unknown }).status ?? null
      : null
  console.error("editorial_sitemap_unavailable", {
    name: error instanceof Error ? error.name : "UnknownError",
    status,
  })
}

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const indexingPolicy = resolveSearchIndexingPolicy()
  if (!indexingPolicy.sitemapEnabled) return []

  const baseUrl = getPublicSiteBaseUrl()
  const staticRoutes: MetadataRoute.Sitemap = [
    { url: `${baseUrl}/`, priority: 1.0 },
    { url: `${baseUrl}/anuncios`, priority: 0.9 },
    { url: `${baseUrl}/acompanhantes`, priority: 0.95 },
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
  let dynamicBlogIndexRoutes: MetadataRoute.Sitemap = []
  let dynamicBlogRoutes: MetadataRoute.Sitemap = []
  let dynamicBlogCategoryRoutes: MetadataRoute.Sitemap = []
  let lastModAnunciosIndex: Date | undefined
  let lastModAcompanhantesIndex: Date | undefined
  let lastModBlogIndex: Date | undefined

  const descoberta = await descobrirLocalidadesPublicas()

  for (const estado of descoberta.estados) {
    if (!estado.indexacao.indexavel || !estado.indexacao.canonica) continue
    const cidadesIndexaveis = estado.cidades.filter(
      (cidade) => cidade.indexacao.indexavel && cidade.indexacao.canonica,
    )

    const estadoLastMod = parseDate(estado.ultimaAtualizacao)
    const estadoUrl = buildPublicUrl(buildPublicPath("acompanhantes", estado.uf))
    urlSet.add(estadoUrl)
    dynamicEstadoRoutes.push({ url: estadoUrl, lastModified: estadoLastMod, priority: 0.9 })
    lastModAcompanhantesIndex = maxDate(lastModAcompanhantesIndex, estadoLastMod)

    for (const cidade of cidadesIndexaveis) {
      const cidadeLastMod = parseDate(cidade.ultimaAtualizacao)
      const cidadeUrl = buildPublicUrl(
        buildPublicPath("acompanhantes", estado.uf, cidade.slug),
      )
      if (!urlSet.has(cidadeUrl)) {
        urlSet.add(cidadeUrl)
        dynamicCidadeRoutes.push({
          url: cidadeUrl,
          lastModified: cidadeLastMod,
          priority: 0.85,
        })
      }
      lastModAcompanhantesIndex = maxDate(lastModAcompanhantesIndex, cidadeLastMod)

      for (const bairro of cidade.bairros) {
        if (!bairro.indexacao.indexavel || !bairro.indexacao.canonica) continue

        const bairroLastMod = parseDate(bairro.ultimaAtualizacao)
        const bairroUrl = buildPublicUrl(
          buildPublicPath("acompanhantes", estado.uf, cidade.slug, bairro.slug),
        )
        if (!urlSet.has(bairroUrl)) {
          urlSet.add(bairroUrl)
          dynamicBairroRoutes.push({
            url: bairroUrl,
            lastModified: bairroLastMod,
            priority: 0.8,
          })
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
    dynamicAnuncioRoutes.push({
      url: anuncioUrl,
      lastModified: anuncioLastMod,
      priority: 0.8,
    })
  }

  try {
    const editorial = await buildEditorialSitemap(baseUrl)
    dynamicBlogIndexRoutes = editorial.index
    dynamicBlogRoutes = editorial.posts
    dynamicBlogCategoryRoutes = editorial.categories
    lastModBlogIndex = editorial.lastModified
  } catch (error) {
    logEditorialSitemapFailure(error)
  }

  const finalStaticRoutes = staticRoutes.map((route) => {
    if (route.url === `${baseUrl}/anuncios`) {
      return { ...route, lastModified: lastModAnunciosIndex }
    }
    if (route.url === `${baseUrl}/acompanhantes`) {
      return { ...route, lastModified: lastModAcompanhantesIndex }
    }
    if (route.url === `${baseUrl}/`) {
      return {
        ...route,
        lastModified: maxDate(
          maxDate(lastModAnunciosIndex, lastModAcompanhantesIndex),
          lastModBlogIndex,
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
    ...dynamicBlogIndexRoutes,
    ...dynamicBlogRoutes,
    ...dynamicBlogCategoryRoutes,
  ].filter((route) => isSafeSitemapUrl(route.url, indexingPolicy))
}
