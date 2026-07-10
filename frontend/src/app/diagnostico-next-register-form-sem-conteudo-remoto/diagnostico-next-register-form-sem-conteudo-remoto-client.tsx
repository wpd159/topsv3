'use client'

import { RegisterForm } from '@/components/auth/register-form'

// Teste 4C da matriz de diagnostico: RegisterForm real, sem interceptar
// window.fetch, mas com o useEffect de carga dos 3 documentos juridicos
// (fetchPublicSiteContent + setDocumentosJuridicos) desativado via
// diagnosticSkipLegalContentLoad. Isola se o mount desse efeito e o
// re-render que ele causa e o gatilho do ghosting.
export function DiagnosticoNextRegisterFormSemConteudoRemotoClient() {
  return (
    <div className="mx-auto max-w-lg p-4">
      <h1 className="mb-2 text-xl font-bold">
        Diagnóstico Next — RegisterForm sem conteúdo remoto
      </h1>

      <div className="mb-4 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-900">
        Teste do RegisterForm sem carregamento remoto de termos no mount. Não envie.
      </div>

      <RegisterForm submitSource="DIAGNOSTICO" diagnosticSkipLegalContentLoad />
    </div>
  )
}
