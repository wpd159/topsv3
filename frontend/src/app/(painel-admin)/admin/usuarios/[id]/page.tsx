'use client'

import { useMemo, useState, useEffect } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  ArrowLeftIcon,
  BanknotesIcon,
  CalendarIcon,
  ChatBubbleLeftRightIcon,
  ClipboardDocumentListIcon,
  EnvelopeIcon,
  ExclamationTriangleIcon,
  FolderOpenIcon,
  MapPinIcon,
  PencilSquareIcon,
  PhoneIcon,
  PowerIcon,
  TicketIcon,
  TrashIcon,
  UserIcon,
} from '@heroicons/react/24/solid'
import AnunciosDoUsuarioTable from '../../components/anuncios-do-usuario-table'
import DocumentosUsuarioSection from '../../components/documentos-usuarios-section'
import AdicionarCreditosDialog from '../../components/adicionar-creditos-dialog'
import TicketDetailsModal from '../../components/ticket-details-modal'
import {
  type AdminUsuarioDetalhes,
  buildWhatsAppUrl,
  formatarCodigoBeneficio,
  formatarDataBR,
  formatarDataHoraBR,
  formatarLocalUsuario,
  formatarTelefoneExibicao,
  getTipoUsuarioMeta,
  getUsuarioHandle,
  getUsuarioNomePrincipal,
} from '../../components/admin-usuarios-utils'
import { formatCPF } from '@/utils/formatter'
import { useAuth } from '@/context/AuthContext'

type TicketResumo = {
  id: number
  assunto: string
  status: string
  criadoEm?: string | null
  ultimaInteracao?: string | null
  atendente?: string | null
}

type QuickFilterState = {
  status: string | null
  beneficio: string | null
}

async function readApiError(response: Response, fallback: string) {
  const raw = await response.text().catch(() => '')
  if (!raw.trim()) return fallback
  try {
    const parsed = JSON.parse(raw) as { error?: string; message?: string }
    return String(parsed.error || parsed.message || '').trim() || fallback
  } catch {
    return raw.trim() || fallback
  }
}

function getTicketBadgeClass(status?: string | null) {
  switch ((status || '').toUpperCase()) {
    case 'ABERTO':
      return 'border-amber-300 bg-amber-100 text-amber-700'
    case 'EM_ANDAMENTO':
      return 'border-blue-300 bg-blue-100 text-blue-700'
    case 'ENCERRADO':
    case 'FECHADO':
      return 'border-gray-300 bg-gray-100 text-gray-700'
    default:
      return 'border-gray-300 bg-gray-100 text-gray-700'
  }
}

export default function DetalhesUsuarioPage() {
  const params = useParams()
  const router = useRouter()
  const { usuario: usuarioLogado } = useAuth()

  const userId = useMemo(() => {
    const raw = (params?.id ?? '') as string | string[]
    return Array.isArray(raw) ? raw[0] : raw
  }, [params?.id])

  const [usuario, setUsuario] = useState<AdminUsuarioDetalhes | null>(null)
  const [tickets, setTickets] = useState<TicketResumo[]>([])
  const [ticketsLoading, setTicketsLoading] = useState(true)
  const [loading, setLoading] = useState(true)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [twoFactorResetOpen, setTwoFactorResetOpen] = useState(false)
  const [resettingTwoFactor, setResettingTwoFactor] = useState(false)
  const [creditoOpen, setCreditoOpen] = useState(false)
  const [ticketOpen, setTicketOpen] = useState(false)
  const [ticketSelecionado, setTicketSelecionado] = useState<TicketResumo | null>(null)
  const [quickFilter, setQuickFilter] = useState<QuickFilterState>({ status: null, beneficio: null })

  const rolarParaAnuncios = () => {
    window.setTimeout(() => {
      document.getElementById('anuncios')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }, 50)
  }

  const abrirAnuncios = (nextFilter: Partial<QuickFilterState> = {}) => {
    setQuickFilter({
      status: nextFilter.status ?? null,
      beneficio: nextFilter.beneficio ?? null,
    })
    rolarParaAnuncios()
  }

  const atualizarSaldoUsuario = (novoSaldo: number) => {
    setUsuario((prev) => (prev ? { ...prev, totalCreditos: novoSaldo } : prev))
  }

  const carregarUsuario = async () => {
    if (!userId) return

    try {
      setLoading(true)
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/usuarios/${userId}`, {
        credentials: 'include',
      })

      if (!res.ok) {
        throw new Error(await readApiError(res, 'Erro ao carregar detalhes do usuário.'))
      }

      const data = await res.json()
      setUsuario(data)
    } catch (error) {
      const message =
        error instanceof Error && error.message.trim()
          ? error.message
          : 'Erro ao carregar detalhes do usuário.'
      toast.error(message)
      setUsuario(null)
    } finally {
      setLoading(false)
    }
  }

  const carregarTickets = async () => {
    if (!userId) return

    try {
      setTicketsLoading(true)
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/suporte/usuario/${userId}/tickets-resumo`, {
        credentials: 'include',
      })

      if (!res.ok) {
        throw new Error(await readApiError(res, 'Erro ao carregar tickets do usuário.'))
      }

      const data = await res.json()
      setTickets(Array.isArray(data) ? data : [])
    } catch (error) {
      const message =
        error instanceof Error && error.message.trim()
          ? error.message
          : 'Erro ao carregar tickets do usuário.'
      toast.error(message)
      setTickets([])
    } finally {
      setTicketsLoading(false)
    }
  }

  useEffect(() => {
    void carregarUsuario()
    void carregarTickets()
  }, [userId])

  useEffect(() => {
    if (typeof window !== 'undefined' && window.location.hash === '#anuncios') {
      rolarParaAnuncios()
    }
  }, [usuario?.id])

  const alternarStatus = async () => {
    if (!usuario) return

    try {
      const endpoint = usuario.status === 'ATIVO' ? 'inativar' : 'ativar'
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/usuarios/${usuario.id}/${endpoint}`, {
        method: 'PUT',
        credentials: 'include',
      })

      if (!res.ok) {
        throw new Error(await readApiError(res, 'Erro ao alterar status do usuário.'))
      }

      setUsuario((prev) =>
        prev
          ? {
              ...prev,
              status: prev.status === 'ATIVO' ? 'INATIVO' : 'ATIVO',
            }
          : prev
      )

      toast.success(`Usuário ${usuario.status === 'ATIVO' ? 'inativado' : 'ativado'} com sucesso.`)
    } catch (error) {
      const message =
        error instanceof Error && error.message.trim()
          ? error.message
          : 'Falha ao alterar status do usuário.'
      toast.error(message)
    }
  }

  const excluirUsuario = async () => {
    if (!usuario) return

    try {
      setDeleting(true)

      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/usuarios/${usuario.id}`, {
        method: 'DELETE',
        credentials: 'include',
      })

      if (!res.ok) {
        throw new Error(await readApiError(res, 'Erro ao excluir usuário.'))
      }

      toast.success('Usuário excluído com sucesso.')
      setDeleteOpen(false)
      router.push('/admin/usuarios')
    } catch (error) {
      const message =
        error instanceof Error && error.message.trim()
          ? error.message
          : 'Falha ao excluir usuário.'
      toast.error(message)
    } finally {
      setDeleting(false)
    }
  }

  const resetarTwoFactor = async () => {
    if (!usuario) return

    try {
      setResettingTwoFactor(true)
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/usuarios/${usuario.id}/2fa/reset`, {
        method: 'POST',
        credentials: 'include',
      })

      if (!res.ok) {
        throw new Error(await readApiError(res, 'Erro ao resetar 2FA.'))
      }

      setUsuario((prev) => (prev ? { ...prev, twoFactorAtivo: false } : prev))
      setTwoFactorResetOpen(false)
      toast.success('2FA removido com sucesso.')
    } catch (error) {
      const message =
        error instanceof Error && error.message.trim()
          ? error.message
          : 'Falha ao resetar 2FA do usuário.'
      toast.error(message)
    } finally {
      setResettingTwoFactor(false)
    }
  }

  if (loading) {
    return <div className="py-10 text-center text-gray-500">Carregando usuário...</div>
  }

  if (!usuario) {
    return <div className="py-10 text-center text-gray-500">Usuário não encontrado.</div>
  }

  const nomePrincipal = getUsuarioNomePrincipal(usuario)
  const handle = getUsuarioHandle(usuario)
  const tipo = getTipoUsuarioMeta(usuario)
  const ativo = usuario.status === 'ATIVO'
  const isAdmin = (usuarioLogado?.cargo || '').toUpperCase() === 'ADMIN'
  const local = formatarLocalUsuario(usuario)
  const totalCreditos = Number(usuario.totalCreditos || 0)
  const totalAnuncios = Number(usuario.totalAnuncios || 0)
  const anunciosAtivos = Number(usuario.anunciosAtivos || 0)
  const anunciosPendentes = Number(usuario.anunciosPendentes || 0)
  const beneficiosAtivos = Number(usuario.beneficiosAtivos || 0)
  const beneficiosCodigos = usuario.beneficiosAtivosCodigos || []
  const whatsappUrl = buildWhatsAppUrl(usuario.telefone)
  const anuncios = (usuario.anuncios || []).map((anuncio) => ({
    id:
      anuncio.id ??
      Number(String(anuncio.linkAnuncio || '').split('/').filter(Boolean).pop() || 0),
    titulo: anuncio.titulo || 'Sem título',
    status: anuncio.status || '—',
    data: formatarDataBR(anuncio.dataPublicacao),
    beneficiosAtivosCodigos: anuncio.beneficiosAtivosCodigos || [],
  }))
  const beneficioDisponivelNosAnuncios = (codigo: string) =>
    anuncios.some((anuncio) => (anuncio.beneficiosAtivosCodigos || []).includes(codigo))

  const cardsResumo = [
    {
      label: 'Anúncios',
      value: totalAnuncios,
      className: 'text-gray-900',
      action: () => abrirAnuncios(),
    },
    {
      label: 'Ativos',
      value: anunciosAtivos,
      className: 'text-emerald-700',
      action: () => abrirAnuncios({ status: 'ATIVO' }),
    },
    {
      label: 'Pendentes',
      value: anunciosPendentes,
      className: 'text-amber-700',
      action: () => abrirAnuncios({ status: 'PENDENTE' }),
    },
    {
      label: 'Créditos',
      value: totalCreditos,
      className: 'text-[#C41E73]',
      action: () => setCreditoOpen(true),
    },
  ]

  return (
    <section className="space-y-6 pb-8">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
        <div className="flex items-start gap-3">
          <Button
            variant="ghost"
            size="icon"
            onClick={() => router.push('/admin/usuarios')}
            className="mt-0.5 text-gray-600 hover:bg-gray-100"
          >
            <ArrowLeftIcon className="h-5 w-5" />
          </Button>

          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-bold text-gray-900">{nomePrincipal}</h1>
              <Badge
                variant="outline"
                className={`rounded-md border px-2 py-1 text-[11px] font-medium ${
                  ativo
                    ? 'border-green-300 bg-green-100 text-green-700'
                    : 'border-red-300 bg-red-100 text-red-700'
                }`}
              >
                {ativo ? 'Ativo' : 'Inativo'}
              </Badge>
              <Badge
                variant="outline"
                className={`rounded-md border px-2 py-1 text-[11px] font-medium ${tipo.className}`}
              >
                {tipo.label}
              </Badge>
            </div>

            <p className="text-sm text-gray-500">
              Gestão cadastral, comercial e operacional do usuário, com atalhos para anúncios, documentos, tickets e crédito.
            </p>

            <div className="flex flex-wrap gap-x-5 gap-y-2 text-sm text-gray-600">
              <span className="inline-flex items-center gap-2">
                <MapPinIcon className="h-4 w-4 text-[#C41E73]" />
                {local}
              </span>
              <span className="inline-flex items-center gap-2">
                <CalendarIcon className="h-4 w-4 text-[#C41E73]" />
                Cadastro em {formatarDataHoraBR(usuario.dataCadastro ?? usuario.criadoEm)}
              </span>
              {handle ? <span className="inline-flex items-center gap-2 text-gray-500">{handle}</span> : null}
            </div>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2 xl:justify-end">
          <Button
            onClick={() => setCreditoOpen(true)}
            className="bg-[#FC1EAD] text-white hover:bg-[#e01a9a]"
          >
            <BanknotesIcon className="mr-2 h-4 w-4" />
            Adicionar crédito
          </Button>

          <Button
            variant="outline"
            onClick={() => abrirAnuncios()}
            className="border-gray-300 text-gray-700 hover:bg-gray-50"
          >
            <FolderOpenIcon className="mr-2 h-4 w-4" />
            Ver anúncios
          </Button>

          <Button
            variant="outline"
            onClick={() => router.push(`/admin/usuarios/${userId}/editar`)}
            className="border-[#C41E73]/40 text-[#C41E73] hover:bg-[#FC1EAD]/10"
          >
            <PencilSquareIcon className="mr-2 h-4 w-4" />
            Editar
          </Button>

          <Button
            variant="outline"
            onClick={() =>
              router.push(`/admin/registros?busca=${encodeURIComponent(usuario.email || usuario.username || '')}`)
            }
            className="border-[#C41E73]/40 text-[#C41E73] hover:bg-[#FC1EAD]/10"
          >
            <ClipboardDocumentListIcon className="mr-2 h-4 w-4" />
            Ver logs
          </Button>

          <Button
            variant="outline"
            onClick={alternarStatus}
            className={
              ativo
                ? 'border-red-300 text-red-600 hover:bg-red-50'
                : 'border-green-300 text-green-600 hover:bg-green-50'
            }
          >
            <PowerIcon className="mr-2 h-4 w-4" />
            {ativo ? 'Desativar' : 'Ativar'}
          </Button>

          {isAdmin && usuario.twoFactorAtivo ? (
            <Button
              variant="outline"
              onClick={() => setTwoFactorResetOpen(true)}
              className="border-amber-300 text-amber-700 hover:bg-amber-50"
            >
              <ExclamationTriangleIcon className="mr-2 h-4 w-4" />
              Remover 2FA
            </Button>
          ) : null}

          <Button
            variant="outline"
            onClick={() => setDeleteOpen(true)}
            className="border-red-300 text-red-600 hover:bg-red-50"
          >
            <TrashIcon className="mr-2 h-4 w-4" />
            Excluir
          </Button>
        </div>
      </div>

      <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <div className="space-y-6">
          <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm">
            <div className="mb-4 flex items-center gap-2">
              <UserIcon className="h-5 w-5 text-[#C41E73]" />
              <h2 className="text-lg font-semibold text-gray-900">Informações do usuário</h2>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <p className="text-xs uppercase tracking-wide text-gray-500">Nome</p>
                <p className="mt-1 text-sm font-medium text-gray-900">{nomePrincipal}</p>
              </div>
              <div>
                <p className="text-xs uppercase tracking-wide text-gray-500">Username</p>
                <p className="mt-1 text-sm font-medium text-gray-900">{handle || '—'}</p>
              </div>
              <div>
                <p className="text-xs uppercase tracking-wide text-gray-500">Email</p>
                <p className="mt-1 inline-flex items-center gap-2 text-sm font-medium text-gray-900">
                  <EnvelopeIcon className="h-4 w-4 text-[#C41E73]" />
                  {usuario.email || '—'}
                </p>
              </div>
              <div>
                <p className="text-xs uppercase tracking-wide text-gray-500">Telefone</p>
                {whatsappUrl ? (
                  <a
                    href={whatsappUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="mt-1 inline-flex items-center gap-2 text-sm font-medium text-[#C41E73] hover:underline"
                  >
                    <PhoneIcon className="h-4 w-4 text-[#25D366]" />
                    {formatarTelefoneExibicao(usuario.telefone)}
                  </a>
                ) : (
                  <p className="mt-1 inline-flex items-center gap-2 text-sm font-medium text-gray-900">
                    <PhoneIcon className="h-4 w-4 text-[#C41E73]" />
                    {formatarTelefoneExibicao(usuario.telefone)}
                  </p>
                )}
              </div>
              <div>
                <p className="text-xs uppercase tracking-wide text-gray-500">CPF</p>
                <p className="mt-1 text-sm font-medium text-gray-900">
                  {usuario.cpf ? formatCPF(usuario.cpf) : '—'}
                </p>
              </div>
              <div>
                <p className="text-xs uppercase tracking-wide text-gray-500">Data de nascimento</p>
                <p className="mt-1 text-sm font-medium text-gray-900">
                  {formatarDataBR(usuario.dataNascimento)}
                </p>
              </div>
              <div>
                <p className="text-xs uppercase tracking-wide text-gray-500">Status</p>
                <div className="mt-1">
                  <Badge
                    variant="outline"
                    className={`rounded-md border px-2 py-1 text-[11px] font-medium ${
                      ativo
                        ? 'border-green-300 bg-green-100 text-green-700'
                        : 'border-red-300 bg-red-100 text-red-700'
                    }`}
                  >
                    {ativo ? 'Ativo' : 'Inativo'}
                  </Badge>
                </div>
              </div>
              <div>
                <p className="text-xs uppercase tracking-wide text-gray-500">2FA</p>
                <div className="mt-1">
                  <Badge
                    variant="outline"
                    className={`rounded-md border px-2 py-1 text-[11px] font-medium ${
                      usuario.twoFactorAtivo
                        ? 'border-amber-300 bg-amber-100 text-amber-700'
                        : 'border-gray-300 bg-gray-100 text-gray-700'
                    }`}
                  >
                    {usuario.twoFactorAtivo ? 'Ativo' : 'Desativado'}
                  </Badge>
                </div>
              </div>
              <div>
                <p className="text-xs uppercase tracking-wide text-gray-500">Local</p>
                <p className="mt-1 text-sm font-medium text-gray-900">{local}</p>
              </div>
            </div>
          </div>

          <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm">
            <div className="mb-4 flex items-center gap-2">
              <FolderOpenIcon className="h-5 w-5 text-[#C41E73]" />
              <h2 className="text-lg font-semibold text-gray-900">Resumo de conta</h2>
            </div>

            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              {cardsResumo.map((card) => (
                <button
                  key={card.label}
                  type="button"
                  onClick={card.action}
                  className="rounded-xl border border-gray-100 bg-gray-50 p-4 text-left transition hover:border-[#FC1EAD]/30 hover:bg-[#FC1EAD]/5"
                >
                  <p className="text-xs uppercase tracking-wide text-gray-500">{card.label}</p>
                  <p className={`mt-2 text-2xl font-bold ${card.className}`}>{card.value}</p>
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="space-y-6">
          <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm">
            <div className="mb-4 flex items-center gap-2">
              <BanknotesIcon className="h-5 w-5 text-[#C41E73]" />
              <h2 className="text-lg font-semibold text-gray-900">Monetização</h2>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <button
                type="button"
                onClick={() => setCreditoOpen(true)}
                className="rounded-xl border border-gray-100 bg-gray-50 p-4 text-left transition hover:border-[#FC1EAD]/30 hover:bg-[#FC1EAD]/5"
              >
                <p className="text-xs uppercase tracking-wide text-gray-500">Créditos atuais</p>
                <p className="mt-2 text-2xl font-bold text-[#C41E73]">{totalCreditos}</p>
              </button>

              <div className="rounded-xl border border-gray-100 bg-gray-50 p-4">
                <p className="text-xs uppercase tracking-wide text-gray-500">Benefícios ativos</p>
                <p className="mt-2 text-2xl font-bold text-gray-900">{beneficiosAtivos}</p>
              </div>
            </div>

            <div className="mt-5 rounded-xl border border-dashed border-gray-200 bg-gray-50 p-4">
              <p className="text-xs uppercase tracking-wide text-gray-500">Sinais comerciais</p>
              {beneficiosCodigos.length > 0 ? (
                <div className="mt-3 flex flex-wrap gap-2">
                  {beneficiosCodigos.map((codigo) => {
                    const clicavel = beneficioDisponivelNosAnuncios(codigo)
                    return (
                      <button
                        key={codigo}
                        type="button"
                        disabled={!clicavel}
                        onClick={() => abrirAnuncios({ beneficio: codigo })}
                        className="disabled:cursor-default"
                      >
                        <Badge
                          variant="outline"
                          className={`rounded-md border px-2 py-1 text-[11px] font-medium ${
                            clicavel
                              ? 'border-emerald-300 bg-emerald-100 text-emerald-700 hover:bg-emerald-200'
                              : 'border-gray-300 bg-gray-100 text-gray-500'
                          }`}
                        >
                          {formatarCodigoBeneficio(codigo)}
                        </Badge>
                      </button>
                    )
                  })}
                </div>
              ) : (
                <p className="mt-3 text-sm text-gray-600">
                  Nenhum benefício ativo vinculado a este usuário no momento.
                </p>
              )}
            </div>
          </div>

          <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm">
            <div className="mb-4 flex items-center gap-2">
              <MapPinIcon className="h-5 w-5 text-[#C41E73]" />
              <h2 className="text-lg font-semibold text-gray-900">Local e cadastro</h2>
            </div>

            <div className="space-y-3 text-sm text-gray-700">
              <div className="flex items-start justify-between gap-4">
                <span className="text-gray-500">Cidade / UF</span>
                <span className="font-medium text-gray-900">{local}</span>
              </div>
              <div className="flex items-start justify-between gap-4">
                <span className="text-gray-500">Bairro</span>
                <span className="font-medium text-gray-900">{usuario.bairroNome || '—'}</span>
              </div>
              <div className="flex items-start justify-between gap-4">
                <span className="text-gray-500">Cadastro</span>
                <span className="font-medium text-gray-900">{formatarDataHoraBR(usuario.dataCadastro ?? usuario.criadoEm)}</span>
              </div>
              <div className="flex items-start justify-between gap-4">
                <span className="text-gray-500">Documentos</span>
                <span className="font-medium text-gray-900">{usuario.totalDocumentos || 0}</span>
              </div>
              <div className="flex items-start justify-between gap-4">
                <span className="text-gray-500">Tickets</span>
                <span className="font-medium text-gray-900">{tickets.length}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <DocumentosUsuarioSection
        documentos={usuario.documentosUrls || []}
        manageHref={`/admin/usuarios/${usuario.id}/editar#documentos`}
      />

      <section className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm">
        <div className="mb-4 flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <TicketIcon className="h-5 w-5 text-[#C41E73]" />
            <div>
              <h2 className="text-lg font-semibold text-gray-900">Tickets de suporte</h2>
              <p className="text-sm text-gray-500">Chamados recentes abertos por este usuário.</p>
            </div>
          </div>

          <Button
            variant="outline"
            className="border-gray-300 text-gray-700 hover:bg-gray-50"
            onClick={() => router.push('/admin/tickets')}
          >
            <ChatBubbleLeftRightIcon className="mr-2 h-4 w-4" />
            Abrir central
          </Button>
        </div>

        {ticketsLoading ? (
          <div className="py-6 text-sm text-gray-500">Carregando tickets...</div>
        ) : tickets.length === 0 ? (
          <div className="rounded-xl border border-dashed border-gray-200 bg-gray-50 px-4 py-5 text-sm text-gray-500">
            Este usuário não possui tickets registrados no momento.
          </div>
        ) : (
          <div className="space-y-3">
            {tickets.map((ticket) => (
              <div
                key={ticket.id}
                className="flex flex-col gap-3 rounded-xl border border-gray-100 bg-gray-50 px-4 py-4 lg:flex-row lg:items-center lg:justify-between"
              >
                <div className="min-w-0 space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium text-gray-900">#{ticket.id}</span>
                    <Badge
                      variant="outline"
                      className={`rounded-md border px-2 py-1 text-[11px] font-medium ${getTicketBadgeClass(ticket.status)}`}
                    >
                      {ticket.status || '—'}
                    </Badge>
                  </div>
                  <p className="line-clamp-2 text-sm font-medium text-gray-800">{ticket.assunto || 'Sem assunto'}</p>
                  <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-gray-500">
                    <span>Abertura: {formatarDataHoraBR(ticket.criadoEm)}</span>
                    <span>Última interação: {formatarDataHoraBR(ticket.ultimaInteracao)}</span>
                    {ticket.atendente ? <span>Atendente: {ticket.atendente}</span> : null}
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    className="border-[#C41E73]/40 text-[#C41E73] hover:bg-[#FC1EAD]/10"
                    onClick={() => {
                      setTicketSelecionado(ticket)
                      setTicketOpen(true)
                    }}
                  >
                    Ver
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      <section id="anuncios">
        <AnunciosDoUsuarioTable
          anuncios={anuncios}
          statusFilter={quickFilter.status}
          beneficioFilter={quickFilter.beneficio}
          onClearQuickFilter={() => setQuickFilter({ status: null, beneficio: null })}
        />
      </section>

      <AdicionarCreditosDialog
        open={creditoOpen}
        onOpenChange={setCreditoOpen}
        usuarioId={usuario.id}
        nomeUsuario={nomePrincipal}
        saldoAtual={usuario.totalCreditos ?? 0}
        onSuccess={atualizarSaldoUsuario}
      />

      <TicketDetailsModal
        open={ticketOpen}
        onOpenChange={setTicketOpen}
        ticket={
          ticketSelecionado
            ? {
                ...ticketSelecionado,
                abertoPorNome: usuario.nomeCompleto,
                abertoPorUsername: usuario.username,
              }
            : null
        }
      />

      <Dialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <ExclamationTriangleIcon className="h-5 w-5 text-red-600" />
              Excluir usuário
            </DialogTitle>
          </DialogHeader>

          <div className="space-y-2 text-sm text-gray-700">
            <p>Tem certeza que deseja excluir este usuário?</p>
            <p className="text-gray-500">
              Esta ação é <span className="font-semibold text-red-600">irreversível</span> e removerá dados
              associados, como anúncios, documentos e vínculos operacionais desse cadastro.
            </p>
          </div>

          <DialogFooter className="gap-2 sm:gap-0">
            <Button variant="outline" onClick={() => setDeleteOpen(false)} disabled={deleting}>
              Cancelar
            </Button>
            <Button
              onClick={excluirUsuario}
              disabled={deleting}
              className="bg-red-600 text-white hover:bg-red-700"
            >
              {deleting ? 'Excluindo...' : 'Excluir definitivamente'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={twoFactorResetOpen} onOpenChange={setTwoFactorResetOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <ExclamationTriangleIcon className="h-5 w-5 text-amber-600" />
              Remover 2FA
            </DialogTitle>
          </DialogHeader>

          <div className="space-y-2 text-sm text-gray-700">
            <p>Tem certeza que deseja remover o 2FA deste usuário?</p>
            <p className="text-gray-500">
              A senha e os demais dados da conta serão preservados. O usuário poderá ativar o 2FA novamente depois.
            </p>
          </div>

          <DialogFooter className="gap-2 sm:gap-0">
            <Button variant="outline" onClick={() => setTwoFactorResetOpen(false)} disabled={resettingTwoFactor}>
              Cancelar
            </Button>
            <Button
              onClick={resetarTwoFactor}
              disabled={resettingTwoFactor}
              className="bg-amber-600 text-white hover:bg-amber-700"
            >
              {resettingTwoFactor ? 'Removendo...' : 'Remover 2FA'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}
