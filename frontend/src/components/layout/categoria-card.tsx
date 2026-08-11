'use client'

import Image from "next/image"
import Link from "next/link"
import { useRef, useState, type MouseEvent, type ReactNode } from "react"
import { cn } from "@/lib/utils"
import { ArrowPathIcon, ArrowRightIcon } from "@heroicons/react/24/solid"

interface CategoriaCardProps {
  titulo: string
  descricao: string
  imagem: string
  icon: ReactNode
  destino: string
  novo?: boolean
  destaque?: boolean
}

export function CategoriaCard({
  titulo,
  descricao,
  imagem,
  icon,
  destino,
  novo,
  destaque,
}: CategoriaCardProps) {
  const [pending, setPending] = useState(false)
  const pendingRef = useRef(false)

  const handleClick = (event: MouseEvent<HTMLAnchorElement>) => {
    if (
      event.button !== 0
      || event.metaKey
      || event.ctrlKey
      || event.shiftKey
      || event.altKey
    ) {
      return
    }
    if (pendingRef.current) {
      event.preventDefault()
      return
    }
    pendingRef.current = true
    setPending(true)
  }

  return (
    <Link
      href={destino}
      onClick={handleClick}
      aria-busy={pending}
      className={cn(
        "group relative block h-full overflow-hidden rounded-2xl border border-pink-100 bg-white shadow-[0_0_16px_rgba(252,30,173,0.08)] transition-all duration-300 hover:-translate-y-0.5 hover:border-pink-200 hover:shadow-[0_0_24px_rgba(252,30,173,0.16)] active:scale-[0.99] active:border-pink-400",
        pending && "pointer-events-none border-pink-400 ring-2 ring-pink-200",
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
          role={pending ? "status" : undefined}
          aria-live={pending ? "polite" : undefined}
          className="flex w-full items-center justify-between rounded-md border border-gray-300 px-4 py-2 text-sm font-medium transition-all duration-300 group-hover:border-pink-600 group-hover:bg-pink-600 group-hover:text-white"
        >
          {pending ? "Abrindo..." : "Ver mais"}
          {pending ? (
            <ArrowPathIcon className="ml-2 h-4 w-4 animate-spin" aria-hidden="true" />
          ) : (
            <ArrowRightIcon className="ml-2 h-4 w-4" aria-hidden="true" />
          )}
        </span>
      </div>
    </Link>
  )
}
