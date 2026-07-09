'use client'

import { useEffect, useMemo, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import {
  ArrowPathIcon,
  ChatBubbleLeftEllipsisIcon,
  CheckCircleIcon,
  ClockIcon,
  EnvelopeIcon,
  EyeIcon,
  TagIcon,
  UserIcon,
  XCircleIcon,
} from "@heroicons/react/24/solid"
import { toast } from "sonner"
import SockJS from "sockjs-client"
import { Client } from "@stomp/stompjs"
import TicketChatModal from "../components/ticket-chat-modal"
import TicketDetailsModal from "../components/ticket-details-modal"
import { useAuth } from "@/context/AuthContext"

type Status = "ABERTO" | "EM_ANDAMENTO" | "FECHADO"
type FiltroStatus = "TODOS" | Status
type FiltroMotivo = "TODOS" | string
type Ordenacao = "RECENTES" | "ANTIGOS"
type RoleMensagem = "USER" | "ADMIN" | "MODERADOR" | null

interface TicketItem {
  id: number
  abertoPorUsername: string
  abertoPorNome?: string
  abertoPorEmail: string
  motivo: string
  descricao: string
  status: Status
  dataAbertura: string
  possuiNovaMensagem: boolean
  ultimaMensagemEnviadaPorRole: RoleMensagem
}

const normalizarStatusTicket = (valor: string | null) => {
  if (!valor) return "-"
  const map: Record<string, string> = {
    ABERTO: "Aberto",
    EM_ANDAMENTO: "Em andamento",
    FECHADO: "Fechado",
  }
  return map[valor] || valor
}

const normalizarMotivoTicket = (valor: string | null) => {
  if (!valor) return "Geral"
  return valor
    .toLowerCase()
    .split("_")
    .filter(Boolean)
    .map((parte) => parte.charAt(0).toUpperCase() + parte.slice(1))
    .join(" ")
}

const normalizarBusca = (valor: string) =>
  valor
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .trim()

const formatarDataTicket = (valor: string) => {
  const data = new Date(valor)
  if (Number.isNaN(data.getTime())) return "Data indisponível"
  return data.toLocaleString("pt-BR")
}

const formatarTempoRelativo = (valor: string) => {
  const data = new Date(valor)
  if (Number.isNaN(data.getTime())) return ""

  const diferenca = data.getTime() - Date.now()
  const diferencaAbsoluta = Math.abs(diferenca)
  const rtf = new Intl.RelativeTimeFormat("pt-BR", { numeric: "auto" })
  const minuto = 60 * 1000
  const hora = 60 * minuto
  const dia = 24 * hora

  if (diferencaAbsoluta < minuto) return "agora"
  if (diferencaAbsoluta < hora) return rtf.format(Math.round(diferenca / minuto), "minute")
  if (diferencaAbsoluta < dia) return rtf.format(Math.round(diferenca / hora), "hour")
  return rtf.format(Math.round(diferenca / dia), "day")
}

const resumirMensagem = (valor: string | null, limite = 160) => {
  const texto = valor?.trim() || "Sem mensagem inicial disponível."
  if (texto.length <= limite) return texto
  return `${texto.slice(0, limite).trimEnd()}...`
}

export default function AdminTicketsPage() {
  const { usuario } = useAuth()
  const [tickets, setTickets] = useState<TicketItem[]>([])
  const [loading, setLoading] = useState(true)
  const [filtroStatus, setFiltroStatus] = useState<FiltroStatus>("TODOS")
  const [busca, setBusca] = useState("")
  const [filtroMotivo, setFiltroMotivo] = useState<FiltroMotivo>("TODOS")
  const [ordenacao, setOrdenacao] = useState<Ordenacao>("RECENTES")
  const [ticketSelecionado, setTicketSelecionado] = useState<TicketItem | null>(null)
  const [openChat, setOpenChat] = useState(false)
  const [openDetalhes, setOpenDetalhes] = useState(false)
  const openChatRef = useRef(false)
  const ticketSelecionadoIdRef = useRef<number | null>(null)

  const getBadgeColor = (status: string) => {
    switch (status) {
      case "ABERTO":
        return "border-emerald-300 bg-emerald-100 text-emerald-800 shadow-[inset_0_0_0_1px_rgba(16,185,129,0.14)]"
      case "EM_ANDAMENTO":
        return "border-amber-300 bg-amber-100 text-amber-800 shadow-[inset_0_0_0_1px_rgba(245,158,11,0.14)]"
      case "FECHADO":
        return "border-slate-200 bg-slate-100 text-slate-600"
      default:
        return "bg-gray-100 text-gray-600 border-gray-300"
    }
  }

  const getCardColor = (status: Status) => {
    switch (status) {
      case "ABERTO":
        return "border-emerald-200/90 bg-emerald-50/30"
      case "EM_ANDAMENTO":
        return "border-amber-200/90 bg-amber-50/30"
      case "FECHADO":
        return "border-slate-200 bg-white"
      default:
        return "border-gray-200 bg-white"
    }
  }

  const getFiltroButtonClass = (status: FiltroStatus) => {
    const ativo = filtroStatus === status

    if (status === "ABERTO") {
      return ativo
        ? "bg-green-600 text-white hover:bg-green-700 border-green-600"
        : "bg-white text-green-700 border-green-300 hover:bg-green-50"
    }

    if (status === "EM_ANDAMENTO") {
      return ativo
        ? "bg-yellow-500 text-white hover:bg-yellow-600 border-yellow-500"
        : "bg-white text-yellow-700 border-yellow-300 hover:bg-yellow-50"
    }

    if (status === "FECHADO") {
      return ativo
        ? "bg-gray-700 text-white hover:bg-gray-800 border-gray-700"
        : "bg-white text-gray-700 border-gray-300 hover:bg-gray-50"
    }

    return ativo
      ? "bg-[#C41E73] text-white hover:bg-[#a81861] border-[#C41E73]"
      : "bg-white text-[#C41E73] border-[#C41E73]/30 hover:bg-[#FC1EAD]/10"
  }

  const mapApiToTicket = (raw: any): TicketItem => ({
    id: raw.id,
    abertoPorUsername: raw.abertoPorUsername ?? raw.abertoPor?.username ?? "—",
    abertoPorNome: raw.abertoPorNome ?? raw.abertoPor?.nomeCompleto ?? raw.abertoPorUsername ?? "—",
    abertoPorEmail: raw.abertoPorEmail ?? raw.abertoPor?.email ?? "—",
    motivo: raw.motivo ?? "GERAL",
    descricao: raw.descricao ?? "—",
    status: (raw.status as Status) ?? "ABERTO",
    dataAbertura: raw.dataAbertura ?? new Date().toISOString(),
    possuiNovaMensagem: false,
    ultimaMensagemEnviadaPorRole: null,
  })

  useEffect(() => {
    openChatRef.current = openChat
  }, [openChat])

  useEffect(() => {
    ticketSelecionadoIdRef.current = ticketSelecionado?.id ?? null
  }, [ticketSelecionado?.id])

  const carregarTickets = async (silencioso = false) => {
    try {
      setLoading(true)
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/suporte`, {
        credentials: 'include',
        cache: 'no-store',
      })

      if (!res.ok) throw new Error('Falha ao listar tickets')

      const data = await res.json()
      setTickets(Array.isArray(data) ? data.map(mapApiToTicket) : [])

      if (!silencioso) {
        toast.success('Tickets atualizados.')
      }
    } catch {
      toast.error('Não foi possível carregar os tickets.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    carregarTickets(true)
  }, [])

  const ticketIdsKey = useMemo(
    () => tickets.map((ticket) => ticket.id).sort((a, b) => a - b).join(","),
    [tickets]
  )

  useEffect(() => {
    if (!usuario?.id || !ticketIdsKey) return

    const socket = new SockJS(`${process.env.NEXT_PUBLIC_API_URL}/ws-suporte`)
    const client = new Client({
      webSocketFactory: () => socket as any,
      reconnectDelay: 3000,
      onConnect: () => {
        ticketIdsKey.split(",").forEach((ticketId) => {
          if (!ticketId) return

          client.subscribe(`/topic/suporte/${ticketId}`, (message) => {
            if (!message.body) return

            const data = JSON.parse(message.body)
            const currentTicketId = Number(data.suporteId ?? ticketId)
            const veioDoUsuario = data.enviadoPorRole === "USER"
            const ticketAbertoNoModal =
              openChatRef.current && ticketSelecionadoIdRef.current === currentTicketId

            setTickets((prev) =>
              prev.map((ticket) =>
                ticket.id === currentTicketId
                  ? {
                      ...ticket,
                      status: ticket.status === "ABERTO" ? "EM_ANDAMENTO" : ticket.status,
                      possuiNovaMensagem: veioDoUsuario ? !ticketAbertoNoModal : false,
                      ultimaMensagemEnviadaPorRole: data.enviadoPorRole ?? null,
                    }
                  : ticket
              )
            )

            setTicketSelecionado((prev) =>
              prev && prev.id === currentTicketId
                ? {
                    ...prev,
                    status: prev.status === "ABERTO" ? "EM_ANDAMENTO" : prev.status,
                    possuiNovaMensagem: false,
                    ultimaMensagemEnviadaPorRole: data.enviadoPorRole ?? null,
                  }
                : prev
            )
          })
        })
      },
    })

    client.activate()

    return () => {
      client.deactivate()
    }
  }, [ticketIdsKey, usuario?.id])

  const fecharTicket = async (id: number) => {
    if (!usuario?.id) {
      toast.error('Você precisa estar autenticado.')
      return
    }

    try {
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/suporte/${id}/fechar`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ usuarioId: String(usuario.id) }),
      })

      if (!res.ok) throw new Error('Falha ao fechar ticket')

      setTickets((prev) =>
        prev.map((ticket) => (ticket.id === id ? { ...ticket, status: 'FECHADO' } : ticket))
      )

      toast.success('Ticket fechado com sucesso.')
    } catch {
      toast.error('Não foi possível fechar o ticket.')
    }
  }

  const totalAbertos = useMemo(
    () => tickets.filter((ticket) => ticket.status === 'ABERTO').length,
    [tickets]
  )

  const totalEmAndamento = useMemo(
    () => tickets.filter((ticket) => ticket.status === 'EM_ANDAMENTO').length,
    [tickets]
  )

  const totalFechados = useMemo(
    () => tickets.filter((ticket) => ticket.status === 'FECHADO').length,
    [tickets]
  )

  const motivosDisponiveis = useMemo(
    () =>
      Array.from(new Set(tickets.map((ticket) => ticket.motivo).filter(Boolean))).sort((a, b) =>
        normalizarMotivoTicket(a).localeCompare(normalizarMotivoTicket(b), "pt-BR")
      ),
    [tickets]
  )

  const ticketsFiltrados = useMemo(() => {
    const buscaNormalizada = normalizarBusca(busca)
    const base = tickets.filter((ticket) => {
      if (filtroStatus !== 'TODOS' && ticket.status !== filtroStatus) return false
      if (filtroMotivo !== 'TODOS' && ticket.motivo !== filtroMotivo) return false

      if (!buscaNormalizada) return true

      const alvo = normalizarBusca(
        [ticket.abertoPorNome, ticket.abertoPorUsername, ticket.abertoPorEmail].filter(Boolean).join(" ")
      )

      return alvo.includes(buscaNormalizada)
    })

    return [...base].sort((a, b) => {
      const dataA = new Date(a.dataAbertura).getTime()
      const dataB = new Date(b.dataAbertura).getTime()
      return ordenacao === "RECENTES" ? dataB - dataA : dataA - dataB
    })
  }, [tickets, filtroStatus, filtroMotivo, busca, ordenacao])

  if (loading && tickets.length === 0) {
    return <section className="py-10 text-center text-gray-500">Carregando tickets...</section>
  }

  return (
    <section className="pb-12">
      <div className="mb-8 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold text-gray-800">Tickets de Suporte</h1>
          <p className="text-sm text-gray-500">
            Visualize, responda e acompanhe as conversas dos usuários.
          </p>
        </div>

        <Button
          variant="outline"
          onClick={() => carregarTickets()}
          disabled={loading}
          className="flex items-center gap-2 border-[#C41E73]/30 text-[#C41E73] hover:bg-[#FC1EAD]/10"
        >
          <ArrowPathIcon className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Atualizar
        </Button>
      </div>

      <div className="mb-6 flex flex-wrap gap-3">
        <Button variant="outline" onClick={() => setFiltroStatus('TODOS')} className={getFiltroButtonClass('TODOS')}>
          Todos ({tickets.length})
        </Button>
        <Button variant="outline" onClick={() => setFiltroStatus('ABERTO')} className={getFiltroButtonClass('ABERTO')}>
          Abertos ({totalAbertos})
        </Button>
        <Button
          variant="outline"
          onClick={() => setFiltroStatus('EM_ANDAMENTO')}
          className={getFiltroButtonClass('EM_ANDAMENTO')}
        >
          Em andamento ({totalEmAndamento})
        </Button>
        <Button variant="outline" onClick={() => setFiltroStatus('FECHADO')} className={getFiltroButtonClass('FECHADO')}>
          Fechados ({totalFechados})
        </Button>
      </div>

      <div className="mb-5 grid grid-cols-1 gap-3 lg:grid-cols-[minmax(0,1.4fr)_minmax(220px,0.8fr)_180px]">
        <Input
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
          placeholder="Buscar por nome, username ou e-mail"
          className="h-11 border-gray-300 bg-white"
        />

        <select
          value={filtroMotivo}
          onChange={(e) => setFiltroMotivo(e.target.value)}
          className="h-11 rounded-3xl border border-gray-300 bg-white px-3 text-sm text-gray-700 outline-none transition focus:border-[#C41E73]/40 focus:ring-2 focus:ring-[#C41E73]/10"
        >
          <option value="TODOS">Todos os motivos</option>
          {motivosDisponiveis.map((motivo) => (
            <option key={motivo} value={motivo}>
              {normalizarMotivoTicket(motivo)}
            </option>
          ))}
        </select>

        <select
          value={ordenacao}
          onChange={(e) => setOrdenacao(e.target.value as Ordenacao)}
          className="h-11 rounded-3xl border border-gray-300 bg-white px-3 text-sm text-gray-700 outline-none transition focus:border-[#C41E73]/40 focus:ring-2 focus:ring-[#C41E73]/10"
        >
          <option value="RECENTES">Mais recentes</option>
          <option value="ANTIGOS">Mais antigos</option>
        </select>
      </div>

      <div className="mb-4 flex items-center justify-between text-xs text-gray-500">
        <span>{ticketsFiltrados.length} resultado(s)</span>
        {(busca || filtroMotivo !== "TODOS" || ordenacao !== "RECENTES") && (
          <button
            type="button"
            onClick={() => {
              setBusca("")
              setFiltroMotivo("TODOS")
              setOrdenacao("RECENTES")
            }}
            className="font-medium text-[#C41E73] hover:underline"
          >
            Limpar filtros extras
          </button>
        )}
      </div>

      {ticketsFiltrados.length === 0 ? (
        <div className="rounded-xl border border-dashed border-gray-300 bg-white p-10 text-center text-gray-500">
          Nenhum ticket encontrado para esse filtro.
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {ticketsFiltrados.map((ticket) => (
            <Card
              key={ticket.id}
              className={`border p-4 shadow-sm transition hover:shadow-md ${getCardColor(ticket.status)}`}
            >
              <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                <div className="min-w-0 flex-1 space-y-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge
                      className={`flex items-center gap-1 rounded-full border px-2.5 py-1 text-[11px] font-semibold ${getBadgeColor(ticket.status)}`}
                    >
                      {(ticket.status === 'ABERTO' || ticket.status === 'EM_ANDAMENTO') && (
                        <ClockIcon className="h-3.5 w-3.5" />
                      )}
                      {ticket.status === 'FECHADO' && <CheckCircleIcon className="h-3.5 w-3.5" />}
                      {normalizarStatusTicket(ticket.status)}
                    </Badge>

                    <span className="text-xs font-medium text-gray-700">Ticket #{ticket.id}</span>
                    <span className="text-xs text-gray-500">
                      {formatarDataTicket(ticket.dataAbertura)}
                    </span>
                    <span className="text-xs font-medium text-gray-400">
                      {formatarTempoRelativo(ticket.dataAbertura)}
                    </span>
                  </div>

                  <div className="grid grid-cols-1 gap-x-4 gap-y-1.5 text-sm text-gray-700 md:grid-cols-2">
                    <div className="flex min-w-0 items-center gap-2">
                      <UserIcon className="h-4 w-4 text-[#C41E73]" />
                      <span className="truncate font-medium text-gray-900">
                        {ticket.abertoPorNome || ticket.abertoPorUsername}
                      </span>
                    </div>

                    <div className="flex min-w-0 items-center gap-2 text-gray-500">
                      <EnvelopeIcon className="h-4 w-4 text-gray-400" />
                      <span className="truncate">{ticket.abertoPorEmail}</span>
                    </div>

                    <div className="flex min-w-0 items-center gap-2 text-gray-600 md:col-span-2">
                      <TagIcon className="h-4 w-4 text-[#C41E73]" />
                      <span>Motivo: {normalizarMotivoTicket(ticket.motivo)}</span>
                    </div>
                  </div>

                  <div className="rounded-2xl border border-gray-200/80 bg-white/80 px-3 py-2">
                    <p className="text-[11px] font-semibold uppercase tracking-[0.08em] text-gray-500">
                      Mensagem inicial
                    </p>
                    <p className="mt-1 text-sm leading-5 text-gray-700">{resumirMensagem(ticket.descricao)}</p>
                  </div>
                </div>

                <div className="flex w-full flex-col gap-2 sm:flex-row xl:w-56 xl:flex-col">
                  <Button
                    onClick={() => {
                      setTicketSelecionado({ ...ticket, possuiNovaMensagem: false })
                      setTickets((prev) =>
                        prev.map((item) =>
                          item.id === ticket.id ? { ...item, possuiNovaMensagem: false } : item
                        )
                      )
                      setOpenChat(true)
                    }}
                    className="flex w-full items-center justify-center gap-2 bg-[#C41E73] text-white hover:bg-[#a81861]"
                  >
                    <ChatBubbleLeftEllipsisIcon className="h-4 w-4" />
                    Responder
                  </Button>

                  <Button
                    variant="outline"
                    onClick={() => {
                      setTicketSelecionado(ticket)
                      setOpenDetalhes(true)
                    }}
                    className="flex w-full items-center justify-center gap-2 border-gray-300 text-gray-700 hover:bg-gray-100"
                  >
                    <EyeIcon className="h-4 w-4" />
                    Ver Detalhes
                  </Button>

                  {ticket.status !== 'FECHADO' && (
                    <Button
                      variant="outline"
                      onClick={() => fecharTicket(ticket.id)}
                      className="flex w-full items-center justify-center gap-2 border-red-300 text-red-600 hover:bg-red-50"
                    >
                      <XCircleIcon className="h-4 w-4" />
                      Fechar
                    </Button>
                  )}
                </div>
              </div>
              {ticket.possuiNovaMensagem && (
                <div className="mt-3 flex items-center">
                  <span className="inline-flex items-center rounded-full bg-red-100 px-2.5 py-1 text-[11px] font-semibold text-red-600">
                    Nova mensagem
                  </span>
                </div>
              )}
            </Card>
          ))}
        </div>
      )}

      <TicketChatModal open={openChat} onOpenChange={setOpenChat} ticket={ticketSelecionado} />
      <TicketDetailsModal open={openDetalhes} onOpenChange={setOpenDetalhes} ticket={ticketSelecionado} />
    </section>
  )
}
