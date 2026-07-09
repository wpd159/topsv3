'use client'

import { useEffect, useState } from 'react'
import { DashboardStatCard } from '../dashboard/dashboard-stats-cards'
import {
  BanknotesIcon,
  ArrowTrendingUpIcon,
  ChartBarIcon,
  ShieldCheckIcon,
} from '@heroicons/react/24/solid'

type IntegracaoFinanceira = {
  disponivel: boolean
  provider: string
  ambiente: string
  statusIntegracao: string
  saldoDisponivel?: number | null
  consultadoEm?: string | null
  mensagem?: string | null
}

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

export default function FinanceiroCards() {
  const [pagamentos, setPagamentos] = useState<any[]>([])
  const [integracao, setIntegracao] = useState<IntegracaoFinanceira | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    const fetchDados = async () => {
      try {
        setErro(null)
        const [pagamentosResp, integracaoResp] = await Promise.all([
          fetch(`${process.env.NEXT_PUBLIC_API_URL}/financeiro/pagamentos`, {
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
          }),
          fetch(`${process.env.NEXT_PUBLIC_API_URL}/financeiro/integracao`, {
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
          }),
        ])

        if (pagamentosResp.ok) {
          setPagamentos(await pagamentosResp.json())
        } else {
          const message = await readApiError(pagamentosResp, 'Falha ao carregar pagamentos')
          console.error('[admin/financeiro] GET /financeiro/pagamentos', {
            status: pagamentosResp.status,
            message,
          })
          setErro(message)
        }

        if (integracaoResp.ok) {
          setIntegracao(await integracaoResp.json())
        } else {
          const message = await readApiError(integracaoResp, 'Falha ao carregar integração financeira')
          console.error('[admin/financeiro] GET /financeiro/integracao', {
            status: integracaoResp.status,
            message,
          })
          setErro((prev) => prev || message)
        }
      } catch (error) {
        console.error('[admin/financeiro] erro ao buscar cards financeiros', error)
        setErro('Falha ao conectar ao servidor financeiro.')
      } finally {
        setCarregando(false)
      }
    }

    fetchDados()
  }, [])

  const pagamentosAprovados = pagamentos.filter(
    (p) => p.status?.toLowerCase() === 'approved'
  )

  const totalFaturado = pagamentosAprovados.reduce((acc, p) => acc + (p.valor || 0), 0)

  const entradasRecentes = pagamentosAprovados
    .filter((p) => {
      const dataPag = new Date(p.dataPagamento)
      const agora = new Date()
      const diffDias = (agora.getTime() - dataPag.getTime()) / (1000 * 60 * 60 * 24)
      return diffDias <= 30
    })
    .reduce((acc, p) => acc + (p.valor || 0), 0)

  const formatCurrency = (v: number) =>
    v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })

  const cards = [
    {
      label: 'Saldo Efí',
      valor: carregando
        ? '...'
        : integracao?.disponivel && typeof integracao?.saldoDisponivel === 'number'
          ? formatCurrency(integracao.saldoDisponivel)
          : 'Indisponível',
      icon: <BanknotesIcon className="w-6 h-6 text-[#C41E73]" />,
    },
    {
      label: 'Entradas Recentes (30d)',
      valor: carregando ? '...' : formatCurrency(entradasRecentes),
      icon: <ArrowTrendingUpIcon className="w-6 h-6 text-[#C41E73]" />,
    },
    {
      label: 'Faturamento Total',
      valor: carregando ? '...' : formatCurrency(totalFaturado),
      icon: <ChartBarIcon className="w-6 h-6 text-[#C41E73]" />,
    },
    {
      label: 'Integração Efí',
      valor: carregando ? '...' : `${integracao?.ambiente || '---'} · ${integracao?.statusIntegracao || '---'}`,
      icon: <ShieldCheckIcon className="w-6 h-6 text-[#C41E73]" />,
    },
  ]

  return (
    <div className="space-y-3 mb-8">
      {erro ? (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {erro}
        </div>
      ) : null}
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => (
          <DashboardStatCard
            key={card.label}
            label={card.label}
            value={card.valor}
            icon={card.icon}
          />
        ))}
      </div>
    </div>
  )
}
