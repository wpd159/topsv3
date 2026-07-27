'use client'

import Link from 'next/link'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { ArrowDownAZ, ArrowUpAZ, Search, UserRound } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

import { listAdminUsers } from './api'
import type { AdminUserPage } from './types'

const USER_STATUS = ['ATIVO', 'PENDENTE', 'SUSPENSO', 'DESATIVADO', 'IMPORTADO']
const KYC_STATUS = ['SEM_ENVIO', 'PENDENTE', 'EM_ANALISE', 'APROVADO', 'REJEITADO', 'AJUSTE_SOLICITADO']

function pretty(value: string) {
  return value.replaceAll('_', ' ').toLocaleLowerCase('pt-BR').replace(/^./, (letter) => letter.toUpperCase())
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

function statusTone(status: string) {
  if (status === 'ATIVO' || status === 'APROVADO') return 'border-emerald-300 bg-emerald-50 text-emerald-800'
  if (status === 'SUSPENSO' || status === 'REJEITADO') return 'border-red-300 bg-red-50 text-red-800'
  if (status === 'PENDENTE' || status === 'EM_ANALISE' || status === 'AJUSTE_SOLICITADO') {
    return 'border-amber-300 bg-amber-50 text-amber-800'
  }
  return 'border-zinc-300 bg-zinc-50 text-zinc-700'
}

export function AdminUsuariosList() {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const [pageData, setPageData] = useState<AdminUserPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [reload, setReload] = useState(0)
  const [term, setTerm] = useState(searchParams.get('termo') ?? '')

  const filters = useMemo(() => ({
    termo: searchParams.get('termo') ?? '',
    status: searchParams.get('status') ?? 'TODOS',
    kyc: searchParams.get('kyc') ?? 'TODOS',
    ordenacao: searchParams.get('ordenacao') ?? 'RECENTES',
    page: Math.max(Number(searchParams.get('page') ?? '0') || 0, 0),
    size: [20, 50, 100].includes(Number(searchParams.get('size')))
      ? Number(searchParams.get('size'))
      : 20,
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
    listAdminUsers(filters)
      .then((result) => {
        if (active) setPageData(result)
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

  function submitSearch(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    updateQuery({ termo: term.trim(), page: null })
  }

  const returnQuery = searchParams.toString()
  const detailHref = (id: string) => `/admin/usuarios/${encodeURIComponent(id)}${
    returnQuery ? `?retorno=${encodeURIComponent(returnQuery)}` : ''
  }`

  return (
    <section className="space-y-5">
      <header>
        <h1 className="text-2xl font-bold text-zinc-950">Usuários</h1>
        <p className="mt-1 text-sm text-zinc-600">Contas, verificação KYC e anúncios vinculados.</p>
      </header>

      <form onSubmit={submitSearch} className="grid gap-3 border-y border-zinc-200 py-4 lg:grid-cols-[minmax(16rem,1fr)_12rem_13rem_12rem_auto]">
        <div className="flex gap-2">
          <Input
            value={term}
            onChange={(event) => setTerm(event.target.value)}
            placeholder="Nome, e-mail, CPF, telefone ou ID"
            aria-label="Buscar usuário"
          />
          <Button type="submit" size="icon" aria-label="Buscar">
            <Search className="h-4 w-4" />
          </Button>
        </div>
        <Select value={filters.status} onValueChange={(value) => updateQuery({ status: value, page: null })}>
          <SelectTrigger aria-label="Filtrar por estado da conta"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="TODOS">Todos os estados</SelectItem>
            {USER_STATUS.map((status) => <SelectItem key={status} value={status}>{pretty(status)}</SelectItem>)}
          </SelectContent>
        </Select>
        <Select value={filters.kyc} onValueChange={(value) => updateQuery({ kyc: value, page: null })}>
          <SelectTrigger aria-label="Filtrar por situação do KYC"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="TODOS">Todos os KYC</SelectItem>
            {KYC_STATUS.map((status) => <SelectItem key={status} value={status}>{pretty(status)}</SelectItem>)}
          </SelectContent>
        </Select>
        <Select value={filters.ordenacao} onValueChange={(value) => updateQuery({ ordenacao: value, page: null })}>
          <SelectTrigger aria-label="Ordenar usuários"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="RECENTES">Mais recentes</SelectItem>
            <SelectItem value="ANTIGOS">Mais antigos</SelectItem>
          </SelectContent>
        </Select>
        <Button
          type="button"
          variant="outline"
          onClick={() => {
            setTerm('')
            router.push(pathname)
          }}
        >
          Limpar
        </Button>
      </form>

      {error ? <ContractState error={error} onRetry={() => setReload((value) => value + 1)} /> : null}

      {loading && !pageData ? <p className="py-16 text-center text-sm text-zinc-500">Carregando usuários...</p> : null}

      {!loading && !error && pageData?.itens.length === 0 ? (
        <div className="py-16 text-center">
          <UserRound className="mx-auto h-8 w-8 text-zinc-400" />
          <p className="mt-3 font-medium text-zinc-800">Nenhum usuário encontrado</p>
          <p className="mt-1 text-sm text-zinc-500">A busca foi concluída sem resultados.</p>
        </div>
      ) : null}

      {pageData?.itens.length ? (
        <>
          <div className="hidden overflow-x-auto border-y border-zinc-200 md:block">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Usuário</TableHead>
                  <TableHead>Contato</TableHead>
                  <TableHead>CPF</TableHead>
                  <TableHead>Conta</TableHead>
                  <TableHead>KYC</TableHead>
                  <TableHead>Anúncios</TableHead>
                  <TableHead>Cadastro</TableHead>
                  <TableHead className="text-right">Ação</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {pageData.itens.map((user) => (
                  <TableRow key={user.id}>
                    <TableCell>
                      <p className="font-semibold text-zinc-900">{user.nome || 'Sem nome'}</p>
                      <p className="mt-1 font-mono text-xs text-zinc-500">{user.id}</p>
                    </TableCell>
                    <TableCell>
                      <p className="max-w-64 truncate text-sm">{user.email || 'Não informado'}</p>
                      <p className="text-xs text-zinc-500">{user.telefone || 'Sem telefone'}</p>
                    </TableCell>
                    <TableCell>{user.cpfMascarado || 'Não informado'}</TableCell>
                    <TableCell>
                      <Badge variant="outline" className={statusTone(user.status)}>{pretty(user.status)}</Badge>
                      {user.bloqueado ? <Badge variant="destructive" className="ml-1">Bloqueado</Badge> : null}
                    </TableCell>
                    <TableCell><Badge variant="outline" className={statusTone(user.kycStatus)}>{pretty(user.kycStatus)}</Badge></TableCell>
                    <TableCell>{user.totalAnuncios}</TableCell>
                    <TableCell className="whitespace-nowrap">{formatDate(user.criadoEm)}</TableCell>
                    <TableCell className="text-right">
                      <Button asChild size="sm" variant="outline"><Link href={detailHref(user.id)}>Abrir</Link></Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          <div className="grid gap-3 md:hidden">
            {pageData.itens.map((user) => (
              <article key={user.id} className="border-b border-zinc-200 pb-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="font-semibold text-zinc-900">{user.nome || 'Sem nome'}</p>
                    <p className="truncate text-sm text-zinc-600">{user.email || 'Não informado'}</p>
                  </div>
                  <Button asChild size="sm" variant="outline"><Link href={detailHref(user.id)}>Abrir</Link></Button>
                </div>
                <div className="mt-3 flex flex-wrap gap-2">
                  <Badge variant="outline" className={statusTone(user.status)}>{pretty(user.status)}</Badge>
                  <Badge variant="outline" className={statusTone(user.kycStatus)}>KYC {pretty(user.kycStatus)}</Badge>
                  <Badge variant="outline">{user.totalAnuncios} anúncio(s)</Badge>
                  {user.bloqueado ? <Badge variant="destructive">Bloqueado</Badge> : null}
                </div>
                <p className="mt-2 text-xs text-zinc-500">{user.cpfMascarado || 'CPF não informado'} · {user.telefone || 'Sem telefone'}</p>
              </article>
            ))}
          </div>
        </>
      ) : null}

      {pageData ? (
        <footer className="flex flex-col gap-3 border-t border-zinc-200 pt-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-2 text-sm text-zinc-600">
            <span>{pageData.totalElements} usuário(s)</span>
            <Select value={String(filters.size)} onValueChange={(value) => updateQuery({ size: value, page: null })}>
              <SelectTrigger className="w-24" aria-label="Usuários por página"><SelectValue /></SelectTrigger>
              <SelectContent>
                {[20, 50, 100].map((size) => <SelectItem key={size} value={String(size)}>{size}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
          <div className="flex items-center justify-between gap-3 sm:justify-end">
            <Button type="button" variant="outline" disabled={filters.page === 0 || loading} onClick={() => updateQuery({ page: filters.page - 1 })}>
              {filters.ordenacao === 'ANTIGOS' ? <ArrowUpAZ className="mr-2 h-4 w-4" /> : null}
              Anterior
            </Button>
            <span className="whitespace-nowrap text-sm text-zinc-600">Página {pageData.page + 1} de {Math.max(pageData.totalPages, 1)}</span>
            <Button type="button" variant="outline" disabled={pageData.last || loading} onClick={() => updateQuery({ page: filters.page + 1 })}>
              Próxima
              {filters.ordenacao === 'RECENTES' ? <ArrowDownAZ className="ml-2 h-4 w-4" /> : null}
            </Button>
          </div>
        </footer>
      ) : null}
    </section>
  )
}
