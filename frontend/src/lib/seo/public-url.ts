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

export function parsePublicPage(value?: string) {
  if (value === undefined) return 0
  if (value === "") return null
  if (!/^(0|[1-9]\d*)$/.test(value)) return null

  const page = Number(value)
  return Number.isSafeInteger(page) ? page : null
}

export function isCleanPublicFirstPage(value: string | undefined, page: number) {
  return value === undefined && page === 0
}

export function isPublicPageOutOfRange(
  page: number,
  pagination: { totalPaginas: number },
) {
  return page > 0 && page >= Math.max(0, pagination.totalPaginas)
}
