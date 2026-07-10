'use client'

import { useState } from 'react'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import { formatPhone } from '@/utils/formatter'
import {
  UserIcon,
  EnvelopeIcon,
  LockClosedIcon,
  PhoneIcon,
  CalendarDaysIcon,
} from '@heroicons/react/24/outline'

// Mesma classe visual do cadastro, copiada para isolar o teste (nao alterada
// no arquivo original).
const registerInputClass =
  'transition-none focus-visible:ring-0 focus-visible:ring-offset-0 focus-visible:shadow-none sm:transition-[color,box-shadow] sm:focus-visible:ring-[3px] sm:focus-visible:ring-ring/50'

// Teste 3 da matriz de diagnostico: mesmo visual do teste 2, agora com
// inputs controlados por useState e a mascara atual de telefone
// (formatPhone). Sem checagem de duplicidade, fetch, submit real ou
// componentes do cadastro.
export function DiagnosticoNextControladoClient() {
  const [username, setUsername] = useState('')
  const [dataNascimento, setDataNascimento] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  return (
    <div className="mx-auto max-w-lg space-y-4 p-4">
      <h1 className="text-xl font-bold">Diagnóstico Next — controlado</h1>
      <p className="text-sm text-gray-600">
        Teste 3: React controlado (useState) + máscara atual de telefone/data, com o mesmo
        visual do cadastro. Sem checagem de duplicidade, fetch, submit real ou componentes do
        cadastro.
      </p>

      <div className="relative">
        <UserIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
        <Input
          type="text"
          placeholder="Nome de usuario"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          className={cn(registerInputClass, 'pl-10 py-5')}
        />
      </div>

      <div className="relative">
        <CalendarDaysIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
        <Input
          type="date"
          value={dataNascimento}
          onChange={(e) => setDataNascimento(e.target.value)}
          className={cn(registerInputClass, 'register-date-input h-9 min-h-9 pl-10 py-1 leading-5')}
        />
      </div>

      <div className="relative">
        <PhoneIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
        <Input
          type="tel"
          placeholder="Telefone"
          value={phone}
          onChange={(e) => setPhone(formatPhone(e.target.value))}
          maxLength={15}
          className={cn(registerInputClass, 'pl-10 py-5')}
        />
      </div>

      <div className="relative">
        <EnvelopeIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
        <Input
          type="email"
          placeholder="Seu e-mail"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className={cn(registerInputClass, 'pl-10 py-5')}
        />
      </div>

      <div className="relative">
        <LockClosedIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
        <Input
          type="password"
          placeholder="Senha"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className={cn(registerInputClass, 'pl-10 py-5')}
        />
      </div>
    </div>
  )
}
