"use client"

import { useState } from "react"
import Image from "next/image"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import {
  ChatBubbleBottomCenterTextIcon,
  WrenchScrewdriverIcon,
  BugAntIcon,
  EnvelopeIcon,
  PencilIcon,
} from "@heroicons/react/24/outline"
import { toast } from "sonner"
import { useAuth } from "@/context/AuthContext"
import { getPublicLogoUrl } from "@/lib/public-site-assets"

interface FeedbackDialogProps {
  open: boolean
  onOpenChange: (value: boolean) => void
}

export default function FeedbackDialog({ open, onOpenChange }: FeedbackDialogProps) {
  const { usuario } = useAuth()

  const [tipo, setTipo] = useState<"FEATURE" | "BUG" | "">("")
  const [titulo, setTitulo] = useState("")
  const [descricao, setDescricao] = useState("")
  const [emailOpcional, setEmailOpcional] = useState("")
  const [loading, setLoading] = useState(false)

  const API = process.env.NEXT_PUBLIC_API_URL

  const enviar = async () => {
    if (!tipo) {
      toast.warning("Selecione o tipo do feedback.")
      return
    }
    if (!titulo.trim() || !descricao.trim()) {
      toast.warning("Preencha título e descrição.")
      return
    }

    try {
      setLoading(true)

      const body = {
        tipo,
        titulo,
        descricao,
        emailOpcional: usuario ? null : emailOpcional,
      }

      const res = await fetch(`${API}/sugestoes`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(body),
      })

      if (!res.ok) {
        const txt = await res.text()
        toast.error(txt || "Falha ao enviar feedback")
        return
      }

      toast.success("Feedback enviado com sucesso! Obrigado 🙌")
      onOpenChange(false)
      setTipo("")
      setTitulo("")
      setDescricao("")
      setEmailOpcional("")
    } catch (err) {
      toast.error("Erro ao enviar feedback")
    } finally {
      setLoading(false)
    }
  }

  const iconClass = "w-5 h-5 mr-2"

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md p-6 rounded-xl">
        <DialogHeader>
          <DialogTitle className="flex justify-center mb-2">
            <Image
              src={getPublicLogoUrl()}
              alt="Logo"
              width={150}
              height={50}
              className="h-10 w-auto max-w-[180px] object-contain"
              priority
              fetchPriority="high"
              unoptimized
            />
          </DialogTitle>
          <DialogDescription className="text-center text-gray-600">
            Ajude-nos a melhorar a plataforma enviando sugestões ou reportando bugs.
          </DialogDescription>
        </DialogHeader>

        {/* TIPO */}
        <div className="grid grid-cols-2 gap-3 mt-4">
          <button
            className={`flex items-center justify-center gap-2 px-3 py-3 rounded-lg border transition ${
              tipo === "FEATURE"
                ? "border-[#FC1EAD] bg-pink-50 text-[#FC1EAD]"
                : "border-gray-300 text-gray-700 hover:bg-gray-50"
            }`}
            onClick={() => setTipo("FEATURE")}
          >
            <WrenchScrewdriverIcon className={iconClass} />
            Sugestão
          </button>

          <button
            className={`flex items-center justify-center gap-2 px-3 py-3 rounded-lg border transition ${
              tipo === "BUG"
                ? "border-red-500 bg-red-50 text-red-500"
                : "border-gray-300 text-gray-700 hover:bg-gray-50"
            }`}
            onClick={() => setTipo("BUG")}
          >
            <BugAntIcon className={iconClass} />
            Bug
          </button>
        </div>

        {/* TÍTULO */}
        <div className="relative mt-4">
          <PencilIcon className="absolute left-3 top-2.5 w-5 h-5 text-gray-400" />
          <Input
            placeholder="Título do feedback"
            value={titulo}
            onChange={(e) => setTitulo(e.target.value)}
            className="pl-10 py-5"
          />
        </div>

        {/* DESCRIÇÃO */}
        <div className="relative mt-4">
          <ChatBubbleBottomCenterTextIcon className="absolute left-3 top-3 w-5 h-5 text-gray-400" />
          <Textarea
            placeholder="Descreva sua sugestão ou o problema encontrado..."
            value={descricao}
            onChange={(e) => setDescricao(e.target.value)}
            className="pl-10 pt-3 min-h-[120px]"
          />
        </div>

        {/* EMAIL OPCIONAL (somente para anônimos) */}
        {!usuario && (
          <div className="relative mt-4">
            <EnvelopeIcon className="absolute left-3 top-2.5 w-5 h-5 text-gray-400" />
            <Input
              type="email"
              placeholder="Seu e-mail (opcional)"
              value={emailOpcional}
              onChange={(e) => setEmailOpcional(e.target.value)}
              className="pl-10 py-5"
            />
          </div>
        )}

        {/* BOTÃO */}
        <Button
          disabled={loading}
          onClick={enviar}
          className="w-full py-5 mt-6 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold"
        >
          {loading ? "Enviando..." : "Enviar feedback"}
        </Button>
      </DialogContent>
    </Dialog>
  )
}
