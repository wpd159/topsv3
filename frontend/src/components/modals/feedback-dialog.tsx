'use client'

import { useRef, useState } from 'react'
import {
  BugAntIcon,
  ChatBubbleBottomCenterTextIcon,
  PencilIcon,
  WrenchScrewdriverIcon,
} from '@heroicons/react/24/outline'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { useAuth } from '@/context/AuthContext'
import {
  enviarSugestao,
  novaSugestaoIdempotencyKey,
  sugestaoError,
} from '@/lib/sugestao-api'

interface FeedbackDialogProps {
  open: boolean
  onOpenChange: (value: boolean) => void
}

export default function FeedbackDialog({ open, onOpenChange }: FeedbackDialogProps) {
  const { usuario } = useAuth()
  const [tipo, setTipo] = useState<'FEATURE' | 'BUG' | ''>('')
  const [titulo, setTitulo] = useState('')
  const [descricao, setDescricao] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [protocolo, setProtocolo] = useState<string | null>(null)
  const chave = useRef<string | null>(null)

  const limpar = () => {
    setTipo('')
    setTitulo('')
    setDescricao('')
    setErro(null)
    setProtocolo(null)
    chave.current = null
  }

  const enviar = async () => {
    if (!usuario) {
      setErro('Entre na sua conta para enviar uma sugestao.')
      return
    }
    if (!tipo || titulo.trim().length < 5 || descricao.trim().length < 10) {
      setErro('Preencha o tipo, o titulo e a descricao antes de enviar.')
      return
    }
    setEnviando(true)
    setErro(null)
    chave.current ??= novaSugestaoIdempotencyKey()
    try {
      const resposta = await enviarSugestao({
        tipo,
        titulo: titulo.trim(),
        descricao: descricao.trim(),
        idempotencyKey: chave.current,
      })
      setProtocolo(resposta.protocolo)
      chave.current = null
    } catch (cause) {
      setErro(sugestaoError(cause).message)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(value) => {
        onOpenChange(value)
        if (!value) limpar()
      }}
    >
      <DialogContent className="rounded-lg p-6 sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-center">Enviar feedback</DialogTitle>
          <DialogDescription className="text-center text-gray-600">
            Compartilhe uma melhoria ou relate um problema encontrado.
          </DialogDescription>
        </DialogHeader>

        <div className="mt-4 grid grid-cols-2 gap-3">
          <button
            type="button"
            className={`flex items-center justify-center gap-2 rounded-lg border px-3 py-3 ${
              tipo === 'FEATURE'
                ? 'border-[#FC1EAD] bg-pink-50 text-[#FC1EAD]'
                : 'border-gray-300 text-gray-700'
            }`}
            onClick={() => setTipo('FEATURE')}
          >
            <WrenchScrewdriverIcon className="h-5 w-5" />
            Sugestao
          </button>
          <button
            type="button"
            className={`flex items-center justify-center gap-2 rounded-lg border px-3 py-3 ${
              tipo === 'BUG'
                ? 'border-red-500 bg-red-50 text-red-500'
                : 'border-gray-300 text-gray-700'
            }`}
            onClick={() => setTipo('BUG')}
          >
            <BugAntIcon className="h-5 w-5" />
            Bug
          </button>
        </div>

        <div className="relative mt-4">
          <PencilIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
          <Input
            value={titulo}
            onChange={(event) => setTitulo(event.target.value)}
            placeholder="Titulo do feedback"
            maxLength={160}
            className="py-5 pl-10"
          />
        </div>

        <div className="relative mt-4">
          <ChatBubbleBottomCenterTextIcon className="absolute left-3 top-3 h-5 w-5 text-gray-400" />
          <Textarea
            value={descricao}
            onChange={(event) => setDescricao(event.target.value)}
            placeholder="Descreva sua sugestao ou o problema encontrado"
            maxLength={3000}
            className="min-h-[120px] pl-10 pt-3"
          />
        </div>

        {erro ? (
          <p className="mt-4 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">
            {erro}
          </p>
        ) : null}

        {protocolo ? (
          <div className="mt-4 rounded-md border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
            <p className="font-medium">Sugestao enviada com sucesso.</p>
            <p className="mt-1">Protocolo {protocolo}</p>
          </div>
        ) : (
          <Button
            type="button"
            onClick={() => void enviar()}
            disabled={enviando}
            className="mt-4 w-full py-5"
          >
            {enviando ? 'Enviando...' : 'Enviar feedback'}
          </Button>
        )}
      </DialogContent>
    </Dialog>
  )
}
