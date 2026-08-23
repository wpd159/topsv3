"use client"

import { useEffect, useRef, useState } from "react"
import { ArrowPathIcon, PlayIcon } from "@heroicons/react/24/solid"
import { Button } from "@/components/ui/button"
import { RestrictedMediaOverlay } from "@/components/compliance/restricted-media-overlay"
import { VisitorVerificationModal } from "@/components/compliance/visitor-verification-modal"
import { publicApiUrl } from "@/lib/api-contract"
import {
  AGE_VERIFICATION_CHANGED_EVENT,
  obterStatusVisitante,
  statusSatisfazEscopo,
} from "@/lib/compliance/visitor-access"
import {
  fontePublicaSegura,
  mimeTypeVideoDeclaravel,
  type MidiaPublica,
} from "@/lib/media/public-media"
import { cn } from "@/lib/utils"

type SensitiveVideoProps = {
  midia: MidiaPublica
  anuncioId?: string | number | null
  anuncioSlug?: string | null
  className?: string
  thumbnail?: boolean
  onVerificationSuccess?: () => void
  onAuthorizationChange?: (authorized: boolean) => void
  onAbrirPaginaDoAnuncio?: () => void
  preload?: "none" | "metadata" | "auto"
  onVideoElementChange?: (video: HTMLVideoElement | null) => void
  onPlay?: (video: HTMLVideoElement) => void
  onPause?: (video: HTMLVideoElement) => void
  onEnded?: (video: HTMLVideoElement) => void
  onError?: (video: HTMLVideoElement) => void
  onRetry?: () => void
}

export function SensitiveVideo({
  midia,
  anuncioId,
  anuncioSlug,
  className,
  thumbnail = false,
  onVerificationSuccess,
  onAuthorizationChange,
  onAbrirPaginaDoAnuncio,
  preload = "metadata",
  onVideoElementChange,
  onPlay,
  onPause,
  onEnded,
  onError,
  onRetry,
}: SensitiveVideoProps) {
  const [verificationOpen, setVerificationOpen] = useState(false)
  const [sessionAuthorized, setSessionAuthorized] = useState(midia.autorizada)
  const [mediaError, setMediaError] = useState(false)
  const [retryKey, setRetryKey] = useState(0)
  const statusCheckedAfterError = useRef(false)
  const restricted = midia.visibilidadeMidia === "RESTRITA_18"

  useEffect(() => {
    setMediaError(false)
    setRetryKey(0)
    statusCheckedAfterError.current = false
    if (midia.autorizada) setSessionAuthorized(true)
  }, [midia.autorizada, midia.id, midia.urlPublica])

  useEffect(() => {
    if (thumbnail || !restricted) return
    let active = true
    const refresh = () => {
      void obterStatusVisitante()
        .then((status) => {
          if (!active) return
          setSessionAuthorized(statusSatisfazEscopo(
            status,
            "MIDIA_RESTRITA",
            "REINFORCED",
          ))
        })
        .catch(() => {
          // Falha de status não revoga uma autorização já conhecida.
        })
    }
    refresh()
    window.addEventListener(AGE_VERIFICATION_CHANGED_EVENT, refresh)
    return () => {
      active = false
      window.removeEventListener(AGE_VERIFICATION_CHANGED_EVENT, refresh)
    }
  }, [restricted, thumbnail])

  const authorized = midia.autorizada || sessionAuthorized
  const blocked = restricted && !authorized

  useEffect(() => {
    if (thumbnail) return
    onAuthorizationChange?.(authorized)
  }, [authorized, onAuthorizationChange, thumbnail])

  const source = thumbnail || blocked
    ? null
    : restricted
      ? publicApiUrl(`/compliance/visitor/media/${encodeURIComponent(String(midia.id))}`)
      : fontePublicaSegura(midia)

  const checkAuthoritativeStatusAfterError = () => {
    if (!restricted || statusCheckedAfterError.current) return
    statusCheckedAfterError.current = true
    void obterStatusVisitante(true)
      .then((status) => {
        setSessionAuthorized(statusSatisfazEscopo(
          status,
          "MIDIA_RESTRITA",
          "REINFORCED",
        ))
      })
      .catch(() => {
        // Rede, 404, 5xx ou erro de codec não revogam o token.
      })
  }

  const retry = () => {
    statusCheckedAfterError.current = false
    onRetry?.()
    setMediaError(false)
    setRetryKey((current) => current + 1)
  }

  if (thumbnail) {
    return (
      <div
        className={cn(
          "flex h-full w-full items-center justify-center bg-zinc-950 text-white",
          className,
        )}
        role="img"
        aria-label="Miniatura de vídeo"
      >
        <span className="flex h-9 w-9 items-center justify-center rounded-full bg-black/70 ring-1 ring-white/25">
          <PlayIcon className="h-5 w-5" aria-hidden="true" />
        </span>
      </div>
    )
  }

  return (
    <>
      <div className="relative h-full w-full bg-zinc-950">
        {source && !mediaError ? (
          <video
            key={`${String(midia.id)}:${retryKey}`}
            ref={onVideoElementChange}
            controls
            playsInline
            preload={preload}
            className={cn("h-full w-full bg-zinc-950 object-contain object-center", className)}
            onPlay={(event) => onPlay?.(event.currentTarget)}
            onPause={(event) => onPause?.(event.currentTarget)}
            onEnded={(event) => onEnded?.(event.currentTarget)}
            onLoadedMetadata={() => setMediaError(false)}
            onError={(event) => {
              setMediaError(true)
              onError?.(event.currentTarget)
              checkAuthoritativeStatusAfterError()
            }}
          >
            <source src={source} type={mimeTypeVideoDeclaravel(midia.mimeType)} />
            Seu navegador não oferece reprodução deste vídeo.
          </video>
        ) : blocked ? (
          <RestrictedMediaOverlay
            onConfirm={() => setVerificationOpen(true)}
            onAbrirPaginaDoAnuncio={onAbrirPaginaDoAnuncio}
          />
        ) : (
          <div
            className={cn(
              "flex h-full w-full items-center justify-center bg-zinc-950 px-5 text-center text-white/85",
              className,
            )}
            role="alert"
          >
            <div className="space-y-3">
              <p className="text-sm font-semibold">Mídia indisponível</p>
              <p className="text-xs text-white/70">
                Não foi possível reproduzir este vídeo.
              </p>
              <Button type="button" variant="outline" onClick={retry} className="gap-2">
                <ArrowPathIcon className="h-4 w-4" aria-hidden="true" />
                Tentar novamente
              </Button>
            </div>
          </div>
        )}
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
          setSessionAuthorized(true)
          setMediaError(false)
          statusCheckedAfterError.current = false
          setRetryKey((current) => current + 1)
          setVerificationOpen(false)
          onVerificationSuccess?.()
        }}
      />
    </>
  )
}

export default SensitiveVideo
