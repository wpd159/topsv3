// app/meus-tickets/page.tsx
'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'
import { Button } from '@/components/ui/button'
import {
  ArrowRightIcon,
  ChatBubbleLeftRightIcon,
  CheckCircleIcon,
  ClockIcon,
  PaperAirplaneIcon,
  UserCircleIcon,
  XCircleIcon,
} from '@heroicons/react/24/solid'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { useAuth } from '@/context/AuthContext'
import { toast } from 'sonner'

type TicketStatusResumo = 'ABERTO' | 'ENCERRADO'
type TicketStatusDetalhe = 'ABERTO' | 'EM_ANDAMENTO' | 'FECHADO'
type RoleMensagem = 'USER' | 'ADMIN' | 'MODERADOR' | null

type TicketResumoDTO = {
  id: number
  assunto: string
  status: TicketStatusResumo
  atendente?: string | null
  criadoEm: string
  ultimaInteracao?: string | null
  ultimaMensagemEnviadaPorRole?: RoleMensagem
}

type SuporteDetalhadoDTO = {
  id: number
  assunto: string
  status: TicketStatusDetalhe
  atendente?: string | null
  criadoEm: string
  ultimaInteracao?: string | null
  mensagens: Array<{
    id: number
    conteudo: string
    enviadoEm: string
    enviadoPorId?: number | null
    enviadoPorRole?: RoleMensagem
  }>
}

type TicketCardVM = {
  id: string
  assunto: string
  status: TicketStatusResumo
  atendente: string
  criadoEm: string
  ultimaInteracao?: string
  ultimaMensagemEnviadaPorRole?: RoleMensagem
}

type MensagemVM = {
  id: string
  ticketId: string
  remetente: 'usuario' | 'atendente'
  texto: string
  data: string
}

type LastSeenByTicket = Record<string, string>

const LAST_SEEN_STORAGE_KEY = 'meus-tickets-last-seen-v1'

const toUiFromDetalhe = (status: TicketStatusDetalhe): TicketStatusResumo =>
  status === 'FECHADO' ? 'ENCERRADO' : 'ABERTO'

const safeIso = (value?: string | null) => (typeof value === 'string' ? value : '')

const formatDateBR = (iso?: string | null) => {
  if (!iso) return ''
  const normalized = iso.replace(' ', 'T')
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('pt-BR')
}

const formatRemetente = (
  role?: RoleMensagem,
  enviadoPorId?: number | null,
  usuarioId?: number
): 'usuario' | 'atendente' => {
  if (role === 'USER' || (usuarioId != null && enviadoPorId === usuarioId)) {
    return 'usuario'
  }
  return 'atendente'
}

const readLastSeenMap = (): LastSeenByTicket => {
  if (typeof window === 'undefined') return {}
  try {
    const raw = window.localStorage.getItem(LAST_SEEN_STORAGE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

const writeLastSeenMap = (value: LastSeenByTicket) => {
  if (typeof window === 'undefined') return
  window.localStorage.setItem(LAST_SEEN_STORAGE_KEY, JSON.stringify(value))
}

const getApiErrorMessage = async (res: Response) => {
  try {
    const data = await res.json()
    if (typeof data?.message === 'string' && data.message.trim()) {
      return data.message.trim()
    }
  } catch {
  }

  return `Erro ${res.status}`
}

export default function MeusTicketsPage() {
  const { usuario } = useAuth()
  const [filtro, setFiltro] = useState<'todos' | 'ABERTO' | 'ENCERRADO'>('todos')
  const [loading, setLoading] = useState(true)
  const [tickets, setTickets] = useState<TicketCardVM[]>([])
  const [lastSeenByTicket, setLastSeenByTicket] = useState<LastSeenByTicket>({})
  const [openChatTicketId, setOpenChatTicketId] = useState<string | null>(null)
  const [mensagens, setMensagens] = useState<MensagemVM[]>([])
  const [novaMensagem, setNovaMensagem] = useState('')
  const [sending, setSending] = useState(false)

  const endRef = useRef<HTMLDivElement | null>(null)
  const stompClientRef = useRef<Client | null>(null)
  const knownMessageIdsRef = useRef<Set<string>>(new Set())
  const openChatTicketIdRef = useRef<string | null>(null)
  const usuarioIdRef = useRef<number | undefined>(undefined)

  useEffect(() => {
    setLastSeenByTicket(readLastSeenMap())
  }, [])

  useEffect(() => {
    openChatTicketIdRef.current = openChatTicketId
  }, [openChatTicketId])

  useEffect(() => {
    usuarioIdRef.current = typeof usuario?.id === 'number' ? usuario.id : undefined
  }, [usuario?.id])

  const marcarTicketComoLido = useCallback((ticketId: string, seenAt?: string) => {
    const resolvedSeenAt = safeIso(seenAt) || new Date().toISOString()
    setLastSeenByTicket((prev) => {
      const next = { ...prev, [ticketId]: resolvedSeenAt }
      writeLastSeenMap(next)
      return next
    })
  }, [])

  const aplicarResumoNaLista = useCallback((data: TicketResumoDTO[]) => {
    const mapped: TicketCardVM[] = data.map((ticket) => ({
      id: String(ticket.id),
      assunto: ticket.assunto,
      status: ticket.status,
      atendente: ticket.atendente ?? '-',
      criadoEm: safeIso(ticket.criadoEm),
      ultimaInteracao: safeIso(ticket.ultimaInteracao) || undefined,
      ultimaMensagemEnviadaPorRole: ticket.ultimaMensagemEnviadaPorRole ?? null,
    }))

    setTickets(mapped)
  }, [])

  const loadTickets = useCallback(async () => {
    if (!usuario?.id) {
      setTickets([])
      setLoading(false)
      return
    }

    try {
      setLoading(true)
      const res = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL}/suporte/usuario/${usuario.id}/tickets-resumo`,
        { credentials: 'include', cache: 'no-store' }
      )

      if (res.status === 204) {
        setTickets([])
        return
      }

      if (!res.ok) throw new Error(`Erro ${res.status}`)

      const data: TicketResumoDTO[] = await res.json()
      aplicarResumoNaLista(data)
    } catch {
      setTickets([])
    } finally {
      setLoading(false)
    }
  }, [aplicarResumoNaLista, usuario?.id])

  useEffect(() => {
    loadTickets()
  }, [loadTickets])

  useEffect(() => {
    const handler = () => {
      loadTickets()
    }

    window.addEventListener('suporte-ticket-changed', handler as EventListener)
    return () => window.removeEventListener('suporte-ticket-changed', handler as EventListener)
  }, [loadTickets])

  const ticketIdsKey = useMemo(() => tickets.map((ticket) => ticket.id).sort().join(','), [tickets])

  useEffect(() => {
    if (!usuario?.id || !ticketIdsKey) return

    const socket = new SockJS(`${process.env.NEXT_PUBLIC_API_URL}/ws-suporte`)
    const client = new Client({
      webSocketFactory: () => socket as any,
      reconnectDelay: 3000,
      onConnect: () => {
        ticketIdsKey.split(',').forEach((ticketId) => {
          if (!ticketId) return

          client.subscribe(`/topic/suporte/${ticketId}`, (frame) => {
            if (!frame.body) return

            const data = JSON.parse(frame.body)
            const currentTicketId = String(data.suporteId ?? ticketId)
            const messageId = String(data.id)

            setTickets((prev) =>
              prev.map((ticket) =>
                ticket.id === currentTicketId
                  ? {
                      ...ticket,
                      status: 'ABERTO',
                      atendente:
                        data.enviadoPorRole === 'ADMIN' || data.enviadoPorRole === 'MODERADOR'
                          ? data.enviadoPorNome || ticket.atendente
                          : ticket.atendente,
                      ultimaInteracao: safeIso(data.enviadoEm) || ticket.ultimaInteracao,
                      ultimaMensagemEnviadaPorRole: data.enviadoPorRole ?? null,
                    }
                  : ticket
              )
            )

            if (openChatTicketIdRef.current === currentTicketId) {
              if (!knownMessageIdsRef.current.has(messageId)) {
                knownMessageIdsRef.current.add(messageId)
                setMensagens((prev) => [
                  ...prev,
                  {
                    id: messageId,
                    ticketId: currentTicketId,
                    remetente: formatRemetente(
                      data.enviadoPorRole ?? null,
                      data.enviadoPorId,
                      usuarioIdRef.current
                    ),
                    texto: String(data.conteudo ?? ''),
                    data: formatDateBR(data.enviadoEm),
                  },
                ])
              }

              marcarTicketComoLido(currentTicketId, safeIso(data.enviadoEm))
            }
          })
        })
      },
    })

    client.activate()
    stompClientRef.current = client

    return () => {
      client.deactivate()
      stompClientRef.current = null
    }
  }, [marcarTicketComoLido, ticketIdsKey, usuario?.id])

  const ticketsFiltrados = useMemo(() => {
    if (filtro === 'todos') return tickets
    return tickets.filter((ticket) => ticket.status === filtro)
  }, [tickets, filtro])

  const abrirChat = useCallback(
    async (ticketId: string) => {
      setOpenChatTicketId(ticketId)

      try {
        const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/suporte/${ticketId}`, {
          credentials: 'include',
          cache: 'no-store',
        })

        if (!res.ok) throw new Error(`Erro ${res.status}`)

        const dto: SuporteDetalhadoDTO = await res.json()

        setTickets((prev) =>
          prev.map((ticket) =>
            ticket.id === ticketId
              ? {
                  ...ticket,
                  status: toUiFromDetalhe(dto.status),
                  atendente: dto.atendente ?? ticket.atendente ?? '-',
                  ultimaInteracao: safeIso(dto.ultimaInteracao) || ticket.ultimaInteracao,
                  criadoEm: safeIso(dto.criadoEm) || ticket.criadoEm,
                  ultimaMensagemEnviadaPorRole:
                    dto.mensagens.length > 0
                      ? dto.mensagens[dto.mensagens.length - 1].enviadoPorRole ?? null
                      : ticket.ultimaMensagemEnviadaPorRole ?? null,
                }
              : ticket
          )
        )

        const loadedMessages: MensagemVM[] = Array.isArray(dto.mensagens)
          ? dto.mensagens.map((mensagem) => ({
              id: String(mensagem.id),
              ticketId: String(dto.id),
              remetente: formatRemetente(
                mensagem.enviadoPorRole ?? null,
                mensagem.enviadoPorId,
                typeof usuario?.id === 'number' ? usuario.id : undefined
              ),
              texto: String(mensagem.conteudo ?? ''),
              data: formatDateBR(mensagem.enviadoEm),
            }))
          : []

        loadedMessages.forEach((mensagem) => knownMessageIdsRef.current.add(mensagem.id))
        setMensagens(loadedMessages)
        marcarTicketComoLido(ticketId, safeIso(dto.ultimaInteracao))
      } catch {
        setMensagens([])
      }
    },
    [marcarTicketComoLido, usuario?.id]
  )

  const enviarMensagem = async () => {
    if (!openChatTicketId || !usuario?.id || !novaMensagem.trim() || ticketAberto?.status === 'ENCERRADO') {
      return
    }

    const optimisticMessage: MensagemVM = {
      id: crypto.randomUUID(),
      ticketId: openChatTicketId,
      remetente: 'usuario',
      texto: novaMensagem.trim(),
      data: formatDateBR(new Date().toISOString()),
    }

    setMensagens((current) => [...current, optimisticMessage])
    setNovaMensagem('')
    setSending(true)

    try {
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/suporte/${openChatTicketId}/mensagem`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ usuarioId: String(usuario.id), conteudo: optimisticMessage.texto }),
      })

      if (!res.ok) {
        throw new Error(await getApiErrorMessage(res))
      }

      const saved = await res.json()
      const savedMessageId = String(saved.id)
      knownMessageIdsRef.current.add(savedMessageId)

      setMensagens((current) => {
        const withoutOptimistic = current.filter((mensagem) => mensagem.id !== optimisticMessage.id)
        if (withoutOptimistic.some((mensagem) => mensagem.id === savedMessageId)) {
          return withoutOptimistic
        }

        return [
          ...withoutOptimistic,
          {
            id: savedMessageId,
            ticketId: openChatTicketId,
            remetente: 'usuario',
            texto: String(saved.conteudo ?? optimisticMessage.texto),
            data: formatDateBR(saved.enviadoEm),
          },
        ]
      })

      setTickets((prev) =>
        prev.map((ticket) =>
          ticket.id === openChatTicketId
            ? {
                ...ticket,
                status: 'ABERTO',
                ultimaInteracao: safeIso(saved.enviadoEm) || new Date().toISOString(),
                ultimaMensagemEnviadaPorRole: saved.enviadoPorRole ?? 'USER',
              }
            : ticket
        )
      )

      marcarTicketComoLido(openChatTicketId, safeIso(saved.enviadoEm))
    } catch (error) {
      setMensagens((current) => current.filter((mensagem) => mensagem.id !== optimisticMessage.id))
      setNovaMensagem(optimisticMessage.texto)

      const message =
        error instanceof Error && error.message
          ? error.message
          : 'Nao foi possivel enviar sua mensagem.'

      if (message.includes('encerrado')) {
        setTickets((prev) =>
          prev.map((ticket) =>
            ticket.id === openChatTicketId
              ? {
                  ...ticket,
                  status: 'ENCERRADO',
                }
              : ticket
          )
        )
      }

      toast.error(message)
    } finally {
      setSending(false)
    }
  }

  const abrirNovoTicket = () => {
    window.dispatchEvent(new Event('open-suporte-ticket'))
  }

  const ticketAberto = useMemo(
    () => tickets.find((ticket) => ticket.id === openChatTicketId) || null,
    [tickets, openChatTicketId]
  )
  const ticketEncerrado = ticketAberto?.status === 'ENCERRADO'

  const mensagensDoTicket = useMemo(
    () => mensagens.filter((mensagem) => mensagem.ticketId === openChatTicketId),
    [mensagens, openChatTicketId]
  )

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [mensagensDoTicket.length, openChatTicketId])

  return (
    <section className="mx-auto px-6 py-10">
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <ChatBubbleLeftRightIcon className="size-6 text-[#FC1EAD]" />
            <h1 className="text-2xl font-bold tracking-tight">Meus Tickets</h1>
          </div>
          <p className="mt-1 text-sm text-gray-600">
            Historico de atendimentos. Abra um ticket novo ou continue um existente.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {(['todos', 'ABERTO', 'ENCERRADO'] as const).map((key) => {
            const active = filtro === key
            return (
              <button
                key={key}
                onClick={() => setFiltro(key)}
                className={`rounded-full border px-3 py-1.5 text-sm transition ${
                  active
                    ? 'border-[#FC1EAD] bg-[#FC1EAD]/10 text-[#FC1EAD]'
                    : 'border-gray-300 text-gray-700 hover:bg-gray-50'
                }`}
              >
                {key === 'todos' ? 'Todos' : key === 'ABERTO' ? 'Abertos' : 'Encerrados'}
              </button>
            )
          })}

          <Button
            onClick={abrirNovoTicket}
            className="bg-[#FC1EAD] py-1 font-semibold text-white hover:bg-[#e01a9a]"
          >
            Abrir ticket
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="rounded-xl border py-16 text-center">Carregando...</div>
      ) : ticketsFiltrados.length === 0 ? (
        <div className="rounded-xl border py-16 text-center">
          <p className="text-gray-600">
            {usuario?.id
              ? 'Nenhum ticket encontrado nesse filtro.'
              : 'Voce precisa estar logado para ver seus tickets.'}
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {ticketsFiltrados.map((ticket) => (
            <TicketCard
              key={ticket.id}
              ticket={ticket}
              hasNovaResposta={hasNovaResposta(ticket, lastSeenByTicket)}
              onOpenChat={abrirChat}
            />
          ))}
        </div>
      )}

      <Dialog
        open={!!openChatTicketId}
        onOpenChange={(open) => {
          if (!open) setOpenChatTicketId(null)
        }}
      >
        <DialogContent className="overflow-hidden rounded-xl p-0 sm:max-w-md">
          {ticketAberto ? (
            <div className="flex h-[480px] flex-col">
              <div className="border-b px-4 py-3">
                <DialogHeader>
                  <DialogTitle className="font-semibold text-gray-800">
                    {ticketAberto.assunto} <span className="text-gray-400">#{ticketAberto.id}</span>
                  </DialogTitle>
                  <div className="mt-1 flex items-center gap-2 text-xs text-gray-500">
                    <UserCircleIcon className="size-4 text-gray-400" />
                    <span>
                      Atendente: <strong className="text-gray-700">{ticketAberto.atendente}</strong>
                    </span>
                  </div>
                </DialogHeader>
              </div>

              <div className="flex-1 space-y-3 overflow-y-auto bg-gray-50 p-4">
                {mensagensDoTicket.map((mensagem) => (
                  <div
                    key={mensagem.id}
                    className={`flex ${
                      mensagem.remetente === 'usuario' ? 'justify-end' : 'justify-start'
                    }`}
                  >
                    <div
                      className={`max-w-[75%] rounded-lg px-3 py-2 text-sm ${
                        mensagem.remetente === 'usuario' ? 'bg-[#FC1EAD]/20' : 'border bg-white'
                      }`}
                    >
                      <p className="whitespace-pre-wrap break-words">{mensagem.texto}</p>
                      <span className="mt-1 block text-[10px] text-gray-400">{mensagem.data}</span>
                    </div>
                  </div>
                ))}
                <div ref={endRef} />
              </div>

              <div className="space-y-2 border-t bg-white p-3">
                {ticketEncerrado && (
                  <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                    Este ticket foi encerrado e nao aceita novas mensagens.
                  </p>
                )}
                <Textarea
                  placeholder="Digite sua mensagem..."
                  value={novaMensagem}
                  onChange={(event) => setNovaMensagem(event.target.value)}
                  rows={2}
                  disabled={ticketEncerrado || sending}
                />
                <Button
                  onClick={enviarMensagem}
                  disabled={ticketEncerrado || sending || !novaMensagem.trim()}
                  className="w-full bg-[#FC1EAD] py-5 font-semibold text-white hover:bg-[#e01a9a] disabled:opacity-60"
                >
                  <PaperAirplaneIcon className="mr-2 h-5 w-5 -rotate-45" />
                  {ticketEncerrado ? 'Ticket encerrado' : sending ? 'Enviando...' : 'Enviar'}
                </Button>
              </div>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </section>
  )
}

function TicketCard({
  ticket,
  hasNovaResposta,
  onOpenChat,
}: {
  ticket: TicketCardVM
  hasNovaResposta: boolean
  onOpenChat: (ticketId: string) => void
}) {
  return (
    <div className="w-full rounded-xl border bg-white p-4 shadow-sm transition hover:shadow sm:p-5">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
        <div className="flex-shrink-0">
          <StatusPill status={ticket.status} />
        </div>

        <div className="grid flex-1 grid-cols-1 gap-3 md:grid-cols-3">
          <div>
            <p className="text-sm text-gray-500">Assunto</p>
            <div className="mt-0.5 flex flex-wrap items-center gap-2">
              <h3 className="leading-tight font-semibold text-gray-900">{ticket.assunto}</h3>
              {hasNovaResposta && (
                <span className="inline-flex items-center rounded-full bg-red-100 px-2.5 py-1 text-[11px] font-semibold text-red-600">
                  Nova resposta
                </span>
              )}
            </div>
            <p className="mt-1 text-xs text-gray-500">#{ticket.id}</p>
          </div>

          <div className="flex items-center gap-2">
            <UserCircleIcon className="size-5 text-gray-500" />
            <div>
              <p className="text-sm text-gray-500">Atendente</p>
              <p className="font-medium text-gray-800">{ticket.atendente}</p>
            </div>
          </div>

          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <ClockIcon className="size-5 text-gray-500" />
              <div className="text-sm">
                <p className="text-gray-500">
                  Criado em: <span className="text-gray-800">{formatDateBR(ticket.criadoEm) || '-'}</span>
                </p>
                {ticket.ultimaInteracao && (
                  <p className="text-gray-500">
                    Ultima interacao:{' '}
                    <span className="text-gray-800">{formatDateBR(ticket.ultimaInteracao)}</span>
                  </p>
                )}
              </div>
            </div>

            <Button
              variant="outline"
              onClick={() => onOpenChat(ticket.id)}
              className="border-[#C41E73]/30 text-[#C41E73] hover:bg-[#FC1EAD]/10"
            >
              Abrir
              <ArrowRightIcon className="ml-2 h-4 w-4" />
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}

function StatusPill({ status }: { status: TicketStatusResumo }) {
  const isAberto = status === 'ABERTO'

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium ${
        isAberto ? 'border-emerald-300 bg-emerald-50 text-emerald-700' : 'border-gray-300 bg-gray-100 text-gray-700'
      }`}
    >
      {isAberto ? (
        <CheckCircleIcon className="size-4 text-emerald-600" />
      ) : (
        <XCircleIcon className="size-4 text-gray-600" />
      )}
      {isAberto ? 'Aberto' : 'Encerrado'}
    </span>
  )
}

function hasNovaResposta(ticket: TicketCardVM, lastSeenByTicket: LastSeenByTicket) {
  const role = ticket.ultimaMensagemEnviadaPorRole
  if (role !== 'ADMIN' && role !== 'MODERADOR') {
    return false
  }

  if (!ticket.ultimaInteracao) {
    return false
  }

  const seenAt = lastSeenByTicket[ticket.id]
  if (!seenAt) {
    return true
  }

  const lastInteractionTime = new Date(ticket.ultimaInteracao).getTime()
  const seenAtTime = new Date(seenAt).getTime()

  if (Number.isNaN(lastInteractionTime) || Number.isNaN(seenAtTime)) {
    return true
  }

  return lastInteractionTime > seenAtTime
}
