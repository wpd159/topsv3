import type { Metadata } from 'next'
import { DiagnosticoNextRegisterFormSemConteudoRemotoClient } from './diagnostico-next-register-form-sem-conteudo-remoto-client'

export const metadata: Metadata = {
  title: 'Diagnóstico Next — RegisterForm sem conteúdo remoto',
  robots: {
    index: false,
    follow: false,
    nocache: true,
    googleBot: { index: false, follow: false, noarchive: true },
  },
}

export default function DiagnosticoNextRegisterFormSemConteudoRemotoPage() {
  return <DiagnosticoNextRegisterFormSemConteudoRemotoClient />
}
