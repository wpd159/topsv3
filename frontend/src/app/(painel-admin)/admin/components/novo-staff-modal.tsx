'use client'

import { useEffect, useState } from 'react'
import { Loader2, ShieldCheck } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { createAdminStaff } from '@/features/admin-staff/api'

type NovoStaffModalProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  onCreated: () => void
}

export default function NovoStaffModal({ open, onOpenChange, onCreated }: NovoStaffModalProps) {
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [papel, setPapel] = useState<'ADMIN' | 'MODERADOR'>('MODERADOR')
  const [ativo, setAtivo] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    if (!open) return
    setNome('')
    setEmail('')
    setPapel('MODERADOR')
    setAtivo(true)
    setError(null)
  }, [open])

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setError(null)
    try {
      await createAdminStaff({ nome, email, papel, ativo })
      onCreated()
      onOpenChange(false)
    } catch (reason) {
      setError(reason)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={submit}>
          <DialogHeader>
            <DialogTitle>Novo membro da equipe</DialogTitle>
            <DialogDescription>
              O acesso será definido pelo fluxo seguro de redefinição. Nenhuma senha será exibida.
            </DialogDescription>
          </DialogHeader>
          <div className="my-5 space-y-4">
            {error ? <ContractState error={error} compact /> : null}
            <div className="space-y-2">
              <Label htmlFor="staff-name">Nome</Label>
              <Input id="staff-name" value={nome} onChange={(event) => setNome(event.target.value)} maxLength={120} required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="staff-email">E-mail</Label>
              <Input id="staff-email" value={email} onChange={(event) => setEmail(event.target.value)} type="email" required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="staff-role">Papel</Label>
              <Select value={papel} onValueChange={(value) => setPapel(value as 'ADMIN' | 'MODERADOR')}>
                <SelectTrigger id="staff-role"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="ADMIN">Administrador</SelectItem>
                  <SelectItem value="MODERADOR">Moderador</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <label className="flex min-h-11 items-center gap-3 rounded-md border border-zinc-200 px-3 text-sm">
              <input type="checkbox" checked={ativo} onChange={(event) => setAtivo(event.target.checked)} />
              Conta ativa
            </label>
            <p className="flex gap-2 text-xs text-zinc-500">
              <ShieldCheck className="h-4 w-4 shrink-0" />
              Apenas ADMIN e MODERADOR são aceitos. Não há criação de papel Comercial.
            </p>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" disabled={busy} onClick={() => onOpenChange(false)}>Cancelar</Button>
            <Button type="submit" disabled={busy || nome.trim().length < 2 || !email.includes('@')}>
              {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              Criar staff
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
