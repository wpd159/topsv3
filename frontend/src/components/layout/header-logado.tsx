"use client"

import { useState, useRef, useEffect } from "react"
import { useRouter, usePathname } from "next/navigation"
import Image from "next/image"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import {
  UserCircleIcon,
  Bars3Icon,
  XMarkIcon,
  ChevronDownIcon,
  HeartIcon,
  RectangleStackIcon,
  Cog6ToothIcon,
  ArrowRightOnRectangleIcon,
  ChatBubbleLeftRightIcon,
  ShieldCheckIcon,
  BanknotesIcon,
  TicketIcon,
  MegaphoneIcon,
  RocketLaunchIcon,
} from "@heroicons/react/24/solid"
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet"
import { useAuth } from "@/context/AuthContext"
import FeedbackDialog from "@/components/modals/feedback-dialog"
import { getPublicLogoUrl } from "@/lib/public-site-assets"

export default function HeaderLogado() {
  const router = useRouter()
  const pathname = usePathname()

  const [open, setOpen] = useState(false)
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [feedbackOpen, setFeedbackOpen] = useState(false)

  const dropdownRef = useRef<HTMLDivElement>(null)
  const hoverTimeout = useRef<NodeJS.Timeout | null>(null)
  const { usuario, logout, novasMensagens } = useAuth()

  const nome = usuario?.username || "Usuário"
  const cargo = usuario?.cargo || "Usuário"
  const role = usuario?.cargo?.toUpperCase() || ""
  const isAdmin = ["ADMIN", "MODERADOR"].includes(role)

  const go = (to: string) => {
    router.push(to)
    setOpen(false)
  }

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setDropdownOpen(false)
      }
    }
    document.addEventListener("mousedown", handleClickOutside)
    return () => document.removeEventListener("mousedown", handleClickOutside)
  }, [])

  const handleMouseEnter = () => {
    if (hoverTimeout.current) clearTimeout(hoverTimeout.current)
    setDropdownOpen(true)
  }

  const handleMouseLeave = () => {
    hoverTimeout.current = setTimeout(() => setDropdownOpen(false), 200)
  }

  const isActive = (path: string) => pathname === path

  return (
    <header className="flex items-center justify-between px-6 md:py-10 py-5 bg-white border-b relative">

      {/* Logo */}
      <div className="flex items-center gap-2">
        <Image
          src={getPublicLogoUrl()}
          alt="Logo"
          width={140}
          height={50}
          loading="lazy"
          unoptimized
          className="cursor-pointer h-12 w-auto max-w-[200px] object-contain"
          onClick={() => router.push("/")}
        />
        <div className="text-xs text-gray-500 hidden sm:block">Brasil</div>
      </div>

      {/* Links principais */}
      <nav className="hidden md:flex items-start gap-8 font-normal text-md cursor-pointer">
        {[{ label: "Acompanhantes", href: "/acompanhantes" }, { label: "Anúncios", href: "/anuncios" }].map(
          ({ label, href }) => (
            <Link
              key={href}
              className={`relative transition-all cursor-pointer pb-1 ${
                isActive(href)
                  ? "text-[#FC1EAD] font-semibold after:content-[''] after:absolute after:bottom-0 after:left-0 after:w-full after:h-[2px] after:bg-[#FC1EAD]"
                  : "text-gray-700 hover:text-[#FC1EAD]"
              }`}
              href={href}
            >
              {label}
            </Link>
          )
        )}
      </nav>

      {/* Ações desktop */}
      <div className="hidden md:flex items-center gap-4 relative">
        {!isAdmin && (
          <Button
            variant="default"
            className="bg-[#FC1EAD] hover:bg-[#e01a9a] cursor-pointer text-white font-semibold px-5 py-5"
            onClick={() => router.push("/anunciar/wizard")}
          >
            PUBLICAR SEU ANÚNCIO
          </Button>
        )}

        {/* Usuário logado */}
        <div
          ref={dropdownRef}
          className="relative"
          onMouseEnter={handleMouseEnter}
          onMouseLeave={handleMouseLeave}
        >
          <button
            onClick={() => setDropdownOpen(!dropdownOpen)}
            className="flex items-center gap-2 text-[#C41E73] font-medium text-sm hover:opacity-90 transition"
          >
            <UserCircleIcon className="w-7 h-7 text-[#C41E73]" />
            <span className="truncate max-w-[120px]">{nome}</span>
            <ChevronDownIcon className="w-4 h-4 text-gray-500" />
          </button>

          {dropdownOpen && (
            <div className="absolute right-0 mt-3 w-56 bg-white border border-gray-100 rounded-xl shadow-lg z-50">
              <ul className="text-sm text-gray-700 py-2">
                <li onClick={() => router.push("/minha-conta")} className="flex items-center gap-2 px-4 py-2 hover:bg-gray-50 cursor-pointer">
                  <Cog6ToothIcon className="w-5 h-5 text-gray-500" /> Minha Conta
                </li>

                <li onClick={() => router.push("/painel")} className="flex items-center gap-2 px-4 py-2 hover:bg-gray-50 cursor-pointer">
                  <RocketLaunchIcon className="w-5 h-5 text-gray-500" /> Painel Comercial
                </li>

                <li onClick={() => router.push("/meus-anuncios")} className="flex items-center gap-2 px-4 py-2 hover:bg-gray-50 cursor-pointer">
                  <RectangleStackIcon className="w-5 h-5 text-gray-500" /> Meus Anúncios
                </li>

                <li onClick={() => router.push("/creditos")} className="flex items-center gap-2 px-4 py-2 hover:bg-gray-50 cursor-pointer">
                  <BanknotesIcon className="w-5 h-5 text-gray-500" /> Créditos
                </li>

                <li onClick={() => router.push("/favoritos")} className="flex items-center gap-2 px-4 py-2 hover:bg-gray-50 cursor-pointer">
                  <HeartIcon className="w-5 h-5 text-gray-500" /> Favoritos
                </li>

                <li
                  onClick={() => {
                    router.push("/chat")
                  }}
                  className="relative flex items-center gap-2 px-4 py-2 hover:bg-gray-50 cursor-pointer"
                >
                  <ChatBubbleLeftRightIcon className="w-5 h-5 text-gray-500" /> Chat
                  {typeof novasMensagens === "number" && novasMensagens > 0 && (
                    <span className="ml-auto bg-[#FC1EAD] text-white text-[10px] font-bold w-5 h-5 rounded-full flex items-center justify-center animate-pulse">
                      {novasMensagens}
                    </span>
                  )}
                </li>

                {/* Meus Suportes */}
                <li onClick={() => router.push("/meus-tickets")} className="flex items-center gap-2 px-4 py-2 hover:bg-gray-50 cursor-pointer">
                  <TicketIcon className="w-5 h-5 text-gray-500" /> Meus Suportes
                </li>

                {/* 🔥 BOTÃO DESTACADO – Dar Sugestão */}
                <li
                  onClick={() => setFeedbackOpen(true)}
                  className="flex items-center gap-2 px-4 py-2 cursor-pointer
                             border border-[#FC1EAD]/40 rounded-md mt-1
                             text-[#C41E73] font-semibold hover:bg-[#FFF0F8] transition"
                >
                  <MegaphoneIcon className="w-5 h-5 text-[#FC1EAD]" />
                  Dar Sugestão / Reportar Bug
                </li>

                {isAdmin && (
                  <li
                    className="flex items-center gap-2 px-4 py-2 hover:bg-gray-50 cursor-pointer text-[#C41E73] font-medium"
                    onClick={() => router.push("/admin/dashboard")}
                  >
                    <ShieldCheckIcon className="w-5 h-5 text-[#C41E73]" />
                    Painel Administrativo
                  </li>
                )}

                <li className="border-t my-1"></li>

                <li
                  className="flex items-center gap-2 px-4 py-2 text-red-600 hover:bg-red-50 cursor-pointer"
                  onClick={() => {
                    logout()
                    router.push("/")
                  }}
                >
                  <ArrowRightOnRectangleIcon className="w-5 h-5 text-red-500" /> Sair
                </li>
              </ul>
            </div>
          )}
        </div>
      </div>

      {/* Mobile */}
      <div className="md:hidden flex items-center">
        <Sheet open={open} onOpenChange={setOpen}>
          <SheetTrigger asChild>
            <Button variant="ghost" size="icon">
              {open ? <XMarkIcon className="size-8 text-gray-700" /> : <Bars3Icon className="size-8 text-gray-700" />}
            </Button>
          </SheetTrigger>

          <SheetContent side="left" className="w-full sm:w-[350px] p-0 flex h-full flex-col">
            <div className="p-6 pb-0">
              <SheetHeader>
                <SheetTitle className="text-lg font-semibold">
                  <Image
                    src={getPublicLogoUrl()}
                    alt="Logo"
                    width={130}
                    height={40}
                    unoptimized
                    className="cursor-pointer h-16 w-auto max-w-[200px] mx-auto object-contain"
                    onClick={() => go("/")}
                  />
                </SheetTitle>
              </SheetHeader>
            </div>

            <div className="flex-1 overflow-y-auto px-6 pb-6 space-y-6">
              <div className="mt-4 flex flex-col gap-4 text-gray-700 text-base font-medium">
                {[{ label: "Acompanhantes", href: "/acompanhantes" }, { label: "Anúncios", href: "/anuncios" }, { label: "Créditos", href: "/creditos" }].map(
                  ({ label, href }) => (
                    <Link
                      key={href}
                      href={href}
                      onClick={() => setOpen(false)}
                      className={`text-left transition ${
                        isActive(href) ? "text-[#FC1EAD] font-semibold" : "hover:text-[#FC1EAD]"
                      }`}
                    >
                      {label}
                    </Link>
                  )
                )}
              </div>

              <div className="border-t pt-4">
                <Button
                  className="w-full bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold py-5"
                  onClick={() => go("/anunciar/wizard")}
                >
                  PUBLICAR ANÚNCIO
                </Button>
              </div>

              <div className="border-t pt-5 flex flex-col gap-3">
                <div className="flex items-center gap-2 mb-1">
                  <UserCircleIcon className="w-8 h-8 text-[#C41E73]" />
                  <div className="flex flex-col">
                    <span className="text-[#C41E73] font-semibold text-sm">{nome}</span>
                    <span className="text-xs text-gray-500">{cargo}</span>
                  </div>
                </div>

                <Button variant="ghost" className="justify-start text-gray-700" onClick={() => go("/minha-conta")}>
                  <Cog6ToothIcon className="w-5 h-5 mr-2" /> Minha Conta
                </Button>

                <Button variant="ghost" className="justify-start text-gray-700" onClick={() => go("/painel")}>
                  <RocketLaunchIcon className="w-5 h-5 mr-2" /> Painel Comercial
                </Button>

                <Button variant="ghost" className="justify-start text-gray-700" onClick={() => go("/meus-anuncios")}>
                  <RectangleStackIcon className="w-5 h-5 mr-2" /> Meus Anúncios
                </Button>

                <Button variant="ghost" className="justify-start text-gray-700" onClick={() => go("/creditos")}>
                  <BanknotesIcon className="w-5 h-5 mr-2" /> Créditos
                </Button>

                <Button variant="ghost" className="justify-start text-gray-700" onClick={() => go("/favoritos")}>
                  <HeartIcon className="w-5 h-5 mr-2" /> Favoritos
                </Button>

                <Button
                  variant="ghost"
                  className="justify-start text-gray-700 relative"
                  onClick={() => {
                    go("/chat")
                  }}
                >
                  <ChatBubbleLeftRightIcon className="w-5 h-5 mr-2" /> Chat
                  {typeof novasMensagens === "number" && novasMensagens > 0 && (
                    <span className="absolute right-6 top-2 bg-[#FC1EAD] text-white text-[10px] font-bold w-5 h-5 rounded-full flex items-center justify-center animate-pulse">
                      {novasMensagens}
                    </span>
                  )}
                </Button>

                <Button variant="ghost" className="justify-start text-gray-700" onClick={() => go("/meus-tickets")}>
                  <TicketIcon className="w-5 h-5 mr-2" /> Meus Suportes
                </Button>

                {/* 🔥 DESTACADO NO MOBILE */}
                <Button
                  variant="ghost"
                  className="justify-start text-[#C41E73] font-semibold border border-[#FC1EAD]/40 rounded-md hover:bg-[#FFF0F8] transition"
                  onClick={() => setFeedbackOpen(true)}
                >
                  <MegaphoneIcon className="w-5 h-5 mr-2 text-[#FC1EAD]" />
                  Dar Sugestão / Reportar Bug
                </Button>

                {isAdmin && (
                  <Button
                    variant="ghost"
                    className="justify-start text-[#C41E73] font-medium"
                    onClick={() => go("/admin/dashboard")}
                  >
                    <ShieldCheckIcon className="w-5 h-5 mr-2 text-[#C41E73]" /> Painel Administrativo
                  </Button>
                )}

                <Button
                  variant="ghost"
                  className="justify-start text-red-600"
                  onClick={() => {
                    logout()
                    setOpen(false)
                    router.push("/")
                  }}
                >
                  <ArrowRightOnRectangleIcon className="w-5 h-5 mr-2" /> Sair
                </Button>
              </div>
            </div>

            {false && !isAdmin && (
              <div className="p-6 pt-3 border-t bg-white">
                <Button
                  className="w-full bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold py-5"
                  onClick={() => go("/anunciar/wizard")}
                >
                  PUBLICAR SEU ANÚNCIO
                </Button>
              </div>
            )}
          </SheetContent>
        </Sheet>
      </div>

      {/* FEEDBACK DIALOG */}
      <FeedbackDialog open={feedbackOpen} onOpenChange={setFeedbackOpen} />

    </header>
  )
}
