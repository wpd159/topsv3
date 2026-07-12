'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

type PainelShellProps = {
  title: string
  description: string
  children: ReactNode
}

const NAV_ITEMS = [
  { label: 'Visão geral', href: '/painel' },
  { label: 'Meus anúncios', href: '/meus-anuncios' },
  { label: 'Conta e configurações', href: '/minha-conta' },
]

export function PainelShell({ title, description, children }: PainelShellProps) {
  const pathname = usePathname()

  return (
    <section className="mx-auto max-w-7xl px-4 py-8 md:px-6 md:py-10">
      <div className="mb-6 rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm md:p-7">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div className="space-y-2">
            <span className="inline-flex rounded-full bg-pink-50 px-3 py-1 text-xs font-semibold uppercase tracking-[0.22em] text-[#FC1EAD]">
              Painel do anunciante
            </span>
            <div>
              <h1 className="text-2xl font-extrabold tracking-tight text-slate-900 md:text-3xl">
                {title}
              </h1>
              <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600 md:text-base">
                {description}
              </p>
            </div>
          </div>
        </div>

        <div className="relative">
          <div className="-mx-1 mt-5 flex snap-x gap-2 overflow-x-auto px-1 pb-1 pr-12 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
            {NAV_ITEMS.map((item) => {
              const isActive = pathname === item.href || pathname.startsWith(`${item.href}/`)

              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={cn(
                    'snap-start whitespace-nowrap rounded-full border px-4 py-2 text-sm font-medium transition',
                    isActive
                      ? 'border-[#FC1EAD]/30 bg-[#FC1EAD] text-white shadow-sm'
                      : 'border-slate-200 bg-slate-50 text-slate-700 hover:border-slate-300 hover:bg-white'
                  )}
                >
                  {item.label}
                </Link>
              )
            })}
          </div>
          <div className="pointer-events-none absolute inset-y-0 right-0 flex w-12 items-center justify-end bg-gradient-to-l from-white via-white/95 to-transparent md:hidden">
            <span className="flex h-8 w-8 items-center justify-center rounded-full border border-slate-200 bg-white/90 text-slate-500 shadow-sm">
              →
            </span>
          </div>
        </div>
        <p className="mt-2 text-xs font-medium text-slate-500 md:hidden">Deslize para ver mais opções</p>
      </div>

      {children}
    </section>
  )
}
