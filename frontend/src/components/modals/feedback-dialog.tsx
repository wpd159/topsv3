'use client'

import { useState } from 'react'
import { BugAntIcon, ChatBubbleBottomCenterTextIcon, EnvelopeIcon, PencilIcon, WrenchScrewdriverIcon } from '@heroicons/react/24/outline'

import {
  ContractState,
  PendingActionFeedback,
  usePendingContractActions,
} from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { useAuth } from '@/context/AuthContext'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

interface FeedbackDialogProps {
  open: boolean
  onOpenChange: (value: boolean) => void
}

export default function FeedbackDialog({ open, onOpenChange }: FeedbackDialogProps) {
  const { usuario } = useAuth()
  const [tipo, setTipo] = useState<'FEATURE' | 'BUG' | ''>('')
  const [titulo, setTitulo] = useState('')
  const [descricao, setDescricao] = useState('')
  const [emailOpcional, setEmailOpcional] = useState('')
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(
    PENDING_BACKEND_CONTRACTS.suggestions
  )

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="rounded-xl p-6 sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-center">Enviar feedback</DialogTitle>
          <DialogDescription className="text-center text-gray-600">
            Sugestoes e relatos de problemas permanecem disponiveis nesta area.
          </DialogDescription>
        </DialogHeader>

        <div className="mt-4 grid grid-cols-2 gap-3">
          <button
            type="button"
            className={`flex items-center justify-center gap-2 rounded-lg border px-3 py-3 ${
              tipo === 'FEATURE' ? 'border-[#FC1EAD] bg-pink-50 text-[#FC1EAD]' : 'border-gray-300 text-gray-700'
            }`}
            onClick={() => setTipo('FEATURE')}
          >
            <WrenchScrewdriverIcon className="h-5 w-5" />
            Sugestao
          </button>
          <button
            type="button"
            className={`flex items-center justify-center gap-2 rounded-lg border px-3 py-3 ${
              tipo === 'BUG' ? 'border-red-500 bg-red-50 text-red-500' : 'border-gray-300 text-gray-700'
            }`}
            onClick={() => setTipo('BUG')}
          >
            <BugAntIcon className="h-5 w-5" />
            Bug
          </button>
        </div>

        <div className="relative mt-4">
          <PencilIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
          <Input value={titulo} onChange={(event) => setTitulo(event.target.value)} placeholder="Titulo do feedback" className="pl-10 py-5" />
        </div>

        <div className="relative mt-4">
          <ChatBubbleBottomCenterTextIcon className="absolute left-3 top-3 h-5 w-5 text-gray-400" />
          <Textarea value={descricao} onChange={(event) => setDescricao(event.target.value)} placeholder="Descreva sua sugestao ou o problema encontrado" className="min-h-[120px] pl-10 pt-3" />
        </div>

        {!usuario ? (
          <div className="relative mt-4">
            <EnvelopeIcon className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
            <Input type="email" value={emailOpcional} onChange={(event) => setEmailOpcional(event.target.value)} placeholder="Seu e-mail (opcional)" className="pl-10 py-5" />
          </div>
        ) : null}

        <div className="mt-4">
          <ContractState error={error} compact />
          <PendingActionFeedback attemptedAction={attemptedAction} />
        </div>

        <Button type="button" onClick={() => runPendingAction('Enviar feedback')} className="mt-4 w-full py-5">
          Enviar feedback
        </Button>
      </DialogContent>
    </Dialog>
  )
}
