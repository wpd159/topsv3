'use client'

import Header from './header'
import HeaderLogado from './header-logado'
import { HeaderSkeleton } from './header-skeleton'
import { useAuth } from '@/context/AuthContext'

export default function HeaderWrapper() {
  const { usuario, carregando } = useAuth()

  if (carregando) {
    return <HeaderSkeleton />
  }

  return usuario ? <HeaderLogado /> : <Header />
}
