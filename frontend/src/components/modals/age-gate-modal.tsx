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
import {
  confirmarAceiteGlobal,
  obterStatusVisitante,
} from '@/lib/compliance/visitor-access'
import { isAgeGateExemptPath } from '@/lib/compliance/age-gate-route-policy'

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
  const ageGateExempt = isAgeGateExemptPath(pathname)
  const legalNotice = useSiteContent('popup-login')
  const [open, setOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (ageGateExempt) {
      setOpen(false)
      return
    }

    let active = true
    void obterStatusVisitante(true)
      .then((status) => {
        if (active) setOpen(!status.globalAccepted)
      })
      .catch(() => {
        if (active) setOpen(true)
      })
    return () => {
      active = false
    }
  }, [ageGateExempt, pathname])

  async function accept() {
    setSubmitting(true)
    setError(null)
    try {
      await confirmarAceiteGlobal(pathname || '/')
      setOpen(false)
    } catch {
      setError(AGE_GATE_CONFIRMATION_ERROR)
    } finally {
      setSubmitting(false)
    }
  }

  return (
      <Dialog open={!ageGateExempt && open} onOpenChange={() => {}}>
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
              {legalNotice.titulo}
            </DialogTitle>
            <DialogDescription asChild>
              <div
                className="mt-2 min-h-0 flex-1 overflow-y-auto whitespace-pre-line pl-[max(1.5rem,env(safe-area-inset-left))] pr-[max(1.5rem,env(safe-area-inset-right))] text-justify text-gray-600 sm:mt-0 sm:flex-none sm:overflow-visible sm:px-0"
                data-age-gate-body
              >
                <SafeInstitutionalText content={legalNotice.corpo} />
              </div>
            </DialogDescription>
          </DialogHeader>

          <p className="mt-3 shrink-0 pl-[max(1.5rem,env(safe-area-inset-left))] pr-[max(1.5rem,env(safe-area-inset-right))] text-center text-sm text-gray-600 sm:px-0">
            Ao clicar em <b>Aceitar</b>, declaro que sou maior de 18 anos e li os{' '}
            <a href={termsHref} className="text-[#FC1EAD] underline underline-offset-2">
              Termos de Uso
            </a>
            .
          </p>
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
            <Button
              onClick={() => void accept()}
              disabled={submitting}
              className="h-11 bg-[#FC1EAD] hover:bg-[#e01a9a]"
            >
              <CheckCircleIcon className="mr-2 h-5 w-5" />
              {submitting ? 'Aceitando...' : 'Aceitar'}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
  )
}
