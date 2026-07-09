'use client'

import { useRouter } from 'next/navigation'
import { useAuth } from '@/context/AuthContext'
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from '@/components/ui/dropdown-menu'
import {
  Avatar,
  AvatarFallback,
  AvatarImage,
} from '@/components/ui/avatar'

export function SidebarUserFooter() {
  const router = useRouter()
  const { usuario } = useAuth()

  const handleRedirect = () => router.push('/')

  const nome = usuario?.nomeCompleto || 'Usuário'
  const username = usuario?.username || 'perfil'
  const email = usuario?.email || '-'
  const iniciais =
    nome
      .split(' ')
      .map((n) => n[0])
      .join('')
      .substring(0, 2)
      .toUpperCase() || 'US'

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <div className="px-4 -ml-4 pb-4 text-xs text-white/70 cursor-pointer w-full">
          <div className="flex items-center gap-2">
            <Avatar className="w-10 h-10 rounded-full">
              <AvatarFallback className="text-black font-bold">
                {iniciais}
              </AvatarFallback>
            </Avatar>

            <div className="flex flex-col leading-tight">
              {/* Nome completo */}
              <span className="font-semibold text-white truncate max-w-[150px]">
                {username}
              </span>

              {/* Username */}
              <span
                className="text-white/60 truncate max-w-[150px]"
                title={email}
              >
                {email}
              </span>
            </div>
          </div>
        </div>
      </DropdownMenuTrigger>

      <DropdownMenuContent
        align="start"
        sideOffset={4}
        className="w-48 bg-white shadow-md border rounded-md"
      >
        <DropdownMenuItem
          onClick={handleRedirect}
          className="text-sm cursor-pointer hover:bg-muted font-medium px-3 py-2"
        >
          Voltar para o site
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
