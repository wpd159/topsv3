import type { Metadata } from 'next'
import { DiagnosticoRegisterSemChecklistClient } from './diagnostico-register-sem-checklist-client'

export const metadata: Metadata = {
  title: 'Diagnóstico — RegisterForm: sem checklist',
  robots: {
    index: false,
    follow: false,
    nocache: true,
    googleBot: { index: false, follow: false, noarchive: true },
  },
}

export default function DiagnosticoRegisterSemChecklistPage() {
  return <DiagnosticoRegisterSemChecklistClient />
}
