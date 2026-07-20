'use client'

import { ContractState, pendingContractError } from '@/components/feedback/contract-state'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

type CompliancePolicyPageProps = {
  title: string
  description: string
  field: 'adultPrivacyPolicy' | 'restrictedContentTerms' | 'legalAccessNotice'
}

const FALLBACK_POLICIES = {
  adultPrivacyPolicy:
    'O acesso a conteudo adulto restrito gera registros minimos de seguranca e auditoria, com IP mascarado, hash de user agent e identificador pseudonimo de sessao.',
  restrictedContentTerms:
    'Fotos restritas, videos e stories exigem confirmacao valida de idade. O uso indevido pode gerar bloqueio e revogacao de acesso.',
  legalAccessNotice:
    'Este ambiente contem material sensivel destinado exclusivamente a maiores de 18 anos devidamente verificados.',
} as const

export function CompliancePolicyPage({ title, description, field }: CompliancePolicyPageProps) {
  return (
    <section className="mx-auto max-w-[980px] px-4 py-10">
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-gray-900">{title}</h1>
        <p className="mt-2 text-sm text-gray-500">{description}</p>
      </div>

      <div className="mb-4">
        <ContractState
          error={pendingContractError(PENDING_BACKEND_CONTRACTS.complianceContent)}
          compact
        />
      </div>

      <div className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
        <div className="prose prose-sm max-w-none whitespace-pre-line text-gray-700">
          {FALLBACK_POLICIES[field]}
        </div>
      </div>
    </section>
  )
}

export default CompliancePolicyPage
