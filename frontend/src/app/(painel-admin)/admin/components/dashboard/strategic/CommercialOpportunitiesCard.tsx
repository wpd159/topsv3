'use client'

import Link from 'next/link'
import { ArrowTrendingUpIcon, ChevronRightIcon } from '@heroicons/react/24/outline'

export type CommercialOpportunityItem = {
  id: string
  title: string
  subtitle: string
  href: string
}

type Props = {
  items: CommercialOpportunityItem[]
  loading?: boolean
}

export function CommercialOpportunitiesCard({ items, loading }: Props) {
  return (
    <div className="flex h-full flex-col rounded-2xl border border-emerald-100/80 bg-white p-5 shadow-sm">
      <h3 className="text-base font-bold text-gray-900">Oportunidades comerciais</h3>
      <p className="mt-1 text-xs text-gray-500">Listas e indicadores com potencial de receita.</p>
      <div className="mt-4 space-y-1">
        {loading ? (
          <p className="text-sm text-gray-500">Carregando…</p>
        ) : items.length === 0 ? (
          <p className="text-sm text-gray-500">Nenhuma oportunidade listada.</p>
        ) : (
          items.map((item) => (
            <Link
              key={item.id}
              href={item.href}
              className="group flex items-start gap-3 rounded-xl border border-gray-100 bg-gray-50/40 px-3 py-3 transition hover:border-emerald-200 hover:bg-emerald-50/40"
            >
              <ArrowTrendingUpIcon className="mt-0.5 h-4 w-4 shrink-0 text-emerald-700" aria-hidden />
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-gray-900 group-hover:text-emerald-900">{item.title}</p>
                <p className="mt-0.5 text-xs text-gray-600">{item.subtitle}</p>
              </div>
              <ChevronRightIcon className="mt-1 h-4 w-4 shrink-0 text-gray-400 group-hover:text-emerald-700" />
            </Link>
          ))
        )}
      </div>
    </div>
  )
}
