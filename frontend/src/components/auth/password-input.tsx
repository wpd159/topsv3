'use client'

import { useState, type FocusEventHandler, type Ref } from 'react'
import {
  EyeIcon,
  EyeSlashIcon,
  LockClosedIcon,
} from '@heroicons/react/24/outline'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

type PasswordInputProps = {
  value: string
  onChange: (value: string) => void
  placeholder: string
  visibilityContext: string
  disabled?: boolean
  invalid?: boolean
  inputRef?: Ref<HTMLInputElement>
  describedBy?: string
  onFocus?: FocusEventHandler<HTMLInputElement>
  onBlur?: FocusEventHandler<HTMLInputElement>
  className?: string
  id?: string
  name?: string
  autoComplete?: string
  required?: boolean
}

export function PasswordInput({
  value,
  onChange,
  placeholder,
  visibilityContext,
  disabled = false,
  invalid = false,
  inputRef,
  describedBy,
  onFocus,
  onBlur,
  className,
  id,
  name,
  autoComplete = 'new-password',
  required = false,
}: PasswordInputProps) {
  const [visible, setVisible] = useState(false)

  return (
    <div className="relative">
      <LockClosedIcon
        className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400"
        aria-hidden="true"
      />
      <Input
        ref={inputRef}
        id={id}
        name={name}
        type={visible ? 'text' : 'password'}
        autoComplete={autoComplete}
        required={required}
        placeholder={placeholder}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onFocus={onFocus}
        onBlur={onBlur}
        disabled={disabled}
        aria-invalid={invalid}
        aria-describedby={describedBy}
        className={cn('pl-10 pr-12 py-5', invalid && 'border-red-400', className)}
      />
      <button
        type="button"
        aria-label={`${visible ? 'Ocultar' : 'Mostrar'} ${visibilityContext}`}
        aria-pressed={visible}
        onMouseDown={(event) => event.preventDefault()}
        onClick={() => setVisible((current) => !current)}
        disabled={disabled}
        className="absolute right-2 top-1/2 grid h-9 w-9 -translate-y-1/2 place-items-center text-gray-500 hover:text-gray-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#FC1EAD] focus-visible:ring-offset-2 disabled:opacity-50"
      >
        {visible ? (
          <EyeSlashIcon className="h-5 w-5" aria-hidden="true" />
        ) : (
          <EyeIcon className="h-5 w-5" aria-hidden="true" />
        )}
      </button>
    </div>
  )
}
