'use client'

import { useState } from 'react'

import { ContractState, PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

type NovoStaffModalProps = { open: boolean; onOpenChange: (open: boolean) => void }

export default function NovoStaffModal({ open, onOpenChange }: NovoStaffModalProps) {
  const [showCredential, setShowCredential] = useState(false)
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(PENDING_BACKEND_CONTRACTS.adminUsers)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader><DialogTitle>Criar novo membro da equipe</DialogTitle><DialogDescription>Preencha os dados para adicionar um membro administrativo.</DialogDescription></DialogHeader>
        <ContractState error={error} compact />
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2"><Label htmlFor="staff-name">Nome completo</Label><Input id="staff-name" /></div>
          <div className="space-y-2"><Label htmlFor="staff-username">Nome de usuário</Label><Input id="staff-username" /></div>
          <div className="space-y-2"><Label htmlFor="staff-email">E-mail</Label><Input id="staff-email" type="email" /></div>
          <div className="space-y-2"><Label htmlFor="staff-cpf">CPF</Label><Input id="staff-cpf" /></div>
          <div className="space-y-2 sm:col-span-2"><Label htmlFor="staff-credential">Senha</Label><div className="flex gap-2"><Input id="staff-credential" type={showCredential ? 'text' : 'password'} /><Button type="button" variant="outline" onClick={() => setShowCredential((value) => !value)}>{showCredential ? 'Ocultar' : 'Mostrar'} senha</Button></div></div>
          <fieldset className="space-y-2 sm:col-span-2"><legend className="font-medium">Cargo</legend><div className="flex flex-wrap gap-4">{['Administrador', 'Moderador', 'Suporte'].map((role) => <label key={role} className="flex items-center gap-2 text-sm"><input type="checkbox" /> {role}</label>)}</div></fieldset>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" defaultChecked /> Status ativo</label>
          <Button type="button" variant="outline" onClick={() => runPendingAction('Verificar duplicidade do staff')}>Verificar duplicidade</Button>
        </div>
        <PendingActionFeedback attemptedAction={attemptedAction} />
        <DialogFooter><Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Cancelar</Button><Button type="button" onClick={() => runPendingAction('Criar membro da equipe')}>Criar membro</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
