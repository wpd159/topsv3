'use client'

import { useState } from 'react'

import NovoStaffModal from '../components/novo-staff-modal'
import GerenciarStaffTable from '../components/staff-table'
import { ContractState, usePendingContractActions } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export default function AdminStaffPage() {
  const [search, setSearch] = useState('')
  const [newOpen, setNewOpen] = useState(false)
  const { error, runPendingAction } = usePendingContractActions(PENDING_BACKEND_CONTRACTS.adminUsers)
  return <section className="space-y-6"><div className="flex justify-between gap-4"><div><h1 className="text-2xl font-bold">Gerenciar staff</h1><p className="text-sm text-gray-600">Contas administrativas, papéis e estados.</p></div><Button type="button" onClick={() => setNewOpen(true)}>Novo staff</Button></div><ContractState error={error} /><div className="flex gap-2 rounded-lg border bg-white p-4"><Input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar por nome, e-mail ou cargo" /><Button type="button" variant="outline" onClick={() => runPendingAction('Buscar staff')}>Buscar</Button></div><GerenciarStaffTable busca={search} /><NovoStaffModal open={newOpen} onOpenChange={setNewOpen} /></section>
}
