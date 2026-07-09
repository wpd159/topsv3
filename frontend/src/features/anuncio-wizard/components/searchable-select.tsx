'use client'

import { useEffect, useMemo, useState } from 'react'
import { Check, ChevronsUpDown, Search } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'
import type { SearchableSelectOption } from '../wizard-constants'
import { normalizeSearchValue } from '../wizard-utils'

export function SearchableSelect({
  value,
  label,
  placeholder,
  searchPlaceholder,
  emptyText,
  options,
  disabled,
  onSelect,
}: {
  value: string
  label: string
  placeholder: string
  searchPlaceholder: string
  emptyText: string
  options: SearchableSelectOption[]
  disabled?: boolean
  onSelect: (value: string) => void
}) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')

  const filteredOptions = useMemo(() => {
    const normalizedQuery = normalizeSearchValue(query)
    if (!normalizedQuery) return options

    return options.filter((item) =>
      normalizeSearchValue(`${item.label} ${item.searchLabel ?? ''}`).includes(normalizedQuery)
    )
  }, [options, query])

  useEffect(() => {
    if (!open) setQuery('')
  }, [open])

  return (
    <Popover open={open} onOpenChange={(nextOpen) => !disabled && setOpen(nextOpen)}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          role="combobox"
          aria-expanded={open}
          aria-disabled={disabled ? 'true' : 'false'}
          disabled={disabled}
          className={cn(
            'h-12 w-full justify-between rounded-xl border-zinc-200 bg-white px-4 text-left text-base font-normal text-zinc-900 shadow-none hover:bg-white sm:text-sm',
            disabled && 'text-zinc-400'
          )}
        >
          <span className={cn('truncate', !value && 'text-zinc-500')}>
            {value ? label : placeholder}
          </span>
          <ChevronsUpDown className="ml-3 h-4 w-4 shrink-0 text-zinc-400" />
        </Button>
      </PopoverTrigger>

      <PopoverContent
        align="start"
        sideOffset={8}
        className="w-[var(--radix-popover-trigger-width)] rounded-2xl border border-zinc-200 bg-white p-0 shadow-[0_22px_48px_rgba(24,24,27,0.14)]"
      >
        <Command shouldFilter={false} className="rounded-2xl bg-white">
          <div className="flex items-center gap-2 border-b border-zinc-100 px-3">
            <Search className="h-4 w-4 text-zinc-400" />
            <CommandInput
              value={query}
              onValueChange={setQuery}
              placeholder={searchPlaceholder}
              className="h-11 text-base sm:text-sm"
            />
          </div>

          <CommandList className="max-h-[280px] overflow-y-auto p-1">
            <CommandEmpty className="px-3 py-6 text-center text-sm text-zinc-500">
              {emptyText}
            </CommandEmpty>
            <CommandGroup className="p-1">
              {filteredOptions.map((item) => (
                <CommandItem
                  key={item.id}
                  value={item.id}
                  onSelect={() => {
                    onSelect(item.id)
                    setOpen(false)
                  }}
                  className="flex items-start gap-3 rounded-xl px-3 py-3"
                >
                  <div
                    className={cn(
                      'mt-0.5 flex h-5 w-5 items-center justify-center rounded-full border',
                      value === item.id
                        ? 'border-zinc-950 bg-zinc-950 text-white'
                        : 'border-zinc-300 text-transparent'
                    )}
                  >
                    <Check className="h-3 w-3" />
                  </div>

                  <div className="min-w-0">
                    <div className="truncate text-sm font-medium text-zinc-900">{item.label}</div>
                    {item.subtitle ? (
                      <div className="truncate text-xs text-zinc-500">{item.subtitle}</div>
                    ) : null}
                  </div>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
