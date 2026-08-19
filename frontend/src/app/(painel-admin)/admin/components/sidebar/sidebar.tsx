'use client'

import { useEffect, useState } from 'react'
import { Menu } from 'lucide-react'
import Image from 'next/image'
import { useAuth } from '@/context/AuthContext'

import { sidebarLinks } from './sidebar-links'
import { SidebarNav } from './sidebar-nav'
import { SidebarUserFooter } from './sidebar-user-footer'
import { filterSidebarLinksByRole } from './sidebar-utils'

import { Separator } from '@/components/ui/separator'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet'
import { Button } from '@/components/ui/button'
import { getPublicLogoUrl } from '@/lib/public-site-assets'
import { adminApiUrl, apiErrorFromResponse } from '@/lib/api-contract'

type ModerationSummary = {
  anunciosPendentesModeracao: number
}

type TicketIndicators = {
  pendentesEquipe: number
}

type DenunciaIndicators = {
  pendentes: number
}

type SugestaoIndicators = {
  novas: number
}

export default function Sidebar() {
  const [open, setOpen] = useState(false)
  const { usuario } = useAuth()
  const [notificationCounts, setNotificationCounts] = useState({
    tickets: null as number | null,
    denuncias: null as number | null,
    sugestoes: null as number | null,
    revisoes: null as number | null,
  })

  const handleNavClick = () => {
    setOpen(false)
  }

  const filteredLinks = filterSidebarLinksByRole(
    sidebarLinks,
    usuario?.cargo || ''
  )

  useEffect(() => {
    if (!usuario) return

    let active = true
    let inFlight: Promise<void> | null = null

    const carregarContadores = async () => {
      if (inFlight) return inFlight
      inFlight = (async () => {
        try {
          const [
            moderationResponse,
            ticketsResponse,
            denunciasResponse,
            sugestoesResponse,
          ] = await Promise.all([
            fetch(adminApiUrl('/moderacao/resumo'), {
              credentials: 'include',
              cache: 'no-store',
            }),
            fetch(adminApiUrl('/tickets/indicadores'), {
              credentials: 'include',
              cache: 'no-store',
            }),
            fetch(adminApiUrl('/denuncias/indicadores'), {
              credentials: 'include',
              cache: 'no-store',
            }),
            fetch(adminApiUrl('/sugestoes/indicadores'), {
              credentials: 'include',
              cache: 'no-store',
            }),
          ])
          if (!moderationResponse.ok) throw await apiErrorFromResponse(moderationResponse)
          if (!ticketsResponse.ok) throw await apiErrorFromResponse(ticketsResponse)
          if (!denunciasResponse.ok) throw await apiErrorFromResponse(denunciasResponse)
          if (!sugestoesResponse.ok) throw await apiErrorFromResponse(sugestoesResponse)
          const summary = (await moderationResponse.json()) as ModerationSummary
          const tickets = (await ticketsResponse.json()) as TicketIndicators
          const denuncias = (await denunciasResponse.json()) as DenunciaIndicators
          const sugestoes = (await sugestoesResponse.json()) as SugestaoIndicators
          if (!active) return
          setNotificationCounts((current) => ({
            ...current,
            tickets: Number(tickets.pendentesEquipe),
            denuncias: Number(denuncias.pendentes),
            sugestoes: Number(sugestoes.novas),
            revisoes: Number(summary.anunciosPendentesModeracao),
          }))
        } catch {
          if (!active) return
          setNotificationCounts((current) => ({
            ...current,
            tickets: null,
            denuncias: null,
            sugestoes: null,
            revisoes: null,
          }))
        } finally {
          inFlight = null
        }
      })()
      return inFlight
    }

    carregarContadores()
    const interval = window.setInterval(carregarContadores, 30000)
    const handleRevisionUpdate = () => {
      void carregarContadores()
    }
    window.addEventListener('admin-revisions-updated', handleRevisionUpdate)
    window.addEventListener('suporte-ticket-changed', handleRevisionUpdate)
    window.addEventListener('admin-denuncias-updated', handleRevisionUpdate)
    window.addEventListener('admin-sugestoes-updated', handleRevisionUpdate)

    return () => {
      active = false
      window.clearInterval(interval)
      window.removeEventListener('admin-revisions-updated', handleRevisionUpdate)
      window.removeEventListener('suporte-ticket-changed', handleRevisionUpdate)
      window.removeEventListener('admin-denuncias-updated', handleRevisionUpdate)
      window.removeEventListener('admin-sugestoes-updated', handleRevisionUpdate)
    }
  }, [usuario])

  return (
    <>
      <header className="flex items-center gap-3 bg-[#151619] p-4 lg:hidden">
        <Sheet open={open} onOpenChange={setOpen}>
          <SheetTrigger asChild>
            <Button variant="ghost" size="icon" aria-label="Abrir menu">
              <Menu className="h-5 w-5 text-white" />
            </Button>
          </SheetTrigger>

          <SheetContent
            side="left"
            className="!max-w-none !w-full fixed inset-0 flex flex-col border-black bg-black p-4 text-sm"
          >
            <SheetHeader>
              <SheetTitle className="sr-only">Menu de navegacao</SheetTitle>
              <SheetDescription className="sr-only">
                Navegacao principal do painel administrativo.
              </SheetDescription>
            </SheetHeader>

            <div className="sidebar-scroll flex-1 overflow-y-auto">
              <SidebarNav
                items={filteredLinks}
                onItemClick={handleNavClick}
                notificationCounts={notificationCounts}
              />
            </div>

            <Separator className="my-4 bg-white/10" />
            <SidebarUserFooter />
          </SheetContent>
        </Sheet>

        <Image
          src={getPublicLogoUrl()}
          alt="Tops do Job"
          width={150}
          height={60}
          priority
          fetchPriority="high"
          unoptimized
        />
      </header>

      <aside className="mt-5 hidden w-62 flex-col p-4 text-sm lg:flex">
        <div className="flex justify-center">
          <Image
            src={getPublicLogoUrl()}
            alt="Tops do Job"
            width={260}
            height={80}
            priority
            fetchPriority="high"
            unoptimized
          />
        </div>

        <Separator className="my-6 bg-white/10" />

        <div className="sidebar-scroll flex-1 overflow-y-auto">
          <SidebarNav items={filteredLinks} notificationCounts={notificationCounts} />
        </div>

        <Separator className="my-4 mt-auto bg-white/10" />
        <SidebarUserFooter />
      </aside>
    </>
  )
}
