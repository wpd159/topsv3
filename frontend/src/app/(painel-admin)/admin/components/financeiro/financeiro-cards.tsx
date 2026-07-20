'use client'

import { ContractState, pendingContractError } from '@/components/feedback/contract-state'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

const INDICADORES = ['Saldo Efi', 'Entradas recentes', 'Faturamento total', 'Integracao Efi']

export default function FinanceiroCards() {
  return (
    <div className="space-y-3">
      <ContractState error={pendingContractError(PENDING_BACKEND_CONTRACTS.financialAnalytics)} />
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 xl:grid-cols-4">
        {INDICADORES.map((label) => (
          <div key={label} className="rounded-xl border border-gray-200 bg-white p-5">
            <p className="text-sm text-gray-500">{label}</p>
            <p className="mt-2 font-semibold text-gray-800">Contrato pendente</p>
          </div>
        ))}
      </div>
    </div>
  )
}
