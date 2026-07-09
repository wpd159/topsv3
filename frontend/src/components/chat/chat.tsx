'use client'

import { useState, useRef, useEffect } from "react"
import { PaperAirplaneIcon, ArrowTopRightOnSquareIcon } from "@heroicons/react/24/solid"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { useRouter } from "next/navigation"
import SockJS from "sockjs-client"
import { Client } from "@stomp/stompjs"
import { useAuth } from "@/context/AuthContext"

let stompClient: Client | null = null

const gerarCor = (nome: string) => {
  const cores = ["#FC1EAD", "#F97316", "#3B82F6", "#10B981", "#8B5CF6", "#EAB308"]
  const hash = nome.split("").reduce((acc, c) => acc + c.charCodeAt(0), 0)
  return cores[hash % cores.length]
}

const getIniciais = (nome: string) =>
  nome
    .split(" ")
    .slice(0, 2)
    .map((n) => n[0])
    .join("")
    .toUpperCase()

export default function ChatWindow({
  conversa,
  onVoltar,
  isMobile,
}: {
  conversa: any
  onVoltar?: () => void
  isMobile?: boolean
}) {
  const { usuario } = useAuth()
  const [mensagens, setMensagens] = useState<any[]>([])
  const [mensagem, setMensagem] = useState("")
  const mensagensRef = useRef<HTMLDivElement>(null)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const router = useRouter()

  const gerarChatId = (a: number, b: number) => (a < b ? `${a}_${b}` : `${b}_${a}`)

  useEffect(() => {
    audioRef.current = new Audio("/notification_sound.mp3")
  }, [])

  useEffect(() => {
    if (!usuario || !conversa) return

    const socket = new SockJS(`${process.env.NEXT_PUBLIC_API_URL}/ws-suporte`)
    const client = new Client({
      webSocketFactory: () => socket as any,
      reconnectDelay: 5000,
      onConnect: () => {
        const chatId = gerarChatId(usuario.id, conversa.id)
        client.subscribe(`/topic/chat/${chatId}`, (msg) => {
          const body = JSON.parse(msg.body)
          if (body.remetenteId !== usuario.id) {
            audioRef.current?.play().catch(() => null)
          }

          setMensagens((prev) => [...prev, body])
        })
      },
      onStompError: (frame) => console.error("Erro STOMP:", frame),
      onWebSocketClose: () => console.warn("⚠️ Conexão WebSocket encerrada"),
    })

    client.activate()
    stompClient = client

    fetch(`${process.env.NEXT_PUBLIC_API_URL}/chat/${usuario.id}/${conversa.id}`, {
      credentials: "include",
    })
      .then((res) => res.json())
      .then((data) => setMensagens(data))
      .catch((err) => console.error("Erro ao carregar histórico:", err))

    return () => {
      client.deactivate()
    }
  }, [usuario, conversa])

  // Enviar mensagem
  const enviarMensagem = () => {
    if (!mensagem.trim() || !stompClient || !usuario) return

    const payload = {
      remetenteId: usuario.id,
      destinatarioId: conversa.id,
      conteudo: mensagem,
    }

    stompClient.publish({
      destination: "/app/chat.enviar",
      body: JSON.stringify(payload),
    })

    setMensagem("")
  }

  // Scroll automático para o fim
  useEffect(() => {
    mensagensRef.current?.scrollTo({
      top: mensagensRef.current.scrollHeight,
      behavior: "smooth",
    })
  }, [mensagens])

  return (
    <div className="flex flex-col h-full bg-gray-50">
      {/* Cabeçalho */}
      <div className="flex items-center justify-between border-b p-4 bg-white shadow-sm">
        <div className="flex items-center gap-3">
          {/* Botão voltar (mobile) */}
          {isMobile && (
            <button
              onClick={onVoltar}
              className="p-2 -ml-2 mr-1 rounded-full hover:bg-gray-100 transition"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
                strokeWidth={2}
                stroke="currentColor"
                className="w-5 h-5 text-gray-700"
              >
                <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
              </svg>
            </button>
          )}

          {/* Avatar */}
          <div
            className="w-10 h-10 rounded-full flex items-center justify-center text-white font-semibold"
            style={{ backgroundColor: gerarCor(conversa.nome) }}
          >
            {getIniciais(conversa.nome)}
          </div>

          {/* Nome e ação */}
          <div className="flex flex-col">
            <h2 className="font-semibold text-gray-800 text-sm leading-tight">
              {conversa.nome}
            </h2>
            <button
              onClick={() =>
                router.push(`/anuncios?autor=${encodeURIComponent(conversa.nome)}`)
              }
              className="text-xs cursor-pointer text-[#FC1EAD] hover:underline flex items-center gap-1"
            >
              Ver anúncios
              <ArrowTopRightOnSquareIcon className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>

      {/* Área de mensagens */}
      <div
        ref={mensagensRef}
        className="flex-1 overflow-y-auto px-4 py-4 space-y-3 scrollbar-thin scrollbar-thumb-gray-300 scrollbar-track-transparent"
      >
        {mensagens.map((m, i) => (
          <div
            key={i}
            className={`flex ${
              m.remetenteId === usuario?.id ? "justify-end" : "justify-start"
            }`}
          >
            <div
              className={`max-w-[70%] px-3 py-2 rounded-lg text-sm shadow-sm ${
                m.remetenteId === usuario?.id
                  ? "bg-[#FC1EAD] text-white rounded-br-none"
                  : "bg-white border rounded-bl-none text-gray-800"
              }`}
            >
              {m.conteudo}
            </div>
          </div>
        ))}
      </div>

      {/* Input */}
      <div className="flex items-center gap-2 p-4 border-t bg-white shadow-inner">
        <Input
          value={mensagem}
          onChange={(e) => setMensagem(e.target.value)}
          placeholder="Digite uma mensagem..."
          className="flex-1 focus-visible:ring-[#FC1EAD]"
          onKeyDown={(e) => e.key === "Enter" && enviarMensagem()}
        />
        <Button
          onClick={enviarMensagem}
          className="bg-[#FC1EAD] hover:bg-[#e01a9a] active:scale-[0.97] transition-transform"
        >
          <PaperAirplaneIcon className="w-5 h-5 text-white" />
        </Button>
      </div>

      {/* 🔊 Som de notificação */}
      <audio ref={audioRef} src="/notification_sound.mp3" preload="auto" />
    </div>
  )
}
