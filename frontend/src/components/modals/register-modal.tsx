'use client'

import { Dialog, DialogContent } from '@/components/ui/dialog'
import { RegisterForm } from '@/components/auth/register-form'

interface RegisterModalProps {
  open: boolean
  onOpenChange: (value: boolean) => void
  onBackToLogin?: () => void
  refId?: number | null
}

export function RegisterModal({
  open,
  onOpenChange,
  onBackToLogin,
  refId,
}: RegisterModalProps) {
  return (
    // modal={false}: unica excecao estrutural autorizada (modal de criar conta).
    // Desliga o scroll-lock, o focus trap e o bloqueio de pointer-events fora do
    // dialog que o Radix Dialog aplica por padrao (modal=true) - essa combinacao
    // trava rolagem/digitacao em Android antigo. Visual identico ao clone; so o
    // comportamento de interacao muda.
    <Dialog open={open} onOpenChange={onOpenChange} modal={false}>
      <DialogContent className="sm:max-w-lg p-6 rounded-xl">
        <RegisterForm
          refId={refId}
          onSuccess={() => {
            onOpenChange(false)
            onBackToLogin?.()
          }}
          onBackToLogin={() => {
            onOpenChange(false)
            onBackToLogin?.()
          }}
        />
      </DialogContent>
    </Dialog>
  )
}
