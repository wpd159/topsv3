'use client'

import { ContractState, PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export function AvisosAdministracao() {
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(
    PENDING_BACKEND_CONTRACTS.notices
  )

  return (
    <section className="rounded-xl border border-gray-200 bg-white p-5">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="text-base font-semibold text-gray-900">Avisos da administração</h2>
        <div className="flex gap-1">
          <Button type="button" size="icon" variant="ghost" aria-label="Aviso anterior" onClick={() => runPendingAction('Exibir aviso anterior')}>&larr;</Button>
          <Button type="button" size="icon" variant="ghost" aria-label="Próximo aviso" onClick={() => runPendingAction('Exibir próximo aviso')}>&rarr;</Button>
        </div>
      </div>
      <ContractState error={error} compact />
      <div className="mt-3 flex items-center justify-center gap-2" aria-label="Navegação dos avisos">
        <Button type="button" size="icon" variant="ghost" aria-label="Ir para aviso 1" onClick={() => runPendingAction('Exibir aviso selecionado')}>
          <span className="h-2 w-2 rounded-full bg-gray-400" />
        </Button>
      </div>
      <PendingActionFeedback attemptedAction={attemptedAction} />
    </section>
  )
}
