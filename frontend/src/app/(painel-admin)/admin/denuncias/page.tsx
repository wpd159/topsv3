'use client'

import { useState } from 'react'

import { ContractState, PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export default function AdminReportsPage() {
  const [punishOpen, setPunishOpen] = useState(false)
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(PENDING_BACKEND_CONTRACTS.reports)
  return <section className="space-y-6"><div><h1 className="text-2xl font-bold">Denúncias</h1><p className="text-sm text-gray-600">Análise, decisão e histórico das denúncias.</p></div><ContractState error={error} /><PendingActionFeedback attemptedAction={attemptedAction} /><div className="grid gap-3 rounded-lg border bg-white p-4 md:grid-cols-4"><Input placeholder="Buscar denúncia ou anúncio" /><Select defaultValue="TODOS"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="TODOS">Todos os status</SelectItem><SelectItem value="PENDENTE">Pendente</SelectItem><SelectItem value="PUNIDA">Punida</SelectItem><SelectItem value="IGNORADA">Ignorada</SelectItem></SelectContent></Select><Input type="date" aria-label="Data da denúncia" /><Button type="button" variant="outline" onClick={() => runPendingAction('Filtrar denúncias')}>Aplicar filtros</Button></div><div className="overflow-x-auto rounded-lg border bg-white"><Table><TableHeader><TableRow><TableHead>Anúncio</TableHead><TableHead>Motivo</TableHead><TableHead>Denunciante</TableHead><TableHead>Status</TableHead><TableHead>Data</TableHead><TableHead>Ações</TableHead></TableRow></TableHeader><TableBody><TableRow><TableCell colSpan={6}><ContractState error={error} compact /><div className="mt-3 flex flex-wrap gap-2"><Button type="button" variant="outline" onClick={() => runPendingAction('Ver anúncio denunciado')}>Ver anúncio</Button><Button type="button" variant="destructive" onClick={() => setPunishOpen(true)}>Punir (Excluir anúncio)</Button><Button type="button" variant="outline" onClick={() => runPendingAction('Não punir denúncia')}>Não punir</Button></div></TableCell></TableRow></TableBody></Table></div><Dialog open={punishOpen} onOpenChange={setPunishOpen}><DialogContent><DialogHeader><DialogTitle>Punir anúncio</DialogTitle><DialogDescription>Você está prestes a excluir o anúncio denunciado. Informe a justificativa enviada ao anunciante.</DialogDescription></DialogHeader><div className="space-y-2"><Label htmlFor="report-reason">Justificativa</Label><Textarea id="report-reason" rows={5} /></div><PendingActionFeedback attemptedAction={attemptedAction} /><DialogFooter><Button type="button" variant="outline" onClick={() => setPunishOpen(false)}>Cancelar</Button><Button type="button" variant="destructive" onClick={() => runPendingAction('Confirmar punição do anúncio')}>Punir anúncio</Button></DialogFooter></DialogContent></Dialog></section>
}
