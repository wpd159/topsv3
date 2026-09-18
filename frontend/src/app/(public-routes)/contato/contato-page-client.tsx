'use client'

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { LoginModal } from "@/components/modals/login-modal"
import { useAuth } from "@/context/AuthContext"

export default function ContatoPageClient() {
  const { usuario } = useAuth()
  const [loginOpen, setLoginOpen] = useState(false)

  const abrirContato = () => {
    if (usuario) {
      window.dispatchEvent(new Event("open-suporte-ticket"))
      return
    }
    setLoginOpen(true)
  }

  return (
    <section className="mx-auto max-w-3xl px-6 py-10 text-center space-y-6">
      <h1 className="text-2xl md:text-4xl font-extrabold text-gray-900 leading-tight">
        Contato e suporte
      </h1>

      <div className="space-y-4 text-justify text-gray-600 leading-relaxed md:text-center">
        <p>
          Para falar com a equipe pelo suporte interno, entre na sua conta. Depois, use o botão
          de suporte ou acesse Meus tickets para abrir e acompanhar um chamado. Você também
          pode encaminhar sua mensagem ao contato institucional abaixo.
        </p>
        <p>
          Atendimento, privacidade, questões jurídicas, denúncias e segurança:{" "}
          <strong className="text-gray-800">contato@topsdojob.com.br</strong>
        </p>
      </div>

      <div className="pt-2">
        <Button
          onClick={abrirContato}
          className="bg-[#FC1EAD] px-6 py-6 font-semibold text-white hover:bg-[#e01a9a]"
        >
          FALAR COM O SUPORTE
        </Button>
      </div>

      <LoginModal open={loginOpen} onOpenChange={setLoginOpen} />
    </section>
  )
}
