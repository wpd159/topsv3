'use client'

import { useMemo } from 'react'
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

import { Button } from '@/components/ui/button'
import type { AdminDashboardDailyPerformance } from '@/lib/admin-dashboard-api'
import { cn } from '@/lib/utils'

type Period = 7 | 15 | 30

type Props = {
  data: AdminDashboardDailyPerformance | null
  loading: boolean
  period: Period
  onPeriodChange: (period: Period) => void
  error: string | null
  onRetry: () => void
}

function formatDate(iso: string) {
  const [year, month, day] = iso.split('-').map(Number)
  if (!year || !month || !day) return iso
  return `${String(day).padStart(2, '0')}/${String(month).padStart(2, '0')}`
}

function formatNumber(value: number) {
  return value.toLocaleString('pt-BR')
}

function formatPercent(value: number, showSign = false) {
  const sign = showSign && value > 0 ? '+' : ''
  return `${sign}${value.toLocaleString('pt-BR', {
    minimumFractionDigits: 1,
    maximumFractionDigits: 2,
  })}%`
}

function PerformanceTooltip({
  active,
  payload,
}: {
  active?: boolean
  payload?: Array<{
    payload?: {
      data: string
      visualizacoes: number
      cliquesWhatsapp: number
      conversaoPct: number
    }
  }>
}) {
  const point = payload?.[0]?.payload
  if (!active || !point) return null

  return (
    <div className="border border-zinc-200 bg-white p-3 text-xs shadow-lg">
      <p className="font-semibold text-zinc-950">{formatDate(point.data)}</p>
      <p className="mt-2 text-blue-700">
        {formatNumber(point.visualizacoes)} visualizações
      </p>
      <p className="text-emerald-700">
        {formatNumber(point.cliquesWhatsapp)} cliques
      </p>
      <p className="text-zinc-600">
        Conversão: {formatPercent(point.conversaoPct)}
      </p>
    </div>
  )
}

export function StrategicPerformanceChart({
  data,
  loading,
  period,
  onPeriodChange,
  error,
  onRetry,
}: Props) {
  const chartData = useMemo(
    () =>
      (data?.serieDiaria ?? []).map((point) => ({
        ...point,
        label: formatDate(point.data),
      })),
    [data],
  )

  return (
    <section className="border border-zinc-200 bg-white p-5">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h3 className="text-base font-bold text-zinc-950">Desempenho diário</h3>
          <p className="mt-1 text-xs text-zinc-600">
            Visualizações internas e cliques no WhatsApp no fuso canônico.
          </p>
          <div className="mt-3 flex gap-2" aria-label="Período do gráfico">
            {([7, 15, 30] as const).map((days) => (
              <Button
                key={days}
                type="button"
                size="sm"
                variant={period === days ? 'default' : 'outline'}
                className={cn(
                  period === days && 'bg-pink-600 text-white hover:bg-pink-700',
                )}
                onClick={() => onPeriodChange(days)}
              >
                {days} dias
              </Button>
            ))}
          </div>
        </div>

        {data ? (
          <div className="grid w-full gap-2 text-xs sm:grid-cols-2 lg:w-auto lg:min-w-[560px] lg:grid-cols-4">
            <div className="border-l-2 border-blue-500 pl-3">
              <span className="text-zinc-500">Período</span>
              <strong className="block text-sm text-zinc-950">
                {formatNumber(data.totalVisualizacoes)} views
              </strong>
              <span className="text-zinc-600">
                {formatNumber(data.totalCliquesWhatsapp)} cliques
              </span>
            </div>
            <div className="border-l-2 border-zinc-300 pl-3">
              <span className="text-zinc-500">Hoje</span>
              <strong className="block text-sm text-zinc-950">
                {formatNumber(data.hoje.visualizacoes)} views · {formatPercent(data.hoje.conversaoPct)}
              </strong>
              <span className="text-zinc-600">
                {formatNumber(data.hoje.cliquesWhatsapp)} cliques
              </span>
            </div>
            <div className="border-l-2 border-zinc-400 pl-3">
              <span className="text-zinc-500">Ontem</span>
              <strong className="block text-sm text-zinc-950">
                {formatNumber(data.ontem.visualizacoes)} views · {formatPercent(data.ontem.conversaoPct)}
              </strong>
              <span className="text-zinc-600">
                {formatNumber(data.ontem.cliquesWhatsapp)} cliques
              </span>
            </div>
            <div className="border-l-2 border-emerald-500 pl-3">
              <span className="text-zinc-500">Variação diária</span>
              <strong className="block text-sm text-zinc-950">
                {formatPercent(data.variacaoVisualizacoesPct, true)} views
              </strong>
              <span className="text-zinc-600">
                {formatPercent(data.variacaoCliquesPct, true)} cliques
              </span>
            </div>
          </div>
        ) : null}
      </div>

      <div className="mt-4 flex flex-wrap gap-4 text-xs text-zinc-600" aria-label="Legenda do gráfico">
        <span className="inline-flex items-center gap-2">
          <span className="h-3 w-3 bg-blue-300" aria-hidden />
          Barras: visualizações
        </span>
        <span className="inline-flex items-center gap-2">
          <span className="h-0.5 w-5 bg-emerald-600" aria-hidden />
          Linha: cliques no WhatsApp
        </span>
      </div>

      <div className="mt-5 h-[320px] w-full">
        {loading ? (
          <div className="flex h-full items-center justify-center border border-dashed border-zinc-200 text-sm text-zinc-500">
            Carregando série diária...
          </div>
        ) : error ? (
          <div className="flex h-full flex-col items-center justify-center gap-3 border border-red-200 bg-red-50 p-5 text-center">
            <p className="text-sm text-red-800" role="alert">{error}</p>
            <Button type="button" size="sm" variant="outline" onClick={onRetry}>
              Tentar novamente
            </Button>
          </div>
        ) : chartData.length === 0 ? (
          <div className="flex h-full items-center justify-center border border-dashed border-zinc-200 text-sm text-zinc-500">
            Nenhum evento interno no período.
          </div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <ComposedChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e4e4e7" vertical={false} />
              <XAxis
                dataKey="label"
                tick={{ fontSize: 11, fill: '#71717a' }}
                axisLine={{ stroke: '#d4d4d8' }}
              />
              <YAxis
                yAxisId="views"
                tick={{ fontSize: 11, fill: '#2563eb' }}
                tickFormatter={(value) => Number(value).toLocaleString('pt-BR', { notation: 'compact' })}
                axisLine={false}
              />
              <YAxis
                yAxisId="clicks"
                orientation="right"
                tick={{ fontSize: 11, fill: '#047857' }}
                tickFormatter={(value) => Number(value).toLocaleString('pt-BR', { notation: 'compact' })}
                axisLine={false}
              />
              <Tooltip content={<PerformanceTooltip />} />
              <Bar
                yAxisId="views"
                dataKey="visualizacoes"
                name="Visualizações"
                fill="#93c5fd"
                radius={[3, 3, 0, 0]}
                maxBarSize={28}
              />
              <Line
                yAxisId="clicks"
                type="monotone"
                dataKey="cliquesWhatsapp"
                name="Cliques WhatsApp"
                stroke="#059669"
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 4 }}
              />
            </ComposedChart>
          </ResponsiveContainer>
        )}
      </div>
    </section>
  )
}
