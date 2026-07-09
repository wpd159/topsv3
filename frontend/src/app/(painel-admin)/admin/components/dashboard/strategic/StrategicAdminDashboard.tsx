'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'
import type { PremiumBenefitDashboard } from '@/lib/admin-premium-benefits-api'
import {
  fetchAdminPerformanceAnuncios,
  fetchDesempenhoDiario,
  type AdminPerformanceResponse,
  type DesempenhoDiarioResponse,
} from '@/lib/admin-estatisticas-api'
import { TopWhatsappHojeCard } from './TopWhatsappHojeCard'
import { StrategicPerformanceChart } from './StrategicPerformanceChart'
import { PriorityAlertsCard, type PriorityAlertItem } from './PriorityAlertsCard'
import { CommercialOpportunitiesCard, type CommercialOpportunityItem } from './CommercialOpportunitiesCard'
import { StrategicConversionRankings } from './StrategicConversionRankings'
import { StrategicAnalysisTables } from './StrategicAnalysisTables'
import { MonetizationOpportunitiesStrip, type MonetizationCard } from './MonetizationOpportunitiesStrip'
import UltimosUsuariosTable from '../ultimos-usuarios-table'
import { MOD_V2_QUERY_CIDADE } from '@/features/moderation-v2/lib/url-dashboard-filters'
import {
  aggregateByCity,
  aggregateByClassification,
  cidadeAbaixoDaMediaResumo,
  countAltoTrafegoZeroClique,
  matchesStrategicAltoTrafego,
  matchesStrategicBaixaEficiencia,
  pctAnunciosSemClique,
  worstConversionWithTraffic,
} from './strategic-dashboard-utils'

const API_URL = process.env.NEXT_PUBLIC_API_URL

type Period = 7 | 15 | 30

async function readApiError(response: Response, fallback: string) {
  const raw = await response.text().catch(() => '')
  if (!raw.trim()) return `${fallback} (HTTP ${response.status})`
  try {
    const parsed = JSON.parse(raw) as { error?: string; message?: string }
    return String(parsed?.error || parsed?.message || '').trim() || `${fallback} (HTTP ${response.status})`
  } catch {
    return raw.trim() || `${fallback} (HTTP ${response.status})`
  }
}

export function StrategicAdminDashboard() {
  const [period, setPeriod] = useState<Period>(30)
  const [daily, setDaily] = useState<DesempenhoDiarioResponse | null>(null)
  const [dailyLoading, setDailyLoading] = useState(true)
  const [perf, setPerf] = useState<AdminPerformanceResponse | null>(null)
  const [perfLoading, setPerfLoading] = useState(true)
  const [premium, setPremium] = useState<PremiumBenefitDashboard | null>(null)
  const [premiumLoading, setPremiumLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setDailyLoading(true)
      try {
        const d = await fetchDesempenhoDiario(period)
        if (!cancelled) setDaily(d)
      } finally {
        if (!cancelled) setDailyLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [period])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        setLoadError(null)
        setPerfLoading(true)
        setPremiumLoading(true)
        const [perfRes, premiumRes] = await Promise.all([
          fetchAdminPerformanceAnuncios(),
          fetch(`${API_URL}/admin/premium-benefits/dashboard`, {
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
          }),
        ])

        if (!cancelled) {
          setPerf(perfRes)
          if (!perfRes) {
            console.error('[StrategicAdminDashboard] performance nulo')
          }
        }

        if (premiumRes.ok) {
          const pj = await premiumRes.json()
          if (!cancelled) setPremium(pj)
        } else {
          if (!cancelled) {
            setPremium(null)
            const message = await readApiError(premiumRes, 'Falha ao carregar benefícios premium')
            console.error('[StrategicAdminDashboard] premium', message)
          }
        }

        const errs: string[] = []
        if (!perfRes) errs.push('performance de anúncios')
        if (!premiumRes.ok) errs.push('benefícios premium')
        if (errs.length > 0 && !cancelled) {
          setLoadError(`Alguns dados não carregaram: ${errs.join(', ')}. Verifique sessão e API.`)
        }
      } catch {
        if (!cancelled) {
          setPerf(null)
          setPremium(null)
          setLoadError('Falha ao carregar dados do dashboard.')
        }
      } finally {
        if (!cancelled) {
          setPerfLoading(false)
          setPremiumLoading(false)
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const base = perf?.rankingPorCliques ?? []

  const cityRows = useMemo(() => aggregateByCity(base), [base])
  const classRows = useMemo(() => aggregateByClassification(base), [base])

  const topConv = useMemo(() => (perf?.topPorConversao ?? []).slice(0, 5), [perf?.topPorConversao])
  const piorConv = useMemo(() => worstConversionWithTraffic(base, 100, 5), [base])

  const alerts = useMemo((): PriorityAlertItem[] => {
    const out: PriorityAlertItem[] = []
    if (base.length === 0) return out
    const pct = pctAnunciosSemClique(base)
    if (pct > 0) {
      out.push({
        id: 'sem-clique',
        title: `${pct.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}% dos anúncios com views não têm clique`,
        subtitle: 'Base com exposição mas sem conversão em WhatsApp.',
        href: '/admin/moderacao-v2?filtro=com-views-sem-clique',
      })
    }
    const nZero = countAltoTrafegoZeroClique(base, 200)
    if (nZero > 0) {
      out.push({
        id: 'alto-trafego-zero',
        title: `${nZero} anúncio${nZero !== 1 ? 's' : ''} com muitas views e zero clique`,
        subtitle: 'Candidatos a revisão de criativo, preço ou canal.',
        href: '/admin/moderacao-v2?filtro=alto-trafego-zero-clique',
      })
    }
    const below = cidadeAbaixoDaMediaResumo(cityRows, 3)
    if (below && below.nome && below.nome !== '—') {
      out.push({
        id: 'cidade-media',
        title: `Cidade em destaque: ${below.nome}`,
        subtitle: `Conversão agregada ${below.conversao.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}% vs média ${below.media.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%.`,
        href: `/admin/moderacao-v2?${MOD_V2_QUERY_CIDADE}=${encodeURIComponent(below.nome)}`,
      })
    }
    const inef =
      (premium?.beneficiosAtivos ?? 0) > 0 &&
      (premium?.anunciosSemUpsell ?? 0) > 20 &&
      (premium?.anunciosSemUpsell ?? 0) > (premium?.beneficiosAtivos ?? 0) * 3
    if (inef) {
      out.push({
        id: 'premio-dessinc',
        title: 'Possível dessincronia: muitos anúncios sem upsell',
        subtitle: 'Comparar benefícios ativos com tamanho da base comercial.',
        href: '/admin/moderacao-v2?filtro=sem-upsell',
      })
    }
    return out.slice(0, 5)
  }, [base, cityRows, premium])

  const opportunities = useMemo((): CommercialOpportunityItem[] => {
    const out: CommercialOpportunityItem[] = []
    const upsell = premium?.anunciosSemUpsell ?? 0
    if (upsell > 0) {
      out.push({
        id: 'sem-upsell',
        title: `${upsell.toLocaleString('pt-BR')} anúncios sem upsell`,
        subtitle: 'Priorize upgrades e pacotes premium.',
        href: '/admin/moderacao-v2?filtro=sem-upsell',
      })
    }
    const venc = premium?.beneficiosVencendoEmBreve ?? 0
    if (venc > 0) {
      out.push({
        id: 'vencendo',
        title: `${venc.toLocaleString('pt-BR')} benefícios vencendo em breve`,
        subtitle: 'Renovação ou nova oferta comercial.',
        href: '/admin/moderacao-v2?filtro=vencendo-em-breve',
      })
    }
    const altoTrafego = base.filter((i) => matchesStrategicAltoTrafego(i.visualizacoes)).length
    if (altoTrafego > 0) {
      out.push({
        id: 'alto-trafego',
        title: `${altoTrafego.toLocaleString('pt-BR')} anúncios com alto tráfego acumulado`,
        subtitle: 'Prioridade para monetização e retenção.',
        href: '/admin/moderacao-v2?filtro=alto-trafego',
      })
    }
    const aptosUpgrade = Math.min(upsell, base.filter((i) => (i.visualizacoes ?? 0) >= 500).length)
    if (aptosUpgrade > 0) {
      out.push({
        id: 'aptos',
        title: `Até ${aptosUpgrade.toLocaleString('pt-BR')} anúncios com tráfego médio+ sem upsell`,
        subtitle: 'Lista cruzada aproximada para prospecção.',
        href: '/admin/moderacao-v2?filtro=sem-upsell',
      })
    }
    return out.slice(0, 5)
  }, [premium, base])

  const monetizationCards = useMemo((): MonetizationCard[] => {
    const semUpsell = premium?.anunciosSemUpsell ?? 0
    const alto = base.filter((i) => matchesStrategicAltoTrafego(i.visualizacoes)).length
    const inefCount = base.filter((i) => matchesStrategicBaixaEficiencia(i.visualizacoes, i.cliquesWhatsapp)).length
    return [
      {
        id: 'm-upsell',
        title: 'Sem upsell',
        value: semUpsell.toLocaleString('pt-BR'),
        description: 'Anúncios elegíveis sem benefício premium.',
        href: '/admin/moderacao-v2?filtro=sem-upsell',
      },
      {
        id: 'm-trafego',
        title: 'Alto tráfego',
        value: alto.toLocaleString('pt-BR'),
        description: 'Anúncios com ≥2k views (acumulado).',
        href: '/admin/moderacao-v2?filtro=alto-trafego',
      },
      {
        id: 'm-inef',
        title: 'Possível ineficiência',
        value: inefCount.toLocaleString('pt-BR'),
        description: 'Com views e conversão muito baixa (menos de 1%).',
        href: '/admin/moderacao-v2?filtro=baixa-eficiencia',
      },
    ]
  }, [premium, base])

  const loadingTables = perfLoading

  return (
    <section className="space-y-8">
      <div className="flex flex-col gap-2 border-b border-gray-100 pb-6">
        <h1 className="text-2xl font-bold tracking-tight text-gray-900">Dashboard estratégico</h1>
        <p className="max-w-3xl text-sm text-gray-600">
          Leitura executiva: ritmo diário, alertas, oportunidades de receita e recortes da base. Operação ficou em
          atalhos ao final.
        </p>
      </div>

      {loadError ? (
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          {loadError}
        </div>
      ) : null}

      <StrategicPerformanceChart
        data={daily}
        loading={dailyLoading}
        period={period}
        onPeriodChange={setPeriod}
      />

      <TopWhatsappHojeCard />

      <div className="grid gap-6 lg:grid-cols-2">
        <PriorityAlertsCard items={alerts} loading={perfLoading && base.length === 0} />
        <CommercialOpportunitiesCard items={opportunities} loading={premiumLoading} />
      </div>

      <StrategicConversionRankings topConversao={topConv} piorConversao={piorConv} loading={loadingTables} />

      <StrategicAnalysisTables cidadeRows={cityRows} classificacaoRows={classRows} loading={loadingTables} />

      <MonetizationOpportunitiesStrip cards={monetizationCards} loading={premiumLoading && perfLoading} />

      <div className="border-t border-gray-200 pt-8">
        <p className="mb-3 text-[11px] font-semibold uppercase tracking-wide text-gray-500">Monitoramento em tempo real</p>
        <UltimosUsuariosTable variant="compact" />
      </div>

      <p className="text-center text-xs text-gray-400">
        Série diária e rankings usam os endpoints de estatísticas do painel.{' '}
        <Link href="/admin/financeiro" className="text-[#f0198f] hover:underline">
          Financeiro
        </Link>
      </p>
    </section>
  )
}
