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
  previewUrls?: (string | undefined)[]
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
  previewUrls,
  onSelect,
  onRemove,
  multiple = false,
  disabled = false,
  helperText,
  className,
}: FilePickerProps) {
  const inputRef = useRef<HTMLInputElement | null>(null)
  const [dragging, setDragging] = useState(false)
  const [failedPreviews, setFailedPreviews] = useState<Set<string>>(() => new Set())

  const select = (selected: FileList | null) => {
    if (!selected?.length || disabled) return
    onSelect(Array.from(selected))
    if (inputRef.current) inputRef.current.value = ''
  }

  return (
    <div className={cn('w-full min-w-0 max-w-full space-y-3 overflow-hidden', className)}>
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
        <ul className={cn(
          'w-full min-w-0 max-w-full overflow-hidden',
          previewUrls ? 'grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4' : 'space-y-2'
        )} aria-live="polite">
          {files.map((file, index) => {
            const previewUrl = previewUrls?.[index]
            return (
              <li
                key={`${file.name}:${file.size}:${file.lastModified}:${index}`}
                className={cn(
                  'flex w-full min-w-0 max-w-full gap-2 overflow-hidden rounded-xl border border-zinc-200 bg-white px-3 py-2',
                  previewUrls ? 'flex-col items-center' : 'items-center'
                )}
              >
                {previewUrls ? (
                  <div className="flex h-[88px] w-[88px] shrink-0 items-center justify-center overflow-hidden rounded-lg bg-zinc-50">
                    {previewUrl && !failedPreviews.has(previewUrl) ? (
                      // Blob URLs stay local to this session and must not enter the public Next image optimizer.
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        src={previewUrl}
                        alt={`Prévia local da foto ${index + 1}: ${file.name}`}
                        className="h-full w-full object-contain"
                        onError={() => setFailedPreviews((current) => new Set(current).add(previewUrl))}
                      />
                    ) : <span className="px-1 text-center text-xs leading-4 text-zinc-600">Prévia indisponível</span>}
                  </div>
                ) : <FileText className="h-4 w-4 shrink-0 text-zinc-500" aria-hidden="true" />}
                <span className={cn(
                  'min-w-0 truncate text-zinc-700',
                  previewUrls ? 'w-full text-center text-xs' : 'flex-1 text-sm'
                )} title={file.name}>
                  {file.name}{previewUrls ? '' : ' selecionado'}
                </span>
                <button
                  type="button"
                  disabled={disabled}
                  onClick={() => onRemove(index)}
                  className="grid h-9 w-9 shrink-0 place-items-center rounded-lg text-red-600 hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 disabled:opacity-50"
                  aria-label={previewUrls ? `Remover foto ${index + 1}: ${file.name}` : `Remover ${file.name}`}
                  title="Remover arquivo"
                >
                  <Trash2 className="h-4 w-4" aria-hidden="true" />
                </button>
              </li>
            )
          })}
        </ul>
      ) : null}
    </div>
  )
}
