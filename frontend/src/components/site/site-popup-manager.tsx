'use client'

import { useEffect, useState } from 'react'

import { ContractState, pendingContractError } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { PENDING_BACKEND_CONTRACTS } from '@/lib/api-contract'

export function SitePopupManager() {
  const [loginPopupOpen, setLoginPopupOpen] = useState(false)
  const [siteBannerVisible, setSiteBannerVisible] = useState(true)

  useEffect(() => {
    const handler = () => setLoginPopupOpen(true)
    window.addEventListener('tops:login-success', handler)
    return () => window.removeEventListener('tops:login-success', handler)
  }, [])

  return (
    <>
      {siteBannerVisible ? (
        <aside className="border-b border-amber-200 bg-amber-50 px-4 py-2" aria-label="Aviso do site">
          <div className="mx-auto flex max-w-7xl items-start gap-3">
            <div className="min-w-0 flex-1">
              <ContractState error={pendingContractError(PENDING_BACKEND_CONTRACTS.notices)} compact />
            </div>
            <Button type="button" variant="ghost" size="sm" onClick={() => setSiteBannerVisible(false)}>Fechar</Button>
          </div>
        </aside>
      ) : null}
      <Dialog open={loginPopupOpen} onOpenChange={setLoginPopupOpen}>
        <DialogContent className="rounded-xl sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Avisos da plataforma</DialogTitle>
            <DialogDescription>Informacoes relevantes para sua conta e seguranca.</DialogDescription>
          </DialogHeader>
          <ContractState error={pendingContractError(PENDING_BACKEND_CONTRACTS.notices)} compact />
          <DialogFooter>
            <Button variant="outline" onClick={() => setLoginPopupOpen(false)}>Fechar</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
