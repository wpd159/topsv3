'use client'

import { useEffect, useState } from 'react'

import type { AdminSession } from '@/lib/admin-auth-api'
import {
  fetchDashboardAnalyses,
  fetchDashboardDailyPerformance,
  type AdminDashboardAnalyses,
  type AdminDashboardDailyPerformance,
} from '@/lib/admin-dashboard-api'
import { CommercialOpportunitiesCard } from './CommercialOpportunitiesCard'
import { PriorityAlertsCard } from './PriorityAlertsCard'
import { StrategicAnalysisTables } from './StrategicAnalysisTables'
import { StrategicConversionRankings } from './StrategicConversionRankings'
import { StrategicPerformanceChart } from './StrategicPerformanceChart'
import { TopWhatsappHojeCard } from './TopWhatsappHojeCard'

type Period = 7 | 15 | 30

function errorMessage(error: unknown) {
  return error instanceof Error && error.message
    ? error.message
    : 'Não foi possível carregar este bloco.'
}

export function AdminDashboardAnalytics({
  session,
  refreshKey,
}: {
  session: AdminSession
  refreshKey: number
}) {
  const [period, setPeriod] = useState<Period>(30)
  const [daily, setDaily] = useState<AdminDashboardDailyPerformance | null>(null)
  const [dailyLoading, setDailyLoading] = useState(true)
  const [dailyError, setDailyError] = useState<string | null>(null)
  const [dailyRetry, setDailyRetry] = useState(0)
  const [analyses, setAnalyses] = useState<AdminDashboardAnalyses | null>(null)
  const [analysesLoading, setAnalysesLoading] = useState(true)
  const [analysesError, setAnalysesError] = useState<string | null>(null)
  const [analysesRetry, setAnalysesRetry] = useState(0)

  const canRead = session.permissoes.includes('ANUNCIO_LER')
  const isAdmin = session.papeis.includes('ADMIN')

  useEffect(() => {
    if (!canRead) return
    const controller = new AbortController()
    setDailyLoading(true)
    setDailyError(null)
    void fetchDashboardDailyPerformance(period, controller.signal)
      .then(setDaily)
      .catch((error) => {
        if (!controller.signal.aborted) setDailyError(errorMessage(error))
      })
      .finally(() => {
        if (!controller.signal.aborted) setDailyLoading(false)
      })
    return () => controller.abort()
  }, [canRead, period, refreshKey, dailyRetry])

  useEffect(() => {
    if (!canRead) return
    const controller = new AbortController()
    setAnalysesLoading(true)
    setAnalysesError(null)
    void fetchDashboardAnalyses(controller.signal)
      .then(setAnalyses)
      .catch((error) => {
        if (!controller.signal.aborted) setAnalysesError(errorMessage(error))
      })
      .finally(() => {
        if (!controller.signal.aborted) setAnalysesLoading(false)
      })
    return () => controller.abort()
  }, [canRead, refreshKey, analysesRetry])

  if (!canRead) return null

  return (
    <div className="space-y-8 border-t border-zinc-200 pt-8">
      <header>
        <h2 className="text-xl font-bold text-zinc-950">Desempenho e rankings</h2>
        <p className="mt-1 text-sm text-zinc-600">
          Métricas internas agregadas, sem eventos do GA4.
        </p>
      </header>

      <StrategicPerformanceChart
        data={daily}
        loading={dailyLoading}
        period={period}
        onPeriodChange={setPeriod}
        error={dailyError}
        onRetry={() => setDailyRetry((value) => value + 1)}
      />

      <TopWhatsappHojeCard refreshKey={refreshKey} />

      <div className="grid gap-6 lg:grid-cols-2">
        <PriorityAlertsCard
          items={analyses?.alertasPrioritarios ?? []}
          loading={analysesLoading}
          error={analysesError}
          onRetry={() => setAnalysesRetry((value) => value + 1)}
        />
        {isAdmin ? (
          <CommercialOpportunitiesCard
            items={analyses?.oportunidadesComerciais ?? []}
            loading={analysesLoading}
            error={analysesError}
            onRetry={() => setAnalysesRetry((value) => value + 1)}
          />
        ) : null}
      </div>

      <StrategicConversionRankings
        topConversao={analyses?.topConversao ?? []}
        piorConversao={analyses?.piorConversaoComTrafego ?? []}
        minimumViews={analyses?.minimoVisualizacoesPiorConversao ?? 100}
        loading={analysesLoading}
        error={analysesError}
        onRetry={() => setAnalysesRetry((value) => value + 1)}
      />

      <StrategicAnalysisTables
        cidadeRows={analyses?.desempenhoPorCidade ?? []}
        classificacaoRows={analyses?.desempenhoPorClassificacao ?? []}
        loading={analysesLoading}
        error={analysesError}
        onRetry={() => setAnalysesRetry((value) => value + 1)}
      />
    </div>
  )
}
