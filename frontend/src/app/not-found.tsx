'use client'

import { useRouter } from "next/navigation"
import { Button } from "@/components/ui/button"
import { ArrowLeftIcon } from "@heroicons/react/24/solid"

export default function NotFound() {
  const router = useRouter()

  return (
    <main className="min-h-[calc(100vh-100px)] flex flex-col items-center justify-center px-6 text-center">
      {/* Número 404 grande e translúcido */}
      <h1 className="text-[120px] sm:text-[180px] md:text-[220px] font-bold text-gray-200 leading-none select-none">
        404
      </h1>

      {/* Texto principal */}
      <h2 className="text-2xl md:text-3xl font-semibold text-gray-800 mt-4">
        Oops! Página não encontrada.
      </h2>

      {/* Texto secundário */}
      <p className="text-gray-500 mt-2 max-w-md">
        A página que você tentou acessar pode ter sido movida, removida ou nunca existiu.
      </p>

      {/* Botão */}
      <div className="mt-8">
        <Button
          onClick={() => router.push("/")}
          className="bg-[#FC1EAD] hover:bg-[#e01a9a] text-white text-sm font-medium px-6 py-2 rounded-full transition-transform active:scale-95 flex items-center gap-2"
        >
          <ArrowLeftIcon className="w-4 h-4" />
          Voltar para Home
        </Button>
      </div>
    </main>
  )
}
