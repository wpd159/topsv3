import type { Metadata } from 'next'
import { DiagnosticoNextControladoClient } from './diagnostico-next-controlado-client'

export const metadata: Metadata = {
  title: 'Diagnóstico Next — controlado',
  robots: {
    index: false,
    follow: false,
    nocache: true,
    googleBot: { index: false, follow: false, noarchive: true },
  },
}

export default function DiagnosticoNextControladoPage() {
  return <DiagnosticoNextControladoClient />
}
