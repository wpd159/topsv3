'use client'

import { useEffect, useRef } from 'react'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

export function StepPanel({ children }: { children: React.ReactNode }) {
  return <div className="space-y-5">{children}</div>
}

export function Field({ label, children, invalid = false }: { label: string; children: React.ReactNode; invalid?: boolean }) {
  return (
    <div className={cn('space-y-2', invalid && 'rounded-xl ring-2 ring-red-600 ring-offset-2')}>
      <Label className="text-sm font-semibold text-zinc-800">{label}</Label>
      {children}
    </div>
  )
}

export function ChoiceGroup({
  title,
  items,
  selected,
  onToggle,
  invalid = false,
}: {
  title: string
  items: Array<{ value: string; label: string }>
  selected: string[]
  onToggle: (value: string) => void
  invalid?: boolean
}) {
  return (
    <div className={cn('space-y-2', invalid && 'rounded-xl ring-2 ring-red-600 ring-offset-2')}>
      <Label className="text-sm font-semibold text-zinc-800">{title}</Label>
      <div className="flex flex-wrap gap-2">
        {items.map((item) => {
          const active = selected.includes(item.value)
          return (
            <button
              key={item.value}
              type="button"
              onClick={() => onToggle(item.value)}
              className={cn(
                'rounded-full border px-3 py-2 text-sm transition',
                active
                  ? 'border-zinc-950 bg-zinc-950 text-white'
                  : 'border-zinc-200 bg-white text-zinc-700 hover:border-zinc-400'
              )}
            >
              {item.label}
            </button>
          )
        })}
      </div>
    </div>
  )
}

export function ReviewRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-zinc-200 bg-zinc-50 p-4">
      <p className="text-xs font-semibold uppercase tracking-[0.16em] text-zinc-500">{label}</p>
      <p className="mt-1 text-sm font-medium text-zinc-950">{value}</p>
    </div>
  )
}

export function PreviewTag({
  icon,
  label,
  tone = 'dark',
}: {
  icon: React.ReactNode
  label: string
  tone?: 'dark' | 'light'
}) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs',
        tone === 'dark'
          ? 'border border-white/15 bg-white/10 text-zinc-100'
          : 'border border-zinc-200 bg-zinc-50 text-zinc-700'
      )}
    >
      {icon}
      {label}
    </span>
  )
}

export function AutoResizeTextarea({
  value,
  onChange,
  placeholder,
  minRows = 4,
  maxRows = 8,
  maxLength,
  invalid = false,
}: {
  value: string
  onChange: (event: React.ChangeEvent<HTMLTextAreaElement>) => void
  placeholder: string
  minRows?: number
  maxRows?: number
  maxLength?: number
  invalid?: boolean
}) {
  const ref = useRef<HTMLTextAreaElement | null>(null)

  useEffect(() => {
    const textarea = ref.current
    if (!textarea) return

    const computed = window.getComputedStyle(textarea)
    const lineHeight = Number.parseFloat(computed.lineHeight || '24')
    const minHeight = lineHeight * minRows + 24

    if (minRows === maxRows) {
      textarea.style.height = `${minHeight}px`
      return
    }

    textarea.style.height = '0px'
    const maxHeight = lineHeight * maxRows + 24
    const nextHeight = Math.min(Math.max(textarea.scrollHeight, minHeight), maxHeight)
    textarea.style.height = `${nextHeight}px`
  }, [maxRows, minRows, value])

  return (
    <textarea
      ref={ref}
      value={value}
      onChange={onChange}
      rows={minRows}
      placeholder={placeholder}
      maxLength={maxLength}
      aria-invalid={invalid}
      className={cn('min-h-[120px] w-full resize-none rounded-2xl border border-zinc-200 bg-zinc-50/60 px-4 py-3 text-base leading-6 text-zinc-900 shadow-none outline-none transition-[border-color,background-color,box-shadow] placeholder:text-zinc-400 focus:border-zinc-900 focus:bg-white focus:ring-2 focus:ring-zinc-900/10 sm:text-sm', invalid && 'border-red-600')}
    />
  )
}
