'use client'

import { FileText, Trash2, Upload } from 'lucide-react'
import { useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

type FilePickerProps = {
  ariaLabel: string
  buttonLabel: string
  accept: string
  files: File[]
  onSelect: (files: File[]) => void
  onRemove: (index: number) => void
  multiple?: boolean
  disabled?: boolean
  helperText?: string
  className?: string
}

export function FilePicker({
  ariaLabel,
  buttonLabel,
  accept,
  files,
  onSelect,
  onRemove,
  multiple = false,
  disabled = false,
  helperText,
  className,
}: FilePickerProps) {
  const inputRef = useRef<HTMLInputElement | null>(null)
  const [dragging, setDragging] = useState(false)

  const select = (selected: FileList | null) => {
    if (!selected?.length || disabled) return
    onSelect(Array.from(selected))
    if (inputRef.current) inputRef.current.value = ''
  }

  return (
    <div className={cn('min-w-0 space-y-3', className)}>
      <div
        className={cn(
          'flex min-h-28 min-w-0 flex-col items-center justify-center rounded-xl border border-dashed px-4 py-4 text-center transition',
          dragging ? 'border-pink-500 bg-pink-50' : 'border-zinc-300 bg-zinc-50',
          disabled && 'cursor-not-allowed opacity-60'
        )}
        onDragEnter={(event) => {
          event.preventDefault()
          if (!disabled) setDragging(true)
        }}
        onDragOver={(event) => event.preventDefault()}
        onDragLeave={(event) => {
          if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setDragging(false)
        }}
        onDrop={(event) => {
          event.preventDefault()
          setDragging(false)
          select(event.dataTransfer.files)
        }}
      >
        <Upload className="h-5 w-5 text-zinc-500" aria-hidden="true" />
        <Button
          type="button"
          variant="outline"
          disabled={disabled}
          className="mt-3 min-h-11 max-w-full rounded-xl bg-white px-4 focus-visible:ring-2 focus-visible:ring-pink-500"
          aria-label={ariaLabel}
          onClick={() => inputRef.current?.click()}
        >
          <span className="truncate">{buttonLabel}</span>
        </Button>
        {helperText ? <p className="mt-2 text-xs leading-5 text-zinc-500">{helperText}</p> : null}
        <input
          ref={inputRef}
          type="file"
          accept={accept}
          multiple={multiple}
          disabled={disabled}
          className="sr-only"
          tabIndex={-1}
          aria-hidden="true"
          onChange={(event) => select(event.currentTarget.files)}
        />
      </div>

      {files.length ? (
        <ul className="min-w-0 space-y-2" aria-live="polite">
          {files.map((file, index) => (
            <li
              key={`${file.name}:${file.size}:${file.lastModified}:${index}`}
              className="flex min-w-0 items-center gap-2 rounded-xl border border-zinc-200 bg-white px-3 py-2"
            >
              <FileText className="h-4 w-4 shrink-0 text-zinc-500" aria-hidden="true" />
              <span className="min-w-0 flex-1 truncate text-sm text-zinc-700" title={file.name}>
                {file.name} selecionado
              </span>
              <button
                type="button"
                disabled={disabled}
                onClick={() => onRemove(index)}
                className="grid h-9 w-9 shrink-0 place-items-center rounded-lg text-red-600 hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 disabled:opacity-50"
                aria-label={`Remover ${file.name}`}
                title="Remover arquivo"
              >
                <Trash2 className="h-4 w-4" aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}
