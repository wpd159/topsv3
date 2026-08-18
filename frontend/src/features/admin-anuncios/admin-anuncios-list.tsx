'use client'

import Image from 'next/image'
import Link from 'next/link'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { ChevronLeft, ChevronRight, Clapperboard, ExternalLink, RefreshCw, Search, ShieldCheck } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { getAdminSession } from '@/lib/admin-auth-api'
import { maskPhoneBR } from '@/lib/phone-mask'
import { imagemPublicaR2 } from '@/lib/media/public-media'
import { SearchableSelect } from '@/features/anuncio-wizard/components/searchable-select'

import { AdminAnuncioPremiumRapido, premiumBenefitGranted } from './admin-anuncio-premium-rapido'
import { listAdminAdFilterLocations, listAdminAds, listAdminPremiumCatalog, publishAdminStory, reactivateAdminAd } from './api'
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
  { value: 'APROVADOS', label: 'Aprovados' },
  { value: 'PAUSADOS', label: 'Pausados' },
  { value: 'REJEITADOS', label: 'Rejeitados' },
  { value: 'BLOQUEADOS', label: 'Bloqueados' },
]

function statusTone(status: string) {
  if (status === 'APROVADO' || status === 'PUBLICADO') return 'border-emerald-200 bg-emerald-50 text-emerald-800'
  if (status === 'REJEITADO' || status === 'BLOQUEADO' || status === 'REMOVIDO') return 'border-red-200 bg-red-50 text-red-800'
  if (status === 'PENDENTE' || status === 'PENDENTE_REVISAO') return 'border-amber-200 bg-amber-50 text-amber-800'
  return 'border-zinc-200 bg-zinc-50 text-zinc-700'
}

function statusLabel(status: string) {
  return status.toLowerCase().replaceAll('_', ' ').replace(/(^|\s)\S/g, (letter) => letter.toUpperCase())
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
        <Image
          src={item.miniaturaUrl}
          alt=""
          fill
          sizes="56px"
          unoptimized={imagemPublicaR2(item.miniaturaUrl)}
          className="object-cover"
        />
      ) : (
        <div className="flex h-full items-center justify-center text-center text-[10px] font-semibold text-zinc-400">Sem foto</div>
      )}
    </div>
  )
}

function StatusBadges({ item }: { item: AdminAdListItem }) {
  const showModeration = item.status !== item.statusModeracao
    && !(item.status === 'PUBLICADO' && item.statusModeracao === 'APROVADO')
  return (
    <div className="flex flex-wrap gap-1">
      <Badge variant="outline" className={statusTone(item.status)}>{statusLabel(item.status)}</Badge>
      {showModeration ? <Badge variant="outline" className={statusTone(item.statusModeracao)}>{statusLabel(item.statusModeracao)}</Badge> : null}
    </div>
  )
}

function Owner({ item, compact = false }: { item: AdminAdListItem; compact?: boolean }) {
  const owner = item.anunciante
  const digits = owner?.whatsapp?.replace(/\D/g, '')
  const suspended = owner?.status === 'SUSPENSO'
  return (
    <div className={compact ? 'mt-2 text-xs' : 'min-w-[210px] text-xs text-zinc-600'}>
      <p className="font-semibold text-zinc-900">{owner?.nomeCivil || owner?.nome || 'Nome não informado'}</p>
      {suspended ? <Badge variant="outline" className="mt-1 border-amber-300 bg-amber-50 text-amber-900">Proprietário suspenso</Badge> : null}
      <p className="mt-1 break-all">{owner?.email || 'E-mail não informado'}</p>
      {digits ? (
        <a href={`https://wa.me/${digits}`} target="_blank" rel="noreferrer" className="mt-1 inline-flex items-center gap-1 font-medium text-pink-700 hover:underline">
          {maskPhoneBR(owner?.whatsapp || '')}<ExternalLink className="h-3 w-3" aria-hidden="true" />
        </a>
      ) : <p className="mt-1">Não informado</p>}
    </div>
  )
}
function AdminStoryQuickAction({
  item,
  canManage,
  busy,
  onPublish,
}: {
  item: AdminAdListItem
  canManage: boolean
  busy: boolean
  onPublish: (item: AdminAdListItem) => void
}) {
  const action = item.storyAcao
  if (action.estado === 'ATIVO') {
    return (
      <div className="space-y-1">
        <Button type="button" size="sm" variant="outline" className="w-full" disabled>
          <Clapperboard className="mr-2 h-4 w-4" aria-hidden="true" />Story ativo
        </Button>
        {action.expiraEm ? <p className="text-xs text-zinc-500">Expira em {dateLabel(action.expiraEm)}</p> : null}
      </div>
    )
  }

  const enabled = canManage && action.estado === 'ELEGIVEL'
  const reason = canManage
    ? action.motivo || 'Este anúncio ainda não está elegível para Stories.'
    : 'Somente ADMIN pode publicar Stories administrativamente.'

  return (
    <div className="space-y-1">
      <Button
        type="button"
        size="sm"
        variant="outline"
        className="w-full"
        disabled={!enabled || busy}
        title={!enabled ? reason : undefined}
        onClick={() => onPublish(item)}
      >
        <Clapperboard className="mr-2 h-4 w-4" aria-hidden="true" />
        {busy ? 'Adicionando...' : 'Adicionar aos Stories'}
      </Button>
      {!enabled ? <p className="text-xs text-zinc-500">{reason}</p> : null}
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
  const [canManageAds, setCanManageAds] = useState(false)
  const [canManageStories, setCanManageStories] = useState(false)
  const [loading, setLoading] = useState(true)
  const [locationsLoading, setLocationsLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [supportError, setSupportError] = useState<unknown>(null)
  const [locationsError, setLocationsError] = useState<unknown>(null)
  const [actionError, setActionError] = useState<unknown>(null)
  const [busyAdId, setBusyAdId] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [supportReload, setSupportReload] = useState(0)
  const [locationsReload, setLocationsReload] = useState(0)
  const [busyStoryIds, setBusyStoryIds] = useState<Set<string>>(() => new Set())
  const publishingStoryIds = useRef(new Set<string>())
  const storyIdempotencyKeys = useRef(new Map<string, string>())

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
    setSupportError(null)
    const [catalogResult, sessionResult] = await Promise.allSettled([
      listAdminPremiumCatalog(),
      getAdminSession(),
    ])
    if (catalogResult.status === 'fulfilled') {
      setCatalog(catalogResult.value.filter((item) => item.escopo === 'ANUNCIO'))
    } else {
      setCatalog([])
      setSupportError(catalogResult.reason)
    }
    if (sessionResult.status === 'fulfilled') {
      const session = sessionResult.value
      setCanManagePremium(Boolean(session?.papeis.includes('ADMIN') && session?.permissoes.includes('PREMIUM_GERENCIAR')))
      setCanManageAds(Boolean(session?.papeis.includes('ADMIN') && session?.permissoes.includes('ANUNCIO_MODERAR')))
      setCanManageStories(Boolean(session?.papeis.includes('ADMIN')))
    } else {
      setCanManagePremium(false)
      setCanManageAds(false)
      setCanManageStories(false)
      setSupportError(sessionResult.reason)
    }
  }, [])

  const loadLocations = useCallback(async () => {
    setLocationsLoading(true)
    setLocationsError(null)
    try {
      setLocations(await listAdminAdFilterLocations())
    } catch (reason) {
      setLocationsError(reason)
    } finally {
      setLocationsLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load, reload])
  useEffect(() => { void loadSupport() }, [loadSupport, supportReload])
  useEffect(() => { void loadLocations() }, [loadLocations, locationsReload])
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
          .filter((benefit) => premiumBenefitGranted(benefit))
          .map((benefit) => benefit.nome),
      } : item),
    } : current)
  }

  function canReactivate(item: AdminAdListItem) {
    return canManageAds
      && item.status === 'PAUSADO'
      && item.statusModeracao === 'APROVADO'
      && item.anunciante?.status === 'ATIVO'
  }

  async function reactivate(item: AdminAdListItem) {
    if (busyAdId || !canReactivate(item)) return
    setBusyAdId(item.id)
    setActionError(null)
    try {
      await reactivateAdminAd(item.id)
      await load()
    } catch (reason) {
      setActionError(reason)
    } finally {
      setBusyAdId(null)
    }
  }
  async function addToStories(item: AdminAdListItem) {
    if (!canManageStories || item.storyAcao.estado !== 'ELEGIVEL' || publishingStoryIds.current.has(item.id)) return
    publishingStoryIds.current.add(item.id)
    setBusyStoryIds((current) => new Set(current).add(item.id))
    setActionError(null)
    const idempotencyKey = storyIdempotencyKeys.current.get(item.id)
      || `admin-story-${item.id}-${Date.now()}`
    storyIdempotencyKeys.current.set(item.id, idempotencyKey)
    try {
      const story = await publishAdminStory(item.id, idempotencyKey)
      setData((current) => current ? {
        ...current,
        itens: current.itens.map((row) => row.id === item.id ? {
          ...row,
          storyAcao: {
            estado: 'ATIVO',
            storyId: story.storyId,
            expiraEm: story.fimEm,
            motivo: null,
          },
        } : row),
      } : current)
      storyIdempotencyKeys.current.delete(item.id)
    } catch (reason) {
      setActionError(reason)
    } finally {
      publishingStoryIds.current.delete(item.id)
      setBusyStoryIds((current) => {
        const next = new Set(current)
        next.delete(item.id)
        return next
      })
    }
  }


  const states = useMemo(() => uniqueBy(locations, (item) => item.uf), [locations])
  const cities = useMemo(() => context.uf
    ? uniqueBy(locations.filter((item) => item.uf === context.uf), (item) => item.cidadeSlug)
    : [], [context.uf, locations])
  const neighborhoods = useMemo(() => context.cidade
    ? uniqueBy(locations.filter((item) => item.uf === context.uf && item.cidadeSlug === context.cidade && Boolean(item.bairroSlug)), (item) => item.bairroSlug || '')
    : [], [context.cidade, context.uf, locations])
  const cityOptions = useMemo(() => [
    { id: 'TODAS', label: 'Todas' },
    ...cities.map((item) => ({ id: item.cidadeSlug, label: item.cidade })),
  ], [cities])
  const stateOptions = useMemo(() => [
    { id: 'TODOS', label: 'Todos' },
    ...states.map((item) => ({
      id: item.uf,
      label: item.estado ? `${item.estado} (${item.uf})` : item.uf,
    })),
  ], [states])
  const neighborhoodOptions = useMemo(() => [
    { id: 'TODOS', label: 'Todos' },
    ...neighborhoods.map((item) => ({ id: item.bairroSlug || '', label: item.bairro || '' })),
  ], [neighborhoods])
  const selectedCity = cityOptions.find((item) => item.id === (context.cidade || 'TODAS'))
  const selectedNeighborhood = neighborhoodOptions.find((item) => item.id === (context.bairro || 'TODOS'))
  const selectedState = stateOptions.find((item) => item.id === (context.uf || 'TODOS'))
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
        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-[minmax(160px,2fr)_minmax(130px,1.3fr)_minmax(110px,1fr)_minmax(112px,1fr)_minmax(112px,1fr)_minmax(132px,1.1fr)_92px_auto] lg:items-end">
          <label>
            <span className="mb-1 block text-xs font-semibold text-zinc-600">Busca</span>
            <span className="relative block"><Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" aria-hidden="true" /><Input value={searchDraft} onChange={(event) => setSearchDraft(event.target.value)} placeholder="Título, slug ou ID" className="pl-9" /></span>
          </label>
          <label>
            <span className="mb-1 block text-xs font-semibold text-zinc-600">Situação</span>
            <Select value={context.situacao} onValueChange={(value) => update({ situacao: value as AdminAdSituation })}><SelectTrigger className="w-full bg-white"><SelectValue /></SelectTrigger><SelectContent>{SITUATION_OPTIONS.map((option) => <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>)}</SelectContent></Select>
          </label>
          <div>
            <span className="mb-1 block text-xs font-semibold text-zinc-600">Estado</span>
            <SearchableSelect
              value={context.uf || 'TODOS'}
              label={selectedState?.label || 'Todos'}
              placeholder="Todos"
              searchPlaceholder="Pesquisar estado"
              emptyText="Nenhum estado encontrado"
              options={stateOptions}
              disabled={locationsLoading || Boolean(locationsError)}
              onSelect={(value) => update({ uf: value === 'TODOS' ? '' : value, cidade: '', bairro: '' })}
            />
          </div>
          <div>
            <span className="mb-1 block text-xs font-semibold text-zinc-600">Cidade</span>
            <SearchableSelect
              value={context.cidade || 'TODAS'}
              label={selectedCity?.label || 'Todas'}
              placeholder="Todas"
              searchPlaceholder="Pesquisar cidade"
              emptyText="Nenhuma cidade encontrada"
              options={cityOptions}
              disabled={!context.uf || locationsLoading || Boolean(locationsError)}
              onSelect={(value) => update({ cidade: value === 'TODAS' ? '' : value, bairro: '' })}
            />
          </div>
          <div>
            <span className="mb-1 block text-xs font-semibold text-zinc-600">Bairro</span>
            <SearchableSelect
              value={context.bairro || 'TODOS'}
              label={selectedNeighborhood?.label || 'Todos'}
              placeholder="Todos"
              searchPlaceholder="Pesquisar bairro"
              emptyText="Nenhum bairro encontrado"
              options={neighborhoodOptions}
              disabled={!context.cidade || locationsLoading || Boolean(locationsError)}
              onSelect={(value) => update({ bairro: value === 'TODOS' ? '' : value })}
            />
          </div>
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
      {locationsError ? <ContractState error={locationsError} onRetry={() => setLocationsReload((value) => value + 1)} compact /> : null}
      {actionError ? <ContractState error={actionError} compact /> : null}
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
                <div className="mt-3"><AdminAnuncioPremiumRapido anuncioId={item.id} catalog={catalog} benefits={item.beneficiosPremium} canManage={canManagePremium} onChanged={(benefits) => updateRowPremium(item.id, benefits)} onOperationStart={() => setActionError(null)} /></div>
                <div className="mt-2"><AdminStoryQuickAction item={item} canManage={canManageStories} busy={busyStoryIds.has(item.id)} onPublish={(row) => void addToStories(row)} /></div>
                <div className="mt-4 grid gap-2">
                  {canReactivate(item) ? <Button type="button" variant="outline" disabled={Boolean(busyAdId)} onClick={() => void reactivate(item)}>{busyAdId === item.id ? 'Reativando...' : 'Reativar'}</Button> : null}
                  <Button asChild><Link href={adminAdQueueDetailHref(item.id, context)}>Abrir análise</Link></Button>
                </div>
              </article>
            ))}
          </div>

          <div className="hidden overflow-x-auto border border-zinc-200 bg-white md:block [&_thead]:sticky [&_thead]:top-0 [&_thead]:z-10" role="region" aria-label="Tabela de anuncios" tabIndex={0}>
            <table className="w-full min-w-[1320px] text-left text-sm">
              <thead className="border-b border-zinc-200 bg-zinc-50 text-xs uppercase text-zinc-500"><tr><th className="px-4 py-3">Anúncio</th><th className="px-4 py-3">Proprietário</th><th className="px-4 py-3">Localização</th><th className="px-4 py-3">Estado / moderação</th><th className="px-4 py-3">Premium / Stories</th><th className="px-4 py-3">Métricas</th><th className="px-4 py-3">Criação</th><th className="px-4 py-3 text-right">Ação</th></tr></thead>
              <tbody className="divide-y divide-zinc-100">
                {items.map((item) => (
                  <tr key={item.id} className="hover:bg-zinc-50">
                    <td className="px-4 py-3"><div className="flex items-center gap-3"><Thumbnail item={item} /><div className="min-w-0"><p className="max-w-[220px] truncate font-semibold text-zinc-950">{item.titulo}</p><p className="mt-1 max-w-[220px] truncate text-xs text-zinc-500">{item.slug}</p></div></div></td>
                    <td className="px-4 py-3"><Owner item={item} /></td>
                    <td className="px-4 py-3 text-zinc-600">{locationLabel(item)}</td>
                    <td className="px-4 py-3"><StatusBadges item={item} /></td>
                    <td className="px-4 py-3"><div className="min-w-[280px] space-y-3">
                      <AdminAnuncioPremiumRapido anuncioId={item.id} catalog={catalog} benefits={item.beneficiosPremium} canManage={canManagePremium} onChanged={(benefits) => updateRowPremium(item.id, benefits)} onOperationStart={() => setActionError(null)} />
                      <AdminStoryQuickAction item={item} canManage={canManageStories} busy={busyStoryIds.has(item.id)} onPublish={(row) => void addToStories(row)} />
                    </div></td>
                    <td className="px-4 py-3 text-xs text-zinc-700"><strong>{viewsLabel(item)}</strong> views<br /><span>{item.cliquesWhatsapp} cliques</span></td>
                    <td className="px-4 py-3 text-xs text-zinc-600">{dateLabel(item.criadoEm)}</td>
                    <td className="px-4 py-3 text-right"><div className="flex justify-end gap-2">{canReactivate(item) ? <Button type="button" size="sm" variant="outline" disabled={Boolean(busyAdId)} onClick={() => void reactivate(item)}>{busyAdId === item.id ? 'Reativando...' : 'Reativar'}</Button> : null}<Button asChild size="sm"><Link href={adminAdQueueDetailHref(item.id, context)}>Analisar</Link></Button></div></td>
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
