"use client"

import Image from "next/image"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import { VisitorVerificationModal } from "@/components/compliance/visitor-verification-modal"
import {
  registrarEventoGA4,
  identificarContextoPagina,
  identificarDispositivo,
  identificarOrigemTrafego,
  identificarPathAtual,
} from "@/lib/analytics/ga4"
import {
  liberacaoMidiaRestritaVisitante,
  obterStatusVisitante,
  readMirroredVisitorStatus,
  type StatusVisitante,
} from "@/lib/compliance/visitor-access"

type SensitiveImageProps = {
  anuncioId?: number | null
  anuncioSlug?: string | null
  anuncioNome?: string | null
  cidade?: string | null
  contentClassification?: string | null
  src: string
  alt: string
  className?: string
  sizes?: string
  priority?: boolean
  fill?: boolean
  width?: number
  height?: number
  requiresVisitorVerification?: boolean
  requiresStrongVerification?: boolean
  viewerAuthorized?: boolean
  deferCompliancePreview?: boolean
  onUnlocked?: () => void
  onImageClick?: () => void
  /** Quando há overlay de +18, ainda permite abrir a página pública do anúncio (evita "card que não abre"). */
  onAbrirPaginaDoAnuncio?: () => void
  onVerificationSuccess?: () => void
  onError?: () => void
}

function normalizarClassificacao(value?: string | null) {
  return (value ?? "")
    .trim()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^A-Za-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toUpperCase()
}

function exigeProtecao(
  contentClassification?: string | null,
  requiresVisitorVerification?: boolean,
  requiresStrongVerification?: boolean
) {
  const classificacao = normalizarClassificacao(contentClassification)
  return (
    Boolean(requiresVisitorVerification) ||
    Boolean(requiresStrongVerification) ||
    classificacao === "ADULT_RESTRICTED" ||
    classificacao === "ADULTO_RESTRITO" ||
    classificacao === "ADULT_EXPLICIT_BLOCKED" ||
    classificacao === "ADULTO_EXPLICITO_BLOQUEADO"
  )
}

function exigeVerificacaoReforcada(
  contentClassification?: string | null,
  requiresStrongVerification?: boolean
) {
  const classificacao = normalizarClassificacao(contentClassification)
  return (
    Boolean(requiresStrongVerification) ||
    classificacao === "ADULT_EXPLICIT_BLOCKED" ||
    classificacao === "ADULTO_EXPLICITO_BLOQUEADO"
  )
}

export function resolverUrlsSensitiveImage(src: string) {
  if (!src) {
    return { previewSrc: src, originalSrc: src }
  }

  if (/\/compliance\/assets\/\d+\/stream(?:\?.*)?$/i.test(src)) {
    return {
      previewSrc: src.replace(/\/stream(\?.*)?$/i, "/preview"),
      originalSrc: src,
    }
  }

  if (/\/compliance\/assets\/\d+\/preview(?:\?.*)?$/i.test(src)) {
    return {
      previewSrc: src,
      originalSrc: src.replace(/\/preview(\?.*)?$/i, "/stream"),
    }
  }

  if (src.includes("-preview.")) {
    return {
      previewSrc: src,
      originalSrc: src.replace("-preview.", "-original."),
    }
  }

  if (src.includes("-original.")) {
    return {
      previewSrc: src.replace("-original.", "-preview."),
      originalSrc: src,
    }
  }

  return { previewSrc: src, originalSrc: src }
}

function isComplianceAssetUrl(src?: string | null) {
  if (!src) return false

  try {
    const parsed = new URL(src, "https://topsdojob.com")
    return (
      parsed.hostname.includes("backend.topsdojob.com") ||
      parsed.pathname.includes("/compliance/assets/")
    )
  } catch {
    return src.includes("/compliance/assets/")
  }
}

/** Evita aplicar filtros de compliance no container; só o elemento de imagem deve receber blur. */
function classNameSemBlurCompliance(className?: string) {
  if (!className?.trim()) return undefined
  const next = className
    .split(/\s+/)
    .filter((c) => c && c !== "blur-restrito" && c !== "blur-restrito-thumb")
    .join(" ")
    .trim()
  return next || undefined
}

export function SensitiveImage({
  anuncioId,
  anuncioSlug,
  anuncioNome,
  cidade,
  contentClassification,
  src,
  alt,
  className,
  sizes,
  priority = false,
  fill = false,
  width,
  height,
  requiresVisitorVerification = false,
  requiresStrongVerification = false,
  viewerAuthorized = false,
  deferCompliancePreview = false,
  onUnlocked,
  onImageClick,
  onAbrirPaginaDoAnuncio,
  onVerificationSuccess,
  onError,
}: SensitiveImageProps) {
  const [verificationOpen, setVerificationOpen] = useState(false)
  const [statusVisitante, setStatusVisitante] = useState<StatusVisitante | null>(() =>
    readMirroredVisitorStatus()
  )
  const [carregandoOriginal, setCarregandoOriginal] = useState(false)
  const [previewProtegidoIndisponivel, setPreviewProtegidoIndisponivel] = useState(false)
  const [clientePronto, setClientePronto] = useState(false)
  const [fallbackAtivo, setFallbackAtivo] = useState(false)

  const eventoViewRegistradoRef = useRef(false)
  const eventoUnlockRegistradoRef = useRef(false)
  const containerRef = useRef<HTMLDivElement | null>(null)

  const sensivel = useMemo(
    () =>
      exigeProtecao(contentClassification, requiresVisitorVerification, requiresStrongVerification),
    [contentClassification, requiresStrongVerification, requiresVisitorVerification]
  )
  const exigeReforcada = useMemo(
    () => exigeVerificacaoReforcada(contentClassification, requiresStrongVerification),
    [contentClassification, requiresStrongVerification]
  )

  const { previewSrc, originalSrc } = useMemo(() => resolverUrlsSensitiveImage(src), [src])

  useEffect(() => {
    setClientePronto(true)
  }, [])

  useEffect(() => {
    setPreviewProtegidoIndisponivel(false)
    setFallbackAtivo(false)
  }, [previewSrc, originalSrc])

  useEffect(() => {
    if (!sensivel) {
      setCarregandoOriginal(false)
      return
    }

    if (viewerAuthorized) {
      void obterStatusVisitante()
        .then(setStatusVisitante)
        .catch(() => setStatusVisitante(null))
      return
    }

    void obterStatusVisitante()
      .then(setStatusVisitante)
      .catch(() => setStatusVisitante(null))
  }, [sensivel, viewerAuthorized, originalSrc, previewSrc])

  const verificadoClientePersistido =
    !exigeReforcada && Boolean(statusVisitante?.verified)

  const verificadoCompleto = Boolean(
    viewerAuthorized ||
      verificadoClientePersistido ||
      statusVisitante?.explicitVerified ||
      statusVisitante?.explicitLevel === "STRONG" ||
      statusVisitante?.explicitLevel === "REINFORCED" ||
      statusVisitante?.level === "STRONG" ||
      statusVisitante?.level === "REINFORCED"
  )

  const liberacaoSessaoVisitante = liberacaoMidiaRestritaVisitante({
    viewerAuthorized,
    requiresStrongVerification: exigeReforcada,
    statusVisitante,
  })
  const imagemLiberada = !sensivel || liberacaoSessaoVisitante

  const exigeVerificacaoAntesDaImagem = sensivel
  const textoOverlay = "Conteúdo restrito apenas para maiores de 18 anos"

  const botaoOverlay = verificadoCompleto
    ? "Visualizar foto"
          : "Desbloquear conteúdo explícito"

  const contextoEvento = useMemo(
    () => ({
      anuncio_id: anuncioId ?? undefined,
      anuncio_slug: anuncioSlug ?? undefined,
      anuncio_nome: anuncioNome ?? undefined,
      cidade_anuncio: cidade ?? undefined,
      classificacao_conteudo: contentClassification ?? undefined,
      origem_trafego: identificarOrigemTrafego(),
      dispositivo: identificarDispositivo(),
      contexto_pagina: identificarContextoPagina(),
      pagina_path: identificarPathAtual(),
    }),
    [anuncioId, anuncioNome, anuncioSlug, cidade, contentClassification]
  )
  const chaveEventoBase = useMemo(
    () =>
      [
        anuncioId ?? "sem-id",
        anuncioSlug ?? "sem-slug",
        src,
        identificarPathAtual(),
      ].join(":"),
    [anuncioId, anuncioSlug, src]
  )

  useEffect(() => {
    if (!sensivel || !containerRef.current || eventoViewRegistradoRef.current) return

    const observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0]
        if (!entry?.isIntersecting || eventoViewRegistradoRef.current) return

        eventoViewRegistradoRef.current = true
        registrarEventoGA4("blur_image_view", contextoEvento, {
          dedupeKey: `view:${chaveEventoBase}`,
        })
        observer.disconnect()
      },
      { threshold: 0.45 }
    )

    observer.observe(containerRef.current)
    return () => observer.disconnect()
  }, [chaveEventoBase, contextoEvento, sensivel])

  const concluirDesbloqueio = useCallback(() => {
    setCarregandoOriginal(true)
    onUnlocked?.()
  }, [onUnlocked])

  const tentarDesbloquear = useCallback(() => {
    registrarEventoGA4("blur_image_click", contextoEvento)

    if (!sensivel) {
      concluirDesbloqueio()
      return
    }

    if (imagemLiberada) {
      onUnlocked?.()
      return
    }

    if (!exigeVerificacaoAntesDaImagem) {
      concluirDesbloqueio()
      return
    }

    if (!statusVisitante?.verified && !viewerAuthorized && !verificadoClientePersistido) {
      setVerificationOpen(true)
      return
    }

    if (verificadoCompleto) {
      concluirDesbloqueio()
      return
    }

    setVerificationOpen(true)
  }, [
    contextoEvento,
    concluirDesbloqueio,
    imagemLiberada,
    onUnlocked,
    exigeVerificacaoAntesDaImagem,
    sensivel,
    statusVisitante?.verified,
    verificadoCompleto,
    verificadoClientePersistido,
    viewerAuthorized,
  ])

  const srcAtualPrivado = sensivel && !imagemLiberada ? previewSrc : originalSrc
  const aguardarHidratacaoParaPreviewCompliance =
    sensivel &&
    !imagemLiberada &&
    isComplianceAssetUrl(srcAtualPrivado) &&
    deferCompliancePreview &&
    !clientePronto
  const mostrarEstadoRestritoNeutro =
    sensivel && !imagemLiberada && (previewProtegidoIndisponivel || aguardarHidratacaoParaPreviewCompliance)
  const mostrarImagemIndisponivel = fallbackAtivo

  const propsImagem = fill
    ? {
        fill: true as const,
        sizes: sizes ?? "(max-width: 768px) 100vw, 50vw",
      }
    : {
        width: width ?? 800,
        height: height ?? 1200,
      }

  return (
    <>
      <div ref={containerRef} className="relative h-full w-full">
        {mostrarEstadoRestritoNeutro ? (
          <div
            className={cn(
              "flex h-full w-full items-center justify-center bg-zinc-950 text-center text-white/85",
              classNameSemBlurCompliance(className)
            )}
          >
            <div className="space-y-2 px-5">
              <p className="text-sm font-semibold">Conteúdo restrito apenas para maiores de 18 anos</p>
              <p className="text-xs text-white/70">
                A prévia protegida não pôde ser carregada, mas a mídia original segue bloqueada.
              </p>
            </div>
          </div>
        ) : mostrarImagemIndisponivel ? (
          <div
            className={cn(
              "flex h-full w-full items-center justify-center bg-zinc-100 text-center text-zinc-600",
              classNameSemBlurCompliance(className)
            )}
          >
            <div className="space-y-1 px-4">
              <p className="text-sm font-semibold">Imagem indisponivel</p>
              <p className="text-xs text-zinc-500">Nao foi possivel carregar esta midia.</p>
            </div>
          </div>
        ) : (
          <Image
            {...propsImagem}
            src={srcAtualPrivado}
            alt={alt}
            priority={priority}
            unoptimized
            className={cn(
              "object-cover object-center transition duration-300",
              className
            )}
            onLoad={() => {
              if (!sensivel || !imagemLiberada || eventoUnlockRegistradoRef.current) {
                setCarregandoOriginal(false)
                return
              }

              eventoUnlockRegistradoRef.current = true
              setCarregandoOriginal(false)
              registrarEventoGA4("blur_image_unlock", contextoEvento, {
                dedupeKey: `unlock:${chaveEventoBase}`,
              })
            }}
            onError={() => {
              if (sensivel && !imagemLiberada) {
                setPreviewProtegidoIndisponivel(true)
                setCarregandoOriginal(false)
                onError?.()
                return
              }

              setCarregandoOriginal(false)
              if (!fallbackAtivo) {
                setFallbackAtivo(true)
              }
              onError?.()
            }}
            onClick={(event) => {
              if (sensivel && !imagemLiberada) {
                event.preventDefault()
                event.stopPropagation()
                return
              }

              onImageClick?.()
            }}
          />
        )}

        {sensivel && !imagemLiberada && (
          <div
            className="compliance-restricted-overlay"
            onClick={(event) => {
              event.preventDefault()
              event.stopPropagation()
              tentarDesbloquear()
            }}
          >
            <div className="w-full max-w-sm space-y-3 text-white">
              <p className="text-base font-semibold leading-snug sm:text-lg">{textoOverlay}</p>
              <p className="text-sm leading-relaxed text-white/80">
                A imagem original só pode ser exibida após a confirmação de sua idade.
              </p>
              <Button
                type="button"
                onClick={(event) => {
                  event.preventDefault()
                  event.stopPropagation()
                  tentarDesbloquear()
                }}
                className="mt-1 h-10 w-full bg-[#FC1EAD] text-white transition-transform duration-200 hover:scale-[1.01] hover:bg-[#e01a9a]"
              >
                {botaoOverlay}
              </Button>
              {onAbrirPaginaDoAnuncio ? (
                <button
                  type="button"
                  className="mt-3 w-full text-center text-sm font-medium text-white/75 underline underline-offset-2 hover:text-white"
                  onClick={(event) => {
                    event.preventDefault()
                    event.stopPropagation()
                    onAbrirPaginaDoAnuncio()
                  }}
                >
                  Abrir página do anúncio
                </button>
              ) : null}
            </div>
          </div>
        )}

        {carregandoOriginal && (
          <div className="absolute inset-0 z-20 flex items-center justify-center bg-black/35 backdrop-blur-[2px]">
            <div className="rounded-full border border-white/20 bg-black/55 px-4 py-2 text-xs font-medium text-white">
              Carregando imagem...
            </div>
          </div>
        )}
      </div>

      <VisitorVerificationModal
        open={verificationOpen}
        level={exigeReforcada ? "REINFORCED" : "LIGHT"}
        context={{
          anuncioId: anuncioId ?? undefined,
          route: anuncioSlug ? `/anuncios/${anuncioSlug}` : undefined,
          contentClassification: contentClassification ?? null,
        }}
        onOpenChange={setVerificationOpen}
        onVerified={(status) => {
          setStatusVisitante(status)
          onVerificationSuccess?.()

          const liberado = liberacaoMidiaRestritaVisitante({
            viewerAuthorized,
            requiresStrongVerification: exigeReforcada,
            statusVisitante: status,
          })

          if (liberado) {
            concluirDesbloqueio()
          }

          void obterStatusVisitante(true)
            .then(setStatusVisitante)
            .catch(() => {
              // Mantem o status retornado por /verify; evita reabrir a trava por corrida de cookie no mobile.
            })
        }}
      />
    </>
  )
}

export default SensitiveImage
