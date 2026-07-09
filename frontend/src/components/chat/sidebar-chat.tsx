'use client'

import { useState, useEffect, useRef } from "react"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import { useAuth } from "@/context/AuthContext"
import SockJS from "sockjs-client"
import { Client } from "@stomp/stompjs"
import { PlusCircleIcon } from "@heroicons/react/24/solid"

let stompClient: Client | null = null

interface ChatSidebarProps {
  conversaSelecionada: any
  onSelectConversa: (conversa: any) => void
}

export default function ChatSidebar({
  conversaSelecionada,
  onSelectConversa,
}: ChatSidebarProps) {
  const { usuario } = useAuth()
  const [busca, setBusca] = useState("")
  const [conversas, setConversas] = useState<any[]>([])
  const [novoUser, setNovoUser] = useState("")
  const [abrirNova, setAbrirNova] = useState(false)
  const [erro, setErro] = useState("")
  const [notificacoes, setNotificacoes] = useState<Record<number, number>>({})
  const audioRef = useRef<HTMLAudioElement | null>(null)

  useEffect(() => {
    audioRef.current = new Audio("/notification_sound.mp3")
  }, [])

  useEffect(() => {
    if (!usuario) return

    fetch(`${process.env.NEXT_PUBLIC_API_URL}/chat/recentes/${usuario.id}`, {
      credentials: "include",
    })
      .then(async (res) => {
        const text = await res.text()
        return text ? JSON.parse(text) : []
      })
      .then(setConversas)
      .catch((err) => console.error("Erro ao carregar conversas:", err))

    const socket = new SockJS(`${process.env.NEXT_PUBLIC_API_URL}/ws-suporte`)
    const client = new Client({
      webSocketFactory: () => socket as any,
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/chat/global`, (msg) => {
          const data = JSON.parse(msg.body)

          if (data.remetenteId === usuario.id || data.destinatarioId === usuario.id) {
            setConversas((prev) => {
              const chatKey = (a: number, b: number) => (a < b ? `${a}_${b}` : `${b}_${a}`)

              const novaKey = chatKey(data.remetenteId, data.destinatarioId)

              const filtradas = prev.filter(
                (c) => chatKey(c.remetenteId, c.destinatarioId) !== novaKey
              )

              return [data, ...filtradas]
            })

            const parceiroId =
              data.remetenteId === usuario.id ? data.destinatarioId : data.remetenteId

            if (parceiroId !== conversaSelecionada?.id) {
              setNotificacoes((prev) => ({
                ...prev,
                [parceiroId]: (prev[parceiroId] || 0) + 1,
              }))
              audioRef.current?.play().catch(() => null)
            }
          }
        })
      },
    })

    client.activate()
    stompClient = client
    return () => {
      client.deactivate()
    }
  }, [usuario, conversaSelecionada])

  async function iniciarNovaConversa() {
    if (!novoUser.trim()) return
    setErro("")

    try {
      const res = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/usuarios/username/${novoUser}`,
        { credentials: "include" }
      )
      if (!res.ok) throw new Error("Usuário não encontrado.")
      const parceiro = await res.json()
      if (parceiro.id === usuario?.id)
        throw new Error("Você não pode conversar consigo mesmo.")

      onSelectConversa({ id: parceiro.id, nome: parceiro.username })
      setNovoUser("")
      setAbrirNova(false)
      setNotificacoes((prev) => ({ ...prev, [parceiro.id]: 0 }))
    } catch (err: any) {
      setErro(err.message)
    }
  }

  const filtradas = conversas.filter((c) =>
    (c.remetenteUsername + c.destinatarioUsername)
      .toLowerCase()
      .includes(busca.toLowerCase())
  )

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

  return (
    <aside className="w-full md:w-[320px] border-r border-gray-200 flex flex-col bg-white">
      <div className="p-4 border-b flex items-center justify-between bg-gradient-to-r from-pink-50 to-white">
        <h2 className="text-lg font-semibold text-gray-800">Mensagens</h2>
        <Button
          variant="ghost"
          onClick={() => setAbrirNova(!abrirNova)}
          className="text-[#FC1EAD]"
        >
          <PlusCircleIcon className="w-6 h-6" />
        </Button>
      </div>

      {/* Nova conversa */}
      {abrirNova && (
        <div className="p-4 border-b">
          <Input
            value={novoUser}
            onChange={(e) => setNovoUser(e.target.value)}
            placeholder="Digite o username..."
            className="focus-visible:ring-[#FC1EAD]"
            onKeyDown={(e) => e.key === "Enter" && iniciarNovaConversa()}
          />
          {erro && <p className="text-xs text-red-500 mt-2">{erro}</p>}
          <Button
            className="w-full mt-3 bg-[#FC1EAD] hover:bg-[#e01a9a]"
            onClick={iniciarNovaConversa}
          >
            Iniciar conversa
          </Button>
        </div>
      )}

      <div className="p-4 border-b">
        <Input
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
          placeholder="Buscar conversa..."
          className="focus-visible:ring-[#FC1EAD]"
        />
      </div>

      <div className="flex-1 overflow-y-auto scrollbar-thin scrollbar-thumb-gray-300 scrollbar-track-transparent">
        {filtradas.length === 0 ? (
          <p className="text-sm text-gray-400 text-center mt-6">
            Nenhuma conversa encontrada.
          </p>
        ) : (
          filtradas.map((c) => {
            const parceiro =
              c.remetenteId === usuario?.id
                ? { id: c.destinatarioId, nome: c.destinatarioUsername }
                : { id: c.remetenteId, nome: c.remetenteUsername }

            const contador = notificacoes[parceiro.id] || 0

            return (
              <div
                key={c.id}
                onClick={() => {
                  onSelectConversa(parceiro)
                  setNotificacoes((prev) => ({ ...prev, [parceiro.id]: 0 }))
                }}
                className={cn(
                  "cursor-pointer flex items-center gap-3 px-4 py-3 border-b hover:bg-pink-50/60 relative",
                  conversaSelecionada?.id === parceiro.id &&
                    "bg-pink-50 border-l-4 border-l-[#FC1EAD]"
                )}
              >
                <div
                  className="w-11 h-11 rounded-full flex items-center justify-center text-white font-semibold"
                  style={{ backgroundColor: gerarCor(parceiro.nome) }}
                >
                  {getIniciais(parceiro.nome)}
                </div>

                <div className="flex-1 min-w-0">
                  <div className="flex justify-between items-center">
                    <h3
                      className={cn(
                        "text-sm font-medium truncate",
                        conversaSelecionada?.id === parceiro.id
                          ? "text-[#FC1EAD]"
                          : "text-gray-800"
                      )}
                    >
                      {parceiro.nome}
                    </h3>
                    <span className="text-xs text-gray-400">
                      {new Date(c.enviadoEm).toLocaleTimeString([], {
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </span>
                  </div>
                  <p className="text-xs text-gray-500 truncate mt-0.5">
                    {c.conteudo.length > 50
                      ? c.conteudo.slice(0, 50) + "..."
                      : c.conteudo}
                  </p>
                </div>

                {contador > 0 && (
                  <span className="absolute right-12 top-1/2 -translate-y-1/2 bg-[#FC1EAD] text-white text-[10px] font-bold w-5 h-5 rounded-full flex items-center justify-center shadow-md animate-pulse">
                    {contador}
                  </span>
                )}
              </div>
            )
          })
        )}
      </div>

      <audio ref={audioRef} src="/notification_sound.mp3" preload="auto" />
    </aside>
  )
}
