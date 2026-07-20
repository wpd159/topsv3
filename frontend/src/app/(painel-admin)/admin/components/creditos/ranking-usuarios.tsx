'use client'

import { ContractState, pendingContractError } from '@/components/feedback/contract-state'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export default function RankingUsuarios() {
  return (
    <section className="mt-10 space-y-4 rounded-xl border border-gray-200 bg-white p-5">
      <div>
        <h3 className="font-semibold text-gray-900">Top usuarios com mais creditos</h3>
        <p className="text-sm text-gray-500">Ranking administrativo de saldo.</p>
      </div>
      <ContractState error={pendingContractError(PENDING_BACKEND_CONTRACTS.financialAnalytics)} compact />
      <div className="overflow-x-auto rounded-md border border-gray-200">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Posição</TableHead>
              <TableHead>Usuário</TableHead>
              <TableHead>Créditos</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow>
              <TableCell colSpan={3} className="py-5">
                <ContractState error={pendingContractError(PENDING_BACKEND_CONTRACTS.financialAnalytics)} compact />
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
    </section>
  )
}
