'use client'

import { useEffect, useMemo, useState } from 'react'

import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { listarAvisosPublicos, type AvisoPublico } from '@/lib/aviso-api'

function storageKey(prefix: string, notice: AvisoPublico | null) {
  if (!notice) return ''
  return `${prefix}_${notice.id}_${notice.publicadoEm}`
}

function shouldDisplay(key: string, frequency?: AvisoPublico['frequenciaExibicao']) {
  if (typeof window === 'undefined' || !key || frequency === 'SEMPRE') return true
  const saved = localStorage.getItem(key)
  if (!saved) return true
  if (frequency === 'UMA_VEZ') return false
  const displayed = new Date(saved)
  const now = new Date()
  return displayed.getFullYear() !== now.getFullYear()
    || displayed.getMonth() !== now.getMonth()
    || displayed.getDate() !== now.getDate()
}

function markDisplayed(key: string) {
  if (typeof window !== 'undefined' && key) localStorage.setItem(key, new Date().toISOString())
}

function validityLabel(notice: AvisoPublico | null) {
  if (!notice?.ativoAte) return null
  const end = new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'America/Sao_Paulo',
  }).format(new Date(notice.ativoAte))
  return `Disponivel ate ${end}.`
}

export function SitePopupManager() {
  const [siteBanner, setSiteBanner] = useState<AvisoPublico | null>(null)
  const [loginPopup, setLoginPopup] = useState<AvisoPublico | null>(null)
  const [loginOpen, setLoginOpen] = useState(false)

  const siteKey = useMemo(() => storageKey('tops_site_banner', siteBanner), [siteBanner])
  const loginKey = useMemo(() => storageKey('tops_login_popup', loginPopup), [loginPopup])

  useEffect(() => {
    const controller = new AbortController()
    void Promise.all([
      listarAvisosPublicos('SITE', 1, controller.signal),
      listarAvisosPublicos('LOGIN_POPUP', 1, controller.signal),
    ]).then(([site, login]) => {
      const banner = site[0] ?? null
      if (banner && shouldDisplay(storageKey('tops_site_banner', banner), banner.frequenciaExibicao)) {
        setSiteBanner(banner)
      }
      setLoginPopup(login[0] ?? null)
    }).catch(() => {
      setSiteBanner(null)
      setLoginPopup(null)
    })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const handler = () => {
      if (loginPopup && shouldDisplay(loginKey, loginPopup.frequenciaExibicao)) setLoginOpen(true)
    }
    window.addEventListener('tops:login-success', handler)
    return () => window.removeEventListener('tops:login-success', handler)
  }, [loginKey, loginPopup])

  const dismissBanner = () => {
    if (!siteBanner?.permiteDispensar) return
    markDisplayed(siteKey)
    setSiteBanner(null)
  }

  const dismissLogin = () => {
    markDisplayed(loginKey)
    setLoginOpen(false)
  }

  return (
    <>
      {siteBanner ? (
        <aside className="border-b border-pink-200 bg-pink-50 px-4 py-2" aria-label="Aviso do site">
          <div className="mx-auto flex max-w-[1500px] items-start gap-3">
            <div className="min-w-0 flex-1 text-sm text-zinc-800">
              <p className="font-semibold text-zinc-950">{siteBanner.titulo}</p>
              <p className="mt-1 whitespace-pre-line">{siteBanner.descricao}</p>
              {validityLabel(siteBanner) ? <p className="mt-1 text-xs text-zinc-500">{validityLabel(siteBanner)}</p> : null}
            </div>
            {siteBanner.permiteDispensar ? <Button type="button" variant="ghost" size="sm" onClick={dismissBanner}>Fechar</Button> : null}
          </div>
        </aside>
      ) : null}
      <Dialog open={loginOpen} onOpenChange={(open) => !open && dismissLogin()}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader><DialogTitle>{loginPopup?.titulo}</DialogTitle><DialogDescription>Informacao relevante da plataforma.</DialogDescription></DialogHeader>
          <p className="whitespace-pre-line text-sm text-zinc-700">{loginPopup?.descricao}</p>
          {validityLabel(loginPopup) ? <p className="text-xs text-zinc-500">{validityLabel(loginPopup)}</p> : null}
          <DialogFooter><Button onClick={dismissLogin}>Entendi</Button></DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
