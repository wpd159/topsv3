'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'
import {
  CheckCircleIcon,
  ClockIcon,
  ExclamationTriangleIcon,
  PaperAirplaneIcon,
  UserPlusIcon,
} from '@heroicons/react/24/solid'
import {
  ArrowTopRightOnSquareIcon,
  MagnifyingGlassIcon,
  PencilSquareIcon,
  ShieldCheckIcon,
} from '@heroicons/react/24/outline'
import { DashboardStatCard } from '../components/dashboard/dashboard-stats-cards'
import {
  fetchWizardProgressDashboard,
  type WizardProgressDashboardResponse,
} from '@/lib/admin-wizard-progress-api'

type WizardProgressStatus = WizardProgressDashboardResponse['itens'][number]['status']
type StatusFilter = 'TODOS' | WizardProgressStatus

const FILTERS: Array<{ value: StatusFilter; label: string }> = [
  { value: 'TODOS', label: 'Todos' },
  { value: 'EM_PREENCHIMENTO', label: 'Em preenchimento' },
  { value: 'AGUARDANDO_MODERACAO', label: 'Aguardando moderação' },
  { value: 'PUBLICADO', label: 'Publicados' },
  { value: 'REJEITADO', label: 'Rejeitados' },
]

function formatDate(value: string) {
  try {
    return new Intl.DateTimeFormat('pt-BR', {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(value))
  } catch {
    return value
  }
}

function formatStep(step: string) {
  const labels: Record<string, string> = {
    perfil: 'Perfil',
    localizacao: 'Localização',
    servicos: 'Serviços',
    fotos: 'Fotos',
    revisao: 'Revisão',
    premium: 'Premium',
    kyc: 'KYC',
    concluido: 'Concluído',
  }

  return labels[step] || step
}

function formatMode(mode: string) {
  const normalized = mode?.toLowerCase()
  if (normalized === 'create') return 'Criação'
  if (normalized === 'edit') return 'Edição'
  return mode || '-'
}

function formatStatus(status: string) {
  const labels: Record<string, string> = {
    EM_PREENCHIMENTO: 'Em preenchimento',
    AGUARDANDO_MODERACAO: 'Aguardando moderação',
    PUBLICADO: 'Publicado',
    REJEITADO: 'Rejeitado',
  }

  return labels[status] || status
}

function statusClass(status: string) {
  const classes: Record<string, string> = {
    EM_PREENCHIMENTO: 'bg-yellow-100 text-yellow-700',
    AGUARDANDO_MODERACAO: 'bg-blue-100 text-blue-700',
    PUBLICADO: 'bg-green-100 text-green-700',
    REJEITADO: 'bg-red-100 text-red-700',
  }

  return classes[status] || 'bg-gray-100 text-gray-700'
}

function formatRelative(updatedAt: string) {
  const diffMs = Date.now() - new Date(updatedAt).getTime()
  const diffMinutes = Math.max(0, Math.round(diffMs / 60000))

  if (diffMinutes < 1) return 'agora'
  if (diffMinutes < 60) return `${diffMinutes} min atrás`

  const diffHours = Math.round(diffMinutes / 60)
  if (diffHours < 24) return `${diffHours} h atrás`

  const diffDays = Math.round(diffHours / 24)
  return `${diffDays} dia(s) atrás`
}

function normalizeSearch(value?: string | number | null) {
  return String(value ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim()
}

export default function AdminWizardProgressPage() {
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [data, setData] = useState<WizardProgressDashboardResponse | null>(null)
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('TODOS')
  const [search, setSearch] = useState('')

  useEffect(() => {
    let active = true

    const load = async () => {
      setLoading(true)
      setError(null)
      try {
        const response = await fetchWizardProgressDashboard()
        if (active) setData(response)
      } catch (err: any) {
        if (active) {
          setError(err?.message || 'Não foi possível carregar o progresso do wizard.')
        }
      } finally {
        if (active) setLoading(false)
      }
    }

    void load()

    return () => {
      active = false
    }
  }, [])

  const rows = useMemo(() => {
    const term = normalizeSearch(search)

    return [...(data?.itens ?? [])]
      .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
      .filter((row) => statusFilter === 'TODOS' || row.status === statusFilter)
      .filter((row) => {
        if (!term) return true

        const searchable = [
          row.usuario,
          row.email,
          row.telefone,
          row.anuncioSlug,
          row.anuncioId,
        ]
          .map(normalizeSearch)
          .join(' ')

        return searchable.includes(term)
      })
  }, [data?.itens, search, statusFilter])

  return (
    <div className="space-y-6 px-4 py-6 md:px-6">
      <section className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm">
        <p className="text-xs font-semibold uppercase tracking-[0.28em] text-[#FC1EAD]">
          Observabilidade leve
        </p>
        <h1 className="mt-2 text-3xl font-semibold text-gray-900">Progresso do wizard</h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-gray-600">
          Acompanhe onde cada anunciante parou, quem já enviou para moderação e quais fluxos
          precisam de atenção operacional.
        </p>
      </section>

      {error ? (
        <div className="rounded-2xl border border-red-100 bg-red-50 p-5 text-sm text-red-700">
          {error}
        </div>
      ) : null}

      <section className="grid gap-4 md:grid-cols-4">
        <DashboardStatCard
          label="Em preenchimento"
          value={loading ? '...' : data?.emPreenchimento ?? 0}
          icon={<UserPlusIcon className="h-5 w-5" />}
          color="yellow"
        />
        <DashboardStatCard
          label="Aguardando moderação"
          value={loading ? '...' : data?.aguardandoModeracao ?? 0}
          icon={<PaperAirplaneIcon className="h-5 w-5" />}
          color="blue"
        />
        <DashboardStatCard
          label="Publicados"
          value={loading ? '...' : data?.publicados ?? 0}
          icon={<CheckCircleIcon className="h-5 w-5" />}
          color="green"
        />
        <DashboardStatCard
          label="Rejeitados"
          value={loading ? '...' : data?.rejeitados ?? 0}
          icon={<ExclamationTriangleIcon className="h-5 w-5" />}
          color="pink"
        />
      </section>

      <section className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-center gap-3">
            <ClockIcon className="h-5 w-5 text-[#FC1EAD]" />
            <div>
              <h2 className="text-lg font-semibold text-gray-900">Último estado por usuário</h2>
              <p className="text-sm text-gray-500">Ordenado por atividade mais recente.</p>
            </div>
          </div>

          <div className="relative w-full lg:max-w-md">
            <MagnifyingGlassIcon className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Buscar por usuário, e-mail, telefone ou slug"
              className="h-11 w-full rounded-full border border-gray-200 bg-gray-50 pl-10 pr-4 text-sm text-gray-800 outline-none transition focus:border-[#FC1EAD] focus:bg-white focus:ring-2 focus:ring-pink-100"
            />
          </div>
        </div>

        <div className="mt-5 flex flex-wrap gap-2">
          {FILTERS.map((filter) => (
            <button
              key={filter.value}
              type="button"
              onClick={() => setStatusFilter(filter.value)}
              className={`rounded-full border px-4 py-2 text-sm font-semibold transition ${
                statusFilter === filter.value
                  ? 'border-[#FC1EAD] bg-[#FC1EAD] text-white shadow-sm'
                  : 'border-gray-200 bg-white text-gray-600 hover:border-pink-200 hover:bg-pink-50'
              }`}
            >
              {filter.label}
            </button>
          ))}
        </div>

        <div className="mt-5 overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-100 text-sm">
            <thead>
              <tr className="text-left text-xs uppercase tracking-[0.18em] text-gray-500">
                <th className="px-3 py-3">Usuário</th>
                <th className="px-3 py-3">Modo</th>
                <th className="px-3 py-3">Status</th>
                <th className="px-3 py-3">Última etapa</th>
                <th className="px-3 py-3">Última atividade</th>
                <th className="px-3 py-3">Ações</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {rows.length ? (
                rows.map((row) => (
                  <tr key={`${row.id}-${row.modo}`} className="align-top">
                    <td className="px-3 py-4">
                      <div className="font-medium text-gray-900">{row.usuario}</div>
                      <div className="text-xs text-gray-500">ID {row.usuarioId}</div>
                      {row.email ? <div className="text-xs text-gray-500">{row.email}</div> : null}
                      {row.telefone ? <div className="text-xs text-gray-500">{row.telefone}</div> : null}
                      {row.anuncioSlug ? (
                        <div className="mt-1 text-xs font-medium text-gray-500">/{row.anuncioSlug}</div>
                      ) : null}
                    </td>
                    <td className="px-3 py-4 text-gray-600">{formatMode(row.modo)}</td>
                    <td className="px-3 py-4">
                      <span
                        className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ${statusClass(
                          row.status
                        )}`}
                      >
                        {formatStatus(row.status)}
                      </span>
                    </td>
                    <td className="px-3 py-4 text-gray-600">{formatStep(row.ultimoStep)}</td>
                    <td className="px-3 py-4 text-gray-600">
                      <div>{formatRelative(row.updatedAt)}</div>
                      <div className="text-xs text-gray-500">{formatDate(row.updatedAt)}</div>
                    </td>
                    <td className="px-3 py-4">
                      {row.anuncioId ? (
                        <div className="flex min-w-[220px] flex-wrap gap-2">
                          <Link
                            href={row.anuncioSlug ? `/anuncios/${row.anuncioSlug}` : `/admin/anuncios/${row.anuncioId}`}
                            className="inline-flex items-center gap-1 rounded-full border border-gray-200 px-3 py-1.5 text-xs font-semibold text-gray-700 transition hover:border-pink-200 hover:bg-pink-50"
                          >
                            <ArrowTopRightOnSquareIcon className="h-3.5 w-3.5" />
                            Abrir anúncio
                          </Link>
                          <Link
                            href={`/admin/moderacao-v2/${row.anuncioId}`}
                            className="inline-flex items-center gap-1 rounded-full border border-gray-200 px-3 py-1.5 text-xs font-semibold text-gray-700 transition hover:border-pink-200 hover:bg-pink-50"
                          >
                            <ShieldCheckIcon className="h-3.5 w-3.5" />
                            Abrir moderação
                          </Link>
                          <Link
                            href={`/admin/usuarios/${row.usuarioId}/editar`}
                            className="inline-flex items-center gap-1 rounded-full border border-gray-200 px-3 py-1.5 text-xs font-semibold text-gray-700 transition hover:border-pink-200 hover:bg-pink-50"
                          >
                            <PencilSquareIcon className="h-3.5 w-3.5" />
                            Editar usuário
                          </Link>
                        </div>
                      ) : (
                        <span className="text-xs text-gray-400">Sem anúncio associado</span>
                      )}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={6} className="px-3 py-8 text-center text-sm text-gray-500">
                    Nenhum progresso encontrado para os filtros atuais.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
