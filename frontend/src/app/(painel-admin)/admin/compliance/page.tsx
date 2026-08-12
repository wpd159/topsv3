'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import {
  ArrowLeft,
  ArrowRight,
  FileCheck2,
  Gauge,
  ScrollText,
  type LucideIcon,
} from 'lucide-react'

import { Button } from '@/components/ui/button'
import { VisitorAgeLogs } from './visitor-age-logs'
import { VisitorDocuments } from './visitor-documents'
import { VisitorRisk } from './visitor-risk'

type AreaId = 'visitor-logs' | 'visitor-risk' | 'visitor-document-fallback'
type AreaGroup = 'Monitoramento' | 'Evidências e governança'

type HubArea = {
  id: AreaId
  title: string
  description: string
  group: AreaGroup
  href: string
  icon: LucideIcon
}

const HUB_AREAS: HubArea[] = [
  {
    id: 'visitor-logs',
    title: 'Logs visitantes',
    description: 'Resultados sanitizados das verificações etárias.',
    group: 'Monitoramento',
    href: '/admin/compliance#visitor-logs',
    icon: ScrollText,
  },
  {
    id: 'visitor-risk',
    title: 'Risco por sessão',
    description: 'Sinais de risco e falhas por sessão, sem identificadores internos.',
    group: 'Monitoramento',
    href: '/admin/compliance#visitor-risk',
    icon: Gauge,
  },
  {
    id: 'visitor-document-fallback',
    title: 'Documentos visitantes',
    description: 'Evidências enviadas para análise e decisão administrativa.',
    group: 'Evidências e governança',
    href: '/admin/compliance#visitor-document-fallback',
    icon: FileCheck2,
  },
]

const GROUPS: AreaGroup[] = ['Monitoramento', 'Evidências e governança']

function sectionFromHash(hash: string): AreaId | null {
  const candidate = hash.replace(/^#/, '')
  return HUB_AREAS.some((area) => area.id === candidate)
    ? candidate as AreaId
    : null
}

function AreaContent({ area }: { area: AreaId }) {
  if (area === 'visitor-logs') {
    return <VisitorAgeLogs />
  }

  if (area === 'visitor-risk') {
    return <VisitorRisk />
  }

  return <VisitorDocuments />
}

export default function AdminCompliancePage() {
  const [area, setArea] = useState<AreaId | null>(null)

  useEffect(() => {
    const syncArea = () => setArea(sectionFromHash(window.location.hash))
    syncArea()
    window.addEventListener('hashchange', syncArea)
    window.addEventListener('popstate', syncArea)
    return () => {
      window.removeEventListener('hashchange', syncArea)
      window.removeEventListener('popstate', syncArea)
    }
  }, [])

  if (area) {
    return (
      <section className="space-y-6">
        <div className="space-y-3">
          <Button asChild variant="outline">
            <Link href="/admin/compliance" onClick={() => setArea(null)}>
              <ArrowLeft aria-hidden="true" className="h-4 w-4" />
              Voltar ao hub
            </Link>
          </Button>
          <div>
            <p className="text-sm font-medium text-pink-600">Compliance</p>
            <h1 className="text-2xl font-bold text-gray-900">
              {HUB_AREAS.find((item) => item.id === area)?.title}
            </h1>
          </div>
        </div>

        <AreaContent area={area} />
      </section>
    )
  }

  return (
    <section className="space-y-8">
      <header className="space-y-2">
        <h1 className="text-2xl font-bold text-gray-900">Compliance</h1>
        <p className="max-w-3xl text-sm text-gray-600">
          Monitoramento de acesso etário e análise das evidências disponíveis.
        </p>
      </header>

      {GROUPS.map((group) => (
        <section key={group} className="space-y-3" aria-label={group}>
          <h2 className="text-base font-semibold text-gray-900">
            {group}
          </h2>
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
            {HUB_AREAS.filter((item) => item.group === group).map((item) => {
              const Icon = item.icon
              return (
                <article
                  key={item.id}
                  className="flex min-h-44 flex-col justify-between rounded-lg border border-gray-200 bg-white p-5"
                >
                  <div className="space-y-3">
                    <span className="flex h-10 w-10 items-center justify-center rounded-md bg-pink-50 text-pink-600">
                      <Icon aria-hidden="true" className="h-5 w-5" />
                    </span>
                    <div className="space-y-1">
                      <h3 className="text-base font-semibold text-gray-900">{item.title}</h3>
                      <p className="text-sm text-gray-600">{item.description}</p>
                    </div>
                  </div>
                  <Button asChild className="mt-5 w-full sm:w-auto">
                    <Link href={item.href}>
                      Abrir
                      <ArrowRight aria-hidden="true" className="h-4 w-4" />
                    </Link>
                  </Button>
                </article>
              )
            })}
          </div>
        </section>
      ))}
    </section>
  )
}
