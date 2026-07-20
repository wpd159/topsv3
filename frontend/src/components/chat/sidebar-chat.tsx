'use client'

import { useState } from 'react'

import { ContractState, PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

interface ChatSidebarProps { conversaSelecionada: unknown; onSelectConversa: (conversa: unknown) => void }

export default function ChatSidebar({ conversaSelecionada: _conversaSelecionada, onSelectConversa: _onSelectConversa }: ChatSidebarProps) {
  const [newConversation, setNewConversation] = useState(false)
  const [username, setUsername] = useState('')
  const [search, setSearch] = useState('')
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(PENDING_BACKEND_CONTRACTS.support)
  return <aside className="flex w-full flex-col border-r border-gray-200 bg-white md:w-80"><div className="flex items-center justify-between border-b p-4"><h2 className="text-lg font-semibold">Mensagens</h2><Button type="button" variant="ghost" onClick={() => setNewConversation((value) => !value)}>Nova conversa</Button></div>{newConversation ? <div className="space-y-3 border-b p-4"><Input value={username} onChange={(event) => setUsername(event.target.value)} placeholder="Digite o username" /><Button type="button" className="w-full" onClick={() => runPendingAction('Iniciar conversa')}>Iniciar conversa</Button></div> : null}<div className="border-b p-4"><Input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar conversa" /></div><div className="flex-1 space-y-3 overflow-y-auto p-4"><ContractState error={error} compact /><PendingActionFeedback attemptedAction={attemptedAction} /><Button type="button" variant="outline" className="w-full" onClick={() => runPendingAction('Consultar conversas recentes')}>Atualizar conversas</Button></div></aside>
}
