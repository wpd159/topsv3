'use client'

import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Textarea } from "@/components/ui/textarea"
import { Button } from "@/components/ui/button"
import { useState, useEffect } from "react"
import { toast } from "sonner"

interface DenunciaPunirModalProps {
  open: boolean
  onOpenChange: (value: boolean) => void
  denuncia: any
  onConfirm: (justificativa: string) => Promise<void> | void
}

export default function DenunciaPunirModal({
  open,
  onOpenChange,
  denuncia,
  onConfirm,
}: DenunciaPunirModalProps) {
  const [mensagem, setMensagem] = useState("")
  const [loading, setLoading] = useState(false)

  // limpa campo sempre que abrir/fechar
  useEffect(() => {
    if (!open) setMensagem("")
  }, [open])

  if (!denuncia) return null

  const enviarPunicao = async () => {
    if (!mensagem.trim()) {
      toast.error("Digite uma justificativa para o anunciante.")
      return
    }

    setLoading(true)
    try {
      await onConfirm(mensagem) // ✅ agora envia texto completo
      toast.success("Anúncio punido e e-mail enviado ao anunciante.")
      onOpenChange(false)
    } catch (err) {
      toast.error("Erro ao aplicar punição.")
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md p-0 overflow-hidden rounded-xl">
        <div className="flex flex-col h-[420px]">
          <DialogHeader>
            <DialogTitle className="px-4 py-3 border-b text-gray-800 font-semibold">
              Punir Anúncio — {denuncia.username || "Usuário"}
            </DialogTitle>
          </DialogHeader>

          <div className="flex-1 overflow-y-auto p-5 bg-gray-50">
            <p className="text-sm text-gray-700 mb-4 leading-relaxed">
              Você está prestes a excluir o anúncio denunciado.
              Escreva abaixo a justificativa que será enviada por e-mail ao anunciante.
            </p>

            <Textarea
              placeholder="Explique o motivo da exclusão do anúncio..."
              value={mensagem}
              onChange={(e) => setMensagem(e.target.value)}
              rows={6}
              className="resize-none"
            />
          </div>

          <div className="border-t p-3 bg-white flex gap-2">
            <Button
              onClick={enviarPunicao}
              disabled={loading}
              className="w-full bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold py-5"
            >
              {loading ? "Enviando..." : "Enviar e Punir"}
            </Button>
            <Button
              variant="outline"
              onClick={() => onOpenChange(false)}
              className="w-full border-gray-300 text-gray-600 hover:bg-gray-50 font-semibold py-5"
            >
              Cancelar
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
