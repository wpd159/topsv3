'use client'

import { useCallback, useEffect, useState } from 'react'

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
  adminSugestaoError,
  alterarStatusAdminSugestao,
  detalharAdminSugestao,
  type AdminSugestaoDetalhe,
} from '@/lib/admin-sugestao-api'

function dataHora(value: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value))
}

export default function SugestaoDetailsModal({
  open,
  onOpenChange,
  sugestaoId,
  onChanged,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  sugestaoId: string | null
  onChanged: () => void
}) {
  const [detalhe, setDetalhe] = useState<AdminSugestaoDetalhe | null>(null)
  const [providencia, setProvidencia] = useState('')
  const [carregando, setCarregando] = useState(false)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  const carregar = useCallback(async (signal?: AbortSignal) => {
    if (!sugestaoId) return
    setCarregando(true)
    setErro(null)
    try {
      const resposta = await detalharAdminSugestao(sugestaoId, signal)
      setDetalhe(resposta)
      setProvidencia(resposta.providencia || '')
    } catch (cause) {
      if (!signal?.aborted) setErro(adminSugestaoError(cause).message)
    } finally {
      if (!signal?.aborted) setCarregando(false)
    }
  }, [sugestaoId])

  useEffect(() => {
    if (!open || !sugestaoId) return
    const controller = new AbortController()
    void carregar(controller.signal)
    return () => controller.abort()
  }, [carregar, open, sugestaoId])

  const alterar = async (status: 'EM_ANALISE' | 'RESOLVIDO' | 'RECUSADO') => {
    if (!sugestaoId || providencia.trim().length < 3) {
      setErro('Registre a providencia administrativa antes de atualizar o estado.')
      return
    }
    setSalvando(true)
    setErro(null)
    try {
      const resposta = await alterarStatusAdminSugestao(
        sugestaoId,
        status,
        providencia.trim(),
      )
      setDetalhe(resposta)
      onChanged()
    } catch (cause) {
      setErro(adminSugestaoError(cause).message)
    } finally {
      setSalvando(false)
    }
  }

  const status = detalhe?.sugestao.status
  const terminal = status === 'RESOLVIDO' || status === 'RECUSADO'

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{detalhe?.sugestao.titulo || 'Detalhe da sugestao'}</DialogTitle>
          <DialogDescription>
            Conteudo, tratamento administrativo e historico auditavel.
          </DialogDescription>
        </DialogHeader>

        {carregando ? (
          <p className="py-10 text-center text-sm text-zinc-500">Carregando...</p>
        ) : null}
        {erro ? (
          <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">
            {erro}
          </div>
        ) : null}

        {detalhe ? (
          <div className="space-y-5">
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="border-l-2 border-zinc-200 pl-3">
                <p className="text-xs uppercase text-zinc-500">Tipo</p>
                <p className="text-sm font-medium">{detalhe.sugestao.tipoRotulo}</p>
              </div>
              <div className="border-l-2 border-zinc-200 pl-3">
                <p className="text-xs uppercase text-zinc-500">Estado</p>
                <p className="text-sm font-medium">{detalhe.sugestao.statusRotulo}</p>
              </div>
              <div className="border-l-2 border-zinc-200 pl-3">
                <p className="text-xs uppercase text-zinc-500">Usuario</p>
                <p className="text-sm font-medium">{detalhe.sugestao.usuarioNome}</p>
                <p className="text-xs text-zinc-500">
                  {detalhe.sugestao.usuarioEmail || 'E-mail protegido'}
                </p>
              </div>
              <div className="border-l-2 border-zinc-200 pl-3">
                <p className="text-xs uppercase text-zinc-500">Recebida em</p>
                <p className="text-sm font-medium">{dataHora(detalhe.sugestao.criadoEm)}</p>
              </div>
            </div>

            <div>
              <p className="mb-1 text-xs uppercase text-zinc-500">Descricao</p>
              <p className="whitespace-pre-wrap text-sm text-zinc-800">{detalhe.descricao}</p>
            </div>

            {!terminal ? (
              <div className="space-y-3 border-y py-4">
                <label className="block text-sm font-medium" htmlFor="providencia-sugestao">
                  Providencia administrativa
                </label>
                <Textarea
                  id="providencia-sugestao"
                  value={providencia}
                  onChange={(event) => setProvidencia(event.target.value)}
                  maxLength={2000}
                  placeholder="Registre a analise ou decisao interna."
                  className="min-h-24"
                />
                <div className="flex flex-wrap gap-2">
                  {status === 'PENDENTE' ? (
                    <Button
                      type="button"
                      variant="outline"
                      disabled={salvando}
                      onClick={() => void alterar('EM_ANALISE')}
                    >
                      Iniciar analise
                    </Button>
                  ) : null}
                  <Button
                    type="button"
                    disabled={salvando}
                    onClick={() => void alterar('RESOLVIDO')}
                  >
                    Aceitar sugestao
                  </Button>
                  <Button
                    type="button"
                    variant="destructive"
                    disabled={salvando}
                    onClick={() => void alterar('RECUSADO')}
                  >
                    Recusar sugestao
                  </Button>
                </div>
              </div>
            ) : null}

            <div>
              <h3 className="font-semibold text-zinc-950">Historico</h3>
              <div className="mt-3 divide-y border-y">
                {detalhe.historico.map((evento) => (
                  <div key={evento.id} className="py-3 text-sm">
                    <div className="flex flex-wrap justify-between gap-2">
                      <p className="font-medium">{evento.acaoRotulo}</p>
                      <p className="text-xs text-zinc-500">{dataHora(evento.criadoEm)}</p>
                    </div>
                    <p className="text-xs text-zinc-500">
                      {evento.atorNome} - requestId {evento.requestId}
                    </p>
                    {evento.providencia ? (
                      <p className="mt-1 whitespace-pre-wrap text-zinc-700">
                        {evento.providencia}
                      </p>
                    ) : null}
                  </div>
                ))}
              </div>
            </div>
          </div>
        ) : null}

        <DialogFooter>
          {erro && !detalhe ? (
            <Button type="button" variant="outline" onClick={() => void carregar()}>
              Tentar novamente
            </Button>
          ) : null}
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            Fechar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
