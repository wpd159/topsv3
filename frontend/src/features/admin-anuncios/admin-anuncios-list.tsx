'use client'

import Image from 'next/image'
import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight, RefreshCw, Search, ShieldCheck } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

import { listAdminAds } from './api'
import {
  ADMIN_AD_PAGE_SIZE_OPTIONS,
  ADMIN_AD_SORT_OPTIONS,
  adminAdQueueDetailHref,
  adminAdQueueFilters,
  parseAdminAdQueueContext,
  type AdminAdQueueContext,
} from './queue-context'
import type { AdminAdListItem, AdminPage } from './types'

const MODERATION_OPTIONS = ['TODOS', 'PENDENTE', 'APROVADO', 'REJEITADO', 'NAO_ENVIADO', 'BLOQUEADO']

function statusTone(status: string) {
  if (status === 'APROVADO' || status === 'PUBLICADO') return 'border-emerald-200 bg-emerald-50 text-emerald-800'
  if (status === 'REJEITADO' || status === 'REMOVIDO' || status === 'BLOQUEADO') return 'border-red-200 bg-red-50 text-red-800'
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

function viewsLabel(item: AdminAdListItem) {
  return item.visualizacoes?.situacao === 'HISTORICO_PENDENTE' ? '—' : String(item.visualizacoes?.total ?? '—')
}

function Thumbnail({ item }: { item: AdminAdListItem }) {
  return (
    <div className="relative h-16 w-14 shrink-0 overflow-hidden rounded-md bg-zinc-100">
      {item.miniaturaUrl ? (
        <Image src={item.miniaturaUrl} alt="" fill sizes="56px" className="object-cover" />
      ) : (
        <div className="flex h-full items-center justify-center text-center text-[10px] font-semibold text-zinc-400">Sem foto</div>
      )}
    </div>
  )
}

function StatusBadges({ item }: { item: AdminAdListItem }) {
  const duplicated = item.status === item.statusModeracao
  return (
    <div className="flex flex-wrap gap-1">
      <Badge variant="outline" className={statusTone(item.status)}>{item.status}</Badge>
      {!duplicated ? <Badge variant="outline" className={statusTone(item.statusModeracao)}>{item.statusModeracao}</Badge> : null}
    </div>
  )
}

function PremiumBadges({ benefits }: { benefits: string[] }) {
  if (!benefits.length) return <span>—</span>
  return <div className="flex flex-wrap gap-1">{benefits.map((name) => <Badge key={name} variant="outline" className="border-pink-200 bg-pink-50 text-pink-800">{name}</Badge>)}</div>
}

export function AdminAnunciosList({ initialQuery = '' }: { initialQuery?: string }) {
  const searchParams = new URLSearchParams(initialQuery)
  const [context, setContext] = useState<AdminAdQueueContext>(() => parseAdminAdQueueContext(searchParams))
  const [searchDraft, setSearchDraft] = useState(context.termo)
  const [data, setData] = useState<AdminPage<AdminAdListItem> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [reload, setReload] = useState(0)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await listAdminAds(adminAdQueueFilters(context)))
    } catch (reason) {
      setError(reason)
    } finally {
      setLoading(false)
    }
  }, [context])

  useEffect(() => { void load() }, [load, reload])

  function update(values: Partial<AdminAdQueueContext>, resetPage = true) {
    setContext((current) => ({ ...current, ...values, page: resetPage ? 0 : values.page ?? current.page }))
  }

  function submitSearch(event: React.FormEvent) {
    event.preventDefault()
    update({ termo: searchDraft.trim() })
  }

  const items = data?.itens ?? []

  return (
    <div className="space-y-5">
      <header className="flex flex-col gap-3 border-b border-zinc-200 pb-5 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <div className="flex items-center gap-2 text-sm font-semibold text-pink-700"><ShieldCheck className="h-4 w-4" aria-hidden="true" />Moderação canônica</div>
          <h1 className="mt-1 text-2xl font-bold text-zinc-950">Anúncios</h1>
          <p className="mt-1 text-sm text-zinc-600">Fila operacional com métricas, Premium e decisão individual de mídias.</p>
        </div>
        <Button type="button" variant="outline" onClick={() => setReload((value) => value + 1)} disabled={loading}>
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />Atualizar
        </Button>
      </header>

      <form onSubmit={submitSearch} className="space-y-3 border-b border-zinc-200 pb-5">
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-[minmax(240px,2fr)_repeat(4,minmax(120px,1fr))_auto]">
          <label>
            <span className="mb-1 block text-xs font-semibold text-zinc-600">Busca</span>
            <span className="relative block"><Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" aria-hidden="true" /><Input value={searchDraft} onChange={(event) => setSearchDraft(event.target.value)} placeholder="Título ou slug" className="pl-9" /></span>
          </label>
          <label>
            <span className="mb-1 block text-xs font-semibold text-zinc-600">Moderação</span>
            <Select value={context.statusModeracao} onValueChange={(value) => update({ statusModeracao: value })}><SelectTrigger className="w-full bg-white"><SelectValue /></SelectTrigger><SelectContent>{MODERATION_OPTIONS.map((value) => <SelectItem key={value} value={value}>{value === 'TODOS' ? 'Todas' : value}</SelectItem>)}</SelectContent></Select>
          </label>
          <label><span className="mb-1 block text-xs font-semibold text-zinc-600">UF</span><Input value={context.uf} onChange={(event) => update({ uf: event.target.value.toUpperCase().slice(0, 2) })} placeholder="UF" /></label>
          <label><span className="mb-1 block text-xs font-semibold text-zinc-600">Cidade</span><Input value={context.cidade} onChange={(event) => update({ cidade: event.target.value })} placeholder="Slug da cidade" /></label>
          <label><span className="mb-1 block text-xs font-semibold text-zinc-600">Bairro</span><Input value={context.bairro} onChange={(event) => update({ bairro: event.target.value })} placeholder="Slug do bairro" /></label>
          <Button type="submit" className="self-end">Aplicar</Button>
        </div>
        <div className="grid gap-3 sm:grid-cols-2 sm:justify-end lg:ml-auto lg:max-w-xl">
          <label><span className="mb-1 block text-xs font-semibold text-zinc-600">Ordenar por</span><Select value={context.ordenacao} onValueChange={(value) => update({ ordenacao: value as AdminAdQueueContext['ordenacao'] })}><SelectTrigger className="w-full bg-white"><SelectValue /></SelectTrigger><SelectContent>{ADMIN_AD_SORT_OPTIONS.map((option) => <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>)}</SelectContent></Select></label>
          <label><span className="mb-1 block text-xs font-semibold text-zinc-600">Itens por página</span><Select value={String(context.size)} onValueChange={(value) => update({ size: Number(value) as AdminAdQueueContext['size'] })}><SelectTrigger className="w-full bg-white"><SelectValue /></SelectTrigger><SelectContent>{ADMIN_AD_PAGE_SIZE_OPTIONS.map((size) => <SelectItem key={size} value={String(size)}>{size}</SelectItem>)}</SelectContent></Select></label>
        </div>
      </form>

      {error ? <ContractState error={error} onRetry={() => setReload((value) => value + 1)} /> : null}
      {!error && loading && !data ? <p className="py-12 text-center text-sm text-zinc-500">Carregando fila...</p> : null}
      {!error && !loading && items.length === 0 ? <div className="border border-zinc-200 bg-white px-5 py-10 text-center"><p className="font-semibold text-zinc-900">Nenhum anúncio corresponde aos filtros.</p><p className="mt-1 text-sm text-zinc-600">A fila está legitimamente vazia para esta combinação.</p></div> : null}

      {!error && items.length > 0 ? (
        <>
          <div className="space-y-3 md:hidden">
            {items.map((item) => (
              <article key={item.id} className="border border-zinc-200 bg-white p-4">
                <div className="flex items-start gap-3"><Thumbnail item={item} /><div className="min-w-0 flex-1"><h2 className="truncate font-semibold text-zinc-950">{item.titulo}</h2><p className="mt-1 truncate text-[11px] text-zinc-400">{item.slug}</p><p className="mt-1 text-xs text-zinc-500">{item.anunciante?.nome || item.anunciante?.emailMascarado || 'Proprietário não identificado'}</p></div></div>
                <div className="mt-3"><StatusBadges item={item} /></div>
                <p className="mt-3 text-xs text-zinc-600">{locationLabel(item)}</p>
                <div className="mt-3 grid grid-cols-3 gap-2 text-xs"><div><span className="block text-zinc-500">Visualizações</span><strong>{viewsLabel(item)}</strong></div><div><span className="block text-zinc-500">WhatsApp</span><strong>{item.cliquesWhatsapp}</strong></div><div><span className="block text-zinc-500">Criado</span><strong>{dateLabel(item.criadoEm)}</strong></div></div>
                <div className="mt-3 text-xs"><PremiumBadges benefits={item.beneficiosPremiumVigentes} /></div>
                <Button asChild className="mt-4 w-full"><Link href={adminAdQueueDetailHref(item.id, context)}>Abrir análise</Link></Button>
              </article>
            ))}
          </div>

          <div className="hidden overflow-x-auto border border-zinc-200 bg-white md:block">
            <table className="w-full min-w-[1100px] text-left text-sm">
              <thead className="border-b border-zinc-200 bg-zinc-50 text-xs uppercase text-zinc-500"><tr><th className="px-4 py-3">Anúncio</th><th className="px-4 py-3">Proprietário</th><th className="px-4 py-3">Localização</th><th className="px-4 py-3">Estado / moderação</th><th className="px-4 py-3">Premium</th><th className="px-4 py-3">Métricas</th><th className="px-4 py-3">Criação</th><th className="px-4 py-3 text-right">Ação</th></tr></thead>
              <tbody className="divide-y divide-zinc-100">
                {items.map((item) => (
                  <tr key={item.id} className="hover:bg-zinc-50">
                    <td className="px-4 py-3"><div className="flex items-center gap-3"><Thumbnail item={item} /><div className="min-w-0"><p className="max-w-[220px] truncate font-semibold text-zinc-950">{item.titulo}</p><p className="mt-1 max-w-[220px] truncate text-xs text-zinc-500">{item.slug}</p></div></div></td>
                    <td className="px-4 py-3 text-zinc-700"><p>{item.anunciante?.nome || 'Sem nome'}</p><p className="text-xs text-zinc-500">{item.anunciante?.emailMascarado || 'E-mail indisponível'}</p></td>
                    <td className="px-4 py-3 text-zinc-600">{locationLabel(item)}</td>
                    <td className="px-4 py-3"><StatusBadges item={item} /></td>
                    <td className="max-w-[240px] px-4 py-3 text-xs text-zinc-700"><PremiumBadges benefits={item.beneficiosPremiumVigentes} /></td>
                    <td className="px-4 py-3 text-xs text-zinc-700"><strong>{viewsLabel(item)}</strong> views<br /><span>{item.cliquesWhatsapp} cliques</span></td>
                    <td className="px-4 py-3 text-xs text-zinc-600">{dateLabel(item.criadoEm)}</td>
                    <td className="px-4 py-3 text-right"><Button asChild size="sm"><Link href={adminAdQueueDetailHref(item.id, context)}>Analisar</Link></Button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      ) : null}

      {data ? (
        <footer className="flex flex-col gap-3 border-t border-zinc-200 pt-4 text-sm text-zinc-600 sm:flex-row sm:items-center sm:justify-between">
          <span>{data.totalElements} anúncios · página {data.totalPages === 0 ? 0 : data.page + 1} de {data.totalPages}</span>
          <div className="flex gap-2"><Button type="button" size="icon" variant="outline" aria-label="Página anterior" disabled={context.page === 0 || loading} onClick={() => update({ page: Math.max(0, context.page - 1) }, false)}><ChevronLeft className="h-4 w-4" /></Button><Button type="button" size="icon" variant="outline" aria-label="Próxima página" disabled={data.last || loading} onClick={() => update({ page: context.page + 1 }, false)}><ChevronRight className="h-4 w-4" /></Button></div>
        </footer>
      ) : null}
    </div>
  )
}
