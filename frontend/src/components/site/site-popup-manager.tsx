'use client'

import { useEffect, useState } from 'react'

import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'

export type SiteNotice = {
  id: string
  title: string
  message: string
}

type SitePopupManagerProps = {
  notices?: readonly SiteNotice[]
}

export function SitePopupManager({ notices = [] }: SitePopupManagerProps) {
  const [loginPopupOpen, setLoginPopupOpen] = useState(false)
  const [siteBannerVisible, setSiteBannerVisible] = useState(true)
  const notice = notices[0] ?? null

  useEffect(() => {
    if (!notice) return
    const handler = () => setLoginPopupOpen(true)
    window.addEventListener('tops:login-success', handler)
    return () => window.removeEventListener('tops:login-success', handler)
  }, [notice])

  useEffect(() => {
    setSiteBannerVisible(true)
    setLoginPopupOpen(false)
  }, [notice?.id])

  if (!notice) return null

  return (
    <>
      {siteBannerVisible ? (
        <aside className="border-b border-amber-200 bg-amber-50 px-4 py-2" aria-label="Aviso do site">
          <div className="mx-auto flex max-w-7xl items-start gap-3">
            <div className="min-w-0 flex-1 text-sm text-amber-900">
              <p className="font-semibold">{notice.title}</p>
              <p className="mt-1 text-amber-800">{notice.message}</p>
            </div>
            <Button type="button" variant="ghost" size="sm" onClick={() => setSiteBannerVisible(false)}>Fechar</Button>
          </div>
        </aside>
      ) : null}
      <Dialog open={loginPopupOpen} onOpenChange={setLoginPopupOpen}>
        <DialogContent className="rounded-xl sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{notice.title}</DialogTitle>
            <DialogDescription>{notice.message}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setLoginPopupOpen(false)}>Fechar</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
