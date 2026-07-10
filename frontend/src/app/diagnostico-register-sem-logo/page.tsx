import type { Metadata } from 'next'
import { DiagnosticoRegisterSemLogoClient } from './diagnostico-register-sem-logo-client'

export const metadata: Metadata = {
  title: 'Diagnóstico — RegisterForm: sem logo',
  robots: {
    index: false,
    follow: false,
    nocache: true,
    googleBot: { index: false, follow: false, noarchive: true },
  },
}

export default function DiagnosticoRegisterSemLogoPage() {
  return <DiagnosticoRegisterSemLogoClient />
}
