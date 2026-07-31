'use client'

import { CheckCircle2, FileText, IdCard, RefreshCw, ShieldCheck } from 'lucide-react'
import { BirthDateField } from '@/components/forms/birth-date-field'
import { FilePicker } from '@/components/forms/file-picker'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import type { WizardKycStatus } from '../api'
import type { WizardKycState } from '../types'
import { formatCpf } from '../wizard-utils'

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block min-w-0 max-w-full space-y-2 text-sm font-medium text-zinc-800">
      <span>{label}</span>
      {children}
    </label>
  )
}

function statusCopy(status: WizardKycStatus['status']) {
  switch (status) {
    case 'APROVADO':
      return ['Identidade confirmada', 'Seus dados foram aprovados e não precisam ser enviados novamente.']
    case 'PENDENTE':
      return ['Documentos enviados', 'Seu cadastro está aguardando análise. Você já pode concluir o anúncio.']
    case 'EM_ANALISE':
      return ['Documentos em análise', 'A equipe está conferindo seus dados. Você já pode concluir o anúncio.']
    case 'REJEITADO':
      return ['Reenvio necessário', 'Corrija os dados indicados e envie documentos válidos novamente.']
    case 'AJUSTE_SOLICITADO':
      return ['Ajuste solicitado', 'Revise as informações indicadas e faça um novo envio.']
    default:
      return ['Confirme sua identidade', 'Complete os dados e envie seu documento para finalizar.']
  }
}

export function WizardStepKyc({
  state,
  status,
  loading,
  error,
  onReload,
  onPatch,
  onSetDocumentos,
}: {
  state: WizardKycState
  status: WizardKycStatus | null
  loading: boolean
  error: string | null
  onReload: () => void
  onPatch: (payload: Partial<WizardKycState>) => void
  onSetDocumentos: (files: File[]) => void
}) {
  if (loading) {
    return <p className="text-sm text-zinc-600">Carregando sua verificação...</p>
  }

  if (error || !status) {
    return (
      <div className="rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
        <p>{error || 'Não foi possível carregar sua verificação.'}</p>
        <Button type="button" variant="outline" className="mt-3" onClick={onReload}>
          <RefreshCw className="mr-2 h-4 w-4" />
          Tentar novamente
        </Button>
      </div>
    )
  }

  const [title, description] = statusCopy(status.status)
  const showForm = status.podeReenviar
  const frontDoc = state.documentoModo === 'FRENTE_VERSO' ? state.documentos[0] ?? null : null
  const backDoc = state.documentoModo === 'FRENTE_VERSO' ? state.documentos[1] ?? null : null
  const pdfDoc = state.documentoModo === 'PDF' ? state.documentos[0] ?? null : null

  const switchMode = (mode: Exclude<WizardKycState['documentoModo'], null>) => {
    if (state.documentoModo === mode) return
    if (
      state.documentos.length > 0
      && !window.confirm('Trocar o formato removerá os arquivos já selecionados. Deseja continuar?')
    ) {
      return
    }
    onPatch({ documentoModo: mode })
    onSetDocumentos([])
  }

  return (
    <div className="min-w-0 max-w-full space-y-5">
      <div
        className={cn(
          'rounded-2xl border p-4 sm:p-5',
          status.status === 'APROVADO'
            ? 'border-emerald-200 bg-emerald-50'
            : status.status === 'REJEITADO' || status.status === 'AJUSTE_SOLICITADO'
              ? 'border-amber-200 bg-amber-50'
              : 'border-zinc-200 bg-zinc-50'
        )}
      >
        <div className="flex items-start gap-3">
          <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-emerald-700" />
          <div>
            <p className="font-semibold text-zinc-950">{title}</p>
            <p className="mt-1 text-sm leading-6 text-zinc-600">{description}</p>
            {status.motivo ? (
              <p className="mt-2 rounded-xl bg-white/80 px-3 py-2 text-sm text-zinc-800">
                Motivo: {status.motivo}
              </p>
            ) : null}
          </div>
        </div>
      </div>

      {!showForm ? (
        <div className="rounded-2xl border border-zinc-200 bg-white p-4 sm:p-5">
          <div className="grid gap-3 text-sm sm:grid-cols-2">
            <p><span className="font-semibold">Nome civil:</span> {status.nomeCivil || 'Confirmado'}</p>
            <p><span className="font-semibold">CPF:</span> {status.cpfMascarado || 'Confirmado'}</p>
          </div>
          {status.documentos.length > 0 ? (
            <div className="mt-4 flex flex-wrap gap-2">
              {status.documentos.map((documento) => (
                <span
                  key={documento.id}
                  className="inline-flex items-center gap-1 rounded-full border border-zinc-200 px-3 py-1 text-xs text-zinc-700"
                >
                  <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                  {documento.parte === 'UNICO' ? 'Documento único' : `Documento ${documento.parte.toLowerCase()}`}
                </span>
              ))}
            </div>
          ) : null}
        </div>
      ) : (
        <div className="min-w-0 max-w-full space-y-5 overflow-hidden rounded-2xl border border-zinc-200 bg-white p-4 sm:p-5">
          <Field label="Nome civil">
            <Input
              value={state.nomeCompleto}
              placeholder={status.nomeCivil || 'Seu nome completo'}
              onChange={(event) => onPatch({ nomeCompleto: event.target.value })}
              className="h-11 text-base focus-visible:border-[#FC1EAD] focus-visible:ring-pink-200"
            />
          </Field>

          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Data de nascimento">
              <BirthDateField
                value={state.dataNascimento}
                onValueChange={(value) => onPatch({ dataNascimento: value })}
                className="focus-visible:border-[#FC1EAD] focus-visible:ring-pink-200"
              />
            </Field>
            <Field label="CPF">
              <Input
                value={state.cpf}
                placeholder={status.cpfMascarado || '000.000.000-00'}
                inputMode="numeric"
                autoComplete="off"
                onChange={(event) => onPatch({ cpf: formatCpf(event.target.value) })}
                className="h-11 text-base focus-visible:border-[#FC1EAD] focus-visible:ring-pink-200"
              />
              {status.cpfPreenchido && !state.cpf ? (
                <p className="text-xs font-normal text-zinc-500">Deixe em branco para manter o CPF cadastrado.</p>
              ) : null}
            </Field>
          </div>

          <div className="min-w-0 max-w-full space-y-4 overflow-hidden rounded-2xl border border-zinc-200 bg-zinc-50/80 p-4">
            <div>
              <p className="text-sm font-semibold text-zinc-900">Documento de identificação</p>
              <p className="mt-1 text-sm leading-6 text-zinc-600">
                Envie um PDF único ou imagens JPG/PNG da frente e, quando necessário, do verso.
              </p>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <button
                type="button"
                onClick={() => switchMode('FRENTE_VERSO')}
                className={cn(
                  'rounded-2xl border px-4 py-4 text-left transition-colors hover:border-zinc-400',
                  state.documentoModo === 'FRENTE_VERSO' ? 'border-zinc-900 bg-white' : 'border-zinc-200 bg-white/70'
                )}
              >
                <span className="flex items-center gap-2 text-sm font-semibold"><IdCard className="h-4 w-4" /> Documento frente e verso</span>
              </button>
              <button
                type="button"
                onClick={() => switchMode('PDF')}
                className={cn(
                  'rounded-2xl border px-4 py-4 text-left transition-colors hover:border-zinc-400',
                  state.documentoModo === 'PDF' ? 'border-zinc-900 bg-white' : 'border-zinc-200 bg-white/70'
                )}
              >
                <span className="flex items-center gap-2 text-sm font-semibold"><FileText className="h-4 w-4" /> PDF único</span>
              </button>
            </div>

            {state.documentoModo === 'PDF' ? (
              <Field label="PDF do documento">
                <FilePicker
                  ariaLabel="Selecionar PDF do documento"
                  buttonLabel="Selecionar arquivo"
                  accept="application/pdf,.pdf"
                  files={pdfDoc ? [pdfDoc] : []}
                  onSelect={(files) => onSetDocumentos(files.slice(0, 1))}
                  onRemove={() => onSetDocumentos([])}
                  helperText={`PDF de até ${Math.round(status.tamanhoMaximoBytes / 1024 / 1024)} MB.`}
                />
              </Field>
            ) : state.documentoModo === 'FRENTE_VERSO' ? (
              <div className="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-3 sm:grid-cols-2">
                <Field label="Documento frente">
                  <FilePicker
                    ariaLabel="Selecionar imagem da frente do documento"
                    buttonLabel="Selecionar foto"
                    accept="image/jpeg,image/png,.jpg,.jpeg,.png"
                    files={frontDoc ? [frontDoc] : []}
                    onSelect={(files) => onSetDocumentos([files[0], backDoc].filter(Boolean) as File[])}
                    onRemove={() => onSetDocumentos([])}
                    helperText="Imagem principal obrigatória."
                  />
                </Field>
                <Field label="Documento verso">
                  <FilePicker
                    ariaLabel="Selecionar imagem do verso do documento"
                    buttonLabel="Selecionar foto"
                    accept="image/jpeg,image/png,.jpg,.jpeg,.png"
                    disabled={!frontDoc}
                    files={backDoc ? [backDoc] : []}
                    onSelect={(files) => onSetDocumentos([frontDoc, files[0]].filter(Boolean) as File[])}
                    onRemove={() => onSetDocumentos(frontDoc ? [frontDoc] : [])}
                    helperText={frontDoc
                      ? 'Opcional quando a frente contém todos os dados.'
                      : 'Selecione primeiro a frente do documento.'}
                  />
                </Field>
              </div>
            ) : null}
          </div>
        </div>
      )}
    </div>
  )
}
