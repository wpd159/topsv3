'use client'

import { ReactNode, useEffect } from "react"
import { useRouter, usePathname } from "next/navigation"
import "@/app/globals.css"
import Sidebar from "./components/sidebar/sidebar"
import { useAuth } from "@/context/AuthContext"
import { canAccessRoute } from "./components/sidebar/sidebar-utils"

type LayoutProps = {
  children: ReactNode
}

export default function PainelLayout({ children }: LayoutProps) {
  const { usuario, carregando } = useAuth()
  const router = useRouter()
  const pathname = usePathname()

  useEffect(() => {
    if (carregando) return

    if (!usuario) {
      router.replace("/admin/login")
      return
    }

    if (!["ADMIN", "MODERADOR"].includes(usuario.cargo)) {
      router.replace("/acesso-negado")
      return
    }

    // Verifica se o usuário tem acesso à rota atual baseado no seu cargo
    if (!canAccessRoute(pathname, usuario.cargo)) {
      router.replace("/acesso-negado")
    }
  }, [usuario, carregando, router, pathname])

  if (carregando || !usuario) {
    return (
      <div className="flex items-center justify-center h-screen bg-[#151619] text-white">
        Verificando acesso...
      </div>
    )
  }

  if (!["ADMIN", "MODERADOR"].includes(usuario.cargo)) {
    return null
  }

  return (
    <div className="fixed mx-auto inset-0 flex flex-col lg:flex-row bg-[#151619] text-white overflow-hidden">
      <Sidebar />

      <div className="flex-1 lg:mt-4 rounded-t-2xl lg:rounded-tl-2xl bg-[#F9FAFA] text-black overflow-hidden">
        <div className="h-full overflow-y-auto p-4 lg:p-8">
          {children}
        </div>
      </div>
    </div>
  )
}
