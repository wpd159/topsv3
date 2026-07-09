'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'
import {
  ArrowUpRightIcon,
  BoltIcon,
  PlusIcon,
  RocketLaunchIcon,
  SparklesIcon,
} from '@heroicons/react/24/solid'
import { toast } from 'sonner'
import { useAuth } from '@/context/AuthContext'
import { fetchPainelOverview, type PainelOverview } from '@/lib/painel-anunciante-api'
import { PainelShell } from '@/components/painel-anunciante/painel-shell'
import { VisibilityScoreCard } from '@/components/painel-anunciante/visibility-score-card'
import { PainelKpiCard } from '@/components/painel-anunciante/painel-kpi-card'
import { RecommendationCard } from '@/components/painel-anunciante/recommendation-card'

function pluralize(count: number, singular: string, plural: string) {
  return count === 1 ? singular : plural
}

export default function PainelAnunciantePage() {
  const { usuario } = useAuth()
  const [data, setData] = useState<PainelOverview | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true

    const load = async () => {
      try {
        setLoading(true)
        setError(null)
        const response = await fetchPainelOverview()
        if (!active) return
        setData(response)
      } catch {
        if (!active) return
        setError('Não foi possível carregar o painel agora.')
        toast.error('Não foi possível carregar o painel do anunciante.')
      } finally {
        if (active) setLoading(false)
      }
    }

    void load()
    return () => {
      active = false
    }
  }, [])

  const kpis = useMemo(() => {
    if (!data) return []

    return [
      {
        label: 'Score médio',
        value: `${data.scoreMedioVisibilidade}/100`,
        helper:
          data.scoreMedioVisibilidade >= 61
            ? 'Sua base comercial já está competitiva.'
            : 'Há espaço claro para melhorar sua presença.',
        accent: 'pink' as const,
      },
      {
        label: 'Anúncios ativos',
        value: data.anunciosAtivos,
        helper: `${data.anunciosPendentes} ${pluralize(
          data.anunciosPendentes,
          'pendente',
          'pendentes'
        )} e ${data.anunciosPausados} ${pluralize(data.anunciosPausados, 'pausado', 'pausados')}.`,
        accent: 'blue' as const,
      },
      {
        label: 'Prontos para escalar',
        value: data.anunciosProntosParaEscalar,
        helper: 'Anúncios com score suficiente para receber upgrades com mais retorno.',
        accent: 'green' as const,
      },
      {
        label: 'Saldo disponível',
        value: `${data.saldoCreditos} créditos`,
        helper: `${data.totalRecursosPremiumAtivos} ${pluralize(
          data.totalRecursosPremiumAtivos,
          'recurso premium ativo',
          'recursos premium ativos'
        )} agora.`,
        accent: 'amber' as const,
      },
    ]
  }, [data])

  return (
    <PainelShell
      title="Visão geral comercial"
      description="Um painel SaaS pensado para mostrar potencial de crescimento, guiar upgrades e transformar seus anúncios em uma máquina de visibilidade."
    >
      <div className="space-y-6 pb-24 md:pb-10">
        <section className="rounded-[32px] border border-slate-200 bg-slate-900 px-6 py-6 text-white shadow-sm md:px-8 md:py-8">
          <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
            <div className="max-w-3xl">
              <span className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em] text-pink-200">
                <SparklesIcon className="h-4 w-4" />
                Painel comercial
              </span>

              <h2 className="mt-4 text-3xl font-extrabold tracking-tight md:text-4xl">
                {loading ? 'Carregando seu resumo...' : data?.headline}
              </h2>
              <p className="mt-3 max-w-2xl text-sm leading-7 text-slate-300 md:text-base">
                {loading
                  ? 'Buscando os dados de performance, créditos e visibilidade dos seus anúncios.'
                  : `${usuario?.username ? `${usuario.username}, ` : ''}${data?.subheadline ?? ''}`}
              </p>
            </div>

            <div className="grid gap-3 sm:grid-cols-2 lg:w-[360px]">
              <Link
                href={data?.primaryCtaTarget ?? '/creditos'}
                className="inline-flex items-center justify-center gap-2 rounded-2xl bg-[#FC1EAD] px-5 py-3 text-sm font-semibold text-white transition hover:bg-[#df1698]"
              >
                {data?.primaryCtaLabel ?? 'Comprar créditos'}
                <ArrowUpRightIcon className="h-4 w-4" />
              </Link>
              <Link
                href={data?.secondaryCtaTarget ?? '/meus-anuncios'}
                className="inline-flex items-center justify-center gap-2 rounded-2xl border border-white/15 bg-white/5 px-5 py-3 text-sm font-semibold text-white transition hover:bg-white/10"
              >
                {data?.secondaryCtaLabel ?? 'Gerenciar anúncios'}
              </Link>
            </div>
          </div>
        </section>

        {loading ? (
          <div className="grid gap-6 lg:grid-cols-[1.3fr_0.7fr]">
            <div className="h-[280px] rounded-[32px] border border-slate-200 bg-white shadow-sm" />
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">
              <div className="h-[132px] rounded-[28px] border border-slate-200 bg-white shadow-sm" />
              <div className="h-[132px] rounded-[28px] border border-slate-200 bg-white shadow-sm" />
              <div className="h-[132px] rounded-[28px] border border-slate-200 bg-white shadow-sm" />
              <div className="h-[132px] rounded-[28px] border border-slate-200 bg-white shadow-sm" />
            </div>
          </div>
        ) : error || !data ? (
          <div className="rounded-[28px] border border-rose-200 bg-rose-50 px-6 py-5 text-sm text-rose-700">
            {error ?? 'Não foi possível carregar o painel.'}
          </div>
        ) : (
          <>
            <div className="grid gap-6 lg:grid-cols-[1.3fr_0.7fr]">
              <VisibilityScoreCard
                visibilidade={data.visibilidade}
                primaryCtaLabel={data.primaryCtaLabel}
                primaryCtaTarget={data.primaryCtaTarget}
              />

              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">
                {kpis.map((item) => (
                  <PainelKpiCard
                    key={item.label}
                    label={item.label}
                    value={item.value}
                    helper={item.helper}
                    accent={item.accent}
                  />
                ))}
              </div>
            </div>

            <div className="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
              <section className="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm md:p-7">
                <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">
                      Ranking dos anúncios
                    </p>
                    <h3 className="mt-2 text-2xl font-extrabold tracking-tight text-slate-900">
                      Seus anúncios com maior potencial agora
                    </h3>
                    <p className="mt-2 text-sm leading-6 text-slate-600">
                      Aqui o foco é percepção de força comercial: score, recursos premium e próximo
                      passo recomendado.
                    </p>
                  </div>

                  <Link
                    href="/meus-anuncios"
                    className="inline-flex items-center gap-2 text-sm font-semibold text-[#FC1EAD] transition hover:text-[#d71897]"
                  >
                    Ver todos
                    <ArrowUpRightIcon className="h-4 w-4" />
                  </Link>
                </div>

                <div className="mt-6 space-y-4">
                  {data.ranking.length === 0 ? (
                    <div className="rounded-3xl border border-dashed border-slate-300 bg-slate-50 px-5 py-8 text-center text-sm text-slate-500">
                      Nenhum anúncio disponível ainda. Assim que você publicar, o ranking comercial
                      aparece aqui.
                    </div>
                  ) : (
                    data.ranking.map((item, index) => (
                      <div
                        key={item.anuncioId}
                        className="flex flex-col gap-4 rounded-3xl border border-slate-200 p-4 md:flex-row md:items-center"
                      >
                        <div className="flex items-start gap-4 md:w-[52%]">
                          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-slate-900 text-sm font-bold text-white">
                            #{index + 1}
                          </div>

                          <div className="relative h-20 w-16 shrink-0 overflow-hidden rounded-2xl bg-slate-100">
                            {item.fotoCapa ? (
                              <img
                                src={item.fotoCapa}
                                alt={item.anuncioTitulo}
                                className="h-full w-full object-cover"
                                loading="lazy"
                              />
                            ) : (
                              <div className="flex h-full items-center justify-center text-[10px] font-semibold uppercase tracking-[0.2em] text-slate-400">
                                sem foto
                              </div>
                            )}
                          </div>

                          <div className="min-w-0">
                            <h4 className="line-clamp-2 text-lg font-bold text-slate-900">
                              {item.anuncioTitulo}
                            </h4>
                            {item.localizacao ? (
                              <p className="mt-1 text-sm text-slate-500">{item.localizacao}</p>
                            ) : null}
                            <div className="mt-3 flex flex-wrap gap-2">
                              <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-700">
                                Score {item.score}
                              </span>
                              <span className="rounded-full border border-pink-200 bg-pink-50 px-3 py-1 text-xs font-semibold text-[#C41E73]">
                                {item.faixa}
                              </span>
                              {item.impulsionado ? (
                                <span className="rounded-full border border-blue-200 bg-blue-50 px-3 py-1 text-xs font-semibold text-blue-700">
                                  Destaque ativo
                                </span>
                              ) : null}
                            </div>
                          </div>
                        </div>

                        <div className="flex-1 space-y-3">
                          <div className="flex flex-wrap gap-2">
                            {item.recursosAtivos.length === 0 ? (
                              <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-600">
                                Sem recursos premium ativos
                              </span>
                            ) : (
                              item.recursosAtivos.map((recurso) => (
                                <span
                                  key={`${item.anuncioId}-${recurso}`}
                                  className="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-semibold text-slate-700"
                                >
                                  {recurso}
                                </span>
                              ))
                            )}
                          </div>

                          <div className="rounded-2xl bg-slate-50 px-4 py-3 text-sm text-slate-600">
                            Próximo passo recomendado:{' '}
                            <span className="font-semibold text-slate-900">{item.proximoPasso}</span>
                          </div>
                        </div>

                        <div className="md:w-[210px]">
                          <Link
                            href={item.proximoPassoDestino}
                            className="inline-flex w-full items-center justify-center gap-2 rounded-2xl bg-slate-900 px-4 py-3 text-sm font-semibold text-white transition hover:bg-slate-800"
                          >
                            Abrir anúncio
                            <ArrowUpRightIcon className="h-4 w-4" />
                          </Link>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </section>

              <section
                id="recomendacoes"
                className="rounded-[32px] border border-slate-200 bg-white p-6 shadow-sm md:p-7"
              >
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">
                    Recomendações automáticas
                  </p>
                  <h3 className="mt-2 text-2xl font-extrabold tracking-tight text-slate-900">
                    Oportunidades comerciais mais urgentes
                  </h3>
                  <p className="mt-2 text-sm leading-6 text-slate-600">
                    Priorizadas para aumentar sua visibilidade sem mexer nas regras centrais do
                    sistema.
                  </p>
                </div>

                <div className="mt-6 space-y-4">
                  {data.recomendacoes.length === 0 ? (
                    <div className="rounded-3xl border border-dashed border-slate-300 bg-slate-50 px-5 py-8 text-center text-sm text-slate-500">
                      Sem recomendações urgentes agora. Seu painel já está bem equilibrado.
                    </div>
                  ) : (
                    data.recomendacoes.map((item) => (
                      <RecommendationCard key={item.chave} item={item} />
                    ))
                  )}
                </div>
              </section>
            </div>

            <section className="rounded-[32px] border border-slate-200 bg-[linear-gradient(135deg,#111827_0%,#0f172a_55%,#1f2937_100%)] p-6 text-white shadow-sm md:p-8">
              <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
                <div className="max-w-3xl">
                  <span className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em] text-pink-200">
                    <BoltIcon className="h-4 w-4" />
                    CTA principal de monetização
                  </span>
                  <h3 className="mt-4 text-2xl font-extrabold tracking-tight md:text-3xl">
                    Aumente sua visibilidade e apareça para mais clientes
                  </h3>
                  <p className="mt-3 text-sm leading-7 text-slate-300 md:text-base">
                    Use créditos para ativar destaque, carrossel, vídeo, WhatsApp card e outros
                    recursos pagos que já existem na plataforma.
                  </p>
                </div>

                <div className="grid gap-3 sm:grid-cols-2">
                  <Link
                    href={data.primaryCtaTarget}
                    className="inline-flex items-center justify-center gap-2 rounded-2xl bg-[#FC1EAD] px-5 py-3 text-sm font-semibold text-white transition hover:bg-[#df1698]"
                  >
                    <RocketLaunchIcon className="h-4 w-4" />
                    {data.primaryCtaLabel}
                  </Link>
                  <Link
                    href="/creditos"
                    className="inline-flex items-center justify-center gap-2 rounded-2xl border border-white/15 bg-white/5 px-5 py-3 text-sm font-semibold text-white transition hover:bg-white/10"
                  >
                    <PlusIcon className="h-4 w-4" />
                    Comprar créditos
                  </Link>
                </div>
              </div>
            </section>
          </>
        )}
      </div>

      {!loading && data ? (
        <div className="fixed inset-x-4 bottom-4 z-40 md:hidden">
          <Link
            href={data.primaryCtaTarget}
            className="inline-flex w-full items-center justify-center gap-2 rounded-2xl bg-[#FC1EAD] px-5 py-4 text-sm font-semibold text-white shadow-[0_18px_40px_rgba(252,30,173,0.35)] transition hover:bg-[#df1698]"
          >
            <RocketLaunchIcon className="h-4 w-4" />
            {data.primaryCtaLabel}
          </Link>
        </div>
      ) : null}
    </PainelShell>
  )
}
