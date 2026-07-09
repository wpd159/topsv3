'use client'

import { useEffect, useRef, useState } from "react"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Textarea } from "@/components/ui/textarea"
import { Button } from "@/components/ui/button"
import { PaperAirplaneIcon } from "@heroicons/react/24/solid"
import { toast } from "sonner"
import { useAuth } from "@/context/AuthContext"
import SockJS from "sockjs-client"
import { Client } from "@stomp/stompjs"

interface Mensagem {
  id: number
  texto: string
  data: string
  remetente: "usuario" | "suporte"
}

interface TicketChatModalProps {
  open: boolean
  onOpenChange: (value: boolean) => void
  ticket: any
}

export default function TicketChatModal({ open, onOpenChange, ticket }: TicketChatModalProps) {
  const { usuario } = useAuth()
  const [mensagens, setMensagens] = useState<Mensagem[]>([])
  const [novaMensagem, setNovaMensagem] = useState("")
  const [carregando, setCarregando] = useState(false)
  const [ticketFechado, setTicketFechado] = useState(false)

  const stompClient = useRef<Client | null>(null)
  const idsRecebidos = useRef<Set<number>>(new Set())
  const endOfMessagesRef = useRef<HTMLDivElement | null>(null)

  // 🔽 Auto-scroll para última mensagem
  const scrollToBottom = () => {
    endOfMessagesRef.current?.scrollIntoView({ behavior: "smooth" })
  }

  // Sempre que mensagens mudarem, rola pro final
  useEffect(() => {
    if (mensagens.length > 0) scrollToBottom()
  }, [mensagens])

  // 🔹 Carrega histórico
  const carregarDetalhes = async () => {
    if (!ticket?.id) return
    try {
      setCarregando(true)
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/suporte/${ticket.id}`, {
        credentials: "include",
      })
      if (!res.ok) throw new Error("Falha ao buscar ticket")
      const t = await res.json()
      setTicketFechado(t.status === "FECHADO")

      const msgs: Mensagem[] = (t.mensagens || []).map((m: any) => ({
        id: m.id,
        texto: m.conteudo,
        data: new Date(m.enviadoEm).toLocaleString("pt-BR"),
        remetente: m.enviadoPorRole === "USER" ? "usuario" : "suporte",
      }))

      const seed: Mensagem = {
        id: 0,
        texto: t.descricao,
        data: new Date(t.dataAbertura).toLocaleString("pt-BR"),
        remetente: "usuario",
      }

      idsRecebidos.current = new Set([0, ...msgs.map((m) => m.id)])
      setMensagens([seed, ...msgs])
    } catch (e) {
      toast.error("Não foi possível carregar o chat do ticket.")
    } finally {
      setCarregando(false)
    }
  }

  // 🔗 Conecta WS ao abrir modal
  useEffect(() => {
    if (!open || !ticket?.id) return

    carregarDetalhes()

    const socket = new SockJS(`${process.env.NEXT_PUBLIC_API_URL}/ws-suporte`)
    const client = new Client({
      webSocketFactory: () => socket as any,
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/suporte/${ticket.id}`, (message) => {
          if (!message.body) return
          const data = JSON.parse(message.body)
          if (idsRecebidos.current.has(data.id)) return
          idsRecebidos.current.add(data.id)

          setMensagens((prev) => [
            ...prev,
            {
              id: data.id,
              texto: data.conteudo,
              data: new Date(data.enviadoEm).toLocaleString("pt-BR"),
              remetente: data.enviadoPorRole === "USER" ? "usuario" : "suporte",
            },
          ])
        })
      },
    })

    client.activate()
    stompClient.current = client

    return () => {
      client.deactivate()
      stompClient.current = null
    }
  }, [open, ticket?.id])

  useEffect(() => {
    setTicketFechado(ticket?.status === "FECHADO")
  }, [ticket?.status])

  if (!ticket) return null

  const getApiErrorMessage = async (res: Response) => {
    try {
      const data = await res.json()
      if (typeof data?.message === "string" && data.message.trim()) {
        return data.message.trim()
      }
    } catch {
    }

    return `Erro ${res.status}`
  }

  // 💬 Enviar mensagem
  const enviarMensagem = async () => {
    if (!novaMensagem.trim() || ticketFechado) return
    if (!usuario?.id) {
      toast.error("Você precisa estar autenticado.")
      return
    }

    try {
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/suporte/${ticket.id}/mensagem`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          usuarioId: String(usuario.id),
          conteudo: novaMensagem.trim(),
        }),
      })
      if (!res.ok) throw new Error(await getApiErrorMessage(res))

      const msg = await res.json()
      if (!idsRecebidos.current.has(msg.id)) {
        idsRecebidos.current.add(msg.id)
        setMensagens((prev) => [
          ...prev,
          {
            id: msg.id,
            texto: msg.conteudo,
            data: new Date(msg.enviadoEm).toLocaleString("pt-BR"),
            remetente: msg.enviadoPorRole === "USER" ? "usuario" : "suporte",
          },
        ])
      }

      setNovaMensagem("")
      scrollToBottom()
    } catch (e) {
      const message =
        e instanceof Error && e.message
          ? e.message
          : "Não foi possível enviar sua mensagem."

      if (message.includes("encerrado")) {
        setTicketFechado(true)
      }

      toast.error(message)
    }
  }

  const fecharChat = () => {
    setMensagens([])
    setNovaMensagem("")
    idsRecebidos.current = new Set()
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md p-0 overflow-hidden rounded-xl">
        <div className="flex flex-col h-[450px]">
          <DialogHeader>
            <DialogTitle className="px-4 py-3 border-b text-gray-800 font-semibold">
              Ticket de {ticket.abertoPorUsername}
            </DialogTitle>
          </DialogHeader>

          <div className="flex-1 overflow-y-auto p-4 space-y-3 bg-gray-50">
            {carregando ? (
              <div className="text-sm text-gray-500">Carregando chat...</div>
            ) : (
              <>
                {mensagens.map((msg) => (
                  <div
                    key={`${msg.id}-${msg.data}`}
                    className={`flex ${
                      msg.remetente === "suporte" ? "justify-end" : "justify-start"
                    }`}
                  >
                    <div
                      className={`max-w-[75%] px-3 py-2 rounded-lg text-sm ${
                        msg.remetente === "suporte"
                          ? "bg-[#FC1EAD]/20 text-gray-800"
                          : "bg-white border text-gray-700"
                      }`}
                    >
                      <p>{msg.texto}</p>
                      <span className="block text-[10px] text-gray-400 mt-1">
                        {msg.data}
                      </span>
                    </div>
                  </div>
                ))}
                <div ref={endOfMessagesRef} /> {/* 👇 marcador do fim */}
              </>
            )}
          </div>

          <div className="border-t p-3 space-y-2 bg-white">
            {ticketFechado && (
              <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                Este ticket foi encerrado e não aceita novas mensagens.
              </p>
            )}
            <Textarea
              placeholder="Digite sua resposta..."
              value={novaMensagem}
              onChange={(e) => setNovaMensagem(e.target.value)}
              rows={2}
              className="resize-none"
              disabled={ticketFechado}
            />
            <Button
              onClick={enviarMensagem}
              disabled={ticketFechado || !novaMensagem.trim()}
              className="w-full bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold py-5 flex items-center justify-center gap-2"
            >
              <PaperAirplaneIcon className="w-5 h-5 -rotate-45" />
              {ticketFechado ? "Ticket encerrado" : "Enviar"}
            </Button>
            <Button
              onClick={fecharChat}
              variant="outline"
              className="w-full border-gray-300 text-gray-600 hover:bg-gray-50 font-semibold py-5"
            >
              Fechar Chat
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
