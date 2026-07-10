'use client'

import { useEffect } from 'react'
import { RegisterForm } from '@/components/auth/register-form'

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
      <p className="mb-4 text-sm text-gray-600">
        Teste 4: componente de cadastro real, isolado de modal/portal, em página simples.
        Envio bloqueado nesta página (nenhuma API real é chamada, nenhum dado é persistido).
      </p>
      <RegisterForm submitSource="DIAGNOSTICO" />
    </div>
  )
}
