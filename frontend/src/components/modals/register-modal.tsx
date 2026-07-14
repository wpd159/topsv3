'use client'

import { useState } from 'react'
import { RegisterForm } from '@/components/auth/register-form'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { ConfirmarContaModal } from './confirmar-conta-modal'

interface RegisterModalProps {
  open: boolean
  onOpenChange: (value: boolean) => void
  onBackToLogin?: () => void
  refId?: number | null
}

export function RegisterModal({ open, onOpenChange, onBackToLogin, refId }: RegisterModalProps) {
  const [confirmationEmail, setConfirmationEmail] = useState('')
  return (
    <>
    <Dialog open={open} onOpenChange={onOpenChange} modal={false}>
      {open && (
        <button
          type="button"
          aria-hidden="true"
          tabIndex={-1}
          className="fixed inset-0 z-50 cursor-default bg-black/60 animate-in fade-in-0 duration-200"
          onClick={() => onOpenChange(false)}
        />
      )}
      <DialogContent className="sm:max-w-lg p-6 rounded-xl">
        <DialogTitle className="sr-only">Criar conta</DialogTitle>
        <DialogDescription className="sr-only">Crie sua conta para comecar</DialogDescription>
        <RegisterForm
          refId={refId}
          onSuccess={(email) => {
            onOpenChange(false)
            setConfirmationEmail(email)
          }}
          onBackToLogin={() => {
            onOpenChange(false)
            onBackToLogin?.()
          }}
        />
      </DialogContent>
    </Dialog>
    <ConfirmarContaModal
      open={Boolean(confirmationEmail)}
      onOpenChange={(next) => { if (!next) setConfirmationEmail('') }}
      email={confirmationEmail}
      onVerified={() => { setConfirmationEmail(''); onBackToLogin?.() }}
    />
    </>
  )
}
