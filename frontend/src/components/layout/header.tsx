'use client'

import { useEffect, useState } from "react"
import { useRouter, usePathname } from "next/navigation"
import Image from "next/image"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import {
  UserCircleIcon,
  Bars3Icon,
  XMarkIcon,
} from "@heroicons/react/24/solid"
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet"
import { LoginModal } from "@/components/modals/login-modal"
import { RegisterModal } from "../modals/register-modal"
import { HeaderSkeleton } from "./header-skeleton"
import { getPublicLogoUrl } from "@/lib/public-site-assets"

const MOBILE_OVERLAY_CLOSE_MS = 220

export default function Header() {
  const router = useRouter()
  const pathname = usePathname()
  const [open, setOpen] = useState(false)
  const [loginModalOpen, setLoginModalOpen] = useState(false)
  const [registerModalOpen, setRegisterModalOpen] = useState(false)
  const [redirectAfterLogin, setRedirectAfterLogin] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const isActive = (path: string) => pathname === path

  const closeSheetThen = (action: () => void) => {
    setOpen(false)
    window.setTimeout(action, MOBILE_OVERLAY_CLOSE_MS)
  }

  const isMobileViewport = () =>
    typeof window !== "undefined" &&
    window.matchMedia("(max-width: 767px)").matches

  const getRegisterHref = (nextPath?: string | null) => {
    if (nextPath && nextPath.startsWith("/") && !nextPath.startsWith("//")) {
      return `/registrar?next=${encodeURIComponent(nextPath)}`
    }

    return "/registrar"
  }

  const go = (to: string) => {
    if (open) {
      closeSheetThen(() => router.push(to))
      return
    }

    router.push(to)
  }

  const openLogin = (nextPath?: string) => {
    setRedirectAfterLogin(nextPath ?? null)

    if (open) {
      closeSheetThen(() => setLoginModalOpen(true))
      return
    }

    setLoginModalOpen(true)
  }

  const openRegister = (nextPath?: string | null) => {
    setRedirectAfterLogin(nextPath ?? null)

    const navigateToRegisterPage = () => router.push(getRegisterHref(nextPath))

    if (isMobileViewport()) {
      if (open) {
        closeSheetThen(navigateToRegisterPage)
        return
      }

      navigateToRegisterPage()
      return
    }

    if (open) {
      closeSheetThen(() => setRegisterModalOpen(true))
      return
    }

    setRegisterModalOpen(true)
  }

  const openPublishFlow = () => {
    openLogin("/anunciar/wizard")
  }

  useEffect(() => {
    const timeout = setTimeout(() => setLoading(false), 500)
    return () => clearTimeout(timeout)
  }, [])

  if (loading) return <HeaderSkeleton />

  return (
    <>
      <header className="flex items-center justify-between px-6 md:py-10 py-5 bg-white border-b relative">
        {/* Logo */}
        <div className="flex items-center gap-2">
          <Image
            src={getPublicLogoUrl()}
            alt="Logo"
            width={140}
            height={50}
            priority
            fetchPriority="high"
            unoptimized
            className="cursor-pointer h-12 w-auto max-w-[200px] object-contain"
            onClick={() => router.push("/")}
          />
          <div className="text-xs text-gray-500 hidden sm:block">Brasil</div>
        </div>

        {/* Links principais */}
        <nav className="hidden md:flex items-start gap-8 font-normal text-md cursor-pointer">
          {[
            { label: "Acompanhantes", href: "/acompanhantes" },
            { label: "Anúncios", href: "/anuncios" },
          ].map(({ label, href }) => (
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
          ))}
        </nav>

        {/* Ações desktop */}
        <div className="hidden md:flex items-center gap-4 relative">
          <Button
            variant="ghost"
            className="flex items-center gap-2 text-gray-600 py-5 hover:text-gray-900"
            onClick={() => openLogin()}
          >
            <UserCircleIcon className="w-7 h-7" />
            <span>Entrar</span>
          </Button>

          <Button
            variant="outline"
            className="border-gray-300 text-gray-700 hover:bg-gray-100 py-5"
            onClick={() => openRegister()}
          >
            Registrar-se
          </Button>

          <Button
            variant="default"
            className="bg-[#FC1EAD] hover:bg-[#e01a9a] cursor-pointer text-white font-semibold px-5 py-5"
            onClick={openPublishFlow}
          >
            PUBLICAR SEU ANÚNCIO
          </Button>
        </div>

        {/* Mobile */}
        <div className="md:hidden flex items-center">
          <Sheet open={open} onOpenChange={setOpen}>
            <SheetTrigger asChild>
              <Button variant="ghost" size="icon">
                {open ? (
                  <XMarkIcon className="size-8 text-gray-700" />
                ) : (
                  <Bars3Icon className="size-8 text-gray-700" />
                )}
              </Button>
            </SheetTrigger>

            <SheetContent side="left" className="w-[100%] sm:w-[350px] p-6">
              <SheetHeader>
                <SheetTitle className="text-lg justify-center items-center font-semibold">
                  <Image
                    src={getPublicLogoUrl()}
                    alt="Logo"
                    width={130}
                    height={40}
                    priority
                    fetchPriority="high"
                    unoptimized
                    className="cursor-pointer h-16 w-auto max-w-[200px] mx-auto object-contain"
                    onClick={() => go("/")}
                  />
                </SheetTitle>
              </SheetHeader>

              <div className="-mt-2 space-y-6">
                {/* Menu principal mobile */}
                <div className="flex flex-col gap-3 text-gray-700 text-base font-medium">
                  {[
                    { label: "Acompanhantes", href: "/acompanhantes" },
                    { label: "Anúncios", href: "/anuncios" },
                  ].map(({ label, href }) => (
                    <Link
                      key={href}
                      href={href}
                      onClick={() => setOpen(false)}
                      className={`text-left transition ${
                        isActive(href)
                          ? "text-[#FC1EAD] font-semibold"
                          : "hover:text-[#FC1EAD]"
                      }`}
                    >
                      {label}
                    </Link>
                  ))}
                </div>

                {/* Divider */}
                <div className="border-t pt-5 flex flex-col gap-3">
                  <Button
                    variant="ghost"
                    className="justify-start text-gray-700"
                    onClick={() => openLogin()}
                  >
                    <UserCircleIcon className="w-5 h-5 mr-2" />
                    Entrar
                  </Button>

                  <Button
                    variant="outline"
                    className="border-gray-300 text-gray-700 hover:bg-gray-100 justify-start"
                    onClick={() => openRegister()}
                  >
                    Registrar-se
                  </Button>

                  <Button
                    className="bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold justify-start"
                    onClick={openPublishFlow}
                  >
                    PUBLICAR SEU ANÚNCIO
                  </Button>
                </div>
              </div>
            </SheetContent>
          </Sheet>
        </div>
      </header>

      {/* Modais */}
      <LoginModal
        open={loginModalOpen}
        onOpenChange={setLoginModalOpen}
        onOpenRegister={() => setRegisterModalOpen(true)}
        redirectAfterSuccess={redirectAfterLogin}
      />

      <RegisterModal
        open={registerModalOpen}
        onOpenChange={setRegisterModalOpen}
        onBackToLogin={() => {
          window.setTimeout(() => setLoginModalOpen(true), MOBILE_OVERLAY_CLOSE_MS)
        }}
      />
    </>
  )
}
