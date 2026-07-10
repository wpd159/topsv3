'use client'

import { RegisterForm } from '@/components/auth/register-form'

// Teste 4D (bisect): formulario completo do RegisterForm real, sem o bloco
// de checklist de regras de senha, via diagnosticView="full-without-password-checklist".
export function DiagnosticoRegisterSemChecklistClient() {
  return (
    <div className="mx-auto max-w-lg p-4">
      <h1 className="mb-2 text-xl font-bold">Diagnóstico — RegisterForm: sem checklist</h1>
      <div className="mb-4 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-900">
        Formulário completo, sem o checklist de regras de senha. Não envie.
      </div>
      <RegisterForm
        submitSource="DIAGNOSTICO"
        diagnosticSkipLegalContentLoad
        diagnosticView="full-without-password-checklist"
      />
    </div>
  )
}
