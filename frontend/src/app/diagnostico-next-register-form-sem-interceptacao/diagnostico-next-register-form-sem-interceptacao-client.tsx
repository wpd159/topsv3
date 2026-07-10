'use client'

import { RegisterForm } from '@/components/auth/register-form'

// Teste 4B da matriz de diagnostico: RegisterForm real, exatamente como
// esta, SEM nenhum monkeypatch de window.fetch e SEM modal/portal/overlay.
// O teste 4 (diagnostico-next-register-form) sobrescrevia window.fetch
// globalmente, o que contamina o diagnostico: o RegisterForm dispara
// fetches/effects proprios e o Next tambem pode usar fetch internamente.
// Aqui o formulario roda com seus fetches reais (fetchPublicSiteContent,
// verificarDuplicidade) intactos; o usuario so deve tocar/digitar/apagar/
// rolar, sem clicar em "Criar conta".
export function DiagnosticoNextRegisterFormSemInterceptacaoClient() {
  return (
    <div className="mx-auto max-w-lg p-4">
      <h1 className="mb-2 text-xl font-bold">
        Diagnóstico Next — RegisterForm real (sem interceptação)
      </h1>

      <div className="mb-4 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-900">
        Não envie o formulário. Apenas toque, digite, apague e role a tela.
      </div>

      <p className="mb-4 text-sm text-gray-600">
        Teste 4B: componente de cadastro real, sem modal/portal/overlay e sem nenhum
        monkeypatch de <code>window.fetch</code>. O botão de envio continua presente, mas não
        deve ser usado durante este teste.
      </p>

      <RegisterForm submitSource="DIAGNOSTICO" />
    </div>
  )
}
