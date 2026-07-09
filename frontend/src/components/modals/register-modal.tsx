'use client'

import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
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
  const [mounted, setMounted] = useState(false)

  useEffect(() => {
    setMounted(true)
  }, [])

  if (!open || !mounted) return null

  const close = () => onOpenChange(false)

  // Portal simples direto em document.body. Sem Radix, sem focus trap,
  // sem scroll lock e sem no intermediario. No Android antigo, isso evita
  // que camadas do Hero sejam compostas visualmente por cima do cadastro.
  return createPortal(
    <div
      className="fixed inset-0 z-50 overflow-y-auto bg-white sm:flex sm:items-center sm:justify-center sm:bg-black/60 sm:p-6"
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
    </div>,
    document.body
  )
}
