'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useEffect, useRef, type ReactNode } from 'react'
import { ChevronRight } from 'lucide-react'
import { cn } from '@/lib/utils'
import {
  isPainelNavigationItemActive,
  PAINEL_NAV_ITEMS,
} from '@/components/painel-anunciante/painel-navigation'

type PainelShellProps = {
  title: string
  description: string
  children: ReactNode
}

export function PainelShell({ title, description, children }: PainelShellProps) {
  const pathname = usePathname()
  const navigationRef = useRef<HTMLElement>(null)
  const activeItemRef = useRef<HTMLAnchorElement>(null)

  useEffect(() => {
    const navigation = navigationRef.current
    const activeItem = activeItemRef.current
    if (!navigation || !activeItem) return

    const itemStart = activeItem.offsetLeft
    const itemEnd = itemStart + activeItem.offsetWidth
    const visibleStart = navigation.scrollLeft
    const visibleEnd = visibleStart + navigation.clientWidth

    if (itemStart < visibleStart || itemEnd > visibleEnd) {
      navigation.scrollTo({
        left: Math.max(0, itemStart - 16),
        behavior: 'smooth',
      })
    }
  }, [pathname])

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
          <nav
            ref={navigationRef}
            aria-label="Navegação principal do painel do anunciante"
            className="-mx-1 mt-5 flex snap-x gap-2 overflow-x-auto px-1 pb-1 pr-12 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
          >
            {PAINEL_NAV_ITEMS.map((item) => {
              const isActive = isPainelNavigationItemActive(item, pathname)

              return (
                <Link
                  key={item.id}
                  ref={isActive ? activeItemRef : undefined}
                  href={item.href}
                  aria-current={isActive ? 'page' : undefined}
                  className={cn(
                    'snap-start whitespace-nowrap rounded-full border px-4 py-2 text-sm font-medium transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#FC1EAD] focus-visible:ring-offset-2',
                    isActive
                      ? 'border-[#FC1EAD]/30 bg-[#FC1EAD] text-white shadow-sm'
                      : 'border-slate-200 bg-slate-50 text-slate-700 hover:border-slate-300 hover:bg-white'
                  )}
                >
                  {item.label}
                </Link>
              )
            })}
          </nav>
          <div className="pointer-events-none absolute inset-y-0 right-0 flex w-12 items-center justify-end bg-gradient-to-l from-white via-white/95 to-transparent md:hidden">
            <span className="flex h-8 w-8 items-center justify-center rounded-full border border-slate-200 bg-white/90 text-slate-500 shadow-sm" aria-hidden="true">
              <ChevronRight className="h-4 w-4" />
            </span>
          </div>
        </div>
        <p className="mt-2 text-xs font-medium text-slate-500 md:hidden">Deslize para ver mais opções</p>
      </div>

      {children}
    </section>
  )
}
