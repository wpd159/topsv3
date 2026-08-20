import { revalidarCacheCatalogoPublico } from "@/app/(painel-admin)/admin/anuncios/actions"
import type { IndexNowEventType, IndexNowPublicEvent } from "@/lib/seo/indexnow"

function normalizarBaseUrl(value?: string) {
  return (value ?? "").trim().replace(/\/$/, "")
}

function slugify(value?: string | null) {
  if (!value) return ""
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9\s-]/g, "")
    .replace(/\s+/g, "-")
    .replace(/-+/g, "-")
}

export type IndexNowAnuncioContext = {
  slug?: string | null
  estadoUf?: string | null
  cidadeNome?: string | null
  bairroNome?: string | null
}

type IndexNowAnuncioEventInput = {
  eventType: IndexNowEventType
  previous?: IndexNowAnuncioContext | null
  current?: IndexNowAnuncioContext | null
  changeFingerprint?: string | number | null
}

type IndexNowAnunciosEventInput = {
  eventType: IndexNowEventType
  previous?: IndexNowAnuncioContext[]
  current?: IndexNowAnuncioContext[]
  changeFingerprint?: string | number | null
}

function urlsForContext(siteUrl: string, context?: IndexNowAnuncioContext | null) {
  const urls = new Set<string>()
  if (!context) return urls
  if (context.slug) urls.add(`${siteUrl}/anuncios/${encodeURIComponent(context.slug)}`)

  const uf = (context.estadoUf || "").toLowerCase()
  const cidadeSlug = slugify(context.cidadeNome)
  const bairroSlug = slugify(context.bairroNome)
  if (uf) urls.add(`${siteUrl}/acompanhantes/${uf}`)
  if (uf && cidadeSlug) urls.add(`${siteUrl}/acompanhantes/${uf}/${cidadeSlug}`)
  if (uf && cidadeSlug && bairroSlug) {
    urls.add(`${siteUrl}/acompanhantes/${uf}/${cidadeSlug}/${bairroSlug}`)
  }
  return urls
}

function contextFingerprint(context?: IndexNowAnuncioContext | null) {
  if (!context) return "none"
  return [
    context.slug || "",
    (context.estadoUf || "").toLowerCase(),
    slugify(context.cidadeNome),
    slugify(context.bairroNome),
  ].join("|")
}

export function anuncioEstaPublicamenteIndexavel(status?: string | null) {
  return status === "PUBLICADO"
}

export function montarEventoIndexNowAnuncio({
  eventType,
  previous,
  current,
  changeFingerprint,
}: IndexNowAnuncioEventInput): IndexNowPublicEvent | null {
  return montarEventoIndexNowAnuncios({
    eventType,
    previous: previous ? [previous] : [],
    current: current ? [current] : [],
    changeFingerprint,
  })
}

export function montarEventoIndexNowAnuncios({
  eventType,
  previous = [],
  current = [],
  changeFingerprint,
}: IndexNowAnunciosEventInput): IndexNowPublicEvent | null {
  const siteUrl = normalizarBaseUrl(
    process.env.NEXT_PUBLIC_SITE_URL || (typeof window !== "undefined" ? window.location.origin : "")
  )
  if (!siteUrl) return null

  const urls = new Set<string>([`${siteUrl}/anuncios`, `${siteUrl}/acompanhantes`])
  previous.forEach((context) => urlsForContext(siteUrl, context).forEach((url) => urls.add(url)))
  current.forEach((context) => urlsForContext(siteUrl, context).forEach((url) => urls.add(url)))

  const eventFingerprint = [
    eventType,
    previous.map(contextFingerprint).sort().join(";"),
    current.map(contextFingerprint).sort().join(";"),
    changeFingerprint == null ? "" : String(changeFingerprint),
  ].join(":")
  return { eventType, eventFingerprint, urls: Array.from(urls) }
}

export function enviarIndexNowNoCliente(event: IndexNowPublicEvent | null) {
  if (!event || event.urls.length === 0) return
  void revalidarCacheCatalogoPublico(event).catch(() => {
    // A mutation de negocio ja foi concluida; IndexNow permanece best-effort.
  })
}
