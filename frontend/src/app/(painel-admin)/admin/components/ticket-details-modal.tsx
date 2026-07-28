'use client'

import { useCallback, useEffect, useState } from 'react'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  adminSuporteError,
  alterarStatusAdminTicket,
  detalharAdminTicket,
  type AdminTicketDetalhe,
} from '@/lib/admin-suporte-api'

type Props = {
  open: boolean
  onOpenChange: (value: boolean) => void
  ticketId: string | null
  onOpenChat: () => void
  onChanged: () => void
}

function dataHora(value: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value))
}

export default function TicketDetailsModal({
  open,
  onOpenChange,
  ticketId,
  onOpenChat,
  onChanged,
}: Props) {
  const [detalhe, setDetalhe] = useState<AdminTicketDetalhe | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [carregando, setCarregando] = useState(false)
  const [mutando, setMutando] = useState(false)

  const carregar = useCallback(async (signal?: AbortSignal) => {
    if (!ticketId || !open) return
    setCarregando(true)
    setErro(null)
    try {
      setDetalhe(await detalharAdminTicket(ticketId, signal))
    } catch (cause) {
      if (!signal?.aborted) setErro(adminSuporteError(cause).message)
    } finally {
      if (!signal?.aborted) setCarregando(false)
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

  const alterarStatus = async (status: string) => {
    if (!ticketId || mutando) return
    setMutando(true)
    try {
      setDetalhe(await alterarStatusAdminTicket(ticketId, status))
      onChanged()
      toast.success('Estado do ticket atualizado.')
    } catch (cause) {
      toast.error(adminSuporteError(cause).message)
    } finally {
      setMutando(false)
    }
  }

  const finalizado = detalhe
    ? ['RESOLVIDO', 'ENCERRADO'].includes(detalhe.ticket.status)
    : false

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>Detalhes do ticket</DialogTitle>
          <DialogDescription>Solicitante, histórico e andamento do atendimento.</DialogDescription>
        </DialogHeader>

        {carregando ? <p className="py-12 text-center text-sm text-zinc-500">Carregando...</p> : null}
        {erro ? (
          <div className="flex items-center justify-between gap-3 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">
            <p>{erro}</p>
            <Button type="button" size="sm" variant="outline" onClick={() => void carregar()}>
              Tentar novamente
            </Button>
          </div>
        ) : null}

        {detalhe ? (
          <>
            <div className="grid gap-3 border-y py-4 sm:grid-cols-2">
              <div>
                <p className="text-xs font-medium uppercase text-zinc-500">Ticket</p>
                <p className="font-semibold">{detalhe.ticket.protocolo} · {detalhe.ticket.assunto}</p>
              </div>
              <div>
                <p className="text-xs font-medium uppercase text-zinc-500">Categoria</p>
                <p>{detalhe.ticket.categoriaRotulo}</p>
              </div>
              <div>
                <p className="text-xs font-medium uppercase text-zinc-500">Solicitante</p>
                <p>{detalhe.ticket.usuarioNome}</p>
                <p className="break-all text-sm text-zinc-500">{detalhe.ticket.usuarioEmail}</p>
              </div>
              <div>
                <p className="text-xs font-medium uppercase text-zinc-500">Atendimento</p>
                <p>{detalhe.ticket.statusRotulo}</p>
                <p className="text-sm text-zinc-500">
                  {detalhe.responsavelNome ? `Responsável: ${detalhe.responsavelNome}` : 'Sem responsável'}
                </p>
              </div>
            </div>

            <div className="space-y-3">
              <h3 className="font-semibold">Histórico</h3>
              <div className="max-h-80 space-y-3 overflow-y-auto rounded-md border bg-zinc-50 p-3">
                {detalhe.mensagens.map((mensagem) => (
                  <div key={mensagem.id} className="rounded-md border bg-white p-3 text-sm">
                    <div className="flex flex-wrap justify-between gap-2">
                      <span className="font-semibold">{mensagem.remetente}</span>
                      <span className="text-xs text-zinc-500">{dataHora(mensagem.criadoEm)}</span>
                    </div>
                    <p className="mt-2 whitespace-pre-wrap break-words">{mensagem.corpo}</p>
                  </div>
                ))}
              </div>
            </div>

            {!finalizado ? (
              <div className="space-y-2">
                <label htmlFor="ticket-status" className="text-sm font-medium">Alterar estado</label>
                <Select
                  value={detalhe.ticket.status}
                  onValueChange={(value) => void alterarStatus(value)}
                  disabled={mutando}
                >
                  <SelectTrigger id="ticket-status"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ABERTO">Aberto</SelectItem>
                    <SelectItem value="EM_ATENDIMENTO">Em atendimento</SelectItem>
                    <SelectItem value="AGUARDANDO_USUARIO">Aguardando resposta</SelectItem>
                    <SelectItem value="RESOLVIDO">Resolvido</SelectItem>
                    <SelectItem value="ENCERRADO">Encerrado</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            ) : null}
          </>
        ) : null}

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            Fechar
          </Button>
          {detalhe && !finalizado ? (
            <Button type="button" onClick={onOpenChat}>
              Responder
            </Button>
          ) : null}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
