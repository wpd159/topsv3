import type { MetadataRoute } from "next"
import { rewriteLegacyProgrammaticBlogPath } from "@/lib/programmatic-blog-api"
import { shouldIndexAnuncio } from "@/lib/seo/anuncio-indexing"
import {
  isBairroIndexavelLocal,
  isCidadeIndexavelLocal,
  totalAnunciosLocal,
} from "@/lib/seo/local-indexing"

function getBaseUrl() {
  const env = process.env.NEXT_PUBLIC_SITE_URL
  return (env ?? "https://topsdojob.com").replace(/\/$/, "")
}

function parseDate(value: any): Date | undefined {
  if (!value) return undefined
  const d = new Date(value)
  return isNaN(d.getTime()) ? undefined : d
}

function maxDate(a?: Date, b?: Date) {
  if (!a) return b
  if (!b) return a
  return a > b ? a : b
}

async function mapLimit<T, R>(
  items: T[],
  limit: number,
  fn: (item: T) => Promise<R>
): Promise<R[]> {
  const results: R[] = []
  let i = 0

  const workers = Array.from({ length: Math.max(1, limit) }, async () => {
    while (i < items.length) {
      const idx = i++
      results[idx] = await fn(items[idx])
    }
  })

  await Promise.all(workers)
  return results
}

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const baseUrl = getBaseUrl()
  const apiBase = (process.env.NEXT_PUBLIC_API_URL || "").replace(/\/$/, "")

  // Rotas realmente estáticas (pode deixar sem lastModified ou com um valor fixo se quiser)
  const staticRoutes: MetadataRoute.Sitemap = [
    { url: `${baseUrl}/`, priority: 1.0 },
    { url: `${baseUrl}/anuncios`, priority: 0.9 },
    { url: `${baseUrl}/acompanhantes`, priority: 0.95 },

    { url: `${baseUrl}/blog`, priority: 0.7 },
    { url: `${baseUrl}/creditos`, priority: 0.6 },
    { url: `${baseUrl}/faq`, priority: 0.6 },
    { url: `${baseUrl}/sobre`, priority: 0.5 },
    { url: `${baseUrl}/termos-de-uso`, priority: 0.4 },
    { url: `${baseUrl}/politica-de-privacidade`, priority: 0.4 },
    { url: `${baseUrl}/cookies`, priority: 0.4 },
    { url: `${baseUrl}/politicas/verificacao-etaria`, priority: 0.4 },
    { url: `${baseUrl}/consentimento-promocional`, priority: 0.4 },
    { url: `${baseUrl}/aviso-seguranca-whatsapp`, priority: 0.4 },
  ]

  if (!apiBase) return staticRoutes

  const urlSet = new Set<string>()

  const dynamicAnuncioRoutes: MetadataRoute.Sitemap = []
  const dynamicBlogRoutes: MetadataRoute.Sitemap = []
  const dynamicBlogCategoryRoutes: MetadataRoute.Sitemap = []
  const dynamicProgBlogRoutes: MetadataRoute.Sitemap = []
  const dynamicEstadoRoutes: MetadataRoute.Sitemap = []
  const dynamicCidadeRoutes: MetadataRoute.Sitemap = []
  const dynamicBairroRoutes: MetadataRoute.Sitemap = []

  let lastModAnunciosIndex: Date | undefined
  let lastModAcompanhantesIndex: Date | undefined
  let lastModBlogIndex: Date | undefined

  // ===== ANÚNCIOS (/anuncios/{slug}) =====
  try {
    const resAnuncios = await fetch(`${apiBase}/anuncios`, {
      next: { revalidate: 3600 },
      signal: AbortSignal.timeout(8000),
    })

    const dataAnuncios = resAnuncios.ok ? await resAnuncios.json().catch(() => []) : []
    const listAnuncios = Array.isArray(dataAnuncios)
      ? dataAnuncios
      : Array.isArray((dataAnuncios as any)?.content)
        ? (dataAnuncios as any).content
        : []

    for (const a of listAnuncios) {
      const slug = a?.slug
      const lm = parseDate(a?.atualizadoEm)

      if (!slug || typeof slug !== "string") continue
      if (!shouldIndexAnuncio(a)) continue

      const url = `${baseUrl}/anuncios/${encodeURIComponent(slug)}`
      if (urlSet.has(url)) continue
      urlSet.add(url)

      if (lm) lastModAnunciosIndex = maxDate(lastModAnunciosIndex, lm)

      dynamicAnuncioRoutes.push({
        url,
        lastModified: lm,
        priority: 0.8,
      })
    }
  } catch (e) {
    console.error("Erro ao buscar anúncios para sitemap:", e)
  }

  try {
    const resBlog = await fetch(`${apiBase}/blog-posts/public/sitemap`, {
      next: { revalidate: 3600 },
      signal: AbortSignal.timeout(8000),
    })

    const dataBlog = resBlog.ok ? await resBlog.json().catch(() => []) : []
    const listBlog = Array.isArray(dataBlog) ? dataBlog : []

    for (const post of listBlog) {
      const slug = post?.slug
      const lm = parseDate(post?.updatedAt || post?.publishedAt)

      if (!slug || typeof slug !== "string") continue

      const url = `${baseUrl}/blog/${encodeURIComponent(slug)}`
      if (urlSet.has(url)) continue
      urlSet.add(url)

      if (lm) lastModBlogIndex = maxDate(lastModBlogIndex, lm)

      dynamicBlogRoutes.push({
        url,
        lastModified: lm,
        priority: Number(post?.priority ?? 0.7),
      })
    }
  } catch (e) {
    console.error("Erro ao buscar posts do blog para sitemap:", e)
  }

  try {
    const resCategorias = await fetch(`${apiBase}/blog-categorias/public`, {
      next: { revalidate: 3600 },
      signal: AbortSignal.timeout(8000),
    })

    const dataCategorias = resCategorias.ok ? await resCategorias.json().catch(() => []) : []
    const listCategorias = Array.isArray(dataCategorias) ? dataCategorias : []

    for (const categoria of listCategorias) {
      const slug = categoria?.slug
      const lm = parseDate(categoria?.updatedAt || categoria?.atualizadoEm)

      if (!slug || typeof slug !== "string") continue

      const url = `${baseUrl}/blog/categoria/${encodeURIComponent(slug)}`
      if (urlSet.has(url)) continue
      urlSet.add(url)

      if (lm) lastModBlogIndex = maxDate(lastModBlogIndex, lm)

      dynamicBlogCategoryRoutes.push({
        url,
        lastModified: lm,
        priority: 0.65,
      })
    }
  } catch (e) {
    console.error("Erro ao buscar categorias do blog para sitemap:", e)
  }

  try {
    const resProg = await fetch(`${apiBase}/blog-programmatic/public/sitemap-entries`, {
      next: { revalidate: 3600 },
      signal: AbortSignal.timeout(8000),
    })
    const dataProg = resProg.ok ? await resProg.json().catch(() => []) : []
    const listProg = Array.isArray(dataProg) ? dataProg : []

    for (const row of listProg) {
      const rawPath = row?.path
      const lm = parseDate(row?.lastmod)
      if (!rawPath || typeof rawPath !== "string") continue
      const path = rewriteLegacyProgrammaticBlogPath(
        rawPath.startsWith("/") ? rawPath : `/${rawPath}`
      )
      const url = `${baseUrl}${path.startsWith("/") ? path : `/${path}`}`
      if (urlSet.has(url)) continue
      urlSet.add(url)
      if (lm) lastModBlogIndex = maxDate(lastModBlogIndex, lm)
      dynamicProgBlogRoutes.push({
        url,
        lastModified: lm,
        priority: 0.65,
      })
    }
  } catch (e) {
    console.error("Erro ao buscar páginas programáticas do blog para sitemap:", e)
  }

  // ===== CIDADES ATIVAS (gera /acompanhantes/{uf}/{cidade}) + monta mapa por estado =====
  const estadoMap = new Map<
    string,
    {
      uf: string
      ultimaAtualizacao?: Date
      cidades: Array<{ slug: string; lastMod?: Date }>
    }
  >()

  try {
    const resCidades = await fetch(`${apiBase}/anuncios/cidades-ativas`, {
      next: { revalidate: 3600 },
      signal: AbortSignal.timeout(8000),
    })

    const dataCidades = resCidades.ok ? await resCidades.json().catch(() => []) : []
    const listCidades = Array.isArray(dataCidades) ? dataCidades : []

    for (const c of listCidades) {
      const uf = c?.estadoUf
      const cidadeSlug = c?.cidadeSlug
      const lm = parseDate(c?.ultimaAtualizacao)

      if (!uf || !cidadeSlug || typeof uf !== "string" || typeof cidadeSlug !== "string") continue
      if (!isCidadeIndexavelLocal(c)) continue

      const cidadeUrl = `${baseUrl}/acompanhantes/${uf.toLowerCase()}/${cidadeSlug}`
      if (!urlSet.has(cidadeUrl)) {
        urlSet.add(cidadeUrl)
        dynamicCidadeRoutes.push({
          url: cidadeUrl,
          lastModified: lm,
          priority: totalAnunciosLocal(c) >= 20 ? 0.88 : 0.82,
        })
      }

      if (lm) lastModAcompanhantesIndex = maxDate(lastModAcompanhantesIndex, lm)

      if (!estadoMap.has(uf)) {
        estadoMap.set(uf, {
          uf,
          ultimaAtualizacao: lm,
          cidades: [{ slug: cidadeSlug, lastMod: lm }],
        })
      } else {
        const estado = estadoMap.get(uf)!
        estado.ultimaAtualizacao = maxDate(estado.ultimaAtualizacao, lm)

        if (!estado.cidades.some((x) => x.slug === cidadeSlug)) {
          estado.cidades.push({ slug: cidadeSlug, lastMod: lm })
        }
      }
    }
  } catch (e) {
    console.error("Erro ao buscar cidades para sitemap:", e)
  }

  // ===== ESTADOS (/acompanhantes/{uf}) =====
  for (const estado of estadoMap.values()) {
    const url = `${baseUrl}/acompanhantes/${estado.uf.toLowerCase()}`
    if (urlSet.has(url)) continue
    urlSet.add(url)

    dynamicEstadoRoutes.push({
      url,
      lastModified: estado.ultimaAtualizacao,
      priority: 0.9,
    })

    if (estado.ultimaAtualizacao) {
      lastModAcompanhantesIndex = maxDate(lastModAcompanhantesIndex, estado.ultimaAtualizacao)
    }
  }

  // ===== BAIRROS (/acompanhantes/{uf}/{cidade}/{bairro}) =====
  try {
    const tasks: Array<{ uf: string; cidadeSlug: string }> = []

    for (const [uf, estado] of estadoMap.entries()) {
      for (const cidade of estado.cidades) {
        tasks.push({ uf, cidadeSlug: cidade.slug })
      }
    }

    // limita concorrência pra não espancar a API
    const results = await mapLimit(tasks, 8, async ({ uf, cidadeSlug }) => {
      try {
        const resBairros = await fetch(
          `${apiBase}/anuncios/bairros-por-cidade/${encodeURIComponent(uf)}/${encodeURIComponent(cidadeSlug)}`,
          {
            next: { revalidate: 3600 },
            signal: AbortSignal.timeout(6000),
          }
        )

        const dataBairros = resBairros.ok ? await resBairros.json().catch(() => []) : []
        const listBairros = Array.isArray(dataBairros) ? dataBairros : []

        const bairroRoutes: MetadataRoute.Sitemap = []
        let localMax: Date | undefined

        for (const b of listBairros) {
          const bairroSlug = b?.bairroSlug
          const lm = parseDate(b?.ultimaAtualizacao)

          if (!bairroSlug || typeof bairroSlug !== "string") continue
          if (!isBairroIndexavelLocal(b)) continue

          const url = `${baseUrl}/acompanhantes/${uf.toLowerCase()}/${cidadeSlug}/${bairroSlug}`
          if (urlSet.has(url)) continue
          urlSet.add(url)

          localMax = maxDate(localMax, lm)
          bairroRoutes.push({
            url,
            lastModified: lm,
            priority: 0.8,
          })
        }

        return { uf, localMax, bairroRoutes }
      } catch {
        return { uf, localMax: undefined, bairroRoutes: [] as MetadataRoute.Sitemap }
      }
    })

    for (const r of results) {
      dynamicBairroRoutes.push(...r.bairroRoutes)

      if (r.localMax) {
        lastModAcompanhantesIndex = maxDate(lastModAcompanhantesIndex, r.localMax)
        const estado = estadoMap.get(r.uf)
        if (estado) estado.ultimaAtualizacao = maxDate(estado.ultimaAtualizacao, r.localMax)
      }
    }
  } catch (e) {
    console.error("Erro ao buscar bairros para sitemap:", e)
  }

  // ===== Ajusta lastModified das páginas índice com o MAX real =====
  const finalStaticRoutes: MetadataRoute.Sitemap = staticRoutes.map((r) => {
    if (r.url === `${baseUrl}/anuncios`) {
      return { ...r, lastModified: lastModAnunciosIndex }
    }
    if (r.url === `${baseUrl}/acompanhantes`) {
      return { ...r, lastModified: lastModAcompanhantesIndex }
    }
    if (r.url === `${baseUrl}/blog`) {
      return { ...r, lastModified: lastModBlogIndex }
    }
    if (r.url === `${baseUrl}/`) {
      // home pega o “mais recente do site”
      return {
        ...r,
        lastModified: maxDate(maxDate(lastModAnunciosIndex, lastModAcompanhantesIndex), lastModBlogIndex),
      }
    }
    return r
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
  ]
}
