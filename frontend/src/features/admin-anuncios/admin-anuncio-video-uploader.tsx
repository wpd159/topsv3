'use client'

import { Loader2, Upload } from 'lucide-react'
import { useRef, useState } from 'react'

import { FilePicker } from '@/components/forms/file-picker'
import { Button } from '@/components/ui/button'
import { ApiContractError, normalizeApiError } from '@/lib/api-contract'

import { adminVideoUploadIssue, uploadAdminAdVideo } from './api'

type AdminAnuncioVideoUploaderProps = {
  anuncioId: string
  disabled?: boolean
  onReload: () => Promise<void>
}

export function AdminAnuncioVideoUploader({
  anuncioId,
  disabled = false,
  onReload,
}: AdminAnuncioVideoUploaderProps) {
  const [arquivo, setArquivo] = useState<File | null>(null)
  const [idempotencyKey, setIdempotencyKey] = useState<string | null>(null)
  const [issue, setIssue] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [progress, setProgress] = useState(0)
  const [error, setError] = useState<ApiContractError | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const uploadLock = useRef(false)
  const replayAlreadyProcessed = error?.code === 'ADMIN_VIDEO_UPLOAD_ALREADY_PROCESSED'
  const retryAllowed = !error || error.retryable || (error.kind === 'CONFLICT' && !replayAlreadyProcessed)

  function currentDetailIsActive() {
    if (typeof window === 'undefined') return true
    const pathname = window.location?.pathname
    return !pathname || pathname.replace(/\/$/, '') === `/admin/anuncios/${encodeURIComponent(anuncioId)}`
  }

  function selectArquivo(files: File[]) {
    if (uploadLock.current || busy || disabled) return
    const selected = files[0] ?? null
    setArquivo(selected)
    setIdempotencyKey(selected ? crypto.randomUUID() : null)
    setIssue(selected ? adminVideoUploadIssue(selected) : null)
    setError(null)
    setSuccess(null)
    setProgress(0)
  }

  function removeArquivo() {
    if (uploadLock.current || busy || disabled) return
    setArquivo(null)
    setIdempotencyKey(null)
    setIssue(null)
    setError(null)
    setSuccess(null)
    setProgress(0)
  }

  async function submit() {
    if (uploadLock.current || busy || disabled || !arquivo || !idempotencyKey || issue
      || !retryAllowed) return
    if (!currentDetailIsActive()) return
    uploadLock.current = true
    setBusy(true)
    setError(null)
    setSuccess(null)
    setProgress(0)
    try {
      const response = await uploadAdminAdVideo(anuncioId, arquivo, idempotencyKey, setProgress)
      if (!currentDetailIsActive()) {
        throw new ApiContractError(
          'O anúncio exibido mudou durante o envio. Volte ao anúncio original e confira a lista antes de repetir a operação.',
          'CONFLICT', 409, true, response.requestId,
        )
      }
      try {
        await onReload()
      } catch (reloadError) {
        const normalized = normalizeApiError(reloadError)
        throw new ApiContractError(
          `A operação de vídeo foi confirmada pelo servidor, mas não foi possível atualizar a lista. ${normalized.message}`,
          normalized.kind,
          normalized.status,
          true,
          normalized.requestId || response.requestId,
          normalized.code,
        )
      }
      if (!currentDetailIsActive()) {
        throw new ApiContractError(
          'O anúncio exibido mudou durante a atualização. Volte ao anúncio original e confira a lista antes de repetir a operação.',
          'CONFLICT', 409, true, response.requestId,
        )
      }
      if (response.idempotente && response.status !== 'PENDENTE') {
        setProgress(0)
        setError(new ApiContractError(
          `Esta operação já consta como ${response.status}. Nenhum vídeo novo foi adicionado. Confira a lista e selecione novamente um arquivo para iniciar outra operação.`,
          'CONFLICT', 409, false, response.requestId, 'ADMIN_VIDEO_UPLOAD_ALREADY_PROCESSED',
        ))
        return
      }
      setProgress(100)
      setArquivo(null)
      setIdempotencyKey(null)
      setIssue(null)
      setSuccess(response.idempotente
        ? 'O vídeo desta operação já estava pendente; lista atualizada.'
        : 'Vídeo enviado e lista atualizada. Aguardando moderação.')
    } catch (uploadError) {
      setProgress(0)
      setError(normalizeApiError(uploadError))
    } finally {
      uploadLock.current = false
      setBusy(false)
    }
  }

  return (
    <section data-admin-video-uploader className="space-y-4 rounded-md border border-zinc-200 bg-zinc-50 p-4">
      <div>
        <h3 className="font-semibold text-zinc-950">Adicionar vídeo</h3>
        <p className="mt-1 text-sm text-zinc-600">
          Vídeos MP4 ou MOV. O servidor verificará o limite de tamanho do arquivo. O anúncio precisa ter o benefício Vídeo disponível.
          O vídeo ficará pendente de moderação e, quando aprovado, será sempre RESTRITA_18.
        </p>
      </div>
      <FilePicker
        ariaLabel="Selecionar vídeo para o anúncio"
        buttonLabel={arquivo ? 'Substituir vídeo' : 'Selecionar vídeo'}
        accept="video/mp4,video/quicktime,.mp4,.mov"
        files={arquivo ? [arquivo] : []}
        disabled={disabled || busy}
        helperText="Apenas um vídeo por envio."
        onSelect={selectArquivo}
        onRemove={removeArquivo}
      />
      {issue ? (
        <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-900">
          <p className="break-all font-semibold">{arquivo?.name}</p>
          <p className="mt-1">{issue}</p>
          <p className="mt-1">Remova ou substitua o arquivo para continuar.</p>
        </div>
      ) : null}
      {busy ? (
        <div className="space-y-2" role="status" aria-live="polite">
          <div className="flex justify-between gap-2 text-sm text-zinc-700">
            <span>{progress < 99 ? 'Enviando vídeo…' : 'Confirmando vídeo e atualizando a lista…'}</span>
            <span>{progress}%</span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-zinc-200" role="progressbar"
            aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress} aria-label="Progresso do envio do vídeo">
            <div className="h-full bg-pink-600 transition-[width]" style={{ width: `${progress}%` }} />
          </div>
        </div>
      ) : null}
      {error ? (
        <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-900">
          <p className="font-semibold">{replayAlreadyProcessed ? 'Nenhum vídeo novo foi adicionado' : 'Não foi possível confirmar o envio do vídeo'}</p>
          <p className="mt-1 break-all">{arquivo?.name}</p>
          <p className="mt-1">{error.message}</p>
          {!retryAllowed && !replayAlreadyProcessed ? <p className="mt-1">Remova ou substitua o arquivo para continuar.</p> : null}
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
        <Button type="button"
          disabled={disabled || busy || !arquivo || !idempotencyKey || Boolean(issue)
            || !retryAllowed}
          onClick={() => void submit()}>
          {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Upload className="mr-2 h-4 w-4" />}
          {busy ? 'Enviando...' : error && retryAllowed ? 'Tentar novamente' : 'Enviar vídeo'}
        </Button>
      </div>
    </section>
  )
}
