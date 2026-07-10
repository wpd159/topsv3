import type { Metadata } from 'next'
import { DiagnosticoNextRegisterFormClient } from './diagnostico-next-register-form-client'

export const metadata: Metadata = {
  title: 'Diagnóstico Next — RegisterForm real',
  robots: {
    index: false,
    follow: false,
    nocache: true,
    googleBot: { index: false, follow: false, noarchive: true },
  },
}

export default function DiagnosticoNextRegisterFormPage() {
  return <DiagnosticoNextRegisterFormClient />
}
