'use client'

import { useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import Image from 'next/image'

import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { MagnifyingGlassIcon } from '@heroicons/react/24/solid'

export default function Hero() {
  const router = useRouter()

  const [busca, setBusca] = useState('')
  const [loading, setLoading] = useState(false)
  const inputRef = useRef<HTMLInputElement | null>(null)
  const pendingNavigationRef = useRef<number | null>(null)

  useEffect(() => {
    return () => {
      if (pendingNavigationRef.current !== null) {
        window.clearTimeout(pendingNavigationRef.current)
      }
    }
  }, [])

  const pushBuscaRoute = (href: string) => {
    if (typeof window === 'undefined') {
      router.push(href)
      return
    }

    const isMobile = window.matchMedia('(max-width: 767px)').matches
    const activeElement = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const shouldCloseKeyboard = isMobile && activeElement === inputRef.current

    if (!shouldCloseKeyboard) {
      router.push(href)
      return
    }

    if (!activeElement) {
      router.push(href)
      return
    }

    activeElement.blur()

    if (pendingNavigationRef.current !== null) {
      window.clearTimeout(pendingNavigationRef.current)
    }

    pendingNavigationRef.current = window.setTimeout(() => {
      pendingNavigationRef.current = null
      router.push(href)
    }, 120)
  }

  const handleBuscar = () => {
    const params = new URLSearchParams()
    const b = busca.trim()
    if (b) params.set('busca', b)

    setLoading(true)
    pushBuscaRoute(params.toString() ? `/anuncios?${params.toString()}` : '/anuncios')
  }

  return (
    <section className="relative mt-3 flex h-[300px] w-full items-center justify-center overflow-hidden rounded-2xl sm:mt-10 sm:h-[500px]">
      {/* Fundo */}
      <div className="absolute inset-0">
        <Image
          src="/2151117281.jpg"
          alt="Imagem temática de fundo"
          fill
          unoptimized
          priority
          fetchPriority='high'
          sizes="(max-width: 640px) calc(100vw - 2rem), (max-width: 1024px) calc(100vw - 3rem), 1452px"
          quality={85}
          className="object-cover rounded-2xl"
        />
        <div className="absolute inset-0 bg-black/80 rounded-2xl" />
      </div>

      {/* Conteúdo */}
      <div className="relative z-10 w-full max-w-5xl px-4 text-center text-white">
        <h1 className="mb-2 text-xl font-bold leading-snug sm:mb-4 sm:text-5xl sm:leading-tight">
          Encontre <span className="text-[#FC1EAD]">acompanhantes</span> perto de você
        </h1>

        <p className="mb-4 text-xs text-gray-200 sm:mb-10 sm:text-lg">
          Veja anúncios de acompanhantes na sua cidade e região, com contato direto pelo WhatsApp.
        </p>

        {/* Barra (1 input + botão) */}
        <div className="mx-auto w-full max-w-4xl rounded-2xl bg-black/35 p-2 shadow-2xl sm:p-4">
          <div className="flex flex-col gap-2 md:flex-row md:items-center md:gap-3">
            <div className="relative w-full">
              <MagnifyingGlassIcon className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-gray-400" />
              <Input
                ref={inputRef}
                placeholder="Digite cidade, bairro, categoria ou característica..."
                value={busca}
                onChange={(e) => setBusca(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleBuscar()}
                className="h-11 w-full rounded-xl border-0 bg-white pl-12 pr-4 text-gray-800 placeholder:text-gray-500 focus-visible:ring-2 focus-visible:ring-pink-400 sm:h-14"
              />
            </div>

            <Button
              onClick={handleBuscar}
              disabled={loading}
              className="h-11 w-full rounded-xl bg-[#FC1EAD] font-semibold text-white hover:bg-[#e01a9a] md:h-14 md:w-[180px]"
            >
              {loading ? 'Buscando...' : 'Buscar'}
            </Button>
          </div>
        </div>
      </div>
    </section>
  )
}
