'use client'

import { Input } from '@/components/ui/input'
import { maskBirthDate } from '@/lib/date/birth-date'
import { cn } from '@/lib/utils'

export function BirthDateField({
  value,
  onValueChange,
  className,
  id,
}: {
  value: string
  onValueChange: (value: string) => void
  className?: string
  id?: string
}) {
  return (
    <Input
      id={id}
      type="text"
      inputMode="numeric"
      autoComplete="bday"
      placeholder="DD/MM/AAAA"
      value={value}
      maxLength={10}
      onChange={(event) => onValueChange(maskBirthDate(event.target.value))}
      className={cn('h-11 text-base', className)}
    />
  )
}
