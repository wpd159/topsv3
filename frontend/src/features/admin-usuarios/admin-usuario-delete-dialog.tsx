'use client'

import { useEffect, useRef, useState } from 'react'
import { Trash2 } from 'lucide-react'

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
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { normalizeApiError } from '@/lib/api-contract'

import {
  AdminUserDeletionError,
  deleteAdminUser,
  getAdminUserDeletionEligibility,
} from './api'
import type { AdminUserDeletionEligibility } from './types'

const BLOCKER_LABELS: Record<string, string> = {
  CONTA_STAFF: 'Conta administrativa ou de sistema',
  USUARIO_IMPORTADO: 'Conta proveniente de importação',
  POSSUI_ANUNCIOS: 'Possui anúncios vinculados',
  POSSUI_DOCUMENTOS_KYC: 'Possui documentos ou submissão KYC',
  POSSUI_SALDO_OU_LEDGER: 'Possui saldo ou histórico de créditos',
  POSSUI_PAGAMENTOS: 'Possui pagamentos ou conciliações',
  POSSUI_HISTORICO_OPERACIONAL: 'Possui histórico operacional que deve ser preservado',
}

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  usuarioId: string
  nome: string
  onSuccess: () => void | Promise<void>
}

export function AdminUsuarioDeleteDialog({
  open,
  onOpenChange,
  usuarioId,
  nome,
  onSuccess,
}: Props) {
  const [eligibility, setEligibility] = useState<AdminUserDeletionEligibility | null>(null)
  const [confirmation, setConfirmation] = useState('')
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const lock = useRef(false)
  const idempotencyKey = useRef('')

  useEffect(() => {
    if (!open) {
      setEligibility(null)
      setConfirmation('')
      setError(null)
      idempotencyKey.current = ''
      return
    }
    if (!idempotencyKey.current) {
      idempotencyKey.current = crypto.randomUUID()
    }
    let active = true
    setLoading(true)
    setError(null)
    getAdminUserDeletionEligibility(usuarioId)
      .then((result) => {
        if (active) setEligibility(result)
      })
      .catch((reason) => {
        if (active) setError(normalizeApiError(reason))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [open, usuarioId])

  async function confirmDelete() {
    if (!eligibility?.podeExcluir || confirmation !== 'EXCLUIR' || lock.current) return
    lock.current = true
    setBusy(true)
    setError(null)
    try {
      await deleteAdminUser(usuarioId, idempotencyKey.current)
      await onSuccess()
      onOpenChange(false)
    } catch (reason) {
      if (reason instanceof AdminUserDeletionError) {
        setEligibility({ podeExcluir: false, bloqueios: reason.blockers })
        setError(reason)
      } else {
        setError(normalizeApiError(reason))
      }
    } finally {
      lock.current = false
      setBusy(false)
    }
  }

  const blockers = eligibility?.bloqueios ?? []

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!busy) onOpenChange(next) }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Excluir usuário definitivamente</DialogTitle>
          <DialogDescription>
            Esta ação é irreversível e só será concluída se a conta não possuir anúncios,
            documentos, saldo, pagamentos ou outros vínculos que precisem ser preservados.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <p className="text-sm text-zinc-700">
            Conta selecionada: <strong>{nome}</strong>
          </p>
          {loading ? <p className="text-sm text-zinc-500">Verificando dependências...</p> : null}
          {blockers.length > 0 ? (
            <div className="border-l-4 border-amber-500 bg-amber-50 px-4 py-3">
              <p className="text-sm font-semibold text-amber-900">A exclusão está bloqueada:</p>
              <ul className="mt-2 space-y-1 text-sm text-amber-900">
                {blockers.map((blocker) => (
                  <li key={blocker}>{BLOCKER_LABELS[blocker] || 'Vínculo protegido encontrado'}</li>
                ))}
              </ul>
            </div>
          ) : null}
          {eligibility?.podeExcluir ? (
            <div className="space-y-2">
              <Label htmlFor="admin-user-delete-confirmation">
                Digite EXCLUIR para confirmar
              </Label>
              <Input
                id="admin-user-delete-confirmation"
                value={confirmation}
                onChange={(event) => setConfirmation(event.target.value)}
                autoComplete="off"
                disabled={busy}
              />
            </div>
          ) : null}
          {error ? <ContractState error={error} compact /> : null}
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" disabled={busy} onClick={() => onOpenChange(false)}>
            Cancelar
          </Button>
          <Button
            type="button"
            variant="destructive"
            disabled={!eligibility?.podeExcluir || confirmation !== 'EXCLUIR' || busy}
            onClick={() => void confirmDelete()}
          >
            <Trash2 className="mr-2 h-4 w-4" />
            {busy ? 'Excluindo...' : 'Excluir usuário'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
