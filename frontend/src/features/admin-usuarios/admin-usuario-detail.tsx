'use client'

import Link from 'next/link'
import { useParams, useSearchParams } from 'next/navigation'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { ArrowLeft, FileUp, LockKeyhole, Pencil, ShieldCheck, UnlockKeyhole, WalletCards } from 'lucide-react'

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
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { useAuth } from '@/context/AuthContext'
import { blockAdminAdAndUser, unblockAdminUser } from '@/features/admin-anuncios/api'
import type { AdminKycSubmission, AdminLegalBlockCategory } from '@/features/admin-anuncios/types'
import { AdminKycDocumentGrid } from '@/features/admin-documentos/admin-kyc-document-grid'
import { AdminKycUploadDialog } from '@/features/admin-documentos/admin-kyc-upload-dialog'
import { normalizeApiError } from '@/lib/api-contract'
import { maskPhoneBR } from '@/lib/phone-mask'

import { getAdminUser } from './api'
import { AdminUsuarioCreditDialog } from './admin-usuario-credit-dialog'
import type { AdminUserDetail } from './types'

const LEGAL_CATEGORIES: Array<{ value: AdminLegalBlockCategory; label: string }> = [
  { value: 'DENUNCIA_GRAVE', label: 'Denúncia grave' },
  { value: 'USO_NAO_AUTORIZADO_IMAGEM', label: 'Uso não autorizado de imagem' },
  { value: 'FRAUDE', label: 'Fraude' },
  { value: 'ORDEM_OU_RISCO_JURIDICO', label: 'Ordem ou risco jurídico' },
  { value: 'OUTRA_INTERVENCAO', label: 'Outra intervenção' },
]

function pretty(value: string) {
  return value.replaceAll('_', ' ').toLocaleLowerCase('pt-BR').replace(/^./, (letter) => letter.toUpperCase())
}

function formatDate(value?: string | null) {
  if (!value) return 'Não informado'
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

function statusTone(status: string) {
  if (status === 'ATIVO' || status === 'APROVADO' || status === 'PUBLICADO' || status === 'VALIDADO') {
    return 'border-emerald-300 bg-emerald-50 text-emerald-800'
  }
  if (status === 'SUSPENSO' || status === 'REJEITADO' || status === 'BLOQUEADO' || status === 'REMOVIDO') {
    return 'border-red-300 bg-red-50 text-red-800'
  }
  if (status === 'PENDENTE' || status === 'EM_ANALISE' || status === 'AJUSTE_SOLICITADO') {
    return 'border-amber-300 bg-amber-50 text-amber-800'
  }
  return 'border-zinc-300 bg-zinc-50 text-zinc-700'
}

type LegalIntent = 'BLOCK' | 'UNBLOCK' | null

export function AdminUsuarioDetail() {
  const params = useParams<{ id: string }>()
  const searchParams = useSearchParams()
  const { usuario } = useAuth()
  const [detail, setDetail] = useState<AdminUserDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [reload, setReload] = useState(0)
  const [legalIntent, setLegalIntent] = useState<LegalIntent>(null)
  const [legalCategory, setLegalCategory] = useState<AdminLegalBlockCategory>('OUTRA_INTERVENCAO')
  const [legalReason, setLegalReason] = useState('')
  const [legalBusy, setLegalBusy] = useState(false)
  const [legalError, setLegalError] = useState<unknown>(null)
  const [documentDialogOpen, setDocumentDialogOpen] = useState(false)
  const [documentReplacement, setDocumentReplacement] = useState<AdminKycSubmission | null>(null)
  const [creditDialogOpen, setCreditDialogOpen] = useState(false)
  const actionLock = useRef(false)
  const userId = params?.id ?? ''
  const admin = usuario?.cargo === 'ADMIN'

  const backHref = useMemo(() => {
    const retorno = searchParams.get('retorno')
    return retorno ? `/admin/usuarios?${retorno}` : '/admin/usuarios'
  }, [searchParams])

  const load = useCallback(async () => {
    if (!userId) return
    setLoading(true)
    setError(null)
    try {
      setDetail(await getAdminUser(userId))
    } catch (reason) {
      setError(reason)
    } finally {
      setLoading(false)
    }
  }, [userId])

  useEffect(() => {
    void load()
  }, [load, reload])

  async function confirmLegalAction() {
    if (!detail?.anuncioAncoraBloqueioId || !legalIntent || actionLock.current) return
    const reason = legalReason.trim()
    if (reason.length < 5) {
      setLegalError(new Error('Informe um motivo com pelo menos 5 caracteres.'))
      return
    }
    actionLock.current = true
    setLegalBusy(true)
    setLegalError(null)
    try {
      if (legalIntent === 'BLOCK') {
        await blockAdminAdAndUser(detail.anuncioAncoraBloqueioId, {
          categoria: legalCategory,
          motivo: reason,
          observacaoInterna: null,
        })
      } else {
        await unblockAdminUser(detail.anuncioAncoraBloqueioId, reason)
      }
      setLegalIntent(null)
      setLegalReason('')
      await load()
    } catch (reasonError) {
      setLegalError(normalizeApiError(reasonError))
    } finally {
      actionLock.current = false
      setLegalBusy(false)
    }
  }

  if (loading && !detail) return <p className="py-16 text-center text-sm text-zinc-500">Carregando usuário...</p>
  if (error && !detail) return <ContractState error={error} onRetry={() => setReload((value) => value + 1)} />
  if (!detail) return null

  const displayName = detail.nomeCivil || detail.nome || 'Usuário sem nome'

  return (
    <section className="space-y-6">
      <header className="border-b border-zinc-200 pb-4">
        <Link href={backHref} className="inline-flex items-center gap-2 text-sm font-semibold text-pink-700 hover:text-pink-800">
          <ArrowLeft className="h-4 w-4" /> Voltar aos usuários
        </Link>
        <div className="mt-3 flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <h1 className="text-2xl font-bold text-zinc-950">{displayName}</h1>
            <p className="mt-1 break-all font-mono text-xs text-zinc-500">{detail.id}</p>
            <div className="mt-2 flex flex-wrap gap-2">
              <Badge variant="outline" className={statusTone(detail.status)}>{pretty(detail.status)}</Badge>
              <Badge variant="outline" className={statusTone(detail.kycStatus)}>KYC {pretty(detail.kycStatus)}</Badge>
              {detail.bloqueado ? <Badge variant="destructive">Bloqueado</Badge> : null}
            </div>
          </div>
          {admin ? (
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="outline" onClick={() => setCreditDialogOpen(true)}>
                <WalletCards className="mr-2 h-4 w-4" /> Créditos
              </Button>
              {detail.podeBloquear ? (
                <Button type="button" variant="destructive" onClick={() => setLegalIntent('BLOCK')}>
                  <LockKeyhole className="mr-2 h-4 w-4" /> Bloquear usuário
                </Button>
              ) : null}
              {detail.podeDesbloquear ? (
                <Button type="button" variant="outline" onClick={() => setLegalIntent('UNBLOCK')}>
                  <UnlockKeyhole className="mr-2 h-4 w-4" /> Desbloquear usuário
                </Button>
              ) : null}
              <Button asChild type="button" variant="outline">
                <Link href={`/admin/usuarios/${encodeURIComponent(detail.id)}/editar`}>
                  <Pencil className="mr-2 h-4 w-4" /> Editar usuário
                </Link>
              </Button>
            </div>
          ) : null}
        </div>
      </header>

      {error ? <ContractState error={error} onRetry={() => setReload((value) => value + 1)} compact /> : null}

      <section aria-labelledby="dados-usuario">
        <h2 id="dados-usuario" className="text-lg font-semibold text-zinc-950">Dados cadastrais</h2>
        <div className="mt-3 grid gap-6 border-y border-zinc-200 py-5 lg:grid-cols-2">
          <DataGroup
            title="Identificação"
            entries={[
              ['Nome de cadastro', detail.nome || 'Não informado'],
              ['Nome civil', detail.nomeCivil || 'Não informado'],
              [detail.cpfMascarado ? 'CPF mascarado' : 'CPF', detail.cpf || 'Não informado'],
            ]}
          />
          <DataGroup
            title="Contato"
            entries={[
              ['E-mail', detail.email || 'Não informado'],
              ['Telefone / WhatsApp', detail.telefone ? maskPhoneBR(detail.telefone) : 'Não informado'],
            ]}
          />
          <DataGroup
            title="Dados pessoais"
            entries={[
              ['Data de nascimento', detail.dataNascimento ? new Date(`${detail.dataNascimento}T00:00:00`).toLocaleDateString('pt-BR') : 'Não informado'],
            ]}
          />
          <DataGroup
            title="Conta e KYC"
            entries={[
              ['Tipo da conta', pretty(detail.tipoConta)],
              ['Estado da conta', pretty(detail.status)],
              ['Situação do KYC', pretty(detail.kycStatus)],
              ['Cadastro', formatDate(detail.criadoEm)],
            ]}
          />
        </div>
      </section>

      <section aria-labelledby="anuncios-usuario">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 id="anuncios-usuario" className="text-lg font-semibold text-zinc-950">Endereço, localidades e anúncios</h2>
            <p className="mt-1 text-sm text-zinc-500">As localidades cadastrais disponíveis são as vinculadas aos anúncios.</p>
          </div>
          <Badge variant="outline">{detail.anuncios.length}</Badge>
        </div>
        {detail.anuncios.length === 0 ? (
          <p className="mt-3 border-y border-zinc-200 py-8 text-center text-sm text-zinc-500">Nenhum anúncio vinculado.</p>
        ) : (
          <div className="mt-3 divide-y divide-zinc-200 border-y border-zinc-200">
            {detail.anuncios.map((ad) => (
              <article key={ad.id} className="flex flex-col gap-3 py-4 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <p className="font-semibold text-zinc-900">{ad.titulo}</p>
                  <p className="mt-1 break-all text-xs text-zinc-500">{ad.slug}</p>
                  <div className="mt-2 flex flex-wrap gap-2">
                    <Badge variant="outline" className={statusTone(ad.status)}>{pretty(ad.status)}</Badge>
                    {ad.status !== ad.statusModeracao ? (
                      <Badge variant="outline" className={statusTone(ad.statusModeracao)}>{pretty(ad.statusModeracao)}</Badge>
                    ) : null}
                  </div>
                </div>
                <Button asChild variant="outline" size="sm">
                  <Link href={`/admin/anuncios/${encodeURIComponent(ad.id)}`}>Abrir anúncio</Link>
                </Button>
              </article>
            ))}
          </div>
        )}
      </section>

      <section aria-labelledby="kyc-usuario">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 id="kyc-usuario" className="text-lg font-semibold text-zinc-950">Documentos KYC privados</h2>
            <p className="mt-1 text-sm text-zinc-500">Inclusões e substituições permanecem restritas à gestão administrativa do usuário.</p>
          </div>
          <div className="flex items-center gap-2">
            <ShieldCheck className="h-5 w-5 text-zinc-500" aria-hidden="true" />
            {admin ? (
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setDocumentReplacement(null)
                  setDocumentDialogOpen(true)
                }}
              >
                <FileUp className="mr-2 h-4 w-4" /> Adicionar documento
              </Button>
            ) : null}
          </div>
        </div>
        <div className="mt-3">
          <AdminKycDocumentGrid
            submissions={detail.kycEnvios}
            onReplace={admin ? (submission) => {
              setDocumentReplacement(submission)
              setDocumentDialogOpen(true)
            } : undefined}
          />
        </div>
      </section>

      <section aria-labelledby="historico-usuario">
        <h2 id="historico-usuario" className="text-lg font-semibold text-zinc-950">Histórico administrativo</h2>
        {detail.historico.length === 0 ? (
          <p className="mt-3 border-y border-zinc-200 py-8 text-center text-sm text-zinc-500">Nenhum evento administrativo registrado.</p>
        ) : (
          <div className="mt-3 divide-y divide-zinc-200 border-y border-zinc-200">
            {detail.historico.map((event) => (
              <article key={event.id} className="flex flex-col gap-1 py-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="font-medium text-zinc-900">{pretty(event.acao)}</p>
                  <p className="text-xs text-zinc-500">Resultado: {pretty(event.resultado)}</p>
                </div>
                <div className="text-xs text-zinc-500 sm:text-right">
                  <p>{formatDate(event.criadoEm)}</p>
                  {event.requestId ? <p className="mt-1 font-mono">requestId {event.requestId}</p> : null}
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

      <AdminKycUploadDialog
        open={documentDialogOpen}
        onOpenChange={setDocumentDialogOpen}
        usuarioId={detail.id}
        replacing={documentReplacement}
        onSuccess={async () => {
          setDocumentDialogOpen(false)
          setDocumentReplacement(null)
          await load()
        }}
      />

      <AdminUsuarioCreditDialog
        open={creditDialogOpen}
        onOpenChange={setCreditDialogOpen}
        usuarioId={detail.id}
        nome={displayName}
      />

      <Dialog open={legalIntent !== null} onOpenChange={(open) => { if (!open && !legalBusy) setLegalIntent(null) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{legalIntent === 'BLOCK' ? 'Bloquear usuário' : 'Desbloquear usuário'}</DialogTitle>
            <DialogDescription>
              {legalIntent === 'BLOCK'
                ? 'O serviço jurídico canônico bloqueará a conta e retirará seus anúncios do catálogo.'
                : 'A conta será desbloqueada; nenhum anúncio será republicado automaticamente.'}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            {legalIntent === 'BLOCK' ? (
              <div className="space-y-2">
                <Label>Categoria</Label>
                <Select value={legalCategory} onValueChange={(value) => setLegalCategory(value as AdminLegalBlockCategory)}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {LEGAL_CATEGORIES.map((category) => (
                      <SelectItem key={category.value} value={category.value}>{category.label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            ) : null}
            <div className="space-y-2">
              <Label htmlFor="admin-user-legal-reason">Motivo</Label>
              <Textarea
                id="admin-user-legal-reason"
                value={legalReason}
                onChange={(event) => setLegalReason(event.target.value)}
                maxLength={500}
                disabled={legalBusy}
              />
            </div>
            {legalError ? <ContractState error={legalError} compact /> : null}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" disabled={legalBusy} onClick={() => setLegalIntent(null)}>Cancelar</Button>
            <Button
              type="button"
              variant={legalIntent === 'BLOCK' ? 'destructive' : 'default'}
              disabled={legalBusy || legalReason.trim().length < 5}
              onClick={() => void confirmLegalAction()}
            >
              {legalBusy ? 'Processando...' : 'Confirmar'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}

function DataGroup({ title, entries }: { title: string; entries: Array<[string, string]> }) {
  return (
    <div>
      <h3 className="text-sm font-semibold text-zinc-950">{title}</h3>
      <dl className="mt-3 grid gap-3 sm:grid-cols-2">
        {entries.map(([label, value]) => (
          <div key={label}>
            <dt className="text-xs font-semibold uppercase text-zinc-500">{label}</dt>
            <dd className="mt-1 break-words text-sm text-zinc-900">{value}</dd>
          </div>
        ))}
      </dl>
    </div>
  )
}
