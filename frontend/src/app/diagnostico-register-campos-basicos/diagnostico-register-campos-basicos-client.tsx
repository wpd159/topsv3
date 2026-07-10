'use client'

import { RegisterForm } from '@/components/auth/register-form'

// Teste 4D (bisect): renderiza somente username, data, telefone e e-mail do
// RegisterForm real, via diagnosticView="basic-fields".
export function DiagnosticoRegisterCamposBasicosClient() {
  return (
    <div className="mx-auto max-w-lg p-4">
      <h1 className="mb-2 text-xl font-bold">Diagnóstico — RegisterForm: campos básicos</h1>
      <div className="mb-4 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-900">
        Bloco isolado: username, data de nascimento, telefone e e-mail. Não envie.
      </div>
      <RegisterForm
        submitSource="DIAGNOSTICO"
        diagnosticSkipLegalContentLoad
        diagnosticView="basic-fields"
      />
    </div>
  )
}
