'use client'

import { useEffect, useState } from 'react'
import { usePathname } from 'next/navigation'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { toast } from 'sonner'
import {
  ExclamationTriangleIcon,
  CheckCircleIcon,
  XCircleIcon,
} from '@heroicons/react/24/outline'
import {
  acceptAgeGate,
  readAgeGateClientStatus,
} from '@/lib/compliance/age-gate-storage'

const DEFAULT_LEGAL_NOTICE =
  'Este site contém conteúdo sexualmente explícito destinado exclusivamente a maiores de 18 anos. Se você for menor de idade ou se este tipo de conteúdo for considerado ofensivo, deve sair imediatamente.'
const DEFAULT_FOOTER_COPY =
  'O acesso é restrito a maiores de idade. Todos os perfis, imagens e descrições são de caráter adulto.'

type AgeGateModalProps = {
  termsHref?: string
  denyRedirect?: string
}

export function AgeGateModal({
  termsHref = '/termos',
  denyRedirect = 'https://www.google.com',
}: AgeGateModalProps) {
  const pathname = usePathname()
  const [open, setOpen] = useState(false)
  const [legalNotice, setLegalNotice] = useState(DEFAULT_LEGAL_NOTICE)
  const [accepting, setAccepting] = useState(false)

  useEffect(() => {
    if (pathname === '/termos-de-uso' || pathname === '/registrar') {
      setOpen(false)
      return
    }

    setOpen(!readAgeGateClientStatus().accepted)
  }, [pathname])

  useEffect(() => {
    const apiBase = (process.env.NEXT_PUBLIC_API_URL || '').replace(/\/$/, '')
    if (!apiBase) return

    fetch(`${apiBase}/compliance/policies`, {
      cache: 'no-store',
      credentials: 'include',
    })
      .then(async (res) => {
        if (!res.ok) throw new Error('Falha ao carregar políticas públicas')
        return res.json()
      })
      .then((data) => {
        if (typeof data?.legalAccessNotice === 'string' && data.legalAccessNotice.trim()) {
          setLegalNotice(data.legalAccessNotice.trim())
        }
      })
      .catch(() => null)
  }, [])

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
          : 'Não foi possível salvar o aceite. Tente novamente.',
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
      <DialogContent className="sm:max-w-md p-6 rounded-2xl">
        <DialogHeader className="text-center space-y-2">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-pink-100">
            <ExclamationTriangleIcon className="h-7 w-7 text-[#FC1EAD]" />
          </div>

          <DialogTitle className="text-xl font-bold">
            Aviso de Conteúdo Adulto
          </DialogTitle>

          <DialogDescription className="text-gray-600 text-justify">
            {legalNotice}
          </DialogDescription>
        </DialogHeader>

        <p className="text-sm text-center text-gray-600 mt-3 text-justify">
          Ao clicar em <b>Aceitar</b>, declaro que sou maior de 18 anos e li os{' '}
          <a
            href={termsHref}
            className="text-[#FC1EAD] underline underline-offset-2"
          >
            Termos de Uso
          </a>.
        </p>

        <div className="mt-6 grid grid-cols-2 gap-3">
          <Button
            onClick={handleDeny}
            variant="outline"
            className="h-11 border-gray-300 text-gray-700"
            disabled={accepting}
          >
            <XCircleIcon className="w-5 h-5 mr-2" />
            Sair
          </Button>

          <Button
            onClick={() => void handleAccept()}
            className="h-11 bg-[#FC1EAD] hover:bg-[#e01a9a]"
            disabled={accepting}
          >
            <CheckCircleIcon className="w-5 h-5 mr-2" />
            {accepting ? 'Salvando...' : 'Aceitar'}
          </Button>
        </div>

        <p className="text-xs text-center text-gray-400 mt-4 text-justify">
          {DEFAULT_FOOTER_COPY}
        </p>
      </DialogContent>
    </Dialog>
  )
}
