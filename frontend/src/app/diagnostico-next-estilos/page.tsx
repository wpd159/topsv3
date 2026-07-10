import type { Metadata } from 'next'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import {
  UserIcon,
  EnvelopeIcon,
  LockClosedIcon,
  PhoneIcon,
  CalendarDaysIcon,
} from '@heroicons/react/24/outline'

export const metadata: Metadata = {
  title: 'Diagnóstico Next — estilos do cadastro',
  robots: {
    index: false,
    follow: false,
    nocache: true,
    googleBot: { index: false, follow: false, noarchive: true },
  },
}

// Mesma classe visual usada hoje pelos campos do RegisterForm (nao alterada
// aqui, apenas copiada para isolar o teste).
const registerInputClass =
  'transition-none focus-visible:ring-0 focus-visible:ring-offset-0 focus-visible:shadow-none sm:transition-[color,box-shadow] sm:focus-visible:ring-[3px] sm:focus-visible:ring-ring/50'

// Teste 2 da matriz de diagnostico: mesmo formulario minimo, mas com as
// classes visuais reais do cadastro (Input compartilhado, radius, padding,
// foco, sombra, icones). Sem mascara, estado controlado, validacao, API ou
// import do RegisterForm.
export default function DiagnosticoNextEstilosPage() {
  return (
    <div className="mx-auto max-w-lg space-y-4 p-4">
      <h1 className="text-xl font-bold">Diagnóstico Next — estilos do cadastro</h1>
      <p className="text-sm text-gray-600">
        Teste 2: CSS real do cadastro (Input compartilhado + classes atuais), sem estado,
        máscara, validação ou API.
      </p>

      <div className="relative">
        <UserIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
        <Input type="text" placeholder="Nome de usuario" className={cn(registerInputClass, 'pl-10 py-5')} />
      </div>

      <div className="relative">
        <CalendarDaysIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
        <Input
          type="date"
          className={cn(registerInputClass, 'register-date-input h-9 min-h-9 pl-10 py-1 leading-5')}
        />
      </div>

      <div className="relative">
        <PhoneIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
        <Input type="tel" placeholder="Telefone" className={cn(registerInputClass, 'pl-10 py-5')} />
      </div>

      <div className="relative">
        <EnvelopeIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
        <Input type="email" placeholder="Seu e-mail" className={cn(registerInputClass, 'pl-10 py-5')} />
      </div>

      <div className="relative">
        <LockClosedIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
        <Input type="password" placeholder="Senha" className={cn(registerInputClass, 'pl-10 py-5')} />
      </div>

      <textarea
        rows={4}
        placeholder="Campo de texto longo"
        className={cn(registerInputClass, 'w-full rounded-3xl border border-gray-500/40 bg-gray-200 p-3 text-base outline-none')}
      />

      <button type="button" className="w-full rounded-3xl bg-[#FC1EAD] py-5 font-semibold text-white">
        Botão simples
      </button>
    </div>
  )
}
