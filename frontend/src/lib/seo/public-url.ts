import { FINAL_PRODUCTION_ORIGIN } from "@/lib/seo/search-indexing-policy"

export function getPublicSiteBaseUrl() {
  return (process.env.NEXT_PUBLIC_SITE_URL || FINAL_PRODUCTION_ORIGIN).replace(/\/$/, "")
}

export function publicRouteSegment(value: string) {
  return encodeURIComponent(value.trim().toLocaleLowerCase("pt-BR"))
}

export function buildPublicPath(...segments: string[]) {
  const path = segments
    .filter((segment) => segment.trim().length > 0)
    .map(publicRouteSegment)
    .join("/")

  return path ? `/${path}` : "/"
}

export function buildPublicUrl(path: string, page = 0) {
  const url = new URL(path, `${getPublicSiteBaseUrl()}/`)
  if (page > 0) url.searchParams.set("page", String(page))
  return url.toString()
}

// Geographic URLs are zero-based; /anuncios explicitly uses one-based URLs.
export function parsePublicPage(value?: string | string[], pageBase: 0 | 1 = 0) {
  if (value === undefined) return 0
  if (Array.isArray(value)) return null
  if (value === "") return null
  if (!/^(0|[1-9]\d*)$/.test(value)) return null

  const page = Number(value)
  return Number.isSafeInteger(page) && page >= pageBase ? page - pageBase : null
}

export function parsePublicOrderSeed(value?: string | string[]) {
  if (value === undefined) return undefined
  if (Array.isArray(value) || !/^-?\d+$/.test(value)) return null
  const seed = BigInt(value)
  if (seed < BigInt("-9223372036854775808") || seed > BigInt("9223372036854775807")) return null
  return seed.toString()
}

export function buildPublicPageHref(
  path: string,
  page: number,
  ordemSeed?: string,
  searchParams: Record<string, string | string[] | undefined> = {},
  pageBase: 0 | 1 = 0,
) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(searchParams)) {
    if (value !== undefined) {
      for (const item of Array.isArray(value) ? value : [value]) query.append(key, item)
    }
  }
  query.delete("page")
  if (page > 0) query.set("page", String(page + pageBase))
  if (ordemSeed !== undefined) query.set("ordemSeed", ordemSeed)
  const suffix = query.toString()
  return suffix ? `${path}?${suffix}` : path
}

export function isCleanPublicFirstPage(value: string | string[] | undefined, page: number) {
  return value === undefined && page === 0
}

export function isPublicPageOutOfRange(
  page: number,
  pagination: { totalPaginas: number },
) {
  return page > 0 && page >= Math.max(0, pagination.totalPaginas)
}
