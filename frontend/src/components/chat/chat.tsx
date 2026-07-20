'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'

import { ContractState, PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

type Conversation = { id?: string | number; nome?: string } | null
type ChatWindowProps = { conversa: Conversation; onVoltar?: () => void; isMobile?: boolean }

export default function ChatWindow({ conversa, onVoltar, isMobile }: ChatWindowProps) {
  const router = useRouter()
  const [message, setMessage] = useState('')
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(PENDING_BACKEND_CONTRACTS.support)
  const name = conversa?.nome || 'Conversa não carregada'
  return <div className="flex h-full flex-col bg-gray-50"><div className="flex items-center justify-between border-b bg-white p-4"><div className="flex items-center gap-3">{isMobile ? <Button type="button" variant="ghost" size="sm" onClick={onVoltar}>Voltar</Button> : null}<div><h2 className="text-sm font-semibold text-gray-800">{name}</h2><button type="button" className="text-xs text-pink-700 underline" onClick={() => { if (conversa?.nome) router.push(`/anuncios?autor=${encodeURIComponent(conversa.nome)}`); else runPendingAction('Ver anúncios da conversa') }}>Ver anúncios</button></div></div></div><div className="flex-1 space-y-3 overflow-y-auto p-4"><ContractState error={error} compact /><div className="rounded-lg border bg-white p-4 text-sm text-gray-600">O histórico permanece nesta área e não é convertido em conversa vazia.</div></div><div className="space-y-2 border-t bg-white p-4"><Input value={message} onChange={(event) => setMessage(event.target.value)} placeholder="Digite uma mensagem" onKeyDown={(event) => { if (event.key === 'Enter') runPendingAction('Enviar mensagem') }} /><PendingActionFeedback attemptedAction={attemptedAction} /><Button type="button" className="w-full" onClick={() => runPendingAction('Enviar mensagem')}>Enviar mensagem</Button></div></div>
}
