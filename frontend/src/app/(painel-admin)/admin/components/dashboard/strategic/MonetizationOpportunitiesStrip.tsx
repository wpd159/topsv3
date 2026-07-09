'use client'

import Link from 'next/link'
import { ChevronRightIcon } from '@heroicons/react/24/outline'

export type MonetizationCard = {
  id: string
  title: string
  value: string
  description: string
  href: string
}

type Props = {
  cards: MonetizationCard[]
  loading?: boolean
}

export function MonetizationOpportunitiesStrip({ cards, loading }: Props) {
  return (
    <div className="rounded-2xl border border-gray-200/80 bg-white p-6 shadow-sm">
      <h2 className="text-lg font-bold text-gray-900">Monetização</h2>
      <p className="mt-1 text-sm text-gray-500">Indicadores comerciais rápidos — clique para ir à lista adequada.</p>
      <div className="mt-4 grid gap-4 sm:grid-cols-3">
        {loading
          ? [1, 2, 3].map((i) => (
              <div key={i} className="animate-pulse rounded-xl border border-gray-100 bg-gray-50 p-4">
                <div className="h-4 w-24 rounded bg-gray-200" />
                <div className="mt-3 h-8 w-16 rounded bg-gray-200" />
              </div>
            ))
          : cards.map((c) => (
              <Link
                key={c.id}
                href={c.href}
                className="group flex flex-col rounded-xl border border-gray-100 bg-gray-50/30 p-4 transition hover:border-[#f0198f]/30 hover:bg-pink-50/20"
              >
                <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">{c.title}</p>
                <p className="mt-2 text-2xl font-bold tabular-nums text-gray-900">{c.value}</p>
                <p className="mt-1 flex-1 text-xs text-gray-600">{c.description}</p>
                <span className="mt-3 inline-flex items-center text-xs font-semibold text-[#f0198f]">
                  Ver lista
                  <ChevronRightIcon className="ml-0.5 h-3.5 w-3.5 transition group-hover:translate-x-0.5" />
                </span>
              </Link>
            ))}
      </div>
    </div>
  )
}
