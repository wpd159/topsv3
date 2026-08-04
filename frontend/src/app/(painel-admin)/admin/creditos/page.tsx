'use client'

import { useEffect, useMemo } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  adminMonetizacaoAba,
  adminMonetizacaoQuery,
  ADMIN_MONETIZACAO_ABAS,
  type AdminMonetizacaoAba,
} from '@/lib/admin-monetizacao-navigation'
import { PlanoCreditoManager } from '../components/plano-credito-manager'
import { AdminAtivacoesHistorico } from './admin-ativacoes-historico'
import { AdminPremiumCatalogo } from './admin-premium-catalogo'
import { AdminSaldosAjustes } from './admin-saldos-ajustes'
import { AdminStoryConfiguracaoCard } from './admin-story-configuracao-card'

function abaValida(value: string): value is AdminMonetizacaoAba {
  return ADMIN_MONETIZACAO_ABAS.includes(value as AdminMonetizacaoAba)
}

export default function AdminCreditosPage() {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const aba = useMemo(() => adminMonetizacaoAba(searchParams), [searchParams])
  const queryCanonica = useMemo(
    () => adminMonetizacaoQuery(searchParams, aba),
    [aba, searchParams],
  )

  useEffect(() => {
    if (searchParams.toString() === queryCanonica) return
    router.replace(`${pathname}?${queryCanonica}`, { scroll: false })
  }, [pathname, queryCanonica, router, searchParams])

  const navegar = (value: string) => {
    if (!abaValida(value) || value === aba) return
    router.push(`${pathname}?${adminMonetizacaoQuery(searchParams, value)}`, { scroll: false })
  }

  return (
    <section className="min-w-0 space-y-6 pb-12">
      <header>
        <p className="text-xs font-semibold uppercase text-pink-600">Administração comercial</p>
        <h1 className="mt-1 text-2xl font-bold text-gray-950">Monetização</h1>
        <p className="mt-1 max-w-3xl text-sm text-gray-600">
          Configure ofertas e acompanhe operações sem misturar catálogo, saldo e histórico financeiro.
        </p>
      </header>

      <Tabs value={aba} onValueChange={navegar} className="min-w-0 space-y-5">
        <div className="max-w-full overflow-x-auto pb-1" aria-label="Seções de Monetização">
          <TabsList className="w-max min-w-full justify-start" aria-label="Monetização">
            <TabsTrigger className="min-w-max flex-none md:flex-1" value="stories">Stories</TabsTrigger>
            <TabsTrigger className="min-w-max flex-none md:flex-1" value="beneficios">Benefícios Premium</TabsTrigger>
            <TabsTrigger className="min-w-max flex-none md:flex-1" value="pacotes">Pacotes de créditos</TabsTrigger>
            <TabsTrigger className="min-w-max flex-none md:flex-1" value="saldos">Saldos e ajustes</TabsTrigger>
            <TabsTrigger className="min-w-max flex-none md:flex-1" value="ativacoes">Ativações e histórico</TabsTrigger>
          </TabsList>
        </div>

        <TabsContent value="stories" className="min-w-0"><AdminStoryConfiguracaoCard /></TabsContent>
        <TabsContent value="beneficios" className="min-w-0"><AdminPremiumCatalogo /></TabsContent>
        <TabsContent value="pacotes" className="min-w-0"><PlanoCreditoManager /></TabsContent>
        <TabsContent value="saldos" className="min-w-0"><AdminSaldosAjustes /></TabsContent>
        <TabsContent value="ativacoes" className="min-w-0"><AdminAtivacoesHistorico /></TabsContent>
      </Tabs>
    </section>
  )
}