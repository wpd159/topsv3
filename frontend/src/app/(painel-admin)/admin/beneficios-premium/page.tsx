'use client'

import Link from 'next/link'
import { useEffect, useMemo, useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { DashboardStatCard } from '../components/dashboard/dashboard-stats-cards'
import {
  fetchPremiumBenefitsAnuncio,
  fetchPremiumBenefitsDashboardAds,
  fetchPremiumBenefitsDashboard,
  fetchPremiumBenefitsUserAds,
  grantPremiumBenefit,
  PremiumBenefitAnuncioDetail,
  PremiumBenefitAnuncioSummary,
  PremiumBenefitDashboard,
  PremiumBenefitDashboardFilter,
  PremiumBenefitItem,
  PremiumBenefitSearchResponse,
  PremiumBenefitUserSummary,
  revokePremiumBenefit,
  searchPremiumBenefits,
} from '@/lib/admin-premium-benefits-api'
import {
  ArrowTopRightOnSquareIcon,
  BanknotesIcon,
  ChatBubbleLeftRightIcon,
  ClockIcon,
  EyeSlashIcon,
  MagnifyingGlassIcon,
  RectangleStackIcon,
  SparklesIcon,
  UsersIcon,
} from '@heroicons/react/24/outline'

type ActivationFormState = {
  codigos: string[]
  dataInicio: string
  dataFim: string
  duracaoHoras: string
  motivo: string
  observacaoInterna: string
}

type RevokeDialogState = {
  open: boolean
  item: PremiumBenefitItem | null
  motivo: string
  observacaoInterna: string
}

const INITIAL_FORM: ActivationFormState = {
  codigos: [],
  dataInicio: '',
  dataFim: '',
  duracaoHoras: '',
  motivo: '',
  observacaoInterna: '',
}

const AVISO_FOTOS_TEMPORARIO =
  'Benefício temporário: ao vencer, o anúncio volta ao limite padrão de fotos. As mídias existentes permanecem preservadas.'
const BENEFICIO_FOTOS_EXTRA_TITULO = 'Até 10 fotos no anúncio'
const BENEFICIO_FOTOS_EXTRA_DESCRICAO =
  'Amplie o limite do anúncio para até 10 fotos durante a vigência do benefício.'
const STORIES_BENEFIT_CODE = 'STORIES'

const DASHBOARD_FILTER_META: Record<
  PremiumBenefitDashboardFilter,
  { title: string; description: string; empty: string }
> = {
  ATIVOS: {
    title: 'Anúncios com benefícios ativos',
    description: 'Lista anúncios que hoje possuem algum benefício premium ativo ou destaque em vigor.',
    empty: 'Nenhum anúncio com benefício ativo encontrado.',
  },
  VENCENDO_EM_BREVE: {
    title: 'Anúncios vencendo em breve',
    description: 'Mostra anúncios com benefício ativo perto do vencimento para ação rápida da equipe.',
    empty: 'Nenhum anúncio com benefício vencendo em breve.',
  },
  SEM_UPSELL: {
    title: 'Anúncios sem upsell',
    description: 'Mostra anúncios ativos sem destaque e sem benefício premium vigente.',
    empty: 'Nenhum anúncio sem upsell encontrado.',
  },
  OCULTAR_IDADE: {
    title: 'Anúncios com ocultar idade ativo',
    description: 'Lista os anúncios que estão usando o benefício premium de ocultar idade.',
    empty: 'Nenhum anúncio com ocultar idade ativo.',
  },
}

function formatarData(value?: string | string[] | number[] | null) {
  if (!value) return 'Sem data'

  const date = Array.isArray(value)
    ? new Date(
        Number(value[0]),
        Number(value[1] || 1) - 1,
        Number(value[2] || 1),
        Number(value[3] || 0),
        Number(value[4] || 0),
        Number(value[5] || 0)
      )
    : new Date(value)

  return Number.isNaN(date.getTime())
    ? String(value)
    : date.toLocaleString('pt-BR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
}

function formatarCodigo(codigo?: string | null) {
  if (!codigo) return '-'
  const map: Record<string, string> = {
    WHATSAPP_CARD: 'WhatsApp no card',
    OCULTAR_IDADE: 'Ocultar idade',
    ANUNCIO_TOPO: 'Destaque',
    VIDEO_1: 'Vídeo',
    CARROSSEL_FOTOS: 'Carrossel',
    FOTOS_EXTRA_5: 'Até 10 fotos',
    STORIES: 'Stories',
  }
  return map[codigo] || codigo.replaceAll('_', ' ').toLowerCase()
}

function formatarNomeBeneficio(codigo?: string | null, nome?: string | null) {
  if (codigo === 'FOTOS_EXTRA_5') return BENEFICIO_FOTOS_EXTRA_TITULO
  return nome?.trim() || formatarCodigo(codigo)
}

function formatarDescricaoBeneficio(codigo?: string | null, descricao?: string | null) {
  if (codigo === 'FOTOS_EXTRA_5') return BENEFICIO_FOTOS_EXTRA_DESCRICAO
  return descricao?.trim() || ''
}

function isStoriesBenefit(codigo?: string | null) {
  return codigo === STORIES_BENEFIT_CODE
}

function isManualBenefit(item?: PremiumBenefitItem | null) {
  return item?.manual === true || item?.origem === 'MANUAL_ADMIN'
}

function revokeRequiresReason(item?: PremiumBenefitItem | null) {
  return !!item && !isManualBenefit(item)
}

function SearchBlock({
  title,
  empty,
  icon,
  hasItems,
  children,
}: {
  title: string
  empty: string
  icon: React.ReactNode
  hasItems: boolean
  children: React.ReactNode
}) {
  return (
    <div>
      <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-gray-700">
        {icon}
        {title}
      </div>
      <div className="space-y-2">{hasItems ? children : <p className="text-sm text-gray-400">{empty}</p>}</div>
    </div>
  )
}

export default function AdminBeneficiosPremiumPage() {
  const router = useRouter()
  const searchParams = useSearchParams()

  const [dashboard, setDashboard] = useState<PremiumBenefitDashboard | null>(null)
  const [dashboardLoading, setDashboardLoading] = useState(true)
  const [dashboardFilter, setDashboardFilter] = useState<PremiumBenefitDashboardFilter | null>(null)
  const [dashboardAds, setDashboardAds] = useState<PremiumBenefitAnuncioSummary[]>([])
  const [dashboardAdsLoading, setDashboardAdsLoading] = useState(false)
  const [query, setQuery] = useState(searchParams.get('q') ?? '')
  const [searching, setSearching] = useState(false)
  const [results, setResults] = useState<PremiumBenefitSearchResponse>({ usuarios: [], anuncios: [] })
  const [selectedUser, setSelectedUser] = useState<PremiumBenefitUserSummary | null>(null)
  const [userAds, setUserAds] = useState<PremiumBenefitAnuncioSummary[]>([])
  const [loadingUserAds, setLoadingUserAds] = useState(false)
  const [selectedAdId, setSelectedAdId] = useState<number | null>(() => {
    const raw = searchParams.get('anuncioId')
    return raw ? Number(raw) : null
  })
  const [detail, setDetail] = useState<PremiumBenefitAnuncioDetail | null>(null)
  const [loadingDetail, setLoadingDetail] = useState(false)
  const [activationForm, setActivationForm] = useState<ActivationFormState>(INITIAL_FORM)
  const [saving, setSaving] = useState(false)
  const [revokeDialog, setRevokeDialog] = useState<RevokeDialogState>({
    open: false,
    item: null,
    motivo: '',
    observacaoInterna: '',
  })

  const catalogoAnuncio = useMemo(
    () =>
      (detail?.catalogo ?? []).filter(
        (item) => (item.escopo ?? 'ANUNCIO') === 'ANUNCIO' && !isStoriesBenefit(item.codigo)
      ),
    [detail?.catalogo]
  )

  const beneficiosAtivosVisiveis = useMemo(
    () => (detail?.beneficiosAtivos ?? []).filter((item) => !isStoriesBenefit(item.codigo)),
    [detail?.beneficiosAtivos]
  )

  const beneficiosExpiradosVisiveis = useMemo(
    () => (detail?.beneficiosExpirados ?? []).filter((item) => !isStoriesBenefit(item.codigo)),
    [detail?.beneficiosExpirados]
  )

  const historicoVisivel = useMemo(
    () => (detail?.historico ?? []).filter((item) => !isStoriesBenefit(item.codigo)),
    [detail?.historico]
  )

  const activeBenefitCodes = useMemo(
    () => new Set(beneficiosAtivosVisiveis.map((item) => item.codigo)),
    [beneficiosAtivosVisiveis]
  )

  const catalogoDisponivel = useMemo(
    () => catalogoAnuncio.filter((item) => !activeBenefitCodes.has(item.codigo)),
    [activeBenefitCodes, catalogoAnuncio]
  )

  const selectedBenefitItems = useMemo(
    () => catalogoDisponivel.filter((item) => activationForm.codigos.includes(item.codigo)),
    [activationForm.codigos, catalogoDisponivel]
  )

  const revokeMotivoObrigatorio = revokeRequiresReason(revokeDialog.item)

  const updateUrl = (next: { q?: string; usuarioId?: number | null; anuncioId?: number | null }) => {
    const params = new URLSearchParams(searchParams.toString())

    if (next.q !== undefined) next.q ? params.set('q', next.q) : params.delete('q')
    if (next.usuarioId !== undefined) next.usuarioId ? params.set('usuarioId', String(next.usuarioId)) : params.delete('usuarioId')
    if (next.anuncioId !== undefined) next.anuncioId ? params.set('anuncioId', String(next.anuncioId)) : params.delete('anuncioId')

    const queryString = params.toString()
    router.replace(queryString ? `/admin/beneficios-premium?${queryString}` : '/admin/beneficios-premium')
  }

  const loadDashboard = async () => {
    try {
      setDashboardLoading(true)
      setDashboard(await fetchPremiumBenefitsDashboard())
    } catch {
      setDashboard(null)
    } finally {
      setDashboardLoading(false)
    }
  }

  const loadDashboardAds = async (filter: PremiumBenefitDashboardFilter) => {
    try {
      setDashboardFilter(filter)
      setDashboardAdsLoading(true)
      setDashboardAds(await fetchPremiumBenefitsDashboardAds(filter))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Falha ao listar os anúncios do indicador.')
      setDashboardAds([])
    } finally {
      setDashboardAdsLoading(false)
    }
  }

  const clearDashboardAds = () => {
    setDashboardFilter(null)
    setDashboardAds([])
  }

  const handleSearch = async (term = query) => {
    const normalized = term.trim()
    setQuery(normalized)
    updateUrl({ q: normalized })

    if (!normalized) {
      setResults({ usuarios: [], anuncios: [] })
      return
    }

    try {
      setSearching(true)
      setResults(await searchPremiumBenefits(normalized))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Falha ao buscar resultados.')
    } finally {
      setSearching(false)
    }
  }

  const loadUserAds = async (user: PremiumBenefitUserSummary) => {
    try {
      setSelectedUser(user)
      setLoadingUserAds(true)
      setUserAds(await fetchPremiumBenefitsUserAds(user.id))
      updateUrl({ usuarioId: user.id })
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Falha ao listar anúncios do usuário.')
      setUserAds([])
    } finally {
      setLoadingUserAds(false)
    }
  }

  const loadAdDetail = async (anuncioId: number) => {
    try {
      setSelectedAdId(anuncioId)
      setLoadingDetail(true)
      const response = await fetchPremiumBenefitsAnuncio(anuncioId)
      setDetail(response)
      setSelectedUser(response.usuario)
      setUserAds(await fetchPremiumBenefitsUserAds(response.usuario.id))
      setActivationForm(INITIAL_FORM)
      updateUrl({ anuncioId, usuarioId: response.usuario.id })
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Falha ao carregar o anúncio.')
      setDetail(null)
    } finally {
      setLoadingDetail(false)
    }
  }

  const refreshCurrentAd = async (payload?: PremiumBenefitAnuncioDetail) => {
    const anuncioId = payload?.anuncio?.id ?? selectedAdId
    if (!anuncioId) return
    if (payload) {
      setDetail(payload)
      await loadDashboard()
      return
    }
    await Promise.all([loadAdDetail(anuncioId), loadDashboard()])
  }

  const handleGrant = async () => {
    if (!selectedAdId) return toast.error('Selecione um anúncio para gerenciar.')
    if (activationForm.codigos.length === 0) return toast.error('Escolha pelo menos um benefício para ativar.')
    if (!activationForm.motivo.trim()) return toast.error('Informe o motivo da concessão manual.')

    try {
      setSaving(true)
      let response: PremiumBenefitAnuncioDetail | undefined

      for (const codigo of activationForm.codigos) {
        const payload: Record<string, unknown> = {
          codigo,
          motivo: activationForm.motivo.trim(),
          observacaoInterna: activationForm.observacaoInterna.trim() || null,
        }
        if (activationForm.dataInicio) payload.dataInicio = activationForm.dataInicio
        if (activationForm.dataFim) payload.dataFim = activationForm.dataFim
        if (activationForm.duracaoHoras.trim()) payload.duracaoHoras = Number(activationForm.duracaoHoras)

        response = await grantPremiumBenefit(selectedAdId, payload)
      }

      toast.success(
        activationForm.codigos.length > 1
          ? 'Benefícios premium ativados manualmente.'
          : 'Benefício premium ativado manualmente.'
      )
      setActivationForm(INITIAL_FORM)
      await refreshCurrentAd(response)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Falha ao ativar o benefício.')
    } finally {
      setSaving(false)
    }
  }

  const toggleBenefitCode = (codigo: string) => {
    setActivationForm((prev) => ({
      ...prev,
      codigos: prev.codigos.includes(codigo)
        ? prev.codigos.filter((item) => item !== codigo)
        : [...prev.codigos, codigo],
    }))
  }

  const selectAllBenefits = () => {
    setActivationForm((prev) => ({
      ...prev,
      codigos: catalogoDisponivel.map((item) => item.codigo),
    }))
  }

  const clearSelectedBenefits = () => {
    setActivationForm((prev) => ({
      ...prev,
      codigos: [],
    }))
  }

  const handleRevoke = async () => {
    if (!revokeDialog.item) return
    if (isStoriesBenefit(revokeDialog.item.codigo)) return toast.error('Stories não faz parte deste fluxo.')
    if (!revokeDialog.item.podeDesativar) {
      return toast.error('Este benefício não pode ser desativado pelo backend.')
    }

    const motivo = revokeDialog.motivo.trim()
    const observacaoInterna = revokeDialog.observacaoInterna.trim()
    if (revokeMotivoObrigatorio && !motivo) return toast.error('Informe o motivo da desativação.')

    try {
      setSaving(true)
      const payload: Record<string, unknown> = {
        observacaoInterna: observacaoInterna || null,
      }
      if (motivo) payload.motivo = motivo

      const response = await revokePremiumBenefit(revokeDialog.item.id, payload)
      toast.success('Benefício desativado.')
      setRevokeDialog({ open: false, item: null, motivo: '', observacaoInterna: '' })
      await refreshCurrentAd(response)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Falha ao desativar o benefício.')
    } finally {
      setSaving(false)
    }
  }

  useEffect(() => {
    const anuncioIdRaw = searchParams.get('anuncioId')

    if (!anuncioIdRaw) {
      void loadDashboard()
      return
    }

    setDashboard(null)
    setDashboardLoading(false)
  }, [])

  useEffect(() => {
    const q = searchParams.get('q') ?? ''
    const anuncioIdRaw = searchParams.get('anuncioId')

    if (q && q !== query) {
      setQuery(q)
      void handleSearch(q)
    }
    if (anuncioIdRaw) {
      const anuncioId = Number(anuncioIdRaw)
      if (!Number.isNaN(anuncioId) && anuncioId !== selectedAdId) void loadAdDetail(anuncioId)
    }
  }, [searchParams])

  const dashboardCards = [
    {
      label: 'Benefícios ativos',
      value: dashboardLoading ? '...' : String(dashboard?.beneficiosAtivos ?? 0),
      icon: <SparklesIcon className="h-6 w-6 text-pink-600" />,
      color: 'pink' as const,
      filter: 'ATIVOS' as const,
    },
    {
      label: 'Vencendo em breve',
      value: dashboardLoading ? '...' : String(dashboard?.beneficiosVencendoEmBreve ?? 0),
      icon: <ClockIcon className="h-6 w-6 text-yellow-600" />,
      color: 'yellow' as const,
      filter: 'VENCENDO_EM_BREVE' as const,
    },
    {
      label: 'Anúncios sem upsell',
      value: dashboardLoading ? '...' : String(dashboard?.anunciosSemUpsell ?? 0),
      icon: <RectangleStackIcon className="h-6 w-6 text-blue-600" />,
      color: 'blue' as const,
      filter: 'SEM_UPSELL' as const,
    },
    {
      label: 'Ocultar idade ativo',
      value: dashboardLoading ? '...' : String(dashboard?.anunciosComOcultarIdade ?? 0),
      icon: <EyeSlashIcon className="h-6 w-6 text-green-600" />,
      color: 'green' as const,
      filter: 'OCULTAR_IDADE' as const,
    },
  ]

  return (
    <section className="space-y-6 pb-10">
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-bold text-gray-800">Benefícios Premium</h1>
        <p className="text-sm text-gray-500">
          Gerencie concessões comerciais manuais por anúncio sem misturar isso com moderação, créditos ou compliance.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {dashboardCards.map((card) => (
          <DashboardStatCard
            key={card.label}
            label={card.label}
            value={card.value}
            icon={card.icon}
            color={card.color}
            active={dashboardFilter === card.filter}
            onClick={() => void loadDashboardAds(card.filter)}
          />
        ))}
      </div>

      <div className="grid gap-6 xl:grid-cols-[360px_minmax(0,1fr)]">
        <div className="space-y-6">
          <div className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
            <div className="mb-4 flex items-start justify-between gap-3">
              <div>
                <h2 className="text-base font-semibold text-gray-800">
                  {dashboardFilter ? DASHBOARD_FILTER_META[dashboardFilter].title : 'Anúncios do indicador'}
                </h2>
                <p className="mt-1 text-sm text-gray-500">
                  {dashboardFilter
                    ? DASHBOARD_FILTER_META[dashboardFilter].description
                    : 'Clique em um dos indicadores acima para listar os anúncios correspondentes.'}
                </p>
              </div>
              {dashboardFilter ? (
                <Button type="button" variant="outline" size="sm" onClick={clearDashboardAds}>
                  Limpar
                </Button>
              ) : null}
            </div>

            {dashboardAdsLoading ? (
              <p className="text-sm text-gray-500">Carregando anúncios do indicador...</p>
            ) : !dashboardFilter ? (
              <p className="text-sm text-gray-400">Nenhum filtro selecionado.</p>
            ) : dashboardAds.length === 0 ? (
              <p className="text-sm text-gray-400">{DASHBOARD_FILTER_META[dashboardFilter].empty}</p>
            ) : (
              <div className="space-y-2">
                {dashboardAds.map((anuncio) => (
                  <button
                    key={`${dashboardFilter}-${anuncio.id}`}
                    type="button"
                    onClick={() => void loadAdDetail(anuncio.id)}
                    className="w-full rounded-lg border border-gray-200 px-3 py-2 text-left transition hover:border-pink-300 hover:bg-pink-50/40"
                  >
                    <div className="flex items-center justify-between gap-3">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-semibold text-gray-900">{anuncio.titulo}</p>
                        <p className="truncate text-xs text-gray-500">
                          @{anuncio.usuarioUsername} · {anuncio.cidadeNome}
                          {anuncio.bairroNome ? ` · ${anuncio.bairroNome}` : ''}
                        </p>
                      </div>
                      <Badge variant="outline">{anuncio.status || '-'}</Badge>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>

          <div className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
            <div className="mb-4">
              <h2 className="text-base font-semibold text-gray-800">Buscar usuário ou anúncio</h2>
              <p className="mt-1 text-sm text-gray-500">
                Busque por nome, username, email, CPF, título, slug, cidade ou bairro.
              </p>
            </div>

            <div className="flex gap-2">
              <div className="relative flex-1">
                <MagnifyingGlassIcon className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
                <Input
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  onKeyDown={(event) => event.key === 'Enter' && void handleSearch()}
                  placeholder="Buscar por usuário, anúncio ou localização..."
                  className="pl-9"
                />
              </div>
              <Button onClick={() => void handleSearch()} disabled={searching}>
                {searching ? 'Buscando...' : 'Buscar'}
              </Button>
            </div>

            <div className="mt-5 space-y-4">
              <SearchBlock
                title="Usuários encontrados"
                empty="Nenhum usuário listado."
                icon={<UsersIcon className="h-4 w-4 text-blue-600" />}
                hasItems={results.usuarios.length > 0}
              >
                {results.usuarios.map((user) => (
                  <button
                    key={user.id}
                    type="button"
                    onClick={() => void loadUserAds(user)}
                    className="w-full rounded-lg border border-gray-200 px-3 py-2 text-left transition hover:border-pink-300 hover:bg-pink-50/40"
                  >
                    <p className="text-sm font-semibold text-gray-900">{user.nomeCompleto || user.username}</p>
                    <p className="text-xs text-gray-500">@{user.username} · {user.email}</p>
                  </button>
                ))}
              </SearchBlock>

              <SearchBlock
                title="Anúncios encontrados"
                empty="Nenhum anúncio listado."
                icon={<RectangleStackIcon className="h-4 w-4 text-pink-600" />}
                hasItems={results.anuncios.length > 0}
              >
                {results.anuncios.map((anuncio) => (
                  <button
                    key={anuncio.id}
                    type="button"
                    onClick={() => void loadAdDetail(anuncio.id)}
                    className="w-full rounded-lg border border-gray-200 px-3 py-2 text-left transition hover:border-pink-300 hover:bg-pink-50/40"
                  >
                    <p className="text-sm font-semibold text-gray-900">{anuncio.titulo}</p>
                    <p className="text-xs text-gray-500">
                      @{anuncio.usuarioUsername} · {anuncio.cidadeNome} {anuncio.bairroNome ? `· ${anuncio.bairroNome}` : ''}
                    </p>
                  </button>
                ))}
              </SearchBlock>
            </div>
          </div>

          <div className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
            <div className="mb-4">
              <h2 className="text-base font-semibold text-gray-800">Anúncios do usuário</h2>
              <p className="mt-1 text-sm text-gray-500">Selecione um usuário e pule direto para o anúncio certo.</p>
            </div>

            {selectedUser ? (
              <div className="mb-3 rounded-lg bg-gray-50 p-3">
                <p className="text-sm font-semibold text-gray-900">{selectedUser.nomeCompleto || selectedUser.username}</p>
                <p className="text-xs text-gray-500">@{selectedUser.username} · {selectedUser.email}</p>
              </div>
            ) : null}

            {loadingUserAds ? (
              <p className="text-sm text-gray-500">Carregando anúncios do usuário...</p>
            ) : userAds.length === 0 ? (
              <p className="text-sm text-gray-400">Selecione um usuário para listar os anúncios dele.</p>
            ) : (
              <div className="space-y-2">
                {userAds.map((anuncio) => (
                  <button
                    key={anuncio.id}
                    type="button"
                    onClick={() => void loadAdDetail(anuncio.id)}
                    className="w-full rounded-lg border border-gray-200 px-3 py-2 text-left transition hover:border-pink-300 hover:bg-pink-50/40"
                  >
                    <div className="flex items-center justify-between gap-3">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-semibold text-gray-900">{anuncio.titulo}</p>
                        <p className="truncate text-xs text-gray-500">
                          {anuncio.cidadeNome} {anuncio.bairroNome ? `· ${anuncio.bairroNome}` : ''}
                        </p>
                      </div>
                      <Badge variant="outline">{anuncio.status || '-'}</Badge>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>

        <div className="space-y-6">
          {loadingDetail ? (
            <div className="rounded-xl border border-gray-100 bg-white p-8 text-center text-gray-500 shadow-sm">Carregando detalhe do anúncio...</div>
          ) : !detail ? (
            <div className="rounded-xl border border-dashed border-gray-200 bg-white p-10 text-center shadow-sm">
              <SparklesIcon className="mx-auto h-10 w-10 text-pink-400" />
              <h2 className="mt-4 text-lg font-semibold text-gray-800">Selecione um anúncio para gerenciar</h2>
              <p className="mt-2 text-sm text-gray-500">
                Aqui você visualiza benefícios ativos, histórico, concessões manuais e o estado da função de ocultar idade.
              </p>
            </div>
          ) : (
            <>
              <div className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="text-xl font-semibold text-gray-900">{detail.anuncio.titulo}</h2>
                      <Badge variant="outline">{detail.anuncio.status || '-'}</Badge>
                      {detail.anuncio.impulsionado ? <Badge className="bg-pink-50 text-pink-700">Impulsionado</Badge> : null}
                    </div>
                    <p className="mt-1 text-sm text-gray-500">@{detail.usuario.username} · {detail.usuario.email}</p>
                    <p className="mt-1 text-sm text-gray-500">
                      {detail.anuncio.cidadeNome} {detail.anuncio.bairroNome ? `· ${detail.anuncio.bairroNome}` : ''}
                    </p>
                  </div>

                  <div className="flex flex-wrap gap-2">
                    <Button variant="outline" asChild>
                      <Link href={`/admin/moderacao-v2/${detail.anuncio.id}`}>Gerenciamento do anúncio</Link>
                    </Button>
                    <Button asChild className="bg-[#FC1EAD] text-white hover:bg-[#e1199b]">
                      <Link href={`/admin/moderacao-v2/${detail.anuncio.id}`}>
                        <ArrowTopRightOnSquareIcon className="mr-2 h-4 w-4" />
                        Abrir detalhe completo
                      </Link>
                    </Button>
                  </div>
                </div>
              </div>

              <div className="grid gap-6 2xl:grid-cols-[minmax(0,1fr)_360px]">
                <div className="space-y-6">
                  <div className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
                    <div className="mb-4">
                      <h3 className="text-base font-semibold text-gray-900">Ativar benefício manualmente</h3>
                      <p className="mt-1 text-sm text-gray-500">
                        A concessão manual funciona como camada complementar ao plano e às compras por créditos.
                      </p>
                    </div>

                    <div className="grid gap-4 md:grid-cols-2">
                      <div className="space-y-2 text-sm text-gray-700 md:col-span-2">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <span className="font-medium">Benefícios</span>
                          <div className="flex flex-wrap gap-2">
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={selectAllBenefits}
                              disabled={catalogoDisponivel.length === 0}
                            >
                              Selecionar todos
                            </Button>
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={clearSelectedBenefits}
                              disabled={activationForm.codigos.length === 0}
                            >
                              Limpar
                            </Button>
                          </div>
                        </div>

                        {catalogoDisponivel.length === 0 ? (
                          <div className="rounded-xl border border-dashed border-gray-200 bg-gray-50 px-4 py-3 text-sm text-gray-500">
                            Todos os benefícios elegíveis já estão ativos neste anúncio.
                          </div>
                        ) : (
                          <div className="grid gap-3 md:grid-cols-2">
                            {catalogoDisponivel.map((item) => {
                              const checked = activationForm.codigos.includes(item.codigo)
                              return (
                                <button
                                  key={item.id}
                                  type="button"
                                  onClick={() => toggleBenefitCode(item.codigo)}
                                  className={`rounded-xl border px-4 py-3 text-left transition ${
                                    checked
                                      ? 'border-[#FC1EAD] bg-pink-50 shadow-sm'
                                      : 'border-gray-200 bg-white hover:border-pink-300 hover:bg-pink-50/40'
                                  }`}
                                >
                                  <div className="flex items-start gap-3">
                                    <span
                                      className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded border text-[11px] font-bold ${
                                        checked
                                          ? 'border-[#FC1EAD] bg-[#FC1EAD] text-white'
                                          : 'border-gray-300 bg-white text-transparent'
                                      }`}
                                    >
                                      ✓
                                    </span>
                                    <div>
                                      <p className="text-sm font-semibold text-gray-900">
                                        {formatarNomeBeneficio(item.codigo, item.nome)}
                                      </p>
                                      {formatarDescricaoBeneficio(item.codigo, item.descricao) ? (
                                        <p className="mt-1 text-xs text-gray-500">
                                          {formatarDescricaoBeneficio(item.codigo, item.descricao)}
                                        </p>
                                      ) : null}
                                      {item.codigo === 'FOTOS_EXTRA_5' ? (
                                        <p className="mt-2 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-[11px] leading-5 text-amber-800">
                                          {AVISO_FOTOS_TEMPORARIO}
                                        </p>
                                      ) : null}
                                    </div>
                                  </div>
                                </button>
                              )
                            })}
                          </div>
                        )}

                        <p className="text-xs text-gray-500">
                          Você pode ativar vários benefícios de uma vez. A duração, as datas e o motivo abaixo serão aplicados a todos os itens selecionados.
                        </p>
                      </div>

                      <label className="space-y-2 text-sm text-gray-700">
                        <span className="font-medium">Duração em horas</span>
                        <Input
                          type="number"
                          min={1}
                          placeholder="Ex.: 72"
                          value={activationForm.duracaoHoras}
                          onChange={(event) => setActivationForm((prev) => ({ ...prev, duracaoHoras: event.target.value }))}
                        />
                      </label>

                      <label className="space-y-2 text-sm text-gray-700">
                        <span className="font-medium">Data de início</span>
                        <Input
                          type="datetime-local"
                          value={activationForm.dataInicio}
                          onChange={(event) => setActivationForm((prev) => ({ ...prev, dataInicio: event.target.value }))}
                        />
                      </label>

                      <label className="space-y-2 text-sm text-gray-700">
                        <span className="font-medium">Data final</span>
                        <Input
                          type="datetime-local"
                          value={activationForm.dataFim}
                          onChange={(event) => setActivationForm((prev) => ({ ...prev, dataFim: event.target.value }))}
                        />
                      </label>
                    </div>

                    <div className="mt-4 grid gap-4">
                      <label className="space-y-2 text-sm text-gray-700">
                        <span className="font-medium">Motivo da concessão</span>
                        <Input
                          placeholder="Ex.: ajuste comercial, compensação, campanha manual"
                          value={activationForm.motivo}
                          onChange={(event) => setActivationForm((prev) => ({ ...prev, motivo: event.target.value }))}
                        />
                      </label>
                      <label className="space-y-2 text-sm text-gray-700">
                        <span className="font-medium">Observação interna</span>
                        <textarea
                          rows={4}
                          value={activationForm.observacaoInterna}
                          onChange={(event) => setActivationForm((prev) => ({ ...prev, observacaoInterna: event.target.value }))}
                          className="w-full rounded-xl border border-gray-200 px-3 py-3 text-sm outline-none focus:border-[#FC1EAD]"
                          placeholder="Informação interna para a equipe administrativa."
                        />
                      </label>
                    </div>

                    <div className="mt-5 flex flex-wrap items-center justify-between gap-3 rounded-xl bg-pink-50 p-4">
                      <div>
                        <p className="text-sm font-semibold text-gray-900">
                          {selectedBenefitItems.length > 1
                            ? `${selectedBenefitItems.length} benefícios selecionados`
                            : selectedBenefitItems.length === 1
                              ? formatarNomeBeneficio(selectedBenefitItems[0].codigo, selectedBenefitItems[0].nome)
                              : 'Selecione um ou mais benefícios'}
                        </p>
                        <p className="text-xs text-gray-600">
                          Você pode habilitar todos os benefícios elegíveis ou combinar só os recursos desejados para este anúncio.
                        </p>
                      </div>
                      <Button onClick={() => void handleGrant()} disabled={saving} className="bg-[#FC1EAD] text-white hover:bg-[#e1199b]">
                        {saving
                          ? 'Salvando...'
                          : activationForm.codigos.length > 1
                            ? 'Ativar benefícios'
                            : 'Ativar benefício'}
                      </Button>
                    </div>
                  </div>

                  <div className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
                    <div className="mb-4">
                      <h3 className="text-base font-semibold text-gray-900">Benefícios ativos</h3>
                      <p className="mt-1 text-sm text-gray-500">
                        Recursos em vigor no anúncio, vindos de créditos ou de concessão manual complementar.
                      </p>
                    </div>

                    <div className="space-y-3">
                      {beneficiosAtivosVisiveis.length === 0 ? (
                        <p className="text-sm text-gray-400">Nenhum benefício ativo neste anúncio.</p>
                      ) : (
                        beneficiosAtivosVisiveis.map((item) => (
                          <div key={item.id} className="rounded-xl border border-gray-200 p-4">
                            <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                              <div>
                                <div className="flex flex-wrap items-center gap-2">
                                  <p className="text-sm font-semibold text-gray-900">
                                    {formatarNomeBeneficio(item.codigo, item.nome)}
                                  </p>
                                  <Badge variant="outline">{item.origem === 'MANUAL_ADMIN' ? 'Manual' : 'Créditos'}</Badge>
                                  {item.codigo === 'OCULTAR_IDADE' ? <Badge className="bg-slate-100 text-slate-700">Ocultar idade</Badge> : null}
                                </div>
                                <p className="mt-1 text-xs text-gray-500">
                                  Início: {formatarData(item.dataInicio)} · Fim: {item.dataFim ? formatarData(item.dataFim) : 'sem expiração'}
                                </p>
                                {item.observacao ? <p className="mt-2 text-sm text-gray-600">{item.observacao}</p> : null}
                              </div>
                              <div className="flex flex-wrap gap-2">
                                <Badge className="bg-emerald-50 text-emerald-700">
                                  {item.manual ? 'Concessão manual' : `${item.creditosCobrados} créditos`}
                                </Badge>
                                {item.podeDesativar ? (
                                  <Button variant="outline" onClick={() => setRevokeDialog({ open: true, item, motivo: '', observacaoInterna: '' })}>
                                    Desativar
                                  </Button>
                                ) : (
                                  <p className="basis-full text-xs text-amber-700">
                                    Revogação indisponível pelo backend para este registro.
                                  </p>
                                )}
                              </div>
                            </div>
                          </div>
                        ))
                      )}
                    </div>
                  </div>

                  <div className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
                    <div className="mb-4">
                      <h3 className="text-base font-semibold text-gray-900">Benefícios expirados ou encerrados</h3>
                      <p className="mt-1 text-sm text-gray-500">
                        Histórico útil para auditoria e análise de concessões anteriores.
                      </p>
                    </div>
                    <div className="space-y-3">
                      {beneficiosExpiradosVisiveis.length === 0 ? (
                        <p className="text-sm text-gray-400">Nenhum benefício expirado registrado.</p>
                      ) : (
                        beneficiosExpiradosVisiveis.map((item) => (
                          <div key={item.id} className="rounded-xl border border-gray-200 p-4">
                            <div className="flex flex-wrap items-center gap-2">
                              <p className="text-sm font-semibold text-gray-900">
                                {formatarNomeBeneficio(item.codigo, item.nome)}
                              </p>
                              <Badge variant="outline">{item.status}</Badge>
                              <Badge className="bg-gray-100 text-gray-700">{item.origem === 'MANUAL_ADMIN' ? 'Manual' : 'Créditos'}</Badge>
                            </div>
                            <p className="mt-2 text-xs text-gray-500">
                              Início: {formatarData(item.dataInicio)} · Fim: {item.dataFim ? formatarData(item.dataFim) : 'sem expiração'}
                            </p>
                            {item.observacao ? <p className="mt-2 text-sm text-gray-600">{item.observacao}</p> : null}
                          </div>
                        ))
                      )}
                    </div>
                  </div>
                </div>

                <div className="space-y-6">
                  <div className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
                    <div className="mb-4">
                      <h3 className="text-base font-semibold text-gray-900">Recomendações rápidas</h3>
                      <p className="mt-1 text-sm text-gray-500">Atalhos comerciais para agir rápido em anúncios sem recursos pagos.</p>
                    </div>
                    <div className="space-y-3">
                      <div className="rounded-xl border border-gray-200 p-4">
                        <div className="flex items-start gap-3">
                          <ChatBubbleLeftRightIcon className="mt-0.5 h-5 w-5 text-emerald-600" />
                          <div>
                            <p className="text-sm font-semibold text-gray-900">WhatsApp no card</p>
                            <p className="mt-1 text-sm text-gray-500">Útil quando o anúncio precisa converter mais rápido direto da listagem.</p>
                          </div>
                        </div>
                      </div>
                      <div className="rounded-xl border border-gray-200 p-4">
                        <div className="flex items-start gap-3">
                          <EyeSlashIcon className="mt-0.5 h-5 w-5 text-slate-600" />
                          <div>
                            <p className="text-sm font-semibold text-gray-900">Ocultar idade</p>
                            <p className="mt-1 text-sm text-gray-500">Já funciona como benefício premium temporário no backend e na vitrine pública.</p>
                          </div>
                        </div>
                      </div>
                      <div className="rounded-xl border border-gray-200 p-4">
                        <div className="flex items-start gap-3">
                          <BanknotesIcon className="mt-0.5 h-5 w-5 text-pink-600" />
                          <div>
                            <p className="text-sm font-semibold text-gray-900">Impulsionamento + benefícios</p>
                            <p className="mt-1 text-sm text-gray-500">A concessão manual complementa o plano atual sem substituir compras com créditos.</p>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
                    <div className="mb-4">
                      <h3 className="text-base font-semibold text-gray-900">Histórico auditável</h3>
                      <p className="mt-1 text-sm text-gray-500">Toda alteração manual fica vinculada ao anúncio, ao benefício e ao responsável.</p>
                    </div>
                    <div className="space-y-3">
                      {historicoVisivel.length === 0 ? (
                        <p className="text-sm text-gray-400">Nenhuma ação auditada para este anúncio.</p>
                      ) : (
                        historicoVisivel.map((item, index) => (
                          <div key={`${item.acao}-${item.dataHora}-${index}`} className="rounded-xl border border-gray-200 p-4">
                            <div className="flex flex-wrap items-center gap-2">
                              <Badge variant="outline">{item.acao}</Badge>
                              <p className="text-sm font-semibold text-gray-900">{formatarCodigo(item.codigo)}</p>
                            </div>
                            <p className="mt-2 text-xs text-gray-500">{item.actorEmail || 'Admin'} · {formatarData(item.dataHora)}</p>
                            <div className="mt-3 space-y-1 text-sm text-gray-600">
                              <p>De: {item.valorAnterior || '-'}</p>
                              <p>Para: {item.valorNovo || '-'}</p>
                              {item.duracaoHoras ? <p>Duração: {item.duracaoHoras}h</p> : null}
                              {item.dataInicio ? <p>Início: {formatarData(item.dataInicio)}</p> : null}
                              {item.dataFim ? <p>Fim: {formatarData(item.dataFim)}</p> : null}
                              {item.motivo ? <p>Motivo: {item.motivo}</p> : null}
                              {item.observacaoInterna ? <p>Observação: {item.observacaoInterna}</p> : null}
                            </div>
                          </div>
                        ))
                      )}
                    </div>
                  </div>
                </div>
              </div>
            </>
          )}
        </div>
      </div>

      <Dialog
        open={revokeDialog.open}
        onOpenChange={(open) => !open && setRevokeDialog({ open: false, item: null, motivo: '', observacaoInterna: '' })}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {revokeDialog.item
                ? `Desativar ${formatarNomeBeneficio(revokeDialog.item.codigo, revokeDialog.item.nome)} deste anúncio?`
                : 'Desativar benefício'}
            </DialogTitle>
            <DialogDescription>
              {revokeMotivoObrigatorio
                ? 'Benefícios comprados ou ativados com créditos exigem motivo para auditoria. Apenas esta ativação será desativada.'
                : 'A concessão manual será encerrada apenas para este benefício. O motivo é opcional, salvo validação do backend.'}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="rounded-lg bg-gray-50 p-3 text-sm text-gray-700">
              {revokeDialog.item ? (
                <>
                  <p className="font-semibold text-gray-900">
                    {formatarNomeBeneficio(revokeDialog.item.codigo, revokeDialog.item.nome)}
                  </p>
                  <p className="mt-1 text-xs text-gray-500">
                    {revokeDialog.item.dataFim ? `Válido até ${formatarData(revokeDialog.item.dataFim)}` : 'Sem data de término definida'}
                  </p>
                </>
              ) : null}
            </div>

            <label className="space-y-2 text-sm text-gray-700">
              <span className="font-medium">{revokeMotivoObrigatorio ? 'Motivo obrigatório' : 'Motivo (opcional)'}</span>
              <Input
                value={revokeDialog.motivo}
                onChange={(event) => setRevokeDialog((prev) => ({ ...prev, motivo: event.target.value }))}
                placeholder={revokeMotivoObrigatorio ? 'Ex.: estorno, ajuste comercial, solicitação do anunciante' : 'Ex.: fim da campanha, ajuste operacional'}
                required={revokeMotivoObrigatorio}
              />
            </label>

            <label className="space-y-2 text-sm text-gray-700">
              <span className="font-medium">Observação interna</span>
              <textarea
                rows={4}
                value={revokeDialog.observacaoInterna}
                onChange={(event) => setRevokeDialog((prev) => ({ ...prev, observacaoInterna: event.target.value }))}
                className="w-full rounded-xl border border-gray-200 px-3 py-3 text-sm outline-none focus:border-[#FC1EAD]"
                placeholder="Observação complementar para auditoria."
              />
            </label>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setRevokeDialog({ open: false, item: null, motivo: '', observacaoInterna: '' })}>
              Cancelar
            </Button>
            <Button
              onClick={() => void handleRevoke()}
              disabled={saving || (revokeMotivoObrigatorio && !revokeDialog.motivo.trim())}
              className="bg-red-600 text-white hover:bg-red-700"
            >
              {saving ? 'Salvando...' : 'Desativar benefício'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}
