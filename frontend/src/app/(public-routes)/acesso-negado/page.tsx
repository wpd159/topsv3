'use client'

import { Suspense } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { ShieldExclamationIcon, ArrowLeftIcon, HomeIcon } from '@heroicons/react/24/solid'

function AcessoNegadoContent() {
  const router = useRouter()
  const params = useSearchParams()
  const from = params.get('from') || '/'

  return (
    <main className="min-h-[80vh] flex items-center justify-center px-4">
      <div className="max-w-md w-full text-center">
        <div className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-full bg-red-100">
          <ShieldExclamationIcon className="h-8 w-8 text-red-600" />
        </div>

        <h1 className="text-2xl font-semibold tracking-tight">Acesso negado</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Você não tem permissão para acessar <span className="font-medium">{from}</span>.
          Se você acha que isso é um engano, fale com o administrador.
        </p>

        <div className="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-3">
          <button
            onClick={() => router.back()}
            className="inline-flex items-center justify-center gap-2 rounded-md border px-3 py-2 text-sm font-medium hover:bg-accent"
          >
            <ArrowLeftIcon className="h-4 w-4" />
            Voltar
          </button>

          <Link
            href="/"
            className="inline-flex items-center justify-center gap-2 rounded-md border px-3 py-2 text-sm font-medium hover:bg-accent"
          >
            <HomeIcon className="h-4 w-4" />
            Início
          </Link>

          <a
            href="/logout"
            className="inline-flex bg-pink-500 items-center justify-center gap-2 rounded-md text-primary-foreground px-3 py-2 text-sm font-medium hover:opacity-90"
          >
            Sair
          </a>
        </div>

        <p className="mt-6 text-xs text-muted-foreground">
          Código do erro: <span className="font-mono">403</span>
        </p>
      </div>
    </main>
  )
}

export default function AcessoNegadoPage() {
  return (
    <Suspense fallback={<div className="text-center py-20 text-gray-500">Carregando...</div>}>
      <AcessoNegadoContent />
    </Suspense>
  )
}
