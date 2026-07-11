'use client'

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import {
  Select,
  SelectTrigger,
  SelectContent,
  SelectItem,
  SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { Button } from '@/components/ui/button'
import { useState } from 'react'
import { toast } from 'sonner'
import { useAuth } from '@/context/AuthContext'

interface DenunciaModalProps {
  open: boolean
  onOpenChange: (v: boolean) => void
  anuncioId: string | number
  slug: string
}

const MOTIVOS = [
  { label: 'Conteúdo inadequado', value: 'CONTEUDO_INADEQUADO' },
  { label: 'Perfil falso', value: 'PERFIL_FALSO' },
  { label: 'Golpe / Scam', value: 'SCAM' },
  { label: 'Spam', value: 'SPAM' },
  { label: 'Outros', value: 'OUTROS' },
]

// Lê o corpo UMA ÚNICA VEZ e tenta extrair uma mensagem
async function readMessage(res: Response) {
  const raw = await res.text().catch(() => '')
  if (!raw) return 'Erro ao enviar denúncia.'
  try {
    const data = JSON.parse(raw)
    return (
      data?.message ||
      data?.error ||
      data?.erro ||
      data?.mensagem ||
      'Erro ao enviar denúncia.'
    )
  } catch {
    return raw
  }
}

export default function DenunciaModal({ open, onOpenChange, anuncioId }: DenunciaModalProps) {
  const [motivo, setMotivo] = useState('')
  const [descricao, setDescricao] = useState('')
  const [loading, setLoading] = useState(false)
  const { usuario } = useAuth() // apenas para UX (backend usa @AuthenticationPrincipal)

  const enviarDenuncia = async () => {
    if (!motivo) {
      toast.error('Selecione um motivo para a denúncia.')
      return
    }
    if (!usuario?.id) {
      toast.error('Você precisa estar logado para denunciar.')
      return
    }

    setLoading(true)
    try {
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/denuncias/abrir`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
        credentials: 'include', // necessário para o backend identificar o usuário
        body: JSON.stringify({ anuncioId, motivo, descricao }),
      })

      if (!res.ok) {
        if (res.status === 401 || res.status === 403) {
          toast.error('Faça login para enviar denúncias.')
          throw new Error('Não autenticado')
        }
        const mensagem = await readMessage(res)
        throw new Error(mensagem)
      }

      // Sucesso
      toast.success('Denúncia enviada com sucesso! Nossa equipe irá analisar.')
      setMotivo('')
      setDescricao('')
      onOpenChange(false)
    } catch (err: any) {
      toast.error(err?.message || 'Falha ao enviar denúncia.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-lg font-semibold text-gray-800">
            Denunciar anúncio
          </DialogTitle>
          <p className="text-sm text-gray-500 mt-1">
            Selecione o motivo e descreva brevemente o problema.
          </p>
        </DialogHeader>

        <div className="space-y-4 py-3">
          <div>
            <label className="text-sm font-medium text-gray-700">Motivo</label>
            <Select value={motivo} onValueChange={setMotivo}>
              <SelectTrigger className="mt-1 w-full">
                <SelectValue placeholder="Selecione um motivo" />
              </SelectTrigger>
              <SelectContent>
                {MOTIVOS.map((m) => (
                  <SelectItem key={m.value} value={m.value}>
                    {m.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div>
            <label className="text-sm font-medium text-gray-700">Descrição (opcional)</label>
            <Textarea
              placeholder="Descreva o motivo da denúncia..."
              className="mt-1"
              value={descricao}
              onChange={(e) => setDescricao(e.target.value)}
            />
          </div>
        </div>

        <DialogFooter className="flex justify-end gap-2">
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            className="border-gray-300 text-gray-600 hover:bg-gray-50"
          >
            Cancelar
          </Button>
          <Button
            disabled={loading}
            onClick={enviarDenuncia}
            className="bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-semibold"
          >
            {loading ? 'Enviando...' : 'Enviar denúncia'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
