'use client'

import {
  ContractState,
  PendingActionFeedback,
  usePendingContractActions,
} from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

type Props = { busca?: string; status?: string; localExibicao?: string }

export default function GerenciarAvisosTable({ busca: _busca = '', status: _status = 'TODOS', localExibicao: _localExibicao = 'TODOS' }: Props) {
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(PENDING_BACKEND_CONTRACTS.notices)

  return (
    <div className="space-y-3">
      <PendingActionFeedback attemptedAction={attemptedAction} />
      <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <Table>
          <TableHeader><TableRow><TableHead>Título</TableHead><TableHead>Criado por</TableHead><TableHead>Local</TableHead><TableHead>Janela</TableHead><TableHead>Status</TableHead><TableHead>Ações</TableHead></TableRow></TableHeader>
          <TableBody><TableRow><TableCell colSpan={6}><ContractState error={error} compact /><div className="mt-3 flex flex-wrap gap-2"><Button type="button" variant="outline" onClick={() => runPendingAction('Editar aviso')}>Editar</Button><Button type="button" variant="outline" onClick={() => runPendingAction('Publicar aviso')}>Publicar</Button><Button type="button" variant="outline" onClick={() => runPendingAction('Desativar aviso')}>Desativar</Button></div></TableCell></TableRow></TableBody>
        </Table>
      </div>
      <div className="flex justify-between"><Button type="button" variant="outline" onClick={() => runPendingAction('Página anterior de avisos')}>Voltar</Button><Button type="button" variant="outline" onClick={() => runPendingAction('Próxima página de avisos')}>Próximo</Button></div>
    </div>
  )
}
