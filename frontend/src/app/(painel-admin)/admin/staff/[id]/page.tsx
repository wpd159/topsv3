'use client'

import Link from 'next/link'
import { useParams, useSearchParams } from 'next/navigation'
import { useCallback, useEffect, useState } from 'react'
import { ArrowLeft, Pencil, ShieldCheck } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { getAdminStaff } from '@/features/admin-staff/api'
import type { AdminStaffDetail } from '@/features/admin-staff/types'

function formatDate(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

export default function StaffDetailPage() {
  const params = useParams<{ id: string }>()
  const searchParams = useSearchParams()
  const id = params?.id || ''
  const [detail, setDetail] = useState<AdminStaffDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const retorno = searchParams.get('retorno')
  const listHref = retorno ? `/admin/staff?${retorno}` : '/admin/staff'
  const editHref = `/admin/staff/${encodeURIComponent(id)}/editar${retorno ? `?retorno=${encodeURIComponent(retorno)}` : ''}`

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setDetail(await getAdminStaff(id))
    } catch (reason) {
      setError(reason)
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => { void load() }, [load])

  if (loading && !detail) return <p className="py-16 text-center text-sm text-zinc-500">Carregando staff...</p>
  if (error && !detail) return <ContractState error={error} onRetry={() => void load()} />
  if (!detail) return null

  const staff = detail.staff
  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4 border-b border-zinc-200 pb-4">
        <div>
          <Button asChild variant="ghost" className="-ml-3"><Link href={listHref}><ArrowLeft className="mr-2 h-4 w-4" />Voltar</Link></Button>
          <h1 className="text-2xl font-bold text-zinc-950">{staff.nome}</h1>
          <p className="mt-1 break-all text-sm text-zinc-600">{staff.email}</p>
          <div className="mt-3 flex flex-wrap gap-2">
            <Badge variant="outline">{staff.papelRotulo}</Badge>
            <Badge variant={staff.ativo ? 'default' : 'secondary'}>{staff.statusRotulo}</Badge>
            {staff.acessoPendente ? <Badge variant="outline">Acesso pendente</Badge> : null}
          </div>
        </div>
        <Button asChild><Link href={editHref}><Pencil className="mr-2 h-4 w-4" />Editar staff</Link></Button>
      </header>

      {error ? <ContractState error={error} onRetry={() => void load()} compact /> : null}
      <section>
        <h2 className="text-lg font-semibold text-zinc-950">Dados da conta</h2>
        <dl className="mt-3 grid gap-4 border-y border-zinc-200 py-4 sm:grid-cols-2 lg:grid-cols-4">
          <Data label="Papel" value={staff.papelRotulo} />
          <Data label="Estado" value={staff.statusRotulo} />
          <Data label="Acesso" value={staff.acessoPendente ? 'Definição pendente' : 'Configurado'} />
          <Data label="Cadastro" value={formatDate(staff.criadoEm)} />
        </dl>
      </section>

      <section>
        <h2 className="flex items-center gap-2 text-lg font-semibold text-zinc-950"><ShieldCheck className="h-5 w-5" />Permissões efetivas</h2>
        {detail.permissoes.length ? (
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            {detail.permissoes.map((permission) => (
              <div key={permission.codigo} className="border-l-2 border-pink-500 pl-3">
                <p className="font-medium text-zinc-900">{permission.descricao}</p>
                <p className="mt-1 text-xs text-zinc-500">{permission.codigo}</p>
              </div>
            ))}
          </div>
        ) : <p className="mt-3 text-sm text-zinc-500">Nenhuma permissão administrativa efetiva.</p>}
      </section>

      <section>
        <h2 className="text-lg font-semibold text-zinc-950">Histórico administrativo</h2>
        {detail.historico.length ? (
          <div className="mt-3 divide-y divide-zinc-200 border-y border-zinc-200">
            {detail.historico.map((event) => (
              <article key={event.id} className="flex flex-col gap-1 py-3 sm:flex-row sm:justify-between">
                <div><p className="font-medium">{event.acaoRotulo}</p><p className="text-xs text-zinc-500">Ator: {event.atorNome || 'Sistema'}</p></div>
                <div className="text-xs text-zinc-500 sm:text-right"><p>{formatDate(event.criadoEm)}</p>{event.requestId ? <p className="font-mono">requestId {event.requestId}</p> : null}</div>
              </article>
            ))}
          </div>
        ) : <p className="mt-3 text-sm text-zinc-500">Nenhum evento administrativo registrado.</p>}
      </section>
    </section>
  )
}

function Data({ label, value }: { label: string; value: string }) {
  return <div><dt className="text-xs font-semibold uppercase text-zinc-500">{label}</dt><dd className="mt-1 text-sm text-zinc-900">{value}</dd></div>
}
