'use client'

import { XMarkIcon } from '@heroicons/react/24/outline'
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
  if (!open) return null

  const close = () => onOpenChange(false)

  return (
    // Overlay proprio (sem Radix Dialog/portal/focus-trap/scroll-lock/backdrop-filter):
    // em Android antigo essa combinacao trava a rolagem e a digitacao no formulario.
    // O overlay fixed cobre a tela e rola por conta propria; sem bloquear o body e
    // sem transform no card.
    <div
      className="fixed inset-0 z-50 overflow-y-auto bg-black/60 sm:flex sm:items-center sm:justify-center sm:p-6"
      style={{ WebkitOverflowScrolling: 'touch' }}
      onClick={(e) => {
        if (e.target === e.currentTarget) close()
      }}
    >
      <div className="relative min-h-screen w-full bg-white p-6 sm:min-h-0 sm:max-w-lg sm:rounded-xl">
        <button
          type="button"
          onClick={close}
          aria-label="Fechar"
          className="absolute right-4 top-4 text-gray-400 hover:text-gray-600"
        >
          <XMarkIcon className="h-6 w-6" />
        </button>

        <RegisterForm
          refId={refId}
          onSuccess={() => {
            close()
            onBackToLogin?.()
          }}
          onBackToLogin={() => {
            close()
            onBackToLogin?.()
          }}
        />
      </div>
    </div>
  )
}
