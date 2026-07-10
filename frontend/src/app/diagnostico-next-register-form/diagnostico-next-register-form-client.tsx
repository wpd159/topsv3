'use client'

import { useEffect } from 'react'
import { RegisterForm } from '@/components/auth/register-form'

// AVISO (diagnostico 4B): este teste sobrescreve window.fetch globalmente,
// o que contamina o diagnostico — o RegisterForm dispara fetches/effects
// proprios e o Next tambem pode usar fetch internamente. Por isso ele NAO
// serve mais como prova isolada da camada RegisterForm. Use
// /diagnostico-next-register-form-sem-interceptacao para isso; este teste
// fica mantido separado apenas como referencia do que ja foi descartado.
//
// Teste 4 da matriz de diagnostico: RegisterForm real, sem modal, sem
// portal, em pagina simples. O RegisterForm em si NAO e alterado — o fetch
// global e interceptado apenas enquanto esta pagina de diagnostico estiver
// montada (restaurado no cleanup), retornando uma resposta nao-ok. O proprio
// tratamento de erro ja existente no componente (`if (!res.ok) toast.error`)
// exibe essa mensagem, sem editar o componente nem chamar API real.
export function DiagnosticoNextRegisterFormClient() {
  useEffect(() => {
    const originalFetch = window.fetch

    window.fetch = async () =>
      new Response(
        'Diagnostico: envio bloqueado nesta pagina de teste. Nenhuma chamada real foi feita.',
        { status: 503 }
      )

    return () => {
      window.fetch = originalFetch
    }
  }, [])

  return (
    <div className="mx-auto max-w-lg p-4">
      <h1 className="mb-2 text-xl font-bold">Diagnóstico Next — RegisterForm real</h1>

      <div className="mb-4 rounded-lg border border-red-300 bg-red-50 px-3 py-2 text-sm font-semibold text-red-900">
        Este teste sobrescreve window.fetch globalmente e não serve mais como prova isolada.
        Use /diagnostico-next-register-form-sem-interceptacao.
      </div>

      <p className="mb-4 text-sm text-gray-600">
        Teste 4: componente de cadastro real, isolado de modal/portal, em página simples.
        Envio bloqueado nesta página (nenhuma API real é chamada, nenhum dado é persistido).
      </p>
      <RegisterForm submitSource="DIAGNOSTICO" />
    </div>
  )
}
