'use client'

import { ContractState, PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export default function UltimosAnunciosPendentesTable() {
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(PENDING_BACKEND_CONTRACTS.adminAnalytics)
  return <div className="overflow-hidden rounded-lg border bg-white"><div className="border-b p-4"><h3 className="font-semibold">Últimos anúncios pendentes</h3><p className="text-xs text-gray-500">Fila recente para moderação.</p></div><Table><TableHeader><TableRow><TableHead>Título</TableHead><TableHead>Usuário</TableHead><TableHead>Status</TableHead><TableHead>Data</TableHead><TableHead>Ações</TableHead></TableRow></TableHeader><TableBody><TableRow><TableCell colSpan={5}><ContractState error={error} compact /><PendingActionFeedback attemptedAction={attemptedAction} /><Button type="button" variant="outline" className="mt-3" onClick={() => runPendingAction('Ver anúncio pendente')}>Ver anúncio</Button></TableCell></TableRow></TableBody></Table></div>
}
