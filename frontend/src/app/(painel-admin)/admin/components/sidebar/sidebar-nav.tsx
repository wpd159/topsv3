'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { SidebarLink } from './sidebar-links'

type SidebarNavProps = {
  items: SidebarLink[]
  onItemClick?: () => void
  notificationCounts?: Partial<Record<NonNullable<SidebarLink["notificationKey"]>, number | null>>
}

export function SidebarNav({ items, onItemClick, notificationCounts = {} }: SidebarNavProps) {
  const pathname = usePathname()
  const sections = items.reduce<Array<{ section: SidebarLink["section"]; items: SidebarLink[] }>>((acc, item) => {
    const lastSection = acc[acc.length - 1]
    if (!lastSection || lastSection.section !== item.section) {
      acc.push({ section: item.section, items: [item] })
      return acc
    }

    lastSection.items.push(item)
    return acc
  }, [])

  return (
    <nav className="flex flex-col gap-5 text-md text-white">
      {sections.map((section) => (
        <div key={section.section} className="space-y-2">
          <p className="px-3 text-[11px] font-semibold uppercase tracking-[0.18em] text-white/45">
            {section.section}
          </p>

          <div className="flex flex-col gap-2">
            {section.items.map((item) => {
              const baseHref = item.href.split('#')[0]
              const isActive =
                pathname === baseHref ||
                (baseHref !== '/admin' && pathname.startsWith(`${baseHref}/`))
              const notificationCount: number | null =
                item.notificationKey
                  ? notificationCounts[item.notificationKey] ?? null
                  : baseHref === '/admin/anuncios'
                    ? notificationCounts.revisoes ?? null
                    : null
              const hasCounter = Boolean(item.notificationKey) || baseHref === '/admin/anuncios'

              return (
                <Link
                  key={item.label}
                  href={item.href}
                  onClick={onItemClick}
                  className={`flex items-center justify-between gap-3 rounded-md px-3 py-2 font-normal ${
                    isActive ? 'bg-pink-600 text-white' : 'hover:bg-white/10'
                  }`}
                >
                  <span className="flex items-center gap-3">
                    {item.icon}
                    {item.label}
                  </span>

                  {typeof notificationCount === 'number' && notificationCount > 0 && (
                    <span className="flex min-w-5 items-center justify-center rounded-full bg-red-500 px-1.5 py-0.5 text-[10px] font-semibold text-white">
                      {notificationCount > 99 ? '99+' : notificationCount}
                    </span>
                  )}
                  {hasCounter && notificationCount === null && (
                    <span
                      className="flex h-5 w-5 items-center justify-center rounded-full border border-amber-300/40 text-[10px] font-semibold text-amber-200"
                      title="Contador indisponivel: contrato backend pendente ou falha de integracao"
                      aria-label="Contador indisponivel"
                    >
                      !
                    </span>
                  )}
                </Link>
              )
            })}
          </div>
        </div>
      ))}
    </nav>
  )
}
