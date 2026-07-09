'use client'

import { useEffect, useState } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { BanknotesIcon } from '@heroicons/react/24/solid'
import { toast } from 'sonner'

type ModoAjuste = 'ADICIONAR' | 'REMOVER'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  usuarioId: number | null
  nomeUsuario?: string | null
  saldoAtual?: number | null
  onSuccess?: (novoSaldo: number) => void
}

export default function AdicionarCreditosDialog({
  open,
  onOpenChange,
  usuarioId,
  nomeUsuario,
  saldoAtual,
  onSuccess,
}: Props) {
  const [quantidade, setQuantidade] = useState('')
  const [modo, setModo] = useState<ModoAjuste>('ADICIONAR')
  const [saldoExibido, setSaldoExibido] = useState(Number(saldoAtual || 0))
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    if (open) {
      setSaldoExibido(Number(saldoAtual || 0))
    } else {
      setQuantidade('')
      setModo('ADICIONAR')
      setEnviando(false)
    }
  }, [open, saldoAtual])

  const enviarCreditos = async () => {
    if (!usuarioId) return

    const valorNormalizado = quantidade.trim().replace(/[^\d]/g, '')
    const qtdBase = Number(valorNormalizado)

    if (!Number.isInteger(qtdBase) || qtdBase <= 0) {
      toast.warning('Informe um valor inteiro válido para o ajuste de créditos.')
      return
    }

    const qtd = modo === 'REMOVER' ? -qtdBase : qtdBase
    const acaoLabel = modo === 'REMOVER' ? 'removidos' : 'adicionados'

    try {
      setEnviando(true)
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/usuarios/adicionar-creditos`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          usuarioId,
          quantidade: qtd,
        }),
      })

      if (!res.ok) {
        const message = await res.text().catch(() => '')
        throw new Error(message || 'Erro ao ajustar créditos.')
      }

      const data = (await res.json().catch(() => null)) as { saldo?: number } | null
      const novoSaldo = Number(data?.saldo ?? saldoExibido + qtd)
      setSaldoExibido(novoSaldo)
      toast.success(`${qtdBase} créditos ${acaoLabel} para ${nomeUsuario || 'o usuário'}.`)
      onSuccess?.(novoSaldo)
      onOpenChange(false)
    } catch (error) {
      const message = error instanceof Error && error.message.trim()
        ? error.message
        : 'Erro ao ajustar créditos.'
      toast.error(message)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <BanknotesIcon className="h-5 w-5 text-[#C41E73]" />
            Ajustar créditos
          </DialogTitle>
          <DialogDescription>
            Escolha se deseja adicionar ou remover créditos de <span className="font-medium text-[#C41E73]">{nomeUsuario || 'o usuário'}</span>.
          </DialogDescription>
        </DialogHeader>

        <div className="rounded-xl border border-[#FC1EAD]/15 bg-[#FC1EAD]/5 px-4 py-3">
          <p className="text-xs uppercase tracking-wide text-gray-500">Créditos atuais</p>
          <p className="mt-1 text-2xl font-bold text-[#C41E73]">{saldoExibido.toLocaleString('pt-BR')}</p>
        </div>

        <div className="grid grid-cols-2 gap-2">
          <Button
            type="button"
            variant={modo === 'ADICIONAR' ? 'default' : 'outline'}
            onClick={() => setModo('ADICIONAR')}
            className={
              modo === 'ADICIONAR'
                ? 'bg-[#FC1EAD] hover:bg-[#e01a9a]'
                : 'border-gray-300 text-gray-700 hover:bg-gray-100'
            }
          >
            Adicionar
          </Button>
          <Button
            type="button"
            variant={modo === 'REMOVER' ? 'default' : 'outline'}
            onClick={() => setModo('REMOVER')}
            className={
              modo === 'REMOVER'
                ? 'bg-red-600 hover:bg-red-500'
                : 'border-gray-300 text-gray-700 hover:bg-gray-100'
            }
          >
            Remover
          </Button>
        </div>

        <Input
          type="text"
          inputMode="numeric"
          pattern="[0-9]*"
          placeholder={modo === 'REMOVER' ? 'Quantidade a remover' : 'Quantidade a adicionar'}
          value={quantidade}
          onChange={(event) => setQuantidade(event.target.value.replace(/[^\d]/g, ''))}
          className="border-gray-300 focus-visible:border-[#C41E73] focus-visible:ring-[#C41E73]"
        />

        <p className="text-xs text-gray-500">
          Digite um número inteiro. O tipo do ajuste é definido pelo modo selecionado acima.
        </p>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={enviando}>
            Cancelar
          </Button>
          <Button onClick={enviarCreditos} disabled={enviando} className="bg-[#FC1EAD] hover:bg-[#e01a9a]">
            {enviando ? 'Enviando...' : modo === 'REMOVER' ? 'Confirmar remoção' : 'Confirmar ajuste'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
