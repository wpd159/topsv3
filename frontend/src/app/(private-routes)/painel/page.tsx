'use client'

import Link from 'next/link'
import { useState } from 'react'
import { useRouter } from 'next/navigation'
import {
  ArrowRightOnRectangleIcon,
  EnvelopeIcon,
  PhoneIcon,
  UserCircleIcon,
} from '@heroicons/react/24/outline'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { PainelShell } from '@/components/painel-anunciante/painel-shell'
import { useAuth } from '@/context/AuthContext'

export default function PainelAnunciantePage() {
  const router = useRouter()
  const { usuario, logout } = useAuth()
  const [saindo, setSaindo] = useState(false)

  const handleLogout = async () => {
    if (saindo) return
    setSaindo(true)
    try {
      await logout()
      router.replace('/')
    } finally {
      setSaindo(false)
    }
  }

  return (
    <PainelShell
      title="Visão geral"
      description="Acesse os dados atuais da sua conta e mantenha seu perfil atualizado."
    >
      <div className="space-y-6 pb-10">
        <section className="rounded-[32px] border border-slate-200 bg-slate-900 px-6 py-7 text-white shadow-sm md:px-8 md:py-9">
          <div className="flex flex-col gap-6 md:flex-row md:items-end md:justify-between">
            <div className="max-w-2xl">
              <span className="inline-flex rounded-full bg-white/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em] text-pink-200">
                Painel do anunciante
              </span>
              <h2 className="mt-4 text-3xl font-extrabold md:text-4xl">
                Olá, {usuario?.username}
              </h2>
              <p className="mt-3 text-sm leading-7 text-slate-300 md:text-base">
                Sua sessão está ativa. Consulte seus dados e atualize as informações disponíveis no perfil.
              </p>
            </div>
            <Link
              href="/minha-conta"
              className="inline-flex min-h-11 items-center justify-center rounded-2xl bg-[#FC1EAD] px-5 py-3 text-sm font-semibold text-white transition hover:-translate-y-0.5 hover:bg-[#df1698] hover:shadow-lg active:translate-y-0"
            >
              Editar perfil
            </Link>
          </div>
        </section>

        <div className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
          <Card className="border border-slate-200 shadow-sm">
            <CardContent className="p-6 md:p-7">
              <div className="flex items-center gap-3">
                <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-pink-50 text-[#FC1EAD]">
                  <UserCircleIcon className="h-6 w-6" />
                </span>
                <div>
                  <p className="text-sm text-slate-500">Conta autenticada</p>
                  <h3 className="text-xl font-bold text-slate-900">{usuario?.username}</h3>
                </div>
              </div>

              <dl className="mt-6 space-y-4">
                <div className="flex items-start gap-3 border-b border-slate-100 pb-4">
                  <EnvelopeIcon className="mt-0.5 h-5 w-5 shrink-0 text-[#FC1EAD]" />
                  <div className="min-w-0">
                    <dt className="text-xs font-semibold uppercase text-slate-400">E-mail</dt>
                    <dd className="mt-1 break-all text-sm text-slate-700">{usuario?.email}</dd>
                  </div>
                </div>
                <div className="flex items-start gap-3">
                  <PhoneIcon className="mt-0.5 h-5 w-5 shrink-0 text-[#FC1EAD]" />
                  <div>
                    <dt className="text-xs font-semibold uppercase text-slate-400">Telefone</dt>
                    <dd className="mt-1 text-sm text-slate-700">
                      {usuario?.telefone || 'Não informado'}
                    </dd>
                  </div>
                </div>
              </dl>
            </CardContent>
          </Card>

          <Card className="border border-slate-200 shadow-sm">
            <CardContent className="flex h-full flex-col justify-between gap-6 p-6 md:p-7">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">
                  Status da conta
                </p>
                <p className="mt-3 text-2xl font-extrabold text-slate-900">
                  {usuario?.status === 'ATIVO' ? 'Conta ativa' : usuario?.status}
                </p>
                <p className="mt-2 text-sm leading-6 text-slate-600">
                  Os dados exibidos vêm diretamente da sua sessão no backend V3.
                </p>
              </div>
              <Button
                type="button"
                variant="outline"
                onClick={handleLogout}
                disabled={saindo}
                className="min-h-11 w-full justify-center gap-2 rounded-2xl"
              >
                <ArrowRightOnRectangleIcon className="h-5 w-5" />
                {saindo ? 'Saindo...' : 'Sair da conta'}
              </Button>
            </CardContent>
          </Card>
        </div>
      </div>
    </PainelShell>
  )
}
