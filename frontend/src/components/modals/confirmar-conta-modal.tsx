'use client'

import { useEffect, useRef, useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { EnvelopeIcon, KeyIcon } from '@heroicons/react/24/outline'
import { toast } from 'sonner'

function parseApiMessage(body: string, fallback: string): string {
  const text = body.trim()
  if (!text) return fallback
  if (!text.startsWith('{') && !text.startsWith('[')) return text

  try {
    const parsed = JSON.parse(text) as Record<string, unknown>
    if (typeof parsed.message === 'string' && parsed.message.trim()) {
      return parsed.message.trim()
    }
    if (typeof parsed.error === 'string' && parsed.error.trim()) {
      return parsed.error.trim()
    }
  } catch {
    return fallback
  }

  return fallback
}
type ConfirmarContaModalProps = {
  open: boolean
  onOpenChange: (v: boolean) => void
  email: string
  onVerified?: () => Promise<void> | void
}

export function ConfirmarContaModal({
  open,
  onOpenChange,
  email,
  onVerified,
}: ConfirmarContaModalProps) {
  const [codigo, setCodigo] = useState('')
  const [loading, setLoading] = useState(false)

  // --- Reenvio ---
  const [resendLoading, setResendLoading] = useState(false)
  const [cooldown, setCooldown] = useState(0)
  const timerRef = useRef<NodeJS.Timeout | null>(null)

  useEffect(() => {
    if (cooldown <= 0 && timerRef.current) {
      clearInterval(timerRef.current)
      timerRef.current = null
    }
  }, [cooldown])

  useEffect(() => {
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [])

  useEffect(() => {
    if (!open) {
      setCodigo('')
      return
    }

    setCodigo('')
  }, [open, email])

  const startCooldown = (seconds = 30) => {
    setCooldown(seconds)
    if (timerRef.current) clearInterval(timerRef.current)
    timerRef.current = setInterval(() => {
      setCooldown((s) => (s > 0 ? s - 1 : 0))
    }, 1000)
  }

  const disabled = !email || codigo.trim().length < 6

  const handleConfirm = async () => {
    if (disabled) return
    try {
      setLoading(true)
      const body = new URLSearchParams({
        email: email.trim().toLowerCase(),
        codigo: codigo.trim(),
      }).toString()

      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/auth/confirm`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body,
      })

      if (!res.ok) {
        const errText = await res.text()
        toast.error(parseApiMessage(errText, 'Não foi possível confirmar a conta.'))
        return
      }

      toast.success('Conta confirmada! Vamos entrar.')
      onOpenChange(false)
      await onVerified?.()
    } catch (e) {
      toast.error('Falha na confirmação. Tente novamente.')
    } finally {
      setLoading(false)
    }
  }

  const handleResend = async () => {
    if (!email || resendLoading || cooldown > 0) return
    try {
      setResendLoading(true)
      const url = `${process.env.NEXT_PUBLIC_API_URL}/auth/resend-confirmation?email=${encodeURIComponent(
        email.trim().toLowerCase(),
      )}`
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      })

      if (!res.ok) {
        const errText = await res.text()
        toast.error(parseApiMessage(errText, 'Não foi possível reenviar o código.'))
        return
      }

      setCodigo('')
      toast.success('Novo código enviado para seu e-mail.')
      startCooldown(30)
    } catch (e) {
      toast.error('Falha ao reenviar. Verifique sua conexão.')
    } finally {
      setResendLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md p-6 rounded-xl">
        <DialogHeader>
          <DialogTitle>Confirmar conta</DialogTitle>
          <DialogDescription>
            Enviamos um código de 6 dígitos para o seu e-mail.
          </DialogDescription>
        </DialogHeader>

        <div className="mt-4 space-y-4">
          <div className="relative">
            <EnvelopeIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
            <Input value={email} disabled className="pl-10 py-5" />
          </div>

          <div className="relative">
            <KeyIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
            <Input
              inputMode="numeric"
              maxLength={6}
              placeholder="Código de verificação (6 dígitos)"
              value={codigo}
              onChange={(e) => {
                const onlyDigits = e.target.value.replace(/\D/g, '').slice(0, 6)
                setCodigo(onlyDigits)
              }}
              className="pl-10 py-5 tracking-widest"
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleConfirm()
              }}
            />
            <div className="mt-2 flex items-center justify-between text-sm">
              <span className="text-gray-500">Não recebeu o código?</span>
              <button
                type="button"
                onClick={handleResend}
                disabled={resendLoading || cooldown > 0}
                className={`font-medium ${
                  resendLoading || cooldown > 0
                    ? 'text-gray-400 cursor-not-allowed'
                    : 'text-[#FC1EAD] hover:underline'
                }`}
              >
                {resendLoading
                  ? 'Enviando...'
                  : cooldown > 0
                  ? `Reenviar em ${cooldown}s`
                  : 'Reenviar código'}
              </button>
            </div>
          </div>
        </div>

        <Button
          onClick={handleConfirm}
          disabled={disabled || loading}
          className={`w-full py-5 mt-6 font-semibold text-white ${
            !disabled ? 'bg-[#FC1EAD] hover:bg-[#e01a9a]' : 'bg-gray-300 cursor-not-allowed'
          }`}
        >
          {loading ? 'Confirmando...' : 'Confirmar'}
        </Button>
      </DialogContent>
    </Dialog>
  )
}
