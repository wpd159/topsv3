'use client'

import { RegisterForm } from '@/components/auth/register-form'

// Teste 4D (bisect): renderiza somente o bloco de header (logo + titulo) do
// RegisterForm real, via diagnosticView="header". JSX/classes identicos ao
// bloco correspondente do componente completo.
export function DiagnosticoRegisterHeaderClient() {
  return (
    <div className="mx-auto max-w-lg p-4">
      <h1 className="mb-2 text-xl font-bold">Diagnóstico — RegisterForm: header</h1>
      <div className="mb-4 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-900">
        Bloco isolado: somente logo e título iniciais. Não envie.
      </div>
      <RegisterForm
        submitSource="DIAGNOSTICO"
        diagnosticSkipLegalContentLoad
        diagnosticView="header"
      />
    </div>
  )
}
