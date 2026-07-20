'use client'

import { useState } from 'react'

import { ContractState, PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export default function FinanceiroGrafico() {
  const [period, setPeriod] = useState('30 dias')
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(
    PENDING_BACKEND_CONTRACTS.financialAnalytics
  )

  return (
    <section className="space-y-4 rounded-xl border border-gray-200 bg-white p-5">
      <div className="flex items-center justify-between gap-4">
        <h3 className="font-semibold text-gray-900">Evolucao financeira</h3>
        <div className="flex gap-2" aria-label="Periodo do grafico">
          {['7 dias', '30 dias', 'Mes atual'].map((periodo) => (
            <Button key={periodo} variant={period === periodo ? 'default' : 'outline'} size="sm" onClick={() => { setPeriod(periodo); runPendingAction(`Consultar período: ${periodo}`) }}>{periodo}</Button>
          ))}
        </div>
      </div>
      <ContractState error={error} compact />
      <PendingActionFeedback attemptedAction={attemptedAction} />
      <div className="grid gap-3 sm:grid-cols-3">
        {['Receita no período', 'Transações aprovadas', 'Créditos entregues'].map((label) => (
          <div key={label} className="rounded-md border border-gray-200 p-4">
            <p className="text-sm text-gray-500">{label}</p>
            <p className="mt-2 font-semibold text-gray-800">Contagem indisponível</p>
          </div>
        ))}
      </div>
      <div className="relative min-h-72 overflow-hidden rounded-md border border-gray-200 bg-gray-50" aria-label="Gráfico de receita e vendas">
        <div className="absolute inset-0 grid grid-rows-5">
          {Array.from({ length: 5 }, (_, index) => <div key={index} className="border-b border-gray-200 last:border-b-0" />)}
        </div>
        <div className="relative flex min-h-72 items-center justify-center p-6 text-center text-sm text-gray-600">
          Dados do gráfico indisponíveis enquanto o contrato financeiro permanece pendente.
        </div>
      </div>
    </section>
  )
}
