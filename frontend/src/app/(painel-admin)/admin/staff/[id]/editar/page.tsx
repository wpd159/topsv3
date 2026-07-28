'use client'

import Link from 'next/link'
import { useParams, useRouter, useSearchParams } from 'next/navigation'
import { useCallback, useEffect, useState } from 'react'
import { ArrowLeft, Loader2 } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { getAdminStaff, updateAdminStaff } from '@/features/admin-staff/api'
import type { AdminStaffDetail } from '@/features/admin-staff/types'

export default function StaffEditPage() {
  const params = useParams<{ id: string }>()
  const router = useRouter()
  const searchParams = useSearchParams()
  const id = params?.id || ''
  const retorno = searchParams.get('retorno')
  const detailHref = `/admin/staff/${encodeURIComponent(id)}${retorno ? `?retorno=${encodeURIComponent(retorno)}` : ''}`
  const [detail, setDetail] = useState<AdminStaffDetail | null>(null)
  const [nome, setNome] = useState('')
  const [papel, setPapel] = useState<'ADMIN' | 'MODERADOR'>('MODERADOR')
  const [ativo, setAtivo] = useState(true)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [confirmOpen, setConfirmOpen] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await getAdminStaff(id)
      setDetail(result)
      setNome(result.staff.nome)
      setPapel(result.staff.papel === 'ADMIN' ? 'ADMIN' : 'MODERADOR')
      setAtivo(result.staff.ativo)
    } catch (reason) {
      setError(reason)
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => { void load() }, [load])

  async function save() {
    if (!detail || busy) return
    setBusy(true)
    setError(null)
    try {
      await updateAdminStaff(id, { nome, papel, ativo, versao: detail.staff.versao })
      router.push(detailHref)
      router.refresh()
    } catch (reason) {
      setError(reason)
      setConfirmOpen(false)
    } finally {
      setBusy(false)
    }
  }

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (detail?.staff.ativo && !ativo) setConfirmOpen(true)
    else void save()
  }

  if (loading && !detail) return <p className="py-16 text-center text-sm text-zinc-500">Carregando staff...</p>
  if (error && !detail) return <ContractState error={error} onRetry={() => void load()} />
  if (!detail) return null

  return (
    <section className="mx-auto max-w-2xl space-y-5">
      <header>
        <Button asChild variant="ghost" className="-ml-3"><Link href={detailHref}><ArrowLeft className="mr-2 h-4 w-4" />Voltar</Link></Button>
        <h1 className="text-2xl font-bold text-zinc-950">Editar staff</h1>
        <p className="mt-1 text-sm text-zinc-600">Mudanças de papel ou estado encerram as sessões ativas.</p>
      </header>
      {error ? <ContractState error={error} onRetry={() => void load()} /> : null}
      <form onSubmit={submit} className="space-y-5 border-y border-zinc-200 py-5">
        <div className="space-y-2"><Label htmlFor="edit-staff-name">Nome</Label><Input id="edit-staff-name" value={nome} onChange={(event) => setNome(event.target.value)} maxLength={120} required /></div>
        <div className="space-y-2"><Label>E-mail</Label><Input value={detail.staff.email} disabled /><p className="text-xs text-zinc-500">O e-mail de acesso não é alterado nesta operação.</p></div>
        <div className="space-y-2">
          <Label htmlFor="edit-staff-role">Papel</Label>
          <Select value={papel} onValueChange={(value) => setPapel(value as 'ADMIN' | 'MODERADOR')}>
            <SelectTrigger id="edit-staff-role"><SelectValue /></SelectTrigger>
            <SelectContent><SelectItem value="ADMIN">Administrador</SelectItem><SelectItem value="MODERADOR">Moderador</SelectItem></SelectContent>
          </Select>
        </div>
        <label className="flex min-h-11 items-center gap-3 rounded-md border border-zinc-200 px-3 text-sm"><input type="checkbox" checked={ativo} onChange={(event) => setAtivo(event.target.checked)} />Conta ativa</label>
        <div className="flex justify-end gap-2"><Button asChild type="button" variant="outline"><Link href={detailHref}>Cancelar</Link></Button><Button type="submit" disabled={busy || nome.trim().length < 2}>{busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}Salvar</Button></div>
      </form>

      <Dialog open={confirmOpen} onOpenChange={(open) => { if (!busy) setConfirmOpen(open) }}>
        <DialogContent>
          <DialogHeader><DialogTitle>Desativar conta de staff?</DialogTitle><DialogDescription>O login será recusado e as sessões atuais serão encerradas. A conta e a auditoria serão preservadas.</DialogDescription></DialogHeader>
          <DialogFooter><Button type="button" variant="outline" disabled={busy} onClick={() => setConfirmOpen(false)}>Cancelar</Button><Button type="button" variant="destructive" disabled={busy} onClick={() => void save()}>{busy ? 'Processando...' : 'Desativar'}</Button></DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}
