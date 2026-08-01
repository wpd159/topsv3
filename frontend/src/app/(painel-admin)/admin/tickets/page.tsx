'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { MessageSquareText, RefreshCw, Search } from 'lucide-react'

import TicketChatModal from '../components/ticket-chat-modal'
import TicketDetailsModal from '../components/ticket-details-modal'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  adminSuporteError,
  buscarIndicadoresTickets,
  listarAdminTickets,
  type AdminTicketIndicadores,
  type AdminTicketPagina,
} from '@/lib/admin-suporte-api'

const categorias = [
  ['TODOS', 'Todas as categorias'],
  ['ERRO_NO_SISTEMA', 'Erro no sistema'],
  ['PROBLEMAS_COM_PAGAMENTO', 'Pagamento'],
  ['ACESSO_CONTA', 'Acesso / Conta'],
  ['SUGESTAO', 'Sugestão'],
  ['OUTROS', 'Outros'],
] as const

const statusOptions = [
  ['TODOS', 'Todos'],
  ['ABERTOS', 'Abertos'],
  ['EM_ANDAMENTO', 'Em andamento'],
  ['FECHADOS', 'Fechados'],
] as const

function dataHora(value: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value))
}

export default function AdminTicketsPage() {
  const [termo, setTermo] = useState('')
  const [termoAplicado, setTermoAplicado] = useState('')
  const [categoria, setCategoria] = useState('TODOS')
  const [status, setStatus] = useState('TODOS')
  const [ordenacao, setOrdenacao] = useState('RECENTES')
  const [pagina, setPagina] = useState(0)
  const [tamanho] = useState(20)
  const [dados, setDados] = useState<AdminTicketPagina | null>(null)
  const [indicadores, setIndicadores] = useState<AdminTicketIndicadores | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [ticketId, setTicketId] = useState<string | null>(null)
  const [detailsOpen, setDetailsOpen] = useState(false)
  const [chatOpen, setChatOpen] = useState(false)

  const filtros = useMemo(() => ({
    termo: termoAplicado,
    categoria,
    status,
    ordenacao,
    pagina,
    tamanho,
  }), [categoria, ordenacao, pagina, status, tamanho, termoAplicado])

  const sincronizarUrl = useCallback(() => {
    const query = new URLSearchParams()
    if (termoAplicado) query.set('termo', termoAplicado)
    if (categoria !== 'TODOS') query.set('categoria', categoria)
    if (status !== 'TODOS') query.set('status', status)
    if (ordenacao !== 'RECENTES') query.set('ordenacao', ordenacao)
    if (pagina > 0) query.set('page', String(pagina))
    const suffix = query.size ? `?${query.toString()}` : ''
    window.history.replaceState(null, '', `/admin/tickets${suffix}`)
  }, [categoria, ordenacao, pagina, status, termoAplicado])

  const carregar = useCallback(async (signal?: AbortSignal) => {
    setCarregando(true)
    setErro(null)
    try {
      const [paginaCarregada, contadores] = await Promise.all([
        listarAdminTickets(filtros, signal),
        buscarIndicadoresTickets(signal),
      ])
      setDados(paginaCarregada)
      setIndicadores(contadores)
    } catch (cause) {
      if (!signal?.aborted) setErro(adminSuporteError(cause).message)
    } finally {
      if (!signal?.aborted) setCarregando(false)
    }
  }, [filtros])

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const termoInicial = params.get('termo') || ''
    setTermo(termoInicial)
    setTermoAplicado(termoInicial)
    setCategoria(params.get('categoria') || 'TODOS')
    setStatus(params.get('status') || 'TODOS')
    setOrdenacao(params.get('ordenacao') || 'RECENTES')
    setPagina(Math.max(0, Number(params.get('page') || 0)))
  }, [])

  useEffect(() => {
    sincronizarUrl()
    const controller = new AbortController()
    void carregar(controller.signal)
    return () => controller.abort()
  }, [carregar, sincronizarUrl])

  const abrirDetalhe = (id: string) => {
    setTicketId(id)
    setDetailsOpen(true)
  }

  const abrirResposta = (id: string) => {
    setTicketId(id)
    setDetailsOpen(false)
    setChatOpen(true)
  }

  const contagem = (filtro: string) => {
    if (!indicadores) return '—'
    if (filtro === 'TODOS') return indicadores.total
    if (filtro === 'ABERTOS') return indicadores.abertos
    if (filtro === 'EM_ANDAMENTO') {
      return indicadores.emAtendimento + indicadores.aguardandoUsuario
    }
    return indicadores.resolvidos + indicadores.encerrados
  }

  const vazio = !carregando && !erro && (dados?.itens.length ?? 0) === 0

  return (
    <section className="space-y-5">
      <header className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <div>
          <h1 className="text-2xl font-bold text-zinc-950">Tickets</h1>
          <p className="text-sm text-zinc-600">Fila de suporte e histórico de atendimento.</p>
        </div>
        <Button type="button" variant="outline" onClick={() => void carregar()}>
          <RefreshCw className={`mr-2 h-4 w-4 ${carregando ? 'animate-spin' : ''}`} />
          Atualizar
        </Button>
      </header>

      <div className="grid grid-cols-2 gap-2 lg:grid-cols-4">
        {statusOptions.map(([value, label]) => (
          <button
            key={value}
            type="button"
            onClick={() => {
              setStatus(value)
              setPagina(0)
            }}
            className={`border-l-4 px-4 py-3 text-left transition-colors ${
              status === value
                ? 'border-pink-500 bg-pink-50'
                : 'border-zinc-200 bg-white hover:bg-zinc-50'
            }`}
          >
            <span className="block text-xs font-medium text-zinc-500">{label}</span>
            <span className="mt-1 block text-xl font-semibold text-zinc-950">{contagem(value)}</span>
          </button>
        ))}
      </div>

      <div className="grid gap-3 border-y py-4 lg:grid-cols-[minmax(280px,1fr)_220px_180px_auto]">
        <form
          className="relative"
          onSubmit={(event) => {
            event.preventDefault()
            setTermoAplicado(termo.trim())
            setPagina(0)
          }}
        >
          <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-zinc-400" />
          <Input
            value={termo}
            onChange={(event) => setTermo(event.target.value)}
            placeholder="Buscar ticket, nome ou e-mail"
            className="pl-9"
          />
        </form>
        <Select value={categoria} onValueChange={(value) => {
          setCategoria(value)
          setPagina(0)
        }}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            {categorias.map(([value, label]) => (
              <SelectItem key={value} value={value}>{label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={ordenacao} onValueChange={(value) => {
          setOrdenacao(value)
          setPagina(0)
        }}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="RECENTES">Mais recentes</SelectItem>
            <SelectItem value="ANTIGOS">Mais antigos</SelectItem>
          </SelectContent>
        </Select>
        <Button
          type="button"
          variant="outline"
          onClick={() => {
            setTermo('')
            setTermoAplicado('')
            setCategoria('TODOS')
            setStatus('TODOS')
            setOrdenacao('RECENTES')
            setPagina(0)
          }}
        >
          Limpar
        </Button>
      </div>

      {erro ? (
        <div className="flex flex-col items-start justify-between gap-3 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-800 sm:flex-row sm:items-center">
          <p>{erro}</p>
          <Button type="button" size="sm" variant="outline" onClick={() => void carregar()}>
            Tentar novamente
          </Button>
        </div>
      ) : null}

      {carregando ? <p className="py-16 text-center text-sm text-zinc-500">Carregando tickets...</p> : null}

      {vazio ? (
        <div className="flex min-h-64 flex-col items-center justify-center gap-3 border-y py-12 text-center">
          <MessageSquareText className="h-8 w-8 text-zinc-400" />
          <div>
            <p className="font-medium text-zinc-900">Nenhum ticket encontrado</p>
            <p className="text-sm text-zinc-500">A fila está vazia para os filtros aplicados.</p>
          </div>
        </div>
      ) : null}

      <div className="divide-y border-y">
        {dados?.itens.map((ticket) => {
          const finalizado = ['RESOLVIDO', 'ENCERRADO'].includes(ticket.status)
          return (
            <article
              key={ticket.id}
              className="grid gap-3 px-1 py-4 sm:px-3 lg:grid-cols-[minmax(0,1.3fr)_minmax(180px,.7fr)_160px_auto] lg:items-center"
            >
              <div className="min-w-0">
                <p className="truncate font-semibold text-zinc-950">{ticket.assunto}</p>
                <p className="mt-1 line-clamp-2 text-sm text-zinc-600">{ticket.mensagemInicial}</p>
                <p className="mt-1 text-xs text-zinc-400">
                  {ticket.protocolo} · {ticket.categoriaRotulo} · {ticket.totalMensagens} mensagens
                </p>
              </div>
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{ticket.usuarioNome}</p>
                <p className="truncate text-xs text-zinc-500">{ticket.usuarioEmail}</p>
              </div>
              <div>
                <span className={`inline-flex rounded-md border px-2 py-1 text-xs font-medium ${
                  finalizado
                    ? 'border-zinc-200 bg-zinc-100 text-zinc-700'
                    : 'border-amber-200 bg-amber-50 text-amber-800'
                }`}>
                  {ticket.statusRotulo}
                </span>
                <p className="mt-1 text-xs text-zinc-500">{dataHora(ticket.atualizadoEm)}</p>
              </div>
              <div className="flex flex-wrap gap-2 lg:justify-end">
                {!finalizado ? (
                  <Button type="button" size="sm" onClick={() => abrirResposta(ticket.id)}>
                    Responder
                  </Button>
                ) : null}
                <Button type="button" size="sm" variant="outline" onClick={() => abrirDetalhe(ticket.id)}>
                  Ver detalhes
                </Button>
              </div>
            </article>
          )
        })}
      </div>

      {(dados?.totalPaginas ?? 0) > 1 ? (
        <div className="flex items-center justify-between">
          <Button
            type="button"
            variant="outline"
            disabled={pagina === 0}
            onClick={() => setPagina((value) => Math.max(0, value - 1))}
          >
            Anterior
          </Button>
          <span className="text-sm text-zinc-600">
            Página {pagina + 1} de {dados?.totalPaginas}
          </span>
          <Button
            type="button"
            variant="outline"
            disabled={pagina + 1 >= (dados?.totalPaginas ?? 0)}
            onClick={() => setPagina((value) => value + 1)}
          >
            Próxima
          </Button>
        </div>
      ) : null}

      <TicketDetailsModal
        open={detailsOpen}
        onOpenChange={setDetailsOpen}
        ticketId={ticketId}
        onOpenChat={() => {
          setDetailsOpen(false)
          setChatOpen(true)
        }}
        onChanged={() => {
          void carregar()
          window.dispatchEvent(new Event('suporte-ticket-changed'))
        }}
      />
      <TicketChatModal
        open={chatOpen}
        onOpenChange={setChatOpen}
        ticketId={ticketId}
        onChanged={() => {
          void carregar()
          window.dispatchEvent(new Event('suporte-ticket-changed'))
        }}
      />
    </section>
  )
}
