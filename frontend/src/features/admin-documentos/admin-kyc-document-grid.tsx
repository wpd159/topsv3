'use client'

import { useState } from 'react'
import { ExternalLink, FileText, Loader2 } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { Badge } from '@/components/ui/badge'
import type { AdminKycDocument, AdminKycSubmission } from '@/features/admin-anuncios/types'

import { adminDocumentThumbnailUrl, getAdminDocumentTemporaryUrl } from './api'

function pretty(value: string) {
  return value.replaceAll('_', ' ').toLocaleLowerCase('pt-BR').replace(/^./, (letter) => letter.toUpperCase())
}

function formatDate(value?: string | null) {
  if (!value) return 'Data não informada'
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

function documentLabel(document: AdminKycDocument) {
  const type = pretty(document.tipo || 'IDENTIDADE')
  return document.parte === 'UNICO' ? type : `${type} - ${pretty(document.parte)}`
}

export function AdminKycDocumentGrid({
  submissions,
  emptyMessage = 'Nenhum envio de KYC.',
}: {
  submissions: AdminKycSubmission[]
  emptyMessage?: string
}) {
  const [openingId, setOpeningId] = useState<string | null>(null)
  const [failedThumbnails, setFailedThumbnails] = useState<Set<string>>(new Set())
  const [error, setError] = useState<unknown>(null)

  async function openDocument(documentId: string) {
    if (openingId) return
    setOpeningId(documentId)
    setError(null)
    try {
      const result = await getAdminDocumentTemporaryUrl(documentId)
      window.open(result.url, '_blank', 'noopener,noreferrer')
    } catch (reason) {
      setError(reason)
    } finally {
      setOpeningId(null)
    }
  }

  if (submissions.length === 0) {
    return <p className="border-y border-zinc-200 py-8 text-center text-sm text-zinc-500">{emptyMessage}</p>
  }

  return (
    <div className="space-y-5">
      {error ? <ContractState error={error} compact /> : null}
      {submissions.map((submission) => (
        <section key={submission.envioId} className="border-y border-zinc-200 py-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <p className="text-sm font-semibold text-zinc-900">Envio de {formatDate(submission.enviadoEm)}</p>
              <p className="mt-1 text-xs text-zinc-500">{submission.documentos.length} documento(s)</p>
            </div>
            <Badge variant="outline">{pretty(submission.status)}</Badge>
          </div>
          {submission.motivo ? <p className="mt-3 text-sm text-zinc-700">Motivo: {submission.motivo}</p> : null}
          <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {submission.documentos.map((document) => {
              const failed = failedThumbnails.has(document.id)
              const opening = openingId === document.id
              return (
                <button
                  key={document.id}
                  type="button"
                  className="group min-w-0 overflow-hidden rounded-md border border-zinc-200 bg-white text-left transition hover:border-pink-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-pink-500 disabled:cursor-wait disabled:opacity-70"
                  disabled={Boolean(openingId)}
                  onClick={() => void openDocument(document.id)}
                  aria-label={`Abrir ${documentLabel(document)} em visualização protegida`}
                >
                  <span className="relative flex aspect-[4/3] w-full items-center justify-center overflow-hidden bg-zinc-100">
                    {failed ? (
                      <FileText className="h-12 w-12 text-zinc-400" aria-hidden="true" />
                    ) : (
                      // The browser must send the admin session cookie directly to this protected endpoint.
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        src={adminDocumentThumbnailUrl(document.id)}
                        alt=""
                        width={480}
                        height={360}
                        loading="lazy"
                        className="h-full w-full object-contain"
                        onError={() => setFailedThumbnails((current) => new Set(current).add(document.id))}
                      />
                    )}
                    <span className="absolute right-2 top-2 rounded-full bg-white/95 p-2 text-zinc-700 shadow-sm">
                      {opening
                        ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                        : <ExternalLink className="h-4 w-4" aria-hidden="true" />}
                    </span>
                  </span>
                  <span className="block space-y-1 p-3">
                    <span className="block truncate text-sm font-semibold text-zinc-950">
                      {documentLabel(document)}
                    </span>
                    <span className="flex flex-wrap gap-x-2 gap-y-1 text-xs text-zinc-600">
                      <span>{pretty(document.status)}</span>
                      <span>{formatDate(document.criadoEm || submission.enviadoEm)}</span>
                    </span>
                  </span>
                </button>
              )
            })}
          </div>
        </section>
      ))}
    </div>
  )
}
