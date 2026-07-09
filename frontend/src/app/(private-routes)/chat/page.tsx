'use client'

import { Suspense } from "react"
import ChatClient from "./chat-client"

export default function ChatPageWrapper() {
  return (
    <Suspense fallback={<div>Carregando...</div>}>
      <ChatClient />
    </Suspense>
  )
}
