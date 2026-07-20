'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'

import { ContractState, PendingActionFeedback, usePendingContractActions } from '@/components/feedback/contract-state'
import { LoginModal } from '@/components/modals/login-modal'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/context/AuthContext'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

const CATEGORIES = ['Todas', 'Conta', 'Pagamentos', 'Segurança', 'Anúncios', 'Geral']

export default function FAQPage() {
  const router = useRouter()
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('Todas')
  const [loginOpen, setLoginOpen] = useState(false)
  const { usuario } = useAuth()
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(PENDING_BACKEND_CONTRACTS.notices)
  const openSupport = () => {
    if (usuario) {
      window.dispatchEvent(new Event('open-suporte-ticket'))
      return
    }
    setLoginOpen(true)
  }
  return <section className="mx-auto max-w-6xl space-y-6 py-10"><div><h1 className="text-3xl font-bold">FAQ - Perguntas frequentes</h1><p className="mt-2 text-gray-600">Busca, categorias, respostas e acesso ao suporte.</p></div><ContractState error={error} /><PendingActionFeedback attemptedAction={attemptedAction} /><div className="flex flex-col gap-3 rounded-lg border bg-white p-4 sm:flex-row"><Input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Busque por palavra-chave" /><Button type="button" onClick={() => runPendingAction('Buscar pergunta frequente')}>Buscar</Button><Button type="button" variant="outline" onClick={openSupport}>Falar com suporte</Button></div><div className="flex flex-wrap gap-2" aria-label="Categorias de perguntas frequentes">{CATEGORIES.map((item) => <Button key={item} type="button" variant={category === item ? 'default' : 'outline'} onClick={() => { setCategory(item); runPendingAction(`Filtrar FAQ: ${item}`) }}>{item}</Button>)}</div><div className="rounded-lg border bg-white"><button type="button" className="flex w-full items-center justify-between p-4 text-left font-medium" aria-expanded="false" onClick={() => runPendingAction('Abrir pergunta frequente')}><span>Pergunta e resposta não carregadas</span><span>Expandir</span></button><div className="border-t p-4"><ContractState error={error} compact /></div></div><div className="flex flex-wrap justify-between gap-2"><Button type="button" variant="ghost" onClick={() => router.back()}>Voltar</Button><div className="flex gap-2"><Button type="button" variant="secondary" onClick={() => setQuery('')}>Limpar busca</Button><Button type="button" onClick={openSupport}>Ainda com dúvidas? Fale com a gente</Button><Button type="button" variant="outline" onClick={openSupport}>Abrir ticket</Button></div></div><LoginModal open={loginOpen} onOpenChange={setLoginOpen} /></section>
}
