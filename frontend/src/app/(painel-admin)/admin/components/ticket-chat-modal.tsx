'use client'

import { useState } from 'react'

import { ContractState, PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

interface TicketChatModalProps { open: boolean; onOpenChange: (value: boolean) => void; ticket: unknown }

export default function TicketChatModal({ open, onOpenChange, ticket }: TicketChatModalProps) {
  const [message, setMessage] = useState('')
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(PENDING_BACKEND_CONTRACTS.support)
  if (!ticket) return null
  return <Dialog open={open} onOpenChange={onOpenChange}><DialogContent className="sm:max-w-2xl"><DialogHeader><DialogTitle>Conversa completa do ticket</DialogTitle><DialogDescription>Histórico entre usuário e atendimento.</DialogDescription></DialogHeader><ContractState error={error} compact /><div className="h-64 overflow-y-auto rounded-md border border-gray-200 bg-gray-50 p-4"><p className="text-sm text-gray-600">As mensagens não foram carregadas porque o contrato backend está ausente.</p></div><Textarea value={message} onChange={(event) => setMessage(event.target.value)} placeholder="Digite sua resposta" /><PendingActionFeedback attemptedAction={attemptedAction} /><DialogFooter><Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Fechar chat</Button><Button type="button" onClick={() => runPendingAction('Enviar mensagem no ticket')}>Responder</Button><Button type="button" variant="destructive" onClick={() => runPendingAction('Encerrar ticket')}>Encerrar ticket</Button></DialogFooter></DialogContent></Dialog>
}
