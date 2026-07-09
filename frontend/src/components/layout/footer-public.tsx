'use client'

import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import {
  FacebookIcon,
  InstagramIcon,
  TwitterIcon,
  YoutubeIcon,
} from "lucide-react"
import { ArrowUpIcon } from "@heroicons/react/24/outline"
import { LoginModal } from "@/components/modals/login-modal"
import { usePathname, useRouter } from "next/navigation"
import { getPublicLogoUrl } from "@/lib/public-site-assets"

export default function FooterPublic() {
  const pathname = usePathname()
  const router = useRouter()

  const [showScrollTop, setShowScrollTop] = useState(false)
  const [loginOpen, setLoginOpen] = useState(false)

  useEffect(() => {
    const handleScroll = () => setShowScrollTop(window.scrollY > 400)
    window.addEventListener("scroll", handleScroll)
    return () => window.removeEventListener("scroll", handleScroll)
  }, [])

  // footer não aparece no admin (mesmo no público)
  if (pathname.startsWith("/admin")) return null

  const scrollToTop = () => window.scrollTo({ top: 0, behavior: "smooth" })

  // Público: se clicar em “Fale Conosco” ou “Publicar”, pede login
  const onFaleConosco = (e: React.MouseEvent) => {
    e.preventDefault()
    setLoginOpen(true)
  }

  return (
    <footer className="relative border-t mt-16 pb-3">
      <div className="max-w-6xl mx-auto px-6 py-16 grid grid-cols-1 md:grid-cols-4 gap-10 text-sm">
        {/* Sobre */}
        <div className="space-y-3">
          <img
            src={getPublicLogoUrl()}
            alt="Tops do Job"
            className="h-10 w-auto max-w-[200px] object-contain mb-3"
            width={200}
            height={80}
            fetchPriority="high"
          />
          <p className="text-gray-600 leading-relaxed text-justify">
            Desde 2025, o <strong>Tops do Job</strong> conecta acompanhantes e
            clientes de forma segura e discreta, promovendo confiança e
            visibilidade. Somos a plataforma de classificados premium do Brasil.
          </p>

          <div className="flex items-center gap-3 mt-4 text-gray-500">
            <a href="#" aria-label="Instagram" className="hover:text-pink-500">
              <InstagramIcon className="w-5 h-5" />
            </a>
            <a href="#" aria-label="Facebook" className="hover:text-pink-500">
              <FacebookIcon className="w-5 h-5" />
            </a>
            <a href="#" aria-label="Twitter" className="hover:text-pink-500">
              <TwitterIcon className="w-5 h-5" />
            </a>
            <a href="#" aria-label="YouTube" className="hover:text-pink-500">
              <YoutubeIcon className="w-5 h-5" />
            </a>
          </div>
        </div>

        {/* Legal */}
        <div>
          <h4 className="font-semibold text-gray-800 mb-4">Legal</h4>
          <ul className="space-y-2 text-gray-600">
            <li><a href="/termos-de-uso">Termos de Uso</a></li>
            <li><a href="/politica-de-privacidade">Política de Privacidade</a></li>
            <li><a href="/cookies">Política de Cookies</a></li>
          </ul>
        </div>

        {/* Suporte */}
        <div>
          <h4 className="font-semibold text-gray-800 mb-4">Suporte</h4>
          <ul className="space-y-2 text-gray-600">
            <li>
              <button onClick={onFaleConosco} className="hover:underline text-left">
                Fale Conosco
              </button>
            </li>
            <li><a href="/faq">FAQ</a></li>
            <li><a href="#">Blog</a></li>
          </ul>
        </div>

        {/* Empresa */}
        <div>
          <h4 className="font-semibold text-gray-800 mb-4">Empresa</h4>
          <ul className="space-y-2 text-gray-600">
            <li><a href="/sobre">Sobre Nós</a></li>
          </ul>
        </div>
      </div>

      <div className="border-t py-10 text-center">
        <h3 className="text-xl font-semibold mb-4">
          Aumente sua visibilidade agora mesmo
        </h3>

        <Button
          onClick={() => setLoginOpen(true)}
          className="bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold px-6 py-6"
        >
          PUBLICAR SEU ANÚNCIO
        </Button>
      </div>

      <div className="text-gray-700 text-center py-4 text-xs">
        <p>
          Copyright © {new Date().getFullYear()} Tops do Job. Todos os direitos
          reservados.
        </p>
      </div>

      {showScrollTop && (
        <Button
          onClick={scrollToTop}
          variant="outline"
          className="fixed flex bottom-6 items-center justify-center right-24 md:right-24 p-3 rounded-full shadow-lg transition-all duration-300"
          aria-label="Voltar ao topo"
        >
          <ArrowUpIcon className="w-4 h-4 mr-2" />
          <span>Voltar ao Início</span>
        </Button>
      )}

      <LoginModal open={loginOpen} onOpenChange={setLoginOpen} />
    </footer>
  )
}
