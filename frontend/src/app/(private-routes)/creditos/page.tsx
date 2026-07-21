'use client'

import { ContractState } from '@/components/feedback/contract-state'
import { PainelShell } from '@/components/painel-anunciante/painel-shell'
import { ApiContractError } from '@/lib/api-contract'

const creditosPendingError = new ApiContractError(
  'Os recursos de créditos e planos ainda não estão disponíveis nesta área.',
  'INTEGRATION_MISSING',
  404
)

export default function CreditosPage() {
  return (
    <PainelShell
      title="Créditos e planos"
      description="Acompanhe aqui a disponibilidade dos recursos comerciais da sua conta."
    >
      <section aria-label="Disponibilidade de créditos e planos" className="pb-10">
        <ContractState error={creditosPendingError} />
      </section>
    </PainelShell>
  )
}
