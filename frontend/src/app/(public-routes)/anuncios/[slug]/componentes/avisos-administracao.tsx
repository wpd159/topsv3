"use client"

import { useEffect, useState } from "react"
import {
  ClockIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  MegaphoneIcon,
} from "@heroicons/react/24/solid"

type AvisoPublico = {
  id: number
  titulo: string
  descricao: string
  criadoEm?: string
}

type AvisosResponse = {
  content?: AvisoPublico[]
}

export function AvisosAdministracao() {
  const [avisos, setAvisos] = useState<AvisoPublico[]>([])
  const [loading, setLoading] = useState(true)
  const [index, setIndex] = useState(0)

  useEffect(() => {
    ;(async () => {
      try {
        const res = await fetch(
          `${process.env.NEXT_PUBLIC_API_URL}/avisos/publico?localExibicao=ANUNCIO_RODAPE&page=0&size=5`,
          { cache: "no-store" }
        )

        if (!res.ok) {
          setAvisos([])
          return
        }

        const data: AvisosResponse = await res.json()
        const lista = Array.isArray(data?.content) ? data.content : []
        setAvisos(lista)
        setIndex(0)
      } catch {
        setAvisos([])
      } finally {
        setLoading(false)
      }
    })()
  }, [])

  useEffect(() => {
    if (avisos.length <= 1) return

    const interval = setInterval(() => {
      setIndex((prev) => (prev + 1) % avisos.length)
    }, 6000)

    return () => clearInterval(interval)
  }, [avisos.length])

  if (loading) return null
  if (!avisos.length) return null

  const prev = () => setIndex((prev) => (prev - 1 + avisos.length) % avisos.length)
  const next = () => setIndex((prev) => (prev + 1) % avisos.length)

  const avisoAtual = avisos[index]
  const isCarousel = avisos.length > 1

  return (
    <div className="bg-white rounded-xl border border-gray-200 p-5">
      <div className="flex items-center justify-between w-full mb-4">
        <div className="flex items-center gap-2">
          <h2 className="text-base font-semibold text-gray-900">
            Avisos da administração
          </h2>
        </div>

        {isCarousel && (
          <div className="flex gap-2">
            <button
              onClick={prev}
              className="p-1.5 rounded-full hover:bg-gray-100 transition"
              aria-label="Aviso anterior"
            >
              <ChevronLeftIcon className="w-5 h-5 text-gray-600" />
            </button>
            <button
              onClick={next}
              className="p-1.5 rounded-full hover:bg-gray-100 transition"
              aria-label="Próximo aviso"
            >
              <ChevronRightIcon className="w-5 h-5 text-gray-600" />
            </button>
          </div>
        )}
      </div>

      <div className="rounded-lg border border-gray-100 bg-pink-50/40 p-4 min-h-[10px]">
        <h3 className="text-sm font-semibold text-gray-900">
          {avisoAtual.titulo}
        </h3>

        <p className="text-sm text-gray-600 mt-2 whitespace-pre-line line-clamp-5">
          {avisoAtual.descricao}
        </p>

        {avisoAtual.criadoEm && (
          <div className="mt-3 flex items-center gap-1 text-xs text-gray-400">
            <ClockIcon className="w-3.5 h-3.5" />
            <span>
              {new Date(avisoAtual.criadoEm).toLocaleDateString("pt-BR")}
            </span>
          </div>
        )}
      </div>

      {isCarousel && (
        <div className="flex gap-1 mt-4 justify-center">
          {avisos.map((_, i) => (
            <button
              key={i}
              onClick={() => setIndex(i)}
              aria-label={`Ir para aviso ${i + 1}`}
              className={`w-2 h-2 rounded-full transition-all ${
                i === index ? "bg-pink-500 scale-110" : "bg-gray-300"
              }`}
            />
          ))}
        </div>
      )}
    </div>
  )
}
