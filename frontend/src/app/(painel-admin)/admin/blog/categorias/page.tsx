'use client'

import { useState } from 'react'
import Link from 'next/link'

import {
  ContractState,
  PendingActionFeedback,
  usePendingContractActions,
} from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export default function BlogCategoriesPage() {
  const [editing, setEditing] = useState(false)
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(
    PENDING_BACKEND_CONTRACTS.blog
  )

  return (
    <section className="space-y-6">
      <div>
        <Button asChild variant="ghost" className="px-0"><Link href="/admin/blog">Voltar ao blog</Link></Button>
        <h1 className="text-2xl font-bold text-gray-900">Categorias do blog</h1>
      </div>
      <ContractState error={error} />
      <PendingActionFeedback attemptedAction={attemptedAction} />

      <form className="grid gap-4 rounded-lg border border-gray-200 bg-white p-5 md:grid-cols-4" onSubmit={(event) => { event.preventDefault(); runPendingAction(editing ? 'Salvar categoria' : 'Criar categoria') }}>
        <div className="space-y-2"><Label htmlFor="category-name">Nome</Label><Input id="category-name" placeholder="Ex.: Guias e dicas" /></div>
        <div className="space-y-2"><Label htmlFor="category-slug">Slug (opcional)</Label><Input id="category-slug" placeholder="Derivado do nome se vazio" /></div>
        <div className="space-y-2"><Label htmlFor="category-order">Ordem</Label><Input id="category-order" type="number" min={0} /></div>
        <label className="flex items-center gap-2 self-end pb-3 text-sm"><input type="checkbox" defaultChecked /> Ativa (aparece na listagem pública)</label>
        <div className="flex flex-wrap gap-2 md:col-span-4">
          <Button type="submit">{editing ? 'Salvar alterações' : 'Nova categoria'}</Button>
          {editing ? <Button type="button" variant="outline" onClick={() => setEditing(false)}>Cancelar edição</Button> : null}
        </div>
      </form>

      <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <Table>
          <TableHeader><TableRow><TableHead>Nome</TableHead><TableHead>Slug</TableHead><TableHead>Ordem</TableHead><TableHead>Posts publicados</TableHead><TableHead>Ativa</TableHead><TableHead>Ações</TableHead></TableRow></TableHeader>
          <TableBody><TableRow><TableCell colSpan={6}><ContractState error={error} compact /><div className="mt-3 flex gap-2"><Button type="button" variant="outline" onClick={() => setEditing(true)}>Editar</Button><Button type="button" variant="destructive" onClick={() => runPendingAction('Excluir categoria')}>Excluir</Button></div></TableCell></TableRow></TableBody>
        </Table>
      </div>
    </section>
  )
}
