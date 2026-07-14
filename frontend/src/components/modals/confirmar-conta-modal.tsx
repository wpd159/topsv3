'use client'

import { useEffect, useRef, useState } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { EnvelopeIcon, KeyIcon } from '@heroicons/react/24/outline'
import { toast } from 'sonner'
import { confirmPublicAccount, PublicAuthApiError, resendPublicConfirmation } from '@/lib/public-auth-api'

type Props = { open: boolean; onOpenChange: (value: boolean) => void; email: string; onVerified?: () => Promise<void> | void }

export function ConfirmarContaModal({ open, onOpenChange, email, onVerified }: Props) {
  const [codigo, setCodigo] = useState('')
  const [loading, setLoading] = useState(false)
  const [resendLoading, setResendLoading] = useState(false)
  const [cooldown, setCooldown] = useState(0)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => { if (open) setCodigo('') }, [open, email])
  useEffect(() => () => { if (timerRef.current) clearInterval(timerRef.current) }, [])

  const startCooldown = () => {
    setCooldown(30)
    if (timerRef.current) clearInterval(timerRef.current)
    timerRef.current = setInterval(() => setCooldown(value => {
      if (value <= 1 && timerRef.current) { clearInterval(timerRef.current); timerRef.current = null }
      return Math.max(0, value - 1)
    }), 1000)
  }

  const confirm = async () => {
    if (!email || codigo.length !== 6 || loading) return
    try {
      setLoading(true)
      await confirmPublicAccount(email.trim().toLowerCase(), codigo)
      toast.success('Conta confirmada! Vamos entrar.')
      onOpenChange(false)
      await onVerified?.()
    } catch (error) {
      toast.error(error instanceof PublicAuthApiError ? error.message : 'Falha na confirmação. Tente novamente.')
    } finally { setLoading(false) }
  }

  const resend = async () => {
    if (!email || resendLoading || cooldown > 0) return
    try {
      setResendLoading(true)
      await resendPublicConfirmation(email.trim().toLowerCase())
      setCodigo('')
      toast.success('Se a conta estiver pendente, um novo código será disponibilizado.')
      startCooldown()
    } catch (error) {
      toast.error(error instanceof PublicAuthApiError ? error.message : 'Falha ao reenviar. Verifique sua conexão.')
    } finally { setResendLoading(false) }
  }

  return <Dialog open={open} onOpenChange={onOpenChange}>
    <DialogContent className="sm:max-w-md p-6 rounded-xl">
      <DialogHeader><DialogTitle>Confirmar conta</DialogTitle><DialogDescription>Enviamos um código de 6 dígitos para o seu e-mail.</DialogDescription></DialogHeader>
      <div className="mt-4 space-y-4">
        <div className="relative"><EnvelopeIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400"/><Input value={email} disabled className="pl-10 py-5"/></div>
        <div className="relative">
          <KeyIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400"/>
          <Input inputMode="numeric" maxLength={6} placeholder="Código de verificação (6 dígitos)" value={codigo}
            onChange={event => setCodigo(event.target.value.replace(/\D/g, '').slice(0, 6))} className="pl-10 py-5 tracking-widest" onKeyDown={event => { if (event.key === 'Enter') void confirm() }}/>
          <div className="mt-2 flex items-center justify-between text-sm"><span className="text-gray-500">Não recebeu o código?</span>
            <button type="button" onClick={() => void resend()} disabled={resendLoading || cooldown > 0} className="font-medium text-[#FC1EAD] disabled:text-gray-400">
              {resendLoading ? 'Enviando...' : cooldown > 0 ? `Reenviar em ${cooldown}s` : 'Reenviar código'}
            </button></div>
        </div>
      </div>
      <Button onClick={() => void confirm()} disabled={codigo.length !== 6 || loading} className="w-full py-5 mt-6 font-semibold text-white bg-[#FC1EAD] hover:bg-[#e01a9a] disabled:bg-gray-300">
        {loading ? 'Confirmando...' : 'Confirmar'}
      </Button>
    </DialogContent>
  </Dialog>
}
