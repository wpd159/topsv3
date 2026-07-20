'use client'

import { useState } from 'react'

import { ContractState, PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export default function IndicacoesAdminPage() {
  const [configOpen, setConfigOpen] = useState(false)
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(PENDING_BACKEND_CONTRACTS.referrals)
  return <section className="space-y-6"><div className="flex justify-between gap-4"><div><h1 className="text-2xl font-bold">Indicações</h1><p className="text-sm text-gray-600">Resumo, ranking e créditos por indicação.</p></div><Button type="button" onClick={() => setConfigOpen(true)}>Editar créditos por indicação</Button></div><ContractState error={error} /><PendingActionFeedback attemptedAction={attemptedAction} /><div className="grid gap-4 md:grid-cols-3">{['Total de indicações', 'Usuários indicados', 'Créditos concedidos'].map((label) => <Card key={label}><CardHeader><CardTitle className="text-sm">{label}</CardTitle></CardHeader><CardContent><p className="font-semibold">Contagem indisponível</p><Button type="button" variant="link" className="px-0" onClick={() => runPendingAction(`Consultar ${label}`)}>Consultar resumo</Button></CardContent></Card>)}</div><div className="grid gap-3 rounded-lg border bg-white p-4 md:grid-cols-[1fr_auto]"><Input placeholder="Buscar por nome ou username" /><Button type="button" variant="outline" onClick={() => runPendingAction('Filtrar ranking de indicações')}>Aplicar filtro</Button></div><div className="overflow-x-auto rounded-lg border bg-white"><Table><TableHeader><TableRow><TableHead>Nome completo</TableHead><TableHead>Username</TableHead><TableHead>Indicações</TableHead><TableHead>Créditos</TableHead><TableHead>Ações</TableHead></TableRow></TableHeader><TableBody><TableRow><TableCell colSpan={5}><ContractState error={error} compact /><Button type="button" variant="outline" className="mt-3" onClick={() => runPendingAction('Consultar usuário indicado')}>Ver usuário</Button></TableCell></TableRow></TableBody></Table></div><Dialog open={configOpen} onOpenChange={setConfigOpen}><DialogContent><DialogHeader><DialogTitle>Editar créditos por indicação</DialogTitle><DialogDescription>Defina o valor concedido por indicação válida.</DialogDescription></DialogHeader><div className="space-y-2"><Label htmlFor="referral-credit">Novo valor em créditos</Label><Input id="referral-credit" type="number" min={0} /></div><PendingActionFeedback attemptedAction={attemptedAction} /><DialogFooter><Button type="button" variant="outline" onClick={() => setConfigOpen(false)}>Cancelar</Button><Button type="button" onClick={() => runPendingAction('Salvar créditos por indicação')}>Salvar</Button></DialogFooter></DialogContent></Dialog></section>
}
