import type { NextConfig } from "next"
import {
  NEXT_NOINDEX_ROUTE_SOURCES,
  resolveSearchIndexingPolicy,
} from "./src/lib/seo/search-indexing-policy"

function toRemotePattern(origin?: string | null) {
  if (!origin) return null

  try {
    const url = new URL(origin)
    return {
      protocol: url.protocol.replace(":", "") as "http" | "https",
      hostname: url.hostname,
      ...(url.port ? { port: url.port } : {}),
    }
  } catch {
    return null
  }
}

function toOrigin(pattern: { protocol: "http" | "https"; hostname: string; port?: string }) {
  return `${pattern.protocol}://${pattern.hostname}${pattern.port ? `:${pattern.port}` : ""}`
}

type RemotePattern = NonNullable<ReturnType<typeof toRemotePattern>>

const publicR2HostnamePattern = /^pub-[0-9a-f]{32}\.r2\.dev$/i

function toConfiguredPublicR2Pattern(origin?: string | null): RemotePattern | null {
  if (!origin) return null

  let url: URL
  try {
    url = new URL(origin)
  } catch {
    throw new Error("R2_PUBLIC_BASE_URL deve ser uma origem HTTPS valida.")
  }

  const hasOnlyOriginPath = url.pathname === "" || url.pathname === "/"
  if (
    url.protocol !== "https:" ||
    !publicR2HostnamePattern.test(url.hostname) ||
    url.username ||
    url.password ||
    url.port ||
    !hasOnlyOriginPath ||
    url.search ||
    url.hash
  ) {
    throw new Error(
      "R2_PUBLIC_BASE_URL deve apontar para uma origem publica r2.dev em HTTPS, sem caminho, credenciais, porta, query ou fragmento."
    )
  }

  return { protocol: "https", hostname: url.hostname.toLowerCase() }
}

function uniqueRemotePatterns(patterns: RemotePattern[]) {
  const seen = new Set<string>()
  return patterns.filter((pattern) => {
    const key = `${pattern.protocol}://${pattern.hostname}:${pattern.port ?? ""}`
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

const dynamicImageOrigins = [
  "http://localhost:3000",
  "http://127.0.0.1:3000",
  "http://localhost:8080",
  "http://127.0.0.1:8080",
  "https://topsdojob.com",
  "https://backend.topsdojob.com",
  process.env.NEXT_PUBLIC_SITE_URL,
  process.env.NEXT_PUBLIC_API_URL,
]
  .map(toRemotePattern)
  .filter((item): item is NonNullable<ReturnType<typeof toRemotePattern>> => Boolean(item))

const dynamicOrigins = Array.from(new Set(dynamicImageOrigins.map(toOrigin)))
const analyticsEnabled = process.env.NEXT_PUBLIC_ANALYTICS_ENABLED !== "false"
const forceHttps = process.env.NEXT_PUBLIC_FORCE_HTTPS !== "false"
const searchIndexingPolicy = resolveSearchIndexingPolicy()
const securityOrigins =
  process.env.NODE_ENV === "production"
    ? Array.from(
        new Set(
          dynamicImageOrigins
            .filter(({ hostname }) => hostname !== "localhost" && hostname !== "127.0.0.1")
            .map(toOrigin)
        )
      )
    : dynamicOrigins
const configuredPublicR2Pattern = toConfiguredPublicR2Pattern(process.env.R2_PUBLIC_BASE_URL)
// A origem publica da producao segue ativa e precisa permanecer compativel no cutover.
const productionPublicR2Pattern: RemotePattern = {
  protocol: "https",
  hostname: "pub-567428d3703244d483815a05a1e0e0d9.r2.dev",
}
const publicR2Patterns = uniqueRemotePatterns(
  [productionPublicR2Pattern, configuredPublicR2Pattern].filter(
    (pattern): pattern is RemotePattern => Boolean(pattern)
  )
)
const publicR2Origins = publicR2Patterns.map(toOrigin)
// R2 private bucket presigned URLs (docs) commonly use the account endpoint:
// https://<accountId>.r2.cloudflarestorage.com/...
const r2CloudflareStorageWildcard = "https://*.r2.cloudflarestorage.com"

function toWsOrigin(origin?: string | null) {
  if (!origin) return null

  try {
    const url = new URL(origin)
    const protocol = url.protocol === "https:" ? "wss" : "ws"
    return `${protocol}://${url.hostname}${url.port ? `:${url.port}` : ""}`
  } catch {
    return null
  }
}

const connectOrigins = Array.from(
  new Set(
    [
      "'self'",
      ...securityOrigins,
      toWsOrigin(process.env.NEXT_PUBLIC_API_URL),
      r2CloudflareStorageWildcard,
      ...(analyticsEnabled
        ? [
            "https://www.googletagmanager.com",
            "https://www.google-analytics.com",
            "https://region1.google-analytics.com",
          ]
        : []),
      "https://vitals.vercel-insights.com",
      "https://va.vercel-scripts.com",
      "https://nominatim.openstreetmap.org",
      "https://overpass-api.de",
    ].filter((item): item is string => Boolean(item))
  )
)

const mediaOrigins = Array.from(
  new Set([
    "'self'",
    "blob:",
    "data:",
    ...securityOrigins,
    ...publicR2Origins,
    r2CloudflareStorageWildcard,
  ])
)

const imageOrigins = Array.from(
  new Set([
    "'self'",
    "data:",
    "blob:",
    ...securityOrigins,
    ...publicR2Origins,
    r2CloudflareStorageWildcard,
    "https://images.unsplash.com",
    "https://images.pexels.com",
    "https://cdn.pixabay.com",
    "https://www.google.com",
    ...(analyticsEnabled
      ? [
          "https://www.google-analytics.com",
          "https://region1.google-analytics.com",
          "https://www.googletagmanager.com",
        ]
      : []),
  ])
)

const frameOrigins = Array.from(
  new Set([
    "'self'",
    "blob:",
    "data:",
    ...securityOrigins,
    r2CloudflareStorageWildcard,
    "https://www.google.com",
    "https://www.youtube.com",
    "https://player.vimeo.com",
  ])
)

const securityHeaders = [
  {
    key: "Content-Security-Policy",
    value: [
      "default-src 'self'",
      "base-uri 'self'",
      "object-src 'none'",
      "frame-ancestors 'self'",
      `script-src 'self' 'unsafe-inline'${analyticsEnabled ? " https://www.googletagmanager.com" : ""} https://va.vercel-scripts.com`,
      "style-src 'self' 'unsafe-inline'",
      `img-src ${imageOrigins.join(" ")}`,
      `media-src ${mediaOrigins.join(" ")}`,
      `connect-src ${connectOrigins.join(" ")}`,
      `frame-src ${frameOrigins.join(" ")}`,
      "font-src 'self' data:",
      "worker-src 'self' blob:",
      "form-action 'self'",
      process.env.NODE_ENV === "production" && forceHttps ? "upgrade-insecure-requests" : "",
    ]
      .filter(Boolean)
      .join("; "),
  },
  { key: "X-Frame-Options", value: "SAMEORIGIN" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  {
    key: "Permissions-Policy",
    value: "camera=(), microphone=(), payment=(), usb=(), accelerometer=(), gyroscope=()",
  },
  ...(forceHttps
    ? [
        {
          key: "Strict-Transport-Security",
          value: "max-age=31536000; includeSubDomains; preload",
        },
      ]
    : []),
]

const nextConfig: NextConfig = {
  output: "standalone",

  async redirects() {
    const temas = ["acompanhantes", "garotas-de-programa", "anuncios-adultos"] as const
    return temas.map((tema) => ({
      source: `/blog/${tema}/:cidade`,
      destination: `/blog/cidade/${tema}/:cidade`,
      permanent: true,
    }))
  },

  images: {
    minimumCacheTTL: 3600,
    remotePatterns: uniqueRemotePatterns([
      ...dynamicImageOrigins,
      { protocol: "https", hostname: "images.unsplash.com" },
      { protocol: "https", hostname: "images.pexels.com" },
      { protocol: "https", hostname: "cdn.pixabay.com" },
      { protocol: "https", hostname: "cebkahlbbdmvzhfaruad.supabase.co" },
      ...publicR2Patterns,
    ]),
  },

  async headers() {
    const noindex = [
      { key: "X-Robots-Tag", value: "noindex, nofollow, noarchive" },
    ]

    if (!searchIndexingPolicy.publicIndexingEnabled) {
      return [{ source: "/:path*", headers: [...securityHeaders, ...noindex] }]
    }

    return [
      { source: "/:path*", headers: securityHeaders },
      ...NEXT_NOINDEX_ROUTE_SOURCES.map((source) => ({ source, headers: noindex })),
    ]
  },
}

export default nextConfig
