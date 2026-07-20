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

export default function AdminBlogPage() {
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(
    PENDING_BACKEND_CONTRACTS.blog
  )

  return (
    <section className="space-y-6">
      <div className="flex flex-col justify-between gap-3 lg:flex-row lg:items-center">
        <div><h1 className="text-2xl font-bold text-gray-900">Blog</h1><p className="text-sm text-gray-600">Posts, categorias e páginas programáticas.</p></div>
        <div className="flex flex-wrap gap-2">
          <Button asChild variant="outline"><Link href="/admin/blog/categorias">Categorias do blog</Link></Button>
          <Button asChild variant="outline"><Link href="/admin/blog/programatico">Páginas programáticas (SEO)</Link></Button>
          <Button asChild><Link href="/admin/blog/novo">Novo post</Link></Button>
        </div>
      </div>
      <ContractState error={error} />
      <PendingActionFeedback attemptedAction={attemptedAction} />

      <div className="grid gap-3 rounded-lg border border-gray-200 bg-white p-4 md:grid-cols-[1fr_220px_auto]">
        <Input placeholder="Buscar por título, autor ou slug" />
        <Select defaultValue="TODOS"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="TODOS">Todos os status</SelectItem><SelectItem value="RASCUNHO">Rascunho</SelectItem><SelectItem value="PUBLICADO">Publicado</SelectItem><SelectItem value="ARQUIVADO">Arquivado</SelectItem></SelectContent></Select>
        <Button type="button" variant="outline" onClick={() => runPendingAction('Aplicar filtros do blog')}>Aplicar filtros</Button>
      </div>

      <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <Table>
          <TableHeader><TableRow><TableHead>Título</TableHead><TableHead>Categoria</TableHead><TableHead>Autor</TableHead><TableHead>Status</TableHead><TableHead>Atualização</TableHead><TableHead>Ações</TableHead></TableRow></TableHeader>
          <TableBody><TableRow><TableCell colSpan={6}><ContractState error={error} compact /><div className="mt-3 flex flex-wrap gap-2"><Button type="button" variant="outline" onClick={() => runPendingAction('Ver post')}>Ver</Button><Button type="button" variant="outline" onClick={() => runPendingAction('Editar post')}>Editar</Button><Button type="button" variant="outline" onClick={() => runPendingAction('Publicar post')}>Publicar</Button><Button type="button" variant="destructive" onClick={() => runPendingAction('Excluir post')}>Excluir</Button></div></TableCell></TableRow></TableBody>
        </Table>
      </div>
    </section>
  )
}
