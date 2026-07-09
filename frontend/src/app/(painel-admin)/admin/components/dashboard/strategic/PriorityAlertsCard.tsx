'use client'

import Link from 'next/link'
import { ChevronRightIcon, ExclamationTriangleIcon } from '@heroicons/react/24/outline'

export type PriorityAlertItem = {
  id: string
  title: string
  subtitle: string
  href: string
}

type Props = {
  items: PriorityAlertItem[]
  loading?: boolean
}

export function PriorityAlertsCard({ items, loading }: Props) {
  return (
    <div className="flex h-full flex-col rounded-2xl border border-rose-100/80 bg-white p-5 shadow-sm">
      <h3 className="text-base font-bold text-gray-900">Alertas prioritários</h3>
      <p className="mt-1 text-xs text-gray-500">Sinais de atrito ou perda de eficiência na base.</p>
      <div className="mt-4 space-y-1">
        {loading ? (
          <p className="text-sm text-gray-500">Carregando…</p>
        ) : items.length === 0 ? (
          <p className="text-sm text-gray-500">Nenhum alerta calculável no momento.</p>
        ) : (
          items.map((item) => (
            <Link
              key={item.id}
              href={item.href}
              className="group flex items-start gap-3 rounded-xl border border-gray-100 bg-gray-50/40 px-3 py-3 transition hover:border-rose-200 hover:bg-rose-50/50"
            >
              <ExclamationTriangleIcon className="mt-0.5 h-4 w-4 shrink-0 text-rose-600" aria-hidden />
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-gray-900 group-hover:text-rose-900">{item.title}</p>
                <p className="mt-0.5 text-xs text-gray-600">{item.subtitle}</p>
              </div>
              <ChevronRightIcon className="mt-1 h-4 w-4 shrink-0 text-gray-400 group-hover:text-rose-600" />
            </Link>
          ))
        )}
      </div>
    </div>
  )
}
