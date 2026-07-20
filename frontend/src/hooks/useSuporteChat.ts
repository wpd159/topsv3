'use client'

import { useState } from 'react'
import { toast } from 'sonner'

import { BackendContractPendingError, PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export interface Mensagem {
  id: number
  texto: string
  data: string
  remetente: 'usuario' | 'suporte'
}

export function useSuporteChat(_usuario?: unknown) {
  const [ticketAberto, setTicketAberto] = useState(false)
  const [mensagens, setMensagens] = useState<Mensagem[]>([])
  const [novaMensagem, setNovaMensagem] = useState('')
  const [contractError, setContractError] = useState<BackendContractPendingError | null>(null)

  const unavailable = async (..._args: unknown[]) => {
    const error = new BackendContractPendingError(PENDING_BACKEND_CONTRACTS.support)
    setContractError(error)
    toast.error(error.message)
  }

  return {
    ticketAberto,
    mensagens,
    novaMensagem,
    setNovaMensagem,
    abrirChamado: unavailable,
    enviarMensagem: unavailable,
    encerrarTicket: unavailable,
    setTicketAberto,
    setMensagens,
    contractError,
  }
}
