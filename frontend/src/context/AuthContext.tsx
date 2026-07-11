'use client'

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import SockJS from 'sockjs-client'
import { Client, type IMessage } from '@stomp/stompjs'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'
import { getPublicSession, logoutPublic, type PublicAuthUser } from '@/lib/public-auth-api'
import { logoutAdmin } from '@/lib/admin-auth-api'

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
  totalDocumentos: number
  creditos: number
  totalIndicados: number
  creditosIndicacaoGanhos: number
  creditosPorIndicacao: number
  linkIndicacao: string
  status: string
  cargo: string

  cpf?: string | null
  dataNascimento?: string | null
  documentosUrls?: string[] | null
  advertiserVerificationStatus?: string | null
  advertiserVerificationNotes?: string | null
}

type AuthContextType = {
  usuario: Usuario | null
  carregando: boolean
  perfilCompleto: boolean

  // 🔔 chat badge
  novasMensagens: number
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
    totalDocumentos: 0,
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
  const [novasMensagens, setNovasMensagens] = useState(0)
  const zerarNovasMensagens = () => setNovasMensagens(0)

  // refs
  const stompRef = useRef<Client | null>(null)
  const audioRef = useRef<HTMLAudioElement | null>(null)

  // cria áudio 1x
  useEffect(() => {
    audioRef.current = new Audio('/notification_sound.mp3')
    audioRef.current.preload = 'auto'
  }, [])

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

      const publicBase = (process.env.NEXT_PUBLIC_API_URL || '').replace(/\/$/, '')
      const backendBase = publicBase.replace(/\/api\/public$/, '')
      const res = await fetch(`${backendBase}/api/admin/auth/me`, { credentials: 'include' })

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
          totalDocumentos: 0,
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
    setNovasMensagens(0)
    if (stompRef.current?.active) stompRef.current.deactivate()
    stompRef.current = null
  }

  const refresh = async () => {
    return fetchUsuario()
  }

  useEffect(() => {
    fetchUsuario()
  }, [])

  // ========= PERFIL COMPLETO =========
  const perfilCompleto = useMemo(() => {
    if (!usuario) return false

    const temNome =
      !!usuario.nomeCompleto && usuario.nomeCompleto.trim().length >= 3
    const temCpf = !!usuario.cpf && usuario.cpf.trim().length > 0
    const temNascimento = !!usuario.dataNascimento
    const temDocs =
      (usuario.documentosUrls?.length ?? 0) >= 1 ||
      (usuario.totalDocumentos ?? 0) >= 1

    const temLocalizacaoIds =
      !!usuario.estadoId && !!usuario.cidadeId && !!usuario.bairroId

    return temNome && temCpf && temNascimento && temDocs && temLocalizacaoIds
  }, [usuario])

  // ========= WEBSOCKET (centralizado: créditos + chat) =========
  const wsUrl = useMemo(() => {
    const base = (process.env.NEXT_PUBLIC_API_URL || '').replace(/\/$/, '')
    return `${base}/ws-suporte`
  }, [])

  useEffect(() => {
    // se não tem usuário, garante que a conexão morra
    if (!usuario?.id) {
      if (stompRef.current?.active) stompRef.current.deactivate()
      stompRef.current = null
      setNovasMensagens(0)
      return
    }

    // se já está ativo com esse usuário, não recria
    if (stompRef.current?.active) return

    const client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      reconnectDelay: 5000,
      onConnect: () => {
        // 1) Créditos
        client.subscribe(`/topic/creditos/${usuario.id}`, (message: IMessage) => {
          try {
            const payload = JSON.parse(message.body) as {
              usuarioId?: number
              novoSaldo?: number
            }

            if (typeof payload?.novoSaldo === 'number') {
              const novoSaldo = payload.novoSaldo
              setUsuario((prev) => (prev ? { ...prev, creditos: novoSaldo } : prev))
            }
          } catch (e) {
          }
        })

        // 2) Chat global -> badge só pro destinatário logado
        client.subscribe(`/topic/chat/global`, (message: IMessage) => {
          try {
            const data = JSON.parse(message.body) as {
              destinatarioId?: number
            }

            if (data?.destinatarioId === usuario.id) {
              setNovasMensagens((prev) => prev + 1)
              audioRef.current?.play().catch(() => null)
            }
          } catch (e) {
          }
        })
      },
      onStompError: (frame) => {
      },
    })

    client.activate()
    stompRef.current = client

    return () => {
      if (stompRef.current?.active) stompRef.current.deactivate()
      stompRef.current = null
    }
  }, [usuario?.id, wsUrl])

  return (
    <AuthContext.Provider
      value={{
        usuario,
        carregando,
        perfilCompleto,
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
