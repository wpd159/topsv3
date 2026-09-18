'use client'

import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { CheckCircle2, Clock3, LifeBuoy, MessageSquareText, RefreshCw } from 'lucide-react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import {
  criarTicket,
  detalharTicket,
  encerrarTicket,
  listarTickets,
  novaIdempotencyKey,
  responderTicket,
  suporteError,
  type TicketDetalhe,
  type TicketPagina,
  type TicketResumo,
} from '@/lib/suporte-api'
import { ApiContractError } from '@/lib/api-contract'

type Grupo = 'TODOS' | 'ABERTOS' | 'ENCERRADOS'

const categorias = [
  ['ERRO_NO_SISTEMA', 'Erro no sistema'],
  ['PROBLEMAS_COM_PAGAMENTO', 'Pagamento'],
  ['ACESSO_CONTA', 'Acesso / Conta'],
  ['SUGESTAO', 'Sugestão'],
  ['OUTROS', 'Outros'],
] as const

function dataHora(value: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value))
}

function StatusBadge({ ticket }: { ticket: TicketResumo }) {
  const encerrado = ['RESOLVIDO', 'ENCERRADO'].includes(ticket.status)
  return (
    <span className={`inline-flex items-center gap-1 rounded-md border px-2 py-1 text-xs font-medium ${
      encerrado
        ? 'border-zinc-200 bg-zinc-100 text-zinc-700'
        : 'border-emerald-200 bg-emerald-50 text-emerald-700'
    }`}>
      {encerrado ? <CheckCircle2 className="h-3.5 w-3.5" /> : <Clock3 className="h-3.5 w-3.5" />}
      {ticket.statusRotulo}
    </span>
  )
}

export default function MeusTicketsPage() {
  const [grupo, setGrupo] = useState<Grupo>('TODOS')
  const [pagina, setPagina] = useState(0)
  const [dados, setDados] = useState<TicketPagina | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [erroFormulario, setErroFormulario] = useState<ApiContractError | null>(null)
  const [confirmacao, setConfirmacao] = useState<string | null>(null)
  const [novoAberto, setNovoAberto] = useState(false)
  const [detalheAberto, setDetalheAberto] = useState(false)
  const [detalhe, setDetalhe] = useState<TicketDetalhe | null>(null)
  const [carregandoDetalhe, setCarregandoDetalhe] = useState(false)
  const [assunto, setAssunto] = useState('')
  const [categoria, setCategoria] = useState('OUTROS')
  const [descricao, setDescricao] = useState('')
  const [resposta, setResposta] = useState('')
  const [mutando, setMutando] = useState(false)
  const mutacaoEmCurso = useRef(false)
  const chaveCriacao = useRef<string | null>(null)
  const chaveResposta = useRef<string | null>(null)
  const intentConsumida = useRef(false)

  const carregar = useCallback(async (signal?: AbortSignal) => {
    setCarregando(true)
    setErro(null)
    try {
      setDados(await listarTickets(grupo, pagina, signal))
    } catch (cause) {
      if (signal?.aborted) return
      setErro(suporteError(cause).message)
    } finally {
      if (!signal?.aborted) setCarregando(false)
    }
  }, [grupo, pagina])

  useEffect(() => {
    const controller = new AbortController()
    void carregar(controller.signal)
    return () => controller.abort()
  }, [carregar])

  useEffect(() => {
    const atualizar = () => void carregar()
    window.addEventListener('suporte-ticket-changed', atualizar)
    return () => window.removeEventListener('suporte-ticket-changed', atualizar)
  }, [carregar])

  useEffect(() => {
    if (intentConsumida.current || typeof window === 'undefined') return
    intentConsumida.current = true
    const url = new URL(window.location.href)
    if (url.searchParams.get('novo') !== '1') return
    const categoriaIntent = url.searchParams.get('categoria')
    if (categorias.some(([value]) => value === categoriaIntent)) {
      setCategoria(categoriaIntent as (typeof categorias)[number][0])
    }
    setErroFormulario(null)
    setNovoAberto(true)
    url.searchParams.delete('novo')
    url.searchParams.delete('categoria')
    window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}${url.hash}`)
  }, [])

  useEffect(() => {
    if (!detalheAberto || !detalhe?.ticket.id) return
    let consultaEmCurso = false
    const atualizar = async () => {
      if (consultaEmCurso || document.hidden) return
      consultaEmCurso = true
      try {
        setDetalhe(await detalharTicket(detalhe.ticket.id))
      } catch {
        // Falhas continuam visiveis nas acoes explicitas de carregamento e retry.
      } finally {
        consultaEmCurso = false
      }
    }
    const timer = window.setInterval(() => void atualizar(), 10_000)
    return () => window.clearInterval(timer)
  }, [detalhe?.ticket.id, detalheAberto])

  const abrirDetalhe = async (ticketId: string) => {
    setDetalheAberto(true)
    setCarregandoDetalhe(true)
    setErro(null)
    try {
      setDetalhe(await detalharTicket(ticketId))
      window.dispatchEvent(new Event('suporte-ticket-changed'))
      void carregar()
    } catch (cause) {
      setErro(suporteError(cause).message)
      setDetalheAberto(false)
    } finally {
      setCarregandoDetalhe(false)
    }
  }

  const criar = async (event: FormEvent) => {
    event.preventDefault()
    if (mutacaoEmCurso.current) return
    const assuntoSeguro = assunto.trim()
    const descricaoSegura = descricao.trim()
    if (assuntoSeguro.length < 6 || descricaoSegura.length < 10) {
      setErroFormulario(new ApiContractError(
        'Informe um assunto com pelo menos 6 caracteres e uma descrição com pelo menos 10 caracteres.',
        'INVALID_REQUEST',
        422,
      ))
      return
    }
    mutacaoEmCurso.current = true
    setMutando(true)
    setErroFormulario(null)
    setConfirmacao(null)
    try {
      chaveCriacao.current ??= novaIdempotencyKey('suporte-criar')
      const criado = await criarTicket(
        { assunto: assuntoSeguro, categoria, descricao: descricaoSegura },
        chaveCriacao.current,
      )
      chaveCriacao.current = null
      setAssunto('')
      setCategoria('OUTROS')
      setDescricao('')
      setNovoAberto(false)
      setDetalhe(criado)
      setDetalheAberto(true)
      setGrupo('TODOS')
      setPagina(0)
      setDados(await listarTickets('TODOS', 0))
      setCarregando(false)
      window.dispatchEvent(new Event('suporte-ticket-changed'))
      const mensagem = `Ticket ${criado.ticket.protocolo} aberto com sucesso. Acompanhe a resposta em Meus tickets.`
      setConfirmacao(mensagem)
      toast.success(mensagem)
    } catch (cause) {
      const contractError = suporteError(cause)
      setErroFormulario(contractError)
      toast.error(contractError.message)
    } finally {
      mutacaoEmCurso.current = false
      setMutando(false)
    }
  }

  const responder = async () => {
    if (!detalhe || !resposta.trim() || mutacaoEmCurso.current) return
    mutacaoEmCurso.current = true
    setMutando(true)
    try {
      chaveResposta.current ??= novaIdempotencyKey('suporte-responder')
      await responderTicket(
        detalhe.ticket.id,
        resposta,
        chaveResposta.current,
      )
      chaveResposta.current = null
      setResposta('')
      setDetalhe(await detalharTicket(detalhe.ticket.id))
      await carregar()
      window.dispatchEvent(new Event('suporte-ticket-changed'))
    } catch (cause) {
      toast.error(suporteError(cause).message)
    } finally {
      mutacaoEmCurso.current = false
      setMutando(false)
    }
  }

  const encerrar = async () => {
    if (!detalhe || mutacaoEmCurso.current) return
    mutacaoEmCurso.current = true
    setMutando(true)
    try {
      setDetalhe(await encerrarTicket(detalhe.ticket.id))
      await carregar()
      window.dispatchEvent(new Event('suporte-ticket-changed'))
      toast.success('Ticket encerrado.')
    } catch (cause) {
      toast.error(suporteError(cause).message)
    } finally {
      mutacaoEmCurso.current = false
      setMutando(false)
    }
  }

  const vazio = useMemo(
    () => !carregando && !erro && (dados?.itens.length ?? 0) === 0,
    [carregando, dados, erro],
  )

  return (
    <section className="mx-auto w-full max-w-6xl space-y-5 px-4 py-6 sm:px-6">
      <header className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <div>
          <h1 className="text-2xl font-bold text-zinc-950">Meus tickets</h1>
          <p className="text-sm text-zinc-600">Acompanhe suas conversas com a equipe de suporte.</p>
        </div>
        <Button type="button" onClick={() => {
          setErroFormulario(null)
          setNovoAberto(true)
        }}>
          <LifeBuoy className="mr-2 h-4 w-4" />
          Abrir ticket
        </Button>
      </header>

      {confirmacao ? (
        <p role="status" className="rounded-md border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
          {confirmacao}
        </p>
      ) : null}

      <div className="flex flex-wrap items-center gap-2 border-b pb-4">
        {([
          ['TODOS', 'Todos'],
          ['ABERTOS', 'Abertos'],
          ['ENCERRADOS', 'Encerrados'],
        ] as const).map(([value, label]) => (
          <Button
            key={value}
            type="button"
            size="sm"
            variant={grupo === value ? 'default' : 'outline'}
            onClick={() => {
              setGrupo(value)
              setPagina(0)
            }}
          >
            {label}
          </Button>
        ))}
        <Button
          type="button"
          size="icon"
          variant="ghost"
          className="ml-auto"
          onClick={() => void carregar()}
          title="Atualizar tickets"
          aria-label="Atualizar tickets"
        >
          <RefreshCw className={`h-4 w-4 ${carregando ? 'animate-spin' : ''}`} />
        </Button>
      </div>

      {erro ? (
        <div className="flex flex-col items-start gap-3 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-800 sm:flex-row sm:items-center sm:justify-between">
          <p>{erro}</p>
          <Button type="button" size="sm" variant="outline" onClick={() => void carregar()}>
            Tentar novamente
          </Button>
        </div>
      ) : null}

      {carregando ? (
        <p className="py-16 text-center text-sm text-zinc-500">Carregando tickets...</p>
      ) : null}

      {vazio ? (
        <div className="flex min-h-64 flex-col items-center justify-center gap-3 border-y py-12 text-center">
          <MessageSquareText className="h-8 w-8 text-zinc-400" />
          <div>
            <p className="font-medium text-zinc-900">Nenhum ticket neste filtro</p>
            <p className="text-sm text-zinc-500">Quando precisar, abra uma conversa com o suporte.</p>
          </div>
        </div>
      ) : null}

      <div className="divide-y border-y">
        {dados?.itens.map((ticket) => (
          <button
            key={ticket.id}
            type="button"
            className="grid w-full gap-3 px-1 py-4 text-left transition-colors hover:bg-zinc-50 sm:grid-cols-[minmax(0,1fr)_auto_auto] sm:items-center sm:px-3"
            onClick={() => void abrirDetalhe(ticket.id)}
          >
            <span className="min-w-0">
              <span className="block truncate font-semibold text-zinc-950">{ticket.assunto}</span>
              <span className="mt-1 block text-xs text-zinc-500">
                {ticket.protocolo} · {ticket.categoriaRotulo} · {ticket.totalMensagens} mensagens
              </span>
            </span>
            <span className="flex flex-wrap items-center gap-2">
              <StatusBadge ticket={ticket} />
              {ticket.naoLidas > 0 ? (
                <span className="rounded-full bg-[#FC1EAD] px-2 py-1 text-xs font-semibold text-white">
                  {ticket.naoLidas > 99 ? '99+' : ticket.naoLidas} nova(s)
                </span>
              ) : null}
            </span>
            <span className="text-xs text-zinc-500 sm:text-right">
              Atualizado {dataHora(ticket.atualizadoEm)}
            </span>
          </button>
        ))}
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

      <Dialog open={novoAberto} onOpenChange={setNovoAberto}>
        <DialogContent className="max-h-[100dvh] overflow-y-auto sm:max-w-lg">
          <form onSubmit={criar} className="space-y-4">
            <DialogHeader>
              <DialogTitle>Abrir ticket</DialogTitle>
              <DialogDescription>Informe o assunto, a categoria e descreva o que aconteceu.</DialogDescription>
            </DialogHeader>
            <div className="space-y-2">
              <Label htmlFor="ticket-subject">Assunto</Label>
              <Input
                id="ticket-subject"
                value={assunto}
                onChange={(event) => setAssunto(event.target.value)}
                maxLength={160}
                minLength={6}
                aria-describedby="ticket-subject-help"
                required
              />
              <p id="ticket-subject-help" className="text-xs text-zinc-500">Mínimo de 6 caracteres.</p>
            </div>
            <div className="space-y-2">
              <Label>Categoria</Label>
              <Select value={categoria} onValueChange={setCategoria}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {categorias.map(([value, label]) => (
                    <SelectItem key={value} value={value}>{label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="ticket-description">Descrição</Label>
              <Textarea
                id="ticket-description"
                value={descricao}
                onChange={(event) => setDescricao(event.target.value)}
                rows={6}
                maxLength={4000}
                minLength={10}
                aria-describedby="ticket-description-help"
                required
              />
              <p id="ticket-description-help" className="text-xs text-zinc-500">Mínimo de 10 caracteres.</p>
            </div>
            {erroFormulario ? (
              <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-900">
                <p>{erroFormulario.message}</p>
                {erroFormulario.requestId ? (
                  <p className="mt-1 text-xs">Identificador: {erroFormulario.requestId}</p>
                ) : null}
              </div>
            ) : null}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setNovoAberto(false)}>
                Cancelar
              </Button>
              <Button type="submit" disabled={mutando}>
                {mutando ? 'Abrindo ticket...' : 'Abrir ticket'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={detalheAberto} onOpenChange={setDetalheAberto}>
        <DialogContent className="max-h-[90dvh] overflow-hidden p-0 sm:max-w-2xl">
          {carregandoDetalhe || !detalhe ? (
            <p className="p-8 text-center text-sm text-zinc-500">Carregando conversa...</p>
          ) : (
            <div className="flex max-h-[90dvh] flex-col">
              <DialogHeader className="border-b p-5 pr-12">
                <DialogTitle>{detalhe.ticket.assunto}</DialogTitle>
                <DialogDescription>
                  {detalhe.ticket.protocolo} · {detalhe.ticket.categoriaRotulo}
                </DialogDescription>
                <div><StatusBadge ticket={detalhe.ticket} /></div>
              </DialogHeader>
              <div className="min-h-48 flex-1 space-y-3 overflow-y-auto bg-zinc-50 p-4 sm:p-5">
                {detalhe.mensagens.map((mensagem) => (
                  <div
                    key={mensagem.id}
                    className={`flex ${mensagem.minha ? 'justify-end' : 'justify-start'}`}
                  >
                    <div className={`max-w-[88%] rounded-md border px-3 py-2 text-sm sm:max-w-[72%] ${
                      mensagem.minha ? 'border-pink-200 bg-pink-50' : 'bg-white'
                    }`}>
                      <p className="text-xs font-semibold text-zinc-600">{mensagem.remetente}</p>
                      <p className="mt-1 whitespace-pre-wrap break-words text-zinc-900">{mensagem.corpo}</p>
                      <p className="mt-1 text-[11px] text-zinc-400">{dataHora(mensagem.criadoEm)}</p>
                    </div>
                  </div>
                ))}
              </div>
              {!['RESOLVIDO', 'ENCERRADO'].includes(detalhe.ticket.status) ? (
                <div className="space-y-3 border-t bg-white p-4">
                  <Textarea
                    value={resposta}
                    onChange={(event) => setResposta(event.target.value)}
                    placeholder="Digite sua resposta"
                    rows={3}
                    maxLength={4000}
                  />
                  <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-between">
                    <Button type="button" variant="outline" disabled={mutando} onClick={() => void encerrar()}>
                      Encerrar ticket
                    </Button>
                    <Button type="button" disabled={mutando || !resposta.trim()} onClick={() => void responder()}>
                      {mutando ? 'Enviando...' : 'Enviar resposta'}
                    </Button>
                  </div>
                </div>
              ) : (
                <p className="border-t bg-white p-4 text-center text-sm text-zinc-500">
                  Este ticket está encerrado e não aceita novas mensagens.
                </p>
              )}
            </div>
          )}
        </DialogContent>
      </Dialog>
    </section>
  )
}
