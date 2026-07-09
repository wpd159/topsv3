'use client'

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
  DialogTrigger,
} from "@/components/ui/dialog"
import { XCircleIcon } from "@heroicons/react/24/solid"

interface ReprovarAnuncioDialogProps {
  onConfirm: (motivo: string) => void
}

export default function ReprovarAnuncioDialog({ onConfirm }: ReprovarAnuncioDialogProps) {
  const [open, setOpen] = useState(false)
  const [motivo, setMotivo] = useState("")

  const handleConfirm = () => {
    if (motivo.trim().length === 0) {
      alert("Por favor, descreva o motivo da reprovação.")
      return
    }
    onConfirm(motivo)
    setMotivo("")
    setOpen(false)
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button
          variant="outline"
          className="flex items-center hover:text-none gap-1 border-red-300 text-red-600 hover:bg-red-50"
        >
          <XCircleIcon className="w-4 h-4" />
          Reprovar
        </Button>
      </DialogTrigger>

      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-lg font-semibold text-gray-800">
            Reprovar Anúncio
          </DialogTitle>
          <DialogDescription className="text-sm text-gray-500">
            Escreva o motivo da reprovação. Essa mensagem pode ser enviada ao anunciante.
          </DialogDescription>
        </DialogHeader>

        <Textarea
          placeholder="Descreva o motivo da reprovação..."
          value={motivo}
          onChange={(e) => setMotivo(e.target.value)}
          className="min-h-[120px] resize-none"
        />

        <DialogFooter className="mt-4 flex justify-end gap-2">
          <Button
            variant="outline"
            onClick={() => setOpen(false)}
            className="text-gray-600 hover:bg-gray-100"
          >
            Cancelar
          </Button>
          <Button
            onClick={handleConfirm}
            className="bg-red-600 text-white hover:bg-red-700"
          >
            Confirmar Reprovação
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
