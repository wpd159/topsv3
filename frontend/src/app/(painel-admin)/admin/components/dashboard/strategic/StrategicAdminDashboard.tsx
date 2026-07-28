'use client'

import Link from 'next/link'
import { useCallback, useEffect, useMemo, useRef, useState, type ComponentType } from 'react'
import {
  BadgeCheck,
  CirclePause,
  Clock3,
  Eye,
  FileCheck2,
  Flag,
  Headphones,
  Lightbulb,
  MessageCircle,
  RefreshCw,
  Sparkles,
  Users,
} from 'lucide-react'

import { Button } from '@/components/ui/button'
import { getAdminUserIndicators } from '@/features/admin-usuarios/api'
import type { AdminUserIndicators } from '@/features/admin-usuarios/types'
import { getAdminSession, type AdminSession } from '@/lib/admin-auth-api'
import {
  fetchDashboardAnuncios,
  fetchDashboardHoje,
  fetchDashboardMidias,
  fetchDashboardModeracao,
  type AdminDashboardAnuncios,
  type AdminDashboardHoje,
  type AdminDashboardMidias,
  type AdminDashboardModeracao,
} from '@/lib/admin-dashboard-api'
import {
  buscarIndicadoresDenuncias,
  type AdminDenunciaIndicadores,
} from '@/lib/admin-denuncia-api'
import {
  buscarIndicadoresSugestoes,
  type AdminSugestaoIndicadores,
} from '@/lib/admin-sugestao-api'
import {
  buscarIndicadoresTickets,
  type AdminTicketIndicadores,
} from '@/lib/admin-suporte-api'
import { AdminDashboardAnalytics } from './AdminDashboardAnalytics'

type SourceKey =
  | 'usuarios'
  | 'anuncios'
  | 'moderacao'
  | 'midias'
  | 'tickets'
  | 'denuncias'
  | 'sugestoes'
  | 'hoje'

type SourceStatus = 'idle' | 'loading' | 'ready' | 'error'

type DashboardData = {
  usuarios: AdminUserIndicators
  anuncios: AdminDashboardAnuncios
  moderacao: AdminDashboardModeracao
  midias: AdminDashboardMidias
  tickets: AdminTicketIndicadores
  denuncias: AdminDenunciaIndicadores
  sugestoes: AdminSugestaoIndicadores
  hoje: AdminDashboardHoje
}

type SourceState = {
  status: SourceStatus
  message?: string
}

type MetricCard = {
  id: string
  label: string
  value?: number
  source: SourceKey
  icon: ComponentType<{ className?: string }>
  href?: string
  tone?: 'neutral' | 'attention' | 'positive'
}

const INITIAL_SOURCE_STATE: Record<SourceKey, SourceState> = {
  usuarios: { status: 'idle' },
  anuncios: { status: 'idle' },
  moderacao: { status: 'idle' },
  midias: { status: 'idle' },
  tickets: { status: 'idle' },
  denuncias: { status: 'idle' },
  sugestoes: { status: 'idle' },
  hoje: { status: 'idle' },
}

const EMPTY_DATA: Partial<DashboardData> = {}

function hasPermission(session: AdminSession, permission: string) {
  return session.permissoes.includes(permission)
}

function sourceErrorMessage(error: unknown) {
  if (error instanceof Error && error.message) return error.message
  return 'Não foi possível carregar este indicador.'
}

function formatNumber(value: number | undefined) {
  return value === undefined ? '—' : value.toLocaleString('pt-BR')
}

function metricTone(tone: MetricCard['tone']) {
  if (tone === 'attention') return 'border-amber-300 bg-amber-50 text-amber-800'
  if (tone === 'positive') return 'border-emerald-300 bg-emerald-50 text-emerald-800'
  return 'border-zinc-200 bg-white text-zinc-800'
}

function Metric({
  item,
  state,
}: {
  item: MetricCard
  state: SourceState
}) {
  const Icon = item.icon
  const loading = state.status === 'loading' || state.status === 'idle'
  const error = state.status === 'error'
  const content = (
    <>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs font-semibold text-zinc-600">{item.label}</p>
          <p className="mt-2 text-2xl font-bold text-zinc-950">
            {loading ? <span className="inline-block h-7 w-16 animate-pulse bg-zinc-200" /> : formatNumber(item.value)}
          </p>
        </div>
        <span className={`flex h-9 w-9 shrink-0 items-center justify-center border ${metricTone(item.tone)}`}>
          <Icon className="h-4 w-4" />
        </span>
      </div>
      {error ? (
        <p className="mt-3 text-xs text-red-700" role="alert">{state.message}</p>
      ) : item.href ? (
        <p className="mt-3 text-xs font-medium text-pink-700">Abrir fila</p>
      ) : null}
    </>
  )

  const className = [
    'min-h-28 border border-zinc-200 bg-white p-4',
    item.href && !error ? 'transition-colors hover:border-pink-300 hover:bg-pink-50/30 focus:outline-none focus:ring-2 focus:ring-pink-300' : '',
  ].join(' ')

  if (item.href && !error) {
    return <Link href={item.href} className={className}>{content}</Link>
  }
  return <div className={className}>{content}</div>
}

export function StrategicAdminDashboard() {
  const [data, setData] = useState<Partial<DashboardData>>(EMPTY_DATA)
  const [sources, setSources] = useState<Record<SourceKey, SourceState>>(INITIAL_SOURCE_STATE)
  const [session, setSession] = useState<AdminSession | null>(null)
  const [sessionError, setSessionError] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const loadGeneration = useRef(0)

  const loadSource = useCallback(async <K extends SourceKey>(
    key: K,
    request: Promise<DashboardData[K]>,
    generation: number,
  ) => {
    if (loadGeneration.current !== generation) return
    setSources((current) => ({ ...current, [key]: { status: 'loading' } }))
    try {
      const payload = await request
      if (loadGeneration.current !== generation) return
      setData((current) => ({ ...current, [key]: payload }))
      setSources((current) => ({ ...current, [key]: { status: 'ready' } }))
    } catch (error) {
      if (loadGeneration.current !== generation) return
      setSources((current) => ({
        ...current,
        [key]: { status: 'error', message: sourceErrorMessage(error) },
      }))
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    const generation = ++loadGeneration.current

    async function load() {
      setSessionError(null)
      setData(EMPTY_DATA)
      setSources(INITIAL_SOURCE_STATE)
      try {
        const currentSession = await getAdminSession()
        if (!active || !currentSession) {
          if (active) setSessionError('Sessão administrativa necessária.')
          return
        }
        setSession(currentSession)
        const requests: Promise<void>[] = []
        if (hasPermission(currentSession, 'ANUNCIO_LER')) {
          requests.push(loadSource('usuarios', getAdminUserIndicators(), generation))
          requests.push(loadSource('anuncios', fetchDashboardAnuncios(controller.signal), generation))
          requests.push(loadSource('denuncias', buscarIndicadoresDenuncias(controller.signal), generation))
        }
        if (
          hasPermission(currentSession, 'ANUNCIO_MODERAR')
          && hasPermission(currentSession, 'DOCUMENTO_REVISAR')
        ) {
          requests.push(loadSource('moderacao', fetchDashboardModeracao(controller.signal), generation))
        }
        if (hasPermission(currentSession, 'MIDIA_REVISAR')) {
          requests.push(loadSource('midias', fetchDashboardMidias(controller.signal), generation))
        }
        if (hasPermission(currentSession, 'SUPORTE_ATENDER')) {
          requests.push(loadSource('tickets', buscarIndicadoresTickets(controller.signal), generation))
          requests.push(loadSource('sugestoes', buscarIndicadoresSugestoes(controller.signal), generation))
        }
        if (currentSession.papeis.includes('ADMIN') && hasPermission(currentSession, 'ANUNCIO_LER')) {
          requests.push(loadSource('hoje', fetchDashboardHoje(controller.signal), generation))
        }
        await Promise.allSettled(requests)
      } catch (error) {
        if (active) setSessionError(sourceErrorMessage(error))
      }
    }

    void load()
    return () => {
      active = false
      controller.abort()
      if (loadGeneration.current === generation) loadGeneration.current += 1
    }
  }, [loadSource, refreshKey])

  const cards = useMemo<MetricCard[]>(() => {
    const items: MetricCard[] = []
    if (session && hasPermission(session, 'ANUNCIO_LER')) {
      items.push(
        { id: 'usuarios', label: 'Usuários totais', value: data.usuarios?.totalUsuarios, source: 'usuarios', icon: Users, href: '/admin/usuarios' },
        { id: 'novos', label: 'Novos usuários hoje', value: data.usuarios?.novosHoje, source: 'usuarios', icon: Clock3, href: '/admin/usuarios?ordenacao=RECENTES' },
        { id: 'publicados', label: 'Anúncios publicados', value: data.anuncios?.publicados, source: 'anuncios', icon: BadgeCheck, tone: 'positive' },
        { id: 'pendentes', label: 'Anúncios pendentes', value: data.anuncios?.pendentesRevisao, source: 'anuncios', icon: FileCheck2, href: '/admin/anuncios?situacao=PENDENTES_MODERACAO', tone: 'attention' },
        { id: 'pausados', label: 'Anúncios pausados', value: data.anuncios?.pausados, source: 'anuncios', icon: CirclePause, href: '/admin/anuncios?situacao=PAUSADOS' },
        { id: 'denuncias', label: 'Denúncias pendentes', value: data.denuncias?.pendentes, source: 'denuncias', icon: Flag, href: '/admin/denuncias?status=PENDENTE', tone: 'attention' },
      )
    }
    if (
      session
      && hasPermission(session, 'ANUNCIO_MODERAR')
      && hasPermission(session, 'DOCUMENTO_REVISAR')
    ) {
      items.push({
        id: 'kyc',
        label: 'Documentos KYC pendentes',
        value: data.moderacao?.documentosPendentes,
        source: 'moderacao',
        icon: FileCheck2,
        href: '/admin/usuarios?kyc=PENDENTE',
        tone: 'attention',
      })
    }
    if (session && hasPermission(session, 'SUPORTE_ATENDER')) {
      items.push(
        { id: 'tickets', label: 'Tickets pendentes', value: data.tickets?.pendentesEquipe, source: 'tickets', icon: Headphones, href: '/admin/tickets?status=ABERTOS', tone: 'attention' },
        { id: 'sugestoes', label: 'Sugestões pendentes', value: data.sugestoes?.novas, source: 'sugestoes', icon: Lightbulb, href: '/admin/sugestoes?status=PENDENTE', tone: 'attention' },
      )
    }
    if (session && hasPermission(session, 'MIDIA_REVISAR')) {
      items.push({
        id: 'stories',
        label: 'Stories ativos',
        value: data.midias?.storiesPublicados,
        source: 'midias',
        icon: Sparkles,
      })
    }
    if (session?.papeis.includes('ADMIN') && hasPermission(session, 'ANUNCIO_LER')) {
      items.push(
        { id: 'views', label: 'Visualizações hoje', value: data.hoje?.visualizacoes, source: 'hoje', icon: Eye },
        { id: 'clicks', label: 'Cliques no WhatsApp hoje', value: data.hoje?.cliquesWhatsapp, source: 'hoje', icon: MessageCircle },
        { id: 'premium', label: 'Benefícios Premium vigentes', value: data.hoje?.beneficiosPremiumVigentes, source: 'hoje', icon: Sparkles },
      )
    }
    return items
  }, [data, session])

  const refreshing = Object.values(sources).some((source) => source.status === 'loading')

  return (
    <section className="space-y-6">
      <header className="flex flex-col justify-between gap-3 border-b border-zinc-200 pb-5 sm:flex-row sm:items-end">
        <div>
          <h1 className="text-2xl font-bold text-zinc-950">Dashboard</h1>
          <p className="mt-1 text-sm text-zinc-600">
            Indicadores operacionais da pré-produção, sem dados simulados.
          </p>
          {data.hoje ? (
            <p className="mt-1 text-xs text-zinc-500">
              Hoje: {new Intl.DateTimeFormat('pt-BR').format(new Date(`${data.hoje.dataReferencia}T12:00:00`))}
              {' · '}
              {data.hoje.fusoHorario}
            </p>
          ) : null}
        </div>
        <Button
          type="button"
          variant="outline"
          onClick={() => setRefreshKey((value) => value + 1)}
          disabled={refreshing}
        >
          <RefreshCw className={`mr-2 h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} />
          Atualizar
        </Button>
      </header>

      {sessionError ? (
        <div className="flex flex-col items-start justify-between gap-3 border border-red-200 bg-red-50 p-4 text-sm text-red-800 sm:flex-row sm:items-center">
          <p role="alert">{sessionError}</p>
          <Button type="button" size="sm" variant="outline" onClick={() => setRefreshKey((value) => value + 1)}>
            Tentar novamente
          </Button>
        </div>
      ) : null}

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {cards.map((item) => (
          <Metric key={item.id} item={item} state={sources[item.source]} />
        ))}
      </div>

      {!sessionError && session && cards.length === 0 ? (
        <div className="border-y border-zinc-200 py-12 text-center">
          <p className="text-sm font-medium text-zinc-800">Nenhum indicador disponível para suas permissões.</p>
        </div>
      ) : null}

      {!sessionError && session ? (
        <AdminDashboardAnalytics session={session} refreshKey={refreshKey} />
      ) : null}
    </section>
  )
}
