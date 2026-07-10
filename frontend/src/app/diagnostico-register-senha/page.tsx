import type { Metadata } from 'next'
import { DiagnosticoRegisterSenhaClient } from './diagnostico-register-senha-client'

export const metadata: Metadata = {
  title: 'Diagnóstico — RegisterForm: senha',
  robots: {
    index: false,
    follow: false,
    nocache: true,
    googleBot: { index: false, follow: false, noarchive: true },
  },
}

export default function DiagnosticoRegisterSenhaPage() {
  return <DiagnosticoRegisterSenhaClient />
}
