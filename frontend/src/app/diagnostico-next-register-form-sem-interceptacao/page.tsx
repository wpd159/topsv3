import type { Metadata } from 'next'
import { DiagnosticoNextRegisterFormSemInterceptacaoClient } from './diagnostico-next-register-form-sem-interceptacao-client'

export const metadata: Metadata = {
  title: 'Diagnóstico Next — RegisterForm real (sem interceptação)',
  robots: {
    index: false,
    follow: false,
    nocache: true,
    googleBot: { index: false, follow: false, noarchive: true },
  },
}

export default function DiagnosticoNextRegisterFormSemInterceptacaoPage() {
  return <DiagnosticoNextRegisterFormSemInterceptacaoClient />
}
