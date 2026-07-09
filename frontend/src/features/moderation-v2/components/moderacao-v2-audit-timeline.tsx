'use client'

import { useMemo } from 'react'
import { Badge } from '@/components/ui/badge'
import { corrigirTextoCorrompido } from '@/lib/text/encoding'
import type { AdminAuditLogItem, ModerationAnuncioDetail, VisitorVerificationAuditItem } from '../api/types'

function texto(v?: string | null, fb = '—') {
  const s = corrigirTextoCorrompido(v ?? '').trim()
  return s || fb
}

const AUDIT_DISPLAY_TZ = 'America/Sao_Paulo'

/** Aceita ISO com offset/Z, ISO local ambíguo, ou array Jackson [y,m,d,h?,min?,sec?] (legado). */
export function parseBackendDateTime(v: unknown): Date | null {
  if (v == null) return null
  if (typeof v === 'string') {
    const d = new Date(v)
    return Number.isNaN(d.getTime()) ? null : d
  }
  if (Array.isArray(v)) {
    const y = Number(v[0])
    const m = Number(v[1])
    const day = Number(v[2])
    if (!Number.isFinite(y) || !Number.isFinite(m) || !Number.isFinite(day)) return null
    const h = v.length > 3 ? Number(v[3]) : 0
    const min = v.length > 4 ? Number(v[4]) : 0
    const sec = v.length > 5 ? Number(v[5]) : 0
    const d = new Date(y, m - 1, day, h, min, sec)
    return Number.isNaN(d.getTime()) ? null : d
  }
  return null
}

function firstParsedDate(...candidates: unknown[]): Date | null {
  for (const c of candidates) {
    const d = parseBackendDateTime(c)
    if (d) return d
  }
  return null
}

/** Exibição alinhada ao fuso usado na API de auditoria (Brasil), independente do fuso do navegador. */
function formatLegalDateTime(d: Date): string {
  const parts = new Intl.DateTimeFormat('pt-BR', {
    timeZone: AUDIT_DISPLAY_TZ,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(d)
  const g = (t: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === t)?.value ?? ''
  return `${g('day')}/${g('month')}/${g('year')} ${g('hour')}:${g('minute')}:${g('second')}`
}

const AUDIT_ACTION_TITLE: Record<string, string> = {
  ANUNCIO_MOD_APPROVED: 'Aprovação na moderação',
  ANUNCIO_MOD_REJECTED: 'Reprovação na moderação',
  ANUNCIO_STAFF_EDITED: 'Edição pelo staff',
  CLASSIFICATION_CHANGED: 'Alteração de classificação de conteúdo',
  REVIEW_DECISION: 'Decisão de revisão / remoderação',
  REVIEW_STARTED: 'Revisão iniciada',
  PREMIUM_BENEFIT_GRANTED: 'Benefício premium concedido',
  PREMIUM_BENEFIT_REVOKED: 'Benefício premium revogado',
  ANUNCIO_STAFF_MEDIA_REMOVED: 'Remoção de mídia (staff)',
  ANUNCIO_STATUS_STAFF: 'Alteração de status do anúncio',
  ANUNCIO_REMOVED_LOGICAL: 'Remoção lógica do anúncio (staff)',
  ANUNCIO_REMOVIDO_PELO_USUARIO: 'Remoção lógica pelo próprio anunciante',
}

const VISITOR_EVENT_TITLE: Record<string, string> = {
  CHALLENGE_STARTED: 'Verificação etária — desafio iniciado',
  VERIFICATION_SUCCEEDED: 'Verificação etária — sucesso',
  VERIFICATION_FAILED: 'Verificação etária — falha',
  ACCESS_GRANTED: 'Acesso a conteúdo restringido concedido',
  ACCESS_BLOCKED: 'Acesso a conteúdo restringido bloqueado',
  TOKEN_REVOKED: 'Token de idade revogado',
  RISK_ESCALATED: 'Risco escalado',
  RISK_TEMP_BLOCKED: 'Bloqueio temporário por risco',
  RISK_HARD_BLOCKED: 'Bloqueio definitivo por risco',
  SESSION_FLAGGED: 'Sessão marcada para revisão',
}

type CompliancePair = { label: string; value: string }

type TimelineRow = {
  key: string
  sortMs: number
  category: string
  title: string
  actorLine: string
  detailLines: string[]
  compliance: CompliancePair[]
  techHint?: string
}

function complianceFromAuditLog(log: AdminAuditLogItem): CompliancePair[] {
  const out: CompliancePair[] = []
  if (log.ipMasked) out.push({ label: 'IP (mascarado)', value: log.ipMasked })
  if (log.userAgentHash) out.push({ label: 'User-Agent (hash)', value: log.userAgentHash })
  if (log.actorUserId != null) out.push({ label: 'ID usuário interno', value: String(log.actorUserId) })
  const em = texto(log.actorEmail, '')
  if (em && em !== '—') out.push({ label: 'Responsável (e-mail)', value: em })
  return out
}

function complianceFromVisitor(ev: VisitorVerificationAuditItem): CompliancePair[] {
  const out: CompliancePair[] = []
  if (ev.ipMasked) out.push({ label: 'IP (mascarado)', value: ev.ipMasked })
  if (ev.userAgentHash) out.push({ label: 'User-Agent (hash)', value: ev.userAgentHash })
  const sess = texto(ev.visitorSessionId, '')
  if (sess && sess !== '—') out.push({ label: 'Sessão visitante', value: sess })
  return out
}

function auditCategory(actionType: string): string {
  switch (actionType) {
    case 'ANUNCIO_MOD_APPROVED':
    case 'ANUNCIO_MOD_REJECTED':
      return 'Moderação'
    case 'CLASSIFICATION_CHANGED':
      return 'Classificação'
    case 'REVIEW_DECISION':
    case 'REVIEW_STARTED':
      return 'Remoderação / revisão'
    case 'ANUNCIO_STAFF_EDITED':
      return 'Edição'
    case 'PREMIUM_BENEFIT_GRANTED':
    case 'PREMIUM_BENEFIT_REVOKED':
      return 'Benefícios premium'
    case 'ANUNCIO_STAFF_MEDIA_REMOVED':
      return 'Mídia'
    case 'ANUNCIO_STATUS_STAFF':
    case 'ANUNCIO_REMOVED_LOGICAL':
    case 'ANUNCIO_REMOVIDO_PELO_USUARIO':
      return 'Status / retirada'
    default:
      return 'Administrativo'
  }
}

function buildRows(
  anuncio: ModerationAnuncioDetail,
  auditLogs: AdminAuditLogItem[],
  visitorEvents: VisitorVerificationAuditItem[]
): TimelineRow[] {
  const rows: TimelineRow[] = []

  const criacao = firstParsedDate(anuncio.dataCriacaoIso, anuncio.dataCriacao)
  if (criacao) {
    rows.push({
      key: `sys-criacao-${anuncio.id}`,
      sortMs: criacao.getTime(),
      category: 'Criação',
      title: 'Criação do anúncio',
      actorLine: anuncio.username ? `Anunciante: @${texto(anuncio.username)}` : '—',
      detailLines: [
        `Registro de cadastro conforme data armazenada no sistema (criadoEm / dataCriacao).`,
      ],
      compliance: [],
      techHint: undefined,
    })
  }

  for (const log of auditLogs) {
    const d = firstParsedDate(log.createdAtIso, log.createdAt) ?? new Date(0)
    const action = log.actionType ?? ''
    const title = AUDIT_ACTION_TITLE[action] ?? `Registro administrativo (${action})`
    const det = texto(log.details, '')
    rows.push({
      key: `audit-${log.id}`,
      sortMs: d.getTime(),
      category: auditCategory(action),
      title,
      actorLine: texto(log.actorEmail, 'Sistema / não informado'),
      detailLines: det && det !== '—' ? [det] : [],
      compliance: complianceFromAuditLog(log),
      techHint: action,
    })
  }

  for (const ev of visitorEvents) {
    const d = firstParsedDate(ev.createdAtIso, ev.createdAt) ?? new Date(0)
    const et = ev.eventType ?? ''
    const title = VISITOR_EVENT_TITLE[et] ?? `Evento de verificação (${et})`
    const lines: string[] = []
    const st = texto(ev.authorizationStatus, '')
    if (st && st !== '—') lines.push(`Status de autorização: ${st}`)
    const vr = texto(ev.verificationResult, '')
    if (vr && vr !== '—') lines.push(`Resultado da verificação: ${vr}`)
    const cl = texto(ev.challengeLevel, '')
    if (cl && cl !== '—') lines.push(`Método / nível do desafio: ${cl}`)
    const cr = texto(ev.challengeResult, '')
    if (cr && cr !== '—') lines.push(`Resultado do desafio: ${cr}`)
    const dec = texto(ev.decision, '')
    if (dec && dec !== '—') lines.push(`Decisão automática: ${dec}`)
    const reason = texto(ev.reason, '')
    if (reason && reason !== '—') lines.push(`Motivo: ${reason}`)
    const cc = texto(ev.contentClassification, '')
    if (cc && cc !== '—') lines.push(`Classificação de contexto: ${cc}`)
    const route = texto(ev.route, '')
    if (route && route !== '—') lines.push(`Rota: ${route}`)

    rows.push({
      key: `visitor-${ev.id}-${ev.eventId ?? ''}`,
      sortMs: d.getTime(),
      category: 'Verificação etária (visitante)',
      title,
      actorLine: 'Visitante (sessão) — ver detalhes técnicos',
      detailLines: lines,
      compliance: complianceFromVisitor(ev),
      techHint: et,
    })
  }

  rows.sort((a, b) => a.sortMs - b.sortMs)
  return rows
}

export type ModeracaoV2AuditTimelineProps = {
  anuncio: ModerationAnuncioDetail
  anuncioId: number
  auditLogs: AdminAuditLogItem[]
  visitorEvents: VisitorVerificationAuditItem[]
}

export function ModeracaoV2AuditTimeline({
  anuncio,
  anuncioId,
  auditLogs,
  visitorEvents,
}: ModeracaoV2AuditTimelineProps) {
  const rows = useMemo(
    () => buildRows(anuncio, auditLogs, visitorEvents),
    [anuncio, auditLogs, visitorEvents]
  )

  const views = anuncio.visualizacoes ?? 0
  const wa = anuncio.cliquesWhatsapp ?? 0

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-slate-200 bg-slate-50/80 px-4 py-3 text-sm text-slate-800">
        <p className="font-semibold text-slate-900">Métricas cumulativas (estado atual)</p>
        <p className="mt-1 tabular-nums text-slate-700">
          Visualizações registradas: <strong>{views}</strong>
          <span className="mx-2 text-slate-300">|</span>
          Cliques WhatsApp: <strong>{wa}</strong>
        </p>
        <p className="mt-2 text-xs text-slate-600">
          Linha do tempo do anúncio #{anuncioId}: <code className="text-[11px]">admin_audit_logs</code> (aprovação, classificação, mídia,
          benefícios premium quando registrados), eventos de verificação etária com este <code className="text-[11px]">anuncioId</code>, e o
          marco de criação. Lista “Histórico” na seção premium complementa benefícios. Ordem cronológica crescente. Horários
          exibidos em <span className="font-mono">America/Sao_Paulo</span> (Brasília); instantes ISO com offset vêm do backend
          quando disponíveis.
        </p>
      </div>

      {rows.length === 0 ? (
        <p className="text-sm text-gray-500">Nenhum evento para exibir nesta consolidação.</p>
      ) : (
        <div className="relative max-h-[min(36rem,70vh)] overflow-y-auto pr-1">
          <div className="absolute bottom-0 left-[15px] top-2 w-px bg-gray-200" aria-hidden />
          <ul className="relative space-y-3">
            {rows.map((row) => (
              <li key={row.key} className="flex gap-3 pl-1">
                <div
                  className="relative z-10 mt-1.5 h-3 w-3 shrink-0 rounded-full border-2 border-white bg-[#f0198f] shadow-sm"
                  aria-hidden
                />
                <div className="min-w-0 flex-1 rounded-xl border border-gray-100 bg-white px-4 py-3 shadow-sm">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge variant="outline" className="text-[10px] font-medium text-gray-600">
                          {row.category}
                        </Badge>
                        {row.techHint ? (
                          <span className="font-mono text-[10px] text-gray-400">{row.techHint}</span>
                        ) : null}
                      </div>
                      <h3 className="mt-1 text-sm font-semibold text-gray-900">{row.title}</h3>
                      <p className="text-xs text-gray-600">
                        <time dateTime={new Date(row.sortMs).toISOString()}>{formatLegalDateTime(new Date(row.sortMs))}</time>
                      </p>
                    </div>
                  </div>
                  <p className="mt-2 text-xs text-gray-700">
                    <span className="font-medium text-gray-800">Responsável / contexto:</span> {row.actorLine}
                  </p>
                  {row.detailLines.length > 0 ? (
                    <div className="mt-2 space-y-1">
                      {row.detailLines.map((line, j) => (
                        <p key={j} className="whitespace-pre-wrap text-[11px] leading-relaxed text-gray-600">
                          {line}
                        </p>
                      ))}
                    </div>
                  ) : null}
                  {row.compliance.length > 0 ? (
                    <div className="mt-3 rounded-lg border border-gray-100 bg-gray-50/90 px-3 py-2">
                      <p className="text-[10px] font-semibold uppercase tracking-wide text-gray-500">Dados de compliance</p>
                      <dl className="mt-1 space-y-1">
                        {row.compliance.map((c) => (
                          <div key={c.label} className="flex flex-col gap-0 sm:flex-row sm:gap-2">
                            <dt className="shrink-0 text-[10px] font-medium text-gray-500">{c.label}</dt>
                            <dd className="min-w-0 break-all font-mono text-[10px] text-gray-800">{c.value}</dd>
                          </div>
                        ))}
                      </dl>
                    </div>
                  ) : null}
                </div>
              </li>
            ))}
          </ul>
          <p className="mt-3 text-center text-[10px] text-gray-400">{rows.length} evento(s) · ordem cronológica</p>
        </div>
      )}
    </div>
  )
}
