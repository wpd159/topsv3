'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'

import ChatWindow from '@/components/chat/chat'
import ChatSidebar from '@/components/chat/sidebar-chat'
import type { ApiContractError } from '@/lib/api-contract'
import {
  chatError,
  enviarChatMensagem,
  fetchChatConversa,
  fetchChatConversas,
  iniciarChatConversa,
  iniciarChatConversaPorAnuncio,
  marcarChatConversaComoLida,
  type ChatConversa,
  type ChatConversaDetalhe,
} from '@/lib/chat-api'

const CONVERSATION_POLL_MS = 10_000
const MESSAGE_POLL_MS = 4_000

export default function ChatClient() {
  const [conversations, setConversations] = useState<ChatConversa[]>([])
  const [conversation, setConversation] = useState<ChatConversa | null>(null)
  const [detail, setDetail] = useState<ChatConversaDetalhe | null>(null)
  const [error, setError] = useState<ApiContractError | null>(null)
  const [loadingConversations, setLoadingConversations] = useState(true)
  const [loadingDetail, setLoadingDetail] = useState(false)
  const [starting, setStarting] = useState(false)
  const [sending, setSending] = useState(false)
  const [mobile, setMobile] = useState(false)
  const router = useRouter()
  const searchParams = useSearchParams()
  const requestedUser = searchParams.get('usuario')
  const requestedAd = searchParams.get('anuncio')
  const requestedConversationId = searchParams.get('conversa')
  const requestedUserHandled = useRef<string | null>(null)
  const requestedAdHandled = useRef<string | null>(null)
  const requestedConversationHandled = useRef<string | null>(null)
  const startingRef = useRef(false)
  const pendingMessage = useRef<{ key: string; body: string } | null>(null)

  const loadConversations = useCallback(async (silent = false) => {
    if (!silent) setLoadingConversations(true)
    try {
      const data = await fetchChatConversas()
      setConversations(data)
      setConversation((current) => {
        if (!current) return current
        return data.find((item) => item.id === current.id) ?? current
      })
      if (!silent) setError(null)
    } catch (cause) {
      if (!silent) setError(chatError(cause))
    } finally {
      if (!silent) setLoadingConversations(false)
    }
  }, [])

  const loadDetail = useCallback(async (selected: ChatConversa, silent = false) => {
    if (!silent) setLoadingDetail(true)
    try {
      const data = await fetchChatConversa(selected.id)
      setDetail(data)
      setConversation(data.conversa)
      if (data.conversa.naoLidas > 0) {
        await marcarChatConversaComoLida(selected.id)
        window.dispatchEvent(new Event('topsv3:chat-nao-lidas'))
        void loadConversations(true)
      }
      if (!silent) setError(null)
    } catch (cause) {
      if (!silent) setError(chatError(cause))
    } finally {
      if (!silent) setLoadingDetail(false)
    }
  }, [loadConversations])

  const openConversation = useCallback((
    selected: ChatConversa,
    navigation: 'push' | 'replace' | 'none'
  ) => {
    requestedConversationHandled.current = selected.id
    setConversation(selected)
    setDetail(null)
    setError(null)
    void loadDetail(selected)
    if (navigation === 'push') router.push(`/chat?conversa=${encodeURIComponent(selected.id)}`)
    if (navigation === 'replace') router.replace(`/chat?conversa=${encodeURIComponent(selected.id)}`)
  }, [loadDetail, router])

  const selectConversation = useCallback((selected: ChatConversa) => {
    openConversation(selected, 'push')
  }, [openConversation])

  const startConversation = useCallback(async (
    username: string,
    navigation: 'push' | 'replace' = 'push'
  ) => {
    if (startingRef.current) return
    startingRef.current = true
    setStarting(true)
    setError(null)
    try {
      const started = await iniciarChatConversa(username.trim())
      await loadConversations(true)
      openConversation(started, navigation)
    } catch (cause) {
      setError(chatError(cause))
    } finally {
      startingRef.current = false
      setStarting(false)
    }
  }, [loadConversations, openConversation])

  const startAdConversation = useCallback(async (anuncioId: string) => {
    if (startingRef.current) return
    startingRef.current = true
    setStarting(true)
    setError(null)
    try {
      const started = await iniciarChatConversaPorAnuncio(anuncioId)
      await loadConversations(true)
      openConversation(started, 'replace')
    } catch (cause) {
      setError(chatError(cause))
    } finally {
      startingRef.current = false
      setStarting(false)
    }
  }, [loadConversations, openConversation])

  const retryCurrentAction = useCallback(() => {
    if (requestedAd) {
      void startAdConversation(requestedAd)
      return
    }
    if (requestedUser) {
      void startConversation(requestedUser, 'replace')
      return
    }
    void loadConversations()
  }, [loadConversations, requestedAd, requestedUser, startAdConversation, startConversation])

  const sendMessage = useCallback(async (message: string) => {
    if (!conversation || sending) return false
    setSending(true)
    setError(null)
    const pending = pendingMessage.current
    const key = pending?.body === message ? pending.key : crypto.randomUUID()
    pendingMessage.current = { key, body: message }
    try {
      await enviarChatMensagem(conversation.id, message, key)
      pendingMessage.current = null
      await Promise.all([
        loadDetail(conversation, true),
        loadConversations(true),
      ])
      window.dispatchEvent(new Event('topsv3:chat-nao-lidas'))
      return true
    } catch (cause) {
      setError(chatError(cause))
      return false
    } finally {
      setSending(false)
    }
  }, [conversation, loadConversations, loadDetail, sending])

  useEffect(() => {
    const resize = () => setMobile(window.innerWidth < 768)
    resize()
    window.addEventListener('resize', resize)
    return () => window.removeEventListener('resize', resize)
  }, [])

  useEffect(() => {
    void loadConversations()
    const timer = window.setInterval(() => void loadConversations(true), CONVERSATION_POLL_MS)
    return () => window.clearInterval(timer)
  }, [loadConversations])

  useEffect(() => {
    if (!conversation) return
    const timer = window.setInterval(() => void loadDetail(conversation, true), MESSAGE_POLL_MS)
    return () => window.clearInterval(timer)
  }, [conversation, loadDetail])

  useEffect(() => {
    if (!requestedUser || requestedUserHandled.current === requestedUser) return
    requestedUserHandled.current = requestedUser
    void startConversation(requestedUser, 'replace')
  }, [requestedUser, startConversation])

  useEffect(() => {
    if (!requestedAd || requestedAdHandled.current === requestedAd) return
    requestedAdHandled.current = requestedAd
    void startAdConversation(requestedAd)
  }, [requestedAd, startAdConversation])

  useEffect(() => {
    if (!requestedConversationId) {
      requestedConversationHandled.current = null
      setConversation(null)
      setDetail(null)
      return
    }
    if (requestedConversationHandled.current === requestedConversationId) return
    const selected = conversations.find((item) => item.id === requestedConversationId)
    if (selected) openConversation(selected, 'none')
  }, [conversations, openConversation, requestedConversationId])

  return (
    <section className="mt-5">
      <div className="flex h-[calc(100dvh-180px)] min-h-[32rem] overflow-hidden rounded-lg border border-gray-200 bg-white">
        {!mobile || !conversation ? (
          <ChatSidebar
            conversations={conversations}
            selectedId={conversation?.id ?? null}
            loading={loadingConversations}
            starting={starting}
            error={error}
            onRetry={retryCurrentAction}
            onStartConversation={startConversation}
            onSelectConversation={selectConversation}
          />
        ) : null}
        {!mobile || conversation ? (
          <div className="min-w-0 flex flex-1 flex-col">
            <ChatWindow
              conversation={conversation}
              detail={detail}
              loading={loadingDetail}
              sending={sending}
              error={error}
              onRetry={() => conversation && void loadDetail(conversation)}
              onSend={sendMessage}
              onBack={() => {
                requestedConversationHandled.current = null
                setConversation(null)
                setDetail(null)
                router.push('/chat')
              }}
              isMobile={mobile}
            />
          </div>
        ) : null}
      </div>
    </section>
  )
}
