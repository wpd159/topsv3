'use client'

import { type ReactNode, useState } from 'react'

import {
  ContractState,
  PendingActionFeedback,
  usePendingContractActions,
} from '@/components/feedback/contract-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'
import { VisitorAgeLogs } from './visitor-age-logs'

const SECTIONS = [
  { id: 'admin-logs', label: 'Auditoria administrativa' },
  { id: 'legal-acceptances', label: 'Aceites jurídicos' },
  { id: 'visitor-logs', label: 'Logs visitantes' },
  { id: 'visitor-document-fallback', label: 'Documentos visitantes' },
  { id: 'visitor-risk', label: 'Risco por sessão' },
  { id: 'critical-events', label: 'Eventos críticos' },
  { id: 'settings', label: 'Configurações' },
] as const

type SectionId = (typeof SECTIONS)[number]['id']
type ReviewAction = 'Aprovar documento' | 'Rejeitar documento' | null

function PendingTable({ columns, error, actions }: { columns: string[]; error: unknown; actions?: ReactNode }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-gray-200">
      <Table>
        <TableHeader><TableRow>{columns.map((column) => <TableHead key={column}>{column}</TableHead>)}</TableRow></TableHeader>
        <TableBody><TableRow><TableCell colSpan={columns.length}><ContractState error={error} compact />{actions ? <div className="mt-3">{actions}</div> : null}</TableCell></TableRow></TableBody>
      </Table>
    </div>
  )
}

export default function AdminCompliancePage() {
  const [section, setSection] = useState<SectionId>('admin-logs')
  const [reviewAction, setReviewAction] = useState<ReviewAction>(null)
  const [documentDetailOpen, setDocumentDetailOpen] = useState(false)
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(
    PENDING_BACKEND_CONTRACTS.complianceAdmin
  )

  function chooseSection(id: SectionId) {
    setSection(id)
    window.history.replaceState(null, '', `#${id}`)
  }

  return (
    <section className="space-y-6">
      <div><h1 className="text-2xl font-bold text-gray-900">Compliance</h1><p className="text-sm text-gray-600">Auditoria, aceites, visitantes, documentos e configurações.</p></div>
      <ContractState error={error} />
      <PendingActionFeedback attemptedAction={attemptedAction} />

      <nav className="flex flex-wrap gap-2" aria-label="Áreas de compliance">
        {SECTIONS.map((item) => <Button key={item.id} type="button" variant={section === item.id ? 'default' : 'outline'} onClick={() => chooseSection(item.id)}>{item.label}</Button>)}
      </nav>

      <div id="admin-logs" hidden={section !== 'admin-logs'} className="space-y-4">
        <Card><CardHeader><CardTitle>Auditoria administrativa</CardTitle></CardHeader><CardContent className="space-y-4">
          <div className="grid gap-3 md:grid-cols-5"><Input placeholder="Ação" /><Input placeholder="Entidade" /><Input placeholder="Responsável" /><Input type="date" aria-label="Data inicial" /><Input type="date" aria-label="Data final" /></div>
          <div className="flex flex-wrap gap-2"><Button type="button" onClick={() => runPendingAction('Aplicar filtros de auditoria')}>Aplicar filtros</Button><Button type="button" variant="outline" onClick={() => runPendingAction('Limpar filtros de auditoria')}>Limpar</Button><Button type="button" variant="outline" onClick={() => runPendingAction('Exportar CSV de auditoria')}>Exportar CSV</Button></div>
          <PendingTable columns={['Ação', 'Entidade', 'Responsável', 'IP', 'Data', 'Detalhes']} error={error} actions={<Button type="button" variant="outline" onClick={() => runPendingAction('Abrir detalhe de auditoria')}>Abrir detalhe</Button>} />
        </CardContent></Card>
      </div>

      <div id="legal-acceptances" hidden={section !== 'legal-acceptances'} className="space-y-4">
        <Card><CardHeader><CardTitle>Aceites jurídicos</CardTitle></CardHeader><CardContent className="space-y-4">
          <div className="grid gap-3 md:grid-cols-4"><Input placeholder="Usuário ou documento" /><Input placeholder="Versão" /><Select defaultValue="TODOS"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="TODOS">Todos os status</SelectItem><SelectItem value="ACEITO">Aceito</SelectItem><SelectItem value="REVOGADO">Revogado</SelectItem></SelectContent></Select><Button type="button" onClick={() => runPendingAction('Aplicar filtros de aceites')}>Aplicar filtros</Button></div>
          <PendingTable columns={['Usuário', 'Documento', 'Versão', 'Status', 'Origem', 'IP', 'User-agent', 'Data']} error={error} />
          <div className="flex justify-between"><Button type="button" variant="outline" onClick={() => runPendingAction('Página anterior de aceites')}>Anterior</Button><Button type="button" variant="outline" onClick={() => runPendingAction('Próxima página de aceites')}>Próxima</Button></div>
        </CardContent></Card>
      </div>

      <div id="visitor-logs" hidden={section !== 'visitor-logs'} className="space-y-4">
        <VisitorAgeLogs />
      </div>

      <div id="visitor-document-fallback" hidden={section !== 'visitor-document-fallback'} className="space-y-4">
        <Card><CardHeader><CardTitle className="flex items-center gap-2">Documentos visitantes <Badge variant="outline">Privado</Badge></CardTitle></CardHeader><CardContent className="space-y-4">
          <div className="grid gap-3 md:grid-cols-4"><Input placeholder="ID da sessão" /><Select defaultValue="TODOS"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="TODOS">Todos os status</SelectItem><SelectItem value="PENDENTE">Pendente</SelectItem><SelectItem value="APROVADO">Aprovado</SelectItem><SelectItem value="REJEITADO">Rejeitado</SelectItem></SelectContent></Select><Button type="button" onClick={() => runPendingAction('Filtrar documentos visitantes')}>Aplicar filtros</Button><Button type="button" variant="outline" onClick={() => runPendingAction('Abrir moderação documental')}>Abrir moderação</Button></div>
          <PendingTable columns={['ID', 'Sessão', 'Anúncio', 'Nível', 'Status', 'Arquivo', 'Data', 'Ações']} error={error} actions={<div className="flex flex-wrap gap-2"><Button type="button" variant="outline" onClick={() => setDocumentDetailOpen(true)}>Visualizar documento</Button><Button type="button" onClick={() => setReviewAction('Aprovar documento')}>Aprovar</Button><Button type="button" variant="destructive" onClick={() => setReviewAction('Rejeitar documento')}>Rejeitar</Button></div>} />
          <div className="rounded-md border border-gray-200 p-4"><h3 className="font-semibold">Histórico da decisão</h3><p className="mt-1 text-sm text-gray-600">Administrador, status, motivo, requestId e data permanecem visíveis quando o contrato estiver disponível.</p><ContractState error={error} compact /></div>
        </CardContent></Card>
      </div>

      <div id="visitor-risk" hidden={section !== 'visitor-risk'} className="space-y-4">
        <Card><CardHeader><CardTitle>Visão de risco por sessão</CardTitle></CardHeader><CardContent className="space-y-4">
          <p className="text-sm text-gray-600">Score atual, decisão vigente, bloqueios e trilha resumida.</p>
          <PendingTable columns={['Sessão', 'Score', 'Decisão', 'Falhas', 'Explícito', 'Último motivo', 'Última atividade']} error={error} actions={<Button type="button" variant="outline" onClick={() => runPendingAction('Consultar perfil de risco da sessão')}>Abrir</Button>} />
        </CardContent></Card>
      </div>

      <div id="critical-events" hidden={section !== 'critical-events'} className="space-y-4">
        <Card><CardHeader><CardTitle>Fila de eventos críticos</CardTitle></CardHeader><CardContent className="space-y-4">
          <p className="text-sm text-gray-600">Escalonamentos, bloqueios temporários e sessões sinalizadas.</p>
          <PendingTable columns={['Evento', 'Sessão', 'Score', 'Decisão', 'Motivo', 'Data']} error={error} actions={<Button type="button" variant="outline" onClick={() => runPendingAction('Abrir evento crítico')}>Abrir</Button>} />
        </CardContent></Card>
      </div>

      <div id="settings" hidden={section !== 'settings'} className="space-y-4">
        <Card><CardHeader><CardTitle>Configurações de compliance</CardTitle></CardHeader><CardContent className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2"><div className="space-y-2"><Label htmlFor="compliance-score">Score mínimo</Label><Input id="compliance-score" type="number" /></div><div className="space-y-2"><Label htmlFor="compliance-retention">Retenção em dias</Label><Input id="compliance-retention" type="number" /></div></div>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" /> Exigir decisão explícita</label>
          <Button type="button" onClick={() => runPendingAction('Salvar configurações de compliance')}>Salvar configurações</Button>
        </CardContent></Card>
      </div>

      <Dialog open={documentDetailOpen} onOpenChange={setDocumentDetailOpen}>
        <DialogContent><DialogHeader><DialogTitle>Visualização documental privada</DialogTitle><DialogDescription>A abertura administrativa usa acesso temporário e autorizado quando o contrato existir.</DialogDescription></DialogHeader><ContractState error={error} /><PendingActionFeedback attemptedAction={attemptedAction} /><DialogFooter><Button type="button" variant="outline" onClick={() => setDocumentDetailOpen(false)}>Fechar</Button><Button type="button" onClick={() => runPendingAction('Abrir documento por URL temporária')}>Abrir documento</Button></DialogFooter></DialogContent>
      </Dialog>

      <Dialog open={reviewAction !== null} onOpenChange={(open) => { if (!open) setReviewAction(null) }}>
        <DialogContent><DialogHeader><DialogTitle>{reviewAction}</DialogTitle><DialogDescription>Confirme a decisão administrativa. Nenhum estado será alterado sem o contrato V3.</DialogDescription></DialogHeader><div className="space-y-2"><Label htmlFor="review-reason">Motivo da decisão</Label><Textarea id="review-reason" required /></div><PendingActionFeedback attemptedAction={attemptedAction} /><DialogFooter><Button type="button" variant="outline" onClick={() => setReviewAction(null)}>Cancelar</Button><Button type="button" variant={reviewAction === 'Rejeitar documento' ? 'destructive' : 'default'} onClick={() => runPendingAction(reviewAction || 'Revisar documento')}>Confirmar decisão</Button></DialogFooter></DialogContent>
      </Dialog>
    </section>
  )
}
