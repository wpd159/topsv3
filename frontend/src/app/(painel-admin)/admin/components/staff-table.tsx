'use client'

import { useState } from 'react'

import { ContractState, PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export default function GerenciarStaffTable({ busca: _busca = '' }: { busca?: string }) {
  const [deleteOpen, setDeleteOpen] = useState(false)
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(PENDING_BACKEND_CONTRACTS.adminUsers)

  return <div className="space-y-3"><PendingActionFeedback attemptedAction={attemptedAction} /><div className="overflow-x-auto rounded-lg border border-gray-200 bg-white"><Table><TableHeader><TableRow><TableHead>Nome</TableHead><TableHead>E-mail</TableHead><TableHead>Cargo</TableHead><TableHead>Status</TableHead><TableHead>Ações</TableHead></TableRow></TableHeader><TableBody><TableRow><TableCell colSpan={5}><ContractState error={error} compact /><div className="mt-3 flex flex-wrap gap-2"><Button type="button" variant="outline" onClick={() => runPendingAction('Visualizar staff')}>Visualizar</Button><Button type="button" variant="outline" onClick={() => runPendingAction('Editar staff')}>Editar</Button><Button type="button" variant="outline" onClick={() => runPendingAction('Ativar ou desativar staff')}>Ativar/Desativar</Button><Button type="button" variant="destructive" onClick={() => setDeleteOpen(true)}>Excluir</Button></div></TableCell></TableRow></TableBody></Table></div><div className="flex justify-between"><Button type="button" variant="outline" onClick={() => runPendingAction('Página anterior de staff')}>Voltar</Button><Button type="button" variant="outline" onClick={() => runPendingAction('Próxima página de staff')}>Próximo</Button></div><Dialog open={deleteOpen} onOpenChange={setDeleteOpen}><DialogContent><DialogHeader><DialogTitle>Confirmar exclusão</DialogTitle><DialogDescription>Tem certeza que deseja excluir este membro? A ação não pode ser simulada.</DialogDescription></DialogHeader><PendingActionFeedback attemptedAction={attemptedAction} /><DialogFooter><Button type="button" variant="outline" onClick={() => setDeleteOpen(false)}>Cancelar</Button><Button type="button" variant="destructive" onClick={() => runPendingAction('Excluir staff')}>Confirmar exclusão</Button></DialogFooter></DialogContent></Dialog></div>
}
