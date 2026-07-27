'use client'

import { type ReactNode, useEffect, useState } from 'react'

import {
  ContractState,
  PendingActionFeedback,
  usePendingContractActions,
} from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'
import { VisitorDocuments } from './visitor-documents'
import { VisitorAgeLogs } from './visitor-age-logs'
import { VisitorRisk } from './visitor-risk'

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

function sectionFromHash(hash: string): SectionId {
  const candidate = hash.replace(/^#/, '')
  return SECTIONS.some((item) => item.id === candidate)
    ? candidate as SectionId
    : 'admin-logs'
}

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
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(
    PENDING_BACKEND_CONTRACTS.complianceAdmin
  )

  useEffect(() => {
    const syncSection = () => setSection(sectionFromHash(window.location.hash))
    syncSection()
    window.addEventListener('hashchange', syncSection)
    window.addEventListener('popstate', syncSection)
    return () => {
      window.removeEventListener('hashchange', syncSection)
      window.removeEventListener('popstate', syncSection)
    }
  }, [])

  function chooseSection(id: SectionId) {
    setSection(id)
    if (window.location.hash !== `#${id}`) {
      window.history.pushState(null, '', `#${id}`)
      window.dispatchEvent(new Event('hashchange'))
    }
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
          <PendingTable columns={['Ação', 'Entidade', 'Responsável', 'Contexto sanitizado', 'Data', 'Detalhes']} error={error} actions={<Button type="button" variant="outline" onClick={() => runPendingAction('Abrir detalhe de auditoria')}>Abrir detalhe</Button>} />
        </CardContent></Card>
      </div>

      <div id="legal-acceptances" hidden={section !== 'legal-acceptances'} className="space-y-4">
        <Card><CardHeader><CardTitle>Aceites jurídicos</CardTitle></CardHeader><CardContent className="space-y-4">
          <div className="grid gap-3 md:grid-cols-4"><Input placeholder="Usuário ou documento" /><Input placeholder="Versão" /><Select defaultValue="TODOS"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="TODOS">Todos os status</SelectItem><SelectItem value="ACEITO">Aceito</SelectItem><SelectItem value="REVOGADO">Revogado</SelectItem></SelectContent></Select><Button type="button" onClick={() => runPendingAction('Aplicar filtros de aceites')}>Aplicar filtros</Button></div>
          <PendingTable columns={['Usuário', 'Documento', 'Versão', 'Status', 'Origem sanitizada', 'Data']} error={error} />
          <div className="flex justify-between"><Button type="button" variant="outline" onClick={() => runPendingAction('Página anterior de aceites')}>Anterior</Button><Button type="button" variant="outline" onClick={() => runPendingAction('Próxima página de aceites')}>Próxima</Button></div>
        </CardContent></Card>
      </div>

      <div id="visitor-logs" hidden={section !== 'visitor-logs'} className="space-y-4">
        <VisitorAgeLogs />
      </div>

      <div id="visitor-document-fallback" hidden={section !== 'visitor-document-fallback'} className="space-y-4">
        <VisitorDocuments />
      </div>

      <div id="visitor-risk" hidden={section !== 'visitor-risk'} className="space-y-4">
        <VisitorRisk />
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

    </section>
  )
}
