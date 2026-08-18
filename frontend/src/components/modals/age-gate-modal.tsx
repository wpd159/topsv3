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
import { acceptGlobalAgeGate } from '@/lib/compliance/age-gate-api'
import {
  notificarMudancaVerificacao,
  obterStatusVisitante,
} from '@/lib/compliance/visitor-access'

type AgeGateModalProps = {
  termsHref?: string
  denyRedirect?: string
}

export function AgeGateModal({
  termsHref = '/termos-de-uso',
  denyRedirect = 'https://www.google.com',
}: AgeGateModalProps) {
  const pathname = usePathname()
  const legalNotice = useSiteContent('popup-login')
  const [open, setOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (pathname === '/termos-de-uso' || pathname === '/registrar') {
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
  }, [pathname])

  async function accept() {
    setSubmitting(true)
    setError(null)
    try {
      const status = await acceptGlobalAgeGate(pathname || '/')
      if (!status.accepted) throw new Error('Aceite global nao confirmado.')
      const visitorStatus = await obterStatusVisitante(true)
      if (!visitorStatus.globalAccepted) {
        throw new Error('Aceite global nao confirmado pelo estado canonico.')
      }
      setOpen(false)
      notificarMudancaVerificacao(visitorStatus)
    } catch (nextError) {
      setError(nextError instanceof Error
        ? nextError.message
        : 'Nao foi possivel registrar o aceite.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
      <Dialog open={open} onOpenChange={() => {}}>
        <DialogContent className="rounded-2xl p-6 sm:max-w-md">
          <DialogHeader className="space-y-2 text-center">
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-pink-100">
              <ExclamationTriangleIcon className="h-7 w-7 text-[#FC1EAD]" />
            </div>
            <DialogTitle className="text-xl font-bold">{legalNotice.titulo}</DialogTitle>
            <DialogDescription asChild>
              <div className="whitespace-pre-line text-justify text-gray-600">
                <SafeInstitutionalText content={legalNotice.corpo} />
              </div>
            </DialogDescription>
          </DialogHeader>

          <p className="mt-3 text-center text-sm text-gray-600">
            Ao clicar em <b>Aceitar</b>, declaro que sou maior de 18 anos e li os{' '}
            <a href={termsHref} className="text-[#FC1EAD] underline underline-offset-2">
              Termos de Uso
            </a>
            .
          </p>
          {error ? (
            <p role="alert" className="mt-3 text-center text-sm text-red-600">
              {error}
            </p>
          ) : null}

          <div className="mt-6 grid grid-cols-2 gap-3">
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
