'use client'

import { useEffect, useState } from "react"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { toast } from "sonner"

interface Mensagem {
  id: number
  texto: string
  data: string
  remetente: "usuario" | "suporte"
}

interface TicketDetailsModalProps {
  open: boolean
  onOpenChange: (value: boolean) => void
  ticket: any
}

export default function TicketDetailsModal({ open, onOpenChange, ticket }: TicketDetailsModalProps) {
  const [mensagens, setMensagens] = useState<Mensagem[]>([])
  const [carregando, setCarregando] = useState(false)

  useEffect(() => {
    const carregar = async () => {
      if (!open || !ticket?.id) return
      try {
        setCarregando(true)
        const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/suporte/${ticket.id}`, {
          credentials: 'include',
        })
        if (!res.ok) throw new Error('Falha ao buscar ticket')
        const detalhe = await res.json()

        const msgs: Mensagem[] = (detalhe.mensagens || []).map((mensagem: any) => ({
          id: mensagem.id,
          texto: mensagem.conteudo,
          data: new Date(mensagem.enviadoEm).toLocaleString('pt-BR'),
          remetente:
            mensagem.enviadoPorRole === 'USER' || mensagem.enviadoPor?.role === 'USER'
              ? 'usuario'
              : 'suporte',
        }))

        const seed: Mensagem = {
          id: 0,
          texto: detalhe.descricao,
          data: new Date(detalhe.dataAbertura).toLocaleString('pt-BR'),
          remetente: 'usuario',
        }

        setMensagens([seed, ...msgs])
      } catch {
        toast.error('Não foi possível carregar os detalhes do ticket.')
      } finally {
        setCarregando(false)
      }
    }

    carregar()
  }, [open, ticket?.id])

  if (!ticket) return null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg overflow-hidden rounded-xl p-0">
        <div className="flex h-[500px] flex-col">
          <DialogHeader>
            <DialogTitle className="border-b px-4 py-3 font-semibold text-gray-800">
              Conversa completa — {ticket.abertoPorNome || ticket.abertoPorUsername || 'Usuário'}
            </DialogTitle>
          </DialogHeader>

          <div className="flex-1 space-y-3 overflow-y-auto bg-gray-50 p-5">
            {carregando ? (
              <div className="text-sm text-gray-500">Carregando...</div>
            ) : (
              mensagens.map((mensagem) => (
                <div
                  key={`${mensagem.id}-${mensagem.data}`}
                  className={`flex flex-col ${
                    mensagem.remetente === 'suporte' ? 'items-end text-right' : 'items-start text-left'
                  }`}
                >
                  <div
                    className={`max-w-[80%] rounded-lg px-4 py-2 text-sm shadow-sm ${
                      mensagem.remetente === 'suporte'
                        ? 'bg-[#FC1EAD]/20 text-gray-800'
                        : 'border bg-white text-gray-700'
                    }`}
                  >
                    <p>{mensagem.texto}</p>
                  </div>
                  <span className="mt-1 text-[11px] text-gray-400">
                    {mensagem.remetente === 'suporte' ? 'Suporte' : 'Usuário'} — {mensagem.data}
                  </span>
                </div>
              ))
            )}
          </div>

          <div className="border-t bg-white p-3">
            <Button
              onClick={() => onOpenChange(false)}
              className="w-full bg-[#FC1EAD] py-5 font-semibold text-white hover:bg-[#e01a9a]"
            >
              Fechar
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
