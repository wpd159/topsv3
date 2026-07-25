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
import { toast } from 'sonner'
import {
  CheckCircleIcon,
  ExclamationTriangleIcon,
  XCircleIcon,
} from '@heroicons/react/24/outline'
import {
  acceptAgeGate,
  readAgeGateClientStatus,
} from '@/lib/compliance/age-gate-storage'
import { useSiteContent } from '@/components/site-content/site-content-provider'

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
  const [accepting, setAccepting] = useState(false)

  useEffect(() => {
    if (pathname === '/termos-de-uso' || pathname === '/registrar') {
      setOpen(false)
      return
    }

    setOpen(!readAgeGateClientStatus().accepted)
  }, [pathname])

  const handleAccept = async () => {
    setAccepting(true)
    try {
      await acceptAgeGate()
      setOpen(false)
    } catch (error) {
      setOpen(true)
      toast.error(
        error instanceof Error
          ? error.message
          : 'Nao foi possivel salvar o aceite. Tente novamente.',
      )
    } finally {
      setAccepting(false)
    }
  }

  const handleDeny = () => {
    window.location.href = denyRedirect
  }

  return (
    <Dialog open={open} onOpenChange={() => {}}>
      <DialogContent className="rounded-2xl p-6 sm:max-w-md">
        <DialogHeader className="space-y-2 text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-pink-100">
            <ExclamationTriangleIcon className="h-7 w-7 text-[#FC1EAD]" />
          </div>

          <DialogTitle className="text-xl font-bold">{legalNotice.titulo}</DialogTitle>

          <DialogDescription className="whitespace-pre-line text-justify text-gray-600">
            {legalNotice.corpo}
          </DialogDescription>
        </DialogHeader>

        <p className="mt-3 text-justify text-center text-sm text-gray-600">
          Ao clicar em <b>Aceitar</b>, declaro que sou maior de 18 anos e li os{' '}
          <a
            href={termsHref}
            className="text-[#FC1EAD] underline underline-offset-2"
          >
            Termos de Uso
          </a>
          .
        </p>

        <div className="mt-6 grid grid-cols-2 gap-3">
          <Button
            onClick={handleDeny}
            variant="outline"
            className="h-11 border-gray-300 text-gray-700"
            disabled={accepting}
          >
            <XCircleIcon className="mr-2 h-5 w-5" />
            Sair
          </Button>

          <Button
            onClick={() => void handleAccept()}
            className="h-11 bg-[#FC1EAD] hover:bg-[#e01a9a]"
            disabled={accepting}
          >
            <CheckCircleIcon className="mr-2 h-5 w-5" />
            {accepting ? 'Salvando...' : 'Aceitar'}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
