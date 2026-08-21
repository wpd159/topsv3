'use client'

import { useState } from 'react'
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
import type { AdminKycSubmission } from '@/features/admin-anuncios/types'
import { normalizeApiError } from '@/lib/api-contract'

import { AdminKycDocumentGrid } from './admin-kyc-document-grid'
import type { AdminKycDecision } from './api'
import { decideAdminKycSubmission } from './api'

export function AdminKycReviewGrid({
  submissions,
  emptyMessage,
  onReplace,
  onDecisionComplete,
}: {
  submissions: AdminKycSubmission[]
  emptyMessage?: string
  onReplace?: (submission: AdminKycSubmission) => void
  onDecisionComplete?: () => void | Promise<void>
}) {
  const [decisionIntent, setDecisionIntent] = useState<{
    submission: AdminKycSubmission
    decision: AdminKycDecision
  } | null>(null)
  const [decisionReason, setDecisionReason] = useState('')
  const [decisionError, setDecisionError] = useState<unknown>(null)
  const [decisionBusyId, setDecisionBusyId] = useState<string | null>(null)

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
      await onDecisionComplete?.()
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
  const decisionNeedsReason = decisionIntent !== null && decisionIntent.decision !== 'APROVAR'

  return (
    <>
      <AdminKycDocumentGrid
        submissions={submissions}
        emptyMessage={emptyMessage}
        onReplace={onReplace}
        decisionBusyId={decisionBusyId}
        onDecision={(submission, decision) => {
          setDecisionIntent({ submission, decision })
          setDecisionReason('')
          setDecisionError(null)
        }}
      />
      <Dialog open={decisionIntent !== null} onOpenChange={(open) => { if (!open) closeDecision() }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{decisionTitle}</DialogTitle>
            <DialogDescription>
              {decisionIntent?.decision === 'APROVAR'
                ? 'Todos os documentos deste envio serão marcados como validados. A situação do KYC será atualizada após a confirmação.'
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
    </>
  )
}
