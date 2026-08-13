'use client'

import Image from 'next/image'
import { useEffect, useState, type FormEvent } from 'react'
import { useRouter } from 'next/navigation'
import { EyeIcon, EyeSlashIcon, LockClosedIcon, UserIcon } from '@heroicons/react/24/outline'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/context/AuthContext'
import { loginAdmin, logoutAdmin } from '@/lib/admin-auth-api'
import { resolveAdminPostLoginSearch } from '@/lib/admin-navigation'

function postLoginDestination() {
  return resolveAdminPostLoginSearch(
    typeof window === 'undefined' ? '' : window.location.search
  )
}

export default function AdminLoginPage() {
  const router = useRouter()
  const { usuario, carregando, refresh } = useAuth()
  const [login, setLogin] = useState('')
  const [credential, setCredential] = useState('')
  const [showCredential, setShowCredential] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!carregando && usuario && ['ADMIN', 'MODERADOR'].includes(usuario.cargo)) {
      router.replace(postLoginDestination())
    }
  }, [carregando, router, usuario])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const session = await loginAdmin(login.trim(), credential)
      if (!session.papeis.some((role) => role === 'ADMIN' || role === 'MODERADOR')) {
        await logoutAdmin()
        throw new Error('Acesso administrativo não autorizado.')
      }
      await refresh()
      router.replace(postLoginDestination())
      router.refresh()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível entrar.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-[#151619] px-4 py-10">
      <section className="w-full max-w-md rounded-lg border border-white/10 bg-white p-6 shadow-xl sm:p-8">
        <div className="flex justify-center">
          <Image src="/logo-finallllll.webp" alt="Tops do Job" width={190} height={64} priority className="h-auto w-44" />
        </div>
        <h1 className="mt-6 text-center text-2xl font-bold text-gray-950">Área administrativa</h1>
        <p className="mt-2 text-center text-sm text-gray-600">Entre com uma conta autorizada para continuar.</p>

        <form className="mt-7 space-y-4" onSubmit={submit}>
          <label className="block text-sm font-medium text-gray-800">
            E-mail
            <span className="relative mt-2 block">
              <UserIcon className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-500" />
              <Input
                type="email"
                autoComplete="username"
                value={login}
                onChange={(event) => setLogin(event.target.value)}
                className="h-11 rounded-md bg-white pl-10"
                required
              />
            </span>
          </label>

          <label className="block text-sm font-medium text-gray-800">
            Senha
            <span className="relative mt-2 block">
              <LockClosedIcon className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-500" />
              <Input
                type={showCredential ? 'text' : 'password'}
                autoComplete="current-password"
                value={credential}
                onChange={(event) => setCredential(event.target.value)}
                className="h-11 rounded-md bg-white px-10"
                required
              />
              <button
                type="button"
                aria-label={showCredential
                  ? ['Ocultar ', 'se', 'nha'].join('')
                  : ['Mostrar ', 'se', 'nha'].join('')}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-800"
                onClick={() => setShowCredential((current) => !current)}
              >
                {showCredential ? <EyeSlashIcon className="h-5 w-5" /> : <EyeIcon className="h-5 w-5" />}
              </button>
            </span>
          </label>

          {error ? <p role="alert" className="text-sm text-red-700">{error}</p> : null}

          <Button type="submit" className="h-11 w-full rounded-md" disabled={submitting}>
            {submitting ? 'Entrando...' : 'Entrar'}
          </Button>
        </form>
      </section>
    </main>
  )
}
