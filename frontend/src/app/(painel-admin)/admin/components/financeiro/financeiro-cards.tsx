'use client'

import { AlertTriangle, Banknote, CircleDollarSign, Clock3, CreditCard, ReceiptText } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import type { AdminRelatorioReceitaResumo } from '@/lib/admin-pagamentos-api'

type Props = {
  resumo: AdminRelatorioReceitaResumo | null
  loading: boolean
  error: unknown
  onRetry: () => void
}

const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export default function FinanceiroCards({ resumo, loading, error, onRetry }: Props) {
  const indicadores = [
    { label: 'Receita confirmada', value: resumo ? moeda.format(resumo.receitaConfirmada) : '—', icon: CircleDollarSign },
    { label: 'Pagamentos confirmados', value: resumo?.pagamentosConfirmados.toLocaleString('pt-BR') ?? '—', icon: ReceiptText },
    { label: 'Ticket médio', value: resumo ? moeda.format(resumo.ticketMedio) : '—', icon: Banknote },
    { label: 'Créditos vendidos', value: resumo?.creditosVendidos.toLocaleString('pt-BR') ?? '—', icon: CreditCard },
  ]

  return (
    <div className="space-y-4">
      {error ? <ContractState error={error} onRetry={onRetry} /> : null}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {indicadores.map(({ label, value, icon: Icon }) => (
          <div key={label} className="border-l-2 border-pink-500 bg-white px-4 py-3">
            <p className="flex items-center gap-2 text-xs font-semibold text-zinc-500">
              <Icon className="h-4 w-4" />
              {label}
            </p>
            <p className="mt-2 text-xl font-bold text-zinc-950">{loading ? '—' : value}</p>
          </div>
        ))}
      </div>
      {resumo ? (
        <div className="grid gap-2 text-sm sm:grid-cols-2 xl:grid-cols-4">
          <Status label="Pendentes" value={resumo.pagamentosPendentes} icon={Clock3} />
          <Status label="Falhos" value={resumo.pagamentosFalhos} icon={AlertTriangle} />
          <Status label="Cancelados ou expirados" value={resumo.pagamentosCancelados} icon={AlertTriangle} />
          <Status label="Estornados" value={resumo.pagamentosEstornados} icon={AlertTriangle} />
        </div>
      ) : null}
      {resumo?.alertasConciliacao.length ? (
        <div className="border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950" role="alert">
          <p className="font-semibold">Atenção à reconciliação</p>
          <ul className="mt-2 space-y-1">
            {resumo.alertasConciliacao.map((alerta) => (
              <li key={alerta.codigo}>{alerta.quantidade.toLocaleString('pt-BR')} · {alerta.mensagem}</li>
            ))}
          </ul>
          <p className="mt-2 text-xs">Os dados não foram corrigidos automaticamente.</p>
        </div>
      ) : null}
    </div>
  )
}

function Status({ label, value, icon: Icon }: { label: string; value: number; icon: typeof Clock3 }) {
  return (
    <div className="flex items-center justify-between border border-zinc-200 bg-white px-3 py-2">
      <span className="flex items-center gap-2 text-zinc-600"><Icon className="h-4 w-4" />{label}</span>
      <strong className="text-zinc-950">{value.toLocaleString('pt-BR')}</strong>
    </div>
  )
}
