'use client'

import { useState } from 'react'

import TicketChatModal from '../components/ticket-chat-modal'
import TicketDetailsModal from '../components/ticket-details-modal'
import { ContractState, PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export default function AdminTicketsPage() {
  const [detailsOpen, setDetailsOpen] = useState(false)
  const [chatOpen, setChatOpen] = useState(false)
  const [ticket] = useState<Record<string, never>>({})
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(PENDING_BACKEND_CONTRACTS.support)
  return <section className="space-y-6"><div className="flex justify-between gap-4"><div><h1 className="text-2xl font-bold">Tickets</h1><p className="text-sm text-gray-600">Fila, detalhes e chat de atendimento.</p></div><Button type="button" variant="outline" onClick={() => runPendingAction('Atualizar tickets')}>Atualizar</Button></div><ContractState error={error} /><PendingActionFeedback attemptedAction={attemptedAction} /><div className="flex flex-wrap gap-2">{['Todos', 'Abertos', 'Em andamento', 'Fechados'].map((status) => <Button key={status} type="button" variant="outline" onClick={() => runPendingAction(`Filtrar tickets: ${status}`)}>{status} (contagem indisponível)</Button>)}</div><div className="grid gap-3 rounded-lg border bg-white p-4 md:grid-cols-4"><Input placeholder="Buscar ticket ou usuário" /><Select defaultValue="TODOS"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="TODOS">Todos os motivos</SelectItem><SelectItem value="ERRO">Erro no sistema</SelectItem><SelectItem value="PAGAMENTO">Pagamento</SelectItem><SelectItem value="CONTA">Conta</SelectItem></SelectContent></Select><Select defaultValue="RECENTES"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="RECENTES">Mais recentes</SelectItem><SelectItem value="ANTIGOS">Mais antigos</SelectItem></SelectContent></Select><Button type="button" variant="outline" onClick={() => runPendingAction('Limpar filtros extras')}>Limpar filtros</Button></div><div className="overflow-x-auto rounded-lg border bg-white"><Table><TableHeader><TableRow><TableHead>Ticket</TableHead><TableHead>Usuário</TableHead><TableHead>Motivo</TableHead><TableHead>Status</TableHead><TableHead>Atualização</TableHead><TableHead>Ações</TableHead></TableRow></TableHeader><TableBody><TableRow><TableCell colSpan={6}><ContractState error={error} compact /><div className="mt-3 flex flex-wrap gap-2"><Button type="button" onClick={() => setChatOpen(true)}>Responder</Button><Button type="button" variant="outline" onClick={() => setDetailsOpen(true)}>Ver detalhes</Button><Button type="button" variant="destructive" onClick={() => runPendingAction('Fechar ticket')}>Fechar</Button></div></TableCell></TableRow></TableBody></Table></div><TicketDetailsModal open={detailsOpen} onOpenChange={setDetailsOpen} ticket={ticket} onOpenChat={() => setChatOpen(true)} /><TicketChatModal open={chatOpen} onOpenChange={setChatOpen} ticket={ticket} /></section>
}
