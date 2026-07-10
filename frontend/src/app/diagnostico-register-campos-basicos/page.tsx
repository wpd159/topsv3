import type { Metadata } from 'next'
import { DiagnosticoRegisterCamposBasicosClient } from './diagnostico-register-campos-basicos-client'

export const metadata: Metadata = {
  title: 'Diagnóstico — RegisterForm: campos básicos',
  robots: {
    index: false,
    follow: false,
    nocache: true,
    googleBot: { index: false, follow: false, noarchive: true },
  },
}

export default function DiagnosticoRegisterCamposBasicosPage() {
  return <DiagnosticoRegisterCamposBasicosClient />
}
