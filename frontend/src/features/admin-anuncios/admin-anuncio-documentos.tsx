'use client'

import { useCallback, useEffect, useState } from 'react'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'

import { ContractState } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import type { AdminKycDecision } from '@/features/admin-documentos/api'
import { decideAdminKycSubmission } from '@/features/admin-documentos/api'
import { AdminKycDocumentGrid } from '@/features/admin-documentos/admin-kyc-document-grid'
import { normalizeApiError } from '@/lib/api-contract'

import { listAdminAdDocuments } from './api'
import type { AdminAdvertiserDetail, AdminKycSubmission } from './types'

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
  const [error, setError] = useState<unknown>(null)
  const [decisionIntent, setDecisionIntent] = useState<{
    submission: AdminKycSubmission
    decision: AdminKycDecision
  } | null>(null)
  const [decisionReason, setDecisionReason] = useState('')
  const [decisionError, setDecisionError] = useState<unknown>(null)
  const [decisionBusyId, setDecisionBusyId] = useState<string | null>(null)

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

  function closeDecision() {
    if (decisionBusyId) return
    setDecisionIntent(null)
    setDecisionReason('')
    setDecisionError(null)
  }

  async function confirmDecision() {
    if (!decisionIntent || decisionBusyId) return
    const reason = decisionReason.trim()
    if (decisionIntent.decision !== 'APROVAR' && reason.length < 3) {
      setDecisionError(new Error('Informe um motivo com pelo menos 3 caracteres.'))
      return
    }
    setDecisionBusyId(decisionIntent.submission.envioId)
    setDecisionError(null)
    try {
      await decideAdminKycSubmission(
        decisionIntent.submission.envioId,
        decisionIntent.decision,
        reason,
      )
      toast.success(decisionIntent.decision === 'APROVAR'
        ? 'Documentos validados com sucesso.'
        : 'Decisão documental registrada com sucesso.')
      setDecisionIntent(null)
      setDecisionReason('')
      await load()
    } catch (reasonError) {
      setDecisionError(normalizeApiError(reasonError))
    } finally {
      setDecisionBusyId(null)
    }
  }

  const decisionTitle = decisionIntent?.decision === 'APROVAR'
    ? 'Validar documentos'
    : decisionIntent?.decision === 'SOLICITAR_AJUSTE'
      ? 'Solicitar ajuste dos documentos'
      : 'Rejeitar documentos'
  const decisionNeedsReason = decisionIntent?.decision !== 'APROVAR'

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
          <div>
            <h3 className="font-semibold text-zinc-950">Documentos privados</h3>
            <p className="text-xs text-zinc-500">Miniaturas e visualização integral usam o mesmo acesso privado.</p>
          </div>
          <Button type="button" size="sm" variant="outline" onClick={() => void load()} disabled={loading}>
            Atualizar
          </Button>
        </div>
        {error ? <div className="mt-4"><ContractState error={error} onRetry={() => void load()} compact /></div> : null}
        {loading ? (
          <p className="mt-5 flex items-center gap-2 text-sm text-zinc-500">
            <Loader2 className="h-4 w-4 animate-spin" />Carregando documentos...
          </p>
        ) : (
          <div className="mt-4">
            <AdminKycDocumentGrid
              submissions={submissions}
              emptyMessage="Nenhum envio documental disponível."
              decisionBusyId={decisionBusyId}
              onDecision={(submission, decision) => {
                setDecisionIntent({ submission, decision })
                setDecisionReason('')
                setDecisionError(null)
              }}
            />
          </div>
        )}
      </div>
      <Dialog open={decisionIntent !== null} onOpenChange={(open) => { if (!open) closeDecision() }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{decisionTitle}</DialogTitle>
            <DialogDescription>
              {decisionIntent?.decision === 'APROVAR'
                ? 'Todos os documentos deste envio serão marcados como validados. O anúncio poderá ser aprovado quando os demais requisitos também estiverem atendidos.'
                : 'A decisão será aplicada a todos os documentos deste envio e ficará registrada no histórico administrativo.'}
            </DialogDescription>
          </DialogHeader>
          {decisionNeedsReason ? (
            <div className="space-y-2">
              <Label htmlFor="admin-kyc-decision-reason">Motivo</Label>
              <Textarea
                id="admin-kyc-decision-reason"
                value={decisionReason}
                onChange={(event) => setDecisionReason(event.target.value)}
                maxLength={240}
                disabled={Boolean(decisionBusyId)}
              />
            </div>
          ) : null}
          {decisionError ? <ContractState error={decisionError} compact /> : null}
          <DialogFooter>
            <Button type="button" variant="outline" disabled={Boolean(decisionBusyId)} onClick={closeDecision}>
              Cancelar
            </Button>
            <Button
              type="button"
              variant={decisionIntent?.decision === 'REPROVAR' ? 'destructive' : 'default'}
              disabled={Boolean(decisionBusyId) || (decisionNeedsReason && decisionReason.trim().length < 3)}
              onClick={() => void confirmDecision()}
            >
              {decisionBusyId ? 'Processando...' : 'Confirmar decisão'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
