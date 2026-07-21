'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'
import { ArrowTrendingUpIcon, ChatBubbleLeftRightIcon, EyeIcon, RocketLaunchIcon } from '@heroicons/react/24/solid'
import { Line, LineChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { toast } from 'sonner'
import { fetchPainelPerformance, type PainelPerformance } from '@/lib/painel-anunciante-api'
import { PainelShell } from '@/components/painel-anunciante/painel-shell'
import { PainelKpiCard } from '@/components/painel-anunciante/painel-kpi-card'
import { ContractState } from '@/components/feedback/contract-state'
import { normalizeApiError } from '@/lib/api-contract'
import { formatarVisualizacoesCanonicas } from '@/lib/visualizacoes-canonicas'

function formatNumber(value: number) {
  return value.toLocaleString('pt-BR')
}

function formatPercent(value: number | null) {
  if (value === null) return '\u2014'
  return `${value.toLocaleString('pt-BR', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}%`
}

function formatSeriesLabel(value: string) {
  return new Date(`${value}T00:00:00Z`).toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    timeZone: 'UTC',
  })
}

function formatTrend(value: number) {
  if (value > 0) return 'de alta'
  if (value < 0) return 'de queda'
  return 'estavel'
}

export default function PainelAnunciantePerformancePage() {
  const [data, setData] = useState<PainelPerformance | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [requestVersion, setRequestVersion] = useState(0)

  useEffect(() => {
    let active = true

    const load = async () => {
      try {
        setLoading(true)
        setError(null)
        const response = await fetchPainelPerformance()
        if (!active) return
        setData(response)
      } catch (cause) {
        if (!active) return
        const normalized = normalizeApiError(cause)
        setError(normalized)
        toast.error(normalized.message)
      } finally {
        if (active) setLoading(false)
      }
    }

    load()
    return () => {
      active = false
    }
  }, [requestVersion])

  const cards = useMemo(() => {
    if (!data) return []
    return [
      {
        label: 'Visualizações totais',
        value: formatarVisualizacoesCanonicas(data.visualizacoes),
        helper: 'Volume total acumulado de exposição dos seus anúncios.',
        accent: 'blue' as const,
      },
      {
        label: 'Cliques no WhatsApp',
        value: formatNumber(data.totalCliquesWhatsapp),
        helper: 'Cliques reais em intenção direta de contato.',
        accent: 'green' as const,
      },
      {
        label: 'CTR geral',
        value: formatPercent(data.ctrGeral),
        helper: 'Relação entre visualizações e cliques no WhatsApp.',
        accent: 'pink' as const,
      },
      {
        label: 'Ads com premium',
        value: formatNumber(data.anunciosComBeneficioPremiumVigente),
        helper: 'Anúncios com ao menos um benefício premium vigente.',
        accent: 'amber' as const,
      },
    ]
  }, [data])

  const chartData = useMemo(
    () => data?.serieCliquesWhatsapp.map((item) => ({ ...item, label: formatSeriesLabel(item.data) })) ?? [],
    [data]
  )

  return (
    <PainelShell
      title="Performance"
      description="Aqui os números reais aparecem com mais detalhe: evolução diária, CTR, ranking por anúncio e situação dos recursos premium ativos."
    >
      <div className="space-y-6">
        {loading ? (
          <>
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {Array.from({ length: 4 }).map((_, index) => (
                <div key={index} className="h-[138px] rounded-[28px] border border-slate-200 bg-white shadow-sm" />
              ))}
            </div>
            <div className="h-[360px] rounded-[32px] border border-slate-200 bg-white shadow-sm" />
          </>
        ) : error || !data ? (
          <ContractState
            error={error ?? new Error('Não foi possível carregar a performance.')}
            onRetry={() => setRequestVersion((version) => version + 1)}
          />
        ) : (
          <>
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {cards.map((card) => (
                <PainelKpiCard
                  key={card.label}
                  label={card.label}
                  value={card.value}
                  helper={card.helper}
                  accent={card.accent}
                />
              ))}
            </div>

            <div className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
              <section className="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm md:p-7">
                <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">
                      Evolução diária
                    </p>
                    <h2 className="mt-2 text-2xl font-extrabold tracking-tight text-slate-900">
                      Cliques no WhatsApp nos últimos 14 dias
                    </h2>
                    <p className="mt-2 text-sm leading-6 text-slate-600">
                      Esta curva mostra intenção direta de contato, que hoje é o sinal mais confiável disponível na base atual.
                    </p>
                  </div>
                </div>

                <div className="mt-6 h-[280px] w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={chartData}>
                      <CartesianGrid stroke="#e5e7eb" strokeDasharray="4 4" vertical={false} />
                      <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fill: '#64748b', fontSize: 12 }} />
                      <YAxis allowDecimals={false} tickLine={false} axisLine={false} tick={{ fill: '#64748b', fontSize: 12 }} />
                      <Tooltip
                        contentStyle={{
                          borderRadius: 16,
                          border: '1px solid #e2e8f0',
                          boxShadow: '0 12px 30px rgba(15,23,42,0.08)',
                        }}
                        formatter={(value) => [`${value} clique(s)`, 'WhatsApp']}
                      />
                      <Line
                        type="monotone"
                        dataKey="cliques"
                        stroke="#FC1EAD"
                        strokeWidth={3}
                        dot={{ r: 4, fill: '#FC1EAD' }}
                        activeDot={{ r: 6 }}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              </section>

              <section className="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm md:p-7">
                <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">
                  Comparação entre períodos
                </p>
                <h2 className="mt-2 text-2xl font-extrabold tracking-tight text-slate-900">
                  Ritmo comercial recente
                </h2>

                <div className="mt-6 grid gap-4">
                  <div className="rounded-3xl border border-slate-200 bg-slate-50 p-5">
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-sm font-semibold text-slate-500">Últimos 7 dias</p>
                        <p className="mt-2 text-3xl font-extrabold text-slate-900">
                          {formatNumber(data.comparativo.cliquesPeriodoAtual)}
                        </p>
                      </div>
                      <ChatBubbleLeftRightIcon className="h-8 w-8 text-[#FC1EAD]" />
                    </div>
                  </div>

                  <div className="rounded-3xl border border-slate-200 bg-slate-50 p-5">
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-sm font-semibold text-slate-500">7 dias anteriores</p>
                        <p className="mt-2 text-3xl font-extrabold text-slate-900">
                          {formatNumber(data.comparativo.cliquesPeriodoAnterior)}
                        </p>
                      </div>
                      <EyeIcon className="h-8 w-8 text-slate-500" />
                    </div>
                  </div>

                  <div className="rounded-3xl border border-slate-200 bg-[linear-gradient(135deg,#fff5fb_0%,#ffffff_100%)] p-5">
                    <div className="flex items-center justify-between gap-4">
                      <div>
                        <p className="text-sm font-semibold text-slate-500">Variação</p>
                        <p className="mt-2 text-3xl font-extrabold text-slate-900">
                          {formatPercent(data.comparativo.variacaoPercentual)}
                        </p>
                        <p className="mt-2 text-sm text-slate-600">
                          Tendência {formatTrend(data.comparativo.variacaoPercentual)}.
                        </p>
                      </div>
                      <ArrowTrendingUpIcon className="h-8 w-8 text-[#FC1EAD]" />
                    </div>
                  </div>
                </div>
              </section>
            </div>

            <section className="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm md:p-7">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">
                    Ranking detalhado
                  </p>
                  <h2 className="mt-2 text-2xl font-extrabold tracking-tight text-slate-900">
                    Anúncios ordenados por resultado
                  </h2>
                  <p className="mt-2 text-sm leading-6 text-slate-600">
                    Aqui entram os números reais de visualizações, cliques, CTR e recursos premium ativos.
                  </p>
                </div>

                <Link
                  href="/creditos"
                  className="inline-flex items-center gap-2 text-sm font-semibold text-[#FC1EAD] transition hover:text-[#d71897]"
                >
                  Comprar créditos
                  <RocketLaunchIcon className="h-4 w-4" />
                </Link>
              </div>

              <div className="mt-6 overflow-hidden rounded-3xl border border-slate-200">
                <div className="overflow-x-auto">
                  <table className="min-w-full divide-y divide-slate-200 text-sm">
                    <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
                      <tr>
                        <th className="px-4 py-4">Anúncio</th>
                        <th className="px-4 py-4 text-right">Views</th>
                        <th className="px-4 py-4 text-right">Cliques</th>
                        <th className="px-4 py-4 text-right">CTR</th>
                        <th className="px-4 py-4">Premium</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-200 bg-white">
                      {data.ranking.length === 0 ? (
                        <tr>
                          <td colSpan={5} className="px-4 py-10 text-center text-sm text-slate-500">
                            Nenhum anúncio disponível para análise ainda.
                          </td>
                        </tr>
                      ) : (
                        data.ranking.map((item) => (
                          <tr key={item.anuncioId} className="align-top">
                            <td className="px-4 py-4">
                              <div className="flex min-w-[240px] items-start gap-3">
                                <div className="relative h-16 w-14 shrink-0 overflow-hidden rounded-2xl bg-slate-100">
                                  {item.fotoCapa ? (
                                    <img src={item.fotoCapa} alt={item.anuncioTitulo} className="h-full w-full object-cover" />
                                  ) : null}
                                </div>
                                <div className="min-w-0">
                                  <p className="line-clamp-2 font-semibold text-slate-900">{item.anuncioTitulo}</p>
                                  {item.localizacao ? (
                                    <p className="mt-1 text-xs text-slate-500">{item.localizacao}</p>
                                  ) : null}
                                </div>
                              </div>
                            </td>
                            <td className="px-4 py-4 text-right font-semibold text-slate-900">
                              {formatarVisualizacoesCanonicas(item.visualizacoes)}
                            </td>
                            <td className="px-4 py-4 text-right font-semibold text-slate-900">
                              {formatNumber(item.cliquesWhatsapp)}
                            </td>
                            <td className="px-4 py-4 text-right font-semibold text-slate-900">
                              {formatPercent(item.ctr)}
                            </td>
                            <td className="px-4 py-4">
                              <div className="flex min-w-[180px] flex-wrap gap-2">
                                {item.beneficiosPremiumVigentes === 0 ? (
                                  <span className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-[11px] font-semibold text-slate-500">
                                    Sem benefício vigente
                                  </span>
                                ) : (
                                  <span className="rounded-full border border-amber-200 bg-amber-50 px-2.5 py-1 text-[11px] font-semibold text-amber-700">
                                    {item.beneficiosPremiumVigentes}{' '}
                                    {item.beneficiosPremiumVigentes === 1 ? 'benefício vigente' : 'benefícios vigentes'}
                                  </span>
                                )}
                              </div>
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </section>
          </>
        )}
      </div>
    </PainelShell>
  )
}
