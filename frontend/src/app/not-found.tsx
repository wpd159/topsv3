import type { Metadata } from "next"
import Link from "next/link"
import { ArrowLeftIcon } from "@heroicons/react/24/solid"
import { PublicChrome } from "@/components/layout/public-chrome"

export const metadata: Metadata = {
  title: "Página não encontrada | Tops do Job",
  description: "A página solicitada não está disponível.",
  robots: { index: false, follow: true },
}

export default function NotFound() {
  return (
    <PublicChrome>
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
          <Link
            href="/"
            className="inline-flex items-center gap-2 rounded-full bg-[#FC1EAD] px-6 py-2 text-sm font-medium text-white transition-transform hover:bg-[#e01a9a] active:scale-95"
          >
            <ArrowLeftIcon className="w-4 h-4" />
            Voltar para Home
          </Link>
        </div>
      </main>
    </PublicChrome>
  )
}
