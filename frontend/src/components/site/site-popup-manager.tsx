"use client"

import { useEffect, useMemo, useState } from "react"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { fetchPublicSiteContent } from "@/lib/site-content"

type AvisoPublico = {
  id: number
  titulo: string
  descricao: string
  localExibicao?: string
  frequenciaExibicao?: string
  permiteDispensar?: boolean
}

type AvisosResponse = {
  content?: AvisoPublico[]
}

function shouldDisplay(storageKey: string, frequency?: string) {
  if (typeof window === "undefined" || !storageKey) {
    return true
  }

  const saved = localStorage.getItem(storageKey)
  if (!saved) return true

  if (frequency === "UMA_VEZ") {
    return false
  }

  if (frequency === "DIARIO") {
    const savedDate = new Date(saved)
    const now = new Date()
    return (
      savedDate.getFullYear() !== now.getFullYear() ||
      savedDate.getMonth() !== now.getMonth() ||
      savedDate.getDate() !== now.getDate()
    )
  }

  return true
}

function markDisplayed(storageKey: string) {
  if (typeof window === "undefined" || !storageKey) return
  localStorage.setItem(storageKey, new Date().toISOString())
}

function apiUrl(path: string) {
  const base = (process.env.NEXT_PUBLIC_API_URL || "").replace(/\/$/, "")
  return `${base}${path.startsWith("/") ? "" : "/"}${path}`
}

export function SitePopupManager() {
  const [siteBanner, setSiteBanner] = useState<AvisoPublico | null>(null)
  const [loginPopup, setLoginPopup] = useState<AvisoPublico | null>(null)
  const [loginPopupOpen, setLoginPopupOpen] = useState(false)
  const [loginFallbackText, setLoginFallbackText] = useState("")

  const bannerStorageKey = useMemo(
    () => (siteBanner?.id ? `tops_site_banner_${siteBanner.id}` : ""),
    [siteBanner?.id]
  )
  const loginStorageKey = useMemo(
    () => (loginPopup?.id ? `tops_login_popup_${loginPopup.id}` : "tops_login_popup_fallback"),
    [loginPopup?.id]
  )

  useEffect(() => {
    fetchPublicSiteContent("popup-login")
      .then((entry) => setLoginFallbackText(entry.corpo || "Leia os avisos da plataforma antes de continuar."))
      .catch(() => null)

    fetch(apiUrl("/avisos/publico?localExibicao=SITE&page=0&size=1"), { cache: "no-store" })
      .then((res) => (res.ok ? res.json() : null))
      .then((data: AvisosResponse | null) => {
        const first = Array.isArray(data?.content) ? data!.content[0] : null
        if (!first) return
        if (!shouldDisplay(`tops_site_banner_${first.id}`, first.frequenciaExibicao)) {
          return
        }
        setSiteBanner(first)
      })
      .catch(() => null)

    fetch(apiUrl("/avisos/publico?localExibicao=LOGIN_POPUP&page=0&size=1"), {
      cache: "no-store",
    })
      .then((res) => (res.ok ? res.json() : null))
      .then((data: AvisosResponse | null) => {
        const first = Array.isArray(data?.content) ? data!.content[0] : null
        if (first) setLoginPopup(first)
      })
      .catch(() => null)
  }, [])

  useEffect(() => {
    const handler = () => {
      if (!shouldDisplay(loginStorageKey, loginPopup?.frequenciaExibicao)) {
        return
      }
      setLoginPopupOpen(true)
    }

    window.addEventListener("tops:login-success", handler)
    return () => window.removeEventListener("tops:login-success", handler)
  }, [loginPopup?.frequenciaExibicao, loginStorageKey])

  const dismissBanner = () => {
    if (siteBanner?.id && typeof window !== "undefined") {
      markDisplayed(bannerStorageKey)
    }
    setSiteBanner(null)
  }

  const dismissLoginPopup = () => {
    markDisplayed(loginStorageKey)
    setLoginPopupOpen(false)
  }

  return (
    <>
      {siteBanner && (
        <div className="fixed inset-x-0 bottom-0 z-40 border-t border-pink-100 bg-pink-50/95 shadow-[0_-10px_30px_rgba(15,23,42,0.12)] backdrop-blur">
          <div className="mx-auto flex max-w-[1500px] items-center justify-between gap-4 px-4 py-3 text-sm text-gray-700 sm:px-6 lg:px-8">
            <div>
              <span className="font-semibold text-gray-900">{siteBanner.titulo}</span>
              <span className="ml-2 whitespace-pre-line">{siteBanner.descricao}</span>
            </div>

            {siteBanner.permiteDispensar !== false && (
              <Button variant="ghost" className="text-xs text-gray-600 hover:bg-pink-100" onClick={dismissBanner}>
                Fechar
              </Button>
            )}
          </div>
        </div>
      )}

      <Dialog open={loginPopupOpen} onOpenChange={setLoginPopupOpen}>
        <DialogContent className="sm:max-w-md rounded-xl">
          <DialogHeader>
            <DialogTitle>{loginPopup?.titulo || "Aviso Importante"}</DialogTitle>
            <DialogDescription className="text-gray-600">
              Informações relevantes para sua conta e sua segurança.
            </DialogDescription>
          </DialogHeader>

          <div className="rounded-xl border border-gray-100 bg-pink-50/40 p-4 text-sm text-gray-700 whitespace-pre-line">
            {loginPopup?.descricao || loginFallbackText}
          </div>

          <DialogFooter>
            <Button className="bg-[#FC1EAD] hover:bg-[#e01a9a] text-white" onClick={dismissLoginPopup}>
              Entendi
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
