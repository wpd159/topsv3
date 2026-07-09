'use client'

import { useRef, useState } from "react"
import { XMarkIcon, ChevronLeftIcon, ChevronRightIcon } from "@heroicons/react/24/solid"
import { cn } from "@/lib/utils"

const filtros = [
  "Com local",
  "Até R$119,90",
  "Online",
  "Possui avaliações",
  "Fotos com rosto",
  "Com áudio",
  "Mídia de comparação recente",
]

export default function FiltrosRapidos() {
  const [ativos, setAtivos] = useState<string[]>([])
  const scrollRef = useRef<HTMLDivElement>(null)

  const toggleFiltro = (filtro: string) => {
    setAtivos((prev) =>
      prev.includes(filtro) ? prev.filter((f) => f !== filtro) : [...prev, filtro]
    )
  }

  const scroll = (dir: "left" | "right") => {
    if (!scrollRef.current) return
    const amount = 200
    scrollRef.current.scrollBy({
      left: dir === "left" ? -amount : amount,
      behavior: "smooth",
    })
  }

  return (
    <div className="relative flex items-center w-full">
      {/* Botão esquerda */}
      <button
        onClick={() => scroll("left")}
        className="cursor-pointer absolute left-1 top-1/2 -translate-y-1/2 bg-white/70 hover:bg-white rounded-full p-1 transition z-10 sm:hidden shadow-sm"
      >
        <ChevronLeftIcon className="w-5 h-5 text-gray-700" />
      </button>

      {/* Scroll container */}
      <div
        ref={scrollRef}
        className="flex items-center gap-2 overflow-x-auto scrollbar-none scroll-smooth w-full px-8 sm:px-0"
      >
        {filtros.map((filtro, i) => {
          const ativo = ativos.includes(filtro)
          return (
            <button
              key={i}
              onClick={() => toggleFiltro(filtro)}
              className={cn(
                "flex items-center gap-2 cursor-pointer border text-sm px-4 py-1.5 rounded-full whitespace-nowrap transition-all flex-shrink-0",
                ativo
                  ? "bg-[#ff72ce] border-[#ff72ce] text-white"
                  : "border-[#ff72ce] text-[#ff72ce] font-medium hover:bg-[#FC1EAD]/10"
              )}
            >
              {filtro}
              {ativo && <XMarkIcon className="w-4 h-4 text-white" />}
            </button>
          )
        })}
      </div>

      {/* Botão direita */}
      <button
        onClick={() => scroll("right")}
        className="cursor-pointer absolute right-1 top-1/2 -translate-y-1/2 bg-white/70 hover:bg-white rounded-full p-1 transition z-10 sm:hidden shadow-sm"
      >
        <ChevronRightIcon className="w-5 h-5 text-gray-700" />
      </button>
    </div>
  )
}
