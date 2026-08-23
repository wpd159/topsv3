"use client"

import type { MouseEvent } from "react"
import { Button } from "@/components/ui/button"

type RestrictedMediaOverlayProps = {
  onConfirm: () => void
  onAbrirPaginaDoAnuncio?: () => void
}

function executarSemPropagar(
  event: MouseEvent<HTMLElement>,
  action: () => void,
) {
  event.preventDefault()
  event.stopPropagation()
  action()
}

export function RestrictedMediaOverlay({
  onConfirm,
  onAbrirPaginaDoAnuncio,
}: RestrictedMediaOverlayProps) {
  return (
    <div
      className="compliance-restricted-overlay"
      data-restricted-media-overlay
      onClick={(event) => executarSemPropagar(event, onConfirm)}
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
          onClick={(event) => executarSemPropagar(event, onConfirm)}
        >
          Confirmar maioridade
        </Button>
        {onAbrirPaginaDoAnuncio ? (
          <button
            type="button"
            className="mt-3 w-full text-center text-sm font-medium text-white/75 underline underline-offset-2 hover:text-white"
            onClick={(event) => executarSemPropagar(event, onAbrirPaginaDoAnuncio)}
          >
            Abrir página do anúncio
          </button>
        ) : null}
      </div>
    </div>
  )
}

export default RestrictedMediaOverlay
