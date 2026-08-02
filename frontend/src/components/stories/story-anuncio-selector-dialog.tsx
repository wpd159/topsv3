'use client'

import Image from 'next/image'
import { useEffect, useRef, useState } from 'react'
import { ImageIcon, MapPin, Megaphone } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { meuAnuncioUrlPublicaSegura } from '@/components/anuncios/meu-anuncio-card'
import { getStoryEntryState } from '@/components/stories/story-entry-state'
import type { MeuAnuncio } from '@/lib/meus-anuncios-api'
import { cn } from '@/lib/utils'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  anuncios: MeuAnuncio[]
  returnFocusTo?: HTMLElement | null
  onSelect: (anuncio: MeuAnuncio) => void
}

export function StoryAnuncioSelectorDialog({ open, onOpenChange, anuncios, returnFocusTo, onSelect }: Props) {
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const continuingRef = useRef(false)

  useEffect(() => {
    if (open) {
      continuingRef.current = false
      setSelectedId(null)
    }
  }, [open])

  const selected = anuncios.find((anuncio) => anuncio.id === selectedId) ?? null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="flex max-h-[calc(100dvh-1rem)] w-[calc(100vw-1rem)] flex-col overflow-hidden p-0 sm:max-h-[calc(100dvh-2rem)] sm:max-w-xl"
        onOpenAutoFocus={(event) => {
          event.preventDefault()
          const content = event.currentTarget as HTMLElement | null
          if (!content) return
          requestAnimationFrame(() => {
            content.querySelector<HTMLElement>('input:not(:disabled), button:not(:disabled)')?.focus()
          })
        }}
        onCloseAutoFocus={(event) => {
          event.preventDefault()
          if (continuingRef.current) return
          requestAnimationFrame(() => {
            if (returnFocusTo?.isConnected) returnFocusTo.focus()
          })
        }}
      >
        <div className="min-h-0 flex-1 overflow-y-auto px-4 py-5 sm:px-6">
          <DialogHeader className="pr-8 text-left">
            <DialogTitle>Escolha o anúncio para o Story</DialogTitle>
            <DialogDescription>
              Selecione um dos seus anúncios para continuar no fluxo de Stories.
            </DialogDescription>
          </DialogHeader>

          <fieldset className="mt-5 space-y-3">
            <legend className="sr-only">Anúncio que será usado no Story</legend>
            {anuncios.map((anuncio) => {
              const entry = getStoryEntryState(anuncio)
              const cover = !anuncio.capa?.restrita
                ? meuAnuncioUrlPublicaSegura(anuncio.capa?.urlPublica)
                : null
              const location = anuncio.localizacao?.cidade
                ? [anuncio.localizacao.cidade, anuncio.localizacao.uf].filter(Boolean).join(' - ')
                : 'Cidade não informada'
              const selectedItem = selectedId === anuncio.id

              return (
                <label
                  key={anuncio.id}
                  className={cn(
                    'flex min-w-0 gap-3 rounded-lg border p-3 transition focus-within:ring-2 focus-within:ring-[#FC1EAD] focus-within:ring-offset-2',
                    entry.disabled
                      ? 'cursor-not-allowed border-slate-200 bg-slate-50 opacity-70'
                      : 'cursor-pointer border-slate-200 hover:border-pink-300 hover:bg-pink-50/40',
                    selectedItem && 'border-[#FC1EAD] bg-pink-50'
                  )}
                >
                  <input
                    type="radio"
                    name="story-anuncio"
                    value={anuncio.id}
                    checked={selectedItem}
                    onChange={() => setSelectedId(anuncio.id)}
                    disabled={entry.disabled}
                    className="mt-5 shrink-0 accent-[#FC1EAD]"
                    aria-describedby={`story-selector-status-${anuncio.id}`}
                  />
                  <span className="relative h-16 w-16 shrink-0 overflow-hidden rounded-lg bg-slate-100">
                    {cover ? (
                      <Image
                        src={cover}
                        alt=""
                        fill
                        className="object-cover"
                        sizes="64px"
                      />
                    ) : (
                      <span className="flex h-full items-center justify-center text-slate-400">
                        <ImageIcon className="h-6 w-6" aria-hidden="true" />
                      </span>
                    )}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block break-words text-sm font-semibold text-slate-950">{anuncio.titulo}</span>
                    <span className="mt-1 flex items-center gap-1 text-xs text-slate-500">
                      <MapPin className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                      <span className="truncate">{location}</span>
                    </span>
                    <span
                      id={`story-selector-status-${anuncio.id}`}
                      className={cn(
                        'mt-1.5 flex items-start gap-1 text-xs font-medium',
                        entry.disabled ? 'text-slate-600' : 'text-[#b5127a]'
                      )}
                    >
                      <Megaphone className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                      <span className="break-words">{entry.summary}</span>
                    </span>
                  </span>
                </label>
              )
            })}
          </fieldset>

          <div className="mt-5 grid gap-2 sm:grid-cols-2">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button
              type="button"
              disabled={!selected}
              onClick={() => {
                if (selected) {
                  continuingRef.current = true
                  onSelect(selected)
                }
              }}
            >
              Continuar
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
