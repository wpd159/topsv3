'use client'

import Link from 'next/link'

import {
  ContractState,
  PendingActionFeedback,
  usePendingContractActions,
} from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export default function ProgrammaticBlogPage() {
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(PENDING_BACKEND_CONTRACTS.blog)

  return (
    <section className="space-y-6">
      <div><Button asChild variant="ghost" className="px-0"><Link href="/admin/blog">Voltar aos posts</Link></Button><h1 className="text-2xl font-bold">Páginas programáticas (SEO)</h1></div>
      <ContractState error={error} />
      <PendingActionFeedback attemptedAction={attemptedAction} />

      <div className="space-y-4 rounded-lg border border-gray-200 bg-white p-5">
        <h2 className="font-semibold">Processar lote</h2>
        <div className="grid gap-3 md:grid-cols-4">
          <Select defaultValue="acompanhantes"><SelectTrigger aria-label="Tema do lote"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="acompanhantes">Acompanhantes</SelectItem><SelectItem value="massagens">Massagens</SelectItem><SelectItem value="sexo-virtual">Sexo virtual</SelectItem></SelectContent></Select>
          <Input placeholder="UF" maxLength={2} aria-label="UF do lote" />
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" /> Somente pendentes</label>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" /> Publicar após processar</label>
        </div>
        <Button type="button" onClick={() => runPendingAction('Executar lote programático')}>Executar lote</Button>
      </div>

      <div className="grid gap-3 rounded-lg border border-gray-200 bg-white p-4 md:grid-cols-4">
        <Input placeholder="Tema" />
        <Input placeholder="UF" maxLength={2} />
        <Select defaultValue="TODOS"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="TODOS">Todos</SelectItem><SelectItem value="SIM">Publicados</SelectItem><SelectItem value="NAO">Não publicados</SelectItem></SelectContent></Select>
        <Button type="button" variant="outline" onClick={() => runPendingAction('Aplicar filtros programáticos')}>Aplicar filtros</Button>
      </div>

      <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <Table>
          <TableHeader><TableRow><TableHead>Cidade</TableHead><TableHead>Tema</TableHead><TableHead>Título</TableHead><TableHead>Status</TableHead><TableHead>Ações</TableHead></TableRow></TableHeader>
          <TableBody><TableRow><TableCell colSpan={5}><ContractState error={error} compact /><div className="mt-3 flex flex-wrap gap-2"><Button type="button" variant="outline" onClick={() => runPendingAction('Visualizar página programática')}>Ver</Button><Button type="button" variant="outline" onClick={() => runPendingAction('Publicar página programática')}>Publicar/Despublicar</Button><Button type="button" variant="outline" onClick={() => runPendingAction('Reprocessar página programática')}>Reprocessar</Button></div></TableCell></TableRow></TableBody>
        </Table>
      </div>
    </section>
  )
}
