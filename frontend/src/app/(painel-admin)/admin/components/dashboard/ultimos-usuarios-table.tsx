'use client'

import Link from 'next/link'

import { ContractState, PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

type Props = {
  variant?: 'default' | 'compact'
}

export default function UltimosUsuariosTable({ variant = 'default' }: Props) {
  const compact = variant === 'compact'
  const { error: pending, attemptedAction, runPendingAction } = usePendingContractActions(
    PENDING_BACKEND_CONTRACTS.adminUsers
  )

  return (
    <div className={`overflow-hidden rounded-2xl border border-gray-200/80 bg-white shadow-sm ${compact ? '' : 'mt-10'}`}>
      <div className={`border-b px-5 py-4 ${compact ? 'bg-gray-50/80' : 'bg-gradient-to-r from-[#FC1EAD]/10 to-transparent'}`}>
        <h3 className="text-base font-semibold text-gray-800">Ultimos usuarios cadastrados</h3>
        <p className="mt-1 text-xs text-gray-500">A listagem permanece disponivel e aguarda o contrato V3.</p>
      </div>
      <div className="hidden overflow-x-auto md:block">
        <Table>
          <TableHeader>
            <TableRow className="border-b bg-gray-50/60 text-[11px] uppercase tracking-wider text-gray-500">
              <TableHead>Nome</TableHead>
              <TableHead>Email</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Data</TableHead>
              <TableHead>Acoes</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow>
              <TableCell colSpan={5} className="p-4">
                <ContractState error={pending} compact />
                <div className="mt-3 flex flex-wrap gap-2">
                  <Button type="button" variant="outline" size="sm" onClick={() => runPendingAction('Ver usuário recente')}>Ver usuário</Button>
                  <Button type="button" variant="outline" size="sm" onClick={() => runPendingAction('Editar usuário recente')}>Editar</Button>
                  <Button type="button" variant="outline" size="sm" onClick={() => runPendingAction('Ativar ou desativar usuário recente')}>
                    Ativar/Desativar
                  </Button>
                  <PendingActionFeedback attemptedAction={attemptedAction} />
                </div>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
      <div className="space-y-3 p-4 md:hidden">
        <ContractState error={pending} compact />
      </div>
      <div className="flex justify-end border-t border-gray-100 p-4">
        <Button asChild variant="outline" size="sm">
          <Link href="/admin/usuarios">Gerenciar usuarios</Link>
        </Button>
      </div>
    </div>
  )
}
