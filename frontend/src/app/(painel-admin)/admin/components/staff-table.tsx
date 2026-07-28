'use client'

import Link from 'next/link'
import { Eye, Pencil } from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { AdminStaffSummary } from '@/features/admin-staff/types'

function formatDate(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

export default function GerenciarStaffTable({ itens, retorno }: { itens: AdminStaffSummary[]; retorno: string }) {
  const href = (id: string, edit = false) => `/admin/staff/${encodeURIComponent(id)}${edit ? '/editar' : ''}${
    retorno ? `?retorno=${encodeURIComponent(retorno)}` : ''
  }`
  return (
    <>
      <div className="hidden overflow-x-auto border-y border-zinc-200 md:block">
        <Table>
          <TableHeader><TableRow><TableHead>Nome</TableHead><TableHead>E-mail</TableHead><TableHead>Papel</TableHead><TableHead>Estado</TableHead><TableHead>Acesso</TableHead><TableHead>Cadastro</TableHead><TableHead className="text-right">Ações</TableHead></TableRow></TableHeader>
          <TableBody>
            {itens.map((staff) => (
              <TableRow key={staff.id}>
                <TableCell className="font-semibold">{staff.nome}</TableCell>
                <TableCell>{staff.email}</TableCell>
                <TableCell><Badge variant="outline">{staff.papelRotulo}</Badge></TableCell>
                <TableCell><Badge variant={staff.ativo ? 'default' : 'secondary'}>{staff.statusRotulo}</Badge></TableCell>
                <TableCell>{staff.acessoPendente ? 'Definição pendente' : 'Configurado'}</TableCell>
                <TableCell className="whitespace-nowrap">{formatDate(staff.criadoEm)}</TableCell>
                <TableCell>
                  <div className="flex justify-end gap-2">
                    <Button asChild size="icon" variant="outline" title="Visualizar staff"><Link href={href(staff.id)}><Eye className="h-4 w-4" /></Link></Button>
                    <Button asChild size="icon" variant="outline" title="Editar staff"><Link href={href(staff.id, true)}><Pencil className="h-4 w-4" /></Link></Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      <div className="divide-y divide-zinc-200 border-y border-zinc-200 md:hidden">
        {itens.map((staff) => (
          <article key={staff.id} className="space-y-3 py-4">
            <div>
              <p className="font-semibold text-zinc-950">{staff.nome}</p>
              <p className="break-all text-sm text-zinc-600">{staff.email}</p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Badge variant="outline">{staff.papelRotulo}</Badge>
              <Badge variant={staff.ativo ? 'default' : 'secondary'}>{staff.statusRotulo}</Badge>
              {staff.acessoPendente ? <Badge variant="outline">Acesso pendente</Badge> : null}
            </div>
            <div className="flex gap-2">
              <Button asChild size="sm" variant="outline"><Link href={href(staff.id)}><Eye className="mr-2 h-4 w-4" />Ver</Link></Button>
              <Button asChild size="sm" variant="outline"><Link href={href(staff.id, true)}><Pencil className="mr-2 h-4 w-4" />Editar</Link></Button>
            </div>
          </article>
        ))}
      </div>
    </>
  )
}
