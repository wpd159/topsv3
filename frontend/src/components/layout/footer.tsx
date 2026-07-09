'use client'

import { type MouseEvent, useEffect, useState } from "react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { ArrowUpIcon } from "@heroicons/react/24/outline"
import { LoginModal } from "@/components/modals/login-modal"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/context/AuthContext"
import { fetchAllPublicSiteContent } from "@/lib/site-content"
import { getPublicLogoUrl } from "@/lib/public-site-assets"

const DEFAULT_ABOUT =
  "Desde 2025, o Tops do Job conecta acompanhantes e clientes de forma segura e discreta, promovendo confiança e visibilidade. Somos uma plataforma de classificados premium no Brasil."

const LINKS_SEO = [
  { nome: "Acompanhantes em Goiânia", href: "/acompanhantes/go/goiania" },
  { nome: "Acompanhantes em Brasília", href: "/acompanhantes/df/brasilia" },
  { nome: "Acompanhantes em Aparecida de Goiânia", href: "/acompanhantes/go/aparecida-de-goiania" },
  { nome: "Acompanhantes em São Paulo", href: "/acompanhantes/sp/sao-paulo" },
  { nome: "Acompanhantes no Rio de Janeiro", href: "/acompanhantes/rj/rio-de-janeiro" },
  { nome: "Acompanhantes em Belo Horizonte", href: "/acompanhantes/mg/belo-horizonte" },
  { nome: "Acompanhantes em Manaus", href: "/acompanhantes/am/manaus" },
  { nome: "Acompanhantes em Cuiabá", href: "/acompanhantes/mt/cuiaba" },
]

const PILARES_CONFIANCA = [
  "Contato direto com os anunciantes",
  "Privacidade e discrição na navegação",
  "Anúncios com moderação contínua",
  "Busca simples, rápida e segura",
]

export default function Footer() {
  const pathname = usePathname()
  const router = useRouter()
  const { usuario } = useAuth()
  const [showScrollTop, setShowScrollTop] = useState(false)
  const [loginOpen, setLoginOpen] = useState(false)
  const [sobreTexto, setSobreTexto] = useState(DEFAULT_ABOUT)

  useEffect(() => {
    const handleScroll = () => setShowScrollTop(window.scrollY > 400)
    window.addEventListener("scroll", handleScroll)
    return () => window.removeEventListener("scroll", handleScroll)
  }, [])

  useEffect(() => {
    let active = true
    fetchAllPublicSiteContent()
      .then((entries) => {
        if (!active) return
        const footerSummary = entries.find((entry) => entry.contentKey === "footer-resumo-institucional")
        const about = entries.find((entry) => entry.contentKey === "quem-somos")
        const footerSummaryPersistido =
          !!footerSummary?.id ||
          (!!footerSummary?.updatedBy && footerSummary.updatedBy !== "fallback")
        const resumo =
          (footerSummaryPersistido ? footerSummary?.corpo?.trim() : "") ||
          about?.corpo?.split(/\n{2,}/)[0]?.trim()
        if (resumo) setSobreTexto(resumo)
      })
      .catch(() => null)

    return () => {
      active = false
    }
  }, [])

  if (pathname.startsWith("/admin")) {
    return null
  }

  const onFaleConosco = (event: MouseEvent<HTMLButtonElement>) => {
    event.preventDefault()
    if (usuario) {
      window.dispatchEvent(new Event("open-suporte-ticket"))
      return
    }
    setLoginOpen(true)
  }

  const onPublicar = () => {
    if (usuario) {
      router.push("/anunciar/wizard")
      return
    }
    setLoginOpen(true)
  }

  return (
    <footer className="relative mt-16 border-t bg-white pb-3">
      <div className="mx-auto max-w-6xl px-6 py-14">
        <div className="grid grid-cols-1 gap-10 border-b pb-12 lg:grid-cols-[1.2fr_0.9fr_0.9fr_1.2fr]">
          <div className="space-y-4">
            <img
              src={getPublicLogoUrl()}
              alt="Tops do Job"
              className="h-10 w-auto max-w-[200px] object-contain"
              width={200}
              height={80}
            />
            <p className="max-w-md text-justify text-sm leading-relaxed text-gray-600">
              {sobreTexto}
            </p>

            <div className="pt-2">
              <h5 className="mb-3 text-sm font-semibold text-gray-800">Confiança e segurança</h5>
              <ul className="grid grid-cols-1 gap-2 text-sm text-gray-600 sm:grid-cols-2">
                {PILARES_CONFIANCA.map((item) => (
                  <li key={item} className="rounded-full border border-gray-200 bg-gray-50 px-3 py-2">
                    {item}
                  </li>
                ))}
              </ul>
            </div>

            <div className="flex items-center gap-3 pt-1">
              <a
                href="https://x.com/topsdojob"
                target="_blank"
                rel="noopener noreferrer"
                aria-label="Twitter X"
                className="transition hover:scale-105"
              >
                <svg
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                  className="h-6 w-6"
                  fill="#000000"
                >
                  <path d="M18.901 1.153h3.68l-8.04 9.19L24 22.847h-7.406l-5.8-7.584-6.638 7.584H.474l8.6-9.83L0 1.154h7.594l5.243 6.932 6.064-6.933Zm-1.29 19.494h2.04L6.486 3.24H4.298l13.313 17.407Z" />
                </svg>
              </a>
              <a
                href="https://www.tiktok.com/@topsdojob"
                target="_blank"
                rel="noopener noreferrer"
                aria-label="TikTok"
                className="transition hover:scale-105"
              >
                <svg
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                  className="h-6 w-6"
                  fill="none"
                >
                  <path
                    d="M14.2 3c.37 1.88 1.49 3.33 3.28 4.12a5.5 5.5 0 0 0 2.32.46v3.17a8.6 8.6 0 0 1-3.4-.69 8.92 8.92 0 0 1-1.97-1.14v6.02c0 3.07-2.5 5.56-5.58 5.56a5.57 5.57 0 0 1-4.64-8.66 5.58 5.58 0 0 1 7.88-1.49v3.35a2.6 2.6 0 0 0-3.72.84 2.58 2.58 0 0 0 2.22 3.93 2.58 2.58 0 0 0 2.58-2.57V3h3.03Z"
                    fill="#25F4EE"
                  />
                  <path
                    d="M15.03 3c.37 1.88 1.49 3.33 3.28 4.12a5.5 5.5 0 0 0 2.32.46v3.17a8.6 8.6 0 0 1-3.4-.69 8.92 8.92 0 0 1-1.97-1.14v6.02c0 3.07-2.5 5.56-5.58 5.56a5.57 5.57 0 0 1-4.64-8.66 5.58 5.58 0 0 1 7.88-1.49v3.35a2.6 2.6 0 0 0-3.72.84 2.58 2.58 0 0 0 2.22 3.93 2.58 2.58 0 0 0 2.58-2.57V3h3.03Z"
                    fill="#FE2C55"
                    opacity="0.85"
                    transform="translate(0.6 0.35)"
                  />
                  <path
                    d="M14.62 3c.37 1.88 1.49 3.33 3.28 4.12a5.5 5.5 0 0 0 2.32.46v3.17a8.6 8.6 0 0 1-3.4-.69 8.92 8.92 0 0 1-1.97-1.14v6.02c0 3.07-2.5 5.56-5.58 5.56a5.57 5.57 0 0 1-4.64-8.66 5.58 5.58 0 0 1 7.88-1.49v3.35a2.6 2.6 0 0 0-3.72.84 2.58 2.58 0 0 0 2.22 3.93 2.58 2.58 0 0 0 2.58-2.57V3h3.03Z"
                    fill="#111111"
                  />
                </svg>
              </a>
            </div>
          </div>

          <div>
            <h4 className="mb-4 text-sm font-semibold uppercase tracking-[0.08em] text-gray-800">Legal</h4>
            <ul className="space-y-2 text-sm text-gray-600">
              <li>
                <Link href="/termos-de-uso" className="transition hover:text-pink-500">
                  Termos da plataforma
                </Link>
              </li>
              <li>
                <Link href="/politica-de-privacidade" className="transition hover:text-pink-500">
                  Política de Privacidade
                </Link>
              </li>
              <li>
                <Link href="/cookies" className="transition hover:text-pink-500">
                  Política de Cookies
                </Link>
              </li>
              <li>
                <Link href="/politicas/verificacao-etaria" className="transition hover:text-pink-500">
                  Verificação Etária
                </Link>
              </li>
              <li>
                <Link href="/consentimento-promocional" className="transition hover:text-pink-500">
                  Consentimento Promocional
                </Link>
              </li>
              <li>
                <Link href="/aviso-seguranca-whatsapp" className="transition hover:text-pink-500">
                  Aviso de Segurança no WhatsApp
                </Link>
              </li>
            </ul>
          </div>

          <div>
            <h4 className="mb-4 text-sm font-semibold uppercase tracking-[0.08em] text-gray-800">Navegação</h4>
            <ul className="space-y-2 text-sm text-gray-600">
              <li>
                <Link href="/sobre" className="transition hover:text-pink-500">
                  Sobre o Tops do Job
                </Link>
              </li>
              <li>
                <Link href="/contato" className="transition hover:text-pink-500">
                  Contato
                </Link>
              </li>
              <li>
                <button
                  onClick={onFaleConosco}
                  className="block w-full text-left transition hover:text-pink-500"
                >
                  Fale Conosco
                </button>
              </li>
              <li>
                <Link href="/faq" className="transition hover:text-pink-500">
                  Central de ajuda
                </Link>
              </li>
              <li>
                <Link href="/blog" className="transition hover:text-pink-500">
                  Blog
                </Link>
              </li>
            </ul>
          </div>

          <div>
            <h4 className="mb-4 text-sm font-semibold uppercase tracking-[0.08em] text-gray-800">Cidades populares</h4>
            <ul className="grid grid-cols-1 gap-2 text-sm text-gray-600 sm:grid-cols-2 lg:grid-cols-1">
              {LINKS_SEO.map((link) => (
                <li key={link.href}>
                  <Link href={link.href} className="transition hover:text-pink-500 hover:underline">
                    {link.nome}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-6 py-10 lg:grid-cols-[1.2fr_auto] lg:items-center">
          <div className="space-y-2 text-center lg:text-left">
            <h3 className="text-2xl font-semibold text-gray-900">
              Aumente sua visibilidade e apareça para mais clientes
            </h3>
            <p className="max-w-2xl text-sm leading-relaxed text-gray-600">
              Destaque seu perfil nas listagens, receba mais contatos qualificados e fortaleça sua presença
              em uma plataforma com navegação simples, moderação ativa e atualização diária.
            </p>
          </div>

          <div className="flex justify-center lg:justify-end">
            <Button
              onClick={onPublicar}
              className="min-w-[220px] bg-[#FC1EAD] px-6 py-6 font-semibold text-white hover:bg-[#e01a9a]"
            >
              PUBLICAR SEU ANÚNCIO
            </Button>
          </div>
        </div>
      </div>

      <div className="border-t py-4 text-center text-xs text-gray-700">
        <p>Copyright © {new Date().getFullYear()} Tops do Job. Todos os direitos reservados.</p>
      </div>

      {showScrollTop && (
        <Button
          onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}
          variant="outline"
          className="fixed bottom-6 right-24 flex items-center justify-center rounded-full p-3 shadow-lg transition-all duration-300 md:right-24"
          aria-label="Voltar ao topo"
        >
          <ArrowUpIcon className="mr-2 h-4 w-4" />
          <span>Voltar ao Início</span>
        </Button>
      )}

      <LoginModal
        open={loginOpen}
        onOpenChange={setLoginOpen}
        redirectAfterSuccess="/anunciar/wizard"
      />
    </footer>
  )
}
