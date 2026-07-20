'use client'

import { useEffect, useState } from 'react'
import { useSearchParams } from 'next/navigation'

import ChatWindow from '@/components/chat/chat'
import ChatSidebar from '@/components/chat/sidebar-chat'
import { ContractState, pendingContractError } from '@/components/feedback/contract-state'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export default function ChatClient() {
  const [conversation, setConversation] = useState<unknown>(null)
  const [mobile, setMobile] = useState(false)
  const searchParams = useSearchParams()
  useEffect(() => { const resize = () => setMobile(window.innerWidth < 768); resize(); window.addEventListener('resize', resize); return () => window.removeEventListener('resize', resize) }, [])
  const requestedUser = searchParams.get('usuario')
  return <section className="mt-5 space-y-3"><ContractState error={pendingContractError(PENDING_BACKEND_CONTRACTS.support)} compact />{requestedUser ? <p className="text-sm text-gray-600">Conversa solicitada com @{requestedUser}; identificação depende do contrato V3.</p> : null}<div className="flex h-[calc(100vh-180px)] overflow-hidden rounded-xl border border-gray-200 bg-white">{!mobile || !conversation ? <ChatSidebar conversaSelecionada={conversation} onSelectConversa={setConversation} /> : null}{!mobile || conversation ? <div className="flex flex-1 flex-col"><ChatWindow conversa={conversation as { id?: string | number; nome?: string } | null} onVoltar={() => setConversation(null)} isMobile={mobile} /></div> : null}</div></section>
}
