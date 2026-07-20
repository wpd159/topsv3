'use client'

import { ContractState, PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

type TicketDetailsModalProps = { open: boolean; onOpenChange: (value: boolean) => void; ticket: unknown; onOpenChat?: () => void }

export default function TicketDetailsModal({ open, onOpenChange, ticket, onOpenChat }: TicketDetailsModalProps) {
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(PENDING_BACKEND_CONTRACTS.support)
  if (!ticket) return null
  return <Dialog open={open} onOpenChange={onOpenChange}><DialogContent className="sm:max-w-lg"><DialogHeader><DialogTitle>Detalhes do ticket</DialogTitle><DialogDescription>Solicitante, motivo, descrição, status e datas.</DialogDescription></DialogHeader><ContractState error={error} compact /><div className="grid gap-3 sm:grid-cols-2">{['Solicitante', 'Motivo', 'Status', 'Criado em', 'Atualizado em', 'Descrição'].map((field) => <div key={field} className="rounded-md border p-3"><p className="text-xs uppercase text-gray-500">{field}</p><p className="text-sm">Dado indisponível</p></div>)}</div><PendingActionFeedback attemptedAction={attemptedAction} /><DialogFooter><Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Fechar</Button><Button type="button" variant="outline" onClick={() => { onOpenChat?.(); runPendingAction('Abrir conversa do ticket') }}>Abrir chat</Button><Button type="button" variant="destructive" onClick={() => runPendingAction('Encerrar ticket')}>Encerrar ticket</Button></DialogFooter></DialogContent></Dialog>
}
