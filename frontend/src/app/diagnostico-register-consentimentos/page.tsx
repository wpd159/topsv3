import type { Metadata } from 'next'
import { DiagnosticoRegisterConsentimentosClient } from './diagnostico-register-consentimentos-client'

export const metadata: Metadata = {
  title: 'Diagnóstico — RegisterForm: consentimentos',
  robots: {
    index: false,
    follow: false,
    nocache: true,
    googleBot: { index: false, follow: false, noarchive: true },
  },
}

export default function DiagnosticoRegisterConsentimentosPage() {
  return <DiagnosticoRegisterConsentimentosClient />
}
