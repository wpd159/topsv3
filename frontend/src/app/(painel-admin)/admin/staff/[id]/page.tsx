'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'

import { ContractState, PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export default function StaffDetailPage() {
  const params = useParams<{ id: string }>()
  const id = params?.id || ''
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(PENDING_BACKEND_CONTRACTS.adminUsers)
  return <section className="space-y-6"><div><Button asChild variant="ghost" className="px-0"><Link href="/admin/staff">Voltar ao staff</Link></Button><h1 className="text-2xl font-bold">Detalhe do staff</h1><p className="text-xs text-gray-500">ID técnico: {id || 'indisponível'}</p></div><ContractState error={error} /><PendingActionFeedback attemptedAction={attemptedAction} /><Card><CardHeader><CardTitle>Dados do membro</CardTitle></CardHeader><CardContent className="grid gap-4 sm:grid-cols-2">{['Nome completo', 'Nome de usuário', 'E-mail', 'CPF', 'Cargo', 'Status', 'Criado em', 'Último acesso'].map((field) => <div key={field} className="rounded-md border border-gray-100 p-3"><p className="text-xs uppercase text-gray-500">{field}</p><p className="text-sm">Dado indisponível pelo contrato.</p></div>)}</CardContent></Card><div className="flex flex-wrap gap-2"><Button asChild><Link href={`/admin/staff/${encodeURIComponent(id)}/editar`}>Editar</Link></Button><Button type="button" variant="outline" onClick={() => runPendingAction('Ativar ou desativar staff')}>Ativar/Desativar</Button><Button type="button" variant="destructive" onClick={() => runPendingAction('Excluir staff')}>Excluir</Button></div></section>
}
