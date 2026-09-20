'use client'

import { useEffect, useState } from 'react'
import { usePathname } from 'next/navigation'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import {
  CheckCircleIcon,
  ExclamationTriangleIcon,
  XCircleIcon,
} from '@heroicons/react/24/outline'
import { useSiteContent } from '@/components/site-content/site-content-provider'
import { SafeInstitutionalText } from '@/components/site-content/safe-site-content-body'
import { CookieOptions } from '@/components/site/cookie-options'
import {
  CONSENT_EVENT,
  allCookiesConsent,
  defaultConsent,
  persistCookieConsent,
  readCookieConsent,
  type ConsentState,
} from '@/lib/cookie-consent'
import {
  confirmarAceiteGlobal,
  obterStatusVisitante,
} from '@/lib/compliance/visitor-access'

type AgeGateModalProps = {
  termsHref?: string
  denyRedirect?: string
}

const AGE_GATE_CONFIRMATION_ERROR = 'Nao foi possivel confirmar o aceite. Tente novamente.'

export function AgeGateModal({
  termsHref = '/termos-de-uso',
  denyRedirect = 'https://www.google.com',
}: AgeGateModalProps) {
  const pathname = usePathname()
  const legalNotice = useSiteContent('popup-login')
  const [ageAccepted, setAgeAccepted] = useState<boolean | null>(null)
  const [storedConsent, setStoredConsent] = useState<ConsentState | null>(null)
  const [choice, setChoice] = useState<ConsentState>(defaultConsent)
  const [personalizing, setPersonalizing] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const excludedPath = pathname === '/termos-de-uso' || pathname === '/registrar'
  const needsCookies = storedConsent === null
  // A política pode ser consultada sem escolher cookies; a confirmação etária permanece exigida.
  const open = !excludedPath && ageAccepted !== null
    && (!ageAccepted || (needsCookies && (pathname !== '/cookies' || error !== null)))

  useEffect(() => {
    const syncConsent = () => setStoredConsent(readCookieConsent())
    syncConsent()
    window.addEventListener(CONSENT_EVENT, syncConsent)
    return () => window.removeEventListener(CONSENT_EVENT, syncConsent)
  }, [])

  useEffect(() => {
    setStoredConsent(readCookieConsent())
    setPersonalizing(false)
    setChoice(defaultConsent)
    setError(null)
    setAgeAccepted(null)
    if (excludedPath) {
      return
    }

    let active = true
    void obterStatusVisitante(true)
      .then((status) => {
        if (active) setAgeAccepted(status.globalAccepted === true)
      })
      .catch(() => {
        if (active) setAgeAccepted(false)
      })
    return () => {
      active = false
    }
  }, [pathname, excludedPath])

  async function accept(cookieChoice?: ConsentState) {
    if (submitting) return
    setSubmitting(true)
    setError(null)
    let confirmingAge = !ageAccepted
    try {
      if (confirmingAge) {
        await confirmarAceiteGlobal(pathname || '/')
        setAgeAccepted(true)
        confirmingAge = false
      }
      // A confirmação etária falha antes de qualquer escrita de cookies.
      // Uma preferência válida preexistente (inclusive recusa) é preservada.
      const current = readCookieConsent()
      setStoredConsent(cookieChoice && !current ? persistCookieConsent(cookieChoice) : current)
    } catch {
      setError(confirmingAge
        ? AGE_GATE_CONFIRMATION_ERROR
        : 'Nao foi possivel salvar as preferencias. Tente novamente.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
      <Dialog open={open} onOpenChange={() => {}}>
        <DialogContent
          className="flex h-[100dvh] max-h-[100dvh] w-full max-w-none flex-col overflow-hidden rounded-none border-0 p-0 sm:grid sm:h-auto sm:max-h-[85vh] sm:w-full sm:max-w-md sm:overflow-y-auto sm:rounded-2xl sm:border sm:p-6 [&_[data-slot=dialog-close]]:right-[max(1rem,env(safe-area-inset-right))] [&_[data-slot=dialog-close]]:top-[max(1rem,env(safe-area-inset-top))] sm:[&_[data-slot=dialog-close]]:right-4 sm:[&_[data-slot=dialog-close]]:top-4"
          data-age-gate-layout
        >
          <DialogHeader className="contents space-y-2 text-center sm:flex">
            <div
              className="mx-auto mt-[max(1.25rem,env(safe-area-inset-top))] flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-pink-100 sm:mt-0"
              data-age-gate-header
            >
              <ExclamationTriangleIcon className="h-7 w-7 text-[#FC1EAD]" />
            </div>
            <DialogTitle className="mt-2 shrink-0 pl-[max(1.5rem,env(safe-area-inset-left))] pr-[max(3.5rem,env(safe-area-inset-right))] text-xl font-bold sm:mt-0 sm:px-0">
              {ageAccepted ? 'Suas preferências de cookies' : legalNotice.titulo}
            </DialogTitle>
            <DialogDescription asChild>
              <div
                className="mt-2 min-h-0 flex-1 overflow-y-auto whitespace-pre-line pl-[max(1.5rem,env(safe-area-inset-left))] pr-[max(1.5rem,env(safe-area-inset-right))] text-justify text-gray-600 sm:mt-0 sm:flex-none sm:overflow-visible sm:px-0"
                data-age-gate-body
              >
                {!ageAccepted ? (
                  <>
                    <SafeInstitutionalText content={legalNotice.corpo} />
                    <p className="mt-3 text-center text-sm">
                      Ao escolher uma opção para entrar, declaro que sou maior de 18 anos e li os{' '}
                      <a href={termsHref} className="text-[#FC1EAD] underline underline-offset-2">
                        Termos de Uso
                      </a>.
                    </p>
                  </>
                ) : null}
                {needsCookies ? (
                  <div className="mt-4 space-y-4 border-t pt-4 text-left">
                    <p className="text-sm">
                      Cookies opcionais dependem da sua escolha. Você pode entrar somente com os
                      necessários e alterar ou revogar sua escolha depois em{' '}
                      <a href="/cookies" className="text-[#FC1EAD] underline underline-offset-2">
                        Preferências de cookies
                      </a>.
                    </p>
                    {personalizing ? (
                      <div id="age-gate-cookie-options">
                        <CookieOptions consent={choice} onChange={setChoice} disabled={submitting} />
                      </div>
                    ) : null}
                  </div>
                ) : null}
              </div>
            </DialogDescription>
          </DialogHeader>

          {error ? (
            <p
              role="alert"
              className="mt-3 shrink-0 pl-[max(1.5rem,env(safe-area-inset-left))] pr-[max(1.5rem,env(safe-area-inset-right))] text-center text-sm text-red-600 sm:px-0"
            >
              {error}
            </p>
          ) : null}

          <div
            className="mt-4 grid shrink-0 grid-cols-2 gap-3 border-t bg-background pl-[max(1.5rem,env(safe-area-inset-left))] pr-[max(1.5rem,env(safe-area-inset-right))] pb-[max(1rem,env(safe-area-inset-bottom))] pt-4 sm:mt-6 sm:border-0 sm:bg-transparent sm:p-0"
            data-age-gate-footer
          >
            {needsCookies ? (
              <>
                <Button
                  onClick={() => void accept(allCookiesConsent)}
                  disabled={submitting}
                  className="col-span-2 h-auto min-h-11 whitespace-normal bg-[#FC1EAD] hover:bg-[#e01a9a]"
                >
                  {ageAccepted ? 'Aceitar todos os cookies' : 'Aceitar todos os cookies e entrar'}
                </Button>
                <Button
                  onClick={() => void accept(defaultConsent)}
                  disabled={submitting}
                  variant="outline"
                  className="col-span-2 h-auto min-h-11 whitespace-normal"
                >
                  Entrar somente com os necessários
                </Button>
                {personalizing ? (
                  <Button
                    onClick={() => void accept(choice)}
                    disabled={submitting}
                    variant="outline"
                    className="col-span-2 h-auto min-h-11 whitespace-normal"
                  >
                    {ageAccepted ? 'Salvar preferências' : 'Salvar preferências e entrar'}
                  </Button>
                ) : null}
                <Button
                  onClick={() => setPersonalizing(!personalizing)}
                  disabled={submitting}
                  aria-expanded={personalizing}
                  aria-controls="age-gate-cookie-options"
                  variant="outline"
                  className="h-auto min-h-11 whitespace-normal"
                >
                  Personalizar cookies
                </Button>
              </>
            ) : null}
            <Button
              onClick={() => {
                window.location.href = denyRedirect
              }}
              variant="outline"
              className="h-11 border-gray-300 text-gray-700"
            >
              <XCircleIcon className="mr-2 h-5 w-5" />
              Sair
            </Button>
            {!needsCookies ? (
              <Button
                onClick={() => void accept()}
                disabled={submitting}
                className="h-11 bg-[#FC1EAD] hover:bg-[#e01a9a]"
              >
                <CheckCircleIcon className="mr-2 h-5 w-5" />
                {submitting ? 'Aceitando...' : 'Aceitar'}
              </Button>
            ) : null}
          </div>
        </DialogContent>
      </Dialog>
  )
}
