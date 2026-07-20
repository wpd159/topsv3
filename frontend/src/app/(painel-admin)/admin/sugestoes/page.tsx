'use client'

import { useState } from 'react'

import SugestoesTable from '../components/sugestoes-table'
import { ContractState, usePendingContractActions } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export default function AdminSugestoesPage() {
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('TODAS')
  const { error, runPendingAction } = usePendingContractActions(PENDING_BACKEND_CONTRACTS.suggestions)
  return <section className="space-y-6"><div><h1 className="text-2xl font-bold">Sugestões</h1><p className="text-sm text-gray-600">Triagem e acompanhamento das sugestões recebidas.</p></div><ContractState error={error} /><div className="grid gap-3 rounded-lg border bg-white p-4 md:grid-cols-[1fr_220px_auto]"><Input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar sugestão" /><Select value={status} onValueChange={setStatus}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="TODAS">Todos os status</SelectItem><SelectItem value="PENDENTE">Pendente</SelectItem><SelectItem value="EM_ANALISE">Em análise</SelectItem><SelectItem value="CONCLUIDA">Concluída</SelectItem></SelectContent></Select><Button type="button" variant="outline" onClick={() => runPendingAction('Aplicar filtros de sugestões')}>Aplicar filtros</Button></div><SugestoesTable busca={search} status={status} /></section>
}
