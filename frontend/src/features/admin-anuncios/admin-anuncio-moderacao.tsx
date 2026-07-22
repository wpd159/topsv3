'use client'

import Link from 'next/link'
import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  ArrowLeft,
  CheckCircle2,
  Clock3,
  FileWarning,
  Image as ImageIcon,
  Loader2,
  ShieldAlert,
  Video,
  XCircle,
} from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { getAdminSession } from '@/lib/admin-auth-api'

import {
  decideAdminMedia,
  decideAdminReview,
  getAdminAd,
  getAdminMediaPreview,
  listAdminAdHistory,
  listAdminAdMedia,
  submitAdminReview,
} from './api'
import type {
  AdminAdDetail,
  AdminMediaItem,
  AdminMediaPreview,
  AdminModerationHistoryItem,
} from './types'

type DecisionIntent =
  | { kind: 'OPEN_REVIEW'; title: string; requiresReason: true }
  | { kind: 'REVIEW'; title: string; action: 'APROVAR' | 'REPROVAR' | 'SOLICITAR_AJUSTE'; requiresReason: boolean }
  | { kind: 'MEDIA'; title: string; media: AdminMediaItem; action: 'APROVAR' | 'REPROVAR'; visibility?: 'LIVRE' | 'RESTRITA_18'; requiresReason: boolean }

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
  if (value === 'REJEITADO' || value === 'REJEITADA') return 'border-red-200 bg-red-50 text-red-800'
  return 'border-amber-200 bg-amber-50 text-amber-800'
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

  if (error) {
    return (
      <div className="flex aspect-video items-center justify-center bg-zinc-100 p-4 text-center">
        <div>
          <FileWarning className="mx-auto h-6 w-6 text-zinc-500" aria-hidden="true" />
          <p className="mt-2 text-xs text-zinc-600">Prévia indisponível.</p>
          <Button type="button" size="sm" variant="ghost" className="mt-1" onClick={() => setReload((value) => value + 1)}>Tentar novamente</Button>
        </div>
      </div>
    )
  }

  if (!preview) {
    return <div className="flex aspect-video items-center justify-center bg-zinc-100"><Loader2 className="h-5 w-5 animate-spin text-zinc-500" aria-label="Carregando prévia" /></div>
  }

  if (media.tipo === 'VIDEO') {
    return <video src={preview.url} controls preload="metadata" className="aspect-video w-full bg-black object-contain" />
  }

  return <img src={preview.url} alt="Mídia em análise" className="aspect-video w-full bg-zinc-100 object-contain" loading="lazy" />
}

function DecisionDialog({
  intent,
  busy,
  error,
  onClose,
  onConfirm,
}: {
  intent: DecisionIntent | null
  busy: boolean
  error: unknown
  onClose: () => void
  onConfirm: (reason: string) => void
}) {
  const [reason, setReason] = useState('')

  useEffect(() => {
    setReason('')
  }, [intent])

  return (
    <Dialog open={Boolean(intent)} onOpenChange={(open) => { if (!open && !busy) onClose() }}>
      <DialogContent className="rounded-md">
        <DialogHeader>
          <DialogTitle>{intent?.title}</DialogTitle>
          <DialogDescription>
            A decisão será registrada com ator, data UTC e identificador da requisição.
          </DialogDescription>
        </DialogHeader>
        {intent?.requiresReason ? (
          <label>
            <span className="mb-2 block text-sm font-semibold text-zinc-800">Motivo obrigatório</span>
            <Textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={240} rows={4} disabled={busy} />
            <span className="mt-1 block text-right text-xs text-zinc-500">{reason.length}/240</span>
          </label>
        ) : null}
        {error ? <ContractState error={error} compact /> : null}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Cancelar</Button>
          <Button type="button" onClick={() => onConfirm(reason)} disabled={busy || Boolean(intent?.requiresReason && !reason.trim())}>
            {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" /> : null}
            Confirmar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export function AdminAnuncioModeracao({ anuncioId }: { anuncioId: string }) {
  const [ad, setAd] = useState<AdminAdDetail | null>(null)
  const [media, setMedia] = useState<AdminMediaItem[]>([])
  const [history, setHistory] = useState<AdminModerationHistoryItem[]>([])
  const [permissions, setPermissions] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [intent, setIntent] = useState<DecisionIntent | null>(null)
  const [actionError, setActionError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const [reload, setReload] = useState(0)
  const [visibility, setVisibility] = useState<Record<string, 'LIVRE' | 'RESTRITA_18'>>({})

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [adResponse, session] = await Promise.all([
        getAdminAd(anuncioId),
        getAdminSession(),
      ])
      const sessionPermissions = session?.permissoes ?? []
      const canReadMedia = sessionPermissions.includes('MIDIA_REVISAR')
      const canReadHistory = canReadMedia || sessionPermissions.includes('ANUNCIO_MODERAR')
      const [mediaResponse, historyResponse] = await Promise.all([
        canReadMedia ? listAdminAdMedia(anuncioId) : Promise.resolve(null),
        canReadHistory ? listAdminAdHistory(anuncioId) : Promise.resolve([]),
      ])
      setAd(adResponse)
      setMedia(mediaResponse?.itens.filter((item) => String(item.tipo) !== 'STORY') ?? [])
      setHistory(historyResponse)
      setPermissions(sessionPermissions)
      setVisibility((current) => {
        const next = { ...current }
        mediaResponse?.itens.forEach((item) => {
          if (item.tipo === 'FOTO' && item.visibilidadeMidia) next[item.id] = item.visibilidadeMidia
        })
        return next
      })
    } catch (reason) {
      setError(reason)
    } finally {
      setLoading(false)
    }
  }, [anuncioId])

  useEffect(() => {
    void load()
  }, [load, reload])

  const canModerateAd = permissions.includes('ANUNCIO_MODERAR')
  const canModerateMedia = permissions.includes('MIDIA_REVISAR')
  const canReadHistory = canModerateAd || canModerateMedia
  const actionableMedia = useMemo(() => new Set(['PENDENTE', 'AJUSTE_SOLICITADO']), [])

  async function confirmDecision(reason: string) {
    if (!intent || busy || !ad) return
    setBusy(true)
    setActionError(null)
    try {
      if (intent.kind === 'OPEN_REVIEW') {
        await submitAdminReview(ad.id, reason)
      } else if (intent.kind === 'REVIEW') {
        if (!ad.revisaoAberta?.id) throw new Error('Não existe revisão aberta para este anúncio.')
        await decideAdminReview(ad.revisaoAberta.id, intent.action, reason)
      } else {
        await decideAdminMedia(intent.media.id, intent.action, intent.visibility, reason)
      }
      setIntent(null)
      setReload((value) => value + 1)
    } catch (reasonError) {
      setActionError(reasonError)
    } finally {
      setBusy(false)
    }
  }

  if (loading && !ad) return <p className="py-16 text-center text-sm text-zinc-500">Carregando análise...</p>
  if (error || !ad) return <ContractState error={error ?? new Error('Anúncio indisponível.')} onRetry={() => setReload((value) => value + 1)} />

  const reviewOpen = Boolean(ad.revisaoAberta && ['ABERTA', 'EM_ANALISE'].includes(ad.revisaoAberta.status))

  return (
    <div className="space-y-8">
      <header className="border-b border-zinc-200 pb-5">
        <Link href="/admin/anuncios" className="inline-flex items-center gap-2 text-sm font-semibold text-pink-700 hover:text-pink-800">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          Voltar para a fila
        </Link>
        <div className="mt-4 flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <h1 className="text-2xl font-bold text-zinc-950">{ad.titulo}</h1>
            <p className="mt-1 break-all text-xs text-zinc-500">{ad.slug}</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Badge variant="outline" className={moderationTone(ad.status)}>{ad.status}</Badge>
            <Badge variant="outline" className={moderationTone(ad.statusModeracao)}>{ad.statusModeracao}</Badge>
          </div>
        </div>
      </header>

      <section aria-labelledby="dados-anuncio" className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_300px]">
        <div className="min-w-0">
          <h2 id="dados-anuncio" className="text-lg font-semibold text-zinc-950">Dados do anúncio</h2>
          <dl className="mt-4 grid gap-x-6 gap-y-4 sm:grid-cols-2">
            <div><dt className="text-xs font-semibold uppercase text-zinc-500">Categoria</dt><dd className="mt-1 text-sm text-zinc-900">{formatEnum(ad.categoria)}</dd></div>
            <div><dt className="text-xs font-semibold uppercase text-zinc-500">Preço</dt><dd className="mt-1 text-sm text-zinc-900">{formatPrice(ad.preco)}</dd></div>
            <div><dt className="text-xs font-semibold uppercase text-zinc-500">Localização</dt><dd className="mt-1 text-sm text-zinc-900">{[ad.localizacao?.bairro, ad.localizacao?.cidade, ad.localizacao?.uf].filter(Boolean).join(' · ') || 'Não informada'}</dd></div>
            <div><dt className="text-xs font-semibold uppercase text-zinc-500">Contato</dt><dd className="mt-1 break-all text-sm text-zinc-900">{ad.whatsapp || 'Não informado'}</dd></div>
            <div className="sm:col-span-2"><dt className="text-xs font-semibold uppercase text-zinc-500">Descrição</dt><dd className="mt-1 whitespace-pre-wrap text-sm leading-6 text-zinc-800">{ad.descricao || ad.descricaoResumo || 'Não informada'}</dd></div>
            <div><dt className="text-xs font-semibold uppercase text-zinc-500">Serviços</dt><dd className="mt-1 text-sm text-zinc-900">{ad.servicos.length ? ad.servicos.map(formatEnum).join(', ') : 'Não informados'}</dd></div>
            <div><dt className="text-xs font-semibold uppercase text-zinc-500">Locais de atendimento</dt><dd className="mt-1 text-sm text-zinc-900">{ad.locaisAtendimento.length ? ad.locaisAtendimento.map(formatEnum).join(', ') : 'Não informados'}</dd></div>
          </dl>
        </div>
        <aside className="border-l-0 border-zinc-200 lg:border-l lg:pl-6">
          <h2 className="text-sm font-semibold text-zinc-950">Proprietário</h2>
          <p className="mt-3 text-sm font-medium text-zinc-900">{ad.anunciante?.nome || 'Nome indisponível'}</p>
          <p className="mt-1 text-xs text-zinc-600">{ad.anunciante?.emailMascarado || 'E-mail indisponível'}</p>
          <p className="mt-1 text-xs text-zinc-500">Conta: {formatEnum(ad.anunciante?.status)}</p>
          <div className="mt-5 border-t border-zinc-200 pt-4 text-xs text-zinc-600">
            <p>Criado em {formatDate(ad.criadoEm)}</p>
            <p className="mt-1">Atualizado em {formatDate(ad.atualizadoEm)}</p>
          </div>
        </aside>
      </section>

      <section aria-labelledby="decisao-anuncio" className="border-y border-zinc-200 py-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h2 id="decisao-anuncio" className="text-lg font-semibold text-zinc-950">Decisão do anúncio</h2>
            <p className="mt-1 text-sm text-zinc-600">
              {reviewOpen ? `Revisão ${ad.revisaoAberta?.status.toLowerCase()} desde ${formatDate(ad.revisaoAberta?.criadoEm)}.` : 'Não existe revisão aberta para decisão.'}
            </p>
          </div>
          {canModerateAd ? (
            <div className="flex flex-wrap gap-2">
              {!reviewOpen && ad.statusModeracao === 'PENDENTE' ? (
                <Button type="button" variant="outline" onClick={() => setIntent({ kind: 'OPEN_REVIEW', title: 'Abrir revisão do anúncio', requiresReason: true })}>Abrir revisão</Button>
              ) : null}
              {reviewOpen ? (
                <>
                  <Button type="button" onClick={() => setIntent({ kind: 'REVIEW', title: 'Aprovar anúncio', action: 'APROVAR', requiresReason: false })}><CheckCircle2 className="mr-2 h-4 w-4" />Aprovar</Button>
                  <Button type="button" variant="outline" onClick={() => setIntent({ kind: 'REVIEW', title: 'Solicitar ajuste no anúncio', action: 'SOLICITAR_AJUSTE', requiresReason: true })}><Clock3 className="mr-2 h-4 w-4" />Solicitar ajuste</Button>
                  <Button type="button" variant="destructive" onClick={() => setIntent({ kind: 'REVIEW', title: 'Rejeitar anúncio', action: 'REPROVAR', requiresReason: true })}><XCircle className="mr-2 h-4 w-4" />Rejeitar</Button>
                </>
              ) : null}
            </div>
          ) : <p className="text-sm font-medium text-amber-700">Seu perfil não possui ANUNCIO_MODERAR.</p>}
        </div>
      </section>

      <section aria-labelledby="midias-anuncio">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 id="midias-anuncio" className="text-lg font-semibold text-zinc-950">Fotos e vídeos</h2>
            <p className="mt-1 text-sm text-zinc-600">Cada decisão afeta somente a mídia identificada. Stories não integram esta fila.</p>
          </div>
          <span className="text-sm text-zinc-500">{media.length} mídias</span>
        </div>
        {!canModerateMedia ? (
          <p className="mt-4 border border-amber-200 bg-amber-50 p-5 text-sm font-medium text-amber-800">Seu perfil não possui permissão para revisar mídias.</p>
        ) : media.length === 0 ? (
          <p className="mt-4 border border-zinc-200 bg-white p-5 text-sm text-zinc-600">Nenhuma foto ou vídeo vinculado ao anúncio.</p>
        ) : (
          <div className="mt-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {media.map((item) => {
              const actionable = actionableMedia.has(item.status)
              const selectedVisibility = item.tipo === 'VIDEO' ? 'RESTRITA_18' : visibility[item.id]
              return (
                <article key={item.id} className="overflow-hidden rounded-md border border-zinc-200 bg-white">
                  <MediaPreview media={item} />
                  <div className="p-4">
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex items-center gap-2 font-semibold text-zinc-950">
                        {item.tipo === 'VIDEO' ? <Video className="h-4 w-4" /> : <ImageIcon className="h-4 w-4" />}
                        {formatEnum(item.tipo)} {item.ordem != null ? `#${item.ordem + 1}` : ''}
                      </div>
                      <Badge variant="outline" className={moderationTone(item.status)}>{item.status}</Badge>
                    </div>
                    <dl className="mt-3 grid grid-cols-2 gap-2 text-xs text-zinc-600">
                      <div><dt>Dimensões</dt><dd className="font-medium text-zinc-900">{item.largura && item.altura ? `${item.largura} × ${item.altura}` : '—'}</dd></div>
                      <div><dt>Arquivo</dt><dd className="font-medium text-zinc-900">{item.mimeType || '—'}</dd></div>
                    </dl>
                    {item.tipo === 'VIDEO' ? (
                      <p className="mt-4 flex items-center gap-2 rounded-md bg-pink-50 px-3 py-2 text-xs font-semibold text-pink-900"><ShieldAlert className="h-4 w-4" />Sempre RESTRITA_18</p>
                    ) : (
                      <fieldset className="mt-4" disabled={!canModerateMedia || !actionable || busy}>
                        <legend className="text-xs font-semibold text-zinc-700">Classificação na aprovação</legend>
                        <div className="mt-2 flex flex-wrap gap-4 text-sm">
                          <label className="flex items-center gap-2"><input type="radio" name={`visibility-${item.id}`} checked={selectedVisibility === 'LIVRE'} onChange={() => setVisibility((current) => ({ ...current, [item.id]: 'LIVRE' }))} />LIVRE</label>
                          <label className="flex items-center gap-2"><input type="radio" name={`visibility-${item.id}`} checked={selectedVisibility === 'RESTRITA_18'} onChange={() => setVisibility((current) => ({ ...current, [item.id]: 'RESTRITA_18' }))} />RESTRITA_18</label>
                        </div>
                      </fieldset>
                    )}
                    {canModerateMedia && actionable ? (
                      <div className="mt-4 grid grid-cols-2 gap-2">
                        <Button type="button" size="sm" disabled={!selectedVisibility || busy} onClick={() => setIntent({ kind: 'MEDIA', title: `Aprovar ${formatEnum(item.tipo).toLowerCase()}`, media: item, action: 'APROVAR', visibility: selectedVisibility, requiresReason: false })}><CheckCircle2 className="mr-2 h-4 w-4" />Aprovar</Button>
                        <Button type="button" size="sm" variant="destructive" disabled={busy} onClick={() => setIntent({ kind: 'MEDIA', title: `Rejeitar ${formatEnum(item.tipo).toLowerCase()}`, media: item, action: 'REPROVAR', requiresReason: true })}><XCircle className="mr-2 h-4 w-4" />Rejeitar</Button>
                      </div>
                    ) : null}
                  </div>
                </article>
              )
            })}
          </div>
        )}
      </section>

      <section aria-labelledby="historico-moderacao" className="border-t border-zinc-200 pt-6">
        <h2 id="historico-moderacao" className="text-lg font-semibold text-zinc-950">Histórico de moderação</h2>
        {!canReadHistory ? (
          <p className="mt-3 text-sm font-medium text-amber-700">Seu perfil não possui permissão para consultar o histórico de moderação.</p>
        ) : history.length === 0 ? (
          <p className="mt-3 text-sm text-zinc-600">Nenhuma decisão de moderação registrada.</p>
        ) : (
          <ol className="mt-4 divide-y divide-zinc-200 border-y border-zinc-200">
            {history.map((item) => (
              <li key={item.id} className="grid gap-2 py-4 sm:grid-cols-[1fr_auto]">
                <div>
                  <p className="text-sm font-semibold text-zinc-900">{formatEnum(item.decisao || item.acao)}</p>
                  <p className="mt-1 text-xs text-zinc-600">{formatEnum(item.alvoTipo)} · {item.status ? formatEnum(item.status) : 'sem mudança de estado'}</p>
                  {item.motivo ? <p className="mt-2 text-sm text-zinc-700">{item.motivo}</p> : null}
                </div>
                <div className="text-left text-xs text-zinc-500 sm:text-right">
                  <p>{formatDate(item.criadoEm)}</p>
                  <p className="mt-1">Ator {item.atorId?.slice(0, 8) || 'não identificado'}</p>
                  <p className="mt-1">Request {item.requestId?.slice(0, 16) || 'não informado'}</p>
                </div>
              </li>
            ))}
          </ol>
        )}
      </section>

      <DecisionDialog intent={intent} busy={busy} error={actionError} onClose={() => { setIntent(null); setActionError(null) }} onConfirm={(reason) => void confirmDecision(reason)} />
    </div>
  )
}
