'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'

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
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export default function AdminUserEditPage() {
  const params = useParams<{ id: string }>()
  const id = params?.id || ''
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(
    PENDING_BACKEND_CONTRACTS.adminUsers
  )

  return (
    <section className="mx-auto max-w-4xl space-y-6">
      <div className="flex items-center justify-between gap-4">
        <div>
          <Button asChild variant="ghost" className="px-0"><Link href={`/admin/usuarios/${encodeURIComponent(id)}`}>Voltar ao usuário</Link></Button>
          <h1 className="text-2xl font-bold text-gray-900">Editar usuário</h1>
        </div>
        <div className="flex gap-2">
          <Button asChild variant="outline"><Link href={`/admin/usuarios/${encodeURIComponent(id)}`}>Cancelar</Link></Button>
          <Button type="button" onClick={() => runPendingAction('Salvar alterações do usuário')}>Salvar alterações</Button>
        </div>
      </div>

      <ContractState error={error} />
      <PendingActionFeedback attemptedAction={attemptedAction} />

      <Card>
        <CardHeader><CardTitle>Dados cadastrais</CardTitle></CardHeader>
        <CardContent className="grid gap-5 sm:grid-cols-2">
          <div className="space-y-2"><Label htmlFor="full-name">Nome completo</Label><Input id="full-name" placeholder="Carregamento depende do contrato V3" /></div>
          <div className="space-y-2"><Label htmlFor="username">Nome de usuário</Label><Input id="username" placeholder="Carregamento depende do contrato V3" /></div>
          <div className="space-y-2"><Label htmlFor="email">E-mail</Label><Input id="email" type="email" placeholder="Carregamento depende do contrato V3" /></div>
          <div className="space-y-2"><Label htmlFor="phone">Telefone</Label><Input id="phone" placeholder="Carregamento depende do contrato V3" /></div>
          <div className="space-y-2"><Label htmlFor="cpf">CPF</Label><Input id="cpf" placeholder="Carregamento depende do contrato V3" /></div>
          <div className="space-y-2"><Label htmlFor="birth-date">Data de nascimento</Label><Input id="birth-date" type="date" /></div>
          <div className="space-y-2"><Label>Status</Label><Select defaultValue="indisponivel"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="indisponivel">Status indisponível</SelectItem><SelectItem value="ativo">Ativo</SelectItem><SelectItem value="inativo">Inativo</SelectItem></SelectContent></Select></div>
          <div className="space-y-2"><Label>Tipo de conta</Label><Select defaultValue="indisponivel"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="indisponivel">Tipo indisponível</SelectItem><SelectItem value="usuario">Usuário</SelectItem><SelectItem value="anunciante">Anunciante</SelectItem></SelectContent></Select></div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>Documentos</CardTitle></CardHeader>
        <CardContent className="space-y-3">
          <p className="text-sm text-gray-600">Os documentos privados permanecem disponíveis para gestão quando o contrato administrativo estiver disponível.</p>
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="outline" onClick={() => runPendingAction('Ver documento do usuário')}>Ver</Button>
            <Button type="button" onClick={() => runPendingAction('Adicionar novo documento ao usuário')}>Novo documento</Button>
          </div>
        </CardContent>
      </Card>
    </section>
  )
}
