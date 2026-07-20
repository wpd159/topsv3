'use client'

import { useState } from 'react'

import {
  ContractState,
  PendingActionFeedback,
  usePendingContractActions,
} from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

const STATUS = ['Todos', 'Ativos', 'Inativos', 'Pendentes', 'Bloqueados']
const TIPOS = ['Todos os tipos', 'Anunciante', 'Usuário', 'Administrador', 'Moderador']
const UFS = ['Todas as UFs', 'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO']

export default function AdminUsersPage() {
  const [status, setStatus] = useState('Todos')
  const [creditDialog, setCreditDialog] = useState(false)
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(
    PENDING_BACKEND_CONTRACTS.adminUsers
  )

  return (
    <section className="space-y-6">
      <div className="flex flex-col justify-between gap-4 lg:flex-row lg:items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Usuários</h1>
          <p className="mt-1 text-sm text-gray-600">Busca, filtros e gestão administrativa de usuários.</p>
        </div>
        <Button type="button" onClick={() => runPendingAction('Adicionar usuário')}>
          Adicionar usuário
        </Button>
      </div>

      <ContractState error={error} />
      <PendingActionFeedback attemptedAction={attemptedAction} />

      <div className="flex flex-wrap gap-2" aria-label="Status dos usuários">
        {STATUS.map((item) => (
          <Button
            key={item}
            type="button"
            variant={status === item ? 'default' : 'outline'}
            onClick={() => {
              setStatus(item)
              runPendingAction(`Filtrar por ${item}`)
            }}
          >
            {item}
          </Button>
        ))}
      </div>

      <div className="grid gap-3 rounded-lg border border-gray-200 bg-white p-4 md:grid-cols-2 xl:grid-cols-5">
        <Input placeholder="Buscar por nome, username ou e-mail" aria-label="Buscar usuário" />
        <Select defaultValue="Todas as UFs" onValueChange={() => runPendingAction('Filtrar por UF')}>
          <SelectTrigger aria-label="Filtrar por UF"><SelectValue /></SelectTrigger>
          <SelectContent>{UFS.map((uf) => <SelectItem key={uf} value={uf}>{uf}</SelectItem>)}</SelectContent>
        </Select>
        <Input placeholder="Cidade" aria-label="Filtrar por cidade" />
        <Select defaultValue="Todos os tipos" onValueChange={() => runPendingAction('Filtrar por tipo')}>
          <SelectTrigger aria-label="Filtrar por tipo"><SelectValue /></SelectTrigger>
          <SelectContent>{TIPOS.map((tipo) => <SelectItem key={tipo} value={tipo}>{tipo}</SelectItem>)}</SelectContent>
        </Select>
        <div className="flex gap-2">
          <Button type="button" variant="outline" onClick={() => runPendingAction('Aplicar filtros')}>Aplicar</Button>
          <Button type="button" variant="ghost" onClick={() => runPendingAction('Limpar filtros')}>Limpar</Button>
        </div>
      </div>

      <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Usuário</TableHead>
              <TableHead>Local</TableHead>
              <TableHead>Anúncios</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Tipo</TableHead>
              <TableHead>Ações</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow>
              <TableCell colSpan={6} className="py-6">
                <ContractState error={error} compact />
                <div className="mt-3 flex flex-wrap gap-2">
                  <Button type="button" variant="outline" onClick={() => runPendingAction('Visualizar usuário')}>Visualizar</Button>
                  <Button type="button" variant="outline" onClick={() => runPendingAction('Editar usuário')}>Editar</Button>
                  <Button type="button" variant="outline" onClick={() => runPendingAction('Ativar ou desativar usuário')}>Ativar/Desativar</Button>
                  <Button type="button" variant="outline" onClick={() => setCreditDialog(true)}>Crédito</Button>
                  <Button type="button" variant="outline" onClick={() => runPendingAction('Abrir detalhes do usuário')}>Abrir detalhes</Button>
                </div>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>

      <div className="flex items-center justify-between">
        <Button type="button" variant="outline" onClick={() => runPendingAction('Página anterior')}>Anterior</Button>
        <span className="text-sm text-gray-600">Contagem indisponível</span>
        <Button type="button" variant="outline" onClick={() => runPendingAction('Próxima página')}>Próxima</Button>
      </div>

      <Dialog open={creditDialog} onOpenChange={setCreditDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Ajustar créditos</DialogTitle>
            <DialogDescription>
              Escolha se deseja adicionar ou remover créditos e registre o motivo.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>Operação</Label>
              <Select defaultValue="adicionar">
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="adicionar">Adicionar</SelectItem>
                  <SelectItem value="remover">Remover</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="users-credit-amount">Quantidade</Label>
              <Input id="users-credit-amount" type="number" min={1} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="users-credit-reason">Motivo</Label>
              <Textarea id="users-credit-reason" />
            </div>
            <PendingActionFeedback attemptedAction={attemptedAction} />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setCreditDialog(false)}>Fechar</Button>
            <Button type="button" onClick={() => runPendingAction('Confirmar ajuste de créditos')}>Confirmar ajuste</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}
