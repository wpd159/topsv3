'use client'

import { RegisterForm } from '@/components/auth/register-form'

// Teste 4D (bisect): renderiza somente senha, confirmar senha e
// checklist/regras de senha do RegisterForm real, via diagnosticView="password".
export function DiagnosticoRegisterSenhaClient() {
  return (
    <div className="mx-auto max-w-lg p-4">
      <h1 className="mb-2 text-xl font-bold">Diagnóstico — RegisterForm: senha</h1>
      <div className="mb-4 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-900">
        Bloco isolado: senha, confirmar senha e checklist de regras. Não envie.
      </div>
      <RegisterForm
        submitSource="DIAGNOSTICO"
        diagnosticSkipLegalContentLoad
        diagnosticView="password"
      />
    </div>
  )
}
