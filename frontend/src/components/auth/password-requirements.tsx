'use client'

import { CheckCircleIcon, MinusCircleIcon } from '@heroicons/react/24/outline'
import { cn } from '@/lib/utils'
import { validatePassword } from '@/utils/formatter'

export const PASSWORD_REQUIREMENTS = [
  ['Pelo menos 8 caracteres', 'length'],
  ['Contém letra maiúscula', 'uppercase'],
  ['Contém letra minúscula', 'lowercase'],
  ['Contém número', 'number'],
  ['Contém pontuação ou símbolo', 'symbol'],
  ['Não contém sequências óbvias', 'noCommon'],
] as const

export function passwordMeetsPolicy(value: string) {
  return Object.values(validatePassword(value)).every(Boolean)
}

export function pendingPasswordRequirements(value: string) {
  const validation = validatePassword(value)
  return PASSWORD_REQUIREMENTS.filter(([, key]) => !validation[key]).map(([label]) => label)
}

export function PasswordRequirements({
  value,
  id,
  className,
}: {
  value: string
  id?: string
  className?: string
}) {
  const validation = validatePassword(value)

  return (
    <ul
      id={id}
      className={cn('space-y-1.5 text-xs text-gray-500', className)}
      aria-label="Requisitos da senha"
      aria-live="polite"
    >
      {PASSWORD_REQUIREMENTS.map(([label, key]) => {
        const satisfied = validation[key]
        const Icon = satisfied ? CheckCircleIcon : MinusCircleIcon

        return (
          <li
            key={key}
            className={cn(
              'flex items-center gap-2',
              satisfied ? 'text-emerald-700' : 'text-gray-500'
            )}
            data-password-requirement={key}
            data-satisfied={satisfied}
          >
            <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
            <span>{label}</span>
          </li>
        )
      })}
    </ul>
  )
}
