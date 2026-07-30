export const FINAL_PRODUCTION_ORIGIN = "https://topsdojob.com"
export const SEARCH_INDEXING_MODE_ENV = "SEARCH_INDEXING_MODE"

export const NON_INDEXABLE_ROUTE_PREFIXES = [
  "/acesso-negado",
  "/admin",
  "/anunciar",
  "/api",
  "/chat",
  "/checkout",
  "/creditos",
  "/documentos",
  "/favoritos",
  "/internal",
  "/kyc",
  "/meus-anuncios",
  "/meus-tickets",
  "/minha-conta",
  "/painel",
  "/planos-e-creditos",
  "/preview",
  "/registrar",
  "/uploads",
  "/webhooks",
] as const

export const NON_INDEXABLE_QUERY_PATTERNS = [
  "/*?*filter=",
  "/*&filter=",
  "/*?*sort=",
  "/*&sort=",
  "/*?*search=",
  "/*&search=",
  "/*?*busca=",
  "/*&busca=",
  "/*?*utm_",
  "/*&utm_",
] as const

export const NEXT_NOINDEX_ROUTE_SOURCES = [
  "/acesso-negado",
  "/admin/:path*",
  "/anunciar/:path*",
  "/api/:path*",
  "/chat/:path*",
  "/checkout/:path*",
  "/creditos/:path*",
  "/documentos/:path*",
  "/favoritos/:path*",
  "/internal/:path*",
  "/kyc/:path*",
  "/meus-anuncios/:path*",
  "/meus-tickets/:path*",
  "/minha-conta/:path*",
  "/painel/:path*",
  "/planos-e-creditos",
  "/preview/:path*",
  "/registrar",
  "/uploads/:path*",
  "/webhooks/:path*",
] as const

type SearchIndexingEnvironment = Record<string, string | undefined>
export type SearchIndexingMode = "blocked" | "public"

export interface SearchIndexingPolicy {
  mode: SearchIndexingMode
  canonicalOrigin: string
  publicIndexingEnabled: boolean
  sitemapEnabled: boolean
  googlebotEnabled: boolean
  googleExtendedEnabled: boolean
  oaiSearchBotEnabled: boolean
  gptBotEnabled: boolean
  chatGptUserEnabled: boolean
  applebotEnabled: boolean
  bingbotEnabled: boolean
}

export interface SearchRobotsRule {
  userAgent: string
  allow?: string[]
  disallow: string[]
  crawlDelay?: number
}

function normalizeOrigin(rawValue: string) {
  let url: URL
  try {
    url = new URL(rawValue)
  } catch {
    throw new Error("NEXT_PUBLIC_SITE_URL deve ser uma origem absoluta valida.")
  }

  if (
    !["http:", "https:"].includes(url.protocol) ||
    url.username ||
    url.password ||
    (url.pathname !== "" && url.pathname !== "/") ||
    url.search ||
    url.hash
  ) {
    throw new Error("NEXT_PUBLIC_SITE_URL deve conter somente a origem canonica.")
  }

  return url.origin
}

export function resolveSearchIndexingPolicy(
  environment: SearchIndexingEnvironment = process.env,
): SearchIndexingPolicy {
  const configuredMode = environment[SEARCH_INDEXING_MODE_ENV]?.trim().toLowerCase()
  const mode = configuredMode || "blocked"

  if (mode !== "blocked" && mode !== "public") {
    throw new Error(`${SEARCH_INDEXING_MODE_ENV} deve ser blocked ou public.`)
  }

  const canonicalOrigin = normalizeOrigin(
    environment.NEXT_PUBLIC_SITE_URL || FINAL_PRODUCTION_ORIGIN,
  )

  if (mode === "public" && canonicalOrigin !== FINAL_PRODUCTION_ORIGIN) {
    throw new Error(
      `${SEARCH_INDEXING_MODE_ENV}=public exige o dominio final ${FINAL_PRODUCTION_ORIGIN}.`,
    )
  }

  const publicIndexingEnabled = mode === "public"
  return {
    mode,
    canonicalOrigin,
    publicIndexingEnabled,
    sitemapEnabled: publicIndexingEnabled,
    googlebotEnabled: publicIndexingEnabled,
    googleExtendedEnabled: publicIndexingEnabled,
    oaiSearchBotEnabled: publicIndexingEnabled,
    gptBotEnabled: publicIndexingEnabled,
    chatGptUserEnabled: publicIndexingEnabled,
    applebotEnabled: publicIndexingEnabled,
    bingbotEnabled: publicIndexingEnabled,
  }
}

export function buildCrawlerDisallowRules() {
  return [
    ...NON_INDEXABLE_ROUTE_PREFIXES.flatMap((prefix) => [prefix, `${prefix}/*`]),
    ...NON_INDEXABLE_QUERY_PATTERNS,
  ]
}

export function buildSearchRobotsRules(
  policy: SearchIndexingPolicy,
): SearchRobotsRule[] {
  if (!policy.publicIndexingEnabled) {
    return [{ userAgent: "*", disallow: ["/"] }]
  }

  const disallow = buildCrawlerDisallowRules()
  const publicCrawlerAgents = [
    "Googlebot",
    "Googlebot-Image",
    "Googlebot-Video",
    "Google-Extended",
    "OAI-SearchBot",
    "GPTBot",
    "ChatGPT-User",
    "Applebot",
    "bingbot",
    "*",
  ]

  return publicCrawlerAgents.map((userAgent) => ({
    userAgent,
    allow: ["/"],
    disallow,
  }))
}

export function isNonIndexableRoute(pathOrUrl: string) {
  let pathname: string
  try {
    pathname = new URL(pathOrUrl, `${FINAL_PRODUCTION_ORIGIN}/`).pathname
  } catch {
    return true
  }

  return NON_INDEXABLE_ROUTE_PREFIXES.some(
    (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`),
  )
}

export function isSafeSitemapUrl(urlValue: string, policy: SearchIndexingPolicy) {
  if (!policy.sitemapEnabled) return false

  try {
    const url = new URL(urlValue)
    return (
      url.origin === policy.canonicalOrigin &&
      !url.search &&
      !url.hash &&
      !isNonIndexableRoute(url.pathname)
    )
  } catch {
    return false
  }
}

export function buildPublicRobotsMetadata(indexable: boolean, richPreviews = false) {
  const policy = resolveSearchIndexingPolicy()
  if (!policy.publicIndexingEnabled) {
    return { index: false, follow: false, noarchive: true }
  }

  return {
    index: indexable,
    follow: true,
    ...(richPreviews
      ? {
          "max-image-preview": "large" as const,
          "max-snippet": -1,
          "max-video-preview": -1,
        }
      : {}),
  }
}
