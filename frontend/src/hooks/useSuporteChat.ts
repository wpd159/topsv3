'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'

import {
  criarTicket,
  detalharTicket,
  encerrarTicket as encerrarTicketApi,
  listarTickets,
  novaIdempotencyKey,
  responderTicket,
  suporteError,
  type TicketDetalhe,
} from '@/lib/suporte-api'
import type { ApiContractError } from '@/lib/api-contract'

export interface Mensagem {
  id: string
  texto: string
  data: string
  remetente: 'usuario' | 'suporte'
}

export function useSuporteChat(usuario?: unknown) {
  const [detalhe, setDetalhe] = useState<TicketDetalhe | null>(null)
  const [novaMensagem, setNovaMensagem] = useState('')
  const [contractError, setContractError] = useState<ApiContractError | null>(null)
  const [processando, setProcessando] = useState(false)
  const consultaEmCurso = useRef<Promise<void> | null>(null)
  const mutacaoEmCurso = useRef(false)
  const chaveCriacao = useRef<string | null>(null)
  const chaveResposta = useRef<string | null>(null)

  const carregarAberto = useCallback(async () => {
    if (!usuario) return
    if (consultaEmCurso.current) return consultaEmCurso.current
    consultaEmCurso.current = (async () => {
      try {
        const pagina = await listarTickets('ABERTOS', 0)
        if (pagina.itens[0]) {
          setDetalhe(await detalharTicket(pagina.itens[0].id))
        } else {
          setDetalhe(null)
        }
        setContractError(null)
      } catch (cause) {
        setContractError(suporteError(cause))
      } finally {
        consultaEmCurso.current = null
      }
    })()
    return consultaEmCurso.current
  }, [usuario])

  useEffect(() => {
    void carregarAberto()
    const timer = window.setInterval(() => {
      if (!document.hidden) void carregarAberto()
    }, 10_000)
    return () => window.clearInterval(timer)
  }, [carregarAberto])

  const abrirChamado = async (assunto: string, categoria: string, descricao: string) => {
    if (mutacaoEmCurso.current) return
    mutacaoEmCurso.current = true
    setProcessando(true)
    try {
      chaveCriacao.current ??= novaIdempotencyKey('suporte-fab')
      setDetalhe(await criarTicket(
        { assunto, categoria, descricao },
        chaveCriacao.current,
      ))
      chaveCriacao.current = null
      setContractError(null)
      toast.success('Ticket aberto com sucesso.')
    } catch (cause) {
      const error = suporteError(cause)
      setContractError(error)
      toast.error(error.message)
    } finally {
      mutacaoEmCurso.current = false
      setProcessando(false)
    }
  }

  const enviarMensagem = async () => {
    if (!detalhe || !novaMensagem.trim() || mutacaoEmCurso.current) return
    mutacaoEmCurso.current = true
    setProcessando(true)
    try {
      chaveResposta.current ??= novaIdempotencyKey('suporte-fab-resposta')
      await responderTicket(
        detalhe.ticket.id,
        novaMensagem,
        chaveResposta.current,
      )
      chaveResposta.current = null
      setNovaMensagem('')
      setDetalhe(await detalharTicket(detalhe.ticket.id))
      setContractError(null)
    } catch (cause) {
      const error = suporteError(cause)
      setContractError(error)
      toast.error(error.message)
    } finally {
      mutacaoEmCurso.current = false
      setProcessando(false)
    }
  }

  const encerrarTicket = async () => {
    if (!detalhe || mutacaoEmCurso.current) return
    mutacaoEmCurso.current = true
    setProcessando(true)
    try {
      await encerrarTicketApi(detalhe.ticket.id)
      setDetalhe(null)
      setContractError(null)
      toast.success('Ticket encerrado.')
    } catch (cause) {
      const error = suporteError(cause)
      setContractError(error)
      toast.error(error.message)
    } finally {
      mutacaoEmCurso.current = false
      setProcessando(false)
    }
  }

  const mensagens: Mensagem[] = (detalhe?.mensagens ?? []).map((mensagem) => ({
    id: mensagem.id,
    texto: mensagem.corpo,
    data: new Intl.DateTimeFormat('pt-BR', {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(mensagem.criadoEm)),
    remetente: mensagem.minha ? 'usuario' : 'suporte',
  }))

  return {
    ticketAberto: Boolean(detalhe && !['RESOLVIDO', 'ENCERRADO'].includes(detalhe.ticket.status)),
    mensagens,
    novaMensagem,
    setNovaMensagem,
    abrirChamado,
    enviarMensagem,
    encerrarTicket,
    setTicketAberto: (aberto: boolean) => {
      if (!aberto) setDetalhe(null)
    },
    setMensagens: () => undefined,
    contractError,
    processando,
  }
}
