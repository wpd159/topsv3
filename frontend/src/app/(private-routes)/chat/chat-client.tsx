'use client'

import ChatSidebar from "@/components/chat/sidebar-chat"
import ChatWindow from "@/components/chat/chat"
import { useState, useEffect } from "react"
import { useSearchParams } from "next/navigation"

export default function ChatClient() {
  const [conversaSelecionada, setConversaSelecionada] = useState<any>(null)
  const [isMobile, setIsMobile] = useState(false)
  const searchParams = useSearchParams()

  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth < 768)
    handleResize()
    window.addEventListener("resize", handleResize)
    return () => window.removeEventListener("resize", handleResize)
  }, [])

  useEffect(() => {
    const username = searchParams.get("usuario")
    if (!username) return

    ;(async () => {
      try {
        const res = await fetch(
          `${process.env.NEXT_PUBLIC_API_URL}/usuarios/username/${encodeURIComponent(username)}`,
          { credentials: "include" }
        )
        if (!res.ok) return
        const usuario = await res.json()
        setConversaSelecionada({ id: usuario.id, nome: usuario.username })
      } catch (e) {
      }
    })()
  }, [searchParams])

  return (
    <section className="h-[calc(100vh-100px)] mt-5 flex border border-gray-200 rounded-xl overflow-hidden bg-white">
      {(!isMobile || !conversaSelecionada) && (
        <ChatSidebar
          conversaSelecionada={conversaSelecionada}
          onSelectConversa={setConversaSelecionada}
        />
      )}

      {(!isMobile || conversaSelecionada) && (
        <div className="flex-1 flex flex-col">
          {conversaSelecionada ? (
            <ChatWindow
              conversa={conversaSelecionada}
              onVoltar={() => setConversaSelecionada(null)}
              isMobile={isMobile}
            />
          ) : (
            !isMobile && (
              <div className="flex flex-1 items-center justify-center text-gray-500 text-sm">
                Selecione uma conversa para começar o chat.
              </div>
            )
          )}
        </div>
      )}
    </section>
  )
}
