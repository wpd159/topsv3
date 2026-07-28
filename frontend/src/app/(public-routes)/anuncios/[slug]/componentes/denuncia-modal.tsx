'use client'

import { useState } from 'react'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import {
  criarDenuncia,
  denunciaError,
  novaDenunciaIdempotencyKey,
} from '@/lib/denuncia-api'

interface DenunciaModalProps {
  open: boolean
  onOpenChange: (value: boolean) => void
  anuncioId: string | number
  slug: string
}

const MOTIVOS = [
  { label: 'Conteudo inadequado', value: 'CONTEUDO_INADEQUADO' },
  { label: 'Perfil falso', value: 'PERFIL_FALSO' },
  { label: 'Golpe / Scam', value: 'GOLPE' },
  { label: 'Spam', value: 'SPAM' },
  { label: 'Outros', value: 'OUTROS' },
]

export default function DenunciaModal({ open, onOpenChange, anuncioId }: DenunciaModalProps) {
  const [motivo, setMotivo] = useState('')
  const [descricao, setDescricao] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [protocolo, setProtocolo] = useState<string | null>(null)
  const [idempotencyKey, setIdempotencyKey] = useState(novaDenunciaIdempotencyKey)

  const alterarAbertura = (value: boolean) => {
    if (!value && enviando) return
    onOpenChange(value)
    if (!value) {
      setMotivo('')
      setDescricao('')
      setErro(null)
      setProtocolo(null)
      setIdempotencyKey(novaDenunciaIdempotencyKey())
    }
  }

  const enviar = async () => {
    if (!motivo || enviando) return
    setEnviando(true)
    setErro(null)
    try {
      const criada = await criarDenuncia({
        anuncioId: String(anuncioId),
        motivo,
        descricao: descricao.trim() || undefined,
      }, idempotencyKey)
      setProtocolo(criada.protocolo)
    } catch (cause) {
      setErro(denunciaError(cause).message)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={alterarAbertura}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-lg font-semibold text-gray-800">Denunciar anuncio</DialogTitle>
          <DialogDescription>
            Selecione o motivo e descreva brevemente o problema.
          </DialogDescription>
        </DialogHeader>

        {protocolo ? (
          <div className="space-y-3 py-4">
            <p className="font-medium text-emerald-800">Denuncia enviada com sucesso.</p>
            <p className="text-sm text-gray-600">
              Protocolo {protocolo}. A equipe administrativa fara a analise.
            </p>
          </div>
        ) : (
          <div className="space-y-4 py-3">
            <div>
              <label className="text-sm font-medium text-gray-700">Motivo</label>
              <Select value={motivo} onValueChange={setMotivo} disabled={enviando}>
                <SelectTrigger className="mt-1 w-full">
                  <SelectValue placeholder="Selecione um motivo" />
                </SelectTrigger>
                <SelectContent>
                  {MOTIVOS.map((item) => (
                    <SelectItem key={item.value} value={item.value}>{item.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-700">Descricao (opcional)</label>
              <Textarea
                value={descricao}
                onChange={(event) => setDescricao(event.target.value)}
                placeholder="Descreva o motivo da denuncia"
                className="mt-1"
                maxLength={1000}
                disabled={enviando}
              />
            </div>
            {erro ? (
              <p role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                {erro}
              </p>
            ) : null}
          </div>
        )}

        <DialogFooter className="flex justify-end gap-2">
          <Button variant="outline" onClick={() => alterarAbertura(false)}>
            {protocolo ? 'Fechar' : 'Cancelar'}
          </Button>
          {!protocolo ? (
            <Button type="button" disabled={!motivo || enviando} onClick={() => void enviar()}>
              {enviando ? 'Enviando...' : 'Enviar denuncia'}
            </Button>
          ) : null}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
