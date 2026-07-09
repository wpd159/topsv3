'use client'

import { useEffect, useState } from 'react'
import { usePathname } from 'next/navigation'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import {
  ExclamationTriangleIcon,
  CheckCircleIcon,
  XCircleIcon,
} from '@heroicons/react/24/outline'
import {
  acceptAgeGate,
  AGE_GATE_TTL_DAYS,
  fetchAgeGateStatus,
  persistAgeGateClient,
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

  useEffect(() => {
    if (pathname === '/termos-de-uso' || pathname === '/registrar') {
      setOpen(false)
      return
    }

    let cancelled = false

    async function resolveGate() {
      const local = readAgeGateClientStatus()
      if (local.accepted) {
        if (!cancelled) setOpen(false)
        return
      }

      const remote = await fetchAgeGateStatus()
      if (cancelled) return
      setOpen(!remote.accepted)
    }

    void resolveGate()
    return () => {
      cancelled = true
    }
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

  const handleAccept = () => {
    // Persiste o aceite localmente e fecha o modal de imediato. A chamada
    // remota é best-effort: se a API falhar/demorar, o fechamento não é afetado.
    try {
      persistAgeGateClient(Date.now() + AGE_GATE_TTL_DAYS * 24 * 60 * 60 * 1000)
    } catch {
      // noop
    }
    setOpen(false)
    void acceptAgeGate(pathname || '/').catch(() => null)
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
            type="button"
            onClick={handleDeny}
            variant="outline"
            className="h-11 border-gray-300 text-gray-700"
          >
            <XCircleIcon className="w-5 h-5 mr-2" />
            Sair
          </Button>

          <Button
            type="button"
            onClick={handleAccept}
            className="h-11 bg-[#FC1EAD] hover:bg-[#e01a9a]"
          >
            <CheckCircleIcon className="w-5 h-5 mr-2" />
            Aceitar
          </Button>
        </div>

        <p className="text-xs text-center text-gray-400 mt-4 text-justify">
          {DEFAULT_FOOTER_COPY}
        </p>
      </DialogContent>
    </Dialog>
  )
}
