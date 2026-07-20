'use client'

import { useState } from 'react'
import { Input } from '@/components/ui/input'
import { Select, SelectTrigger, SelectValue, SelectContent, SelectItem } from '@/components/ui/select'
import { Button } from '@/components/ui/button'
import { FunnelIcon } from '@heroicons/react/24/outline'
import { ContractState, PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export default function FinanceiroFiltro() {
  const [filtros, setFiltros] = useState({ tipo: '', status: '', periodo: '' })
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(
    PENDING_BACKEND_CONTRACTS.financialAnalytics
  )

  return (
    <div className="space-y-4 rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
      <ContractState error={error} compact />
      <PendingActionFeedback attemptedAction={attemptedAction} />
      <div className="flex flex-wrap items-end justify-between gap-4">
      <div className="flex flex-wrap gap-4">

        <div>
          <p className="text-sm text-gray-600 mb-1">Status</p>
          <Select onValueChange={(v) => setFiltros((f) => ({ ...f, status: v }))}>
            <SelectTrigger className="w-[160px]">
              <SelectValue placeholder="Todos" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="pendente">Pendente</SelectItem>
              <SelectItem value="pago">Pago</SelectItem>
              <SelectItem value="cancelado">Cancelado</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div>
          <p className="text-sm text-gray-600 mb-1">Período</p>
          <Input type="month" className="w-[180px]" value={filtros.periodo} onChange={(event) => setFiltros((current) => ({ ...current, periodo: event.target.value }))} />
        </div>
      </div>

      <Button type="button" className="flex items-center gap-1 font-semibold" onClick={() => runPendingAction('Filtrar movimentações financeiras')}>
        <FunnelIcon className="w-4 h-4" />
        Filtrar
      </Button>
      </div>
    </div>
  )
}
