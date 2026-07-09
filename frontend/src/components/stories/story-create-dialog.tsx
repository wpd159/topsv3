"use client"

import { useEffect, useRef, useState } from "react"
import { PlusIcon, XMarkIcon, BanknotesIcon } from "@heroicons/react/24/solid"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { CreditCardIcon } from "lucide-react"
import { toast } from "sonner"

function isVideoFile(file: File) {
  return file.type.startsWith("video/")
}

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  anuncioId: number
  custoCreditos: number
  duracaoHoras?: number | null
  onPublish: (file: File, dias: number, anuncioId: number) => Promise<boolean>
  onBuyCredits: () => void
}

export function StoryCreateDialog({
  open,
  onOpenChange,
  anuncioId,
  custoCreditos,
  duracaoHoras,
  onPublish,
  onBuyCredits,
}: Props) {
  const [submitting, setSubmitting] = useState(false)
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)

  const galleryInputRef = useRef<HTMLInputElement | null>(null)
  const cameraInputRef = useRef<HTMLInputElement | null>(null)

  const horas = duracaoHoras && duracaoHoras > 0 ? duracaoHoras : 24
  const dias = Math.max(1, Math.ceil(horas / 24))
  const creditos = Math.max(0, custoCreditos || 0)
  const duracaoLabel =
    horas < 24 ? `${horas}h` : horas % 24 === 0 ? `${horas / 24} dia${horas / 24 > 1 ? "s" : ""}` : `${horas}h`

  useEffect(() => {
    if (!open) {
      setFile(null)
      setPreview(null)
    }
  }, [open])

  useEffect(() => {
    if (!file) {
      setPreview(null)
      return
    }
    const url = URL.createObjectURL(file)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  async function handlePublish() {
    if (anuncioId == null || anuncioId === undefined || anuncioId <= 0) {
      toast.error("Selecione um anúncio ativo")
      return
    }
    if (!file) {
      toast.error("Selecione uma foto ou vídeo.")
      return
    }
    try {
      setSubmitting(true)
      const ok = await onPublish(file, dias, anuncioId)
      if (ok) onOpenChange(false)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader className="space-y-1">
          <DialogTitle className="flex items-center gap-2">
            <PlusIcon className="h-5 w-5 text-[#FC1EAD]" />
            Criar Story
          </DialogTitle>
          <DialogDescription>
            Publique uma foto ou vídeo vinculada ao anúncio selecionado. O custo e a duração seguem o catálogo atual.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 pt-2">
          <div className="rounded-xl border border-gray-200 bg-gray-50 p-3">
            {!preview ? (
              <div className="text-sm text-gray-600">Selecione uma mídia abaixo (galeria ou câmera).</div>
            ) : file && isVideoFile(file) ? (
              <video src={preview} controls className="w-full rounded-lg" />
            ) : (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={preview} alt="Preview" className="w-full rounded-lg object-cover" />
            )}
          </div>

          <input
            ref={galleryInputRef}
            type="file"
            accept="image/*,video/*"
            className="hidden"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
          <input
            ref={cameraInputRef}
            type="file"
            accept="image/*,video/*"
            capture="environment"
            className="hidden"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />

          <div className="flex flex-col gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => galleryInputRef.current?.click()}
              disabled={submitting}
              className="w-full"
            >
              Escolher da galeria
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => cameraInputRef.current?.click()}
              disabled={submitting}
              className="w-full"
            >
              Tirar agora
            </Button>
          </div>

          <div className="space-y-2">
            <label className="text-sm font-semibold text-gray-900">Duração</label>

            <div className="rounded-lg border border-[#FC1EAD] bg-pink-50 px-3 py-2">
              <div className="text-sm font-semibold text-gray-900">{duracaoLabel}</div>
              <div className="text-[11px] text-gray-600">{creditos} créditos</div>
            </div>

            <div className="flex items-center gap-2 pt-1 text-sm text-gray-700">
              <BanknotesIcon className="h-5 w-5 text-green-600" />
              <span>
                Custo: <span className="font-semibold text-[#FC1EAD]">{creditos} créditos</span>
              </span>
            </div>

            <p className="text-xs text-gray-500">
              Story fica disponível até expirar no prazo configurado para a feature STORIES.
            </p>
          </div>
        </div>

        <div className="mt-5 flex flex-col gap-2">
          <Button
            onClick={handlePublish}
            disabled={submitting || !file}
            className="w-full bg-[#FC1EAD] text-white hover:bg-[#e01a9a]"
          >
            {submitting ? "Publicando..." : "Pagar e publicar"}
          </Button>

          <Button
            type="button"
            onClick={onBuyCredits}
            variant="outline"
            className="w-full flex items-center justify-center gap-2 border-pink-400 text-[#FC1EAD] hover:bg-pink-50"
            disabled={submitting}
          >
            <CreditCardIcon className="h-4 w-4" />
            Comprar Créditos
          </Button>

          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={submitting}
            className="w-full text-gray-700"
          >
            <XMarkIcon className="mr-1 h-4 w-4" />
            Cancelar
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
