'use client'

import { RegisterForm } from '@/components/auth/register-form'

// Teste 4D (bisect): formulario completo do RegisterForm real, sem o logo
// (next/image), via diagnosticView="full-without-logo".
export function DiagnosticoRegisterSemLogoClient() {
  return (
    <div className="mx-auto max-w-lg p-4">
      <h1 className="mb-2 text-xl font-bold">Diagnóstico — RegisterForm: sem logo</h1>
      <div className="mb-4 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-900">
        Formulário completo, sem o logo (next/image). Não envie.
      </div>
      <RegisterForm
        submitSource="DIAGNOSTICO"
        diagnosticSkipLegalContentLoad
        diagnosticView="full-without-logo"
      />
    </div>
  )
}
