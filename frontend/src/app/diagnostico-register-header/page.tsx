import type { Metadata } from 'next'
import { DiagnosticoRegisterHeaderClient } from './diagnostico-register-header-client'

export const metadata: Metadata = {
  title: 'Diagnóstico — RegisterForm: header',
  robots: {
    index: false,
    follow: false,
    nocache: true,
    googleBot: { index: false, follow: false, noarchive: true },
  },
}

export default function DiagnosticoRegisterHeaderPage() {
  return <DiagnosticoRegisterHeaderClient />
}
