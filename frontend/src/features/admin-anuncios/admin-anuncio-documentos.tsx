'use client'

import { useCallback, useEffect, useState } from 'react'
import { Download, ExternalLink, FileText, Loader2 } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'

import { getAdminDocumentTemporaryUrl, listAdminAdDocuments } from './api'
import type { AdminAdvertiserDetail, AdminKycDocument, AdminKycSubmission } from './types'

function formatDate(value?: string | null) {
  if (!value) return 'Não informado'
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function formatBytes(value: number) {
  if (!Number.isFinite(value) || value <= 0) return '—'
  return `${(value / 1024 / 1024).toFixed(2)} MB`
}

export function AdminAnuncioDocumentos({
  anuncioId,
  anunciante,
  autorizado,
}: {
  anuncioId: string
  anunciante?: AdminAdvertiserDetail | null
  autorizado: boolean
}) {
  const [submissions, setSubmissions] = useState<AdminKycSubmission[]>([])
  const [loading, setLoading] = useState(autorizado)
  const [openingId, setOpeningId] = useState<string | null>(null)
  const [error, setError] = useState<unknown>(null)

  const load = useCallback(async () => {
    if (!autorizado) return
    setLoading(true)
    setError(null)
    try {
      setSubmissions(await listAdminAdDocuments(anuncioId))
    } catch (reason) {
      setError(reason)
    } finally {
      setLoading(false)
    }
  }, [anuncioId, autorizado])

  useEffect(() => { void load() }, [load])

  async function openDocument(document: AdminKycDocument, download: boolean) {
    if (openingId) return
    setOpeningId(document.id)
    setError(null)
    try {
      const temporary = await getAdminDocumentTemporaryUrl(document.id)
      const anchor = window.document.createElement('a')
      anchor.href = temporary.url
      anchor.target = '_blank'
      anchor.rel = 'noopener noreferrer'
      if (download) anchor.download = `documento-${document.parte.toLowerCase()}`
      anchor.click()
    } catch (reason) {
      setError(reason)
    } finally {
      setOpeningId(null)
    }
  }

  if (!autorizado) {
    return <p className="text-sm font-medium text-amber-700">Seu perfil não possui DOCUMENTO_REVISAR.</p>
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[300px_minmax(0,1fr)]">
      <aside className="border-b border-zinc-200 pb-5 lg:border-b-0 lg:border-r lg:pb-0 lg:pr-6">
        <h3 className="text-sm font-semibold text-zinc-950">Dados para conferência</h3>
        <dl className="mt-4 space-y-3 text-sm">
          <div><dt className="text-xs text-zinc-500">Nome civil</dt><dd className="font-medium text-zinc-900">{anunciante?.nomeCivil || anunciante?.nome || 'Não informado'}</dd></div>
          <div><dt className="text-xs text-zinc-500">CPF</dt><dd className="font-medium text-zinc-900">{anunciante?.cpf || 'Não informado'}</dd></div>
          <div><dt className="text-xs text-zinc-500">Conta</dt><dd className="font-medium text-zinc-900">{anunciante?.status || 'Não informado'}</dd></div>
        </dl>
      </aside>
      <div className="min-w-0">
        <div className="flex items-center justify-between gap-3">
          <div><h3 className="font-semibold text-zinc-950">Documentos privados</h3><p className="text-xs text-zinc-500">URLs temporárias são emitidas somente ao abrir ou baixar.</p></div>
          <Button type="button" size="sm" variant="outline" onClick={() => void load()} disabled={loading}>Atualizar</Button>
        </div>
        {error ? <div className="mt-4"><ContractState error={error} onRetry={() => void load()} compact /></div> : null}
        {loading ? <p className="mt-5 flex items-center gap-2 text-sm text-zinc-500"><Loader2 className="h-4 w-4 animate-spin" />Carregando documentos...</p> : null}
        {!loading && submissions.length === 0 ? <p className="mt-5 text-sm text-zinc-600">Nenhum envio documental disponível.</p> : null}
        <div className="mt-4 space-y-4">
          {submissions.map((submission) => (
            <section key={submission.envioId} className="rounded-md border border-zinc-200 p-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div><p className="text-sm font-semibold text-zinc-900">Envio de {formatDate(submission.enviadoEm)}</p><p className="mt-1 text-xs text-zinc-500">{submission.documentos.length} arquivo(s)</p></div>
                <Badge variant="outline">{submission.status}</Badge>
              </div>
              {submission.motivo ? <p className="mt-3 text-sm text-zinc-700">Motivo: {submission.motivo}</p> : null}
              <div className="mt-3 divide-y divide-zinc-100">
                {submission.documentos.map((document) => (
                  <div key={document.id} className="flex flex-col gap-3 py-3 sm:flex-row sm:items-center sm:justify-between">
                    <div className="flex min-w-0 items-center gap-3">
                      <FileText className="h-5 w-5 shrink-0 text-pink-700" />
                      <div className="min-w-0"><p className="text-sm font-medium text-zinc-900">{document.parte}</p><p className="truncate text-xs text-zinc-500">{document.mimeType || 'Tipo indisponível'} · {formatBytes(document.tamanhoBytes)} · {document.status}</p></div>
                    </div>
                    <div className="flex gap-2">
                      <Button type="button" size="sm" variant="outline" disabled={Boolean(openingId)} onClick={() => void openDocument(document, false)}><ExternalLink className="mr-2 h-4 w-4" />Visualizar</Button>
                      <Button type="button" size="sm" variant="outline" disabled={Boolean(openingId)} onClick={() => void openDocument(document, true)}><Download className="mr-2 h-4 w-4" />Baixar</Button>
                    </div>
                  </div>
                ))}
              </div>
            </section>
          ))}
        </div>
      </div>
    </div>
  )
}
