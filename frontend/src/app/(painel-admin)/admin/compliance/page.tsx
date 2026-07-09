'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import {
  formatarClassificacaoConteudo,
  formatarStatusVerificacaoAnunciante,
} from '@/lib/compliance/content-classification'

type Dashboard = {
  restrictedContentCount?: number
  auditLogCount?: number
  visitorEventCount?: number
  criticalVisitorEvents?: number
  activeVisitorTokens?: number
  flaggedRiskSessions?: number
}

type RestrictedContent = {
  anuncioId: number
  titulo: string
  slug: string
  classification: string
  status: string
  usuarioId: number
  username: string
  advertiserVerificationStatus: string
  criadoEm: string
}

type AuditLog = {
  id: number
  actionType: string
  entityType: string
  entityId: string
  actorEmail: string
  ipMasked: string
  details: string
  createdAt: string
}

type VisitorEvent = {
  id: number
  eventType: string
  route: string
  authorizationStatus: string
  challengeLevel?: string
  challengeResult?: string
  contentClassification?: string
  riskScore?: number
  decision?: string
  reasonCode?: string
  verificationResult: string
  reason: string
  tokenScope?: string
  ipMasked: string
  createdAt: string
}

type RiskProfile = {
  id: number
  visitorSessionId: string
  currentScore: number
  currentDecision: string
  internalReputation: number
  consecutiveFailures: number
  restrictedAccessCount: number
  explicitAccessCount: number
  flaggedForReview: boolean
  tempBlockedUntil?: string | null
  hardBlockedUntil?: string | null
  lastReasonCode?: string | null
  lastRoute?: string | null
  lastAnuncioId?: number | null
  lastSeenAt?: string | null
}

type CriticalEvent = {
  id: number
  eventType: string
  decision?: string | null
  reasonCode?: string | null
  riskScore?: number | null
  visitorSessionId?: string | null
  route?: string | null
  anuncioId?: number | null
  contentClassification?: string | null
  challengeLevel?: string | null
  challengeResult?: string | null
  authorizationStatus?: string | null
  createdAt: string
}

type VisitorDocSubmission = {
  id: number
  submissionId: string
  visitorSessionId: string
  challengeId?: string | null
  anuncioId?: number | null
  effectiveLevelSnapshot?: string | null
  contentType?: string
  byteSize?: number
  fileSha256?: string
  status: string
  reviewNotes?: string | null
  reviewedAt?: string | null
  reviewedByEmail?: string | null
  createdAt: string
  signedViewUrl?: string | null
}

type AceiteJuridico = {
  id: number
  usuarioId?: number | null
  username?: string | null
  email?: string | null
  tipoTermo: string
  contentKey?: string | null
  termoVersao: number
  termoHash: string
  tituloDocumento: string
  accepted: boolean
  acceptedAt: string
  ipMasked?: string | null
  userAgentHash?: string | null
  source: string
  originPath?: string | null
}

type AceitesPage = {
  content: AceiteJuridico[]
  totalPages: number
  totalElements: number
  number: number
  size: number
}

type Settings = {
  visitorLightTokenTtlMinutes: number
  visitorStrongTokenTtlMinutes: number
  visitorReinforcedTokenTtlMinutes: number
  visitorExplicitTokenTtlMinutes: number
  visitorRevalidationHours: number
  blurStrength: number
  verificationRateLimitPerHour: number
  riskWindowMinutes: number
  restrictedBurstWindowMinutes: number
  tempBlockMinutes: number
  maxAttemptsPerWindow: number
  maxFailuresPerWindow: number
  maxExplicitAccessPerWindow: number
  maxRestrictedBurstAccesses: number
  reviewFlagScore: number
  riskRequireLevel2Score: number
  riskRequireLevel3Score: number
  riskTempBlockScore: number
  riskHardBlockScore: number
  logRetentionDays: number
  enableVisitorVerification: boolean
  enableRiskEngine: boolean
  enableStrongVerification: boolean
  enableDocumentOnDemand: boolean
  bindVisitorTokenToIp: boolean
  bindVisitorTokenToUserAgent: boolean
  requireLightVerificationForNonExplicit: boolean
  requireAdvertiserApprovalForRestricted: boolean
  ageVerificationPolicy: string
  adultPrivacyPolicy: string
  restrictedContentTerms: string
  legalAccessNotice: string
}

type ComplianceFetchResult<T> =
  | { ok: true; data: T }
  | { ok: false; label: string; message: string }

export default function AdminCompliancePage() {
  const router = useRouter()
  const API = (process.env.NEXT_PUBLIC_API_URL || '').replace(/\/$/, '')
  const [dashboard, setDashboard] = useState<Dashboard>({})
  const [restrictedContent, setRestrictedContent] = useState<RestrictedContent[]>([])
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([])
  const [visitorEvents, setVisitorEvents] = useState<VisitorEvent[]>([])
  const [riskProfiles, setRiskProfiles] = useState<RiskProfile[]>([])
  const [criticalEvents, setCriticalEvents] = useState<CriticalEvent[]>([])
  const [visitorDocSubmissions, setVisitorDocSubmissions] = useState<VisitorDocSubmission[]>([])
  const [legalAcceptances, setLegalAcceptances] = useState<AceitesPage>({
    content: [],
    totalPages: 0,
    totalElements: 0,
    number: 0,
    size: 20,
  })
  const [legalAcceptanceFilters, setLegalAcceptanceFilters] = useState({
    email: '',
    tipoTermo: '',
    source: '',
    from: '',
    to: '',
    page: 0,
  })
  const [settings, setSettings] = useState<Settings | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadErrors, setLoadErrors] = useState<string[]>([])
  const [dashboardLoaded, setDashboardLoaded] = useState(false)

  const fetchJson = async <T,>(path: string, label: string): Promise<ComplianceFetchResult<T>> => {
    const controller = new AbortController()
    const timeout = window.setTimeout(() => controller.abort(), 15000)

    try {
      const res = await fetch(`${API}${path}`, {
        credentials: 'include',
        signal: controller.signal,
      })

      if (!res.ok) {
        return { ok: false, label, message: `${label}: HTTP ${res.status}` }
      }

      return { ok: true, data: await res.json() as T }
    } catch (error) {
      const message = error instanceof Error && error.name === 'AbortError'
        ? `${label}: tempo limite ao carregar`
        : `${label}: falha ao carregar`

      return { ok: false, label, message }
    } finally {
      window.clearTimeout(timeout)
    }
  }

  const fetchAll = async () => {
    if (!API) {
      setLoadErrors(['NEXT_PUBLIC_API_URL nao configurado para o painel administrativo.'])
      setLoading(false)
      return
    }

    setLoading(true)
    setLoadErrors([])

    try {
      const [
        dashboardRes,
        restrictedRes,
        logsRes,
        visitorRes,
        visitorDocRes,
        riskRes,
        criticalRes,
        settingsRes,
      ] = await Promise.all([
        fetchJson<Dashboard>('/admin/compliance/dashboard', 'Resumo'),
        fetchJson<RestrictedContent[]>('/admin/compliance/restricted-content', 'Conteudo restrito'),
        fetchJson<AuditLog[]>('/admin/compliance/audit-logs', 'Logs administrativos'),
        fetchJson<VisitorEvent[]>('/admin/compliance/visitor-events', 'Eventos de visitantes'),
        fetchJson<VisitorDocSubmission[]>('/admin/compliance/visitor-document-submissions', 'Documentos de visitantes'),
        fetchJson<RiskProfile[]>('/admin/compliance/visitor-risk', 'Risco de visitantes'),
        fetchJson<CriticalEvent[]>('/admin/compliance/critical-events', 'Eventos criticos'),
        fetchJson<Settings>('/admin/compliance/settings', 'Configuracoes'),
      ])

      const errors = [
        dashboardRes,
        restrictedRes,
        logsRes,
        visitorRes,
        visitorDocRes,
        riskRes,
        criticalRes,
        settingsRes,
      ]
        .filter((result): result is { ok: false; label: string; message: string } => !result.ok)
        .map((result) => result.message)

      if (dashboardRes.ok) setDashboard(dashboardRes.data)
      setDashboardLoaded(dashboardRes.ok)
      if (restrictedRes.ok) setRestrictedContent(Array.isArray(restrictedRes.data) ? restrictedRes.data : [])
      if (logsRes.ok) setAuditLogs(Array.isArray(logsRes.data) ? logsRes.data : [])
      if (visitorRes.ok) setVisitorEvents(Array.isArray(visitorRes.data) ? visitorRes.data : [])
      if (visitorDocRes.ok) setVisitorDocSubmissions(Array.isArray(visitorDocRes.data) ? visitorDocRes.data : [])
      if (riskRes.ok) setRiskProfiles(Array.isArray(riskRes.data) ? riskRes.data : [])
      if (criticalRes.ok) setCriticalEvents(Array.isArray(criticalRes.data) ? criticalRes.data : [])
      if (settingsRes.ok) setSettings(settingsRes.data)

      if (errors.length > 0) {
        setLoadErrors(errors)
        toast.error('Algumas informacoes de compliance nao foram carregadas.')
      }

      void fetchLegalAcceptances(0)
    } catch {
      toast.error('Nao foi possivel carregar o painel de compliance.')
    } finally {
      setLoading(false)
    }
  }

  const fetchLegalAcceptances = async (
    page = legalAcceptanceFilters.page,
    overrides?: Partial<typeof legalAcceptanceFilters>
  ) => {
    if (!API) return

    const filtros = {
      ...legalAcceptanceFilters,
      ...overrides,
      page,
    }

    const params = new URLSearchParams()
    if (filtros.email) params.set('email', filtros.email)
    if (filtros.tipoTermo) params.set('tipoTermo', filtros.tipoTermo)
    if (filtros.source) params.set('source', filtros.source)
    if (filtros.from) params.set('from', filtros.from)
    if (filtros.to) params.set('to', filtros.to)
    params.set('page', String(page))
    params.set('size', '20')

    try {
      const res = await fetch(`${API}/admin/compliance/legal-acceptances?${params.toString()}`, {
        credentials: 'include',
      })

      if (!res.ok) throw new Error(await res.text())

      const data = await res.json()
      setLegalAcceptances({
        content: Array.isArray(data?.content) ? data.content : [],
        totalPages: Number(data?.totalPages ?? 0),
        totalElements: Number(data?.totalElements ?? 0),
        number: Number(data?.number ?? page),
        size: Number(data?.size ?? 20),
      })
      setLegalAcceptanceFilters(filtros)
    } catch (err: any) {
      toast.error(err?.message || 'Nao foi possivel carregar os aceites juridicos.')
    }
  }

  useEffect(() => {
    fetchAll()
  }, [API])

  const saveSettings = async () => {
    if (!API || !settings) return
    try {
      const res = await fetch(`${API}/admin/compliance/settings`, {
        method: 'PUT',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(settings),
      })
      if (!res.ok) throw new Error(await res.text())
      toast.success('Configuracoes de compliance salvas.')
      fetchAll()
    } catch (err: any) {
      toast.error(err?.message || 'Nao foi possivel salvar as configuracoes.')
    }
  }

  const reviewVisitorDocument = async (submissionId: string, status: 'APPROVED' | 'REJECTED') => {
    if (!API) return
    const notes = window.prompt('Notas da análise (opcional):') ?? ''
    try {
      const res = await fetch(
        `${API}/admin/compliance/visitor-document-submissions/${encodeURIComponent(submissionId)}`,
        {
          method: 'PATCH',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ status, notes: notes.trim() || null }),
        }
      )
      if (!res.ok) throw new Error(await res.text())
      toast.success('Análise registrada.')
      fetchAll()
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Falha ao registrar análise.'
      toast.error(msg)
    }
  }

  const exportCsv = async () => {
    if (!API) return
    try {
      const res = await fetch(`${API}/admin/compliance/audit-logs/export.csv`, {
        credentials: 'include',
      })
      if (!res.ok) throw new Error(await res.text())
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = 'compliance-audit-logs.csv'
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (err: any) {
      toast.error(err?.message || 'Nao foi possivel exportar o CSV.')
    }
  }

  const hasLoadError = (label: string) => loadErrors.some((error) => error.startsWith(`${label}:`))

  if (loading && !settings) {
    return <div className="py-16 text-center text-gray-500">Carregando painel de compliance...</div>
  }

  return (
    <section className="space-y-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold text-gray-800">Compliance e Conteudo Restrito</h1>
        <p className="text-sm text-gray-500">
          Governanca operacional para verificacoes, classificacao de conteudo, auditoria e politicas.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-4 xl:grid-cols-8">
        {[
          ['Conteudos restritos', dashboard.restrictedContentCount ?? 0],
          ['Logs administrativos', dashboard.auditLogCount ?? 0],
          ['Eventos de visitantes', dashboard.visitorEventCount ?? 0],
          ['Eventos criticos', dashboard.criticalVisitorEvents ?? 0],
          ['Tokens ativos', dashboard.activeVisitorTokens ?? 0],
          ['Sessoes sinalizadas', dashboard.flaggedRiskSessions ?? 0],
        ].map(([label, value]) => (
          <div key={String(label)} className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
            <p className="text-sm text-gray-500">{label}</p>
            <p className="mt-2 text-3xl font-bold text-gray-900">{dashboardLoaded ? String(value) : 'Erro'}</p>
          </div>
        ))}
      </div>

      {loadErrors.length > 0 && (
        <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
          <p className="font-semibold">Algumas informacoes nao foram carregadas.</p>
          <ul className="mt-2 list-disc space-y-1 pl-5">
            {loadErrors.map((error) => (
              <li key={error}>{error}</li>
            ))}
          </ul>
        </div>
      )}

      <div id="restricted-content" className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
        <h2 className="text-lg font-semibold text-gray-900">Conteudo classificado como restrito</h2>
        <p className="mb-4 text-sm text-gray-500">
          Fila de triagem. A classificacao, aprovacao e reprovacao acontecem no detalhe do anuncio.
        </p>

        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="border-b text-left text-gray-500">
                <th className="py-3 pr-4">Anuncio</th>
                <th className="py-3 pr-4">Classificacao</th>
                <th className="py-3 pr-4">Status</th>
                <th className="py-3 pr-4">Anunciante</th>
                <th className="py-3">Moderacao</th>
              </tr>
            </thead>
            <tbody>
              {restrictedContent.map((item) => (
                <tr key={item.anuncioId} className="border-b">
                  <td className="py-3 pr-4">
                    <div className="font-medium text-gray-900">{item.titulo}</div>
                    <div className="text-xs text-gray-500">/{item.slug}</div>
                  </td>
                  <td className="py-3 pr-4">{formatarClassificacaoConteudo(item.classification)}</td>
                  <td className="py-3 pr-4">{item.status}</td>
                  <td className="py-3 pr-4">
                    <div>{item.username}</div>
                    <div className="text-xs text-gray-500">
                      {formatarStatusVerificacaoAnunciante(item.advertiserVerificationStatus)}
                    </div>
                  </td>
                  <td className="py-3">
                    <Button size="sm" variant="outline" onClick={() => router.push(`/admin/moderacao-v2/${item.anuncioId}`)}>
                      Abrir moderacao
                    </Button>
                  </td>
                </tr>
              ))}
              {restrictedContent.length === 0 && (
                <tr>
                  <td className="py-4 text-gray-500" colSpan={5}>
                    {hasLoadError('Conteudo restrito')
                      ? 'Erro ao carregar conteudo restrito.'
                      : 'Nenhum conteudo restrito identificado.'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div id="admin-logs" className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
        <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-lg font-semibold text-gray-900">Auditoria administrativa</h2>
            <p className="text-sm text-gray-500">Rastro estruturado das acoes de governanca e revisao.</p>
          </div>

          <Button onClick={exportCsv} className="bg-[#FC1EAD] text-white hover:bg-[#e01a9a]">
            Exportar CSV
          </Button>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="border-b text-left text-gray-500">
                <th className="py-3 pr-4">Acao</th>
                <th className="py-3 pr-4">Entidade</th>
                <th className="py-3 pr-4">Responsavel</th>
                <th className="py-3 pr-4">IP</th>
                <th className="py-3 pr-4">Data</th>
                <th className="py-3">Detalhes</th>
              </tr>
            </thead>
            <tbody>
              {auditLogs.slice(0, 20).map((log) => (
                <tr key={log.id} className="border-b align-top">
                  <td className="py-3 pr-4">{log.actionType}</td>
                  <td className="py-3 pr-4">
                    {log.entityType}
                    {log.entityId ? ` #${log.entityId}` : ''}
                  </td>
                  <td className="py-3 pr-4">{log.actorEmail || '-'}</td>
                  <td className="py-3 pr-4">{log.ipMasked || '-'}</td>
                  <td className="py-3 pr-4">{new Date(log.createdAt).toLocaleString('pt-BR')}</td>
                  <td className="py-3 text-gray-600">{log.details}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div id="visitor-logs" className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
        <h2 className="text-lg font-semibold text-gray-900">Eventos de verificacao de visitantes</h2>
        <p className="mb-4 text-sm text-gray-500">Trilha minima de acesso, bloqueio, expiracao e revogacao.</p>

        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="border-b text-left text-gray-500">
                <th className="py-3 pr-4">Evento</th>
                <th className="py-3 pr-4">Rota</th>
                <th className="py-3 pr-4">Status</th>
                <th className="py-3 pr-4">Resultado</th>
                <th className="py-3 pr-4">IP</th>
                <th className="py-3">Data</th>
              </tr>
            </thead>
            <tbody>
              {visitorEvents.slice(0, 20).map((event) => (
                <tr key={event.id} className="border-b">
                  <td className="py-3 pr-4">{event.eventType}</td>
                  <td className="py-3 pr-4 text-gray-600">{event.route || '-'}</td>
                  <td className="py-3 pr-4">{event.authorizationStatus || '-'}</td>
                  <td className="py-3 pr-4">
                    {event.decision || event.verificationResult || event.reason || '-'}
                    {typeof event.riskScore === 'number' ? ` • score ${event.riskScore}` : ''}
                  </td>
                  <td className="py-3 pr-4">{event.ipMasked || '-'}</td>
                  <td className="py-3">{new Date(event.createdAt).toLocaleString('pt-BR')}</td>
                </tr>
              ))}
              {visitorEvents.length === 0 && (
                <tr>
                  <td className="py-4 text-gray-500" colSpan={6}>
                    {hasLoadError('Eventos de visitantes')
                      ? 'Erro ao carregar eventos de visitantes.'
                      : 'Nenhum evento de visitante registrado.'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div id="visitor-document-fallback" className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
        <h2 className="text-lg font-semibold text-gray-900">Fallback documental (age gate)</h2>
        <p className="mb-4 text-sm text-gray-500">
          Envios para análise manual quando a verificação automática ou o risco exigem revisão humana. Arquivo fica no
          armazenamento privado; use o link temporário para visualizar.
        </p>

        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="border-b text-left text-gray-500">
                <th className="py-3 pr-4">ID</th>
                <th className="py-3 pr-4">Sessão</th>
                <th className="py-3 pr-4">Anúncio</th>
                <th className="py-3 pr-4">Nível</th>
                <th className="py-3 pr-4">Status</th>
                <th className="py-3 pr-4">Arquivo</th>
                <th className="py-3 pr-4">Data</th>
                <th className="py-3">Ações</th>
              </tr>
            </thead>
            <tbody>
              {visitorDocSubmissions.slice(0, 50).map((row) => (
                <tr key={row.submissionId} className="border-b align-top">
                  <td className="py-3 pr-4 font-mono text-xs">{row.submissionId.slice(0, 8)}…</td>
                  <td className="py-3 pr-4 font-mono text-xs text-gray-700">{row.visitorSessionId}</td>
                  <td className="py-3 pr-4">{row.anuncioId ?? '—'}</td>
                  <td className="py-3 pr-4">{row.effectiveLevelSnapshot ?? '—'}</td>
                  <td className="py-3 pr-4">{row.status}</td>
                  <td className="py-3 pr-4">
                    {row.signedViewUrl ? (
                      <a
                        href={row.signedViewUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="text-pink-600 underline"
                      >
                        Abrir
                      </a>
                    ) : (
                      '—'
                    )}
                  </td>
                  <td className="py-3 pr-4">{new Date(row.createdAt).toLocaleString('pt-BR')}</td>
                  <td className="py-3">
                    {row.status === 'PENDING' ? (
                      <div className="flex flex-wrap gap-2">
                        <Button
                          type="button"
                          size="sm"
                          className="h-8"
                          onClick={() => void reviewVisitorDocument(row.submissionId, 'APPROVED')}
                        >
                          Aprovar
                        </Button>
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          className="h-8"
                          onClick={() => void reviewVisitorDocument(row.submissionId, 'REJECTED')}
                        >
                          Rejeitar
                        </Button>
                      </div>
                    ) : (
                      <span className="text-gray-500">
                        {row.reviewedByEmail ?? ''}
                        {row.reviewNotes ? ` — ${row.reviewNotes}` : ''}
                      </span>
                    )}
                  </td>
                </tr>
              ))}
              {visitorDocSubmissions.length === 0 && (
                <tr>
                  <td className="py-4 text-gray-500" colSpan={8}>
                    Nenhum envio documental registrado.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div id="visitor-risk" className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
        <h2 className="text-lg font-semibold text-gray-900">Visao de risco por sessao</h2>
        <p className="mb-4 text-sm text-gray-500">Score atual, decisao vigente, bloqueios e trilha resumida.</p>

        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="border-b text-left text-gray-500">
                <th className="py-3 pr-4">Sessao</th>
                <th className="py-3 pr-4">Score</th>
                <th className="py-3 pr-4">Decisao</th>
                <th className="py-3 pr-4">Falhas</th>
                <th className="py-3 pr-4">Explicito</th>
                <th className="py-3 pr-4">Ultimo motivo</th>
                <th className="py-3">Ultima atividade</th>
              </tr>
            </thead>
            <tbody>
              {riskProfiles.slice(0, 20).map((profile) => (
                <tr key={profile.id} className="border-b align-top">
                  <td className="py-3 pr-4 font-mono text-xs text-gray-700">{profile.visitorSessionId}</td>
                  <td className="py-3 pr-4 font-semibold text-gray-900">{profile.currentScore}</td>
                  <td className="py-3 pr-4">
                    {profile.currentDecision}
                    {profile.flaggedForReview ? ' • FLAG' : ''}
                  </td>
                  <td className="py-3 pr-4">{profile.consecutiveFailures}</td>
                  <td className="py-3 pr-4">{profile.explicitAccessCount}</td>
                  <td className="py-3 pr-4 text-gray-600">{profile.lastReasonCode || '-'}</td>
                  <td className="py-3">
                    {profile.lastSeenAt ? new Date(profile.lastSeenAt).toLocaleString('pt-BR') : '-'}
                  </td>
                </tr>
              ))}
              {riskProfiles.length === 0 && (
                <tr>
                  <td className="py-4 text-gray-500" colSpan={7}>
                    Nenhuma sessao de risco registrada.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div id="critical-events" className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
        <h2 className="text-lg font-semibold text-gray-900">Fila de eventos criticos</h2>
        <p className="mb-4 text-sm text-gray-500">Escalonamentos, bloqueios temporarios e sessoes sinalizadas.</p>

        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="border-b text-left text-gray-500">
                <th className="py-3 pr-4">Evento</th>
                <th className="py-3 pr-4">Sessao</th>
                <th className="py-3 pr-4">Score</th>
                <th className="py-3 pr-4">Decisao</th>
                <th className="py-3 pr-4">Motivo</th>
                <th className="py-3">Data</th>
              </tr>
            </thead>
            <tbody>
              {criticalEvents.slice(0, 20).map((event) => (
                <tr key={event.id} className="border-b">
                  <td className="py-3 pr-4">{event.eventType}</td>
                  <td className="py-3 pr-4 font-mono text-xs text-gray-700">{event.visitorSessionId || '-'}</td>
                  <td className="py-3 pr-4">{event.riskScore ?? '-'}</td>
                  <td className="py-3 pr-4">{event.decision || event.authorizationStatus || '-'}</td>
                  <td className="py-3 pr-4 text-gray-600">{event.reasonCode || event.contentClassification || '-'}</td>
                  <td className="py-3">{new Date(event.createdAt).toLocaleString('pt-BR')}</td>
                </tr>
              ))}
              {criticalEvents.length === 0 && (
                <tr>
                  <td className="py-4 text-gray-500" colSpan={6}>
                    {hasLoadError('Eventos criticos')
                      ? 'Erro ao carregar eventos criticos.'
                      : 'Nenhum evento critico no momento.'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div id="legal-acceptances" className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
        <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h2 className="text-lg font-semibold text-gray-900">Aceites juridicos</h2>
            <p className="text-sm text-gray-500">
              Historico auditavel de termos, privacidade e consentimento promocional.
            </p>
          </div>

          <div className="grid grid-cols-1 gap-3 md:grid-cols-5">
            <Input
              placeholder="Filtrar por e-mail"
              value={legalAcceptanceFilters.email}
              onChange={(e) => setLegalAcceptanceFilters((prev) => ({ ...prev, email: e.target.value }))}
            />
            <Input
              placeholder="Tipo do termo"
              value={legalAcceptanceFilters.tipoTermo}
              onChange={(e) => setLegalAcceptanceFilters((prev) => ({ ...prev, tipoTermo: e.target.value, page: 0 }))}
            />
            <Input
              placeholder="Origem"
              value={legalAcceptanceFilters.source}
              onChange={(e) => setLegalAcceptanceFilters((prev) => ({ ...prev, source: e.target.value, page: 0 }))}
            />
            <Input
              type="date"
              value={legalAcceptanceFilters.from}
              onChange={(e) => setLegalAcceptanceFilters((prev) => ({ ...prev, from: e.target.value, page: 0 }))}
            />
            <Input
              type="date"
              value={legalAcceptanceFilters.to}
              onChange={(e) => setLegalAcceptanceFilters((prev) => ({ ...prev, to: e.target.value, page: 0 }))}
            />
          </div>
        </div>

        <div className="mb-4 flex gap-2">
          <Button onClick={() => fetchLegalAcceptances(0)} className="bg-[#FC1EAD] text-white hover:bg-[#e01a9a]">
            Aplicar filtros
          </Button>
          <Button
            variant="outline"
            onClick={() => {
              const filtrosLimpos = {
                email: '',
                tipoTermo: '',
                source: '',
                from: '',
                to: '',
                page: 0,
              }
              setLegalAcceptanceFilters(filtrosLimpos)
              fetchLegalAcceptances(0, filtrosLimpos)
            }}
          >
            Limpar
          </Button>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="border-b text-left text-gray-500">
                <th className="py-3 pr-4">Usuario</th>
                <th className="py-3 pr-4">Documento</th>
                <th className="py-3 pr-4">Versao</th>
                <th className="py-3 pr-4">Status</th>
                <th className="py-3 pr-4">Origem</th>
                <th className="py-3 pr-4">IP</th>
                <th className="py-3 pr-4">User-agent</th>
                <th className="py-3">Data</th>
              </tr>
            </thead>
            <tbody>
              {legalAcceptances.content.map((item) => (
                <tr key={item.id} className="border-b align-top">
                  <td className="py-3 pr-4">
                    <div className="font-medium text-gray-900">{item.username || 'Sem usuario'}</div>
                    <div className="text-xs text-gray-500">{item.email || 'Nao vinculado'}</div>
                  </td>
                  <td className="py-3 pr-4">
                    <div className="font-medium text-gray-900">{item.tituloDocumento}</div>
                    <div className="text-xs text-gray-500">{item.tipoTermo}</div>
                    <div className="text-xs text-gray-400">{item.termoHash.slice(0, 16)}...</div>
                  </td>
                  <td className="py-3 pr-4">{item.termoVersao}</td>
                  <td className="py-3 pr-4">{item.accepted ? 'Aceito' : 'Nao aceito'}</td>
                  <td className="py-3 pr-4">
                    <div>{item.source}</div>
                    <div className="text-xs text-gray-500">{item.originPath || '-'}</div>
                  </td>
                  <td className="py-3 pr-4">{item.ipMasked || '-'}</td>
                  <td className="py-3 pr-4 text-xs text-gray-500">{item.userAgentHash?.slice(0, 16) || '-'}</td>
                  <td className="py-3">{new Date(item.acceptedAt).toLocaleString('pt-BR')}</td>
                </tr>
              ))}
              {legalAcceptances.content.length === 0 && (
                <tr>
                  <td colSpan={8} className="py-6 text-center text-gray-500">
                    Nenhum aceite juridico encontrado para os filtros aplicados.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="mt-4 flex items-center justify-between text-sm text-gray-500">
          <span>Total de registros: {legalAcceptances.totalElements}</span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              disabled={legalAcceptances.number <= 0}
              onClick={() => fetchLegalAcceptances(Math.max(legalAcceptances.number - 1, 0))}
            >
              Anterior
            </Button>
            <Button
              variant="outline"
              disabled={legalAcceptances.number + 1 >= legalAcceptances.totalPages}
              onClick={() => fetchLegalAcceptances(legalAcceptances.number + 1)}
            >
              Proxima
            </Button>
          </div>
        </div>
      </div>

      {settings && (
        <div id="settings" className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
          <div className="mb-4 flex items-center justify-between">
            <div>
              <h2 className="text-lg font-semibold text-gray-900">Configuracoes e politicas</h2>
              <p className="text-sm text-gray-500">Parametros de expiracao, blur, politicas e retenção.</p>
            </div>

            <Button onClick={saveSettings} className="bg-[#FC1EAD] text-white hover:bg-[#e01a9a]">
              Salvar configuracoes
            </Button>
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <div className="space-y-2">
              <label className="text-sm font-medium text-gray-700">TTL visitante leve (min)</label>
              <Input
                type="number"
                value={settings.visitorLightTokenTtlMinutes}
                onChange={(e) =>
                  setSettings((prev) => prev ? { ...prev, visitorLightTokenTtlMinutes: Number(e.target.value) } : prev)
                }
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-gray-700">TTL visitante reforcado (min)</label>
              <Input
                type="number"
                value={settings.visitorReinforcedTokenTtlMinutes}
                onChange={(e) =>
                  setSettings((prev) => prev ? { ...prev, visitorReinforcedTokenTtlMinutes: Number(e.target.value) } : prev)
                }
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-gray-700">TTL verificacao forte (min)</label>
              <Input
                type="number"
                value={settings.visitorStrongTokenTtlMinutes}
                onChange={(e) =>
                  setSettings((prev) => prev ? { ...prev, visitorStrongTokenTtlMinutes: Number(e.target.value) } : prev)
                }
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-gray-700">TTL token explicito (min)</label>
              <Input
                type="number"
                value={settings.visitorExplicitTokenTtlMinutes}
                onChange={(e) =>
                  setSettings((prev) => prev ? { ...prev, visitorExplicitTokenTtlMinutes: Number(e.target.value) } : prev)
                }
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-gray-700">Forca do blur</label>
              <Input
                type="number"
                value={settings.blurStrength}
                onChange={(e) =>
                  setSettings((prev) => prev ? { ...prev, blurStrength: Number(e.target.value) } : prev)
                }
              />
            </div>
          </div>

          <div className="mt-5 grid grid-cols-1 gap-4 md:grid-cols-4">
            <div className="space-y-2">
              <label className="text-sm font-medium text-gray-700">Janela de risco (min)</label>
              <Input
                type="number"
                value={settings.riskWindowMinutes}
                onChange={(e) =>
                  setSettings((prev) => prev ? { ...prev, riskWindowMinutes: Number(e.target.value) } : prev)
                }
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-gray-700">Burst restrito (min)</label>
              <Input
                type="number"
                value={settings.restrictedBurstWindowMinutes}
                onChange={(e) =>
                  setSettings((prev) => prev ? { ...prev, restrictedBurstWindowMinutes: Number(e.target.value) } : prev)
                }
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-gray-700">Bloqueio temporario (min)</label>
              <Input
                type="number"
                value={settings.tempBlockMinutes}
                onChange={(e) =>
                  setSettings((prev) => prev ? { ...prev, tempBlockMinutes: Number(e.target.value) } : prev)
                }
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-gray-700">Rate limit por hora</label>
              <Input
                type="number"
                value={settings.verificationRateLimitPerHour}
                onChange={(e) =>
                  setSettings((prev) => prev ? { ...prev, verificationRateLimitPerHour: Number(e.target.value) } : prev)
                }
              />
            </div>
          </div>

          <div className="mt-5 grid grid-cols-1 gap-4 md:grid-cols-4">
            <div className="space-y-2">
              <label className="text-sm font-medium text-gray-700">Max tentativas</label>
              <Input
                type="number"
                value={settings.maxAttemptsPerWindow}
                onChange={(e) =>
                  setSettings((prev) => prev ? { ...prev, maxAttemptsPerWindow: Number(e.target.value) } : prev)
                }
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-gray-700">Max falhas</label>
              <Input
                type="number"
                value={settings.maxFailuresPerWindow}
                onChange={(e) =>
                  setSettings((prev) => prev ? { ...prev, maxFailuresPerWindow: Number(e.target.value) } : prev)
                }
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-gray-700">Max acesso explicito</label>
              <Input
                type="number"
                value={settings.maxExplicitAccessPerWindow}
                onChange={(e) =>
                  setSettings((prev) => prev ? { ...prev, maxExplicitAccessPerWindow: Number(e.target.value) } : prev)
                }
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-gray-700">Max burst restrito</label>
              <Input
                type="number"
                value={settings.maxRestrictedBurstAccesses}
                onChange={(e) =>
                  setSettings((prev) => prev ? { ...prev, maxRestrictedBurstAccesses: Number(e.target.value) } : prev)
                }
              />
            </div>
          </div>

          <div className="mt-5 grid grid-cols-1 gap-4 md:grid-cols-5">
            {[
              ['Score flag', 'reviewFlagScore'],
              ['Score nivel 2', 'riskRequireLevel2Score'],
              ['Score nivel 3', 'riskRequireLevel3Score'],
              ['Score temp block', 'riskTempBlockScore'],
              ['Score hard block', 'riskHardBlockScore'],
            ].map(([label, key]) => (
              <div key={String(key)} className="space-y-2">
                <label className="text-sm font-medium text-gray-700">{label}</label>
                <Input
                  type="number"
                  value={settings[key as keyof Settings] as number}
                  onChange={(e) =>
                    setSettings((prev) =>
                      prev ? { ...prev, [key]: Number(e.target.value) } as Settings : prev
                    )
                  }
                />
              </div>
            ))}
          </div>

          <div className="mt-5 grid grid-cols-1 gap-3 md:grid-cols-2">
            {[
              ['Ativar verificacao de visitante', 'enableVisitorVerification'],
              ['Ativar risk engine', 'enableRiskEngine'],
              ['Ativar verificacao forte', 'enableStrongVerification'],
              ['Ativar documento sob demanda', 'enableDocumentOnDemand'],
              ['Vincular token ao IP', 'bindVisitorTokenToIp'],
              ['Vincular token ao user-agent', 'bindVisitorTokenToUserAgent'],
              ['Exigir nivel leve para nao explicito', 'requireLightVerificationForNonExplicit'],
              ['Exigir aprovacao de anunciante', 'requireAdvertiserApprovalForRestricted'],
            ].map(([label, key]) => (
              <label key={String(key)} className="flex items-center gap-3 rounded-xl border border-gray-100 px-4 py-3 text-sm text-gray-700">
                <input
                  type="checkbox"
                  checked={Boolean(settings[key as keyof Settings])}
                  onChange={(e) =>
                    setSettings((prev) =>
                      prev ? { ...prev, [key]: e.target.checked } as Settings : prev
                    )
                  }
                  className="accent-pink-500"
                />
                <span>{label}</span>
              </label>
            ))}
          </div>

          <div className="mt-5 grid grid-cols-1 gap-4">
            <div className="space-y-2">
              <label className="text-sm font-medium text-gray-700">Politica de verificacao etaria</label>
              <Textarea
                rows={4}
                value={settings.ageVerificationPolicy}
                onChange={(e) =>
                  setSettings((prev) => prev ? { ...prev, ageVerificationPolicy: e.target.value } : prev)
                }
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-gray-700">Privacidade de conteudo adulto</label>
              <Textarea
                rows={4}
                value={settings.adultPrivacyPolicy}
                onChange={(e) =>
                  setSettings((prev) => prev ? { ...prev, adultPrivacyPolicy: e.target.value } : prev)
                }
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-gray-700">Termos de conteudo restrito</label>
              <Textarea
                rows={4}
                value={settings.restrictedContentTerms}
                onChange={(e) =>
                  setSettings((prev) => prev ? { ...prev, restrictedContentTerms: e.target.value } : prev)
                }
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-gray-700">Aviso legal de acesso</label>
              <Textarea
                rows={3}
                value={settings.legalAccessNotice}
                onChange={(e) =>
                  setSettings((prev) => prev ? { ...prev, legalAccessNotice: e.target.value } : prev)
                }
              />
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
