'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useParams } from 'next/navigation'

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

type Confirmation = 'status' | 'two-factor' | 'delete' | null

const DETAIL_FIELDS = [
  'Nome completo',
  'Nome de usuário',
  'E-mail',
  'Telefone',
  'CPF',
  'Data de nascimento',
  'Localização',
  'Cadastro',
]

export default function AdminUserDetailPage() {
  const params = useParams<{ id: string }>()
  const userId = params?.id || ''
  const [creditDialog, setCreditDialog] = useState(false)
  const [confirmation, setConfirmation] = useState<Confirmation>(null)
  const [ticketDialog, setTicketDialog] = useState(false)
  const [adQuery, setAdQuery] = useState('')
  const [adStatus, setAdStatus] = useState('todos')
  const [adBenefit, setAdBenefit] = useState('todos')
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(
    PENDING_BACKEND_CONTRACTS.adminUsers
  )

  return (
    <section className="space-y-6">
      <div className="flex flex-col justify-between gap-4 xl:flex-row xl:items-start">
        <div className="space-y-2">
          <Button asChild variant="ghost" className="px-0"><Link href="/admin/usuarios">Voltar aos usuários</Link></Button>
          <h1 className="text-2xl font-bold text-gray-900">Detalhe do usuário</h1>
          <div className="flex flex-wrap items-center gap-2 text-sm text-gray-600">
            <span>ID técnico: {userId || 'indisponível'}</span>
            <Badge variant="outline">Status indisponível</Badge>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="button" onClick={() => setCreditDialog(true)}>Adicionar crédito</Button>
          <Button type="button" variant="outline" onClick={() => document.getElementById('anuncios-do-usuario')?.scrollIntoView({ behavior: 'smooth', block: 'start' })}>Ver anúncios</Button>
          <Button asChild variant="outline"><Link href={`/admin/usuarios/${encodeURIComponent(userId)}/editar`}>Editar</Link></Button>
          <Button asChild variant="outline"><Link href={`/admin/registros?usuarioId=${encodeURIComponent(userId)}`}>Ver logs e registros</Link></Button>
          <Button type="button" variant="outline" onClick={() => setConfirmation('status')}>Ativar/Desativar</Button>
          <Button type="button" variant="outline" onClick={() => setConfirmation('two-factor')}>Remover 2FA</Button>
          <Button type="button" variant="destructive" onClick={() => setConfirmation('delete')}>Excluir usuário</Button>
        </div>
      </div>

      <ContractState error={error} />
      <PendingActionFeedback attemptedAction={attemptedAction} />

      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader><CardTitle>Dados cadastrais</CardTitle></CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            {DETAIL_FIELDS.map((field) => (
              <div key={field} className="rounded-md border border-gray-100 bg-gray-50 p-3">
                <p className="text-xs font-medium uppercase text-gray-500">{field}</p>
                <p className="mt-1 text-sm text-gray-700">Dado indisponível pelo contrato administrativo.</p>
              </div>
            ))}
            <div className="sm:col-span-2 flex flex-wrap gap-2">
              <Button type="button" variant="outline" onClick={() => runPendingAction('Abrir WhatsApp')}>Abrir WhatsApp</Button>
              <Button type="button" variant="outline" onClick={() => setTicketDialog(true)}>Abrir ticket</Button>
            </div>
          </CardContent>
        </Card>

        <div className="space-y-5">
          <Card>
            <CardHeader><CardTitle>Resumo</CardTitle></CardHeader>
            <CardContent className="space-y-3">
              {['Anúncios ativos', 'Créditos atuais', 'Tickets', 'Indicações'].map((label) => (
                <button key={label} type="button" className="w-full rounded-md border border-gray-100 p-3 text-left" onClick={() => runPendingAction(`Consultar ${label}`)}>
                  <span className="block text-sm font-medium text-gray-800">{label}</span>
                  <span className="text-xs text-gray-500">Contagem indisponível</span>
                </button>
              ))}
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle>Benefícios</CardTitle></CardHeader>
            <CardContent className="space-y-2">
              {['OCULTAR_IDADE', 'FOTOS_EXTRA_5', 'ANUNCIO_TOPO', 'WHATSAPP_CARD'].map((benefit) => (
                <button key={benefit} type="button" className="w-full rounded-md border border-gray-100 px-3 py-2 text-left text-sm" onClick={() => runPendingAction(`Filtrar anúncios por ${benefit}`)}>
                  {benefit} <span className="text-gray-500">- estado indisponível</span>
                </button>
              ))}
            </CardContent>
          </Card>
        </div>
      </div>

      <Card id="anuncios-do-usuario">
        <CardHeader>
          <CardTitle>Anúncios do usuário</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-3 md:grid-cols-3">
            <Input
              value={adQuery}
              onChange={(event) => setAdQuery(event.target.value)}
              placeholder="Buscar anúncio"
              aria-label="Buscar anúncio do usuário"
            />
            <Select value={adStatus} onValueChange={setAdStatus}>
              <SelectTrigger aria-label="Filtrar anúncios por status"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="todos">Todos os status</SelectItem>
                <SelectItem value="ativo">Ativo</SelectItem>
                <SelectItem value="pendente">Pendente</SelectItem>
                <SelectItem value="pausado">Pausado</SelectItem>
                <SelectItem value="rejeitado">Rejeitado</SelectItem>
              </SelectContent>
            </Select>
            <Select value={adBenefit} onValueChange={setAdBenefit}>
              <SelectTrigger aria-label="Filtrar anúncios por benefício"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="todos">Todos os benefícios</SelectItem>
                <SelectItem value="ocultar-idade">OCULTAR_IDADE</SelectItem>
                <SelectItem value="fotos-extra">FOTOS_EXTRA_5</SelectItem>
                <SelectItem value="topo">ANUNCIO_TOPO</SelectItem>
                <SelectItem value="whatsapp">WHATSAPP_CARD</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="flex justify-end">
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                setAdQuery('')
                setAdStatus('todos')
                setAdBenefit('todos')
              }}
            >
              Limpar filtro
            </Button>
          </div>
          <div className="overflow-x-auto rounded-md border border-gray-200">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Título</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Benefícios</TableHead>
                  <TableHead>Data</TableHead>
                  <TableHead>Ações</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow>
                  <TableCell colSpan={5} className="py-5">
                    <ContractState error={error} compact />
                    <Button type="button" variant="outline" className="mt-3" onClick={() => runPendingAction('Ver anúncio do usuário')}>Ver</Button>
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>Documentos do usuário</CardTitle></CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-gray-600">A gestão documental permanece privada e depende do contrato administrativo correspondente.</p>
          <ContractState error={error} compact />
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="outline" onClick={() => runPendingAction('Gerenciar documentos do usuário')}>Gerenciar documentos</Button>
            <Button type="button" variant="outline" onClick={() => runPendingAction('Visualizar documento do usuário')}>Visualizar</Button>
            <Button type="button" variant="outline" onClick={() => runPendingAction('Abrir documento em nova aba')}>Abrir em nova aba</Button>
            <Button type="button" variant="outline" onClick={() => runPendingAction('Baixar documento do usuário')}>Baixar</Button>
            <Button type="button" onClick={() => runPendingAction('Adicionar novo documento ao usuário')}>Novo documento</Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex-row items-center justify-between">
          <CardTitle>Tickets relacionados</CardTitle>
          <Button asChild variant="outline"><Link href={`/admin/tickets?usuarioId=${encodeURIComponent(userId)}`}>Abrir central de tickets</Link></Button>
        </CardHeader>
        <CardContent>
          <ContractState error={error} compact />
          <Button type="button" className="mt-3" variant="outline" onClick={() => setTicketDialog(true)}>Ver conversa completa</Button>
        </CardContent>
      </Card>

      <Dialog open={creditDialog} onOpenChange={setCreditDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Ajustar créditos</DialogTitle>
            <DialogDescription>Escolha se deseja adicionar ou remover créditos e informe o motivo.</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2"><Label>Operação</Label><Select defaultValue="adicionar"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="adicionar">Adicionar</SelectItem><SelectItem value="remover">Remover</SelectItem></SelectContent></Select></div>
            <div className="space-y-2"><Label htmlFor="credit-amount">Quantidade</Label><Input id="credit-amount" type="number" min={1} /></div>
            <div className="space-y-2"><Label htmlFor="credit-reason">Motivo</Label><Textarea id="credit-reason" /></div>
            <PendingActionFeedback attemptedAction={attemptedAction} />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setCreditDialog(false)}>Cancelar</Button>
            <Button type="button" onClick={() => runPendingAction('Confirmar ajuste de créditos')}>Confirmar ajuste</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={confirmation !== null} onOpenChange={(open) => { if (!open) setConfirmation(null) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{confirmation === 'delete' ? 'Excluir usuário' : confirmation === 'two-factor' ? 'Remover 2FA' : 'Alterar status'}</DialogTitle>
            <DialogDescription>
              {confirmation === 'delete'
                ? 'Confirme a exclusão administrativa. Nenhuma alteração será simulada.'
                : confirmation === 'two-factor'
                  ? 'A senha e os demais dados seriam preservados.'
                  : 'Confirme a ativação ou desativação da conta.'}
            </DialogDescription>
          </DialogHeader>
          <PendingActionFeedback attemptedAction={attemptedAction} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setConfirmation(null)}>Cancelar</Button>
            <Button type="button" variant={confirmation === 'delete' ? 'destructive' : 'default'} onClick={() => runPendingAction(confirmation === 'delete' ? 'Excluir usuário' : confirmation === 'two-factor' ? 'Remover 2FA' : 'Alterar status do usuário')}>Confirmar</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={ticketDialog} onOpenChange={setTicketDialog}>
        <DialogContent>
          <DialogHeader><DialogTitle>Conversa completa</DialogTitle><DialogDescription>Histórico e resposta do ticket relacionado.</DialogDescription></DialogHeader>
          <ContractState error={error} compact />
          <Textarea placeholder="Digite uma resposta" />
          <PendingActionFeedback attemptedAction={attemptedAction} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setTicketDialog(false)}>Fechar</Button>
            <Button type="button" onClick={() => runPendingAction('Responder ticket')}>Responder</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}
