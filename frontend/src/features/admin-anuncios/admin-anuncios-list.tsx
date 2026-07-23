'use client'

import Image from 'next/image'
import Link from 'next/link'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { ChevronLeft, ChevronRight, ExternalLink, RefreshCw, Search, ShieldCheck } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { getAdminSession } from '@/lib/admin-auth-api'

import { AdminAnuncioPremiumRapido } from './admin-anuncio-premium-rapido'
import { listAdminAdFilterLocations, listAdminAds, listAdminPremiumCatalog } from './api'
import {
  ADMIN_AD_PAGE_SIZE_OPTIONS,
  ADMIN_AD_SORT_OPTIONS,
  adminAdQueueDetailHref,
  adminAdQueueFilters,
  adminAdQueueListHref,
  parseAdminAdQueueContext,
  type AdminAdQueueContext,
} from './queue-context'
import type {
  AdminAdListItem,
  AdminAdSituation,
  AdminFilterLocation,
  AdminPage,
  AdminPremiumCatalogItem,
  AdminQueuePremiumBenefit,
} from './types'

const SITUATION_OPTIONS: Array<{ value: AdminAdSituation; label: string }> = [
  { value: 'TODOS', label: 'Todos' },
  { value: 'PENDENTES_MODERACAO', label: 'Pendentes de moderação' },
  { value: 'PAUSADOS', label: 'Pausados' },
  { value: 'REJEITADOS', label: 'Rejeitados' },
  { value: 'BLOQUEADOS', label: 'Bloqueados' },
]

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

function Owner({ item, compact = false }: { item: AdminAdListItem; compact?: boolean }) {
  const owner = item.anunciante
  const digits = owner?.whatsapp?.replace(/\D/g, '')
  return (
    <div className={compact ? 'mt-2 text-xs' : 'min-w-[210px] text-xs text-zinc-600'}>
      <p className="font-semibold text-zinc-900">{owner?.nomeCivil || owner?.nome || 'Nome não informado'}</p>
      <p className="mt-1 break-all">{owner?.email || 'E-mail não informado'}</p>
      {digits ? (
        <a href={`https://wa.me/${digits}`} target="_blank" rel="noreferrer" className="mt-1 inline-flex items-center gap-1 font-medium text-pink-700 hover:underline">
          {owner?.whatsapp}<ExternalLink className="h-3 w-3" aria-hidden="true" />
        </a>
      ) : <p className="mt-1">Não informado</p>}
    </div>
  )
}

function uniqueBy<T>(items: T[], key: (item: T) => string) {
  return [...new Map(items.map((item) => [key(item), item])).values()]
}

export function AdminAnunciosList({ initialQuery = '' }: { initialQuery?: string }) {
  const searchParams = new URLSearchParams(initialQuery)
  const [context, setContext] = useState<AdminAdQueueContext>(() => parseAdminAdQueueContext(searchParams))
  const [searchDraft, setSearchDraft] = useState(context.termo)
  const [data, setData] = useState<AdminPage<AdminAdListItem> | null>(null)
  const [locations, setLocations] = useState<AdminFilterLocation[]>([])
  const [catalog, setCatalog] = useState<AdminPremiumCatalogItem[]>([])
  const [canManagePremium, setCanManagePremium] = useState(false)
  const [loading, setLoading] = useState(true)
  const [supportLoading, setSupportLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [supportError, setSupportError] = useState<unknown>(null)
  const [reload, setReload] = useState(0)
  const [supportReload, setSupportReload] = useState(0)

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

  const loadSupport = useCallback(async () => {
    setSupportLoading(true)
    setSupportError(null)
    try {
      const [locationResponse, catalogResponse, session] = await Promise.all([
        listAdminAdFilterLocations(),
        listAdminPremiumCatalog(),
        getAdminSession(),
      ])
      setLocations(locationResponse)
      setCatalog(catalogResponse.filter((item) => item.escopo === 'ANUNCIO'))
      setCanManagePremium(Boolean(session?.papeis.includes('ADMIN') && session?.permissoes.includes('PREMIUM_GERENCIAR')))
    } catch (reason) {
      setSupportError(reason)
    } finally {
      setSupportLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load, reload])
  useEffect(() => { void loadSupport() }, [loadSupport, supportReload])
  useEffect(() => {
    globalThis.history?.replaceState(null, '', adminAdQueueListHref(context))
  }, [context])

  function update(values: Partial<AdminAdQueueContext>, resetPage = true) {
    setContext((current) => ({ ...current, ...values, page: resetPage ? 0 : values.page ?? current.page }))
  }

  function submitSearch(event: React.FormEvent) {
    event.preventDefault()
    update({ termo: searchDraft.trim() })
  }

  function updateRowPremium(id: string, benefits: AdminQueuePremiumBenefit[]) {
    setData((current) => current ? {
      ...current,
      itens: current.itens.map((item) => item.id === id ? {
        ...item,
        beneficiosPremium: benefits,
        beneficiosPremiumVigentes: benefits
          .filter((benefit) => ['ATIVO', 'VENCENDO'].includes(benefit.status))
          .map((benefit) => benefit.nome),
      } : item),
    } : current)
  }

  const states = useMemo(() => uniqueBy(locations, (item) => item.uf), [locations])
  const cities = useMemo(() => context.uf
    ? uniqueBy(locations.filter((item) => item.uf === context.uf), (item) => item.cidadeSlug)
    : [], [context.uf, locations])
  const neighborhoods = useMemo(() => context.cidade
    ? uniqueBy(locations.filter((item) => item.uf === context.uf && item.cidadeSlug === context.cidade && Boolean(item.bairroSlug)), (item) => item.bairroSlug || '')
    : [], [context.cidade, context.uf, locations])
  const items = data?.itens ?? []

  return (
    <div className="space-y-4">
      <header className="flex flex-col gap-3 border-b border-zinc-200 pb-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <div className="flex items-center gap-2 text-sm font-semibold text-pink-700"><ShieldCheck className="h-4 w-4" aria-hidden="true" />Moderação canônica</div>
          <h1 className="mt-1 text-2xl font-bold text-zinc-950">Anúncios</h1>
          <p className="mt-1 text-sm text-zinc-600">Fila operacional com métricas, Premium e decisão individual de mídias.</p>
        </div>
        <Button type="button" variant="outline" onClick={() => setReload((value) => value + 1)} disabled={loading}>
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />Atualizar
        </Button>
      </header>

      <form onSubmit={submitSearch} className="border-b border-zinc-200 pb-4">
        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-[minmax(160px,2fr)_minmax(130px,1.3fr)_64px_minmax(112px,1fr)_minmax(112px,1fr)_minmax(132px,1.1fr)_92px_auto] lg:items-end">
          <label>
            <span className="mb-1 block text-xs font-semibold text-zinc-600">Busca</span>
            <span className="relative block"><Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" aria-hidden="true" /><Input value={searchDraft} onChange={(event) => setSearchDraft(event.target.value)} placeholder="Título ou slug" className="pl-9" /></span>
          </label>
          <label>
            <span className="mb-1 block text-xs font-semibold text-zinc-600">Situação</span>
            <Select value={context.situacao} onValueChange={(value) => update({ situacao: value as AdminAdSituation })}><SelectTrigger className="w-full bg-white"><SelectValue /></SelectTrigger><SelectContent>{SITUATION_OPTIONS.map((option) => <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>)}</SelectContent></Select>
          </label>
          <label>
            <span className="mb-1 block text-xs font-semibold text-zinc-600">UF</span>
            <Select value={context.uf || 'TODOS'} disabled={supportLoading || Boolean(supportError)} onValueChange={(value) => update({ uf: value === 'TODOS' ? '' : value, cidade: '', bairro: '' })}><SelectTrigger className="w-full bg-white"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="TODOS">Todas</SelectItem>{states.map((item) => <SelectItem key={item.uf} value={item.uf}>{item.uf}</SelectItem>)}</SelectContent></Select>
          </label>
          <label>
            <span className="mb-1 block text-xs font-semibold text-zinc-600">Cidade</span>
            <Select value={context.cidade || 'TODAS'} disabled={!context.uf || supportLoading || Boolean(supportError)} onValueChange={(value) => update({ cidade: value === 'TODAS' ? '' : value, bairro: '' })}><SelectTrigger className="w-full bg-white"><SelectValue placeholder="Todas" /></SelectTrigger><SelectContent><SelectItem value="TODAS">Todas</SelectItem>{cities.map((item) => <SelectItem key={item.cidadeSlug} value={item.cidadeSlug}>{item.cidade}</SelectItem>)}</SelectContent></Select>
          </label>
          <label>
            <span className="mb-1 block text-xs font-semibold text-zinc-600">Bairro</span>
            <Select value={context.bairro || 'TODOS'} disabled={!context.cidade || supportLoading || Boolean(supportError)} onValueChange={(value) => update({ bairro: value === 'TODOS' ? '' : value })}><SelectTrigger className="w-full bg-white"><SelectValue placeholder="Todos" /></SelectTrigger><SelectContent><SelectItem value="TODOS">Todos</SelectItem>{neighborhoods.map((item) => <SelectItem key={item.bairroSlug} value={item.bairroSlug || ''}>{item.bairro}</SelectItem>)}</SelectContent></Select>
          </label>
          <label>
            <span className="mb-1 block text-xs font-semibold text-zinc-600">Ordenar por</span>
            <Select value={context.ordenacao} onValueChange={(value) => update({ ordenacao: value as AdminAdQueueContext['ordenacao'] })}><SelectTrigger className="w-full bg-white"><SelectValue /></SelectTrigger><SelectContent>{ADMIN_AD_SORT_OPTIONS.map((option) => <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>)}</SelectContent></Select>
          </label>
          <label>
            <span className="mb-1 block text-xs font-semibold text-zinc-600">Itens por página</span>
            <Select value={String(context.size)} onValueChange={(value) => update({ size: Number(value) as AdminAdQueueContext['size'] })}><SelectTrigger className="w-full bg-white"><SelectValue /></SelectTrigger><SelectContent>{ADMIN_AD_PAGE_SIZE_OPTIONS.map((size) => <SelectItem key={size} value={String(size)}>{size}</SelectItem>)}</SelectContent></Select>
          </label>
          <Button type="submit">Aplicar</Button>
        </div>
      </form>

      {supportError ? <ContractState error={supportError} onRetry={() => setSupportReload((value) => value + 1)} compact /> : null}
      {error ? <ContractState error={error} onRetry={() => setReload((value) => value + 1)} /> : null}
      {!error && loading && !data ? <p className="py-12 text-center text-sm text-zinc-500">Carregando fila...</p> : null}
      {!error && !loading && items.length === 0 ? <div className="border border-zinc-200 bg-white px-5 py-10 text-center"><p className="font-semibold text-zinc-900">Nenhum anúncio corresponde aos filtros.</p><p className="mt-1 text-sm text-zinc-600">A fila está legitimamente vazia para esta combinação.</p></div> : null}

      {!error && items.length > 0 ? (
        <>
          <div className="space-y-3 md:hidden">
            {items.map((item) => (
              <article key={item.id} className="border border-zinc-200 bg-white p-4">
                <div className="flex items-start gap-3"><Thumbnail item={item} /><div className="min-w-0 flex-1"><h2 className="truncate font-semibold text-zinc-950">{item.titulo}</h2><p className="mt-1 truncate text-[11px] text-zinc-400">{item.slug}</p><Owner item={item} compact /></div></div>
                <div className="mt-3"><StatusBadges item={item} /></div>
                <p className="mt-3 text-xs text-zinc-600">{locationLabel(item)}</p>
                <div className="mt-3 grid grid-cols-3 gap-2 text-xs"><div><span className="block text-zinc-500">Visualizações</span><strong>{viewsLabel(item)}</strong></div><div><span className="block text-zinc-500">WhatsApp</span><strong>{item.cliquesWhatsapp}</strong></div><div><span className="block text-zinc-500">Criado</span><strong>{dateLabel(item.criadoEm)}</strong></div></div>
                <div className="mt-3"><AdminAnuncioPremiumRapido anuncioId={item.id} catalog={catalog} benefits={item.beneficiosPremium} canManage={canManagePremium} onChanged={(benefits) => updateRowPremium(item.id, benefits)} /></div>
                <Button asChild className="mt-4 w-full"><Link href={adminAdQueueDetailHref(item.id, context)}>Abrir análise</Link></Button>
              </article>
            ))}
          </div>

          <div className="hidden overflow-x-auto border border-zinc-200 bg-white md:block">
            <table className="w-full min-w-[1320px] text-left text-sm">
              <thead className="border-b border-zinc-200 bg-zinc-50 text-xs uppercase text-zinc-500"><tr><th className="px-4 py-3">Anúncio</th><th className="px-4 py-3">Proprietário</th><th className="px-4 py-3">Localização</th><th className="px-4 py-3">Estado / moderação</th><th className="px-4 py-3">Premium</th><th className="px-4 py-3">Métricas</th><th className="px-4 py-3">Criação</th><th className="px-4 py-3 text-right">Ação</th></tr></thead>
              <tbody className="divide-y divide-zinc-100">
                {items.map((item) => (
                  <tr key={item.id} className="hover:bg-zinc-50">
                    <td className="px-4 py-3"><div className="flex items-center gap-3"><Thumbnail item={item} /><div className="min-w-0"><p className="max-w-[220px] truncate font-semibold text-zinc-950">{item.titulo}</p><p className="mt-1 max-w-[220px] truncate text-xs text-zinc-500">{item.slug}</p></div></div></td>
                    <td className="px-4 py-3"><Owner item={item} /></td>
                    <td className="px-4 py-3 text-zinc-600">{locationLabel(item)}</td>
                    <td className="px-4 py-3"><StatusBadges item={item} /></td>
                    <td className="px-4 py-3"><AdminAnuncioPremiumRapido anuncioId={item.id} catalog={catalog} benefits={item.beneficiosPremium} canManage={canManagePremium} onChanged={(benefits) => updateRowPremium(item.id, benefits)} /></td>
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
