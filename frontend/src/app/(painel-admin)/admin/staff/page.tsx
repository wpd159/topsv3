'use client'

import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Search, Shield, ShieldCheck, UserCog, UserPlus } from 'lucide-react'

import NovoStaffModal from '../components/novo-staff-modal'
import GerenciarStaffTable from '../components/staff-table'
import { ContractState } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useAuth } from '@/context/AuthContext'
import { getAdminStaffIndicators, listAdminStaff } from '@/features/admin-staff/api'
import type { AdminStaffIndicators, AdminStaffPage } from '@/features/admin-staff/types'

export default function AdminStaffPage() {
  const { usuario } = useAuth()
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const [term, setTerm] = useState(searchParams.get('termo') ?? '')
  const [data, setData] = useState<AdminStaffPage | null>(null)
  const [indicators, setIndicators] = useState<AdminStaffIndicators | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [reload, setReload] = useState(0)
  const [newOpen, setNewOpen] = useState(false)

  const filters = useMemo(() => ({
    termo: searchParams.get('termo') ?? '',
    papel: searchParams.get('papel') ?? 'TODOS',
    status: searchParams.get('status') ?? 'TODOS',
    ordenacao: searchParams.get('ordenacao') ?? 'RECENTES',
    page: Math.max(Number(searchParams.get('page') ?? '0') || 0, 0),
    size: [20, 30, 50, 100].includes(Number(searchParams.get('size'))) ? Number(searchParams.get('size')) : 20,
  }), [searchParams])

  const updateQuery = useCallback((updates: Record<string, string | number | null>) => {
    const next = new URLSearchParams(searchParams.toString())
    Object.entries(updates).forEach(([key, value]) => {
      if (value === null || value === '' || value === 'TODOS') next.delete(key)
      else next.set(key, String(value))
    })
    router.push(next.size ? `${pathname}?${next.toString()}` : pathname)
  }, [pathname, router, searchParams])

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)
    Promise.all([listAdminStaff(filters), getAdminStaffIndicators()])
      .then(([page, header]) => {
        if (!active) return
        setData(page)
        setIndicators(header)
      })
      .catch((reason) => {
        if (active) setError(reason)
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [filters, reload])

  function search(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    updateQuery({ termo: term.trim(), page: null })
  }

  return (
    <section className="space-y-5">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-zinc-950">Gerenciar staff</h1>
          <p className="mt-1 text-sm text-zinc-600">Contas administrativas, papéis e permissões efetivas.</p>
        </div>
        <Button type="button" onClick={() => setNewOpen(true)}><UserPlus className="mr-2 h-4 w-4" />Novo staff</Button>
      </header>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <Indicator label="Total" value={indicators?.total} icon={UserCog} />
        <Indicator label="Ativos" value={indicators?.ativos} icon={ShieldCheck} />
        <Indicator label="Inativos" value={indicators?.inativos} icon={Shield} />
        <Indicator label="Administradores" value={indicators?.administradores} icon={ShieldCheck} />
        <Indicator label="Moderadores" value={indicators?.moderadores} icon={Shield} />
      </div>

      <form onSubmit={search} className="grid gap-3 border-y border-zinc-200 py-4 lg:grid-cols-[minmax(16rem,1fr)_12rem_12rem_12rem_9rem_auto] lg:items-end">
        <label>
          <span className="mb-1 block text-xs font-semibold text-zinc-600">Busca</span>
          <span className="relative block">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
            <Input value={term} onChange={(event) => setTerm(event.target.value)} className="pl-9" placeholder="Nome ou e-mail" />
          </span>
        </label>
        <Filter label="Papel" value={filters.papel} onChange={(value) => updateQuery({ papel: value, page: null })} options={[
          ['TODOS', 'Todos'], ['ADMIN', 'Administrador'], ['MODERADOR', 'Moderador'],
        ]} />
        <Filter label="Estado" value={filters.status} onChange={(value) => updateQuery({ status: value, page: null })} options={[
          ['TODOS', 'Todos'], ['ATIVO', 'Ativos'], ['INATIVO', 'Inativos'],
        ]} />
        <Filter label="Ordenação" value={filters.ordenacao} onChange={(value) => updateQuery({ ordenacao: value, page: null })} options={[
          ['RECENTES', 'Mais recentes'], ['ANTIGOS', 'Mais antigos'],
        ]} />
        <Filter label="Por página" value={String(filters.size)} onChange={(value) => updateQuery({ size: value, page: null })} options={[
          ['20', '20'], ['30', '30'], ['50', '50'], ['100', '100'],
        ]} />
        <Button type="submit">Aplicar</Button>
      </form>

      {error ? <ContractState error={error} onRetry={() => setReload((value) => value + 1)} /> : null}
      {loading && !data ? <p className="py-16 text-center text-sm text-zinc-600" role="status">Carregando staff...</p> : null}
      {!loading && !error && data?.itens.length === 0 ? (
        <div className="py-16 text-center"><UserCog className="mx-auto h-8 w-8 text-zinc-400" /><p className="mt-3 font-medium">Nenhum staff encontrado</p></div>
      ) : null}
      {data?.itens.length ? (
        <GerenciarStaffTable
          itens={data.itens}
          retorno={searchParams.toString()}
          canDelete={usuario?.cargo === 'ADMIN'}
          onRemoved={() => setReload((value) => value + 1)}
        />
      ) : null}

      {data && data.totalPaginas > 1 ? (
        <div className="flex items-center justify-between gap-3">
          <Button type="button" variant="outline" disabled={data.pagina === 0} onClick={() => updateQuery({ page: data.pagina - 1 })}>Anterior</Button>
          <p className="text-sm text-zinc-600">Página {data.pagina + 1} de {data.totalPaginas}</p>
          <Button type="button" variant="outline" disabled={data.pagina + 1 >= data.totalPaginas} onClick={() => updateQuery({ page: data.pagina + 1 })}>Próxima</Button>
        </div>
      ) : null}
      <NovoStaffModal open={newOpen} onOpenChange={setNewOpen} onCreated={() => setReload((value) => value + 1)} />
    </section>
  )
}

function Indicator({ label, value, icon: Icon }: { label: string; value?: number; icon: typeof Shield }) {
  return <div className="border-l-2 border-pink-500 px-3 py-2"><div className="flex items-center gap-2 text-xs font-semibold text-zinc-500"><Icon className="h-4 w-4" />{label}</div><p className="mt-1 text-2xl font-bold text-zinc-950">{value ?? '—'}</p></div>
}

function Filter({ label, value, onChange, options }: {
  label: string
  value: string
  onChange: (value: string) => void
  options: Array<[string, string]>
}) {
  return <label><span className="mb-1 block text-xs font-semibold text-zinc-600">{label}</span><Select value={value} onValueChange={onChange}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent>{options.map(([option, text]) => <SelectItem key={option} value={option}>{text}</SelectItem>)}</SelectContent></Select></label>
}
