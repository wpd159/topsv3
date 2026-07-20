'use client'

/**
 * Fluxo de decisão (backend AnuncioService):
 * - PUT /anuncios/{id}/aprovar: fila — PENDENTE OU revisão aberta.
 * - PUT /anuncios/{id}/rejeitar: revisão aberta OU PENDENTE sem revisão.
 * Ações legadas de status e remoção permanecem indisponíveis até contrato V3 próprio.
 */

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import {
  ArrowLeftIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  CheckCircleIcon,
  GiftIcon,
  PencilSquareIcon,
  TrashIcon,
} from '@heroicons/react/24/solid'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ContractState, PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import AdminAnuncioStoriesSection from '@/app/(painel-admin)/admin/components/anuncios/admin-anuncio-stories-section'
import FotosAnuncioSection from '@/app/(painel-admin)/admin/components/fotos-anuncio-section'
import { useAuth } from '@/context/AuthContext'
import { enviarIndexNowNoCliente, montarUrlsIndexNowAnuncio } from '@/lib/seo/indexnow-client'
import { corrigirTextoCorrompido } from '@/lib/text/encoding'
import { normalizarCategoria } from '@/utils/normalizer'
import { formatCPF } from '@/utils/formatter'
import {
  alterarStatusStaffApi,
  approveAnuncioApi,
  ativarPremiumBeneficioAdmin,
  desativarPremiumBeneficioAdmin,
  fetchComplianceAuditForAnuncio,
  fetchVisitorVerificationEventsForAnuncio,
  fetchPremiumAnuncioDetailAdmin,
  fetchStaffAnuncioDetail,
  fetchStaffAnunciosList,
  fetchStaffRevision,
  notifyModerationDataUpdated,
  rejectAnuncioApi,
  removerAnuncioLogicamenteStaffApi,
} from '../api/client'
import type {
  AdminAuditLogItem,
  ModerationAnuncioDetail,
  ModerationRevisionDetail,
  VisitorVerificationAuditItem,
} from '../api/types'
import { ModeracaoV2AuditTimeline } from './moderacao-v2-audit-timeline'
import { AnuncioStaffEditForm } from './anuncio-staff-edit-form'
import { ModeracaoV2MediaGallery } from './moderacao-v2-media-gallery'

/** Benefícios ativáveis pelo painel (STORIES permanece fora). Códigos alinhados ao enum FeatureCodigo (backend). */
const PREMIUM_ACTIVATABLE_OPTIONS = [
  { codigo: 'CARROSSEL_FOTOS', label: 'Carrossel de Fotos' },
  { codigo: 'FOTOS_EXTRA_5', label: 'Até 10 fotos' },
  { codigo: 'VIDEO_1', label: 'Vídeo' },
  { codigo: 'WHATSAPP_CARD', label: 'WhatsApp no card' },
  { codigo: 'OCULTAR_IDADE', label: 'Ocultar idade' },
  { codigo: 'ANUNCIO_TOPO', label: 'Destaque' },
] as const

const STORIES_BENEFIT_CODE = 'STORIES'
const MODERATION_V2_CONTEXT_STORAGE_KEY = 'moderacao-v2:list-context'

function normalizePremiumCode(codigo: string | null | undefined) {
  const normalized = (codigo ?? '').trim().toUpperCase()
  return normalized === 'DESTAQUE' ? 'ANUNCIO_TOPO' : normalized
}

function isPremiumFlowVisibleCode(codigo: string | null | undefined) {
  return normalizePremiumCode(codigo) !== STORIES_BENEFIT_CODE
}

function notifyPremiumQuickUpdated(anuncioId: string | number) {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent('moderacao-v2-premium-quick-updated', { detail: { anuncioId, force: true } }))
}

type PremiumBeneficioAtivoRow = {
  id?: string | number | null
  codigo?: string | null
  nome?: string | null
  status?: string | null
  podeDesativar?: boolean | null
  origem?: string | null
  manual?: boolean | null
  dataInicio?: string | null
  dataFim?: string | null
}

type ModerationV2ListContext = {
  ids: string[]
  href?: string | null
  savedAt?: number | null
}

function rotuloStatusPremium(b: PremiumBeneficioAtivoRow): string {
  const st = (b.status ?? '').trim().toUpperCase()
  if (st === 'AGENDADO') return 'Agendado'
  if (st === 'EXPIRADO') return 'Expirado'
  if (st === 'CANCELADO') return 'Cancelado'
  if (st === 'ATIVO' && b.dataInicio) {
    const t = Date.parse(b.dataInicio)
    if (!Number.isNaN(t) && t > Date.now()) return 'Agendado'
  }
  if (st === 'ATIVO') return 'Ativo'
  return b.status?.trim() || '—'
}

function texto(v?: string | null, fb = '—') {
  const s = corrigirTextoCorrompido(v ?? '').trim()
  return s || fb
}

function scrollToSection(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

/** Alinhado a `moderacao-v2-list` / `queue` — métricas inteiras não negativas. */
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

function formatShortDateTime(value?: string | null): string {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return texto(value, '')
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(d)
}

function formatPremiumDuration(start?: string | null, end?: string | null): string {
  if (!start || !end) return ''
  const a = new Date(start)
  const b = new Date(end)
  if (Number.isNaN(a.getTime()) || Number.isNaN(b.getTime())) return ''
  const minutes = Math.max(0, Math.round((b.getTime() - a.getTime()) / 60000))
  if (minutes < 60) return `${minutes} min`
  const hours = Math.round(minutes / 60)
  if (hours < 48) return `${hours}h`
  return `${Math.round(hours / 24)}d`
}

function readStoredModerationContext(): ModerationV2ListContext | null {
  if (typeof window === 'undefined') return null
  try {
    const raw = window.sessionStorage.getItem(MODERATION_V2_CONTEXT_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as ModerationV2ListContext
    const ids = Array.isArray(parsed.ids)
      ? parsed.ids.map((id) => String(id).trim()).filter(Boolean)
      : []
    if (ids.length === 0) return null
    return {
      ids,
      href: typeof parsed.href === 'string' && parsed.href.trim() ? parsed.href : '/admin/moderacao-v2',
      savedAt: typeof parsed.savedAt === 'number' ? parsed.savedAt : null,
    }
  } catch {
    return null
  }
}

/**
 * Mescla campos do JSON público de anúncio (ex.: {@code AnuncioResponseDTO} em /aprovar, /rejeitar, /staff/.../status)
 * sobre o detalhe administrativo já carregado, evitando novo fetch completo.
 */
function mergeStaffDetailFromPublicAnuncioPayload(
  prev: ModerationAnuncioDetail,
  payload: Record<string, unknown>
): ModerationAnuncioDetail {
  const out: ModerationAnuncioDetail = { ...prev }
  const takeStr = (key: keyof ModerationAnuncioDetail, jsonKey: string) => {
    const v = payload[jsonKey]
    if (v === undefined || v === null) return
    const s = typeof v === 'string' ? v : String(v)
    ;(out as Record<string, unknown>)[key as string] = s
  }
  const takeNum = (key: keyof ModerationAnuncioDetail, jsonKey: string) => {
    const v = payload[jsonKey]
    if (v === undefined || v === null) return
    if (typeof v === 'number' && Number.isFinite(v)) {
      ;(out as Record<string, unknown>)[key as string] = v
    } else if (typeof v === 'string' && v.trim() !== '' && !Number.isNaN(Number(v))) {
      ;(out as Record<string, unknown>)[key as string] = Number(v)
    }
  }
  const takeStrList = (key: keyof ModerationAnuncioDetail, jsonKey: string) => {
    const v = payload[jsonKey]
    if (!Array.isArray(v)) return
    const arr = v.filter((x): x is string => typeof x === 'string')
    ;(out as Record<string, unknown>)[key as string] = arr
  }

  takeStr('slug', 'slug')
  takeStr('titulo', 'titulo')
  takeStr('descricao', 'descricao')
  takeNum('cidadeId', 'cidadeId')
  takeStr('cidadeNome', 'cidadeNome')
  takeNum('bairroId', 'bairroId')
  takeStr('bairroNome', 'bairroNome')
  takeStr('localizacaoLabel', 'localizacaoLabel')
  takeNum('preco', 'preco')
  takeStrList('fotosUrl', 'fotosUrl')
  const videos = payload.videosAnuncio
  if (Array.isArray(videos)) {
    out.videosUrl = videos.filter((x): x is string => typeof x === 'string')
  }
  takeStr('username', 'usernameAnunciante')
  takeStr('nomeCompleto', 'nomeAnunciante')
  takeStr('categoria', 'categoria')
  takeNum('visualizacoes', 'visualizacoes')
  if (typeof payload.pendingRevision === 'boolean') {
    out.pendingRevision = payload.pendingRevision
    out.pendingRevisionId =
      typeof payload.pendingRevisionId === 'number' ? payload.pendingRevisionId : null
    out.pendingRevisionStatus =
      typeof payload.pendingRevisionStatus === 'string' ? payload.pendingRevisionStatus : null
  }
  return out
}

function clearPendingRevisionState<T extends ModerationAnuncioDetail>(anuncio: T): T {
  return {
    ...anuncio,
    pendingRevision: false,
    pendingRevisionId: null,
    pendingRevisionStatus: null,
  }
}

export function ModeracaoV2Detail({ anuncioId }: { anuncioId: string | number }) {
  const router = useRouter()
  const { usuario } = useAuth()
  const isAdmin = usuario?.cargo === 'ADMIN'
  const isStaffModeration = usuario?.cargo === 'ADMIN' || usuario?.cargo === 'MODERADOR'

  const [anuncio, setAnuncio] = useState<ModerationAnuncioDetail | null>(null)
  const [revision, setRevision] = useState<ModerationRevisionDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  /** Evita dupla chamada à API antes do re-render de `busy` (ex.: duplo clique). */
  const staffDecisionLockRef = useRef(false)
  const [rejectOpen, setRejectOpen] = useState(false)
  const [rejectMotivo, setRejectMotivo] = useState('')
  const [removeOpen, setRemoveOpen] = useState(false)
  const [removeMotivo, setRemoveMotivo] = useState('')
  const [deleteContractOpen, setDeleteContractOpen] = useState(false)
  const deleteContract = usePendingContractActions('Exclusao definitiva de anuncio')
  const [auditLogs, setAuditLogs] = useState<AdminAuditLogItem[]>([])
  const [visitorAuditEvents, setVisitorAuditEvents] = useState<VisitorVerificationAuditItem[]>([])
  const [auditLoaded, setAuditLoaded] = useState(false)
  const [auditError, setAuditError] = useState<unknown>(null)
  const [auditReload, setAuditReload] = useState(0)
  const [premiumDetail, setPremiumDetail] = useState<Record<string, unknown> | null>(null)
  const [premiumError, setPremiumError] = useState<unknown>(null)
  const [premiumReload, setPremiumReload] = useState(0)
  /** Códigos selecionados para ativação (múltipla). */
  const [premiumSelected, setPremiumSelected] = useState<Set<string>>(() => new Set())
  const [premiumObs, setPremiumObs] = useState('')
  const [auditTimelineVisible, setAuditTimelineVisible] = useState(false)
  const [staffEditFormExpanded, setStaffEditFormExpanded] = useState(false)
  const [listContext, setListContext] = useState<ModerationV2ListContext | null>(null)

  const load = useCallback(async () => {
    if (!String(anuncioId).trim()) return
    setLoading(true)
    try {
      const [a, r] = await Promise.all([
        fetchStaffAnuncioDetail(anuncioId),
        fetchStaffRevision(anuncioId),
      ])
      setAnuncio(r ? a : clearPendingRevisionState(a))
      setRevision(r)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Erro ao carregar.')
      setAnuncio(null)
      setRevision(null)
    } finally {
      setLoading(false)
    }
  }, [anuncioId])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    const stored = readStoredModerationContext()
    if (stored) {
      setListContext(stored)
      return
    }
    let cancelled = false
    void fetchStaffAnunciosList()
      .then((rows) => {
        if (cancelled) return
        const ids = rows.map((row) => String(row.id).trim()).filter(Boolean)
        if (ids.length) setListContext({ ids, href: '/admin/moderacao-v2', savedAt: Date.now() })
      })
      .catch(() => {
        /* navegacao auxiliar opcional */
      })
    return () => {
      cancelled = true
    }
  }, [anuncioId])

  useEffect(() => {
    setAuditTimelineVisible(false)
    setStaffEditFormExpanded(false)
    setAuditLogs([])
    setVisitorAuditEvents([])
    setAuditLoaded(false)
    setAuditError(null)
    setPremiumDetail(null)
    setPremiumError(null)
  }, [anuncioId])

  useEffect(() => {
    if (!isAdmin || anuncio?.id == null) return
    let cancelled = false
    setPremiumError(null)
    void fetchPremiumAnuncioDetailAdmin(anuncioId)
      .then((prem) => {
        if (!cancelled && prem) setPremiumDetail(prem)
      })
      .catch((error) => {
        if (!cancelled) setPremiumError(error)
      })
    return () => {
      cancelled = true
    }
  }, [isAdmin, anuncioId, anuncio?.id, premiumReload])

  useEffect(() => {
    if (!isAdmin || anuncio?.id == null) return
    let cancelled = false
    let auditDone = false
    let visitorsDone = false
    setAuditError(null)

    const finish = () => {
      if (!cancelled && auditDone && visitorsDone) {
        setAuditLoaded(true)
      }
    }

    void fetchComplianceAuditForAnuncio(anuncioId)
      .then((audit) => {
        if (!cancelled) setAuditLogs(audit)
      })
      .catch((error) => {
        if (!cancelled) setAuditError(error)
      })
      .finally(() => {
        auditDone = true
        finish()
      })

    void fetchVisitorVerificationEventsForAnuncio(anuncioId)
      .then((visitors) => {
        if (!cancelled) setVisitorAuditEvents(visitors)
      })
      .catch((error) => {
        if (!cancelled) setAuditError(error)
      })
      .finally(() => {
        visitorsDone = true
        finish()
      })

    return () => {
      cancelled = true
    }
  }, [isAdmin, anuncioId, anuncio?.id, auditReload])

  const removedLogical = Boolean(anuncio?.removidoLogicamente)

  const needsDecision = useMemo(() => {
    if (!anuncio || removedLogical) return false
    if (anuncio.status === 'PENDENTE') return true
    if (revision) return true
    return Boolean(anuncio.pendingRevision)
  }, [anuncio, revision, removedLogical])

  const useRejeitarEndpoint = Boolean(revision) || anuncio?.status === 'PENDENTE'

  const handleApprove = async () => {
    if (staffDecisionLockRef.current) return
    staffDecisionLockRef.current = true
    try {
      setBusy(true)
      const payload = await approveAnuncioApi(anuncioId, 'Aprovação registrada na moderação v2.')
      toast.success('Decisão registrada (aprovação).')
      notifyModerationDataUpdated()
      void enviarIndexNowNoCliente(
        montarUrlsIndexNowAnuncio({
          slug: payload?.slug as string | undefined,
          estadoUf: (payload?.estadoUf as string | undefined) ?? anuncio?.estadoUf ?? undefined,
          cidadeNome: (payload?.cidadeNome as string | undefined) ?? anuncio?.cidadeNome ?? undefined,
          bairroNome: (payload?.bairroNome as string | undefined) ?? anuncio?.bairroNome ?? undefined,
        })
      )
      await load()
      if (isAdmin) {
        try {
          const [audit, visitors] = await Promise.all([
            fetchComplianceAuditForAnuncio(anuncioId),
            fetchVisitorVerificationEventsForAnuncio(anuncioId),
          ])
          setAuditLogs(audit)
          setVisitorAuditEvents(visitors)
        } catch {
          /* opcional */
        }
      }
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch (e) {
      toast.error(e instanceof Error ? corrigirTextoCorrompido(e.message) : 'Falha ao aprovar.')
    } finally {
      staffDecisionLockRef.current = false
      setBusy(false)
    }
  }

  const handleRejectSubmit = async () => {
    const m = rejectMotivo.trim()
    if (!m) {
      toast.warning('Informe o motivo.')
      return
    }
    if (staffDecisionLockRef.current) return
    staffDecisionLockRef.current = true
    try {
      setBusy(true)
      let payload: Record<string, unknown>
      if (useRejeitarEndpoint) {
        payload = await rejectAnuncioApi(anuncioId, m)
      } else {
        payload = await alterarStatusStaffApi(anuncioId, 'REJEITADO', m)
      }
      toast.success('Reprovação registrada.')
      notifyModerationDataUpdated()
      setRejectOpen(false)
      setRejectMotivo('')
      setAnuncio((prev) => {
        if (!prev) return prev
        const merged = mergeStaffDetailFromPublicAnuncioPayload(prev, payload)
        return {
          ...merged,
          status: 'REJEITADO',
          pendingRevision: false,
          pendingRevisionId: null,
          pendingRevisionStatus: null,
        }
      })
      setRevision(null)
      if (isAdmin) {
        try {
          const [audit, visitors] = await Promise.all([
            fetchComplianceAuditForAnuncio(anuncioId),
            fetchVisitorVerificationEventsForAnuncio(anuncioId),
          ])
          setAuditLogs(audit)
          setVisitorAuditEvents(visitors)
        } catch {
          /* opcional */
        }
      }
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch (e) {
      toast.error(e instanceof Error ? corrigirTextoCorrompido(e.message) : 'Falha ao rejeitar.')
    } finally {
      staffDecisionLockRef.current = false
      setBusy(false)
    }
  }

  const togglePausa = async () => {
    if (!anuncio) return
    if (staffDecisionLockRef.current) return
    staffDecisionLockRef.current = true
    try {
      setBusy(true)
      const next = anuncio.status === 'ATIVO' ? 'PAUSADO' : 'ATIVO'
      const payload = await alterarStatusStaffApi(anuncioId, next)
      toast.success(next === 'PAUSADO' ? 'Pausado.' : 'Ativado.')
      notifyModerationDataUpdated()
      setAnuncio((prev) => {
        if (!prev) return prev
        const merged = { ...mergeStaffDetailFromPublicAnuncioPayload(prev, payload), status: next }
        return revision ? merged : clearPendingRevisionState(merged)
      })
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Falha ao alterar status.')
    } finally {
      staffDecisionLockRef.current = false
      setBusy(false)
    }
  }

  const handleRemoveLogical = async () => {
    const m = removeMotivo.trim()
    if (!m) {
      toast.warning('Informe o motivo da remoção lógica.')
      return
    }
    if (staffDecisionLockRef.current) return
    staffDecisionLockRef.current = true
    try {
      setBusy(true)
      await removerAnuncioLogicamenteStaffApi(anuncioId, m)
      toast.success('Anúncio removido logicamente (dados preservados).')
      notifyModerationDataUpdated()
      setRemoveOpen(false)
      setRemoveMotivo('')
      setAnuncio((prev) => (prev ? clearPendingRevisionState({ ...prev, removidoLogicamente: true }) : prev))
      setRevision(null)
    } catch (e) {
      toast.error(e instanceof Error ? corrigirTextoCorrompido(e.message) : 'Falha ao remover.')
    } finally {
      staffDecisionLockRef.current = false
      setBusy(false)
    }
  }

  const handleAtivarPremiumSelecionados = async () => {
    const codes = [...premiumSelected]
    if (codes.length === 0) {
      toast.warning('Selecione pelo menos um benefício.')
      return
    }
    const obs = premiumObs.trim()
    setBusy(true)
    const ok: string[] = []
    const falhas: string[] = []
    try {
      for (const codigo of codes) {
        try {
          await ativarPremiumBeneficioAdmin(anuncioId, {
            codigo,
            observacaoInterna: obs,
          })
          ok.push(codigo)
        } catch (e) {
          const msg = e instanceof Error ? corrigirTextoCorrompido(e.message) : 'Falha'
          falhas.push(`${codigo}: ${msg}`)
        }
      }
      if (ok.length > 0) {
        toast.success(
          ok.length === 1
            ? `Benefício ativado: ${ok[0]}.`
            : `${ok.length} benefícios ativados: ${ok.join(', ')}.`
        )
      }
      if (falhas.length > 0) {
        toast.error(falhas.join(' · '))
      }
      setPremiumDetail(await fetchPremiumAnuncioDetailAdmin(anuncioId))
      notifyPremiumQuickUpdated(anuncioId)
      try {
        setAuditLogs(await fetchComplianceAuditForAnuncio(anuncioId))
      } catch {
        /* vazio */
      }
      setPremiumSelected((prev) => {
        const next = new Set(prev)
        for (const c of ok) {
          next.delete(c)
        }
        return next
      })
    } finally {
      setBusy(false)
    }
  }

  const handleDesativarPremium = async (ativacaoId: string | number, beneficio?: PremiumBeneficioAtivoRow) => {
    if (beneficio?.manual === false && typeof window !== 'undefined') {
      const label = texto(beneficio.nome, beneficio.codigo ?? 'benefício')
      const confirmar = window.confirm(
        `Desativar ${label}? Esta é uma ação administrativa excepcional para um benefício comprado. O efeito será removido, mas os dados do anúncio serão preservados.`
      )
      if (!confirmar) return
    }
    const obs = premiumObs.trim()
    setBusy(true)
    try {
      await desativarPremiumBeneficioAdmin(ativacaoId, {
        motivo: 'Desativação manual — moderação v2',
        observacaoInterna: obs || undefined,
      })
      toast.success('Benefício desativado (efeito removido; dados do anúncio preservados).')
      setPremiumDetail(await fetchPremiumAnuncioDetailAdmin(anuncioId))
      notifyPremiumQuickUpdated(anuncioId)
      try {
        setAuditLogs(await fetchComplianceAuditForAnuncio(anuncioId))
      } catch {
        /* opcional */
      }
    } catch (e) {
      toast.error(e instanceof Error ? corrigirTextoCorrompido(e.message) : 'Falha ao desativar benefício.')
    } finally {
      setBusy(false)
    }
  }

  const localTexto = useMemo(() => {
    if (!anuncio) return '—'
    const dto = texto(anuncio.localizacaoLabel, '').trim()
    if (dto) return dto
    const uf = texto(anuncio.estadoUf, '').trim()
    const cid = texto(anuncio.cidadeNome, '').trim()
    const bai = texto(anuncio.bairroNome, '').trim()
    const part = [bai, cid ? (uf ? `${cid}/${uf}` : cid) : ''].filter(Boolean).join(' — ')
    return part || '—'
  }, [anuncio])

  const beneficiosAtivos = useMemo(
    () =>
      ((premiumDetail?.beneficiosAtivos as PremiumBeneficioAtivoRow[] | undefined) ?? []).filter((item) =>
        isPremiumFlowVisibleCode(item.codigo)
      ),
    [premiumDetail?.beneficiosAtivos]
  )
  const beneficiosExpirados = useMemo(
    () =>
      ((premiumDetail?.beneficiosExpirados as Array<{ codigo?: string; nome?: string }> | undefined) ?? []).filter((item) =>
        isPremiumFlowVisibleCode(item.codigo)
      ),
    [premiumDetail?.beneficiosExpirados]
  )
  const historicoPremium = useMemo(
    () =>
      (
        (premiumDetail?.historico as Array<{ acao?: string; dataHora?: string; actorEmail?: string; codigo?: string }> | undefined) ??
        []
      ).filter((item) => isPremiumFlowVisibleCode(item.codigo)),
    [premiumDetail?.historico]
  )

  const premiumJaAtivoOuAgendado = useMemo(() => {
    const map = new Map<string, { status: string; label: string }>()
    for (const b of beneficiosAtivos) {
      const codigo = normalizePremiumCode(b.codigo)
      if (!codigo) continue
      const st = (b.status ?? '').trim().toUpperCase()
      if (st !== 'ATIVO' && st !== 'AGENDADO') continue
      const label = st === 'AGENDADO' ? 'Agendado' : 'Ativo'
      map.set(codigo, { status: st, label })
    }
    return map
  }, [beneficiosAtivos])

  /** Uma linha por código (para saber id da ativação e se pode desativar na mesma UI do checklist). */
  const beneficioAtivoPorCodigo = useMemo(() => {
    const m = new Map<string, PremiumBeneficioAtivoRow>()
    for (const b of beneficiosAtivos) {
      const c = normalizePremiumCode(b.codigo)
      if (c) m.set(c, b)
    }
    return m
  }, [beneficiosAtivos])

  useEffect(() => {
    setPremiumSelected((prev) => {
      let mudou = false
      const next = new Set(prev)
      for (const codigo of premiumJaAtivoOuAgendado.keys()) {
        if (next.has(codigo)) {
          next.delete(codigo)
          mudou = true
        }
      }
      return mudou ? next : prev
    })
  }, [premiumJaAtivoOuAgendado])

  const metricasAnuncio = useMemo(() => {
    if (!anuncio) {
      return { views: '—' as string, wa: '—' as string, conv: '—' as string }
    }
    const v = nMetric(anuncio.visualizacoes)
    const c = nMetric(anuncio.cliquesWhatsapp)
    const conv =
      v > 0
        ? `${new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format((c / v) * 100)}%`
        : '—'
    return {
      views: formatMetric(anuncio.visualizacoes),
      wa: formatMetric(anuncio.cliquesWhatsapp),
      conv,
    }
  }, [anuncio])

  const contextIndex = useMemo(() => {
    if (!listContext || !String(anuncioId).trim()) return -1
    return listContext.ids.findIndex((id) => id === String(anuncioId))
  }, [anuncioId, listContext])

  const previousAnuncioId = contextIndex > 0 && listContext ? listContext.ids[contextIndex - 1] : null
  const nextAnuncioId =
    listContext && contextIndex >= 0 && contextIndex < listContext.ids.length - 1
      ? listContext.ids[contextIndex + 1]
      : null
  const contextPositionLabel =
    listContext && contextIndex >= 0 ? `${contextIndex + 1} / ${listContext.ids.length}` : 'URL direta'
  const backToListHref = listContext?.href || '/admin/moderacao-v2'

  const goToSibling = (id: string | null) => {
    if (!id) return
    const qs = typeof window !== 'undefined' ? window.location.search : ''
    router.push(`/admin/moderacao-v2/${id}${qs}`)
  }

  const activePremiumCount = useMemo(
    () =>
      beneficiosAtivos.filter((b) => {
        const st = (b.status ?? '').trim().toUpperCase()
        return st === 'ATIVO' || st === 'AGENDADO'
      }).length,
    [beneficiosAtivos]
  )

  const latestAuditLog = auditLogs[0] ?? null
  const latestPremiumHistory = historicoPremium[0] ?? null

  const publishedPhotoCount = anuncio?.fotosUrl?.length ?? 0
  const publishedVideoCount = anuncio?.videosUrl?.length ?? 0
  const pendingMediaCount =
    (revision?.pendingMediaItems?.length ?? 0) +
    (revision?.pendingFotos?.length ?? 0) +
    (revision?.pendingVideos?.length ?? 0)
  const hasActivePremium = activePremiumCount > 0
  const hasActiveVideo =
    premiumJaAtivoOuAgendado.has('VIDEO_1') || premiumJaAtivoOuAgendado.has('VIDEO') || publishedVideoCount > 0
  const hasHiddenAge = premiumJaAtivoOuAgendado.has('OCULTAR_IDADE')
  const operationalBadges = [
    hasActivePremium
      ? { label: 'PREMIUM', className: 'border-[#f0198f]/30 bg-pink-50 text-[#b8146d]' }
      : null,
    hasActiveVideo ? { label: 'VÍDEO', className: 'border-sky-200 bg-sky-50 text-sky-800' } : null,
    hasHiddenAge ? { label: 'IDADE OCULTA', className: 'border-slate-300 bg-slate-50 text-slate-800' } : null,
    revision || anuncio?.pendingRevision
      ? { label: 'REVISÃO', className: 'border-amber-200 bg-amber-50 text-amber-900' }
      : null,
    removedLogical ? { label: 'REMOVIDO', className: 'border-rose-200 bg-rose-50 text-rose-900' } : null,
  ].filter(Boolean) as Array<{ label: string; className: string }>

  if (loading) {
    return <div className="py-16 text-center text-gray-500">Carregando…</div>
  }
  if (!anuncio) {
    return (
      <div className="py-16 text-center text-gray-500">
        Anúncio não encontrado.{' '}
        <Link href={backToListHref} className="text-[#f0198f] underline">
          Voltar
        </Link>
      </div>
    )
  }

  return (
    <div className="relative -mx-4 -mt-4 pb-24 lg:-mx-8 lg:-mt-8">
      <header className="sticky top-0 z-40 border-b border-gray-200 bg-white/95 px-4 py-3 shadow-sm backdrop-blur-md md:px-0">
        <div className="mx-auto flex max-w-5xl flex-col gap-3">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex min-w-0 items-start gap-3">
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="shrink-0 text-gray-600"
              onClick={() => router.push(backToListHref)}
            >
              <ArrowLeftIcon className="h-5 w-5" />
            </Button>
            <div className="min-w-0">
              <p className="text-xs font-medium text-gray-500">
                #{anuncioId} · @{texto(anuncio.username, 'sem-usuario').replace(/^@+/, '')}
              </p>
              <h1 className="truncate text-lg font-bold text-gray-900">{texto(anuncio.titulo, 'Sem título')}</h1>
              <div className="mt-1 flex flex-wrap gap-2">
                <Badge variant="outline" className="text-[11px]">
                  {anuncio.status}
                </Badge>
                {operationalBadges.map((badge) => (
                  <Badge key={badge.label} variant="outline" className={`text-[11px] ${badge.className}`}>
                    {badge.label}
                  </Badge>
                ))}
              </div>
            </div>
          </div>

            <div className="flex flex-wrap items-center gap-2">
            <div className="mr-1 flex items-center gap-1 rounded-full border border-gray-200 bg-gray-50 p-1">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="h-8 rounded-full px-2 text-xs"
                disabled={!previousAnuncioId}
                onClick={() => goToSibling(previousAnuncioId)}
                title="Anúncio anterior no contexto da lista"
              >
                <ChevronLeftIcon className="mr-1 h-4 w-4" />
                Anterior
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="h-8 rounded-full px-2 text-xs"
                disabled={!nextAnuncioId}
                onClick={() => goToSibling(nextAnuncioId)}
                title="Próximo anúncio no contexto da lista"
              >
                Próximo
                <ChevronRightIcon className="ml-1 h-4 w-4" />
              </Button>
              <span className="px-2 text-xs font-semibold tabular-nums text-gray-500">{contextPositionLabel}</span>
            </div>
            {isStaffModeration && !removedLogical && needsDecision && (
              <>
                <Button
                  type="button"
                  className="bg-emerald-600 text-white hover:bg-emerald-700"
                  disabled={busy}
                  onClick={() => void handleApprove()}
                >
                  <CheckCircleIcon className="mr-1 h-4 w-4" />
                  Aprovar
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  className="border-red-300 text-red-700 hover:bg-red-50"
                  disabled={busy}
                  onClick={() => setRejectOpen(true)}
                >
                  Reprovar
                </Button>
              </>
            )}
            {isStaffModeration && !removedLogical && !needsDecision && anuncio.status === 'ATIVO' && (
              <Button type="button" variant="outline" disabled={busy} onClick={() => void togglePausa()}>
                Pausar
              </Button>
            )}
            {isStaffModeration && !removedLogical && !needsDecision && anuncio.status === 'PAUSADO' && (
              <Button type="button" variant="outline" disabled={busy} onClick={() => void togglePausa()}>
                Reativar
              </Button>
            )}
            {isStaffModeration && !removedLogical && !needsDecision && anuncio.status === 'REJEITADO' && (
              <Button type="button" variant="outline" disabled={busy} onClick={() => void togglePausa()}>
                Ativar
              </Button>
            )}
            {isAdmin && !removedLogical && (
              <Button
                type="button"
                variant="outline"
                className="border-gray-300"
                disabled={busy}
                onClick={() => {
                  setStaffEditFormExpanded(true)
                  requestAnimationFrame(() => scrollToSection('sec-edicao'))
                }}
              >
                <PencilSquareIcon className="mr-1 h-4 w-4" />
                Editar
              </Button>
            )}
            {isAdmin && (
              <Button
                type="button"
                variant="outline"
                className="border-gray-300"
                disabled={busy}
                onClick={() => scrollToSection('sec-premium')}
              >
                <GiftIcon className="mr-1 h-4 w-4" />
                Benefícios
              </Button>
            )}
            {isStaffModeration && !removedLogical && !needsDecision && anuncio.status !== 'REJEITADO' && (
              <Button
                type="button"
                variant="outline"
                className="border-red-200 text-red-700"
                disabled={busy}
                onClick={() => setRejectOpen(true)}
              >
                Reprovar (operacional)
              </Button>
            )}
            {isAdmin && !removedLogical && (
              <Button
                type="button"
                variant="outline"
                className="border-rose-300 text-rose-800"
                disabled={busy}
                onClick={() => setRemoveOpen(true)}
              >
                Remover
              </Button>
            )}
            {isAdmin && !removedLogical && (
              <Button
                type="button"
                variant="outline"
                className="border-red-300 text-red-700 hover:bg-red-50"
                disabled={busy}
                onClick={() => setDeleteContractOpen(true)}
              >
                <TrashIcon className="mr-1 h-4 w-4" />
                Excluir
              </Button>
            )}
            <Link
              href={backToListHref}
              className="text-xs font-medium text-[#f0198f] underline-offset-2 hover:underline"
            >
              Fila
            </Link>
            </div>
          </div>
          <nav className="flex gap-1 overflow-x-auto pb-1 text-[11px] font-semibold uppercase tracking-wide text-gray-500">
            {[
              ['sec-status', 'Status'],
              ['sec-premium', 'Premium'],
              ['sec-compliance', 'Compliance'],
              ['sec-midia', 'Mídia'],
              ['sec-revisao', 'Revisão'],
            ].map(([id, label]) => (
              <button
                key={id}
                type="button"
                className="shrink-0 rounded-full border border-gray-200 bg-white px-3 py-1.5 hover:border-[#f0198f]/40 hover:text-[#f0198f]"
                onClick={() => scrollToSection(id)}
              >
                {label}
              </button>
            ))}
          </nav>
        </div>
      </header>

      <div className="mx-auto mt-5 max-w-5xl space-y-6 px-4 md:px-0">
        <section
          id="sec-status"
          aria-label="STATUS - cockpit operacional"
          className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm scroll-mt-24"
        >
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-wide text-gray-500">STATUS</p>
            <h2 className="mt-1 text-base font-semibold text-gray-900">Métricas operacionais</h2>
          </div>
          <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-3">
            <div className="rounded-xl border border-gray-100 bg-gray-50/60 px-4 py-3">
              <p className="text-xs font-medium text-gray-600">
                <span aria-hidden>👁 </span>Visualizações
              </p>
              <p className="mt-1 text-2xl font-semibold tabular-nums text-gray-900">{metricasAnuncio.views}</p>
            </div>
            <div className="rounded-xl border border-gray-100 bg-gray-50/60 px-4 py-3">
              <p className="text-xs font-medium text-gray-600">
                <span aria-hidden>📲 </span>Cliques WhatsApp
              </p>
              <p className="mt-1 text-2xl font-semibold tabular-nums text-gray-900">{metricasAnuncio.wa}</p>
            </div>
            <div className="rounded-xl border border-[#f0198f]/20 bg-pink-50/50 px-4 py-3">
              <p className="text-xs font-medium text-gray-600">
                <span aria-hidden>🔥 </span>Conversão
              </p>
              <p className="mt-1 text-2xl font-bold tabular-nums text-[#f0198f]">{metricasAnuncio.conv}</p>
            </div>
          </div>
          {(latestAuditLog || latestPremiumHistory) ? (
            <div className="mt-4 rounded-xl border border-gray-100 bg-gray-50/70 p-4">
              <p className="text-[11px] font-semibold uppercase tracking-wide text-gray-500">Logs operacionais</p>
              <div className="mt-3 grid gap-3 text-sm md:grid-cols-2">
                {latestAuditLog ? (
                  <div>
                    <p className="text-xs font-medium text-gray-500">Última ação</p>
                    <p className="font-semibold text-gray-900">{texto(latestAuditLog.actionType)}</p>
                    <p className="text-xs text-gray-600">
                      {texto(latestAuditLog.actorEmail)}{' '}
                      {latestAuditLog.createdAt ? `· ${formatShortDateTime(latestAuditLog.createdAt)}` : ''}
                    </p>
                  </div>
                ) : null}
                {latestAuditLog?.actorEmail ? (
                  <div>
                    <p className="text-xs font-medium text-gray-500">Último moderador</p>
                    <p className="font-semibold text-gray-900">{texto(latestAuditLog.actorEmail)}</p>
                  </div>
                ) : null}
                {latestPremiumHistory ? (
                  <div>
                    <p className="text-xs font-medium text-gray-500">Premium recente</p>
                    <p className="font-semibold text-gray-900">
                      {texto(latestPremiumHistory.codigo || latestPremiumHistory.acao)}
                    </p>
                    <p className="text-xs text-gray-600">
                      {texto(latestPremiumHistory.actorEmail)}{' '}
                      {latestPremiumHistory.dataHora ? `· ${formatShortDateTime(latestPremiumHistory.dataHora)}` : ''}
                    </p>
                  </div>
                ) : null}
              </div>
            </div>
          ) : null}
        </section>

        {revision &&
        (revision.changes?.length ||
          revision.pendingMediaItems?.length ||
          (revision.pendingFotos && revision.pendingFotos.length > 0) ||
          (revision.pendingVideos && revision.pendingVideos.length > 0)) ? (
          <section id="sec-revisao" className="rounded-2xl border-2 border-amber-200 bg-amber-50/50 p-5 scroll-mt-24">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-amber-900">REVISÃO</p>
            <h2 className="mt-1 text-base font-semibold text-amber-950">Remoderação — alterações propostas</h2>
            <div className="mt-3 grid gap-2 sm:grid-cols-3">
              <div className="rounded-xl border border-amber-200/70 bg-white/80 px-3 py-2">
                <p className="text-[11px] font-semibold uppercase tracking-wide text-amber-900">O que mudou</p>
                <p className="mt-1 text-lg font-bold tabular-nums text-gray-950">{revision.changes?.length ?? 0}</p>
                <p className="text-xs text-gray-600">campo(s) de texto</p>
              </div>
              <div className="rounded-xl border border-amber-200/70 bg-white/80 px-3 py-2">
                <p className="text-[11px] font-semibold uppercase tracking-wide text-amber-900">Mídia nova</p>
                <p className="mt-1 text-lg font-bold tabular-nums text-gray-950">{pendingMediaCount}</p>
                <p className="text-xs text-gray-600">item(ns) propostos</p>
              </div>
              <div className="rounded-xl border border-amber-200/70 bg-white/80 px-3 py-2">
                <p className="text-[11px] font-semibold uppercase tracking-wide text-amber-900">Mídia publicada</p>
                <p className="mt-1 text-lg font-bold tabular-nums text-gray-950">
                  {publishedPhotoCount + publishedVideoCount}
                </p>
                <p className="text-xs text-gray-600">
                  {publishedPhotoCount} foto(s) · {publishedVideoCount} vídeo(s)
                </p>
              </div>
            </div>
            <p className="mt-2 rounded-lg border border-amber-200/80 bg-white/80 px-3 py-2 text-sm text-amber-950">
              O que está <strong>publicado</strong> permanece inalterado até você <strong>aprovar</strong>.{' '}
              <strong>Reprovar</strong> descarta a revisão; <strong>Aprovar</strong> aplica o proposto. Tudo nesta mesma
              tela.
            </p>
            {revision.changes && revision.changes.length > 0 && (
              <div className="mt-4 grid gap-3 md:grid-cols-2">
                {revision.changes.map((ch) => (
                  <div key={ch.field} className="border-l-4 border-amber-500 bg-white p-4 shadow-sm">
                    <p className="text-xs font-semibold uppercase tracking-wide text-amber-900">
                      {texto(ch.label, ch.field)}
                    </p>
                    <p className="mt-2 text-[11px] font-medium text-gray-500">Publicado</p>
                    <p className="text-sm text-gray-800">{texto(ch.currentValue)}</p>
                    <p className="mt-2 text-[11px] font-medium text-amber-800">Proposto</p>
                    <p className="text-sm font-medium text-gray-950">{texto(ch.pendingValue)}</p>
                  </div>
                ))}
              </div>
            )}
          </section>
        ) : null}

        <section id="sec-midia" className="scroll-mt-24">
          <ModeracaoV2MediaGallery
            anuncioId={anuncioId}
            midiasPublicadas={anuncio.midias ?? []}
            fotosPublicadas={anuncio.fotosUrl ?? []}
            videosPublicados={anuncio.videosUrl ?? []}
            revision={revision}
            removedLogical={removedLogical}
            canModerate={isStaffModeration}
            onReload={load}
          />
        </section>

        {isAdmin && !removedLogical && !revision && !anuncio.pendingRevision ? (
          <FotosAnuncioSection anuncioId={anuncioId} fotos={anuncio.fotosUrl ?? []} onUpdated={() => void load()} />
        ) : null}

        {isStaffModeration && (
          <section id="sec-stories" className="scroll-mt-24">
            <AdminAnuncioStoriesSection anuncioId={anuncioId} apiScope="staff" />
          </section>
        )}

        <section id="sec-resumo" className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm scroll-mt-24">
          <h2 className="text-base font-semibold text-gray-900">Resumo do anúncio</h2>
          <div className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
            <p>
              <span className="font-semibold text-gray-700">Nome:</span> {texto(anuncio.nomeCompleto)}
            </p>
            <p>
              <span className="font-semibold text-gray-700">CPF:</span> {anuncio.cpf ? formatCPF(anuncio.cpf) : '—'}
            </p>
            <p>
              <span className="font-semibold text-gray-700">Categoria:</span>{' '}
              {normalizarCategoria(texto(anuncio.categoria, '')) || '—'}
            </p>
            {removedLogical && (
              <p className="sm:col-span-2 text-sm text-rose-700">
                <span className="font-semibold">Remoção lógica:</span> {texto(anuncio.removidoLogicamenteMotivo)}{' '}
                {anuncio.removidoLogicamenteEm ? `· ${texto(anuncio.removidoLogicamenteEm)}` : ''}
              </p>
            )}
            <p className="sm:col-span-2">
              <span className="font-semibold text-gray-700">Local (texto):</span> {localTexto}
            </p>
            <p className="sm:col-span-2 whitespace-pre-line">
              <span className="font-semibold text-gray-700">Descrição:</span> {texto(anuncio.descricao)}
            </p>
          </div>
        </section>

        {isAdmin && !removedLogical && (
          <section id="sec-edicao" className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm scroll-mt-24">
            <h2 className="text-base font-semibold text-gray-900">Edição do anúncio (administrador)</h2>
            <p className="mt-1 text-sm text-gray-600">
              Alterações diretas no anúncio (multipart). Salve sem sair desta página. Moderadores não têm acesso a este
              bloco.
            </p>
            {!staffEditFormExpanded ? (
              <div className="mt-4">
                <Button type="button" variant="secondary" onClick={() => setStaffEditFormExpanded(true)}>
                  Abrir editor do anúncio
                </Button>
                <p className="mt-2 text-xs text-gray-500">
                  O formulário completo só é carregado após abrir — melhora o desempenho inicial da página.
                </p>
              </div>
            ) : (
              <div className="mt-4">
                <AnuncioStaffEditForm anuncioId={anuncioId} embedded onSaved={() => void load()} />
              </div>
            )}
          </section>
        )}

        {isAdmin && (
          <section id="sec-premium" className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm scroll-mt-24">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <p className="text-[11px] font-semibold uppercase tracking-wide text-gray-500">PREMIUM</p>
                <h2 className="mt-1 text-base font-semibold text-gray-900">Benefícios premium (créditos)</h2>
              </div>
              <Badge variant="outline" className="w-fit text-[11px]">
                {activePremiumCount} ativo(s) / agendado(s)
              </Badge>
            </div>
            <p className="mt-1 text-sm text-gray-600">
              Visualização e ativação manual via <code className="text-xs">/admin/premium-benefits</code>. Logs em
              auditoria admin.
            </p>
            {premiumError ? (
              <div className="mt-3">
                <ContractState
                  error={premiumError}
                  onRetry={() => setPremiumReload((value) => value + 1)}
                  compact
                />
              </div>
            ) : !premiumDetail ? (
              <p className="mt-3 text-sm text-gray-500">Carregando benefícios…</p>
            ) : (
              <div className="mt-4 space-y-4 text-sm">
                <div>
                  <p className="font-semibold text-gray-800">Ativos e agendados</p>
                  {beneficiosAtivos.length === 0 ? (
                    <p className="text-gray-500">Nenhum benefício ativo.</p>
                  ) : (
                    <ul className="mt-2 space-y-2">
                      {beneficiosAtivos.map((b, i) => {
                        const cod = texto(b.codigo, '—')
                        const statusLabel = rotuloStatusPremium(b)
                        const pode = Boolean(b.podeDesativar)
                        const idAtiv = b.id
                        return (
                          <li
                            key={`${cod}-${idAtiv ?? i}`}
                            className="flex flex-col gap-2 rounded-lg border border-gray-100 bg-gray-50/80 px-3 py-2.5 sm:flex-row sm:items-center sm:justify-between"
                          >
                            <div className="min-w-0">
                              <p className="text-sm font-medium text-gray-900">{texto(b.nome, cod)}</p>
                              <p className="font-mono text-[10px] text-gray-500">{cod}</p>
                              <div className="mt-1 flex flex-wrap gap-2">
                                <Badge variant="outline" className="text-[10px]">
                                  {statusLabel}
                                </Badge>
                                {b.dataInicio ? (
                                  <Badge variant="outline" className="text-[10px] font-normal">
                                    Início {formatShortDateTime(b.dataInicio)}
                                  </Badge>
                                ) : null}
                                {b.dataFim ? (
                                  <Badge variant="outline" className="text-[10px] font-normal">
                                    Expira {formatShortDateTime(b.dataFim)}
                                  </Badge>
                                ) : null}
                                {formatPremiumDuration(b.dataInicio, b.dataFim) ? (
                                  <Badge variant="outline" className="text-[10px] font-normal">
                                    Duração {formatPremiumDuration(b.dataInicio, b.dataFim)}
                                  </Badge>
                                ) : null}
                                {b.origem ? (
                                  <Badge variant="secondary" className="text-[10px] font-normal">
                                    {b.origem === 'MANUAL_ADMIN' ? 'Manual (admin)' : texto(b.origem)}
                                  </Badge>
                                ) : null}
                              </div>
                              {!pode ? (
                                <p className="mt-1 text-[10px] text-amber-800">
                                  Revogação manual indisponível (ex.: compra por créditos). Aguardar expiração ou política do
                                  produto.
                                </p>
                              ) : null}
                            </div>
                            {pode && idAtiv != null ? (
                              <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                className="shrink-0 border-rose-200 text-rose-800 hover:bg-rose-50"
                                disabled={busy}
                                onClick={() => void handleDesativarPremium(idAtiv, b)}
                              >
                                Remover / desativar
                              </Button>
                            ) : null}
                          </li>
                        )
                      })}
                    </ul>
                  )}
                </div>
                <div>
                  <p className="font-semibold text-gray-800">Expirados</p>
                  {beneficiosExpirados.length === 0 ? (
                    <p className="text-gray-500">Nenhum registro expirado listado.</p>
                  ) : (
                    <ul className="mt-1 list-inside list-disc text-gray-600">
                      {beneficiosExpirados.map((b, i) => (
                        <li key={i}>{texto(b.nome, b.codigo ?? '—')}</li>
                      ))}
                    </ul>
                  )}
                </div>
                <div>
                  <p className="font-semibold text-gray-800">Histórico de ativações / revogações</p>
                  {historicoPremium.length === 0 ? (
                    <p className="text-gray-500">—</p>
                  ) : (
                    <ul className="mt-1 max-h-40 space-y-1 overflow-y-auto text-xs text-gray-700">
                      {historicoPremium.map((h, i) => (
                        <li key={i} className="rounded border border-gray-100 bg-gray-50 px-2 py-1">
                          {texto(h.acao)} · {texto(h.codigo)} · {texto(h.actorEmail)}{' '}
                          {h.dataHora ? `· ${h.dataHora}` : ''}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
                {removedLogical ? null : (
                  <div className="rounded-xl border border-dashed border-gray-200 p-4">
                    <p className="font-medium text-gray-800">Ativar benefício comprado</p>
                    <p className="mt-1 text-xs text-gray-500">
                      Selecione um ou mais benefícios. Itens já ativos ou agendados ficam bloqueados.
                    </p>
                    <div className="mt-3 space-y-2">
                      {PREMIUM_ACTIVATABLE_OPTIONS.map((opt) => {
                        const codigo = opt.codigo
                        const bloqueado = premiumJaAtivoOuAgendado.has(codigo)
                        const info = premiumJaAtivoOuAgendado.get(codigo)
                        const ativoRow = beneficioAtivoPorCodigo.get(codigo)
                        const podeRevogar =
                          Boolean(ativoRow?.podeDesativar) &&
                          ativoRow?.id != null
                        const checkboxDesabilitado = busy || (bloqueado && !podeRevogar)
                        const marcado = bloqueado || premiumSelected.has(codigo)
                        const id = `premium-opt-${codigo}`
                        return (
                          <label
                            key={codigo}
                            htmlFor={id}
                            title={
                              bloqueado && !podeRevogar
                                ? 'Benefício ativo. Revogação pelo checklist não está disponível para este tipo de concessão — veja a lista “Ativos e agendados” acima.'
                                : undefined
                            }
                            className={[
                              'flex items-start gap-3 rounded-lg border px-3 py-2.5 transition-colors select-none',
                              bloqueado
                                ? 'border-emerald-200 bg-emerald-50/70'
                                : 'border-gray-200 bg-white hover:border-gray-300',
                              checkboxDesabilitado ? 'cursor-not-allowed' : 'cursor-pointer',
                            ].join(' ')}
                          >
                            <Checkbox
                              id={id}
                              className="mt-0.5"
                              checked={marcado}
                              disabled={checkboxDesabilitado}
                              onCheckedChange={(v) => {
                                if (busy) return
                                const on = v === true
                                if (bloqueado) {
                                  if (!on && podeRevogar && ativoRow?.id != null) {
                                    void handleDesativarPremium(ativoRow.id, ativoRow)
                                  }
                                  return
                                }
                                setPremiumSelected((prev) => {
                                  const next = new Set(prev)
                                  if (on) next.add(codigo)
                                  else next.delete(codigo)
                                  return next
                                })
                              }}
                            />
                            <span className="min-w-0 flex-1 leading-snug">
                              <span className="text-sm font-medium text-gray-900">{opt.label}</span>
                              <span className="mt-0.5 block font-mono text-[10px] text-gray-500">{codigo}</span>
                              {bloqueado && !podeRevogar ? (
                                <span className="mt-1 block text-[11px] leading-tight text-amber-900/90">
                                  Revogação indisponível neste checklist (concessão não revogável aqui).
                                </span>
                              ) : null}
                            </span>
                            {bloqueado ? (
                              <Badge
                                variant="outline"
                                className="shrink-0 border-emerald-300 bg-white text-[10px] text-emerald-900"
                              >
                                {info?.label ?? 'Ativo'}
                              </Badge>
                            ) : null}
                          </label>
                        )
                      })}
                    </div>
                    <div className="mt-3 space-y-1">
                      <label className="text-xs font-medium text-gray-600" htmlFor="premium-obs-v2">
                        Observação interna
                      </label>
                      <Input
                        id="premium-obs-v2"
                        value={premiumObs}
                        onChange={(e) => setPremiumObs(e.target.value)}
                        disabled={busy}
                        placeholder="Registro opcional para auditoria…"
                      />
                    </div>
                    <Button
                      type="button"
                      className="mt-3 bg-[#f0198f] text-white hover:bg-[#d9157d]"
                      disabled={busy || premiumSelected.size === 0}
                      onClick={() => void handleAtivarPremiumSelecionados()}
                    >
                      Ativar selecionados
                    </Button>
                  </div>
                )}
              </div>
            )}
          </section>
        )}

        {isAdmin && auditLoaded && anuncio && (
          <section id="sec-auditoria" className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm scroll-mt-24">
            <h2 className="text-base font-semibold text-gray-900">Auditoria e compliance (anúncio)</h2>
            <p className="mt-1 text-sm text-gray-600">
              Trilha consolidada para revisão institucional: mesma origem de dados que{' '}
          <code className="text-xs">admin_audit_logs</code> (filtrado por este anúncio), com eventos de verificação etária e métricas
          agregadas. Moderadores não acessam estas APIs.
            </p>
            {auditError ? (
              <div className="mt-4">
                <ContractState
                  error={auditError}
                  onRetry={() => setAuditReload((value) => value + 1)}
                  compact
                />
              </div>
            ) : !auditTimelineVisible ? (
              <div className="mt-4">
                <Button type="button" variant="secondary" onClick={() => setAuditTimelineVisible(true)}>
                  Ver auditoria
                </Button>
                <p className="mt-2 text-xs text-gray-500">
                  A linha do tempo só é montada após clicar — os dados já foram carregados em segundo plano.
                </p>
              </div>
            ) : (
              <>
                <div className="mt-4">
                  <ModeracaoV2AuditTimeline
                    anuncioId={anuncioId}
                    anuncio={anuncio}
                    auditLogs={auditLogs}
                    visitorEvents={visitorAuditEvents}
                  />
                </div>
                <p className="mt-4 border-t border-gray-100 pt-3 text-[11px] text-gray-500">
                  Lista bruta legada (mesmos registros de auditoria):{' '}
                  {auditLogs.length === 0 ? (
                    'nenhum.'
                  ) : (
                    <span className="font-mono text-gray-600">{auditLogs.length} linha(s) no filtro ANUNCIO / entityId.</span>
                  )}
                </p>
              </>
            )}
          </section>
        )}
      </div>

      <Dialog open={rejectOpen} onOpenChange={setRejectOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reprovar</DialogTitle>
            <DialogDescription>
              {useRejeitarEndpoint
                ? 'Usa `/anuncios/{id}/rejeitar` (pendente ou com revisão aberta — descarta alterações propostas).'
                : 'A rejeição direta aguarda contrato administrativo V3 próprio.'}
            </DialogDescription>
          </DialogHeader>
          <Textarea
            value={rejectMotivo}
            onChange={(e) => setRejectMotivo(e.target.value)}
            placeholder="Motivo para o anunciante / registro…"
            className="min-h-[120px]"
          />
          <DialogFooter className="gap-2">
            <Button type="button" variant="outline" onClick={() => setRejectOpen(false)} disabled={busy}>
              Cancelar
            </Button>
            <Button type="button" variant="destructive" disabled={busy} onClick={() => void handleRejectSubmit()}>
              Confirmar reprovação
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={removeOpen} onOpenChange={setRemoveOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Remover logicamente</DialogTitle>
            <DialogDescription>
              O anúncio sai da vitrine e é marcado como removido. Não há exclusão física no banco nem remoção em
              cascata de revisões, cliques ou mídias.
            </DialogDescription>
          </DialogHeader>
          <Textarea
            value={removeMotivo}
            onChange={(e) => setRemoveMotivo(e.target.value)}
            placeholder="Motivo obrigatório (auditoria)…"
            className="min-h-[120px]"
          />
          <DialogFooter className="gap-2">
            <Button type="button" variant="outline" onClick={() => setRemoveOpen(false)} disabled={busy}>
              Cancelar
            </Button>
            <Button type="button" variant="destructive" disabled={busy} onClick={() => void handleRemoveLogical()}>
              Confirmar remoção lógica
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={deleteContractOpen} onOpenChange={setDeleteContractOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirmar exclusao</DialogTitle>
            <DialogDescription>
              A exclusao definitiva permanece disponivel para paridade funcional, mas nao sera simulada sem um
              contrato backend V3 correspondente.
            </DialogDescription>
          </DialogHeader>
          <PendingActionFeedback attemptedAction={deleteContract.attemptedAction} />
          <DialogFooter className="gap-2">
            <Button type="button" variant="outline" onClick={() => setDeleteContractOpen(false)}>
              Cancelar
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={() => deleteContract.runPendingAction('Excluir anuncio')}
            >
              Confirmar exclusao
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
