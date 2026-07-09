"use client"

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Checkbox } from "@/components/ui/checkbox"
import { fetchPublicSiteContent } from "@/lib/site-content"

type OpenRequest = {
  url: string
  onContinue?: () => void
}

type ContextValue = {
  openWhatsAppWarning: (request: OpenRequest) => void
}

const STORAGE_KEY = "tops_whatsapp_warning_hidden"
const WhatsAppSafetyContext = createContext<ContextValue | undefined>(undefined)

export function WhatsAppSafetyProvider({ children }: { children: ReactNode }) {
  const [request, setRequest] = useState<OpenRequest | null>(null)
  const [hideNextTime, setHideNextTime] = useState(false)
  const [message, setMessage] = useState(
    "A Tops do Job não intermedeia encontros nem pagamentos antecipados. Confirme identidade e condições diretamente com o anunciante."
  )

  useEffect(() => {
    fetchPublicSiteContent("texto-whatsapp")
      .then((entry) => {
        if (entry?.corpo) setMessage(entry.corpo)
      })
      .catch(() => null)
  }, [])

  const value = useMemo<ContextValue>(
    () => ({
      openWhatsAppWarning: (nextRequest) => {
        if (typeof window !== "undefined" && localStorage.getItem(STORAGE_KEY) === "1") {
          nextRequest.onContinue?.()
          window.open(nextRequest.url, "_blank", "noopener,noreferrer")
          return
        }
        setHideNextTime(false)
        setRequest(nextRequest)
      },
    }),
    []
  )

  const close = () => setRequest(null)

  const handleContinue = () => {
    if (!request) return
    if (hideNextTime && typeof window !== "undefined") {
      localStorage.setItem(STORAGE_KEY, "1")
    }
    request.onContinue?.()
    window.open(request.url, "_blank", "noopener,noreferrer")
    close()
  }

  return (
    <WhatsAppSafetyContext.Provider value={value}>
      {children}

      <Dialog open={Boolean(request)} onOpenChange={(open) => !open && close()}>
        <DialogContent className="sm:max-w-md rounded-xl">
          <DialogHeader>
            <DialogTitle className="text-gray-900">Aviso de segurança</DialogTitle>
            <DialogDescription className="text-gray-600">
              Antes de seguir para o WhatsApp, leia com atenção.
            </DialogDescription>
          </DialogHeader>

          <div className="rounded-xl border border-gray-100 bg-pink-50/40 p-4 text-sm text-gray-700 whitespace-pre-line">
            {message}
          </div>

          <label className="flex items-center gap-3 text-sm text-gray-600">
            <Checkbox checked={hideNextTime} onCheckedChange={(checked) => setHideNextTime(Boolean(checked))} />
            <span>Não exibir novamente</span>
          </label>

          <DialogFooter className="flex gap-2 sm:justify-end">
            <Button variant="outline" onClick={close}>
              Cancelar
            </Button>
            <Button className="bg-[#25D366] hover:bg-[#20bd5a] text-white" onClick={handleContinue}>
              Continuar
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </WhatsAppSafetyContext.Provider>
  )
}

export function useWhatsAppSafety() {
  const context = useContext(WhatsAppSafetyContext)
  if (!context) {
    throw new Error("useWhatsAppSafety deve ser usado dentro de WhatsAppSafetyProvider")
  }
  return context
}
