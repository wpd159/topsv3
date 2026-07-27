'use client'

import { useState } from 'react'

import { ContractState } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import type { ApiContractError } from '@/lib/api-contract'
import type { ChatConversa } from '@/lib/chat-api'

type ChatSidebarProps = {
  conversations: ChatConversa[]
  selectedId: string | null
  loading: boolean
  starting: boolean
  error: ApiContractError | null
  onRetry: () => void
  onStartConversation: (username: string) => Promise<void>
  onSelectConversation: (conversation: ChatConversa) => void
}

function formatDate(value: string | null) {
  if (!value) return ''
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

export default function ChatSidebar({
  conversations,
  selectedId,
  loading,
  starting,
  error,
  onRetry,
  onStartConversation,
  onSelectConversation,
}: ChatSidebarProps) {
  const [newConversation, setNewConversation] = useState(false)
  const [username, setUsername] = useState('')
  const [search, setSearch] = useState('')
  const normalizedSearch = search.trim().toLocaleLowerCase('pt-BR')
  const visibleConversations = normalizedSearch
    ? conversations.filter((item) =>
        item.participanteUsername.toLocaleLowerCase('pt-BR').includes(normalizedSearch)
      )
    : conversations

  const submit = async () => {
    const safeUsername = username.trim()
    if (!safeUsername || starting) return
    await onStartConversation(safeUsername)
    setUsername('')
    setNewConversation(false)
  }

  return (
    <aside className="flex w-full min-w-0 flex-col border-r border-gray-200 bg-white md:w-80 md:flex-none">
      <div className="flex items-center justify-between gap-3 border-b p-4">
        <h1 className="text-base font-semibold text-gray-900">Mensagens</h1>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => setNewConversation((value) => !value)}
        >
          Nova conversa
        </Button>
      </div>
      {newConversation ? (
        <form
          className="space-y-3 border-b p-4"
          onSubmit={(event) => {
            event.preventDefault()
            void submit()
          }}
        >
          <Input
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            placeholder="Digite o username..."
            maxLength={120}
            autoComplete="off"
          />
          <Button type="submit" className="w-full" disabled={!username.trim() || starting}>
            {starting ? 'Iniciando...' : 'Iniciar conversa'}
          </Button>
        </form>
      ) : null}
      <div className="border-b p-4">
        <Input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Buscar conversa..."
        />
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto">
        {error ? (
          <div className="p-4">
            <ContractState error={error} onRetry={error.retryable ? onRetry : undefined} compact />
          </div>
        ) : null}
        {loading ? (
          <p className="p-4 text-sm text-gray-500">Carregando conversas...</p>
        ) : visibleConversations.length === 0 ? (
          <p className="p-4 text-sm text-gray-500">
            {search ? 'Nenhuma conversa corresponde a busca.' : 'Nenhuma conversa iniciada.'}
          </p>
        ) : (
          <ul className="divide-y divide-gray-100">
            {visibleConversations.map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  className={`w-full px-4 py-3 text-left transition-colors hover:bg-pink-50 ${
                    selectedId === item.id ? 'bg-pink-50' : 'bg-white'
                  }`}
                  onClick={() => onSelectConversation(item)}
                >
                  <div className="flex items-start justify-between gap-3">
                    <span className="truncate text-sm font-semibold text-gray-900">
                      @{item.participanteUsername}
                    </span>
                    <span className="shrink-0 text-[11px] text-gray-500">
                      {formatDate(item.ultimaMensagemEm)}
                    </span>
                  </div>
                  <div className="mt-1 flex items-center gap-2">
                    <span className="min-w-0 flex-1 truncate text-xs text-gray-600">
                      {item.ultimaMensagem || 'Conversa sem mensagens'}
                    </span>
                    {item.naoLidas > 0 ? (
                      <span className="inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-[#FC1EAD] px-1 text-[10px] font-bold text-white">
                        {item.naoLidas > 99 ? '99+' : item.naoLidas}
                      </span>
                    ) : null}
                  </div>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </aside>
  )
}
