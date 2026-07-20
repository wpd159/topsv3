'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useParams } from 'next/navigation'

import { ContractState, PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export default function StaffEditPage() {
  const params = useParams<{ id: string }>()
  const id = params?.id || ''
  const [showCredential, setShowCredential] = useState(false)
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(PENDING_BACKEND_CONTRACTS.adminUsers)
  return <section className="mx-auto max-w-3xl space-y-6"><div><Button asChild variant="ghost" className="px-0"><Link href={`/admin/staff/${encodeURIComponent(id)}`}>Voltar</Link></Button><h1 className="text-2xl font-bold">Editar membro da equipe</h1></div><ContractState error={error} /><PendingActionFeedback attemptedAction={attemptedAction} /><div className="grid gap-4 rounded-lg border border-gray-200 bg-white p-5 sm:grid-cols-2"><div className="space-y-2"><Label htmlFor="edit-staff-name">Nome completo</Label><Input id="edit-staff-name" /></div><div className="space-y-2"><Label htmlFor="edit-staff-user">Nome de usuário</Label><Input id="edit-staff-user" /></div><div className="space-y-2"><Label htmlFor="edit-staff-email">E-mail</Label><Input id="edit-staff-email" type="email" /></div><div className="space-y-2"><Label htmlFor="edit-staff-cpf">CPF</Label><Input id="edit-staff-cpf" /></div><fieldset className="space-y-2 sm:col-span-2"><legend className="font-medium">Cargo</legend><div className="flex flex-wrap gap-4">{['Administrador', 'Moderador', 'Suporte'].map((role) => <label key={role} className="flex items-center gap-2 text-sm"><input type="checkbox" /> {role}</label>)}</div></fieldset><div className="space-y-2 sm:col-span-2"><Label htmlFor="edit-staff-credential">Senha (opcional)</Label><div className="flex gap-2"><Input id="edit-staff-credential" type={showCredential ? 'text' : 'password'} placeholder="Se deixar em branco, a senha não muda" /><Button type="button" variant="outline" onClick={() => setShowCredential((value) => !value)}>{showCredential ? 'Ocultar' : 'Mostrar'} senha</Button></div></div><label className="flex items-center gap-2 text-sm"><input type="checkbox" /> Status ativo</label></div><div className="flex justify-end gap-2"><Button asChild variant="outline"><Link href={`/admin/staff/${encodeURIComponent(id)}`}>Cancelar</Link></Button><Button type="button" onClick={() => runPendingAction('Salvar alterações do staff')}>Salvar alterações</Button></div></section>
}
