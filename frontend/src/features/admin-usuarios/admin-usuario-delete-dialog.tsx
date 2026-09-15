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
import { Textarea } from '@/components/ui/textarea'
import { revalidarCacheCatalogoPublico } from '@/app/(painel-admin)/admin/anuncios/actions'
import { getAdminAd } from '@/features/admin-anuncios/api'
import { normalizeApiError } from '@/lib/api-contract'
import { enviarIndexNowNoCliente, montarEventoIndexNowAnuncios } from '@/lib/seo/indexnow-client'

import {
  AdminUserDeletionError,
  deleteAdminUser,
  getAdminUser,
  getAdminUserDeletionEligibility,
} from './api'
import type { AdminUserDeletionEligibility, AdminUserDeletionResult } from './types'

const BLOCKER_LABELS: Record<string, string> = {
  CONTA_STAFF: 'Conta administrativa ou de sistema',
  OPERACAO_CONCORRENTE: 'Existe uma operação concorrente em andamento',
  CONTA_JA_EXCLUIDA: 'A conta já foi excluída',
}

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  usuarioId: string
  nome: string
  onSuccess: (result: AdminUserDeletionResult) => void | Promise<void>
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
  const [reason, setReason] = useState('')
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const lock = useRef(false)
  const idempotencyKey = useRef('')

  useEffect(() => {
    if (!open) {
      setEligibility(null)
      setConfirmation('')
      setReason('')
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
    if (
      !eligibility?.podeExcluir
      || confirmation !== 'EXCLUIR'
      || reason.trim().length < 5
      || lock.current
    ) return
    lock.current = true
    setBusy(true)
    setError(null)
    try {
      const eventFingerprint = idempotencyKey.current
      // Capture os vinculos publicos antes que a exclusao os torne indisponiveis.
      const publicAds = await getAdminUser(usuarioId)
        .then((user) => Promise.allSettled(
          user.anuncios
            .filter((anuncio) => anuncio.status === 'PUBLICADO')
            .map((anuncio) => getAdminAd(anuncio.id))
        ))
        .catch(() => [])
      const result = await deleteAdminUser(usuarioId, reason.trim(), eventFingerprint)
      await revalidarCacheCatalogoPublico().catch(() => {
        // A falha auxiliar de cache nao altera o resultado da exclusao.
      })
      void Promise.resolve().then(() => {
        const previous = publicAds.flatMap((entry) => entry.status === 'fulfilled' ? [{
          slug: entry.value.slug,
          estadoUf: entry.value.localizacao?.uf,
          cidadeNome: entry.value.localizacao?.cidade,
          bairroNome: entry.value.localizacao?.bairro,
        }] : [])
        if (previous.length > 0) {
          void enviarIndexNowNoCliente(montarEventoIndexNowAnuncios({
            eventType: 'RETIRADA',
            previous,
            changeFingerprint: eventFingerprint,
          }))
        }
      }).catch(() => {
        // A exclusao ja foi concluida; a notificacao permanece best-effort.
      })
      await onSuccess(result)
      onOpenChange(false)
    } catch (reason) {
      if (reason instanceof AdminUserDeletionError) {
        setEligibility((current) => current
          ? { ...current, podeExcluir: false, bloqueios: reason.blockers }
          : null)
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
  const anonymized = eligibility?.tipoExclusao === 'EXCLUSAO_COM_ANONIMIZACAO'

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!busy) onOpenChange(next) }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Excluir conta</DialogTitle>
          <DialogDescription>
            {anonymized
              ? 'A conta será encerrada e os dados pessoais serão anonimizados. Registros financeiros, auditorias e históricos serão preservados sem identificação pessoal.'
              : 'Esta conta será excluída definitivamente.'}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <p className="text-sm text-zinc-700">
            Conta selecionada: <strong>{nome}</strong>
          </p>
          {loading ? <p className="text-sm text-zinc-500">Verificando vínculos...</p> : null}
          {eligibility?.podeExcluir ? (
            <div className="border-l-4 border-zinc-400 bg-zinc-50 px-4 py-3 text-sm text-zinc-700">
              <p>E-mail, CPF e telefone serão liberados para um novo cadastro.</p>
              <p className="mt-1">A operação é irreversível e a nova conta não herdará dados ou saldo.</p>
              {anonymized ? (
                <p className="mt-1">
                  {eligibility.vinculosPreservados} vínculo(s) histórico(s) serão preservados.
                </p>
              ) : null}
            </div>
          ) : null}
          {blockers.length > 0 ? (
            <div className="border-l-4 border-amber-500 bg-amber-50 px-4 py-3">
              <p className="text-sm font-semibold text-amber-900">A exclusão está bloqueada:</p>
              <ul className="mt-2 space-y-1 text-sm text-amber-900">
                {blockers.map((blocker) => (
                  <li key={blocker}>{BLOCKER_LABELS[blocker] || 'Falha de integridade encontrada'}</li>
                ))}
              </ul>
            </div>
          ) : null}
          {eligibility?.podeExcluir ? (
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="admin-user-delete-reason">Motivo</Label>
                <Textarea
                  id="admin-user-delete-reason"
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  maxLength={500}
                  disabled={busy}
                  placeholder="Informe o motivo administrativo"
                />
              </div>
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
            disabled={
              !eligibility?.podeExcluir
              || confirmation !== 'EXCLUIR'
              || reason.trim().length < 5
              || busy
            }
            onClick={() => void confirmDelete()}
          >
            <Trash2 className="mr-2 h-4 w-4" />
            {busy ? 'Excluindo...' : 'Excluir conta'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
