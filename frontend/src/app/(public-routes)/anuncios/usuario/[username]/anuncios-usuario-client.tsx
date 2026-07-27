'use client'

import { useState } from 'react'
import Link from 'next/link'

import {
  ContractState,
  PendingActionFeedback,
  usePendingContractActions,
} from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export default function AnunciosUsuarioClient({ username }: { username: string }) {
  const login = (username || '').trim()
  const [safetyOpen, setSafetyOpen] = useState(false)
  const { error, attemptedAction, runPendingAction } = usePendingContractActions(
    PENDING_BACKEND_CONTRACTS.publicProfiles
  )

  return (
    <section className="space-y-6 px-4 py-8">
      <nav className="text-sm text-gray-600">
        <Button type="button" variant="ghost" className="mr-2 h-auto p-0 text-sm" onClick={() => window.history.back()}>Voltar</Button>
        <Link href="/anuncios" className="hover:text-pink-600">
          Anúncios
        </Link>
        <span className="mx-2">/</span>
        <span className="font-medium text-gray-900">{login ? `@${login}` : '-'}</span>
      </nav>

      <div className="space-y-1">
        <h1 className="text-2xl font-bold text-gray-900">
          Anúncios {login ? <>de @{login}</> : null}
        </h1>
        <p className="text-sm text-gray-600">Perfis ativos publicados por este usuário.</p>
      </div>

      <ContractState error={error} />
      <PendingActionFeedback attemptedAction={attemptedAction} />

      <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3" aria-label="Anúncios do usuário">
        <article className="overflow-hidden rounded-lg border border-gray-200 bg-white">
          <div className="flex aspect-[4/5] items-center justify-center bg-gray-100 px-6 text-center text-sm text-gray-600">
            A galeria não foi carregada porque o contrato de perfil público está pendente.
          </div>
          <div className="space-y-3 p-4">
            <div className="flex items-center justify-between gap-2">
              <Button type="button" size="sm" variant="outline" onClick={() => runPendingAction('Foto anterior')}>Foto anterior</Button>
              <Button type="button" size="sm" variant="outline" onClick={() => runPendingAction('Próxima foto')}>Próxima foto</Button>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <Button type="button" onClick={() => runPendingAction('Abrir página do anúncio')}>Ver anúncio</Button>
              <Button asChild variant="outline">
                <Link href={`/chat?usuario=${encodeURIComponent(login)}`}>Chat</Link>
              </Button>
              <Button type="button" variant="outline" onClick={() => setSafetyOpen(true)}>WhatsApp</Button>
              <Button type="button" variant="outline" onClick={() => runPendingAction('Adicionar aos favoritos')}>Adicionar aos favoritos</Button>
            </div>
            <Button type="button" variant="ghost" className="w-full" onClick={() => runPendingAction('Confirmar maioridade')}>Confirmar maioridade</Button>
          </div>
        </article>
      </div>

      <Button asChild variant="outline">
        <Link href="/anuncios">Ver catálogo completo</Link>
      </Button>
      <Button asChild variant="ghost">
        <Link href="/politicas/verificacao-etaria">Política de Verificação Etária</Link>
      </Button>

      <Dialog open={safetyOpen} onOpenChange={setSafetyOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Aviso de segurança</DialogTitle>
            <DialogDescription>Antes de seguir para o WhatsApp, leia com atenção.</DialogDescription>
          </DialogHeader>
          <ContractState error={error} compact />
          <PendingActionFeedback attemptedAction={attemptedAction} />
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => setSafetyOpen(false)}>Fechar</Button>
            <Button type="button" variant="outline" onClick={() => setSafetyOpen(false)}>Cancelar</Button>
            <Button type="button" onClick={() => runPendingAction('Continuar para o WhatsApp')}>Continuar</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}
