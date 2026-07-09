'use client'

import { useRouter, useSearchParams } from 'next/navigation'
import { RegisterForm } from '@/components/auth/register-form'

function getSafeNext(value: string | null) {
  if (!value) return null
  if (!value.startsWith('/') || value.startsWith('//')) return null
  return value
}

export default function RegistrarPageInner() {
  const params = useSearchParams()
  const router = useRouter()

  const ref = params.get('ref')
  const next = getSafeNext(params.get('next'))
  const loginHref = next ? `/?login=1&next=${encodeURIComponent(next)}` : '/?login=1'

  return (
    <main className="min-h-dvh w-full bg-white">
      <div className="mx-auto flex min-h-dvh w-full max-w-lg flex-col justify-center px-4 py-10 sm:px-6">
        <RegisterForm
          refId={ref ? Number(ref) : null}
          submitSource="CADASTRO_PAGINA"
          onSuccess={() => router.push(next || '/')}
          onBackToLogin={() => router.push(loginHref)}
        />
      </div>
    </main>
  )
}
