import type { Metadata } from 'next'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Diagnóstico Next — launcher',
  robots: {
    index: false,
    follow: false,
    nocache: true,
    googleBot: { index: false, follow: false, noarchive: true },
  },
}

const testes = [
  {
    href: '/diagnostico-next-basico',
    label: '1. Básico — Next + root layout + providers + CSS global',
  },
  {
    href: '/diagnostico-next-estilos',
    label: '2. Estilos — CSS real do cadastro (sem estado/API)',
  },
  {
    href: '/diagnostico-next-controlado',
    label: '3. Controlado — React (useState) + máscaras/re-render',
  },
  {
    href: '/diagnostico-next-register-form',
    label: '4. RegisterForm real — isolado do modal, envio bloqueado',
  },
]

// Launcher da matriz de diagnostico: apenas links Next <Link> para testar
// navegacao client-side entre as quatro paginas. Nao e linkado em nenhum
// lugar do site publico.
export default function DiagnosticoNextLauncherPage() {
  return (
    <div className="mx-auto max-w-lg space-y-4 p-4">
      <h1 className="text-xl font-bold">Diagnóstico Next</h1>
      <p className="text-sm text-gray-600">
        Matriz de diagnóstico do ghosting em Chrome Android antigo. Cada link isola uma camada
        diferente do frontend.
      </p>
      <ul className="space-y-2">
        {testes.map((teste) => (
          <li key={teste.href}>
            <Link href={teste.href} className="text-[#FC1EAD] underline">
              {teste.label}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}
