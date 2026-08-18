"use client"

import Image from "next/image"
import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { VisitorVerificationModal } from "@/components/compliance/visitor-verification-modal"
import {
  fontePublicaSegura,
  imagemPublicaR2,
  type MidiaPublica,
} from "@/lib/media/public-media"
import { cn } from "@/lib/utils"
import { publicApiUrl } from "@/lib/api-contract"
import {
  AGE_VERIFICATION_CHANGED_EVENT,
  obterStatusVisitante,
} from "@/lib/compliance/visitor-access"

type SensitiveImageProps = {
  midia: MidiaPublica
  anuncioId?: string | number | null
  anuncioSlug?: string | null
  alt: string
  className?: string
  sizes?: string
  priority?: boolean
  fill?: boolean
  width?: number
  height?: number
  onVerificationSuccess?: () => void
  onImageClick?: () => void
  onAbrirPaginaDoAnuncio?: () => void
  onError?: () => void
}

export function SensitiveImage({
  midia,
  anuncioId,
  anuncioSlug,
  alt,
  className,
  sizes,
  priority = false,
  fill = false,
  width,
  height,
  onVerificationSuccess,
  onImageClick,
  onAbrirPaginaDoAnuncio,
  onError,
}: SensitiveImageProps) {
  const [verificationOpen, setVerificationOpen] = useState(false)
  const [erro, setErro] = useState(false)
  const [sessionAuthorized, setSessionAuthorized] = useState(midia.autorizada)
  const [loadedProtectedSource, setLoadedProtectedSource] = useState<string | null>(null)

  useEffect(() => {
    if (midia.visibilidadeMidia !== "RESTRITA_18") return
    let active = true
    const refresh = () => {
      void obterStatusVisitante()
        .then((status) => {
          if (!active) return
          setSessionAuthorized(Boolean(
            status.verified
              && (status.level === "REINFORCED" || status.level === "STRONG"),
          ))
          setErro(false)
        })
        .catch(() => {
          if (active) setSessionAuthorized(false)
        })
    }
    refresh()
    window.addEventListener(AGE_VERIFICATION_CHANGED_EVENT, refresh)
    return () => {
      active = false
      window.removeEventListener(AGE_VERIFICATION_CHANGED_EVENT, refresh)
    }
  }, [midia.visibilidadeMidia])

  const autorizada = midia.autorizada || sessionAuthorized
  const protegida = midia.visibilidadeMidia === "RESTRITA_18" && !autorizada
  const fonte = midia.visibilidadeMidia === "RESTRITA_18" && autorizada
    ? publicApiUrl(`/compliance/visitor/media/${encodeURIComponent(String(midia.id))}`)
    : fontePublicaSegura(midia)
  const fonteEhPreviewPublica =
    protegida &&
    !autorizada &&
    Boolean(midia.previewUrl) &&
    fonte === midia.previewUrl
  const otimizarImagemPublica =
    (midia.visibilidadeMidia === "LIVRE" || fonteEhPreviewPublica) &&
    Boolean(fonte && (fonte.startsWith("/") || /^https?:\/\//i.test(fonte)))
  const loadingProtectedMedia =
    midia.visibilidadeMidia === "RESTRITA_18" &&
    autorizada &&
    Boolean(fonte) &&
    loadedProtectedSource !== fonte

  const propsImagem = fill
    ? { fill: true as const, sizes: sizes ?? "(max-width: 768px) 100vw, 50vw" }
    : { width: width ?? 800, height: height ?? 1200 }

  return (
    <>
      <div className="relative h-full w-full">
        {fonte && !erro ? (
          <Image
            {...propsImagem}
            src={fonte}
            alt={alt}
            priority={priority}
            unoptimized={!otimizarImagemPublica || imagemPublicaR2(fonte)}
            className={cn("object-cover object-center transition duration-300", className)}
            onClick={protegida ? undefined : onImageClick}
            onLoad={() => {
              if (midia.visibilidadeMidia === "RESTRITA_18") {
                setLoadedProtectedSource(fonte)
              }
            }}
            onError={() => {
              if (midia.visibilidadeMidia === "RESTRITA_18") {
                setSessionAuthorized(false)
                setLoadedProtectedSource(null)
              }
              setErro(true)
              onError?.()
            }}
          />
        ) : (
          <div className={cn("flex h-full w-full items-center justify-center bg-zinc-950 text-center text-white/85", className)}>
            <div className="space-y-2 px-5">
              <p className="text-sm font-semibold">
                {protegida ? "Conteúdo disponível após confirmação de idade" : "Mídia indisponível"}
              </p>
              <p className="text-xs text-white/70">
                {protegida
                  ? "A mídia original permanece protegida."
                  : "Não foi possível carregar esta mídia."}
              </p>
            </div>
          </div>
        )}

        {loadingProtectedMedia ? (
          <div
            role="status"
            aria-live="polite"
            className="pointer-events-none absolute inset-0 z-10 flex items-center justify-center bg-zinc-950 text-white"
          >
            <span className="rounded-full bg-black/55 px-4 py-2 text-sm font-medium">
              Carregando conteúdo protegido...
            </span>
          </div>
        ) : null}

        {protegida ? (
          <div
            className="compliance-restricted-overlay"
            onClick={(event) => {
              event.preventDefault()
              event.stopPropagation()
              setVerificationOpen(true)
            }}
          >
            <div className="w-full max-w-sm space-y-3 text-white">
              <p className="text-base font-semibold leading-snug sm:text-lg">
                Conteúdo restrito apenas para maiores de 18 anos
              </p>
              <p className="text-sm leading-relaxed text-white/80">
                A mídia original só será solicitada após confirmação válida de idade.
              </p>
              <Button
                type="button"
                className="mt-1 h-10 w-full bg-[#FC1EAD] text-white transition-transform duration-200 hover:scale-[1.01] hover:bg-[#e01a9a]"
              >
                Confirmar maioridade
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
        ) : null}
      </div>

      <VisitorVerificationModal
        open={verificationOpen}
        level="REINFORCED"
        scope="MIDIA_RESTRITA"
        context={{
          anuncioId: anuncioId ?? undefined,
          route: anuncioSlug ? `/anuncios/${anuncioSlug}` : undefined,
          midiaId: String(midia.id),
        }}
        onOpenChange={setVerificationOpen}
        onVerified={() => {
          setLoadedProtectedSource(null)
          setSessionAuthorized(true)
          setErro(false)
          setVerificationOpen(false)
          onVerificationSuccess?.()
        }}
      />
    </>
  )
}

export default SensitiveImage
