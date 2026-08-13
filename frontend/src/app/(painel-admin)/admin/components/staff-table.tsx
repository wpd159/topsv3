'use client'

import Link from 'next/link'
import { useState } from 'react'
import { Eye, Pencil, Trash2 } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { deleteAdminStaff } from '@/features/admin-staff/api'
import type { AdminStaffSummary } from '@/features/admin-staff/types'

function formatDate(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

export default function GerenciarStaffTable({
  itens,
  retorno,
  canDelete,
  onRemoved,
}: {
  itens: AdminStaffSummary[]
  retorno: string
  canDelete: boolean
  onRemoved: () => void
}) {
  const [target, setTarget] = useState<AdminStaffSummary | null>(null)
  const [removing, setRemoving] = useState(false)
  const [removeError, setRemoveError] = useState<unknown>(null)

  async function removeTarget() {
    if (!target || removing) return
    setRemoving(true)
    setRemoveError(null)
    try {
      await deleteAdminStaff(target.id)
      setTarget(null)
      onRemoved()
    } catch (error) {
      setRemoveError(error)
    } finally {
      setRemoving(false)
    }
  }

  const href = (id: string, edit = false) => `/admin/staff/${encodeURIComponent(id)}${edit ? '/editar' : ''}${
    retorno ? `?retorno=${encodeURIComponent(retorno)}` : ''
  }`
  return (
    <>
      <div className="hidden overflow-x-auto border-y border-zinc-200 md:block [&_thead]:sticky [&_thead]:top-0 [&_thead]:z-10 [&_thead]:bg-white" role="region" aria-label="Tabela de staff" tabIndex={0}>
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
                    {canDelete && staff.ativo ? (
                      <Button type="button" size="icon" variant="destructive" title="Excluir staff" onClick={() => { setRemoveError(null); setTarget(staff) }}><Trash2 className="h-4 w-4" /><span className="sr-only">Excluir staff</span></Button>
                    ) : null}
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
            <div className="grid grid-cols-2 gap-2">
              <Button asChild size="sm" variant="outline"><Link href={href(staff.id)}><Eye className="mr-2 h-4 w-4" />Ver</Link></Button>
              <Button asChild size="sm" variant="outline"><Link href={href(staff.id, true)}><Pencil className="mr-2 h-4 w-4" />Editar</Link></Button>
              {canDelete && staff.ativo ? (
                <Button type="button" size="sm" variant="destructive" className="col-span-2" onClick={() => { setRemoveError(null); setTarget(staff) }}><Trash2 className="mr-2 h-4 w-4" />Excluir staff</Button>
              ) : null}
            </div>
          </article>
        ))}
      </div>

      <Dialog open={Boolean(target)} onOpenChange={(next) => { if (!next && !removing) setTarget(null) }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Excluir este staff?</DialogTitle>
            <DialogDescription>
              O acesso administrativo ser&aacute; removido. O hist&oacute;rico de auditoria ser&aacute; preservado.
            </DialogDescription>
          </DialogHeader>
          {target ? <p className="break-all text-sm font-medium text-zinc-900">{target.nome} &middot; {target.email}</p> : null}
          {removeError ? <ContractState error={removeError} compact /> : null}
          <DialogFooter>
            <Button type="button" variant="outline" disabled={removing} onClick={() => setTarget(null)}>Cancelar</Button>
            <Button type="button" variant="destructive" disabled={removing} onClick={() => void removeTarget()}>
              {removing ? 'Excluindo...' : 'Excluir staff'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
