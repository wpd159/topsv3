'use client'

import { useState } from 'react'

import GerenciarAvisosTable from './avisos-table'
import { ContractState, PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export default function AdminAvisosPage() {
  const [status, setStatus] = useState('TODOS')
  const [createOpen, setCreateOpen] = useState(false)
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(PENDING_BACKEND_CONTRACTS.notices)

  return (
    <section className="space-y-6">
      <div className="flex flex-col justify-between gap-3 md:flex-row md:items-center"><div><h1 className="text-2xl font-bold">Avisos</h1><p className="text-sm text-gray-600">Criação, publicação e desativação de avisos.</p></div><Button type="button" onClick={() => setCreateOpen(true)}>Novo aviso</Button></div>
      <ContractState error={error} />
      <PendingActionFeedback attemptedAction={attemptedAction} />
      <div className="flex flex-wrap gap-2">{['TODOS', 'ATIVOS', 'AGENDADOS', 'INATIVOS'].map((item) => <Button key={item} type="button" variant={status === item ? 'default' : 'outline'} onClick={() => setStatus(item)}>{item}</Button>)}</div>
      <div className="grid gap-3 rounded-lg border border-gray-200 bg-white p-4 md:grid-cols-[1fr_240px_auto]"><Input placeholder="Buscar aviso" /><Select defaultValue="TODOS"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="TODOS">Todos os locais</SelectItem><SelectItem value="SITE">Site</SelectItem><SelectItem value="LOGIN_POPUP">Login</SelectItem><SelectItem value="ANUNCIO_RODAPE">Anúncio</SelectItem></SelectContent></Select><Button type="button" variant="outline" onClick={() => runPendingAction('Aplicar filtros de avisos')}>Aplicar filtros</Button></div>
      <GerenciarAvisosTable status={status} />
      <Dialog open={createOpen} onOpenChange={setCreateOpen}><DialogContent><DialogHeader><DialogTitle>Criar novo aviso</DialogTitle><DialogDescription>Defina o texto, o local de exibição e o comportamento do aviso.</DialogDescription></DialogHeader><div className="space-y-4"><div className="space-y-2"><Label htmlFor="notice-title">Título</Label><Input id="notice-title" /></div><div className="space-y-2"><Label htmlFor="notice-content">Conteúdo</Label><Textarea id="notice-content" rows={5} /></div><div className="space-y-2"><Label>Local de exibição</Label><Select defaultValue="SITE"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="SITE">Site</SelectItem><SelectItem value="LOGIN_POPUP">Login popup</SelectItem><SelectItem value="ANUNCIO_RODAPE">Rodapé do anúncio</SelectItem></SelectContent></Select></div><div className="grid grid-cols-2 gap-3"><div className="space-y-2"><Label htmlFor="notice-start">Início</Label><Input id="notice-start" type="datetime-local" /></div><div className="space-y-2"><Label htmlFor="notice-end">Fim</Label><Input id="notice-end" type="datetime-local" /></div></div><label className="flex items-center gap-2 text-sm"><input type="checkbox" /> Permitir dispensar</label><PendingActionFeedback attemptedAction={attemptedAction} /></div><DialogFooter><Button type="button" variant="outline" onClick={() => setCreateOpen(false)}>Cancelar</Button><Button type="button" onClick={() => runPendingAction('Criar aviso')}>Criar aviso</Button></DialogFooter></DialogContent></Dialog>
    </section>
  )
}
