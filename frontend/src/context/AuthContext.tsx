'use client'

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'
import { getPublicSession, logoutPublic, type PublicAuthUser } from '@/lib/public-auth-api'
import { logoutAdmin } from '@/lib/admin-auth-api'
import { adminApiUrl } from '@/lib/api-contract'

type Usuario = {
  id: string | number
  username: string
  nomeCompleto: string | null
  email: string
  telefone: string | null
  estadoId: number | null
  cidadeId: number | null
  bairroId: number | null
  localizacao: string | null
  cidade: string | null
  descricao: string | null
  twoFactorAtivo: boolean
  totalAnuncios: number
  creditos: number
  totalIndicados: number
  creditosIndicacaoGanhos: number
  creditosPorIndicacao: number
  linkIndicacao: string
  status: string
  cargo: string

  dataNascimento?: string | null
  advertiserVerificationStatus?: string | null
  advertiserVerificationNotes?: string | null
}

type AuthContextType = {
  usuario: Usuario | null
  carregando: boolean

  // 🔔 chat badge
  novasMensagens: number | null
  zerarNovasMensagens: () => void

  login: () => Promise<Usuario | null>
  logout: () => Promise<void>
  refresh: () => Promise<Usuario | null>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

function publicUserToContext(data: PublicAuthUser): Usuario {
  return {
    id: data.id,
    username: data.username,
    nomeCompleto: data.nomeCompleto,
    email: data.email,
    telefone: data.telefone,
    estadoId: null,
    cidadeId: null,
    bairroId: null,
    localizacao: null,
    cidade: null,
    descricao: null,
    twoFactorAtivo: false,
    totalAnuncios: 0,
    creditos: 0,
    totalIndicados: 0,
    creditosIndicacaoGanhos: 0,
    creditosPorIndicacao: 0,
    linkIndicacao: '',
    status: data.status,
    cargo: data.cargo,
  }
}
export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(null)
  const [carregando, setCarregando] = useState(true)

  // 🔔 badge de mensagens
  const [novasMensagens, setNovasMensagens] = useState<number | null>(null)
  const zerarNovasMensagens = useCallback(() => setNovasMensagens(null), [])

  // ========== GET /auth/me ==========
  const fetchUsuario = async (): Promise<Usuario | null> => {
    try {
      const adminRoute = typeof window !== 'undefined' && window.location.pathname.startsWith('/admin')
      if (!adminRoute) {
        const data = await getPublicSession()
        if (!data) {
          setUsuario(null)
          return null
        }
        const nextUser = publicUserToContext(data)
        setUsuario(nextUser)
        return nextUser
      }

      const res = await fetch(adminApiUrl('/auth/me'), { credentials: 'include' })

      if (res.status === 401) {
        setUsuario(null)
        return null
      }

      if (!res.ok) {
        setUsuario(null)
        return null
      }

      const raw = corrigirEstruturaTexto(await res.json()) as Usuario & {
        autenticado?: boolean
        usuarioId?: string
        nome?: string | null
        papeis?: string[]
      }

      if (adminRoute) {
        if (!raw.autenticado) {
          setUsuario(null)
          return null
        }
        const cargo = raw.papeis?.includes('ADMIN') ? 'ADMIN' : raw.papeis?.includes('MODERADOR') ? 'MODERADOR' : ''
        if (!cargo) {
          setUsuario(null)
          return null
        }
        const adminUser: Usuario = {
          id: 0,
          username: raw.email?.split('@')[0] || 'admin',
          nomeCompleto: raw.nome ?? null,
          email: raw.email ?? '',
          telefone: null,
          estadoId: null,
          cidadeId: null,
          bairroId: null,
          localizacao: null,
          cidade: null,
          descricao: null,
          twoFactorAtivo: false,
          totalAnuncios: 0,
          creditos: 0,
          totalIndicados: 0,
          creditosIndicacaoGanhos: 0,
          creditosPorIndicacao: 0,
          linkIndicacao: '',
          status: 'ATIVO',
          cargo,
        }
        setUsuario(adminUser)
        return adminUser
      }

      setUsuario(null)
      return null
    } catch (err) {
      setUsuario(null)
      return null
    } finally {
      setCarregando(false)
    }
  }

  // ========= LOGIN =========
  const login = async () => {
    return fetchUsuario()
  }

  // ========= LOGOUT =========
  const logout = async () => {
    const adminRoute = typeof window !== 'undefined' && window.location.pathname.startsWith('/admin')
    if (adminRoute) {
      await logoutAdmin()
    } else {
      await logoutPublic()
    }
    setUsuario(null)
    setNovasMensagens(null)
  }

  const refresh = async () => {
    return fetchUsuario()
  }

  useEffect(() => {
    fetchUsuario()
  }, [])

  return (
    <AuthContext.Provider
      value={{
        usuario,
        carregando,
        novasMensagens,
        zerarNovasMensagens,
        login,
        logout,
        refresh,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

// ========= HOOK =========
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth deve ser usado dentro de AuthProvider')
  return context
}
