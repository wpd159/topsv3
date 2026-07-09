'use client'

import * as React from 'react'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
} from '@/components/ui/select'

export const Row = ({ children }: { children: React.ReactNode }) => (
  <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">{children}</div>
)

export function LabeledInput(p: {
  id: string
  label: string
  value: string
  onChange: (v: string) => void
  placeholder?: string
  required?: boolean
  type?: string
  inputMode?: React.HTMLAttributes<HTMLInputElement>['inputMode']
  pattern?: string
  className?: string
}) {
  return (
    <div className="flex flex-col gap-2">
      <Label htmlFor={p.id}>{p.label}</Label>
      <Input
        id={p.id}
        value={p.value}
        onChange={(e) => p.onChange(e.target.value)}
        placeholder={p.placeholder}
        required={p.required}
        type={p.type}
        inputMode={p.inputMode}
        pattern={p.pattern}
        className={p.className ?? 'py-5'}
      />
    </div>
  )
}

export function LabeledSelect(p: {
  label: string
  value?: string
  onChange: (v: string) => void
  placeholder: string
  children: React.ReactNode
  disabled?: boolean
}) {
  return (
    <div className="flex flex-col gap-2">
      <Label>{p.label}</Label>
      <Select disabled={p.disabled} value={p.value || undefined} onValueChange={p.onChange}>
        <SelectTrigger className="py-5 w-full bg-gray-200 border-gray-500/40">
          <SelectValue placeholder={p.placeholder} />
        </SelectTrigger>
        <SelectContent>{p.children}</SelectContent>
      </Select>
    </div>
  )
}