'use client'

import Link from 'next/link'
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
import { Textarea } from '@/components/ui/textarea'
import {
  adminDenunciaError,
  alterarStatusAdminDenuncia,
  detalharAdminDenuncia,
  type AdminDenunciaDetalhe,
} from '@/lib/admin-denuncia-api'

type Props = {
  open: boolean
  onOpenChange: (value: boolean) => void
  denunciaId: string | null
  onChanged: () => void
}

function dataHora(value: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value))
}

export default function DenunciaDetailsModal({
  open,
  onOpenChange,
  denunciaId,
  onChanged,
}: Props) {
  const [detalhe, setDetalhe] = useState<AdminDenunciaDetalhe | null>(null)
  const [providencia, setProvidencia] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [carregando, setCarregando] = useState(false)
  const [mutando, setMutando] = useState(false)

  const carregar = useCallback(async (signal?: AbortSignal) => {
    if (!open || !denunciaId) return
    setCarregando(true)
    setErro(null)
    try {
      const carregado = await detalharAdminDenuncia(denunciaId, signal)
      setDetalhe(carregado)
      setProvidencia(carregado.providencia || '')
    } catch (cause) {
      if (!signal?.aborted) setErro(adminDenunciaError(cause).message)
    } finally {
      if (!signal?.aborted) setCarregando(false)
    }
  }, [denunciaId, open])

  useEffect(() => {
    if (!open || !denunciaId) return
    const controller = new AbortController()
    void carregar(controller.signal)
    return () => controller.abort()
  }, [carregar, denunciaId, open])

  const decidir = async (status: 'PUNIDA' | 'IGNORADA') => {
    if (!denunciaId || mutando || providencia.trim().length < 3) return
    setMutando(true)
    setErro(null)
    try {
      const atualizado = await alterarStatusAdminDenuncia(
        denunciaId,
        status,
        providencia.trim(),
      )
      setDetalhe(atualizado)
      setProvidencia(atualizado.providencia || '')
      onChanged()
      toast.success('Providencia registrada na denuncia.')
    } catch (cause) {
      setErro(adminDenunciaError(cause).message)
    } finally {
      setMutando(false)
    }
  }

  const pendente = detalhe?.denuncia.status === 'PENDENTE'

  return (
    <Dialog open={open} onOpenChange={(value) => {
      if (!mutando) onOpenChange(value)
    }}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>Detalhe da denuncia</DialogTitle>
          <DialogDescription>
            Analise o relato e registre a providencia sem alterar o anuncio automaticamente.
          </DialogDescription>
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
            <div className="grid gap-4 border-y py-4 sm:grid-cols-2">
              <div>
                <p className="text-xs font-medium uppercase text-zinc-500">Denuncia</p>
                <p className="font-semibold">{detalhe.denuncia.protocolo}</p>
                <p className="text-sm text-zinc-600">{detalhe.denuncia.motivoRotulo}</p>
              </div>
              <div>
                <p className="text-xs font-medium uppercase text-zinc-500">Estado</p>
                <p>{detalhe.denuncia.statusRotulo}</p>
                <p className="text-sm text-zinc-500">{dataHora(detalhe.denuncia.criadoEm)}</p>
              </div>
              <div>
                <p className="text-xs font-medium uppercase text-zinc-500">Denunciante</p>
                <p>{detalhe.denuncia.denuncianteNome}</p>
                {detalhe.denuncia.denuncianteEmail ? (
                  <p className="text-sm text-zinc-500">{detalhe.denuncia.denuncianteEmail}</p>
                ) : null}
              </div>
              <div>
                <p className="text-xs font-medium uppercase text-zinc-500">Anuncio</p>
                <p>{detalhe.anuncio.titulo}</p>
                <p className="text-sm text-zinc-500">
                  {detalhe.anuncio.status} · {detalhe.anuncio.statusModeracao}
                </p>
              </div>
            </div>

            <div>
              <p className="text-xs font-medium uppercase text-zinc-500">Descricao complementar</p>
              <p className="mt-1 whitespace-pre-wrap break-words text-sm text-zinc-800">
                {detalhe.descricao || 'Nao informada.'}
              </p>
            </div>

            <Button asChild type="button" variant="outline">
              <Link href={`/admin/anuncios/${encodeURIComponent(detalhe.anuncio.id)}`}>
                Abrir detalhe canonico do anuncio
              </Link>
            </Button>

            <div className="space-y-3">
              <h3 className="font-semibold">Historico</h3>
              <div className="max-h-64 space-y-3 overflow-y-auto rounded-md border bg-zinc-50 p-3">
                {detalhe.historico.map((evento) => (
                  <div key={evento.id} className="rounded-md border bg-white p-3 text-sm">
                    <div className="flex flex-wrap justify-between gap-2">
                      <span className="font-semibold">{evento.acaoRotulo}</span>
                      <span className="text-xs text-zinc-500">{dataHora(evento.criadoEm)}</span>
                    </div>
                    <p className="mt-1 text-zinc-600">Responsavel: {evento.atorNome}</p>
                    {evento.providencia ? (
                      <p className="mt-2 whitespace-pre-wrap break-words">{evento.providencia}</p>
                    ) : null}
                  </div>
                ))}
              </div>
            </div>

            {pendente ? (
              <div className="space-y-2">
                <label htmlFor="denuncia-providencia" className="text-sm font-medium">
                  Providencia administrativa
                </label>
                <Textarea
                  id="denuncia-providencia"
                  rows={4}
                  maxLength={2000}
                  value={providencia}
                  onChange={(event) => setProvidencia(event.target.value)}
                  placeholder="Registre a analise e a providencia adotada."
                  disabled={mutando}
                />
                <p className="text-xs text-zinc-500">
                  Bloqueios e exclusoes devem ser executados somente no detalhe canonico do anuncio.
                </p>
              </div>
            ) : detalhe.providencia ? (
              <div className="rounded-md border bg-zinc-50 p-3 text-sm">
                <p className="font-medium">Providencia registrada</p>
                <p className="mt-1 whitespace-pre-wrap break-words">{detalhe.providencia}</p>
              </div>
            ) : null}
          </>
        ) : null}

        <DialogFooter className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={mutando}>
            Fechar
          </Button>
          {pendente ? (
            <>
              <Button
                type="button"
                variant="outline"
                disabled={mutando || providencia.trim().length < 3}
                onClick={() => void decidir('IGNORADA')}
              >
                Sem providencia
              </Button>
              <Button
                type="button"
                disabled={mutando || providencia.trim().length < 3}
                onClick={() => void decidir('PUNIDA')}
              >
                Registrar providencia
              </Button>
            </>
          ) : null}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
