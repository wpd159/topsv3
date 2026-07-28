'use client'

import Link from 'next/link'
import { ChevronRight, TrendingUp } from 'lucide-react'

import { Button } from '@/components/ui/button'
import type { AdminDashboardInsight } from '@/lib/admin-dashboard-api'

type Props = {
  items: AdminDashboardInsight[]
  loading: boolean
  error: string | null
  onRetry: () => void
}

export function CommercialOpportunitiesCard({ items, loading, error, onRetry }: Props) {
  return (
    <section className="h-full border border-zinc-200 bg-white p-5">
      <h3 className="text-base font-bold text-zinc-950">Oportunidades comerciais</h3>
      <p className="mt-1 text-xs text-zinc-600">Recortes operacionais, sem ativação automática.</p>

      <div className="mt-4 divide-y divide-zinc-200">
        {loading ? (
          <p className="py-6 text-sm text-zinc-500">Calculando oportunidades...</p>
        ) : error ? (
          <div className="py-5">
            <p className="text-sm text-red-700" role="alert">{error}</p>
            <Button type="button" size="sm" variant="outline" className="mt-3" onClick={onRetry}>
              Tentar novamente
            </Button>
          </div>
        ) : items.length === 0 ? (
          <p className="py-6 text-sm text-zinc-500">Nenhuma oportunidade no momento.</p>
        ) : (
          items.map((item) => {
            const content = (
              <>
                <TrendingUp className="mt-0.5 h-4 w-4 shrink-0 text-emerald-700" aria-hidden />
                <span className="min-w-0 flex-1">
                  <strong className="block text-sm text-zinc-950">{item.titulo}</strong>
                  <span className="mt-1 block text-xs text-zinc-600">{item.descricao}</span>
                </span>
                {item.href ? <ChevronRight className="mt-1 h-4 w-4 shrink-0 text-zinc-400" aria-hidden /> : null}
              </>
            )
            return item.href ? (
              <Link key={item.codigo} href={item.href} className="flex gap-3 py-3 hover:text-pink-700">
                {content}
              </Link>
            ) : (
              <div key={item.codigo} className="flex gap-3 py-3">{content}</div>
            )
          })
        )}
      </div>
    </section>
  )
}
