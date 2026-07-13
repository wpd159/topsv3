'use client'

import { useCallback, useEffect, useState } from 'react'
import { ExternalLink, FileCheck2, RefreshCw } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { corrigirTextoCorrompido } from '@/lib/text/encoding'
import {
  decideAdminKyc,
  fetchAdminKycQueue,
  fetchAdminKycTemporaryUrl,
} from '../api/client'
import type { AdminKycDecision, AdminKycSubmission } from '../api/types'

function documentLabel(part: string) {
  if (part === 'UNICO') return 'Documento único'
  return part === 'FRENTE' ? 'Frente do documento' : 'Verso do documento'
}

function sizeLabel(bytes: number) {
  return `${Math.max(1, Math.round(bytes / 1024))} KB`
}

export function ModeracaoV2KycPanel() {
  const [items, setItems] = useState<AdminKycSubmission[]>([])
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reasons, setReasons] = useState<Record<string, string>>({})

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setItems(await fetchAdminKycQueue())
    } catch (reason) {
      setError(reason instanceof Error ? corrigirTextoCorrompido(reason.message) : 'Falha ao carregar documentos.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const viewDocument = async (documentId: string) => {
    const tab = window.open('', '_blank')
    if (tab) tab.opener = null
    try {
      const result = await fetchAdminKycTemporaryUrl(documentId)
      if (tab) tab.location.replace(result.url)
      else window.open(result.url, '_blank', 'noopener,noreferrer')
    } catch (reason) {
      tab?.close()
      toast.error(reason instanceof Error ? corrigirTextoCorrompido(reason.message) : 'Documento indisponível.')
    }
  }

  const decide = async (item: AdminKycSubmission, decision: AdminKycDecision) => {
    const reason = reasons[item.envioId]?.trim() || ''
    if (decision !== 'APROVAR' && !reason) {
      toast.warning('Informe o motivo da rejeição ou do ajuste.')
      return
    }
    setBusyId(item.envioId)
    try {
      await decideAdminKyc(item.envioId, decision, reason)
      toast.success('Decisão documental registrada.')
      setReasons((current) => ({ ...current, [item.envioId]: '' }))
      await load()
    } catch (reasonError) {
      toast.error(reasonError instanceof Error
        ? corrigirTextoCorrompido(reasonError.message)
        : 'Falha ao decidir documentos.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section aria-labelledby="kyc-moderation-title" className="mb-8 border-b border-gray-200 pb-8">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 id="kyc-moderation-title" className="text-lg font-semibold text-gray-950">
            Verificação documental
          </h2>
          <p className="mt-1 text-sm text-gray-600">
            Acesso privado, temporário e auditado aos documentos enviados.
          </p>
        </div>
        <Button type="button" size="sm" variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className="h-4 w-4" />
          Atualizar
        </Button>
      </div>

      {loading ? <p className="py-6 text-sm text-gray-500">Carregando verificações...</p> : null}
      {error ? <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">{error}</p> : null}
      {!loading && !error && items.length === 0 ? (
        <p className="rounded-xl border border-gray-200 bg-white p-4 text-sm text-gray-600">
          Nenhum envio documental aguardando análise.
        </p>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-2">
        {items.map((item) => {
          const busy = busyId === item.envioId
          return (
            <article key={item.envioId} className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="font-semibold text-gray-950">{item.nomeCivil}</p>
                  <p className="mt-1 text-sm text-gray-600">
                    {item.cpfMascarado} · Nascimento {item.dataNascimento}
                  </p>
                </div>
                <span className="rounded-full bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-800">
                  {item.status.replace('_', ' ')}
                </span>
              </div>

              <div className="mt-4 flex flex-wrap gap-2">
                {item.documentos.map((document) => (
                  <Button
                    key={document.id}
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => void viewDocument(document.id)}
                  >
                    <ExternalLink className="h-4 w-4" />
                    {documentLabel(document.parte)} · {sizeLabel(document.tamanhoBytes)}
                  </Button>
                ))}
              </div>

              <label className="mt-4 block text-sm font-medium text-gray-800">
                Motivo para rejeição ou ajuste
                <Textarea
                  className="mt-2 min-h-20"
                  maxLength={240}
                  value={reasons[item.envioId] || ''}
                  onChange={(event) => setReasons((current) => ({
                    ...current,
                    [item.envioId]: event.target.value,
                  }))}
                />
              </label>

              <div className="mt-4 flex flex-wrap gap-2">
                <Button type="button" size="sm" disabled={busy} onClick={() => void decide(item, 'APROVAR')}>
                  <FileCheck2 className="h-4 w-4" />
                  Aprovar
                </Button>
                <Button type="button" size="sm" variant="outline" disabled={busy} onClick={() => void decide(item, 'SOLICITAR_AJUSTE')}>
                  Solicitar ajuste
                </Button>
                <Button type="button" size="sm" variant="destructive" disabled={busy} onClick={() => void decide(item, 'REPROVAR')}>
                  Rejeitar
                </Button>
              </div>
            </article>
          )
        })}
      </div>
    </section>
  )
}
