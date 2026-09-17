"use client"

type EventoGA4 = Record<string, string | number | boolean | null | undefined>
export const GA_MEASUREMENT_ID = "G-E0CNBH6WPM"
export const GA_CONSENT_EVENT = "tops:cookie-consent-updated"
const PUBLIC_ORIGIN = "https://topsdojob.com"

declare global {
  interface Window {
    gtag?: (...args: unknown[]) => void
    dataLayer?: IArguments[]
  }
}

const eventosDedupe = new Set<string>()

function obterDedupeKey(evento: string, dedupeKey?: string) {
  return dedupeKey ? `${evento}:${dedupeKey}` : null
}

// Lista de contextos públicos, não uma política de indexação ou de acesso.
// Segmentos livres, títulos e parâmetros de busca nunca vão para a coleta.
function pathPublicoGA4(path: string) {
  if (/^\/(?:anuncios|acompanhantes|blog|contato|cookies|faq|sobre|politica-de-privacidade|termos-de-uso)?\/?$/.test(path)) {
    return path.replace(/\/$/, "") || "/"
  }
  if (/^\/anuncios\/[^/]+\/?$/.test(path)) return "/anuncios/[slug]"
  if (/^\/blog\/[^/]+\/?$/.test(path)) return "/blog/[slug]"
  if (/^\/blog\/categoria\/[^/]+\/?$/.test(path)) return "/blog/categoria/[slug]"
  if (/^\/blog\/cidade\/[^/]+\/[^/]+\/?$/.test(path)) return "/blog/cidade/[tema]/[cidade]"
  if (/^\/politicas\/(?:verificacao-etaria|termos-conteudo-restrito|privacidade-conteudo-restrito|aviso-legal-conteudo-restrito)\/?$/.test(path)) return path.replace(/\/$/, "")
  const localidade = path.match(/^\/acompanhantes\/[a-z]{2}(\/[^/]+)?(\/[^/]+)?\/?$/)
  if (localidade) return "/acompanhantes/[uf]" + (localidade[1] ? "/[cidade]" : "") + (localidade[2] ? "/[bairro]" : "")
  return null
}

export function analyticsPermitido() {
  if (typeof window === "undefined" || typeof document === "undefined") return false
  if (process.env.NODE_ENV !== "production" || process.env.NEXT_PUBLIC_ANALYTICS_ENABLED !== "true") return false
  if (window.location.origin !== PUBLIC_ORIGIN || !pathPublicoGA4(window.location.pathname)) return false
  try {
    const cookie = document.cookie.match(/(?:^|;\s*)cookie_consent=([^;]*)/)
    return Boolean(cookie && JSON.parse(decodeURIComponent(cookie[1]))?.analytics === true)
  } catch {
    return false
  }
}

export function contextoPaginaGA4() {
  const path = pathPublicoGA4(window.location.pathname) ?? "/"
  const params = new URLSearchParams(window.location.search)
  const page = params.get("page")
  const query = page && /^\d{1,6}$/.test(page) && /^\/(anuncios|acompanhantes)(\/|$)/.test(path)
    ? `?page=${page}` : ""
  let referrer = ""
  try {
    const url = new URL(document.referrer)
    // Referência externa limitada à origem; sem credenciais, caminho ou query.
    if (url.protocol === "https:" && url.origin !== PUBLIC_ORIGIN) referrer = url.origin
  } catch { /* Referência ausente ou inválida não é coletada. */ }
  return {
    page_location: `${PUBLIC_ORIGIN}${path}${query}`,
    page_path: `${path}${query}`,
    page_title: `Tops do Job | ${path}`,
    page_referrer: referrer,
  }
}

export function atualizarPrivacidadeGA4() {
  // O SDK consulta esta propriedade antes de enviar. O getter não espera o
  // efeito React: bloqueia também uma mudança de URL/cookie entre renders.
  Object.defineProperty(window, `ga-disable-${GA_MEASUREMENT_ID}`, {
    configurable: true,
    get: () => !analyticsPermitido(),
  })
  if (!analyticsPermitido()) {
    eventosDedupe.clear()
    // Preserve a identidade do dataLayer (e seu push do SDK), removendo apenas
    // eventos ainda enfileirados. Não repetir ações anteriores ao consentimento.
    const layer = window.dataLayer
    if (layer) {
      for (let index = layer.length - 1; index >= 0; index--) {
        if (layer[index]?.[0] === "event") layer.splice(index, 1)
      }
    }
  }
}

export function identificarOrigemTrafego() {
  if (typeof window === "undefined") return "desconhecida"

  const params = new URLSearchParams(window.location.search)
  const utmSource = params.get("utm_source")
  if (utmSource) return utmSource

  if (!document.referrer) return "direto"

  try {
    const referrer = new URL(document.referrer)
    return referrer.hostname || "referencia_externa"
  } catch {
    return "referencia_externa"
  }
}

export function identificarDispositivo() {
  if (typeof window === "undefined") return "desconhecido"

  const ua = window.navigator.userAgent.toLowerCase()
  if (/tablet|ipad/.test(ua)) return "tablet"
  if (/mobi|android|iphone/.test(ua)) return "mobile"
  return "desktop"
}

export function identificarPathAtual() {
  if (typeof window === "undefined") return "/"
  return window.location.pathname || "/"
}

export function identificarContextoPagina() {
  const path = identificarPathAtual()

  if (/^\/anuncios\/[^/]+$/i.test(path)) return "detalhe_anuncio"
  if (/^\/anuncios$/i.test(path)) return "listagem_anuncios"
  return "outra_pagina"
}

export function registrarEventoGA4(
  evento: string,
  _params: EventoGA4 = {},
  options?: { dedupeKey?: string }
) {
  if (!analyticsPermitido() || typeof window.gtag !== "function") return false
  if (evento !== "page_view" && evento !== "click_whatsapp") return false

  const chaveDedupe = obterDedupeKey(evento, options?.dedupeKey)
  if (chaveDedupe && eventosDedupe.has(chaveDedupe)) {
    return false
  }

  // Não propagar parâmetros arbitrários, slugs, telefone ou URL do WhatsApp.
  // O nome preservado mede intenção de clique, nunca contato ou pagamento.
  try {
    const payload = evento === "click_whatsapp"
      ? { event_category: "engagement", event_label: "card_anuncio", ...contextoPaginaGA4() }
      : contextoPaginaGA4()
    window.gtag("event", evento, payload)
    if (chaveDedupe) eventosDedupe.add(chaveDedupe)
    return true
  } catch {
    return false // Falha de telemetria não bloqueia o fluxo funcional.
  }
}
