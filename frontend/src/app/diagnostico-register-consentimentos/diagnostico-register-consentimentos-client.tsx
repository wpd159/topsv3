'use client'

import { RegisterForm } from '@/components/auth/register-form'

// Teste 4D (bisect): renderiza somente os checkboxes de termos, privacidade,
// consentimento promocional e o botao do RegisterForm real, via
// diagnosticView="consents".
export function DiagnosticoRegisterConsentimentosClient() {
  return (
    <div className="mx-auto max-w-lg p-4">
      <h1 className="mb-2 text-xl font-bold">Diagnóstico — RegisterForm: consentimentos</h1>
      <div className="mb-4 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-900">
        Bloco isolado: checkboxes de termos/privacidade/promocional e botão. Não envie.
      </div>
      <RegisterForm
        submitSource="DIAGNOSTICO"
        diagnosticSkipLegalContentLoad
        diagnosticView="consents"
      />
    </div>
  )
}
