"use client"

import Image from "next/image"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { VisitorVerificationModal } from "@/components/compliance/visitor-verification-modal"
import {
  fontePublicaSegura,
  midiaExigeConfirmacaoIdade,
  type MidiaPublica,
} from "@/lib/media/public-media"
import { cn } from "@/lib/utils"

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
  const protegida = midiaExigeConfirmacaoIdade(midia)
  const fonte = fontePublicaSegura(midia)

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
            unoptimized
            className={cn("object-cover object-center transition duration-300", className)}
            onClick={protegida ? undefined : onImageClick}
            onError={() => {
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
        level="LIGHT"
        context={{
          anuncioId: anuncioId ?? undefined,
          route: anuncioSlug ? `/anuncios/${anuncioSlug}` : undefined,
          midiaId: String(midia.id),
        }}
        onOpenChange={setVerificationOpen}
        onVerified={() => {
          setVerificationOpen(false)
          onVerificationSuccess?.()
        }}
      />
    </>
  )
}

export default SensitiveImage
