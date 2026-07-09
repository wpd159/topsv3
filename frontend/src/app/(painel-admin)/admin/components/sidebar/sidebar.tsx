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
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet'
import { Button } from '@/components/ui/button'
import { getPublicLogoUrl } from '@/lib/public-site-assets'

export default function Sidebar() {
  const [open, setOpen] = useState(false)
  const { usuario } = useAuth()
  const [notificationCounts, setNotificationCounts] = useState({
    tickets: 0,
    denuncias: 0,
    sugestoes: 0,
    revisoes: 0,
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
    const api = process.env.NEXT_PUBLIC_API_URL
    if (!api) return

    const carregarContadores = async () => {
      try {
        const [ticketsRes, denunciasRes, sugestoesRes, revisoesRes, anunciosRes] = await Promise.all([
          fetch(`${api}/suporte`, { credentials: 'include', cache: 'no-store' }),
          fetch(`${api}/denuncias`, { credentials: 'include', cache: 'no-store' }),
          fetch(`${api}/sugestoes/admin?pagina=0&tamanho=100`, {
            credentials: 'include',
            cache: 'no-store',
          }),
          fetch(`${api}/anuncios/staff/revisions`, { credentials: 'include', cache: 'no-store' }),
          fetch(`${api}/anuncios/staff`, { credentials: 'include', cache: 'no-store' }),
        ])

        const tickets = ticketsRes.ok ? await ticketsRes.json() : []
        const denuncias = denunciasRes.ok ? await denunciasRes.json() : []
        const sugestoesPayload = sugestoesRes.ok ? await sugestoesRes.json() : { content: [] }
        const sugestoes = Array.isArray(sugestoesPayload?.content) ? sugestoesPayload.content : []
        const revisoes = revisoesRes.ok ? await revisoesRes.json() : []
        const anuncios = anunciosRes.ok ? await anunciosRes.json() : []
        const revisaoAnuncioIds = new Set(
          Array.isArray(revisoes)
            ? revisoes.map((revisao: any) => Number(revisao?.anuncioId)).filter(Number.isFinite)
            : []
        )
        const filaModeracao = Array.isArray(anuncios)
          ? anuncios.filter((anuncio: any) => {
              if (anuncio?.removidoLogicamente) return false
              const status = String(anuncio?.status || '').toUpperCase()
              if (status === 'PENDENTE') return true
              return revisaoAnuncioIds.has(Number(anuncio?.id))
            }).length
          : 0

        if (!active) return

        setNotificationCounts({
          tickets: Array.isArray(tickets)
            ? tickets.filter((ticket: any) => ticket?.status !== 'FECHADO').length
            : 0,
          denuncias: Array.isArray(denuncias)
            ? denuncias.filter((denuncia: any) => denuncia?.status === 'PENDENTE').length
            : 0,
          sugestoes: Array.isArray(sugestoes)
            ? sugestoes.filter((sugestao: any) => sugestao?.status === 'NOVO').length
            : 0,
          revisoes: filaModeracao,
        })
      } catch {
        if (!active) return
        setNotificationCounts({ tickets: 0, denuncias: 0, sugestoes: 0, revisoes: 0 })
      }
    }

    carregarContadores()
    const interval = window.setInterval(carregarContadores, 30000)
    const handleRevisionUpdate = () => {
      void carregarContadores()
    }
    window.addEventListener('admin-revisions-updated', handleRevisionUpdate)

    return () => {
      active = false
      window.clearInterval(interval)
      window.removeEventListener('admin-revisions-updated', handleRevisionUpdate)
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
