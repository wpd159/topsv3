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
  if (value === undefined || value === "") return 0
  if (!/^(0|[1-9]\d*)$/.test(value)) return null

  const page = Number(value)
  return Number.isSafeInteger(page) ? page : null
}
