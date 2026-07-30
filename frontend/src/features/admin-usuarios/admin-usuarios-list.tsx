'use client'

import Link from 'next/link'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { toast } from 'sonner'
import {
  BriefcaseBusiness,
  Eye,
  FolderOpen,
  Search,
  Trash2,
  UserMinus,
  UserPlus,
  Users,
  WalletCards,
} from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useAuth } from '@/context/AuthContext'
import { listAdminAdFilterLocations } from '@/features/admin-anuncios/api'
import { SearchableSelect } from '@/features/anuncio-wizard/components/searchable-select'
import type { SearchableSelectOption } from '@/features/anuncio-wizard/wizard-constants'
import { maskPhoneBR } from '@/lib/phone-mask'

import { getAdminUserIndicators, listAdminUsers } from './api'
import { AdminUsuarioCreditDialog } from './admin-usuario-credit-dialog'
import { AdminUsuarioDeleteDialog } from './admin-usuario-delete-dialog'
import type { AdminUserIndicators, AdminUserPage, AdminUserSummary } from './types'

const KYC_STATUS = ['SEM_ENVIO', 'PENDENTE', 'EM_ANALISE', 'APROVADO', 'REJEITADO', 'AJUSTE_SOLICITADO']
const GROUPS = [
  { value: 'TODOS', label: 'Todos' },
  { value: 'ATIVOS', label: 'Ativos' },
  { value: 'INATIVOS', label: 'Inativos' },
  { value: 'COM_ANUNCIOS', label: 'Com anúncios' },
  { value: 'SEM_ANUNCIOS', label: 'Sem anúncios' },
  { value: 'EXCLUIDOS', label: 'Excluídos' },
]

function pretty(value: string) {
  return value.replaceAll('_', ' ').toLocaleLowerCase('pt-BR').replace(/^./, (letter) => letter.toUpperCase())
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

function statusTone(status: string) {
  if (status === 'ATIVO' || status === 'APROVADO') return 'border-emerald-300 bg-emerald-50 text-emerald-800'
  if (status === 'SUSPENSO' || status === 'REJEITADO' || status === 'DESATIVADO') {
    return 'border-red-300 bg-red-50 text-red-800'
  }
  if (status === 'PENDENTE' || status === 'EM_ANALISE' || status === 'AJUSTE_SOLICITADO') {
    return 'border-amber-300 bg-amber-50 text-amber-800'
  }
  return 'border-zinc-300 bg-zinc-50 text-zinc-700'
}

export function AdminUsuariosList() {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const { usuario } = useAuth()
  const [pageData, setPageData] = useState<AdminUserPage | null>(null)
  const [indicators, setIndicators] = useState<AdminUserIndicators | null>(null)
  const [locations, setLocations] = useState<Awaited<ReturnType<typeof listAdminAdFilterLocations>>>([])
  const [loading, setLoading] = useState(true)
  const [locationsLoading, setLocationsLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [reload, setReload] = useState(0)
  const [term, setTerm] = useState(searchParams.get('termo') ?? '')
  const [creditUser, setCreditUser] = useState<AdminUserSummary | null>(null)
  const [deleteUser, setDeleteUser] = useState<AdminUserSummary | null>(null)
  const admin = usuario?.cargo === 'ADMIN'

  const filters = useMemo(() => ({
    termo: searchParams.get('termo') ?? '',
    status: searchParams.get('status') ?? 'TODOS',
    kyc: searchParams.get('kyc') ?? 'TODOS',
    grupo: searchParams.get('grupo') ?? 'TODOS',
    uf: searchParams.get('uf') ?? '',
    cidade: searchParams.get('cidade') ?? '',
    ordenacao: searchParams.get('ordenacao') ?? 'RECENTES',
    page: Math.max(Number(searchParams.get('page') ?? '0') || 0, 0),
    size: [20, 30, 50, 100].includes(Number(searchParams.get('size')))
      ? Number(searchParams.get('size'))
      : 30,
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
    Promise.all([listAdminUsers(filters), getAdminUserIndicators()])
      .then(([result, header]) => {
        if (!active) return
        setPageData(result)
        setIndicators(header)
      })
      .catch((reason) => {
        if (active) setError(reason)
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [filters, reload])

  useEffect(() => {
    let active = true
    setLocationsLoading(true)
    listAdminAdFilterLocations()
      .then((result) => {
        if (active) setLocations(result)
      })
      .catch((reason) => {
        if (active) setError(reason)
      })
      .finally(() => {
        if (active) setLocationsLoading(false)
      })
    return () => {
      active = false
    }
  }, [])

  const stateOptions = useMemo<SearchableSelectOption[]>(() => {
    const states = new Map<string, string>()
    locations.forEach((item) => states.set(item.uf, item.estado))
    return [
      { id: 'TODOS', label: 'Todas as UFs' },
      ...Array.from(states.entries())
        .sort((left, right) => left[1].localeCompare(right[1], 'pt-BR'))
        .map(([uf, name]) => ({ id: uf, label: `${name} - ${uf}` })),
    ]
  }, [locations])
  const cityOptions = useMemo<SearchableSelectOption[]>(() => {
    const cities = new Map<string, string>()
    locations
      .filter((item) => item.uf === filters.uf)
      .forEach((item) => cities.set(item.cidadeSlug, item.cidade))
    return [
      { id: 'TODAS', label: 'Todas as cidades' },
      ...Array.from(cities.entries())
        .sort((left, right) => left[1].localeCompare(right[1], 'pt-BR'))
        .map(([slug, name]) => ({ id: slug, label: name })),
    ]
  }, [filters.uf, locations])
  const stateLabel = stateOptions.find((item) => item.id === filters.uf)?.label || 'Todas as UFs'
  const cityLabel = cityOptions.find((item) => item.id === filters.cidade)?.label || 'Todas as cidades'

  function submitSearch(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    updateQuery({ termo: term.trim(), page: null })
  }

  const returnQuery = searchParams.toString()
  const detailHref = (id: string, hash = '') => `/admin/usuarios/${encodeURIComponent(id)}${
    returnQuery ? `?retorno=${encodeURIComponent(returnQuery)}` : ''
  }${hash}`

  return (
    <section className="space-y-5">
      <header>
        <h1 className="text-2xl font-bold text-zinc-950">Gerenciar usuários</h1>
        <p className="mt-1 text-sm text-zinc-600">Gestão cadastral, documental e operacional.</p>
      </header>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Indicator label="Total de usuários" value={indicators?.totalUsuarios} icon={Users} />
        <Indicator label="Novos hoje" value={indicators?.novosHoje} icon={UserPlus} />
        <Indicator label="Com anúncios" value={indicators?.comAnuncios} icon={BriefcaseBusiness} />
        <Indicator label="Sem anúncios" value={indicators?.semAnuncios} icon={UserMinus} />
      </div>

      <div className="border-y border-zinc-200 py-4">
        <div className="flex flex-wrap gap-2">
          {GROUPS.map((group) => (
            <Button
              key={group.value}
              type="button"
              size="sm"
              variant={filters.grupo === group.value ? 'default' : 'outline'}
              onClick={() => updateQuery({ grupo: group.value, page: null })}
            >
              {group.label}
            </Button>
          ))}
        </div>
        <form onSubmit={submitSearch} className="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-12 xl:items-end">
          <label className="min-w-0 sm:col-span-2 xl:col-span-3">
            <span className="mb-1 block min-h-4 text-xs font-semibold text-zinc-600">Busca</span>
            <span className="relative block">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
              <Input
                value={term}
                onChange={(event) => setTerm(event.target.value)}
                placeholder="Nome, e-mail, CPF, telefone ou ID"
                className="h-10 w-full pl-9"
              />
            </span>
          </label>
          <div className="min-w-0 xl:col-span-2">
            <span className="mb-1 block min-h-4 text-xs font-semibold text-zinc-600">Estado</span>
            <SearchableSelect
              value={filters.uf || 'TODOS'}
              label={stateLabel}
              placeholder="Todas as UFs"
              searchPlaceholder="Pesquisar estado"
              emptyText="Nenhum estado encontrado"
              options={stateOptions}
              disabled={locationsLoading}
              onSelect={(value) => updateQuery({
                uf: value === 'TODOS' ? '' : value,
                cidade: '',
                page: null,
              })}
            />
          </div>
          <div className="min-w-0 xl:col-span-2">
            <span className="mb-1 block min-h-4 text-xs font-semibold text-zinc-600">Cidade</span>
            <SearchableSelect
              value={filters.cidade || 'TODAS'}
              label={cityLabel}
              placeholder="Todas as cidades"
              searchPlaceholder="Pesquisar cidade"
              emptyText="Nenhuma cidade encontrada"
              options={cityOptions}
              disabled={!filters.uf || locationsLoading}
              onSelect={(value) => updateQuery({ cidade: value === 'TODAS' ? '' : value, page: null })}
            />
          </div>
          <label className="min-w-0 xl:col-span-1">
            <span className="mb-1 block min-h-4 text-xs font-semibold text-zinc-600">KYC</span>
            <Select value={filters.kyc} onValueChange={(value) => updateQuery({ kyc: value, page: null })}>
              <SelectTrigger className="h-10 w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="TODOS">Todos</SelectItem>
                {KYC_STATUS.map((status) => <SelectItem key={status} value={status}>{pretty(status)}</SelectItem>)}
              </SelectContent>
            </Select>
          </label>
          <label className="min-w-0 xl:col-span-2">
            <span className="mb-1 block min-h-4 text-xs font-semibold text-zinc-600">Ordenação</span>
            <Select value={filters.ordenacao} onValueChange={(value) => updateQuery({ ordenacao: value, page: null })}>
              <SelectTrigger className="h-10 w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="RECENTES">Cadastro mais recente</SelectItem>
                <SelectItem value="ANTIGOS">Cadastro mais antigo</SelectItem>
              </SelectContent>
            </Select>
          </label>
          <label className="min-w-0 xl:col-span-1">
            <span className="mb-1 block min-h-4 text-xs font-semibold text-zinc-600">Por página</span>
            <Select value={String(filters.size)} onValueChange={(value) => updateQuery({ size: value, page: null })}>
              <SelectTrigger className="h-10 w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                {[20, 30, 50, 100].map((size) => <SelectItem key={size} value={String(size)}>{size}</SelectItem>)}
              </SelectContent>
            </Select>
          </label>
          <Button type="submit" className="h-10 w-full sm:col-span-2 xl:col-span-1">Aplicar</Button>
        </form>
      </div>

      {error ? <ContractState error={error} onRetry={() => setReload((value) => value + 1)} /> : null}
      {loading && !pageData ? <p className="py-16 text-center text-sm text-zinc-500">Carregando usuários...</p> : null}
      {!loading && !error && pageData?.itens.length === 0 ? (
        <div className="py-16 text-center">
          <Users className="mx-auto h-8 w-8 text-zinc-400" />
          <p className="mt-3 font-medium text-zinc-800">Nenhum usuário encontrado</p>
        </div>
      ) : null}

      {pageData?.itens.length ? (
        <>
          <div className="hidden overflow-x-auto border-y border-zinc-200 md:block">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Usuário</TableHead>
                  <TableHead>Local</TableHead>
                  <TableHead>Anúncios</TableHead>
                  <TableHead>Conta</TableHead>
                  <TableHead>KYC</TableHead>
                  <TableHead>Cadastro</TableHead>
                  <TableHead className="text-right">Ações</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {pageData.itens.map((user) => (
                  <TableRow key={user.id}>
                    <TableCell>
                      <p className="font-semibold text-zinc-900">{user.nome || 'Sem nome'}</p>
                      <p className="max-w-72 truncate text-sm text-zinc-600">{user.email || 'Não informado'}</p>
                      <p className="text-xs text-zinc-500">
                        {user.cpfMascarado || 'CPF não informado'} · {user.telefone ? maskPhoneBR(user.telefone) : 'Sem telefone'}
                      </p>
                    </TableCell>
                    <TableCell>{user.cidadePrincipal && user.ufPrincipal ? `${user.cidadePrincipal}/${user.ufPrincipal}` : '—'}</TableCell>
                    <TableCell>{user.totalAnuncios}</TableCell>
                    <TableCell>
                      <Badge variant="outline" className={statusTone(user.status)}>{pretty(user.status)}</Badge>
                      {user.bloqueado ? <Badge variant="destructive" className="ml-1">Bloqueado</Badge> : null}
                    </TableCell>
                    <TableCell><Badge variant="outline" className={statusTone(user.kycStatus)}>{pretty(user.kycStatus)}</Badge></TableCell>
                    <TableCell className="whitespace-nowrap">{formatDate(user.criadoEm)}</TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-2">
                        <Button asChild size="sm" variant="outline">
                          <Link href={detailHref(user.id, '#anuncios-usuario')}><FolderOpen className="mr-1 h-4 w-4" />Anúncios</Link>
                        </Button>
                        {admin && user.status !== 'EXCLUIDO' ? (
                          <Button type="button" size="sm" variant="outline" onClick={() => setCreditUser(user)}>
                            <WalletCards className="mr-1 h-4 w-4" />Crédito
                          </Button>
                        ) : null}
                        {admin && user.podeExcluir ? (
                          <Button
                            type="button"
                            size="sm"
                            variant="destructive"
                            onClick={() => setDeleteUser(user)}
                            title="Excluir usuário"
                          >
                            <Trash2 className="h-4 w-4" />
                            <span className="sr-only">Excluir usuário</span>
                          </Button>
                        ) : null}
                        <Button asChild size="sm" variant="outline">
                          <Link href={detailHref(user.id)}><Eye className="mr-1 h-4 w-4" />Ver</Link>
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          <div className="grid gap-4 md:hidden">
            {pageData.itens.map((user) => (
              <article key={user.id} className="border-b border-zinc-200 pb-4">
                <p className="font-semibold text-zinc-900">{user.nome || 'Sem nome'}</p>
                <p className="truncate text-sm text-zinc-600">{user.email || 'Não informado'}</p>
                <p className="mt-1 text-xs text-zinc-500">
                  {user.cidadePrincipal && user.ufPrincipal ? `${user.cidadePrincipal}/${user.ufPrincipal}` : 'Local não informado'}
                </p>
                <div className="mt-3 flex flex-wrap gap-2">
                  <Badge variant="outline" className={statusTone(user.status)}>{pretty(user.status)}</Badge>
                  <Badge variant="outline">{user.totalAnuncios} anúncio(s)</Badge>
                  <Badge variant="outline">KYC {pretty(user.kycStatus)}</Badge>
                </div>
                <div className="mt-3 grid grid-cols-2 gap-2">
                  <Button asChild size="sm" variant="outline"><Link href={detailHref(user.id, '#anuncios-usuario')}>Anúncios</Link></Button>
                  {admin && user.status !== 'EXCLUIDO' ? <Button type="button" size="sm" variant="outline" onClick={() => setCreditUser(user)}>Crédito</Button> : null}
                  <Button asChild size="sm" variant="outline"><Link href={detailHref(user.id)}>Ver</Link></Button>
                  {admin && user.podeExcluir ? (
                    <Button type="button" size="sm" variant="destructive" onClick={() => setDeleteUser(user)}>
                      Excluir
                    </Button>
                  ) : null}
                </div>
              </article>
            ))}
          </div>
        </>
      ) : null}

      {pageData ? (
        <footer className="flex flex-col gap-3 border-t border-zinc-200 pt-4 sm:flex-row sm:items-center sm:justify-between">
          <span className="text-sm text-zinc-600">{pageData.totalElements} usuário(s)</span>
          <div className="flex items-center justify-between gap-3 sm:justify-end">
            <Button type="button" variant="outline" disabled={filters.page === 0 || loading} onClick={() => updateQuery({ page: filters.page - 1 })}>
              Anterior
            </Button>
            <span className="whitespace-nowrap text-sm text-zinc-600">Página {pageData.page + 1} de {Math.max(pageData.totalPages, 1)}</span>
            <Button type="button" variant="outline" disabled={pageData.last || loading} onClick={() => updateQuery({ page: filters.page + 1 })}>
              Próxima
            </Button>
          </div>
        </footer>
      ) : null}

      {creditUser ? (
        <AdminUsuarioCreditDialog
          open
          onOpenChange={(open) => { if (!open) setCreditUser(null) }}
          usuarioId={creditUser.id}
          nome={creditUser.nome || 'Usuário'}
        />
      ) : null}

      {deleteUser ? (
        <AdminUsuarioDeleteDialog
          open
          onOpenChange={(open) => { if (!open) setDeleteUser(null) }}
          usuarioId={deleteUser.id}
          nome={deleteUser.nome || 'Usuário'}
          onSuccess={(result) => {
            setPageData((current) => current
              ? {
                  ...current,
                  itens: current.itens.filter((item) => item.id !== deleteUser.id),
                  totalElements: Math.max(0, current.totalElements - 1),
                }
              : current)
            toast.success(result.anonimizado
              ? 'Conta encerrada e anonimizada com sucesso.'
              : 'Conta excluída definitivamente.')
            setReload((value) => value + 1)
          }}
        />
      ) : null}
    </section>
  )
}

function Indicator({
  label,
  value,
  icon: Icon,
}: {
  label: string
  value?: number
  icon: typeof Users
}) {
  return (
    <div className="flex min-h-24 items-center justify-between rounded-md border border-zinc-200 bg-white px-5 py-4">
      <div>
        <p className="text-sm text-zinc-600">{label}</p>
        <p className="mt-1 text-2xl font-bold text-zinc-950">{value == null ? '—' : value.toLocaleString('pt-BR')}</p>
      </div>
      <span className="rounded-full bg-pink-50 p-3 text-pink-700"><Icon className="h-5 w-5" /></span>
    </div>
  )
}
