'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
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
import { Textarea } from '@/components/ui/textarea'
import {
  adminSuporteError,
  detalharAdminTicket,
  responderAdminTicket,
  type AdminTicketDetalhe,
} from '@/lib/admin-suporte-api'
import { novaIdempotencyKey } from '@/lib/suporte-api'

type Props = {
  open: boolean
  onOpenChange: (value: boolean) => void
  ticketId: string | null
  onChanged: () => void
}

export default function TicketChatModal({ open, onOpenChange, ticketId, onChanged }: Props) {
  const [detalhe, setDetalhe] = useState<AdminTicketDetalhe | null>(null)
  const [mensagem, setMensagem] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const mutacaoEmCurso = useRef(false)
  const chaveResposta = useRef<string | null>(null)

  const carregar = useCallback(async (signal?: AbortSignal) => {
    if (!open || !ticketId) return
    setErro(null)
    try {
      setDetalhe(await detalharAdminTicket(ticketId, signal))
    } catch (cause) {
      if (!signal?.aborted) setErro(adminSuporteError(cause).message)
    }
  }, [open, ticketId])

  useEffect(() => {
    if (!open || !ticketId) return
    const controller = new AbortController()
    let consultaEmCurso = false
    const atualizar = async () => {
      if (consultaEmCurso || document.hidden) return
      consultaEmCurso = true
      try {
        await carregar(controller.signal)
      } finally {
        consultaEmCurso = false
      }
    }
    void atualizar()
    const timer = window.setInterval(() => void atualizar(), 10_000)
    return () => {
      controller.abort()
      window.clearInterval(timer)
    }
  }, [carregar, open, ticketId])

  const enviar = async () => {
    if (!ticketId || !mensagem.trim() || mutacaoEmCurso.current) return
    mutacaoEmCurso.current = true
    setEnviando(true)
    try {
      chaveResposta.current ??= novaIdempotencyKey('suporte-admin')
      await responderAdminTicket(
        ticketId,
        mensagem,
        chaveResposta.current,
      )
      chaveResposta.current = null
      setMensagem('')
      setDetalhe(await detalharAdminTicket(ticketId))
      onChanged()
      toast.success('Resposta enviada.')
    } catch (cause) {
      toast.error(adminSuporteError(cause).message)
    } finally {
      mutacaoEmCurso.current = false
      setEnviando(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Responder ticket</DialogTitle>
          <DialogDescription>
            {detalhe ? `${detalhe.ticket.protocolo} · ${detalhe.ticket.assunto}` : 'Carregando ticket...'}
          </DialogDescription>
        </DialogHeader>
        {erro ? <p className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">{erro}</p> : null}
        {detalhe ? (
          <div className="max-h-64 space-y-2 overflow-y-auto rounded-md border bg-zinc-50 p-3">
            {detalhe.mensagens.map((item) => (
              <div key={item.id} className="rounded-md border bg-white p-3 text-sm">
                <div className="flex flex-wrap justify-between gap-2">
                  <span className="font-semibold">{item.remetente}</span>
                  <span className="text-xs text-zinc-500">
                    {new Intl.DateTimeFormat('pt-BR', {
                      dateStyle: 'short',
                      timeStyle: 'short',
                    }).format(new Date(item.criadoEm))}
                  </span>
                </div>
                <p className="mt-2 whitespace-pre-wrap break-words">{item.corpo}</p>
              </div>
            ))}
          </div>
        ) : null}
        <Textarea
          value={mensagem}
          onChange={(event) => setMensagem(event.target.value)}
          placeholder="Digite a resposta da equipe"
          rows={7}
          maxLength={4000}
          disabled={enviando}
        />
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            Cancelar
          </Button>
          <Button type="button" disabled={enviando || !mensagem.trim()} onClick={() => void enviar()}>
            {enviando ? 'Enviando...' : 'Enviar resposta'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
