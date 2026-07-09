'use client'

import Image from "next/image"
import { cn } from "@/lib/utils"
import { ArrowRightIcon } from "@heroicons/react/24/solid"

interface CategoriaCardProps {
  titulo: string
  descricao: string
  imagem: string
  icon: React.ReactNode
  novo?: boolean
  destaque?: boolean
}

export function CategoriaCard({
  titulo,
  descricao,
  imagem,
  icon,
  novo,
  destaque,
}: CategoriaCardProps) {
  return (
    <div
      className={cn(
        "group relative overflow-hidden rounded-2xl border border-gray-100 bg-white transition-all duration-500 hover:-translate-y-1"
      )}
    >
      {/* Imagem + overlay */}
      <div className="relative w-full h-56 overflow-hidden">
        <Image
          src={imagem}
          alt={titulo}
          fill
          sizes="(max-width: 640px) calc(100vw - 3rem), (max-width: 768px) calc(50vw - 3rem), (max-width: 1024px) calc(33vw - 3rem), 258px"
          className="object-cover group-hover:scale-110 transition-transform duration-700 ease-out"
        />

        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/40 to-transparent opacity-80 group-hover:opacity-90 transition-all duration-300" />

        {/* Ícone + título */}
        <div className="absolute bottom-4 left-4 flex items-center gap-2">
          <span className="text-white text-2xl">{icon}</span>
          <h3 className="text-white text-xl font-semibold drop-shadow-md">
            {titulo}
          </h3>
        </div>
      </div>

      {/* Corpo */}
      <div className="p-5 flex flex-col justify-between h-[180px]">
        <p className="text-gray-700 text-sm leading-relaxed mb-4 line-clamp-3">
          {descricao}
        </p>

        <span
          className="flex w-full items-center justify-between rounded-md border border-gray-300 px-4 py-2 text-sm font-medium transition-all duration-300 group-hover:border-pink-600 group-hover:bg-pink-600 group-hover:text-white"
        >
          Ver mais
          <ArrowRightIcon className="w-4 h-4 ml-2" />
        </span>
      </div>
    </div>
  )
}
