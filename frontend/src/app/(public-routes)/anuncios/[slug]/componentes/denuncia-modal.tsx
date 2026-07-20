'use client'

import { useState } from 'react'

import {
  ContractState,
  PendingActionFeedback,
  usePendingContractActions,
} from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

interface DenunciaModalProps {
  open: boolean
  onOpenChange: (value: boolean) => void
  anuncioId: string | number
  slug: string
}

const MOTIVOS = [
  { label: 'Conteudo inadequado', value: 'CONTEUDO_INADEQUADO' },
  { label: 'Perfil falso', value: 'PERFIL_FALSO' },
  { label: 'Golpe / Scam', value: 'SCAM' },
  { label: 'Spam', value: 'SPAM' },
  { label: 'Outros', value: 'OUTROS' },
]

export default function DenunciaModal({ open, onOpenChange }: DenunciaModalProps) {
  const [motivo, setMotivo] = useState('')
  const [descricao, setDescricao] = useState('')
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(
    PENDING_BACKEND_CONTRACTS.reports
  )

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-lg font-semibold text-gray-800">Denunciar anuncio</DialogTitle>
          <p className="mt-1 text-sm text-gray-500">Selecione o motivo e descreva brevemente o problema.</p>
        </DialogHeader>

        <div className="space-y-4 py-3">
          <div>
            <label className="text-sm font-medium text-gray-700">Motivo</label>
            <Select value={motivo} onValueChange={setMotivo}>
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
            <Textarea value={descricao} onChange={(event) => setDescricao(event.target.value)} placeholder="Descreva o motivo da denuncia" className="mt-1" />
          </div>
          <ContractState error={error} compact />
          <PendingActionFeedback attemptedAction={attemptedAction} />
        </div>

        <DialogFooter className="flex justify-end gap-2">
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancelar</Button>
          <Button type="button" onClick={() => runPendingAction('Enviar denúncia')}>Enviar denuncia</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
