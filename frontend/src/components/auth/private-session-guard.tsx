'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '@/context/AuthContext'

export function PrivateSessionGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const { usuario, carregando } = useAuth()

  useEffect(() => {
    if (carregando || usuario || typeof window === 'undefined') return
    const next = `${window.location.pathname}${window.location.search}`
    router.replace(`/?login=1&next=${encodeURIComponent(next)}`)
  }, [carregando, router, usuario])

  if (carregando) {
    return (
      <div className="flex min-h-[45vh] items-center justify-center px-4 text-sm text-slate-500">
        Carregando sua sessão...
      </div>
    )
  }

  if (!usuario) return null
  return children
}
