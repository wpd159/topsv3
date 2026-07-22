'use client'

import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight, RefreshCw, Search, ShieldCheck } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

import { listAdminAds } from './api'
import type { AdminAdListItem, AdminPage } from './types'

const PAGE_SIZE = 20

const STATUS_OPTIONS = [
  'TODOS',
  'RASCUNHO',
  'PENDENTE_REVISAO',
  'APROVADO',
  'PUBLICADO',
  'PAUSADO',
  'REJEITADO',
  'REMOVIDO',
]

const MODERATION_OPTIONS = ['TODOS', 'PENDENTE', 'APROVADO', 'REJEITADO', 'NAO_ENVIADO']

function statusTone(status: string) {
  if (status === 'APROVADO' || status === 'PUBLICADO') return 'border-emerald-200 bg-emerald-50 text-emerald-800'
  if (status === 'REJEITADO' || status === 'REMOVIDO') return 'border-red-200 bg-red-50 text-red-800'
  if (status === 'PENDENTE' || status === 'PENDENTE_REVISAO') return 'border-amber-200 bg-amber-50 text-amber-800'
  return 'border-zinc-200 bg-zinc-50 text-zinc-700'
}

function dateLabel(value?: string | null) {
  if (!value) return 'Sem data'
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

function locationLabel(item: AdminAdListItem) {
  return [item.localizacao?.bairro, item.localizacao?.cidade, item.localizacao?.uf].filter(Boolean).join(' · ') || 'Localização não informada'
}

export function AdminAnunciosList() {
  const [data, setData] = useState<AdminPage<AdminAdListItem> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('TODOS')
  const [statusModeracao, setStatusModeracao] = useState('PENDENTE')
  const [searchDraft, setSearchDraft] = useState('')
  const [termo, setTermo] = useState('')
  const [uf, setUf] = useState('')
  const [cidade, setCidade] = useState('')
  const [bairro, setBairro] = useState('')
  const [reload, setReload] = useState(0)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await listAdminAds({
        page,
        size: PAGE_SIZE,
        status: status === 'TODOS' ? undefined : status,
        statusModeracao: statusModeracao === 'TODOS' ? undefined : statusModeracao,
        termo,
        uf,
        cidade,
        bairro,
      }))
    } catch (reason) {
      setError(reason)
    } finally {
      setLoading(false)
    }
  }, [bairro, cidade, page, status, statusModeracao, termo, uf])

  useEffect(() => {
    void load()
  }, [load, reload])

  function submitSearch(event: React.FormEvent) {
    event.preventDefault()
    setPage(0)
    setTermo(searchDraft.trim())
  }

  const items = data?.itens ?? []

  return (
    <div className="space-y-5">
      <header className="flex flex-col gap-3 border-b border-zinc-200 pb-5 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <div className="flex items-center gap-2 text-sm font-semibold text-pink-700">
            <ShieldCheck className="h-4 w-4" aria-hidden="true" />
            Moderação canônica
          </div>
          <h1 className="mt-1 text-2xl font-bold text-zinc-950">Anúncios</h1>
          <p className="mt-1 text-sm text-zinc-600">Analise os dados comerciais e decida cada mídia individualmente.</p>
        </div>
        <Button type="button" variant="outline" onClick={() => setReload((value) => value + 1)} disabled={loading}>
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
          Atualizar
        </Button>
      </header>

      <form onSubmit={submitSearch} className="grid gap-3 border-b border-zinc-200 pb-5 md:grid-cols-2 xl:grid-cols-6">
        <label className="md:col-span-2 xl:col-span-2">
          <span className="mb-1 block text-xs font-semibold text-zinc-600">Busca</span>
          <span className="relative block">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" aria-hidden="true" />
            <Input value={searchDraft} onChange={(event) => setSearchDraft(event.target.value)} placeholder="Título ou slug" className="pl-9" />
          </span>
        </label>
        <label>
          <span className="mb-1 block text-xs font-semibold text-zinc-600">Estado do anúncio</span>
          <Select value={status} onValueChange={(value) => { setStatus(value); setPage(0) }}>
            <SelectTrigger className="w-full rounded-md bg-white"><SelectValue /></SelectTrigger>
            <SelectContent className="rounded-md">
              {STATUS_OPTIONS.map((value) => <SelectItem className="rounded-md" key={value} value={value}>{value === 'TODOS' ? 'Todos' : value}</SelectItem>)}
            </SelectContent>
          </Select>
        </label>
        <label>
          <span className="mb-1 block text-xs font-semibold text-zinc-600">Moderação</span>
          <Select value={statusModeracao} onValueChange={(value) => { setStatusModeracao(value); setPage(0) }}>
            <SelectTrigger className="w-full rounded-md bg-white"><SelectValue /></SelectTrigger>
            <SelectContent className="rounded-md">
              {MODERATION_OPTIONS.map((value) => <SelectItem className="rounded-md" key={value} value={value}>{value === 'TODOS' ? 'Todas' : value}</SelectItem>)}
            </SelectContent>
          </Select>
        </label>
        <label>
          <span className="mb-1 block text-xs font-semibold text-zinc-600">UF</span>
          <Input value={uf} onChange={(event) => { setUf(event.target.value.toUpperCase().slice(0, 2)); setPage(0) }} placeholder="UF" />
        </label>
        <Button type="submit" className="self-end">Aplicar busca</Button>
        <label>
          <span className="mb-1 block text-xs font-semibold text-zinc-600">Cidade</span>
          <Input value={cidade} onChange={(event) => { setCidade(event.target.value); setPage(0) }} placeholder="Cidade" />
        </label>
        <label>
          <span className="mb-1 block text-xs font-semibold text-zinc-600">Bairro</span>
          <Input value={bairro} onChange={(event) => { setBairro(event.target.value); setPage(0) }} placeholder="Bairro" />
        </label>
      </form>

      {error ? <ContractState error={error} onRetry={() => setReload((value) => value + 1)} /> : null}

      {!error && loading && !data ? <p className="py-12 text-center text-sm text-zinc-500">Carregando fila...</p> : null}

      {!error && !loading && items.length === 0 ? (
        <div className="border border-zinc-200 bg-white px-5 py-10 text-center">
          <p className="font-semibold text-zinc-900">Nenhum anúncio corresponde aos filtros.</p>
          <p className="mt-1 text-sm text-zinc-600">A fila está legitimamente vazia para esta combinação.</p>
        </div>
      ) : null}

      {!error && items.length > 0 ? (
        <>
          <div className="space-y-3 md:hidden">
            {items.map((item) => (
              <article key={item.id} className="border border-zinc-200 bg-white p-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <h2 className="truncate font-semibold text-zinc-950">{item.titulo}</h2>
                    <p className="mt-1 text-xs text-zinc-500">{item.anunciante?.nome || item.anunciante?.emailMascarado || 'Proprietário não identificado'}</p>
                  </div>
                  <Badge variant="outline" className={statusTone(item.statusModeracao)}>{item.statusModeracao}</Badge>
                </div>
                <p className="mt-3 text-xs text-zinc-600">{locationLabel(item)}</p>
                <p className="mt-1 text-xs text-zinc-500">Atualizado em {dateLabel(item.atualizadoEm)}</p>
                <Button asChild className="mt-4 w-full">
                  <Link href={`/admin/anuncios/${item.id}`}>Abrir análise</Link>
                </Button>
              </article>
            ))}
          </div>

          <div className="hidden overflow-x-auto border border-zinc-200 bg-white md:block">
            <table className="w-full min-w-[860px] text-left text-sm">
              <thead className="border-b border-zinc-200 bg-zinc-50 text-xs uppercase text-zinc-500">
                <tr>
                  <th className="px-4 py-3">Anúncio</th>
                  <th className="px-4 py-3">Proprietário</th>
                  <th className="px-4 py-3">Localização</th>
                  <th className="px-4 py-3">Estado</th>
                  <th className="px-4 py-3">Moderação</th>
                  <th className="px-4 py-3 text-right">Ação</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100">
                {items.map((item) => (
                  <tr key={item.id} className="hover:bg-zinc-50">
                    <td className="px-4 py-3">
                      <p className="max-w-[260px] truncate font-semibold text-zinc-950">{item.titulo}</p>
                      <p className="mt-1 max-w-[260px] truncate text-xs text-zinc-500">{item.slug}</p>
                    </td>
                    <td className="px-4 py-3 text-zinc-700">
                      <p>{item.anunciante?.nome || 'Sem nome'}</p>
                      <p className="text-xs text-zinc-500">{item.anunciante?.emailMascarado || 'E-mail indisponível'}</p>
                    </td>
                    <td className="px-4 py-3 text-zinc-600">{locationLabel(item)}</td>
                    <td className="px-4 py-3"><Badge variant="outline" className={statusTone(item.status)}>{item.status}</Badge></td>
                    <td className="px-4 py-3"><Badge variant="outline" className={statusTone(item.statusModeracao)}>{item.statusModeracao}</Badge></td>
                    <td className="px-4 py-3 text-right"><Button asChild size="sm"><Link href={`/admin/anuncios/${item.id}`}>Analisar</Link></Button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      ) : null}

      {data && data.totalPages > 1 ? (
        <footer className="flex items-center justify-between border-t border-zinc-200 pt-4 text-sm text-zinc-600">
          <span>{data.totalElements} anúncios · página {data.page + 1} de {data.totalPages}</span>
          <div className="flex gap-2">
            <Button type="button" size="icon" variant="outline" aria-label="Página anterior" disabled={page === 0 || loading} onClick={() => setPage((value) => Math.max(0, value - 1))}><ChevronLeft className="h-4 w-4" /></Button>
            <Button type="button" size="icon" variant="outline" aria-label="Próxima página" disabled={data.last || loading} onClick={() => setPage((value) => value + 1)}><ChevronRight className="h-4 w-4" /></Button>
          </div>
        </footer>
      ) : null}
    </div>
  )
}
