'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { toast } from 'sonner'
import { fetchPremiumBenefitsDashboardAds } from '@/lib/admin-premium-benefits-api'
import {
  matchesAltoTrafegoZeroCliqueAlert,
  matchesComViewsSemClique,
  matchesStrategicAltoTrafego,
  matchesStrategicBaixaEficiencia,
} from '@/app/(painel-admin)/admin/components/dashboard/strategic/strategic-dashboard-utils'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Pagination, PaginationContent, PaginationItem, PaginationNext, PaginationPrevious } from '@/components/ui/pagination'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  ArrowRightIcon,
  CheckCircleIcon,
  ClockIcon,
  ExclamationCircleIcon,
  PhotoIcon,
  PlayCircleIcon,
} from '@heroicons/react/24/solid'
import {
  ativarPremiumBeneficioAdmin,
  desativarPremiumBeneficioAdmin,
  fetchPremiumAnuncioDetailAdmin,
  fetchStaffAnunciosList,
  fetchStaffRevisions,
} from '../api/client'
import type { ModerationRevisionQueueItem, ModerationStaffListItem } from '../api/types'
import {
  matchesFilter,
  paginate,
  sortModerationRows,
  totalPages,
  type ModerationListFilter,
  type ModerationListSort,
} from '../lib/queue'
import {
  MOD_V2_QUERY_CIDADE,
  MOD_V2_QUERY_FILTRO,
  hasDashboardUrlDrilldown,
  parseCidadeQuery,
  parseDashboardStrategicFiltro,
  premiumDashboardFilterForStrategic,
} from '../lib/url-dashboard-filters'

const PAGE_SIZE_OPTIONS = [20, 30, 50, 100] as const
const MODERATION_V2_CONTEXT_STORAGE_KEY = 'moderacao-v2:list-context'

function formatCriadoLista(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function nMetric(v: unknown): number {
  if (typeof v === 'number' && Number.isFinite(v)) return Math.max(0, Math.floor(v))
  if (typeof v === 'string' && v.trim() !== '' && !Number.isNaN(Number(v))) {
    return Math.max(0, Math.floor(Number(v)))
  }
  return 0
}

function formatMetric(v: unknown): string {
  if (v === undefined || v === null) return '—'
  const n = nMetric(v)
  return String(n)
}

const THUMB_BOX =
  'relative h-[4.5rem] w-[4.5rem] shrink-0 overflow-hidden rounded-lg border border-gray-200 sm:h-[5.25rem] sm:w-[5.25rem]'

function QueueRowThumb({
  url,
  isVideo,
  title,
}: {
  url?: string | null
  isVideo?: boolean | null
  title: string
}) {
  const u = (url ?? '').trim()
  if (!u) {
    return (
      <div
        className="flex h-[4.5rem] w-[4.5rem] shrink-0 items-center justify-center rounded-lg border border-gray-200 bg-gray-100 text-gray-400 sm:h-[5.25rem] sm:w-[5.25rem]"
        aria-hidden
      >
        <PhotoIcon className="h-6 w-6 sm:h-7 sm:w-7" />
      </div>
    )
  }
  if (isVideo) {
    return (
      <div className={`${THUMB_BOX} bg-black`}>
        <video
          src={u}
          className="h-full w-full object-cover"
          muted
          playsInline
          preload="metadata"
          aria-label={`Vídeo de capa — ${title}`}
        />
        <PlayCircleIcon className="pointer-events-none absolute inset-0 m-auto h-7 w-7 text-white/90 drop-shadow sm:h-8 sm:w-8" />
      </div>
    )
  }
  return (
    <div className={`${THUMB_BOX} bg-gray-100`}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={u} alt="" className="h-full w-full object-cover" loading="lazy" decoding="async" />
    </div>
  )
}

const FILTER_OPTIONS: Array<{ value: ModerationListFilter; labelBase: string }> = [
  { value: 'fila', labelBase: 'Fila de moderação' },
  { value: 'revisao', labelBase: 'Só com revisão pendente' },
  { value: 'pendente', labelBase: 'Pendentes' },
  { value: 'ativo', labelBase: 'Ativos' },
  { value: 'rejeitado', labelBase: 'Rejeitados' },
  { value: 'todos', labelBase: 'Todos' },
]

const SORT_OPTIONS: Array<{ value: ModerationListSort; label: string }> = [
  { value: 'recent', label: 'Mais recentes' },
  { value: 'oldest', label: 'Mais antigos' },
  { value: 'views_desc', label: 'Mais visualizações' },
  { value: 'views_asc', label: 'Menos visualizações' },
  { value: 'wa_desc', label: 'Mais cliques WhatsApp' },
  { value: 'wa_asc', label: 'Menos cliques WhatsApp' },
]

const PREMIUM_QUICK_OPTIONS = [
  { codigo: 'ANUNCIO_TOPO', short: 'D', label: 'Destaque' },
  { codigo: 'CARROSSEL_FOTOS', short: 'C', label: 'Carrossel' },
  { codigo: 'VIDEO_1', short: 'V', label: 'Vídeo' },
  { codigo: 'WHATSAPP_CARD', short: 'W', label: 'WhatsApp' },
  { codigo: 'FOTOS_EXTRA_5', short: 'F', label: 'Fotos' },
  { codigo: 'OCULTAR_IDADE', short: 'I', label: 'Ocultar idade' },
] as const

type PremiumQuickCode = (typeof PREMIUM_QUICK_OPTIONS)[number]['codigo']
const STORIES_BENEFIT_CODE = 'STORIES'

function normalizePremiumCode(codigo: string | null | undefined) {
  const normalized = (codigo ?? '').trim().toUpperCase()
  return normalized === 'DESTAQUE' ? 'ANUNCIO_TOPO' : normalized
}

function isPremiumQuickVisibleCode(codigo: string | null | undefined) {
  return normalizePremiumCode(codigo) !== STORIES_BENEFIT_CODE
}

type PremiumQuickActiveRow = {
  id?: number | null
  codigo?: string | null
  status?: string | null
  podeDesativar?: boolean | null
  origem?: string | null
  manual?: boolean | null
}

type PremiumQuickCatalogRow = {
  codigo?: string | null
  duracaoHoras?: number | null
}

const premiumQuickDetailCache = new Map<number, Record<string, unknown>>()
const premiumQuickDetailPromises = new Map<number, Promise<Record<string, unknown>>>()

function premiumActiveRows(detail: Record<string, unknown> | null): PremiumQuickActiveRow[] {
  const value = detail?.beneficiosAtivos
  return Array.isArray(value) ? (value as PremiumQuickActiveRow[]).filter((row) => isPremiumQuickVisibleCode(row.codigo)) : []
}

function premiumCatalogRows(detail: Record<string, unknown> | null): PremiumQuickCatalogRow[] {
  const value = detail?.catalogo
  return Array.isArray(value) ? (value as PremiumQuickCatalogRow[]).filter((row) => isPremiumQuickVisibleCode(row.codigo)) : []
}

async function loadPremiumQuickDetail(anuncioId: number, force = false): Promise<Record<string, unknown>> {
  if (!force) {
    const cached = premiumQuickDetailCache.get(anuncioId)
    if (cached) return cached
    const pending = premiumQuickDetailPromises.get(anuncioId)
    if (pending) return pending
  }
  const pending = fetchPremiumAnuncioDetailAdmin(anuncioId)
    .then((detail) => {
      premiumQuickDetailCache.set(anuncioId, detail)
      return detail
    })
    .finally(() => {
      premiumQuickDetailPromises.delete(anuncioId)
    })
  premiumQuickDetailPromises.set(anuncioId, pending)
  return pending
}

function notifyPremiumQuickUpdated(anuncioId: number) {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent('moderacao-v2-premium-quick-updated', { detail: { anuncioId } }))
}

function PremiumQuickActions({ anuncioId, disabled }: { anuncioId: number; disabled?: boolean | null }) {
  const [detail, setDetail] = useState<Record<string, unknown> | null>(null)
  const [loading, setLoading] = useState(true)
  const [actionCode, setActionCode] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async (force = false) => {
    setLoading(true)
    setError(null)
    try {
      setDetail(await loadPremiumQuickDetail(anuncioId, force))
    } catch (e) {
      setDetail(null)
      setError(e instanceof Error ? e.message : 'Falha ao carregar benefícios.')
    } finally {
      setLoading(false)
    }
  }, [anuncioId])

  useEffect(() => {
    void refresh(true)
  }, [refresh])

  useEffect(() => {
    const handler = (event: Event) => {
      const payload = (event as CustomEvent<{ anuncioId?: number; force?: boolean }>).detail
      if (payload?.anuncioId !== anuncioId) return
      const cached = premiumQuickDetailCache.get(anuncioId)
      if (cached) setDetail(cached)
      if (payload?.force) {
        void refresh(true)
      }
    }
    window.addEventListener('moderacao-v2-premium-quick-updated', handler)
    return () => window.removeEventListener('moderacao-v2-premium-quick-updated', handler)
  }, [anuncioId, refresh])

  const activeByCode = useMemo(() => {
    const map = new Map<string, PremiumQuickActiveRow>()
    for (const row of premiumActiveRows(detail)) {
      const codigo = normalizePremiumCode(row.codigo)
      if (codigo) map.set(codigo, row)
    }
    return map
  }, [detail])

  const catalogByCode = useMemo(() => {
    const map = new Map<string, PremiumQuickCatalogRow>()
    for (const row of premiumCatalogRows(detail)) {
      const codigo = normalizePremiumCode(row.codigo)
      if (codigo) map.set(codigo, row)
    }
    return map
  }, [detail])

  const toggle = async (codigo: PremiumQuickCode) => {
    if (disabled || loading || actionCode) return
    const active = activeByCode.get(codigo)
    setActionCode(codigo)
    setError(null)
    try {
      if (active) {
        if (!active.podeDesativar || active.id == null) {
          toast.warning('Este benefício está ativo, mas não pode ser desativado por este atalho.')
          return
        }
        if (active.manual === false && typeof window !== 'undefined') {
          const confirmar = window.confirm(
            `Desativar ${codigo}? Esta é uma ação administrativa excepcional para um benefício comprado. O efeito será removido, mas os dados do anúncio serão preservados.`
          )
          if (!confirmar) return
        }
        const next = await desativarPremiumBeneficioAdmin(Number(active.id), {
          motivo: 'Desativação manual — moderação v2',
          observacaoInterna: 'Atalho rápido da lista de moderação v2',
        })
        premiumQuickDetailCache.set(anuncioId, next)
        setDetail(next)
        notifyPremiumQuickUpdated(anuncioId)
        toast.success(`${codigo} desativado.`)
        return
      }
      const catalogItem = catalogByCode.get(codigo)
      const next = await ativarPremiumBeneficioAdmin(anuncioId, {
        codigo,
        ...(codigo === 'ANUNCIO_TOPO' && catalogItem?.duracaoHoras ? { duracaoHoras: catalogItem.duracaoHoras } : {}),
        observacaoInterna: 'Atalho rápido da lista de moderação v2',
      })
      premiumQuickDetailCache.set(anuncioId, next)
      setDetail(next)
      notifyPremiumQuickUpdated(anuncioId)
      toast.success(`${codigo} ativado.`)
    } catch (e) {
      const message = e instanceof Error ? e.message : 'Falha ao alterar benefício.'
      setError(message)
      toast.error(message)
      await refresh(true).catch(() => undefined)
    } finally {
      setActionCode(null)
    }
  }

  if (loading) {
    return <div className="text-[10px] leading-tight text-gray-400">Premium...</div>
  }

  if (error) {
    return (
      <button
        type="button"
        className="text-left text-[10px] leading-tight text-rose-600 underline"
        onClick={() => void refresh(true)}
      >
        Recarregar premium
      </button>
    )
  }

  return (
    <div className="flex max-w-[9.5rem] flex-wrap gap-1 md:max-w-[18rem] md:justify-start" aria-label="Atalhos premium">
      {PREMIUM_QUICK_OPTIONS.map((opt) => {
        const active = activeByCode.get(opt.codigo)
        const isActive = Boolean(active)
        const canToggle = !disabled && !actionCode && (!isActive || Boolean(active?.podeDesativar && active.id != null))
        const busy = actionCode === opt.codigo
        return (
          <button
            key={opt.codigo}
            type="button"
            aria-pressed={isActive}
            title={`${opt.label}${isActive && !canToggle ? ' — ativo sem revogação rápida' : ''}`}
            disabled={!canToggle || busy}
            onClick={() => void toggle(opt.codigo)}
            className={[
              'inline-flex h-6 w-6 items-center justify-center rounded-md border text-[10px] font-semibold transition-colors md:h-7 md:w-auto md:px-2 md:text-[11px]',
              isActive
                ? 'border-pink-600 bg-pink-600 text-white shadow-sm hover:border-pink-700 hover:bg-pink-700'
                : 'border-gray-200 bg-white text-gray-600 hover:border-pink-200 hover:bg-pink-50 hover:text-pink-700',
              busy ? 'animate-pulse border-pink-300 bg-pink-50 text-pink-700' : '',
              !canToggle && !busy ? 'cursor-not-allowed opacity-60' : 'cursor-pointer',
            ].join(' ')}
          >
            {busy ? (
              '...'
            ) : (
              <>
                <span className="md:hidden">{opt.short}</span>
                <span className="hidden md:inline">{opt.label}</span>
              </>
            )}
          </button>
        )
      })}
    </div>
  )
}

export function ModeracaoV2List() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const searchParamsKey = searchParams.toString()
  const strategicFiltro = parseDashboardStrategicFiltro(searchParams.get(MOD_V2_QUERY_FILTRO))
  const cidadeQuery = parseCidadeQuery(searchParams.get(MOD_V2_QUERY_CIDADE))

  const [rows, setRows] = useState<ModerationStaffListItem[]>([])
  const [revisionQueue, setRevisionQueue] = useState<ModerationRevisionQueueItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busca, setBusca] = useState('')
  const [filter, setFilter] = useState<ModerationListFilter>('fila')
  const [sortBy, setSortBy] = useState<ModerationListSort>('recent')
  const [pageSize, setPageSize] = useState(30)
  const [page, setPage] = useState(1)
  const [premiumDrilldownIds, setPremiumDrilldownIds] = useState<Set<number> | null>(null)
  const [premiumDrilldownLoading, setPremiumDrilldownLoading] = useState(false)
  const [premiumDrilldownError, setPremiumDrilldownError] = useState<string | null>(null)

  const premiumApiFilter = premiumDashboardFilterForStrategic(strategicFiltro)
  const hasDrilldown = hasDashboardUrlDrilldown(strategicFiltro, cidadeQuery)
  /** Cruzar drill-down sempre com “todos” os status — evita interseção acidental com “fila” no 1º render. */
  const moderationFilterForRows: ModerationListFilter = hasDrilldown ? 'todos' : filter
  const operationalFilterActive: ModerationListFilter = hasDrilldown ? 'todos' : filter

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [staff, revs] = await Promise.all([
        fetchStaffAnunciosList(),
        fetchStaffRevisions().catch(() => [] as ModerationRevisionQueueItem[]),
      ])
      setRows(Array.isArray(staff) ? staff : [])
      setRevisionQueue(Array.isArray(revs) ? revs : [])
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Erro ao carregar.')
      setRows([])
      setRevisionQueue([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (!premiumApiFilter) {
      setPremiumDrilldownIds(null)
      setPremiumDrilldownError(null)
      setPremiumDrilldownLoading(false)
      return
    }
    let cancelled = false
    setPremiumDrilldownLoading(true)
    setPremiumDrilldownError(null)
    ;(async () => {
      try {
        const ads = await fetchPremiumBenefitsDashboardAds(premiumApiFilter)
        if (cancelled) return
        setPremiumDrilldownIds(new Set(ads.map((a) => Number(a.id)).filter(Number.isFinite)))
      } catch {
        if (!cancelled) {
          setPremiumDrilldownIds(null)
          setPremiumDrilldownError(
            'Não foi possível carregar a lista oficial do painel premium (exige sessão admin).'
          )
        }
      } finally {
        if (!cancelled) setPremiumDrilldownLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [premiumApiFilter])

  useEffect(() => {
    const onRev = () => void load()
    window.addEventListener('admin-revisions-updated', onRev)
    return () => window.removeEventListener('admin-revisions-updated', onRev)
  }, [load])

  useEffect(() => {
    setPage(1)
  }, [filter, busca, pageSize, sortBy, strategicFiltro, cidadeQuery, premiumDrilldownIds, searchParamsKey])

  const revisionAnuncioIds = useMemo(
    () => new Set(revisionQueue.map((q) => Number(q.anuncioId)).filter(Number.isFinite)),
    [revisionQueue]
  )

  /** Contagens sobre a lista já carregada (ign busca — alinhado ao backend via revisionAnuncioIds). */
  const filterCounts = useMemo(() => {
    const keys: ModerationListFilter[] = [
      'fila',
      'revisao',
      'pendente',
      'ativo',
      'rejeitado',
      'todos',
    ]
    const out: Record<ModerationListFilter, number> = {
      fila: 0,
      revisao: 0,
      pendente: 0,
      ativo: 0,
      rejeitado: 0,
      todos: 0,
    }
    for (const r of rows) {
      for (const k of keys) {
        if (matchesFilter(r, k, revisionAnuncioIds)) out[k] += 1
      }
    }
    return out
  }, [rows, revisionAnuncioIds])

  const filtered = useMemo(() => {
    const q = busca.trim().toLowerCase()
    let list = rows.filter((r) => {
      if (!matchesFilter(r, moderationFilterForRows, revisionAnuncioIds)) return false
      if (!q) return true
      return (
        (r.titulo || '').toLowerCase().includes(q) ||
        (r.usernameAnunciante || '').toLowerCase().includes(q) ||
        (r.status || '').toLowerCase().includes(q)
      )
    })

    const afterOperational = list.length

    if (premiumApiFilter) {
      if (premiumDrilldownError) {
        list = []
      } else if (premiumDrilldownIds) {
        list = list.filter((r) => premiumDrilldownIds.has(r.id))
      } else {
        list = []
      }
    } else if (strategicFiltro === 'alto-trafego') {
      list = list.filter((r) => matchesStrategicAltoTrafego(r.visualizacoes))
    } else if (strategicFiltro === 'baixa-eficiencia') {
      list = list.filter((r) => matchesStrategicBaixaEficiencia(r.visualizacoes, r.cliquesWhatsapp))
    } else if (strategicFiltro === 'com-views-sem-clique') {
      list = list.filter((r) => matchesComViewsSemClique(r.visualizacoes, r.cliquesWhatsapp))
    } else if (strategicFiltro === 'alto-trafego-zero-clique') {
      list = list.filter((r) => matchesAltoTrafegoZeroCliqueAlert(r.visualizacoes, r.cliquesWhatsapp))
    }

    if (cidadeQuery) {
      const target = cidadeQuery.trim().toLowerCase()
      list = list.filter((r) => (r.cidadeNome ?? '').trim().toLowerCase() === target)
    }

    if (process.env.NODE_ENV === 'development') {
      console.debug('[ModeracaoV2 drilldown]', {
        searchParams: searchParamsKey,
        strategicFiltro,
        cidadeQuery,
        moderationFilterForRows,
        rowsTotal: rows.length,
        afterOperationalCount: afterOperational,
        afterDrilldownCount: list.length,
      })
    }

    return sortModerationRows(list, sortBy)
  }, [
    rows,
    moderationFilterForRows,
    busca,
    sortBy,
    revisionAnuncioIds,
    strategicFiltro,
    cidadeQuery,
    premiumApiFilter,
    premiumDrilldownIds,
    premiumDrilldownError,
    searchParamsKey,
  ])

  const pageCount = totalPages(filtered.length, pageSize)
  const pageItems = useMemo(
    () => paginate(filtered, page, pageSize),
    [filtered, page, pageSize]
  )

  const openDetail = (id: number) => {
    const href = `/admin/moderacao-v2${searchParamsKey ? `?${searchParamsKey}` : ''}`
    if (typeof window !== 'undefined') {
      window.sessionStorage.setItem(
        MODERATION_V2_CONTEXT_STORAGE_KEY,
        JSON.stringify({
          ids: pageItems.map((row) => row.id),
          href,
          savedAt: Date.now(),
        })
      )
    }
    router.push(`/admin/moderacao-v2/${id}${searchParamsKey ? `?${searchParamsKey}` : ''}`)
  }

  useEffect(() => {
    setPage((p) => Math.min(Math.max(1, p), pageCount))
  }, [pageCount])

  const dashboardActiveLabels: string[] = []
  if (strategicFiltro === 'sem-upsell') dashboardActiveLabels.push('Sem upsell')
  if (strategicFiltro === 'vencendo-em-breve') dashboardActiveLabels.push('Benefícios vencendo em breve')
  if (strategicFiltro === 'alto-trafego') dashboardActiveLabels.push('Alto tráfego (≥ 2.000 visualizações)')
  if (strategicFiltro === 'baixa-eficiencia')
    dashboardActiveLabels.push('Possível ineficiência (≥ 300 views, conversão abaixo de 1%)')
  if (strategicFiltro === 'com-views-sem-clique') dashboardActiveLabels.push('Com views e sem clique no WhatsApp')
  if (strategicFiltro === 'alto-trafego-zero-clique')
    dashboardActiveLabels.push('Muitas views (≥ 200) e zero clique')
  if (cidadeQuery) dashboardActiveLabels.push(`Cidade = ${cidadeQuery}`)

  const cidadeSemCampoNoBackend =
    !!cidadeQuery &&
    rows.length > 0 &&
    rows.every((r) => !(r.cidadeNome ?? '').trim())

  const clearDashboardQuery = () => {
    setFilter('fila')
    router.replace('/admin/moderacao-v2')
  }

  const dashboardBanner = hasDrilldown ? (
    <div className="rounded-xl border-2 border-[#f0198f]/25 bg-gradient-to-br from-pink-50/90 to-white px-4 py-4 shadow-sm">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0 space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-[#d9157d]">Filtros ativos (dashboard)</p>
          <ul className="list-inside list-disc space-y-1 text-sm text-gray-900">
            {dashboardActiveLabels.map((t) => (
              <li key={t}>
                <span className="font-medium">{t}</span>
              </li>
            ))}
          </ul>
          <p className="text-xs text-gray-600">
            Base da lista: <span className="font-medium text-gray-800">Todos os status</span> (o drill-down não cruza com
            “Fila de moderação”). Resultado:{' '}
            <span className="tabular-nums font-semibold text-gray-900">{filtered.length}</span> anúncio(s) após filtros
            {premiumApiFilter && premiumDrilldownLoading ? (
              <span className="ml-2 text-amber-700">· Carregando IDs do painel premium…</span>
            ) : null}
          </p>
          {cidadeSemCampoNoBackend ? (
            <p className="text-xs font-medium text-amber-900">
              Lista staff sem campo de cidade no JSON — faça deploy do backend com{' '}
              <span className="font-mono">cidadeNome</span> no DTO staff para este filtro funcionar.
            </p>
          ) : null}
          {strategicFiltro === 'vencendo-em-breve' ? (
            <p className="text-xs text-gray-600">
              O número no card do dashboard costuma contar <span className="font-medium">benefícios</span> prestes a
              vencer; a lista mostra <span className="font-medium">anúncios</span> ligados a eles na API — totais podem
              diferir.
            </p>
          ) : null}
        </div>
        <button
          type="button"
          className="shrink-0 rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm font-medium text-[#f0198f] shadow-sm hover:bg-pink-50"
          onClick={clearDashboardQuery}
        >
          Limpar filtros do dashboard
        </button>
      </div>
    </div>
  ) : null

  const badgeClass = (status: string) => {
    switch (status) {
      case 'ATIVO':
        return 'bg-green-100 text-green-800 border-green-200'
      case 'PENDENTE':
        return 'bg-amber-100 text-amber-900 border-amber-200'
      case 'REJEITADO':
        return 'bg-red-100 text-red-800 border-red-200'
      default:
        return 'bg-gray-100 text-gray-700 border-gray-200'
    }
  }

  const statusIcon = (status: string) => {
    switch (status) {
      case 'ATIVO':
        return <CheckCircleIcon className="mr-1 h-3.5 w-3.5" />
      case 'PENDENTE':
        return <ClockIcon className="mr-1 h-3.5 w-3.5" />
      case 'REJEITADO':
        return <ExclamationCircleIcon className="mr-1 h-3.5 w-3.5" />
      default:
        return <ClockIcon className="mr-1 h-3.5 w-3.5" />
    }
  }

  if (loading) {
    return <div className="py-12 text-center text-sm text-gray-500">Carregando fila…</div>
  }
  if (error) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
        {error}{' '}
        <button type="button" className="underline" onClick={() => void load()}>
          Tentar novamente
        </button>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {dashboardBanner}
      {premiumDrilldownError ? (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          {premiumDrilldownError}
        </div>
      ) : null}
      {/*
        Barra de busca/ordenação em linha própria (full width).
        Evita lg:justify-between com os botões de prioridade — isso empurrava a busca para a direita.
        Ordem: Busca rápida (maior) → Ordenar → Itens por página.
      */}
      <div className="flex flex-col gap-3 xl:flex-row xl:items-end xl:gap-4">
        <div className="min-w-0 w-full xl:min-w-0 xl:flex-1">
          <label className="mb-1 block text-xs font-medium text-gray-600">Busca rápida</label>
          <Input
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            placeholder="Título, @usuário ou status"
            className="h-12 w-full min-w-0 border-gray-200 text-base shadow-sm md:h-11"
          />
        </div>
        <div className="flex w-full flex-col gap-3 sm:flex-row sm:items-end xl:w-auto xl:max-w-2xl xl:shrink-0">
          <div className="min-w-[11rem] w-full sm:min-w-[12rem] sm:flex-1 sm:max-w-xs">
            <label className="mb-1 block text-xs font-medium text-gray-600">Ordenar por</label>
            <Select value={sortBy} onValueChange={(v) => setSortBy(v as ModerationListSort)}>
              <SelectTrigger className="h-10 w-full border-gray-200">
                <SelectValue placeholder="Ordenação" />
              </SelectTrigger>
              <SelectContent>
                {SORT_OPTIONS.map((opt) => (
                  <SelectItem key={opt.value} value={opt.value}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="min-w-[11rem] w-full sm:w-36">
            <label className="mb-1 block text-xs font-medium text-gray-600">Itens por página</label>
            <Select value={String(pageSize)} onValueChange={(v) => setPageSize(Number(v))}>
              <SelectTrigger className="h-10 w-full border-gray-200">
                <SelectValue placeholder="Tamanho" />
              </SelectTrigger>
              <SelectContent>
                {PAGE_SIZE_OPTIONS.map((n) => (
                  <SelectItem key={n} value={String(n)}>
                    {n}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
      </div>

      <div className="space-y-2">
        <p className="text-xs font-medium uppercase tracking-wide text-gray-500">Prioridade operacional</p>
        {hasDrilldown ? (
          <p className="text-xs text-gray-600">
            Botões abaixo ficam bloqueados enquanto houver filtro na URL do dashboard. Limpe o drill-down para voltar à
            fila operacional.
          </p>
        ) : null}
        <div className="flex flex-wrap gap-2">
          {FILTER_OPTIONS.map((opt) => {
            const n = filterCounts[opt.value]
            return (
              <Button
                key={opt.value}
                type="button"
                size="sm"
                variant={operationalFilterActive === opt.value ? 'default' : 'outline'}
                disabled={hasDrilldown}
                title={hasDrilldown ? 'Limpe os filtros do dashboard para alterar a fila operacional' : undefined}
                className={
                  operationalFilterActive === opt.value
                    ? 'bg-[#f0198f] text-white hover:bg-[#f0198f]/90'
                    : 'border-gray-300 text-gray-700'
                }
                onClick={() => setFilter(opt.value)}
              >
                {opt.labelBase}{' '}
                <span className="tabular-nums opacity-95">({n})</span>
              </Button>
            )
          })}
        </div>
      </div>

      <div className="text-sm text-gray-600">
        <span className="font-medium text-gray-900">{filtered.length}</span> anúncio(s) neste filtro
        {!hasDrilldown && filter === 'fila' ? (
          <span className="ml-2 rounded-md bg-amber-50 px-2 py-0.5 text-xs text-amber-900">
            Fila = status PENDENTE ou fila de revisões do backend
          </span>
        ) : null}
      </div>

      {premiumApiFilter && premiumDrilldownLoading ? (
        <div className="rounded-xl border border-dashed border-gray-200 py-14 text-center text-gray-500">
          Carregando lista do painel premium (IDs oficiais)…
        </div>
      ) : pageItems.length === 0 ? (
        <div className="rounded-xl border border-dashed border-gray-200 py-14 text-center text-gray-500">
          Nenhum anúncio neste filtro.
        </div>
      ) : (
        <>
          <div className="hidden overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm md:block">
            <Table>
              <TableHeader>
                <TableRow className="h-9 border-b border-gray-200 bg-gray-50/90 hover:bg-gray-50/90">
                  <TableHead className="w-[min(40%,28rem)] py-2 pl-3 text-xs font-semibold uppercase tracking-wide text-gray-600">
                    Anúncio
                  </TableHead>
                  <TableHead className="w-[18rem] whitespace-nowrap py-2 text-left text-xs font-semibold uppercase tracking-wide text-gray-600">
                    Premium
                  </TableHead>
                  <TableHead className="min-w-[12rem] whitespace-nowrap py-2 text-right text-xs font-semibold uppercase tracking-wide text-gray-600">
                    Métricas
                  </TableHead>
                  <TableHead className="py-2 text-center text-xs font-semibold uppercase tracking-wide text-gray-600">
                    Status
                  </TableHead>
                  <TableHead className="whitespace-nowrap py-2 text-right text-xs font-semibold uppercase tracking-wide text-gray-600">
                    Criado
                  </TableHead>
                  <TableHead className="py-2 pr-3 text-right text-xs font-semibold uppercase tracking-wide text-gray-600">
                    Ação
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {pageItems.map((r) => (
                  <TableRow
                    key={r.id}
                    className="border-b border-gray-100 hover:bg-pink-50/40"
                  >
                    <TableCell className="max-w-[min(28rem,40vw)] py-3 pl-3 align-middle">
                      <div className="flex min-w-0 items-start gap-3">
                        <QueueRowThumb
                          url={r.thumbnailUrl}
                          isVideo={r.thumbnailIsVideo}
                          title={r.titulo || 'Anúncio'}
                        />
                        <div className="min-w-0 pt-0.5">
                          <p className="truncate text-sm font-semibold leading-tight text-gray-900">{r.titulo}</p>
                          <p className="truncate text-xs leading-tight text-gray-500">
                            @{r.usernameAnunciante || '—'}
                          </p>
                          {revisionAnuncioIds.has(r.id) ? (
                            <p className="mt-0.5 text-[10px] font-medium leading-tight text-amber-800">
                              Revisão pendente
                            </p>
                          ) : null}
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className="py-3 align-middle">
                      <PremiumQuickActions anuncioId={r.id} disabled={r.removidoLogicamente} />
                    </TableCell>
                    <TableCell className="py-3 align-middle">
                      <div className="flex flex-col items-end gap-1 text-xs tabular-nums text-gray-700">
                        <span className="whitespace-nowrap" title="Visualizações (contador do anúncio)">
                          👁 {formatMetric(r.visualizacoes)}
                        </span>
                        <span className="whitespace-nowrap" title="Cliques no WhatsApp (agregado)">
                          📲 {formatMetric(r.cliquesWhatsapp)}
                        </span>
                      </div>
                    </TableCell>
                    <TableCell className="py-3 text-center align-middle">
                      <div className="flex flex-col items-center gap-1">
                        {r.removidoLogicamente ? (
                          <Badge
                            variant="outline"
                            className="border-rose-200 bg-rose-50 px-1.5 py-0 text-[10px] text-rose-900"
                          >
                            Removido lógico
                          </Badge>
                        ) : null}
                        <Badge
                          variant="outline"
                          className={`inline-flex items-center px-1.5 py-0 text-[10px] ${badgeClass(r.status)}`}
                        >
                          {statusIcon(r.status)}
                          {r.status}
                        </Badge>
                      </div>
                    </TableCell>
                    <TableCell className="py-3 text-right text-xs tabular-nums text-gray-600 align-middle">
                      {formatCriadoLista(r.dataCriacao)}
                    </TableCell>
                    <TableCell className="py-3 pr-3 text-right align-middle">
                      <Button
                        type="button"
                        size="sm"
                        className="h-8 bg-[#f0198f] px-3 text-xs text-white hover:bg-[#d9157d]"
                        onClick={() => openDetail(r.id)}
                      >
                        Analisar
                        <ArrowRightIcon className="ml-1 h-3.5 w-3.5" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          <div className="space-y-2 md:hidden">
            {pageItems.map((r) => (
              <div
                key={r.id}
                className="rounded-lg border border-gray-200 bg-white p-3 shadow-sm"
              >
                <div className="flex gap-3.5">
                  <QueueRowThumb
                    url={r.thumbnailUrl}
                    isVideo={r.thumbnailIsVideo}
                    title={r.titulo || 'Anúncio'}
                  />
                  <div className="min-w-0 flex-1">
                    <p className="line-clamp-2 text-sm font-semibold leading-snug text-gray-900">{r.titulo}</p>
                    <p className="mt-0.5 truncate text-xs text-gray-500">@{r.usernameAnunciante}</p>
                    <div className="mt-1.5 flex flex-wrap gap-x-3 gap-y-0.5 text-[11px] tabular-nums text-gray-700">
                      <span>
                        👁 {formatMetric(r.visualizacoes)}
                      </span>
                      <span>
                        📲 {formatMetric(r.cliquesWhatsapp)}
                      </span>
                      <span className="text-gray-500">{formatCriadoLista(r.dataCriacao)}</span>
                    </div>
                  </div>
                </div>
                {revisionAnuncioIds.has(r.id) ? (
                  <p className="mt-2 text-[10px] font-medium text-amber-800">Revisão pendente</p>
                ) : null}
                <div className="mt-2">
                  <PremiumQuickActions anuncioId={r.id} disabled={r.removidoLogicamente} />
                </div>
                <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
                  <div className="flex flex-wrap items-center gap-1">
                    {r.removidoLogicamente ? (
                      <Badge variant="outline" className="border-rose-200 bg-rose-50 text-[10px] text-rose-900">
                        Removido lógico
                      </Badge>
                    ) : null}
                    <Badge variant="outline" className={`text-[10px] ${badgeClass(r.status)}`}>
                      {statusIcon(r.status)}
                      {r.status}
                    </Badge>
                  </div>
                </div>
                <Button
                  type="button"
                  size="sm"
                  className="mt-2 h-9 w-full bg-[#f0198f] text-sm text-white hover:bg-[#d9157d]"
                  onClick={() => openDetail(r.id)}
                >
                  Analisar
                  <ArrowRightIcon className="ml-1 h-4 w-4" />
                </Button>
              </div>
            ))}
          </div>

          {pageCount > 1 ? (
            <div className="flex flex-col items-center gap-3 sm:flex-row sm:justify-center">
              <p className="text-sm text-gray-600">
                Página <span className="font-semibold text-gray-900">{page}</span> de{' '}
                <span className="font-semibold text-gray-900">{pageCount}</span>
              </p>
              <Pagination>
                <PaginationContent>
                  <PaginationItem>
                    <PaginationPrevious
                      className={page <= 1 ? 'pointer-events-none opacity-40' : 'cursor-pointer'}
                      onClick={() => setPage((p) => Math.max(1, p - 1))}
                    />
                  </PaginationItem>
                  <PaginationItem>
                    <PaginationNext
                      className={page >= pageCount ? 'pointer-events-none opacity-40' : 'cursor-pointer'}
                      onClick={() => setPage((p) => Math.min(pageCount, p + 1))}
                    />
                  </PaginationItem>
                </PaginationContent>
              </Pagination>
            </div>
          ) : null}
        </>
      )}

      <p className="text-center text-xs text-gray-400">
        Moderação v2 · visualizações e cliques WhatsApp vêm do backend na lista staff.
      </p>
    </div>
  )
}
