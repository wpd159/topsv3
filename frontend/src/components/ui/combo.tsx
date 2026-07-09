'use client'

import * as React from 'react'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Button } from '@/components/ui/button'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'
import { ChevronUpDownIcon, CheckIcon } from '@heroicons/react/24/solid'
import { cn } from '@/lib/utils'

type BaseItem = { id: number; nome: string }

export function Combo<T extends BaseItem>({
  open,
  onOpenChange,
  disabled,
  label,
  items,
  placeholder,
  emptyText,
  isSelected,
  onSelect,
}: {
  open: boolean
  onOpenChange: (v: boolean) => void
  disabled?: boolean
  label: string
  items: T[]
  placeholder: string
  emptyText: string
  isSelected: (x: T) => boolean
  onSelect: (x: T) => void
}) {
  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          role="combobox"
          disabled={disabled}
          className={cn(
            'h-[52px] w-full justify-between rounded-xl bg-gray-200 border border-gray-500/40',
            'hover:bg-gray-200',
            disabled && 'opacity-60'
          )}
        >
          <span className="truncate text-left">{label}</span>
          <ChevronUpDownIcon className="h-4 w-4 opacity-60" />
        </Button>
      </PopoverTrigger>

      <PopoverContent
        align="start"
        sideOffset={8}
        className="w-[var(--radix-popover-trigger-width)] p-0 z-[60]"
      >
        <Command>
          <CommandInput placeholder={placeholder} />
          <CommandList className="max-h-[320px] overflow-y-auto overscroll-contain">
            <CommandEmpty>{emptyText}</CommandEmpty>
            <CommandGroup>
              {items.map((x) => (
                <CommandItem
                  key={x.id}
                  onSelect={() => onSelect(x)}
                >
                  <CheckIcon
                    className={cn('mr-2 h-4 w-4', isSelected(x) ? 'opacity-100' : 'opacity-0')}
                  />
                  {x.nome}
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}