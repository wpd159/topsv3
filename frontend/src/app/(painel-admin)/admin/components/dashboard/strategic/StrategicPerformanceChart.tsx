'use client'

import { useEffect, useMemo } from 'react'
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { DesempenhoDiarioResponse } from '@/lib/admin-estatisticas-api'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

type Period = 7 | 15 | 30

type Props = {
  data: DesempenhoDiarioResponse | null
  loading: boolean
  period: Period
  onPeriodChange: (p: Period) => void
}

function formatAxisDate(iso: string) {
  const [y, m, d] = iso.split('-').map(Number)
  if (!y || !m || !d) return iso
  return `${String(d).padStart(2, '0')}/${String(m).padStart(2, '0')}`
}

function fmtInt(n: number | undefined) {
  return Number(n ?? 0).toLocaleString('pt-BR')
}

function fmtPct(n: number | undefined | null) {
  if (n == null || Number.isNaN(n)) return '—'
  const s = `${n > 0 ? '+' : ''}${Number(n).toLocaleString('pt-BR', { maximumFractionDigits: 1, minimumFractionDigits: 1 })}%`
  return s
}

function coerceDailyPoint(p: { data?: string; visualizacoes?: unknown; cliquesWhatsapp?: unknown }) {
  const dia = typeof p.data === 'string' ? p.data : ''
  const visualizacoes = Number(p.visualizacoes ?? 0)
  const cliquesWhatsapp = Number(p.cliquesWhatsapp ?? 0)
  return {
    data: dia,
    visualizacoes: Number.isFinite(visualizacoes) ? Math.max(0, visualizacoes) : 0,
    cliquesWhatsapp: Number.isFinite(cliquesWhatsapp) ? Math.max(0, cliquesWhatsapp) : 0,
    label: formatAxisDate(dia),
  }
}

export function StrategicPerformanceChart({ data, loading, period, onPeriodChange }: Props) {
  const chartData = useMemo(
    () => (data?.serieDiaria ?? []).map((p) => coerceDailyPoint(p)),
    [data?.serieDiaria]
  )

  const maxViews = useMemo(() => chartData.reduce((m, p) => Math.max(m, p.visualizacoes), 0), [chartData])
  const maxClicks = useMemo(() => chartData.reduce((m, p) => Math.max(m, p.cliquesWhatsapp), 0), [chartData])
  const sumViewsSerie = useMemo(() => chartData.reduce((s, p) => s + p.visualizacoes, 0), [chartData])

  useEffect(() => {
    if (process.env.NODE_ENV !== 'development' || !data || chartData.length === 0) return
    const last = chartData[chartData.length - 1]
    console.debug('[StrategicPerformanceChart série]', {
      pontos: chartData.length,
      maxViews,
      maxClicks,
      sumViewsSerie,
      ultimoDia: last?.data,
      ultimoViews: last?.visualizacoes,
      ultimoCliques: last?.cliquesWhatsapp,
      resumoHojeViews: data.hojeVisualizacoes,
      resumoHojeCliques: data.hojeCliquesWhatsapp,
    })
  }, [data, chartData, maxViews, maxClicks, sumViewsSerie])

  const periods: { key: Period; label: string }[] = [
    { key: 7, label: '7d' },
    { key: 15, label: '15d' },
    { key: 30, label: '30d' },
  ]

  const resumoCoerente =
    !data ||
    chartData.length === 0 ||
    lastPointRoughlyMatchesHoje(chartData, data.hojeVisualizacoes, data.hojeCliquesWhatsapp)

  return (
    <div className="rounded-2xl border border-gray-200/80 bg-white p-6 shadow-sm">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0">
          <h2 className="text-xl font-bold tracking-tight text-gray-900">Desempenho diário</h2>
          <p className="mt-1 max-w-xl text-sm text-gray-500">
            Visualizações (log diário na API) e cliques WhatsApp por dia — barras para volume de views, linha para
            cliques (eixos separados).
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            {periods.map(({ key, label }) => (
              <Button
                key={key}
                type="button"
                size="sm"
                variant={period === key ? 'default' : 'outline'}
                className={cn(
                  period === key && 'bg-[#f0198f] text-white hover:bg-[#d9157d]',
                  period !== key && 'border-gray-200 text-gray-700'
                )}
                onClick={() => onPeriodChange(key)}
              >
                {label}
              </Button>
            ))}
          </div>
        </div>

        <div className="w-full shrink-0 rounded-xl border border-gray-100 bg-gray-50/60 px-4 py-3 text-sm lg:w-72">
          <p className="text-[11px] font-semibold uppercase tracking-wide text-gray-500">Resumo</p>
          {loading ? (
            <p className="mt-2 text-gray-500">Carregando…</p>
          ) : (
            <>
              <p className="mt-2 text-gray-800">
                <span className="font-medium text-gray-600">Hoje:</span>{' '}
                {fmtInt(data?.hojeVisualizacoes)} views · {fmtInt(data?.hojeCliquesWhatsapp)} cliques ·{' '}
                {data?.hojeConversaoPct != null
                  ? `${Number(data.hojeConversaoPct).toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%`
                  : '—'}
              </p>
              <p className="mt-1 text-gray-800">
                <span className="font-medium text-gray-600">Ontem:</span>{' '}
                {fmtInt(data?.ontemVisualizacoes)} views · {fmtInt(data?.ontemCliquesWhatsapp)} cliques ·{' '}
                {data?.ontemConversaoPct != null
                  ? `${Number(data.ontemConversaoPct).toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%`
                  : '—'}
              </p>
              <p className="mt-2 border-t border-gray-200/80 pt-2 text-gray-800">
                <span className="font-medium text-gray-600">Variação (vs ontem):</span> views {fmtPct(data?.varVisualizacoesPct)}{' '}
                · cliques {fmtPct(data?.varCliquesPct)}
              </p>
            </>
          )}
        </div>
      </div>

      {!loading && data && !resumoCoerente ? (
        <p className="mt-3 text-xs text-amber-800">
          O último ponto da série não bate com o resumo &quot;Hoje&quot; — pode ser fuso horário (servidor vs navegador)
          ou fechamento do dia ainda não alinhado. Confira os números brutos no painel da API.
        </p>
      ) : null}

      <div className="mt-6 h-[340px] w-full">
        {loading ? (
          <div className="flex h-full items-center justify-center rounded-xl border border-dashed border-gray-200 bg-gray-50/50 text-sm text-gray-500">
            Carregando série…
          </div>
        ) : chartData.length === 0 ? (
          <div className="flex h-full items-center justify-center rounded-xl border border-dashed border-gray-200 bg-gray-50/50 text-sm text-gray-500">
            Nenhum dado no período.
          </div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <ComposedChart data={chartData} margin={{ top: 8, right: 16, left: 4, bottom: 4 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" vertical={false} />
              <XAxis dataKey="label" tick={{ fontSize: 11, fill: '#6b7280' }} axisLine={{ stroke: '#e5e7eb' }} />
              <YAxis
                yAxisId="views"
                tick={{ fontSize: 11, fill: '#2563eb' }}
                axisLine={{ stroke: '#e5e7eb' }}
                tickFormatter={(v) => Number(v).toLocaleString('pt-BR', { notation: 'compact' })}
              />
              <YAxis
                yAxisId="cliques"
                orientation="right"
                tick={{ fontSize: 11, fill: '#059669' }}
                axisLine={{ stroke: '#e5e7eb' }}
                tickFormatter={(v) => Number(v).toLocaleString('pt-BR', { notation: 'compact' })}
              />
              <Tooltip
                contentStyle={{
                  borderRadius: 10,
                  border: '1px solid #e5e7eb',
                  fontSize: 13,
                }}
                labelFormatter={(_, payload) => {
                  const p = payload?.[0]?.payload as { data?: string } | undefined
                  return p?.data ?? ''
                }}
                formatter={(value: number | string, name: string) => [Number(value).toLocaleString('pt-BR'), name]}
              />
              <Legend wrapperStyle={{ fontSize: 13, paddingTop: 12 }} />
              <Bar
                yAxisId="views"
                dataKey="visualizacoes"
                name="Visualizações (dia)"
                fill="#93c5fd"
                radius={[4, 4, 0, 0]}
                maxBarSize={28}
              />
              <Line
                yAxisId="cliques"
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
      {!loading && chartData.length > 0 ? (
        <p className="mt-2 text-center text-[11px] text-gray-500">
          Série: soma de views no período ≈ {sumViewsSerie.toLocaleString('pt-BR')} (somatório dos dias exibidos).
        </p>
      ) : null}
    </div>
  )
}

function lastPointRoughlyMatchesHoje(
  chartData: Array<{ data: string; visualizacoes: number; cliquesWhatsapp: number }>,
  hojeV: number | undefined,
  hojeC: number | undefined
): boolean {
  const last = chartData[chartData.length - 1]
  if (!last) return true
  const hv = Number(hojeV ?? 0)
  const hc = Number(hojeC ?? 0)
  const tolV = hv === 0 ? 0 : Math.max(5, Math.round(hv * 0.05))
  const tolC = hc === 0 ? 0 : Math.max(2, Math.round(hc * 0.05))
  const okV = hv === 0 ? last.visualizacoes === 0 : Math.abs(last.visualizacoes - hv) <= tolV
  const okC = hc === 0 ? last.cliquesWhatsapp === 0 : Math.abs(last.cliquesWhatsapp - hc) <= tolC
  return okV && okC
}
