"use client"

type EventoGA4 = Record<string, string | number | boolean | null | undefined>
type EventoEnfileirado = {
  evento: string
  params: EventoGA4
  dedupeKey?: string
}

declare global {
  interface Window {
    gtag?: (...args: unknown[]) => void
  }
}

const filaEventos: EventoEnfileirado[] = []
const eventosDedupe = new Set<string>()
let timerFlush: number | null = null

function obterDedupeKey(evento: string, dedupeKey?: string) {
  return dedupeKey ? `${evento}:${dedupeKey}` : null
}

function gtagDisponivel() {
  return typeof window !== "undefined" && typeof window.gtag === "function"
}

function flushFilaGA4() {
  if (!gtagDisponivel()) return

  while (filaEventos.length > 0) {
    const item = filaEventos.shift()
    if (!item) continue
    window.gtag?.("event", item.evento, item.params)
  }

  if (timerFlush) {
    window.clearInterval(timerFlush)
    timerFlush = null
  }
}

function garantirTentativaFlush() {
  if (typeof window === "undefined" || timerFlush) return

  timerFlush = window.setInterval(() => {
    flushFilaGA4()
  }, 1000)
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
  params: EventoGA4 = {},
  options?: { dedupeKey?: string }
) {
  if (typeof window === "undefined") return

  const chaveDedupe = obterDedupeKey(evento, options?.dedupeKey)
  if (chaveDedupe && eventosDedupe.has(chaveDedupe)) {
    return
  }

  if (chaveDedupe) {
    eventosDedupe.add(chaveDedupe)
  }

  if (gtagDisponivel()) {
    window.gtag?.("event", evento, params)
    flushFilaGA4()
    return
  }

  filaEventos.push({
    evento,
    params,
    dedupeKey: options?.dedupeKey,
  })

  garantirTentativaFlush()
}
