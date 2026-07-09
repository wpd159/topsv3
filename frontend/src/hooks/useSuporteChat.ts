'use client'

import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'

export interface Mensagem {
  id: number
  texto: string
  data: string
  remetente: 'usuario' | 'suporte'
}

export function useSuporteChat(usuario?: any) {
  const [ticketAberto, setTicketAberto] = useState(false)
  const [ticketId, setTicketId] = useState<number | null>(null)
  const [mensagens, setMensagens] = useState<Mensagem[]>([])
  const [novaMensagem, setNovaMensagem] = useState('')
  const stompClient = useRef<Client | null>(null)
  const idsRecebidos = useRef<Set<number>>(new Set())

  // 🔍 Busca ticket aberto/andamento
  useEffect(() => {
    const verificarTicketExistente = async () => {
      if (!usuario?.id) return
      const url = `${process.env.NEXT_PUBLIC_API_URL}/suporte/usuario/${usuario.id}`

      try {
        const res = await fetch(url, { credentials: 'include' })
        if (res.status === 204) return
        const t = await res.json()
        if (['ABERTO', 'EM_ANDAMENTO'].includes(t.status)) {
          setTicketId(t.id)
          setTicketAberto(true)
          fetchTicket(t.id)
        }
      } catch {
      }
    }
    verificarTicketExistente()
  }, [usuario])

  // 🔗 WebSocket
  useEffect(() => {
    if (!ticketId) return
    const socket = new SockJS(`${process.env.NEXT_PUBLIC_API_URL}/ws-suporte`)
    const client = new Client({
      webSocketFactory: () => socket as any,
      reconnectDelay: 3000,
      onConnect: () => {
        client.subscribe(`/topic/suporte/${ticketId}`, (msg) => {
          if (!msg.body) return
          const data = JSON.parse(msg.body)
          if (idsRecebidos.current.has(data.id)) return
          idsRecebidos.current.add(data.id)
          setMensagens((prev) => [
            ...prev,
            {
              id: data.id,
              texto: data.conteudo,
              data: new Date(data.enviadoEm).toLocaleString('pt-BR'),
              remetente: data.enviadoPorRole === 'USER' ? 'usuario' : 'suporte',
            },
          ])
        })
      },
    })
    client.activate()
    stompClient.current = client
    return () => {
      client.deactivate()
    }
  }, [ticketId])

  // 🧭 Carrega histórico
  const fetchTicket = async (id: number) => {
    try {
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/suporte/${id}`, {
        credentials: 'include',
      })
      const t = await res.json()
      const msgs = (t.mensagens || []).map((m: any) => ({
        id: m.id,
        texto: m.conteudo,
        data: new Date(m.enviadoEm).toLocaleString('pt-BR'),
        remetente: m.enviadoPorRole === 'USER' ? 'usuario' : 'suporte',
      }))
      const inicial = {
        id: 0,
        texto: t.descricao,
        data: new Date(t.dataAbertura).toLocaleString('pt-BR'),
        remetente: 'usuario',
      }
      idsRecebidos.current = new Set([0, ...msgs.map((m: { id: any }) => m.id)])
      setMensagens([inicial, ...msgs])
    } catch {
      toast.error('Erro ao carregar histórico do ticket')
    }
  }

  // ✉️ Abrir novo chamado
  const abrirChamado = async (motivo: string, descricao: string) => {
    if (!usuario?.id) return toast.error('Faça login primeiro')
    const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/suporte/abrir`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ usuarioId: usuario.id, motivo, descricao }),
    })
    if (!res.ok) return toast.error('Erro ao abrir chamado')
    const t = await res.json()
    setTicketId(t.id)
    setTicketAberto(true)
    fetchTicket(t.id)
  }

  // 💬 Enviar mensagem
  const enviarMensagem = async () => {
    if (!novaMensagem.trim() || !ticketId) return
    const res = await fetch(
      `${process.env.NEXT_PUBLIC_API_URL}/suporte/${ticketId}/mensagem`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ usuarioId: usuario.id, conteudo: novaMensagem }),
      },
    )
    const msg = await res.json()
    if (!idsRecebidos.current.has(msg.id)) {
      idsRecebidos.current.add(msg.id)
      setMensagens((prev) => [
        ...prev,
        {
          id: msg.id,
          texto: msg.conteudo,
          data: new Date(msg.enviadoEm).toLocaleString('pt-BR'),
          remetente: msg.enviadoPorRole === 'USER' ? 'usuario' : 'suporte',
        },
      ])
    }
    setNovaMensagem('')
  }

  // 🚪 Encerrar
  const encerrarTicket = async () => {
    if (!ticketId) return
    await fetch(`${process.env.NEXT_PUBLIC_API_URL}/suporte/${ticketId}/fechar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ usuarioId: usuario.id }),
    })
    toast.success('Ticket encerrado!')
    setTicketAberto(false)
    setTicketId(null)
    setMensagens([])
  }

  return {
    ticketAberto,
    mensagens,
    novaMensagem,
    setNovaMensagem,
    abrirChamado,
    enviarMensagem,
    encerrarTicket,
    setTicketAberto,
    setMensagens,
  }
}
