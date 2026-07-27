'use client'

import { useRef } from 'react'
import type { InputHTMLAttributes, KeyboardEvent } from 'react'

import { Input } from '@/components/ui/input'
import {
  maskPhoneBR,
  phoneCaretFromDigitOffset,
  phoneDigitOffset,
  phoneDigitsBR,
} from '@/lib/phone-mask'

type MaskedPhoneInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange' | 'value'> & {
  value: string
  onValueChange: (value: string) => void
}

export function MaskedPhoneInput({
  value,
  onValueChange,
  ...props
}: MaskedPhoneInputProps) {
  const inputRef = useRef<HTMLInputElement>(null)

  function restoreCaret(digitOffset: number) {
    requestAnimationFrame(() => {
      const input = inputRef.current
      if (!input) return
      const caret = phoneCaretFromDigitOffset(input.value, digitOffset)
      input.setSelectionRange(caret, caret)
    })
  }

  function handleChange(event: React.ChangeEvent<HTMLInputElement>) {
    const digitOffset = phoneDigitOffset(event.target.value, event.target.selectionStart ?? 0)
    onValueChange(maskPhoneBR(event.target.value))
    restoreCaret(digitOffset)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    const input = event.currentTarget
    const start = input.selectionStart ?? 0
    const end = input.selectionEnd ?? start
    if (event.key !== 'Backspace' || start !== end || start < 1 || /\d/.test(input.value[start - 1])) {
      props.onKeyDown?.(event)
      return
    }
    event.preventDefault()
    const digitOffset = phoneDigitOffset(input.value, start)
    const digits = phoneDigitsBR(input.value)
    if (digitOffset < 1) return
    const nextDigits = digits.slice(0, digitOffset - 1) + digits.slice(digitOffset)
    onValueChange(maskPhoneBR(nextDigits))
    restoreCaret(digitOffset - 1)
    props.onKeyDown?.(event)
  }

  return (
    <Input
      {...props}
      ref={inputRef}
      type="tel"
      inputMode="tel"
      autoComplete="tel"
      maxLength={15}
      value={value}
      onChange={handleChange}
      onKeyDown={handleKeyDown}
    />
  )
}
