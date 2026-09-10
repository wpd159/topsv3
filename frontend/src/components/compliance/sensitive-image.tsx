"use client"

import Image from "next/image"
import { useEffect, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { RestrictedMediaOverlay } from "@/components/compliance/restricted-media-overlay"
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
  obterGeracaoStatusVisitante,
  obterStatusVisitante,
  statusSatisfazEscopo,
  type StatusVisitante,
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
  const [verificationContext, setVerificationContext] = useState<string | null>(null)
  const [erro, setErro] = useState(false)
  // A public media DTO is not proof of a verified visitor session.
  const [sessionStatus, setSessionStatus] = useState<{
    status: StatusVisitante
    generation: number
  } | null>(null)
  const [loadedProtectedSource, setLoadedProtectedSource] = useState<string | null>(null)
  const statusCheckedAfterError = useRef(false)
  const contextKey = JSON.stringify([anuncioId, anuncioSlug, midia.id, midia.visibilidadeMidia])
  const verificationOpen = verificationContext === contextKey
  const contextRef = useRef(contextKey)
  contextRef.current = contextKey
  const requestSequence = useRef(0)
  const refreshAfterError = useRef<(() => void) | null>(null)

  useEffect(() => {
    if (midia.visibilidadeMidia !== "RESTRITA_18") return
    let active = true
    const refresh = (force = false) => {
      const sequence = ++requestSequence.current
      const generation = obterGeracaoStatusVisitante()
      const request = force ? obterStatusVisitante(true) : obterStatusVisitante()
      void request
        .then((status) => {
          if (!active || contextRef.current !== contextKey
            || sequence !== requestSequence.current
            || generation !== obterGeracaoStatusVisitante()) return
          setSessionStatus({ status, generation })
          if (!force) setErro(false)
        })
        .catch(() => {
          // Falha ao consultar o status não revoga uma autorização já conhecida.
        })
    }
    const changed = () => {
      setSessionStatus(null)
      refresh()
    }
    refreshAfterError.current = () => refresh(true)
    refresh()
    window.addEventListener(AGE_VERIFICATION_CHANGED_EVENT, changed)
    return () => {
      active = false
      requestSequence.current += 1
      refreshAfterError.current = null
      window.removeEventListener(AGE_VERIFICATION_CHANGED_EVENT, changed)
    }
  }, [contextKey, midia.visibilidadeMidia])

  useEffect(() => {
    if (!sessionStatus || !statusSatisfazEscopo(sessionStatus.status, "MIDIA_RESTRITA", "REINFORCED")) return
    const expiresAt = Date.parse(sessionStatus.status.expiresAt!)
    const timer = window.setTimeout(() => setSessionStatus(null),
      Math.min(Math.max(0, expiresAt - Date.now()), 2_147_483_647))
    return () => window.clearTimeout(timer)
  }, [sessionStatus])

  useEffect(() => {
    setErro(false)
    setLoadedProtectedSource(null)
    statusCheckedAfterError.current = false
    setVerificationContext(null)
  }, [contextKey, midia.autorizada, midia.urlPublica])

  // GENERAL/REINFORCED is a session grant, not tied to a gallery click. Keep a
  // confirmed, unexpired grant across media changes, but never a DTO boolean.
  const autorizada = midia.visibilidadeMidia === "LIVRE"
    ? midia.autorizada
    : Boolean(sessionStatus
      && sessionStatus.generation === obterGeracaoStatusVisitante()
      && statusSatisfazEscopo(sessionStatus.status, "MIDIA_RESTRITA", "REINFORCED"))
  const protegida = midia.visibilidadeMidia === "RESTRITA_18" && !autorizada
  const fonte = midia.visibilidadeMidia === "RESTRITA_18" && autorizada
    ? publicApiUrl(`/compliance/visitor/media/${encodeURIComponent(String(midia.id))}`)
    : fontePublicaSegura({ ...midia, autorizada })
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
              if (contextRef.current !== contextKey) return
              if (midia.visibilidadeMidia === "RESTRITA_18") {
                setLoadedProtectedSource(fonte)
              }
            }}
            onError={() => {
              if (contextRef.current !== contextKey) return
              if (midia.visibilidadeMidia === "RESTRITA_18") {
                setLoadedProtectedSource(null)
                if (!statusCheckedAfterError.current) {
                  statusCheckedAfterError.current = true
                  refreshAfterError.current?.()
                }
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
              {!protegida ? (
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    statusCheckedAfterError.current = false
                    setErro(false)
                  }}
                >
                  Tentar novamente
                </Button>
              ) : null}
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
          <RestrictedMediaOverlay
            onConfirm={() => setVerificationContext(contextKey)}
            onAbrirPaginaDoAnuncio={onAbrirPaginaDoAnuncio}
          />
        ) : null}
      </div>

      <VisitorVerificationModal
        key={contextKey}
        open={verificationOpen}
        level="REINFORCED"
        scope="MIDIA_RESTRITA"
        context={{
          anuncioId: anuncioId ?? undefined,
          route: anuncioSlug ? `/anuncios/${anuncioSlug}` : undefined,
          midiaId: String(midia.id),
        }}
        onOpenChange={(open) => {
          if (contextRef.current === contextKey) setVerificationContext(open ? contextKey : null)
        }}
        onVerified={(status) => {
          if (contextRef.current !== contextKey) return
          requestSequence.current += 1
          setLoadedProtectedSource(null)
          setSessionStatus({ status, generation: obterGeracaoStatusVisitante() })
          setErro(false)
          statusCheckedAfterError.current = false
          setVerificationContext(null)
          onVerificationSuccess?.()
        }}
      />
    </>
  )
}

export default SensitiveImage
