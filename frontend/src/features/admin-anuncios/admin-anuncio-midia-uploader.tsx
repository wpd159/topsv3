'use client'

import { Loader2, Upload } from 'lucide-react'
import { useRef, useState } from 'react'

import { FilePicker } from '@/components/forms/file-picker'
import { Button } from '@/components/ui/button'
import { ApiContractError, normalizeApiError } from '@/lib/api-contract'
import { PHOTO_UPLOAD_ACCEPT, PHOTO_UPLOAD_GUIDANCE, validatePhotoUpload } from '@/lib/photo-upload-validation'

import { uploadAdminAdMedia } from './api'

type AdminAnuncioMidiaUploaderProps = {
  anuncioId: string
  disabled?: boolean
  onReload: () => Promise<void>
}

export function AdminAnuncioMidiaUploader({
  anuncioId,
  disabled = false,
  onReload,
}: AdminAnuncioMidiaUploaderProps) {
  const [arquivo, setArquivo] = useState<File | null>(null)
  const [idempotencyKey, setIdempotencyKey] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<ApiContractError | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [validation, setValidation] = useState<{ status: 'checking' | 'valid' | 'invalid'; message?: string } | null>(null)
  const uploadLock = useRef(false)
  const selectionVersion = useRef(0)
  const selectedFile = useRef<File | null>(null)
  const validatedFile = useRef<File | null>(null)

  function selectArquivo(files: File[]) {
    if (uploadLock.current || busy || disabled) return
    const selected = files[0] ?? null
    const version = ++selectionVersion.current
    selectedFile.current = selected
    validatedFile.current = null
    setArquivo(selected)
    setIdempotencyKey(selected ? crypto.randomUUID() : null)
    setError(null)
    setSuccess(null)
    setValidation(selected ? { status: 'checking' } : null)
    if (!selected) return
    void validatePhotoUpload(selected).then((result) => {
      if (version !== selectionVersion.current || selectedFile.current !== selected) return
      validatedFile.current = result.valid ? selected : null
      setValidation(result.valid ? { status: 'valid' } : { status: 'invalid', message: result.message })
    })
  }

  function removeArquivo() {
    if (uploadLock.current || busy || disabled) return
    selectionVersion.current += 1
    selectedFile.current = null
    validatedFile.current = null
    setArquivo(null)
    setIdempotencyKey(null)
    setValidation(null)
    setError(null)
    setSuccess(null)
  }

  async function submit() {
    if (uploadLock.current || busy || disabled || !arquivo || !idempotencyKey
      || selectedFile.current !== arquivo || validatedFile.current !== arquivo
      || validation?.status !== 'valid' || (error && !error.retryable)) return
    uploadLock.current = true
    setBusy(true)
    setError(null)
    setSuccess(null)
    try {
      const response = await uploadAdminAdMedia(anuncioId, arquivo, idempotencyKey)
      try {
        await onReload()
      } catch (reloadError) {
        const normalized = normalizeApiError(reloadError)
        throw new ApiContractError(
          `A foto foi recebida, mas não foi possível atualizar a lista. ${normalized.message}`,
          normalized.kind,
          normalized.status,
          true,
          normalized.requestId || response.requestId,
          normalized.code,
        )
      }
      selectionVersion.current += 1
      selectedFile.current = null
      validatedFile.current = null
      setArquivo(null)
      setIdempotencyKey(null)
      setValidation(null)
      setSuccess('Foto enviada e lista atualizada. Confira abaixo o estado confirmado pelo servidor.')
    } catch (uploadError) {
      setError(normalizeApiError(uploadError))
    } finally {
      uploadLock.current = false
      setBusy(false)
    }
  }

  return (
    <section data-admin-media-uploader className="space-y-4 rounded-md border border-zinc-200 bg-zinc-50 p-4">
      <div>
        <h3 className="font-semibold text-zinc-950">Adicionar foto</h3>
        <p className="mt-1 text-sm text-zinc-600">
          {PHOTO_UPLOAD_GUIDANCE} A nova foto ficará pendente até receber uma decisão.
        </p>
      </div>
      <FilePicker
        ariaLabel="Selecionar foto para o anúncio"
        buttonLabel={arquivo ? 'Substituir foto' : 'Selecionar foto'}
        accept={PHOTO_UPLOAD_ACCEPT}
        files={arquivo ? [arquivo] : []}
        disabled={disabled || busy}
        helperText="Apenas uma foto por envio."
        onSelect={selectArquivo}
        onRemove={removeArquivo}
      />
      {validation?.status === 'checking' ? (
        <p role="status" className="text-sm text-zinc-600">{arquivo?.name}: Verificando foto…</p>
      ) : null}
      {validation?.status === 'invalid' ? (
        <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-900">
          <p className="break-all font-semibold">{arquivo?.name}</p>
          <p className="mt-1">{validation.message}</p>
          <p className="mt-1">Remova ou substitua o arquivo para continuar.</p>
        </div>
      ) : null}
      {error ? (
        <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-900">
          <p className="font-semibold">Não foi possível enviar a foto</p>
          <p className="mt-1 break-all">{arquivo?.name}</p>
          <p className="mt-1">{error.message}</p>
          {!error.retryable ? <p className="mt-1">Remova ou substitua o arquivo para continuar.</p> : null}
          {error.code || error.requestId ? (
            <dl className="mt-2 grid gap-1 text-xs">
              {error.code ? <div className="flex gap-1"><dt className="font-semibold">code:</dt><dd>{error.code}</dd></div> : null}
              {error.requestId ? <div className="flex min-w-0 gap-1"><dt className="shrink-0 font-semibold">requestId:</dt><dd className="break-all">{error.requestId}</dd></div> : null}
            </dl>
          ) : null}
        </div>
      ) : null}
      {success ? <p role="status" className="rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900">{success}</p> : null}
      <div className="flex justify-end">
        <Button
          type="button"
          disabled={disabled || busy || !arquivo || !idempotencyKey || validation?.status !== 'valid' || Boolean(error && !error.retryable)}
          onClick={() => void submit()}
        >
          {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Upload className="mr-2 h-4 w-4" />}
          {busy ? 'Enviando...' : error?.retryable ? 'Tentar novamente' : 'Enviar foto'}
        </Button>
      </div>
    </section>
  )
}
