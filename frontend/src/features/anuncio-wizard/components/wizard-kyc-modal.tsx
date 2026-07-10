'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import { CheckCircle2, FileText, IdCard, UploadCloud } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import type { WizardKycState } from '../types'
import type { PublishGuardState } from '../wizard-utils'
import { formatCpf, getAdultMaxDate } from '../wizard-utils'
import { Field } from './wizard-ui'

type DocumentMode = 'sides' | 'pdf'

function isPdfFile(file: File | null | undefined) {
  if (!file) return false
  return file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')
}

export function WizardKycModal({
  publishGuard,
  hasExistingKyc,
  publishing,
  kyc,
  onClose,
  onConfirm,
  onPatchKyc,
  onSetDocumentos,
}: {
  publishGuard: PublishGuardState
  hasExistingKyc: boolean
  publishing: boolean
  kyc: WizardKycState
  onClose: () => void
  onConfirm: () => void
  onPatchKyc: (payload: Partial<WizardKycState>) => void
  onSetDocumentos: (payload: File[]) => void
}) {
  const inferredMode = useMemo<DocumentMode>(() => {
    if (kyc.documentos.some((file) => isPdfFile(file))) return 'pdf'
    return 'sides'
  }, [kyc.documentos])

  const [documentMode, setDocumentMode] = useState<DocumentMode>(inferredMode)
  const [frontDoc, setFrontDoc] = useState<File | null>(null)
  const [backDoc, setBackDoc] = useState<File | null>(null)
  const [pdfDoc, setPdfDoc] = useState<File | null>(null)
  const uploadSectionRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (kyc.documentos.length === 0) {
      setFrontDoc(null)
      setBackDoc(null)
      setPdfDoc(null)
      return
    }

    setDocumentMode(inferredMode)
    if (inferredMode === 'pdf') {
      setPdfDoc(kyc.documentos[0] ?? null)
      setFrontDoc(null)
      setBackDoc(null)
      return
    }

    setFrontDoc(kyc.documentos[0] ?? null)
    setBackDoc(kyc.documentos[1] ?? null)
    setPdfDoc(null)
  }, [inferredMode, kyc.documentos])

  const documentCount = kyc.documentos.length || kyc.documentoNomes.length

  const switchMode = (mode: DocumentMode) => {
    setDocumentMode(mode)
    setFrontDoc(null)
    setBackDoc(null)
    setPdfDoc(null)
    onSetDocumentos([])
    window.requestAnimationFrame(() => {
      uploadSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  }

  const applySides = (nextFront: File | null, nextBack: File | null) => {
    const next = [nextFront, nextBack].filter(Boolean) as File[]
    onSetDocumentos(next)
  }

  const handleFrontChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null
    setFrontDoc(file)
    applySides(file, backDoc)
    event.currentTarget.value = ''
  }

  const handleBackChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null
    setBackDoc(file)
    applySides(frontDoc, file)
    event.currentTarget.value = ''
  }

  const handlePdfChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null
    setPdfDoc(file)
    onSetDocumentos(file ? [file] : [])
    event.currentTarget.value = ''
  }

  return (
    <Dialog
      open={publishGuard.open}
      onOpenChange={(open) => {
        if (publishing) return
        if (open) return
      }}
    >
      <DialogContent
        showCloseButton={false}
        className="inset-0 bottom-auto left-0 top-0 h-[100dvh] max-h-[100dvh] w-screen max-w-none translate-x-0 translate-y-0 overflow-hidden rounded-none border-0 bg-white p-0 shadow-none sm:bottom-auto sm:left-1/2 sm:top-1/2 sm:h-auto sm:max-h-[92vh] sm:w-[min(92vw,760px)] sm:max-w-[760px] sm:translate-x-[-50%] sm:translate-y-[-50%] sm:rounded-[32px] sm:border sm:border-zinc-200 sm:shadow-2xl"
        onPointerDownOutside={(event) => event.preventDefault()}
        onEscapeKeyDown={(event) => event.preventDefault()}
      >
        <div className="flex h-[100dvh] max-h-[100dvh] flex-col sm:h-auto sm:max-h-[92vh]">
          <div className="flex-1 overflow-y-auto px-5 pb-4 pt-5 sm:px-6 sm:pb-5 sm:pt-6">
            <div className="space-y-4">
              <DialogHeader className="space-y-2 text-left">
                <DialogTitle className="text-[1.9rem] font-semibold tracking-normal text-zinc-950 sm:text-2xl">
                  {publishGuard.title}
                </DialogTitle>
                <DialogDescription className="text-base leading-7 text-zinc-600 sm:text-sm sm:leading-6">
                  {publishGuard.description}
                </DialogDescription>
              </DialogHeader>

              <div className="rounded-2xl border border-zinc-200 bg-zinc-50 px-4 py-3 text-sm leading-6 text-zinc-700">
                O anúncio será enviado para moderação logo após a confirmação dos dados pessoais e
                documentos obrigatórios.
              </div>

              {!hasExistingKyc ? (
                <div className="space-y-4 rounded-2xl border border-zinc-200 bg-white p-4 sm:p-5">
                  <Field label="Nome real">
                    <Input
                      value={kyc.nomeCompleto}
                      onChange={(event) => onPatchKyc({ nomeCompleto: event.target.value })}
                      className="h-11 text-base"
                    />
                  </Field>

                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    <Field label="Nascimento">
                      <Input
                        type="date"
                        max={getAdultMaxDate()}
                        value={kyc.dataNascimento}
                        onChange={(event) => onPatchKyc({ dataNascimento: event.target.value })}
                        className="h-11 text-base"
                      />
                    </Field>

                    <Field label="CPF">
                      <Input
                        value={kyc.cpf}
                        onChange={(event) => onPatchKyc({ cpf: formatCpf(event.target.value) })}
                        inputMode="numeric"
                        className="h-11 text-base"
                      />
                    </Field>
                  </div>

                  <div className="space-y-3 rounded-2xl border border-zinc-200 bg-zinc-50/80 p-4">
                    <div className="space-y-1">
                      <p className="text-sm font-semibold text-zinc-900">Documentos</p>
                      <p className="text-sm leading-6 text-zinc-600">
                        Escolha a forma mais prática de confirmar sua identidade.
                      </p>
                    </div>

                    <div className="grid gap-3 sm:grid-cols-2">
                      <button
                        type="button"
                        onClick={() => switchMode('sides')}
                        className={cn(
                          'rounded-2xl border px-4 py-4 text-left transition',
                          documentMode === 'sides'
                            ? 'border-zinc-900 bg-white shadow-sm'
                            : 'border-zinc-200 bg-white/70 hover:border-zinc-300'
                        )}
                      >
                        <div className="flex items-center gap-2 text-sm font-semibold text-zinc-900">
                          <IdCard className="h-4 w-4" />
                          Documento frente e verso
                        </div>
                        <p className="mt-2 text-sm leading-6 text-zinc-600">
                          Envie a frente e o verso em imagens separadas, se preferir.
                        </p>
                      </button>

                      <button
                        type="button"
                        onClick={() => switchMode('pdf')}
                        className={cn(
                          'rounded-2xl border px-4 py-4 text-left transition',
                          documentMode === 'pdf'
                            ? 'border-zinc-900 bg-white shadow-sm'
                            : 'border-zinc-200 bg-white/70 hover:border-zinc-300'
                        )}
                      >
                        <div className="flex items-center gap-2 text-sm font-semibold text-zinc-900">
                          <FileText className="h-4 w-4" />
                          PDF único
                        </div>
                        <p className="mt-2 text-sm leading-6 text-zinc-600">
                          Ideal para CNH digital, passaporte ou arquivo consolidado.
                        </p>
                      </button>
                    </div>

                    <div
                      ref={uploadSectionRef}
                      className="scroll-mt-4 rounded-2xl border border-rose-100 bg-white px-3 py-2 text-sm font-semibold text-zinc-900"
                    >
                      {documentMode === 'pdf'
                        ? 'Agora envie o PDF do documento'
                        : 'Agora envie a frente e o verso do documento'}
                    </div>

                    {documentMode === 'sides' ? (
                      <div className="grid gap-3 sm:grid-cols-2">
                        <Field label="Documento frente">
                          <Input
                            type="file"
                            accept="image/*"
                            className="h-11 text-sm"
                            onChange={handleFrontChange}
                          />
                          <p className="text-xs text-zinc-500">
                            {frontDoc?.name || 'Envie a imagem principal do documento.'}
                          </p>
                        </Field>

                        <Field label="Documento verso">
                          <Input
                            type="file"
                            accept="image/*"
                            className="h-11 text-sm"
                            onChange={handleBackChange}
                          />
                          <p className="text-xs text-zinc-500">
                            {backDoc?.name || 'Opcional quando a informação já estiver completa na frente.'}
                          </p>
                        </Field>
                      </div>
                    ) : (
                      <Field label="PDF do documento">
                        <Input
                          type="file"
                          accept="application/pdf,.pdf"
                          className="h-11 text-sm"
                          onChange={handlePdfChange}
                        />
                        <p className="text-xs text-zinc-500">
                          {pdfDoc?.name || 'Envie um único PDF com o documento consolidado.'}
                        </p>
                      </Field>
                    )}

                    <div className="flex items-center gap-2 rounded-2xl border border-zinc-200 bg-white px-3 py-2 text-xs text-zinc-600">
                      <UploadCloud className="h-4 w-4 text-zinc-500" />
                      <span>
                        {documentCount > 0
                          ? `${documentCount} documento(s) preparado(s) para envio`
                          : 'Você pode enviar frente/verso ou um PDF único'}
                      </span>
                    </div>

                    {kyc.documentoNomes.length > 0 ? (
                      <div className="flex flex-wrap gap-2">
                        {kyc.documentoNomes.map((name) => (
                          <span
                            key={name}
                            className="inline-flex items-center gap-1 rounded-full border border-zinc-200 bg-white px-2.5 py-1 text-xs text-zinc-700"
                          >
                            <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                            {name}
                          </span>
                        ))}
                      </div>
                    ) : null}
                  </div>
                </div>
              ) : null}
            </div>
          </div>

          <DialogFooter className="shrink-0 border-t border-zinc-100 bg-white px-5 pb-[calc(env(safe-area-inset-bottom)+16px)] pt-4 sm:px-6 sm:pb-6">
            <div className="flex w-full flex-col gap-2">
              <Button
                type="button"
                onClick={onConfirm}
                disabled={publishing}
                className="h-12 w-full rounded-full bg-zinc-950 text-white hover:bg-zinc-800"
              >
                {publishing ? 'Confirmando…' : publishGuard.actionLabel}
              </Button>

              <Button
                type="button"
                variant="outline"
                onClick={onClose}
                disabled={publishing}
                className="h-12 w-full rounded-full border-zinc-200"
              >
                Agora não
              </Button>
            </div>
          </DialogFooter>
        </div>
      </DialogContent>
    </Dialog>
  )
}
