'use client'

import Link from 'next/link'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  ArrowLeft,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock3,
  ExternalLink,
  FileWarning,
  Image as ImageIcon,
  LockKeyhole,
  Loader2,
  Pencil,
  RotateCcw,
  ShieldAlert,
  Trash2,
  Unlock,
  Video,
  XCircle,
} from 'lucide-react'

import { revalidarCacheCatalogoPublico } from '@/app/(painel-admin)/admin/anuncios/actions'
import { ContractState } from '@/components/feedback/contract-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import { getAdminSession } from '@/lib/admin-auth-api'
import { ApiContractError, normalizeApiError } from '@/lib/api-contract'

import { AdminAnuncioDocumentos } from './admin-anuncio-documentos'
import { AdminAnuncioPremium } from './admin-anuncio-premium'
import { AdminAnuncioStory } from './admin-anuncio-story'
import {
  blockAdminAd,
  blockAdminAdAndUser,
  decideAdminMedia,
  decideAdminPhotosBatch,
  decideAdminReview,
  getAdminAd,
  getAdminAdQueueNavigation,
  getAdminMediaPreview,
  listAdminAdHistory,
  listAdminAdMedia,
  reactivateAdminAd,
  reclassifyAdminMedia,
  removeAdminAd,
  submitAdminReview,
  unblockAdminAd,
  unblockAdminUser,
} from './api'
import { adminAdQueueDetailHref, adminAdQueueListHref, parseAdminAdQueueContext } from './queue-context'
import type {
  AdminAdDetail,
  AdminAdQueueNavigation,
  AdminLegalBlockCategory,
  AdminMediaItem,
  AdminMediaPreview,
  AdminModerationHistoryItem,
  AdminPhotoBatchResponse,
} from './types'

type DecisionIntent =
  | { kind: 'OPEN_REVIEW'; title: string; requiresReason: true }
  | { kind: 'APPROVE_AD'; title: string; requiresReason: false }
  | { kind: 'REPROVE_AD'; title: string; requiresReason: true }
  | { kind: 'REVIEW'; title: string; action: 'APROVAR' | 'REPROVAR' | 'SOLICITAR_AJUSTE'; requiresReason: boolean }
  | { kind: 'MEDIA'; title: string; media: AdminMediaItem; action: 'APROVAR' | 'REPROVAR'; visibility?: 'LIVRE' | 'RESTRITA_18'; requiresReason: boolean }
  | { kind: 'RECLASSIFY'; title: string; media: AdminMediaItem; visibility: 'LIVRE' | 'RESTRITA_18'; requiresReason: true }

type LegalIntent =
  | { kind: 'REACTIVATE'; title: string }
  | { kind: 'BLOCK_AD'; title: string }
  | { kind: 'BLOCK_USER'; title: string }
  | { kind: 'UNBLOCK_AD'; title: string }
  | { kind: 'UNBLOCK_USER'; title: string }

type PhotoDecision = {
  decisao: 'APROVAR' | 'EXCLUIR'
  classificacao?: 'LIVRE' | 'RESTRITA_18'
  observacao: string
}

const LEGAL_CATEGORIES: Array<{ value: AdminLegalBlockCategory; label: string }> = [
  { value: 'DENUNCIA_GRAVE', label: 'Denúncia grave' },
  { value: 'USO_NAO_AUTORIZADO_IMAGEM', label: 'Uso não autorizado de imagem' },
  { value: 'FRAUDE', label: 'Fraude' },
  { value: 'ORDEM_OU_RISCO_JURIDICO', label: 'Ordem ou risco jurídico' },
  { value: 'OUTRA_INTERVENCAO', label: 'Outra intervenção excepcional' },
]

const AUTOMATIC_REVIEW_REASON = 'Revisão aberta automaticamente para aprovação administrativa.'
const AUTOMATIC_REPROVAL_REVIEW_REASON = 'Revisão aberta automaticamente para reprovação administrativa.'

function formatDate(value?: string | null) {
  if (!value) return 'Não informado'
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function formatPrice(value?: number | null) {
  if (value == null) return 'Não informado'
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value)
}

function formatEnum(value?: string | null) {
  if (!value) return 'Não informado'
  return value.toLowerCase().replaceAll('_', ' ').replace(/(^|\s)\S/g, (letter) => letter.toUpperCase())
}

function moderationTone(value?: string | null) {
  if (value === 'APROVADO' || value === 'PUBLICAVEL') return 'border-emerald-200 bg-emerald-50 text-emerald-800'
  if (value === 'REJEITADO' || value === 'REJEITADA' || value === 'BLOQUEADO') return 'border-red-200 bg-red-50 text-red-800'
  return 'border-amber-200 bg-amber-50 text-amber-800'
}

function viewsLabel(ad: AdminAdDetail) {
  const views = ad.metricas.visualizacoes
  return views.situacao === 'HISTORICO_PENDENTE' ? '—' : String(views.total ?? '—')
}

function MediaPreview({ media }: { media: AdminMediaItem }) {
  const [preview, setPreview] = useState<AdminMediaPreview | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    setError(null)
    getAdminMediaPreview(media.id)
      .then((value) => { if (active) setPreview(value) })
      .catch((reason) => { if (active) setError(reason) })
    return () => { active = false }
  }, [media.id, reload])

  if (error) return <div className="flex aspect-video items-center justify-center bg-zinc-100 p-4 text-center"><div><FileWarning className="mx-auto h-6 w-6 text-zinc-500" /><p className="mt-2 text-xs text-zinc-600">Prévia indisponível.</p><Button type="button" size="sm" variant="ghost" onClick={() => setReload((value) => value + 1)}>Tentar novamente</Button></div></div>
  if (!preview) return <div className="flex aspect-video items-center justify-center bg-zinc-100"><Loader2 className="h-5 w-5 animate-spin text-zinc-500" aria-label="Carregando prévia" /></div>
  if (media.tipo === 'VIDEO') return <video src={preview.url} controls preload="metadata" className="aspect-video w-full bg-black object-contain" />
  // A URL temporaria protegida nao deve passar pelo cache compartilhado do Next Image.
  // eslint-disable-next-line @next/next/no-img-element
  return <img src={preview.url} alt="Mídia em análise" className="aspect-video w-full bg-zinc-100 object-contain" loading="lazy" />
}

function DecisionDialog({ intent, busy, error, onClose, onConfirm }: {
  intent: DecisionIntent | null
  busy: boolean
  error: unknown
  onClose: () => void
  onConfirm: (reason: string) => void
}) {
  const [reason, setReason] = useState('')
  const normalizedError = error ? normalizeApiError(error) : null
  const isAdReproval = intent?.kind === 'REPROVE_AD'
  const reasonMaxLength = isAdReproval ? 2000 : 240
  const acceptsObservation = intent?.kind === 'MEDIA'
    && intent.media.tipo === 'FOTO'
    && intent.action === 'APROVAR'
    && intent.visibility === 'RESTRITA_18'
  useEffect(() => { setReason('') }, [intent])
  return (
    <Dialog open={Boolean(intent)} onOpenChange={(open) => { if (!open && !busy) onClose() }}>
      <DialogContent className="rounded-md">
        <DialogHeader>
          <DialogTitle>{intent?.title}</DialogTitle>
          <DialogDescription>
            {isAdReproval
              ? 'O anúncio ficará indisponível e o anunciante receberá um e-mail com o motivo e as alterações necessárias.'
              : 'A decisão será registrada com ator, data UTC e identificador da requisição.'}
          </DialogDescription>
        </DialogHeader>
        {intent?.requiresReason || acceptsObservation ? (
          <label>
            <span className="mb-2 block text-sm font-semibold text-zinc-800">
              {isAdReproval
                ? 'Motivo e alterações necessárias'
                : intent?.requiresReason
                  ? 'Motivo obrigatório'
                  : 'Observações'}
            </span>
            <Textarea
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              maxLength={reasonMaxLength}
              rows={4}
              disabled={busy}
            />
            <span className="mt-1 block text-right text-xs text-zinc-500">{reason.length}/{reasonMaxLength}</span>
          </label>
        ) : null}
        {normalizedError ? (
          <div role="alert" className="border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
            <p className="font-semibold">
              {normalizedError.kind === 'CONFLICT'
                ? intent?.kind === 'MEDIA' || intent?.kind === 'RECLASSIFY'
                  ? 'Estado da mídia atualizado'
                  : 'Estado do anúncio atualizado'
                : 'Não foi possível concluir'}
            </p>
            <p className="mt-1 text-amber-800">{normalizedError.message}</p>
          </div>
        ) : null}
        <DialogFooter><Button type="button" variant="outline" onClick={onClose} disabled={busy}>Cancelar</Button><Button type="button" variant={isAdReproval ? 'destructive' : 'default'} onClick={() => onConfirm(reason)} disabled={busy || Boolean(intent?.requiresReason && !reason.trim())}>{busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}{isAdReproval ? 'Confirmar reprovação' : 'Confirmar'}</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function LegalActionDialog({ intent, busy, error, onClose, onConfirm }: {
  intent: LegalIntent | null
  busy: boolean
  error: unknown
  onClose: () => void
  onConfirm: (category: AdminLegalBlockCategory | null, reason: string, internalNote: string) => void
}) {
  const [category, setCategory] = useState<AdminLegalBlockCategory | ''>('')
  const [reason, setReason] = useState('')
  const [internalNote, setInternalNote] = useState('')
  const isBlock = intent?.kind === 'BLOCK_AD' || intent?.kind === 'BLOCK_USER'
  const isUnlock = intent?.kind === 'UNBLOCK_AD' || intent?.kind === 'UNBLOCK_USER'

  useEffect(() => {
    setCategory('')
    setReason('')
    setInternalNote('')
  }, [intent])

  return (
    <Dialog open={Boolean(intent)} onOpenChange={(open) => { if (!open && !busy) onClose() }}>
      <DialogContent className="rounded-md">
        <DialogHeader>
          <DialogTitle>{intent?.title}</DialogTitle>
          <DialogDescription>
            {isBlock
              ? 'A intervenção retira o anúncio do catálogo sem excluir dados, mídias ou histórico.'
              : intent?.kind === 'REACTIVATE'
                ? 'O anúncio será publicado somente após a confirmação do backend.'
                : 'O desbloqueio não republica anúncios nem reativa Stories ou benefícios expirados.'}
          </DialogDescription>
        </DialogHeader>
        {isBlock ? (
          <div className="space-y-4">
            <label className="block">
              <span className="mb-2 block text-sm font-semibold text-zinc-800">Categoria obrigatória</span>
              <Select value={category} onValueChange={(value) => setCategory(value as AdminLegalBlockCategory)} disabled={busy}>
                <SelectTrigger className="w-full bg-white"><SelectValue placeholder="Selecione a categoria" /></SelectTrigger>
                <SelectContent>{LEGAL_CATEGORIES.map((item) => <SelectItem key={item.value} value={item.value}>{item.label}</SelectItem>)}</SelectContent>
              </Select>
            </label>
            <label className="block">
              <span className="mb-2 block text-sm font-semibold text-zinc-800">Motivo obrigatório</span>
              <Textarea value={reason} onChange={(event) => setReason(event.target.value)} minLength={5} maxLength={1000} rows={4} disabled={busy} />
              <span className="mt-1 block text-right text-xs text-zinc-500">{reason.length}/1000</span>
            </label>
            <label className="block">
              <span className="mb-2 block text-sm font-semibold text-zinc-800">Observação interna opcional</span>
              <Textarea value={internalNote} onChange={(event) => setInternalNote(event.target.value)} maxLength={2000} rows={3} disabled={busy} />
              <span className="mt-1 block text-right text-xs text-zinc-500">{internalNote.length}/2000</span>
            </label>
          </div>
        ) : isUnlock ? (
          <label className="block">
            <span className="mb-2 block text-sm font-semibold text-zinc-800">Motivo opcional</span>
            <Textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={1000} rows={3} disabled={busy} />
            <span className="mt-1 block text-right text-xs text-zinc-500">{reason.length}/1000</span>
          </label>
        ) : null}
        {error ? <ContractState error={error} compact /> : null}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Cancelar</Button>
          <Button
            type="button"
            variant={isBlock ? 'destructive' : 'default'}
            onClick={() => onConfirm(category || null, reason, internalNote)}
            disabled={busy || Boolean(isBlock && (!category || reason.trim().length < 5))}
          >
            {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Confirmar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function RemovalDialog({ open, busy, error, onClose, onConfirm }: {
  open: boolean
  busy: boolean
  error: unknown
  onClose: () => void
  onConfirm: (reason: string) => void
}) {
  const [reason, setReason] = useState('')

  useEffect(() => {
    setReason('')
  }, [open])

  return (
    <Dialog open={open} onOpenChange={(nextOpen) => { if (!nextOpen && !busy) onClose() }}>
      <DialogContent className="rounded-md">
        <DialogHeader>
          <DialogTitle>{'Excluir an\u00fancio'}</DialogTitle>
          <DialogDescription>
            {'Esta a\u00e7\u00e3o remover\u00e1 o an\u00fancio da plataforma e excluir\u00e1 definitivamente suas fotos e v\u00eddeos. O hist\u00f3rico administrativo ser\u00e1 preservado.'}
          </DialogDescription>
        </DialogHeader>
        <label className="block">
          <span className="mb-2 block text-sm font-semibold text-zinc-800">
            {'Motivo obrigat\u00f3rio'}
          </span>
          <Textarea
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            minLength={5}
            maxLength={1000}
            rows={4}
            disabled={busy}
          />
          <span className="mt-1 block text-right text-xs text-zinc-500">{reason.length}/1000</span>
        </label>
        {error ? <ContractState error={error} compact /> : null}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Cancelar</Button>
          <Button
            type="button"
            variant="destructive"
            className="border border-red-950 bg-red-700 text-white hover:bg-red-800"
            onClick={() => onConfirm(reason)}
            disabled={busy || reason.trim().length < 5}
          >
            {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Trash2 className="mr-2 h-4 w-4" />}
            {'Excluir an\u00fancio'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function MediaVisibilitySelector({
  mediaId,
  value,
  disabled,
  onChange,
}: {
  mediaId: string
  value?: 'LIVRE' | 'RESTRITA_18'
  disabled: boolean
  onChange: (value: 'LIVRE' | 'RESTRITA_18') => void
}) {
  return (
    <fieldset className="mt-4" disabled={disabled}>
      <legend className="text-xs font-semibold text-zinc-700">Classificação individual</legend>
      <div className="mt-2 grid grid-cols-2 gap-2">
        {(['LIVRE', 'RESTRITA_18'] as const).map((option) => {
          const id = `visibility-${mediaId}-${option}`
          const checked = value === option
          return (
            <label key={option} htmlFor={id} className={`flex min-h-10 cursor-pointer items-center gap-2 border px-3 py-2 text-sm font-medium ${checked ? 'border-pink-500 bg-pink-50 text-pink-900' : 'border-zinc-200 bg-white text-zinc-700'} ${disabled ? 'cursor-not-allowed opacity-60' : ''}`}>
              <input id={id} type="radio" name={`visibility-${mediaId}`} checked={checked} onChange={() => onChange(option)} className="h-4 w-4 accent-pink-600" />
              {option}
            </label>
          )
        })}
      </div>
    </fieldset>
  )
}

function PendingPhotoDecisionSelector({
  mediaId,
  value,
  disabled,
  onChange,
  onObservationChange,
}: {
  mediaId: string
  value?: PhotoDecision
  disabled: boolean
  onChange: (value: 'LIVRE' | 'RESTRITA_18' | 'EXCLUIR') => void
  onObservationChange: (value: string) => void
}) {
  const selected = value?.decisao === 'EXCLUIR' ? 'EXCLUIR' : value?.classificacao
  const options = [
    { value: 'LIVRE', label: 'LIVRE', destructive: false },
    { value: 'RESTRITA_18', label: 'RESTRITA_18', destructive: false },
    { value: 'EXCLUIR', label: 'EXCLUIR FOTO', destructive: true },
  ] as const

  return (
    <fieldset className="mt-4" disabled={disabled}>
      <legend className="text-xs font-semibold text-zinc-700">Decisão individual</legend>
      <div className="mt-2 grid gap-2">
        {options.map((option) => {
          const id = `photo-decision-${mediaId}-${option.value}`
          const checked = selected === option.value
          const selectedClass = option.destructive
            ? 'border-red-600 bg-red-50 text-red-900'
            : 'border-pink-500 bg-pink-50 text-pink-900'
          return (
            <label
              key={option.value}
              htmlFor={id}
              className={`flex min-h-10 cursor-pointer items-center gap-2 border px-3 py-2 text-sm font-medium ${checked ? selectedClass : 'border-zinc-200 bg-white text-zinc-700'} ${disabled ? 'cursor-not-allowed opacity-60' : ''}`}
            >
              <input
                id={id}
                type="radio"
                name={`photo-decision-${mediaId}`}
                checked={checked}
                onChange={() => onChange(option.value)}
                className={`h-4 w-4 ${option.destructive ? 'accent-red-700' : 'accent-pink-600'}`}
              />
              {option.destructive ? <Trash2 className="h-4 w-4" /> : null}
              {option.label}
            </label>
          )
        })}
      </div>
      {selected === 'RESTRITA_18' ? (
        <label className="mt-3 block">
          <span className="mb-2 block text-xs font-semibold text-zinc-700">Observações</span>
          <Textarea
            value={value?.observacao ?? ''}
            onChange={(event) => onObservationChange(event.target.value)}
            maxLength={240}
            rows={3}
            disabled={disabled}
            placeholder="Observação individual desta foto"
          />
        </label>
      ) : null}
      {selected === 'EXCLUIR' ? (
        <p className="mt-3 border border-red-200 bg-red-50 px-3 py-2 text-xs font-medium text-red-900">
          A foto e seus objetos exclusivos serão excluídos somente após a confirmação do lote.
        </p>
      ) : null}
    </fieldset>
  )
}

function PhotoBatchDialog({
  open,
  busy,
  error,
  livre,
  restrita,
  excluir,
  onClose,
  onConfirm,
}: {
  open: boolean
  busy: boolean
  error: unknown
  livre: number
  restrita: number
  excluir: number
  onClose: () => void
  onConfirm: () => void
}) {
  const normalizedError = error ? normalizeApiError(error) : null
  return (
    <Dialog open={open} onOpenChange={(next) => { if (!next && !busy) onClose() }}>
      <DialogContent className="rounded-md">
        <DialogHeader>
          <DialogTitle>Confirmar decisões das fotos</DialogTitle>
          <DialogDescription>
            O lote será registrado com resultado individual por foto, ator, data UTC e requestId.
          </DialogDescription>
        </DialogHeader>
        <ul className="space-y-2 border-y border-zinc-200 py-4 text-sm text-zinc-800">
          <li><strong>{livre}</strong> {livre === 1 ? 'foto LIVRE' : 'fotos LIVRE'}</li>
          <li><strong>{restrita}</strong> {restrita === 1 ? 'foto RESTRITA_18' : 'fotos RESTRITA_18'}</li>
          <li className={excluir > 0 ? 'font-semibold text-red-800' : ''}>
            <strong>{excluir}</strong> {excluir === 1 ? 'foto será excluída definitivamente' : 'fotos serão excluídas definitivamente'}
          </li>
        </ul>
        {normalizedError ? (
          <div role="alert" className="border border-red-200 bg-red-50 p-3 text-sm text-red-900">
            <p className="font-semibold">Não foi possível concluir o lote</p>
            <p className="mt-1">{normalizedError.message}</p>
          </div>
        ) : null}
        <DialogFooter>
          <Button type="button" variant="outline" disabled={busy} onClick={onClose}>Cancelar</Button>
          <Button type="button" disabled={busy} onClick={onConfirm}>
            {busy ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" />Processando...</> : 'Confirmar decisões'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export function AdminAnuncioModeracao({ anuncioId, initialQuery = '' }: { anuncioId: string; initialQuery?: string }) {
  const queueParams = useMemo(() => new URLSearchParams(initialQuery), [initialQuery])
  const hasQueueContext = queueParams.get('fila') === '1'
  const queueContext = useMemo(() => parseAdminAdQueueContext(queueParams), [queueParams])
  const [ad, setAd] = useState<AdminAdDetail | null>(null)
  const [media, setMedia] = useState<AdminMediaItem[]>([])
  const [history, setHistory] = useState<AdminModerationHistoryItem[]>([])
  const [permissions, setPermissions] = useState<string[]>([])
  const [roles, setRoles] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [intent, setIntent] = useState<DecisionIntent | null>(null)
  const [actionError, setActionError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const decisionLock = useRef(false)
  const [legalIntent, setLegalIntent] = useState<LegalIntent | null>(null)
  const [legalActionError, setLegalActionError] = useState<unknown>(null)
  const [legalBusy, setLegalBusy] = useState(false)
  const [removalOpen, setRemovalOpen] = useState(false)
  const [removalActionError, setRemovalActionError] = useState<unknown>(null)
  const [removalBusy, setRemovalBusy] = useState(false)
  const [reload, setReload] = useState(0)
  const [visibility, setVisibility] = useState<Record<string, 'LIVRE' | 'RESTRITA_18'>>({})
  const [photoDecisions, setPhotoDecisions] = useState<Record<string, PhotoDecision>>({})
  const [photoBatchOpen, setPhotoBatchOpen] = useState(false)
  const [photoBatchBusy, setPhotoBatchBusy] = useState(false)
  const [photoBatchError, setPhotoBatchError] = useState<unknown>(null)
  const [photoBatchResult, setPhotoBatchResult] = useState<AdminPhotoBatchResponse | null>(null)
  const photoBatchLock = useRef(false)
  const [navigation, setNavigation] = useState<AdminAdQueueNavigation | null>(null)
  const [navigationError, setNavigationError] = useState<unknown>(null)
  const [decisionOutcome, setDecisionOutcome] = useState<'APPROVED' | 'REPROVED' | 'OTHER' | null>(null)

  const load = useCallback(async (preserveSelection?: {
    mediaId: string
    value: 'LIVRE' | 'RESTRITA_18'
    mode: 'DECISION' | 'RECLASSIFY'
  }) => {
    setLoading(true)
    setError(null)
    try {
      const [adResponse, session] = await Promise.all([getAdminAd(anuncioId), getAdminSession()])
      const sessionPermissions = session?.permissoes ?? []
      const canReadMedia = sessionPermissions.includes('MIDIA_REVISAR')
      const canReadHistory = canReadMedia || sessionPermissions.includes('ANUNCIO_MODERAR')
      const [mediaResponse, historyResponse] = await Promise.all([
        canReadMedia ? listAdminAdMedia(anuncioId) : Promise.resolve(null),
        canReadHistory ? listAdminAdHistory(anuncioId) : Promise.resolve([]),
      ])
      setAd(adResponse)
      const visibleMedia = mediaResponse?.itens.filter((item) => String(item.tipo) !== 'STORY') ?? []
      setMedia(visibleMedia)
      setPhotoDecisions((current) => Object.fromEntries(
        visibleMedia
          .filter((item) => item.tipo === 'FOTO' && ['PENDENTE', 'AJUSTE_SOLICITADO'].includes(item.status))
          .filter((item) => Boolean(current[item.id]))
          .map((item) => [item.id, current[item.id]]),
      ))
      setHistory(historyResponse)
      setPermissions(sessionPermissions)
      setRoles(session?.papeis ?? [])
      setVisibility(() => {
        const next: Record<string, 'LIVRE' | 'RESTRITA_18'> = {}
        mediaResponse?.itens.forEach((item) => { if (item.tipo === 'FOTO' && item.visibilidadeMidia) next[item.id] = item.visibilidadeMidia })
        if (preserveSelection) {
          const refreshed = mediaResponse?.itens.find((item) => item.id === preserveSelection.mediaId)
          const selectionStillApplies = preserveSelection.mode === 'DECISION'
            ? refreshed && ['PENDENTE', 'AJUSTE_SOLICITADO'].includes(refreshed.status)
            : refreshed?.status === 'PUBLICAVEL'
          if (selectionStillApplies && refreshed?.tipo === 'FOTO') {
            next[preserveSelection.mediaId] = preserveSelection.value
          }
        }
        return next
      })
    } catch (reason) {
      setError(reason)
    } finally {
      setLoading(false)
    }
  }, [anuncioId])

  useEffect(() => { void load() }, [load, reload])

  useEffect(() => {
    setDecisionOutcome(null)
  }, [anuncioId])

  useEffect(() => {
    if (!hasQueueContext) return
    let active = true
    setNavigationError(null)
    getAdminAdQueueNavigation(anuncioId, queueContext)
      .then((result) => { if (active) setNavigation(result) })
      .catch((reason) => { if (active) setNavigationError(reason) })
    return () => { active = false }
  }, [anuncioId, hasQueueContext, queueContext])

  const isAdmin = roles.includes('ADMIN')
  const canModerateAd = permissions.includes('ANUNCIO_MODERAR')
  const canModerateMedia = permissions.includes('MIDIA_REVISAR')
  const canReadDocuments = permissions.includes('DOCUMENTO_REVISAR')
  const canManagePremium = isAdmin && permissions.includes('PREMIUM_GERENCIAR')
  const canManageLegalStatus = isAdmin && canModerateAd
  const canReclassifyMedia = isAdmin && canModerateMedia
  const canReadHistory = canModerateAd || canModerateMedia
  const actionableMedia = useMemo(() => new Set(['PENDENTE', 'AJUSTE_SOLICITADO']), [])
  const mediaOrdinal = useMemo(() => {
    const counters = new Map<string, number>()
    return Object.fromEntries(media.map((item) => {
      const next = (counters.get(item.tipo) ?? 0) + 1
      counters.set(item.tipo, next)
      return [item.id, next]
    }))
  }, [media])
  const pendingPhotos = useMemo(
    () => media.filter((item) => item.tipo === 'FOTO' && actionableMedia.has(item.status)),
    [actionableMedia, media],
  )
  const selectedPhotoCount = pendingPhotos.filter((item) => Boolean(photoDecisions[item.id])).length
  const allPendingPhotosSelected = pendingPhotos.length > 0
    && selectedPhotoCount === pendingPhotos.length
  const photoBatchSummary = pendingPhotos.reduce((summary, item) => {
    const decision = photoDecisions[item.id]
    if (decision?.decisao === 'EXCLUIR') summary.excluir += 1
    else if (decision?.classificacao === 'LIVRE') summary.livre += 1
    else if (decision?.classificacao === 'RESTRITA_18') summary.restrita += 1
    return summary
  }, { livre: 0, restrita: 0, excluir: 0 })

  function selectPhotoDecision(
    mediaId: string,
    choice: 'LIVRE' | 'RESTRITA_18' | 'EXCLUIR',
  ) {
    setPhotoDecisions((current) => {
      const previous = current[mediaId]
      return {
        ...current,
        [mediaId]: choice === 'EXCLUIR'
          ? { decisao: 'EXCLUIR', observacao: '' }
          : {
            decisao: 'APROVAR',
            classificacao: choice,
            observacao: choice === 'RESTRITA_18' ? previous?.observacao ?? '' : '',
          },
      }
    })
    setPhotoBatchResult(null)
  }

  function updatePhotoObservation(mediaId: string, observacao: string) {
    setPhotoDecisions((current) => {
      const decision = current[mediaId]
      if (!decision || decision.classificacao !== 'RESTRITA_18') return current
      return { ...current, [mediaId]: { ...decision, observacao } }
    })
  }

  async function confirmPhotoBatch() {
    if (
      photoBatchLock.current
      || !ad
      || !allPendingPhotosSelected
    ) return
    photoBatchLock.current = true
    setPhotoBatchBusy(true)
    setPhotoBatchError(null)
    try {
      const response = await decideAdminPhotosBatch(
        ad.id,
        pendingPhotos.map((item) => {
          const decision = photoDecisions[item.id]
          return {
            mediaId: item.id,
            decisao: decision.decisao,
            classificacao: decision.classificacao,
            observacao: decision.classificacao === 'RESTRITA_18'
              ? decision.observacao.trim() || undefined
              : undefined,
          }
        }),
      )
      setPhotoBatchResult(response)
      await load()
      const failedIds = new Set(
        response.resultados
          .filter((item) => item.resultado === 'FALHA')
          .map((item) => item.mediaId),
      )
      setPhotoDecisions((current) => Object.fromEntries(
        Object.entries(current).filter(([mediaId]) => failedIds.has(mediaId)),
      ))
      setPhotoBatchOpen(false)
    } catch (reason) {
      setPhotoBatchError(reason)
    } finally {
      photoBatchLock.current = false
      setPhotoBatchBusy(false)
    }
  }

  async function confirmDecision(reason: string) {
    if (!intent || decisionLock.current || !ad) return
    decisionLock.current = true
    setBusy(true)
    setActionError(null)
    try {
      if (intent.kind === 'OPEN_REVIEW') await submitAdminReview(ad.id, reason)
      else if (intent.kind === 'APPROVE_AD' || intent.kind === 'REPROVE_AD') {
        const action = intent.kind === 'APPROVE_AD' ? 'APROVAR' : 'REPROVAR'
        let reviewId = reviewOpen ? ad.revisaoAberta?.id : null
        if (!reviewId) {
          await submitAdminReview(
            ad.id,
            intent.kind === 'APPROVE_AD'
              ? AUTOMATIC_REVIEW_REASON
              : AUTOMATIC_REPROVAL_REVIEW_REASON,
          )
          const refreshedAd = await getAdminAd(ad.id)
          reviewId = refreshedAd.revisaoAberta?.id
        }
        if (!reviewId) throw new Error('A revisão aberta não foi retornada após o envio para análise.')
        await decideAdminReview(reviewId, action, intent.kind === 'REPROVE_AD' ? reason : undefined)
        await revalidarCacheCatalogoPublico()
        setDecisionOutcome(intent.kind === 'APPROVE_AD' ? 'APPROVED' : 'REPROVED')
      } else if (intent.kind === 'REVIEW') {
        if (!ad.revisaoAberta?.id) throw new Error('Não existe revisão aberta para este anúncio.')
        await decideAdminReview(ad.revisaoAberta.id, intent.action, reason)
        if (intent.action === 'APROVAR') await revalidarCacheCatalogoPublico()
        setDecisionOutcome(intent.action === 'APROVAR' ? 'APPROVED' : intent.action === 'REPROVAR' ? 'REPROVED' : 'OTHER')
      } else if (intent.kind === 'MEDIA') {
        const motivo = intent.action === 'REPROVAR' ? reason : undefined
        const observacao = intent.action === 'APROVAR'
          && intent.media.tipo === 'FOTO'
          && intent.visibility === 'RESTRITA_18'
          ? reason
          : undefined
        await decideAdminMedia(
          ad.id,
          intent.media.id,
          intent.action,
          intent.visibility,
          motivo,
          observacao,
        )
      } else {
        await reclassifyAdminMedia(intent.media.id, intent.visibility, reason)
      }
      await load()
      setIntent(null)
    } catch (reasonError) {
      const normalized = normalizeApiError(reasonError)
      if (
        (intent.kind === 'OPEN_REVIEW' || intent.kind === 'APPROVE_AD' || intent.kind === 'REPROVE_AD' || intent.kind === 'REVIEW')
        && normalized.kind === 'CONFLICT'
      ) {
        await load()
        setActionError(new ApiContractError(
          'O estado do anúncio mudou. Os dados do detalhe foram atualizados; revise a decisão e tente novamente.',
          'CONFLICT',
          409,
        ))
        return
      }
      if (intent.kind === 'MEDIA' || intent.kind === 'RECLASSIFY') {
        if (normalized.kind === 'CONFLICT' && intent.visibility) {
          await load({
            mediaId: intent.media.id,
            value: intent.visibility,
            mode: intent.kind === 'MEDIA' ? 'DECISION' : 'RECLASSIFY',
          })
          setActionError(new ApiContractError(
            'O estado da mídia mudou. Os dados do detalhe foram atualizados; revise a decisão e tente novamente.',
            'CONFLICT',
            409,
          ))
          return
        }
        setVisibility((current) => {
          const next = { ...current }
          if (intent.media.visibilidadeMidia) next[intent.media.id] = intent.media.visibilidadeMidia
          else delete next[intent.media.id]
          return next
        })
      }
      setActionError(reasonError)
    } finally {
      decisionLock.current = false
      setBusy(false)
    }
  }

  async function confirmLegalAction(
    category: AdminLegalBlockCategory | null,
    reason: string,
    internalNote: string,
  ) {
    if (!legalIntent || legalBusy || !ad) return
    setLegalBusy(true)
    setLegalActionError(null)
    try {
      if (legalIntent.kind === 'REACTIVATE') await reactivateAdminAd(ad.id)
      else if (legalIntent.kind === 'BLOCK_AD') {
        if (!category) return
        await blockAdminAd(ad.id, {
          categoria: category,
          motivo: reason.trim(),
          observacaoInterna: internalNote.trim() || null,
        })
      } else if (legalIntent.kind === 'BLOCK_USER') {
        if (!category) return
        await blockAdminAdAndUser(ad.id, {
          categoria: category,
          motivo: reason.trim(),
          observacaoInterna: internalNote.trim() || null,
        })
      } else if (legalIntent.kind === 'UNBLOCK_AD') await unblockAdminAd(ad.id, reason)
      else await unblockAdminUser(ad.id, reason)
      setLegalIntent(null)
      await load()
    } catch (reasonError) {
      setLegalActionError(reasonError)
    } finally {
      setLegalBusy(false)
    }
  }

  if (loading && !ad) return <p className="py-16 text-center text-sm text-zinc-500">Carregando análise...</p>
  if (error || !ad) return <ContractState error={error ?? new Error('Anúncio indisponível.')} onRetry={() => setReload((value) => value + 1)} />

  async function confirmRemoval(reason: string) {
    if (!removalOpen || removalBusy || !ad) return
    setRemovalBusy(true)
    setRemovalActionError(null)
    try {
      await removeAdminAd(ad.id, reason.trim())
      setRemovalOpen(false)
      await load()
    } catch (reasonError) {
      setRemovalActionError(reasonError)
    } finally {
      setRemovalBusy(false)
    }
  }

  const reviewOpen = Boolean(ad.revisaoAberta && ['ABERTA', 'EM_ANALISE'].includes(ad.revisaoAberta.status))
  const whatsappDigits = ad.anunciante?.whatsapp?.replace(/\D/g, '')
  const effectiveQueueContext = { ...queueContext, page: navigation?.page ?? queueContext.page }
  const backHref = hasQueueContext ? adminAdQueueListHref(effectiveQueueContext) : '/admin/anuncios'
  const targetHref = (target: { id: string; page: number }) => adminAdQueueDetailHref(
    target.id,
    { ...queueContext, page: target.page },
  )
  const legalBlock = ad.bloqueioJuridico
  const removed = ad.status === 'REMOVIDO'
  const canDecideAdReview = canModerateAd && !removed && ad.status !== 'BLOQUEADO'
  const canApproveAd = canDecideAdReview
    && ad.statusModeracao === 'PENDENTE'
    && (ad.status === 'PENDENTE_REVISAO' || reviewOpen)
  const canReproveAd = canApproveAd
    && ad.anunciante?.status === 'ATIVO'
    && !ad.bloqueioJuridico?.usuarioBloqueado
  const canReactivate = canManageLegalStatus
    && ad.status === 'PAUSADO'
    && ad.statusModeracao === 'APROVADO'
    && ad.anunciante?.status === 'ATIVO'
    && !legalBlock?.usuarioBloqueado
  const canRemove = canManageLegalStatus && !removed && ad.status !== 'BLOQUEADO'
  const headerBusy = busy || legalBusy || removalBusy
  const headerActionClass = 'h-8 whitespace-nowrap px-2.5 text-xs'

  return (
    <div className="space-y-5">
      <header className="border-b border-zinc-200 pb-4">
        <Link href={backHref} className="inline-flex items-center gap-2 text-sm font-semibold text-pink-700 hover:text-pink-800"><ArrowLeft className="h-4 w-4" />Voltar para a fila</Link>
        <div className="mt-3 flex flex-col gap-3">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0"><h1 className="text-2xl font-bold text-zinc-950">{ad.titulo}</h1><p className="mt-1 break-all text-xs text-zinc-500">{ad.slug}</p></div>
            <div className="flex flex-wrap items-center justify-end gap-2">
              <Badge variant="outline" className={moderationTone(ad.status)}>{ad.status}</Badge>
              <Badge variant="outline" className={moderationTone(ad.statusModeracao)}>{ad.statusModeracao}</Badge>
            </div>
          </div>
          <div
            aria-label="Ações jurídicas e administrativas"
            className="flex w-full min-w-0 flex-wrap items-center gap-2 xl:flex-nowrap xl:justify-end xl:gap-1.5"
          >
            {canApproveAd ? (
              <Button
                type="button"
                size="sm"
                className={`${headerActionClass} bg-emerald-700 text-white hover:bg-emerald-800`}
                disabled={headerBusy}
                onClick={() => setIntent({
                  kind: 'APPROVE_AD',
                  title: 'Aprovar anúncio',
                  requiresReason: false,
                })}
              >
                <CheckCircle2 className="mr-2 h-4 w-4" />
                Aprovar anúncio
              </Button>
            ) : null}
            {canReproveAd ? (
              <Button
                type="button"
                size="sm"
                variant="destructive"
                className={headerActionClass}
                disabled={headerBusy}
                onClick={() => setIntent({
                  kind: 'REPROVE_AD',
                  title: 'Reprovar anúncio',
                  requiresReason: true,
                })}
              >
                <XCircle className="mr-1.5 h-4 w-4" />
                Reprovar anúncio
              </Button>
            ) : null}
            {canManageLegalStatus && !removed ? (
              <>
                {canReactivate ? <Button type="button" size="sm" variant="outline" className={headerActionClass} disabled={headerBusy} onClick={() => setLegalIntent({ kind: 'REACTIVATE', title: 'Reativar anúncio pausado' })}><RotateCcw className="mr-1.5 h-4 w-4" />Reativar</Button> : null}
                {legalBlock?.anuncioBloqueado ? <Button type="button" size="sm" variant="outline" className={headerActionClass} disabled={headerBusy} onClick={() => setLegalIntent({ kind: 'UNBLOCK_AD', title: 'Desbloquear anúncio' })}><Unlock className="mr-1.5 h-4 w-4" />Desbloquear anúncio</Button> : null}
                {legalBlock?.usuarioBloqueado ? <Button type="button" size="sm" variant="outline" className={headerActionClass} disabled={headerBusy} onClick={() => setLegalIntent({ kind: 'UNBLOCK_USER', title: 'Desbloquear usuário' })}><Unlock className="mr-1.5 h-4 w-4" />Desbloquear usuário</Button> : null}
                {!legalBlock?.anuncioBloqueado && !legalBlock?.usuarioBloqueado ? <Button type="button" size="sm" variant="destructive" className={headerActionClass} disabled={headerBusy} onClick={() => setLegalIntent({ kind: 'BLOCK_AD', title: 'Bloquear anúncio' })}><LockKeyhole className="mr-1.5 h-4 w-4" />Bloquear anúncio</Button> : null}
                {!legalBlock?.anuncioBloqueado && !legalBlock?.usuarioBloqueado && ad.anunciante?.status !== 'SUSPENSO' ? <Button type="button" size="sm" variant="destructive" className={headerActionClass} disabled={headerBusy} onClick={() => setLegalIntent({ kind: 'BLOCK_USER', title: 'Bloquear anúncio e usuário' })}><ShieldAlert className="mr-1.5 h-4 w-4" />Bloquear anúncio e usuário</Button> : null}
              </>
            ) : null}
            {canRemove ? (
              <Button
                type="button"
                size="sm"
                variant="destructive"
                className={`${headerActionClass} border border-red-950 bg-red-700 text-white hover:bg-red-800`}
                disabled={headerBusy}
                onClick={() => {
                  setRemovalActionError(null)
                  setRemovalOpen(true)
                }}
              >
                <Trash2 className="mr-1.5 h-4 w-4" />
                {'Excluir an\u00fancio'}
              </Button>
            ) : null}
            {isAdmin && canModerateAd ? <Button asChild size="sm" variant="outline" className={headerActionClass}><Link href={`/admin/anuncios/${ad.id}/editar`}><Pencil className="mr-1.5 h-4 w-4" />Editar anúncio</Link></Button> : null}
          </div>
        </div>
      </header>

      {hasQueueContext ? (
        <nav aria-label="Navegação da fila" className="flex flex-col gap-3 border-b border-zinc-200 pb-4 sm:flex-row sm:items-center sm:justify-between">
          {navigation?.anterior ? <Button asChild type="button" variant="outline"><Link href={targetHref(navigation.anterior)}><ChevronLeft className="mr-2 h-4 w-4" />Anterior</Link></Button> : <Button type="button" variant="outline" disabled><ChevronLeft className="mr-2 h-4 w-4" />Anterior</Button>}
          <span className="text-center text-sm font-semibold text-zinc-700">{navigation ? `${navigation.posicao} / ${navigation.total}` : navigationError ? 'Contexto indisponível' : 'Carregando posição...'}</span>
          {navigation?.proximo ? <Button asChild type="button" variant="outline"><Link href={targetHref(navigation.proximo)}>Próximo<ChevronRight className="ml-2 h-4 w-4" /></Link></Button> : <Button type="button" variant="outline" disabled>Próximo<ChevronRight className="ml-2 h-4 w-4" /></Button>}
        </nav>
      ) : null}
      {navigationError ? <ContractState error={navigationError} compact /> : null}
      {decisionOutcome ? (
        <div role="status" className="flex flex-col gap-3 border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900 sm:flex-row sm:items-center sm:justify-between">
          <span>
            {decisionOutcome === 'APPROVED'
              ? 'Anúncio aprovado com sucesso.'
              : decisionOutcome === 'REPROVED'
                ? 'Anúncio reprovado. O anunciante foi informado sobre as alterações necessárias.'
                : 'Decisão persistida. O avanço permanece sob seu controle.'}
          </span>
          <div className="flex flex-wrap items-center gap-2">
            {decisionOutcome === 'APPROVED' ? (
              <>
                <Button asChild size="sm" variant="outline">
                  <Link href={adminAdQueueListHref({ ...queueContext, page: 0, situacao: 'TODOS' })}>Ver Todos</Link>
                </Button>
                <Button asChild size="sm">
                  <Link href={adminAdQueueListHref({ ...queueContext, page: 0, situacao: 'APROVADOS' })}>Ver Aprovados</Link>
                </Button>
              </>
            ) : navigation?.proximo ? (
              <Button asChild size="sm"><Link href={targetHref(navigation.proximo)}>Próximo da fila<ChevronRight className="ml-2 h-4 w-4" /></Link></Button>
            ) : (
              <strong>Fim da fila</strong>
            )}
          </div>
        </div>
      ) : null}

      <section className="grid gap-4 lg:grid-cols-[minmax(0,1.25fr)_minmax(0,1fr)]">
        <div className="rounded-md border border-zinc-200 bg-white p-4">
          <h2 className="text-sm font-semibold text-zinc-950">Proprietário</h2>
          <div className="mt-3 grid gap-3 text-sm sm:grid-cols-2">
            <div><span className="block text-xs text-zinc-500">Nome completo</span><strong>{ad.anunciante?.nomeCivil || ad.anunciante?.nome || 'Não informado'}</strong></div>
            <div><span className="block text-xs text-zinc-500">Conta</span><strong>{formatEnum(ad.anunciante?.status)}</strong></div>
            <div><span className="block text-xs text-zinc-500">E-mail</span><span className="break-all">{ad.anunciante?.email || 'Não informado'}</span></div>
            <div><span className="block text-xs text-zinc-500">CPF</span><span>{ad.anunciante?.cpf || 'Não informado'}</span></div>
            <div className="sm:col-span-2"><span className="block text-xs text-zinc-500">WhatsApp</span>{whatsappDigits ? <a href={`https://wa.me/${whatsappDigits}`} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 font-medium text-pink-700 hover:underline">{ad.anunciante?.whatsapp}<ExternalLink className="h-3 w-3" /></a> : <span>Não informado</span>}</div>
          </div>
        </div>
        <div className="rounded-md border border-zinc-200 bg-white p-4">
          <h2 className="text-sm font-semibold text-zinc-950">Métricas</h2>
          <dl className="mt-3 grid grid-cols-2 gap-3 text-sm">
            <div><dt className="text-xs text-zinc-500">Visualizações</dt><dd className="text-lg font-bold">{viewsLabel(ad)}</dd></div>
            <div><dt className="text-xs text-zinc-500">Cliques WhatsApp</dt><dd className="text-lg font-bold">{ad.metricas.cliquesWhatsapp}</dd></div>
            <div><dt className="text-xs text-zinc-500">CTR</dt><dd className="font-semibold">{ad.metricas.ctr == null ? '—' : `${ad.metricas.ctr.toFixed(2)}%`}</dd></div>
            <div><dt className="text-xs text-zinc-500">Premium vigente</dt><dd className="font-semibold">{ad.metricas.beneficiosPremiumVigentes.length ? ad.metricas.beneficiosPremiumVigentes.join(', ') : 'Nenhum'}</dd></div>
          </dl>
          <p className="mt-3 border-t border-zinc-100 pt-3 text-xs text-zinc-500">Última ação: {ad.metricas.ultimaAcaoAdministrativa ? `${formatEnum(ad.metricas.ultimaAcaoAdministrativa.decisao || ad.metricas.ultimaAcaoAdministrativa.acao)} · ${formatDate(ad.metricas.ultimaAcaoAdministrativa.criadoEm)}` : 'nenhuma ação registrada'}</p>
        </div>
      </section>

      <AdminAnuncioStory anuncioId={ad.id} canManage={isAdmin} />

      <Tabs defaultValue="dados" className="space-y-4">
        <div className="overflow-x-auto"><TabsList className="w-max min-w-full justify-start rounded-md"><TabsTrigger value="dados">Dados e decisão</TabsTrigger><TabsTrigger value="midias">Mídias ({media.length})</TabsTrigger><TabsTrigger value="documentos">Documentos</TabsTrigger><TabsTrigger value="premium">Premium</TabsTrigger><TabsTrigger value="historico">Histórico</TabsTrigger></TabsList></div>

        <TabsContent value="dados" className="space-y-5">
          <section className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_340px]">
            <div>
              <h2 className="text-lg font-semibold text-zinc-950">Dados do anúncio</h2>
              <dl className="mt-4 grid gap-x-6 gap-y-4 sm:grid-cols-2">
                <div><dt className="text-xs font-semibold uppercase text-zinc-500">Categoria</dt><dd className="mt-1 text-sm">{formatEnum(ad.categoria)}</dd></div>
                <div><dt className="text-xs font-semibold uppercase text-zinc-500">Preço</dt><dd className="mt-1 text-sm">{formatPrice(ad.preco)}</dd></div>
                <div><dt className="text-xs font-semibold uppercase text-zinc-500">Localização</dt><dd className="mt-1 text-sm">{[ad.localizacao?.bairro, ad.localizacao?.cidade, ad.localizacao?.uf].filter(Boolean).join(' · ') || 'Não informada'}</dd></div>
                <div><dt className="text-xs font-semibold uppercase text-zinc-500">Região</dt><dd className="mt-1 text-sm">{ad.localizacao?.enderecoResumido || 'Não informada'}</dd></div>
                <div><dt className="text-xs font-semibold uppercase text-zinc-500">Criação</dt><dd className="mt-1 text-sm">{formatDate(ad.criadoEm)}</dd></div>
                <div><dt className="text-xs font-semibold uppercase text-zinc-500">WhatsApp do anúncio</dt><dd className="mt-1 text-sm">{ad.whatsapp || 'Não informado'}</dd></div>
                <div className="sm:col-span-2"><dt className="text-xs font-semibold uppercase text-zinc-500">Descrição</dt><dd className="mt-1 whitespace-pre-wrap text-sm leading-6">{ad.descricao || ad.descricaoResumo || 'Não informada'}</dd></div>
                <div><dt className="text-xs font-semibold uppercase text-zinc-500">Serviços</dt><dd className="mt-1 text-sm">{ad.servicos.length ? ad.servicos.map(formatEnum).join(', ') : 'Não informados'}</dd></div>
                <div><dt className="text-xs font-semibold uppercase text-zinc-500">Locais de atendimento</dt><dd className="mt-1 text-sm">{ad.locaisAtendimento.length ? ad.locaisAtendimento.map(formatEnum).join(', ') : 'Não informados'}</dd></div>
              </dl>
            </div>
            <aside className="rounded-md border border-zinc-200 p-4">
              <h2 className="font-semibold text-zinc-950">Decisão do anúncio</h2>
              <p className="mt-2 text-sm text-zinc-600">
                {reviewOpen
                  ? `Revisão ${ad.revisaoAberta?.status.toLowerCase()} desde ${formatDate(ad.revisaoAberta?.criadoEm)}.`
                  : 'Não existe revisão aberta para decisão.'}
              </p>
              {canDecideAdReview ? (
                <div className="mt-4 flex flex-col gap-2">
                  {!reviewOpen && ad.statusModeracao === 'PENDENTE' ? (
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => setIntent({
                        kind: 'OPEN_REVIEW',
                        title: 'Abrir revisão do anúncio',
                        requiresReason: true,
                      })}
                    >
                      Abrir revisão
                    </Button>
                  ) : null}
                  {reviewOpen ? (
                    <>
                      <Button
                        type="button"
                        variant="outline"
                        onClick={() => setIntent({
                          kind: 'REVIEW',
                          title: 'Solicitar ajuste no anúncio',
                          action: 'SOLICITAR_AJUSTE',
                          requiresReason: true,
                        })}
                      >
                        <Clock3 className="mr-2 h-4 w-4" />
                        Solicitar ajuste
                      </Button>
                    </>
                  ) : null}
                </div>
              ) : (
                <p className="mt-4 text-sm font-medium text-amber-700">
                  {canModerateAd
                    ? 'O estado atual do anúncio não permite decisão.'
                    : 'Sem ANUNCIO_MODERAR.'}
                </p>
              )}
            </aside>
          </section>
        </TabsContent>

        <TabsContent value="midias">
          {!canModerateMedia ? (
            <p className="text-sm font-medium text-amber-700">Seu perfil não possui MIDIA_REVISAR.</p>
          ) : media.length === 0 ? (
            <p className="text-sm text-zinc-600">Nenhuma foto ou vídeo vinculado ao anúncio.</p>
          ) : (
            <div className="space-y-4">
              <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
                {media.map((item) => {
                  const actionable = actionableMedia.has(item.status)
                  const pendingPhoto = item.tipo === 'FOTO' && actionable
                  const actionableVideo = item.tipo === 'VIDEO' && actionable
                  const reclassifiable = item.tipo === 'FOTO' && item.status === 'PUBLICAVEL'
                  const selectedVisibility = item.tipo === 'VIDEO' ? 'RESTRITA_18' : visibility[item.id]
                  return (
                    <article key={item.id} className="overflow-hidden rounded-md border border-zinc-200 bg-white">
                      <MediaPreview media={item} />
                      <div className="p-4">
                        <div className="flex items-start justify-between gap-2">
                          <div className="flex items-center gap-2 font-semibold">{item.tipo === 'VIDEO' ? <Video className="h-4 w-4" /> : <ImageIcon className="h-4 w-4" />}{formatEnum(item.tipo)} #{mediaOrdinal[item.id]}</div>
                          <Badge variant="outline" className={moderationTone(item.status)}>{item.status}</Badge>
                        </div>
                        <dl className="mt-3 grid grid-cols-2 gap-2 text-xs text-zinc-600">
                          <div><dt>Dimensões</dt><dd className="font-medium text-zinc-900">{item.largura && item.altura ? `${item.largura} × ${item.altura}` : '—'}</dd></div>
                          <div><dt>MIME</dt><dd className="break-all font-medium text-zinc-900">{item.mimeType || '—'}</dd></div>
                          <div><dt>Arquivo</dt><dd className="font-medium text-zinc-900">{item.statusArquivo || '—'}</dd></div>
                          <div><dt>Classificação</dt><dd className="font-medium text-zinc-900">{item.visibilidadeMidia || '—'}</dd></div>
                        </dl>
                        {pendingPhoto ? (
                          <PendingPhotoDecisionSelector
                            mediaId={item.id}
                            value={photoDecisions[item.id]}
                            disabled={photoBatchBusy}
                            onChange={(value) => selectPhotoDecision(item.id, value)}
                            onObservationChange={(value) => updatePhotoObservation(item.id, value)}
                          />
                        ) : item.tipo === 'VIDEO' ? (
                          <p className="mt-4 flex items-center gap-2 rounded-md bg-pink-50 px-3 py-2 text-xs font-semibold text-pink-900"><ShieldAlert className="h-4 w-4" />Sempre RESTRITA_18</p>
                        ) : reclassifiable && canReclassifyMedia ? (
                          <MediaVisibilitySelector
                            mediaId={item.id}
                            value={selectedVisibility}
                            disabled={busy}
                            onChange={(value) => setVisibility((current) => ({ ...current, [item.id]: value }))}
                          />
                        ) : null}
                        {actionableVideo ? (
                          <div className="mt-4 grid grid-cols-2 gap-2">
                            <Button type="button" size="sm" disabled={busy} onClick={() => setIntent({ kind: 'MEDIA', title: 'Aplicar e aprovar vídeo', media: item, action: 'APROVAR', visibility: 'RESTRITA_18', requiresReason: false })}>Aplicar e aprovar</Button>
                            <Button type="button" size="sm" variant="destructive" disabled={busy} onClick={() => setIntent({ kind: 'MEDIA', title: 'Rejeitar vídeo', media: item, action: 'REPROVAR', requiresReason: true })}>Rejeitar</Button>
                          </div>
                        ) : reclassifiable && canReclassifyMedia ? (
                          <Button
                            type="button"
                            size="sm"
                            className="mt-4 w-full"
                            disabled={busy || !selectedVisibility || selectedVisibility === item.visibilidadeMidia}
                            onClick={() => selectedVisibility && setIntent({
                              kind: 'RECLASSIFY',
                              title: 'Aplicar nova classificação',
                              media: item,
                              visibility: selectedVisibility,
                              requiresReason: true,
                            })}
                          >
                            Aplicar classificação
                          </Button>
                        ) : null}
                      </div>
                    </article>
                  )
                })}
              </div>
              {photoBatchResult ? (
                <div
                  role="status"
                  className={`border px-4 py-3 text-sm ${photoBatchResult.falhas > 0 ? 'border-amber-300 bg-amber-50 text-amber-950' : 'border-emerald-200 bg-emerald-50 text-emerald-900'}`}
                >
                  <p className="font-semibold">
                    {photoBatchResult.aprovadas} aprovadas, {photoBatchResult.excluidas} excluídas, {photoBatchResult.jaProcessadas} já processadas e {photoBatchResult.falhas} falhas.
                  </p>
                  {photoBatchResult.falhas > 0 ? (
                    <ul className="mt-2 space-y-1">
                      {photoBatchResult.resultados
                        .filter((item) => item.resultado === 'FALHA')
                        .map((item) => (
                          <li key={item.mediaId}>
                            Foto #{mediaOrdinal[item.mediaId] ?? item.mediaId.slice(0, 8)}: {item.motivo || 'Não foi possível concluir a decisão.'}
                          </li>
                        ))}
                    </ul>
                  ) : null}
                </div>
              ) : null}
              {pendingPhotos.length > 0 ? (
                <div className="flex justify-end border-t border-zinc-200 pt-4">
                  <Button
                    type="button"
                    disabled={!allPendingPhotosSelected || photoBatchBusy}
                    onClick={() => {
                      setPhotoBatchError(null)
                      setPhotoBatchOpen(true)
                    }}
                  >
                    {photoBatchBusy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
                    Confirmar decisões das fotos ({selectedPhotoCount})
                  </Button>
                </div>
              ) : null}
            </div>
          )}
        </TabsContent>

        <TabsContent value="documentos"><AdminAnuncioDocumentos anuncioId={ad.id} anunciante={ad.anunciante} autorizado={canReadDocuments} /></TabsContent>
        <TabsContent value="premium"><AdminAnuncioPremium anuncioId={ad.id} canManage={canManagePremium && !removed} disabledReason={removed ? 'Anuncio removido nao pode receber novas ativacoes Premium.' : undefined} /></TabsContent>
        <TabsContent value="historico">{!canReadHistory ? <p className="text-sm font-medium text-amber-700">Sem permissão para consultar o histórico.</p> : history.length === 0 ? <p className="text-sm text-zinc-600">Nenhuma ação administrativa registrada.</p> : <ol className="divide-y divide-zinc-200 border-y border-zinc-200">{history.map((item) => <li key={item.id} className="grid gap-2 py-4 sm:grid-cols-[1fr_auto]"><div><p className="text-sm font-semibold text-zinc-900">{formatEnum(item.decisao || item.acao)}</p><p className="mt-1 text-xs text-zinc-600">{formatEnum(item.alvoTipo)} · {item.status ? formatEnum(item.status) : 'sem mudança de estado'}</p>{item.categoria ? <p className="mt-2 text-xs font-semibold uppercase text-red-700">{formatEnum(item.categoria)}</p> : null}{item.motivo ? <p className="mt-2 whitespace-pre-wrap text-sm text-zinc-700">{item.motivo}</p> : null}{item.observacaoInterna ? <p className="mt-2 whitespace-pre-wrap border-l-2 border-zinc-300 pl-3 text-xs text-zinc-600">Observação interna: {item.observacaoInterna}</p> : null}</div><div className="text-left text-xs text-zinc-500 sm:text-right"><p>{formatDate(item.criadoEm)}</p><p className="mt-1">Responsável {item.atorId?.slice(0, 8) || 'não identificado'}</p><p className="mt-1">Request {item.requestId?.slice(0, 16) || 'não informado'}</p></div></li>)}</ol>}</TabsContent>
      </Tabs>

      <DecisionDialog intent={intent} busy={busy} error={actionError} onClose={() => { setIntent(null); setActionError(null) }} onConfirm={(reason) => void confirmDecision(reason)} />
      <PhotoBatchDialog
        open={photoBatchOpen}
        busy={photoBatchBusy}
        error={photoBatchError}
        livre={photoBatchSummary.livre}
        restrita={photoBatchSummary.restrita}
        excluir={photoBatchSummary.excluir}
        onClose={() => {
          setPhotoBatchOpen(false)
          setPhotoBatchError(null)
        }}
        onConfirm={() => void confirmPhotoBatch()}
      />
      <LegalActionDialog intent={legalIntent} busy={legalBusy} error={legalActionError} onClose={() => { setLegalIntent(null); setLegalActionError(null) }} onConfirm={(category, reason, internalNote) => void confirmLegalAction(category, reason, internalNote)} />
      <RemovalDialog open={removalOpen} busy={removalBusy} error={removalActionError} onClose={() => { setRemovalOpen(false); setRemovalActionError(null) }} onConfirm={(reason) => void confirmRemoval(reason)} />
    </div>
  )
}
