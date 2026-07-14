'use client'

import { usePathname, useRouter } from 'next/navigation'
import { HeartIcon } from '@heroicons/react/24/solid'
import { toast } from 'sonner'
import { useAuth } from '@/context/AuthContext'
import { useFavoritos } from '@/context/FavoritosContext'
import { cn } from '@/lib/utils'

type FavoritoButtonProps = {
  slug: string
  className?: string
  iconClassName?: string
}

export function FavoritoButton({ slug, className, iconClassName }: FavoritoButtonProps) {
  const router = useRouter()
  const pathname = usePathname()
  const { usuario, carregando: carregandoSessao } = useAuth()
  const { isFavorito, isPendente, alternar } = useFavoritos()
  const favorito = isFavorito(slug)
  const pendente = isPendente(slug)

  const abrirLogin = () => {
    const params = new URLSearchParams(typeof window === 'undefined' ? '' : window.location.search)
    params.set('login', '1')
    params.set('next', pathname)
    router.push(`${pathname}?${params.toString()}`, { scroll: false })
  }

  const handleClick = async (event: React.MouseEvent<HTMLButtonElement>) => {
    event.preventDefault()
    event.stopPropagation()
    if (carregandoSessao || pendente) return
    if (!usuario || usuario.cargo !== 'USUARIO') {
      abrirLogin()
      return
    }
    try {
      const novoEstado = await alternar(slug)
      toast.success(novoEstado ? 'Anúncio adicionado aos favoritos.' : 'Anúncio removido dos favoritos.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Não foi possível atualizar o favorito.')
    }
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={carregandoSessao || pendente}
      aria-label={favorito ? 'Remover dos favoritos' : 'Adicionar aos favoritos'}
      aria-pressed={favorito}
      className={cn(
        'rounded-full border border-gray-200 bg-white/90 p-2 shadow-sm transition hover:bg-white disabled:cursor-wait disabled:opacity-70',
        className
      )}
    >
      <HeartIcon
        className={cn('h-5 w-5', favorito ? 'text-[#FC1EAD]' : 'text-gray-600', iconClassName)}
      />
    </button>
  )
}
