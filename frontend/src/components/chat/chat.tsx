'use client'

import { useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'

import { ContractState } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import type { ApiContractError } from '@/lib/api-contract'
import type { ChatConversa, ChatConversaDetalhe } from '@/lib/chat-api'

type ChatWindowProps = {
  conversation: ChatConversa | null
  detail: ChatConversaDetalhe | null
  loading: boolean
  sending: boolean
  error: ApiContractError | null
  onRetry: () => void
  onSend: (message: string) => Promise<boolean>
  onBack: () => void
  isMobile: boolean
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

export default function ChatWindow({
  conversation,
  detail,
  loading,
  sending,
  error,
  onRetry,
  onSend,
  onBack,
  isMobile,
}: ChatWindowProps) {
  const router = useRouter()
  const [message, setMessage] = useState('')
  const endRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'end' })
  }, [detail?.mensagens.length])

  if (!conversation) {
    return (
      <div className="flex h-full items-center justify-center bg-gray-50 p-6 text-center text-sm text-gray-500">
        Selecione uma conversa ou inicie uma nova.
      </div>
    )
  }

  const submit = async () => {
    const safeMessage = message.trim()
    if (!safeMessage || sending) return
    const sent = await onSend(safeMessage)
    if (sent) setMessage('')
  }

  return (
    <div className="flex h-full min-h-0 flex-col bg-gray-50">
      <div className="flex items-center justify-between border-b bg-white px-4 py-3">
        <div className="flex min-w-0 items-center gap-3">
          {isMobile ? (
            <Button type="button" variant="ghost" size="sm" onClick={onBack}>
              Voltar
            </Button>
          ) : null}
          <div className="min-w-0">
            <h2 className="truncate text-sm font-semibold text-gray-900">
              @{conversation.participanteUsername}
            </h2>
            <button
              type="button"
              className="text-xs text-pink-700 underline"
              onClick={() =>
                router.push(
                  `/anuncios?autor=${encodeURIComponent(conversation.participanteUsername)}`
                )
              }
            >
              Ver anuncios
            </button>
          </div>
        </div>
      </div>
      <div className="min-h-0 flex-1 space-y-3 overflow-y-auto p-4" aria-live="polite">
        {error ? (
          <ContractState error={error} onRetry={error.retryable ? onRetry : undefined} compact />
        ) : null}
        {loading && !detail ? (
          <p className="text-sm text-gray-500">Carregando historico...</p>
        ) : detail?.mensagens.length === 0 ? (
          <div className="rounded-lg border border-gray-200 bg-white p-4 text-sm text-gray-600">
            Nenhuma mensagem nesta conversa.
          </div>
        ) : (
          detail?.mensagens.map((item) => (
            <div
              key={item.id}
              className={`flex ${item.minha ? 'justify-end' : 'justify-start'}`}
            >
              <div
                className={`max-w-[85%] rounded-lg px-3 py-2 text-sm shadow-sm md:max-w-[70%] ${
                  item.minha
                    ? 'bg-[#FC1EAD] text-white'
                    : 'border border-gray-200 bg-white text-gray-900'
                }`}
              >
                <p className="whitespace-pre-wrap break-words">{item.corpo}</p>
                <p
                  className={`mt-1 text-[10px] ${
                    item.minha ? 'text-pink-100' : 'text-gray-500'
                  }`}
                >
                  {formatTime(item.enviadoEm)}
                </p>
              </div>
            </div>
          ))
        )}
        <div ref={endRef} />
      </div>
      <form
        className="flex shrink-0 gap-2 border-t bg-white p-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]"
        onSubmit={(event) => {
          event.preventDefault()
          void submit()
        }}
      >
        <Input
          value={message}
          onChange={(event) => setMessage(event.target.value)}
          placeholder="Digite uma mensagem..."
          maxLength={2000}
          className="min-w-0 text-base"
          disabled={sending}
        />
        <Button type="submit" disabled={!message.trim() || sending} className="shrink-0">
          {sending ? 'Enviando...' : 'Enviar'}
        </Button>
      </form>
    </div>
  )
}
