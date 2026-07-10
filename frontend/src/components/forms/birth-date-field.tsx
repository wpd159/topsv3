'use client'

import { useEffect, useId, useRef, useState } from 'react'
import { cn } from '@/lib/utils'
import {
  birthDateBRToIso,
  isAdultBirthDateBR,
  isoToBirthDateBR,
  maskBirthDateBR,
  parseBirthDateBR,
} from '@/lib/date/birth-date'

interface BirthDateFieldProps {
  value: string
  onChange: (isoValue: string) => void
  disabled?: boolean
  error?: string
  id?: string
  name?: string
  className?: string
  minimumAge?: number
}

// Campo de data de nascimento em input de texto puro (sem type="date"). O
// seletor nativo do Android antigo deixa o bitmap do calendario preso no
// compositor grafico e causa o mesmo ghosting ja corrigido no cadastro.
// value/onChange trabalham em ISO (YYYY-MM-DD, mesmo formato ja usado nos
// estados/stores existentes); a mascara DD/MM/AAAA e so a camada visual,
// convertida na fronteira deste componente.
export function BirthDateField({
  value,
  onChange,
  disabled,
  error,
  id,
  name,
  className,
  minimumAge = 18,
}: BirthDateFieldProps) {
  const autoId = useId()
  const fieldId = id ?? autoId
  const errorId = `${fieldId}-error`

  const [text, setText] = useState(() => isoToBirthDateBR(value))
  const [touched, setTouched] = useState(false)
  const lastEmitted = useRef(value)

  useEffect(() => {
    if (value === lastEmitted.current) return
    lastEmitted.current = value
    setText(isoToBirthDateBR(value))
  }, [value])

  const isComplete = text.length === 10
  const parsed = isComplete ? parseBirthDateBR(text) : null
  const isInvalidDate = isComplete && !parsed
  const isUnderage = isComplete && !!parsed && !isAdultBirthDateBR(text, minimumAge)

  const internalError = isInvalidDate
    ? 'Informe uma data de nascimento valida.'
    : isUnderage
      ? `Cadastro permitido apenas para maiores de ${minimumAge} anos.`
      : undefined

  const displayError = touched ? internalError ?? error : error

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const masked = maskBirthDateBR(event.target.value)
    setText(masked)

    const iso = masked.length === 10 ? birthDateBRToIso(masked) : null
    const nextValue = iso && isAdultBirthDateBR(masked, minimumAge) ? iso : ''

    lastEmitted.current = nextValue
    onChange(nextValue)
  }

  return (
    <div>
      <input
        type="text"
        inputMode="numeric"
        pattern="[0-9]*"
        maxLength={10}
        autoComplete="bday"
        placeholder="DD/MM/AAAA"
        id={fieldId}
        name={name}
        value={text}
        disabled={disabled}
        onChange={handleChange}
        onBlur={() => setTouched(true)}
        aria-invalid={!!displayError}
        aria-describedby={displayError ? errorId : undefined}
        className={cn(
          'w-full rounded-3xl border border-gray-500/40 bg-gray-200 px-3 py-3 text-base text-gray-900',
          'placeholder:text-gray-500 outline-none',
          'transition-[border-color,box-shadow] duration-150',
          'focus:border-[#FC1EAD] focus:shadow-[0_0_0_3px_rgba(252,30,173,0.12),0_0_12px_rgba(252,30,173,0.16)]',
          'disabled:cursor-not-allowed disabled:opacity-50 disabled:shadow-none',
          displayError && 'border-red-400',
          className
        )}
      />
      {displayError && (
        <p id={errorId} role="alert" className="mt-1 text-xs text-red-600">
          {displayError}
        </p>
      )}
    </div>
  )
}
